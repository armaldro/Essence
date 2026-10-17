package com.armaldro.essence.agent

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ToolResultBlockParam
import com.armaldro.essence.data.AgentStore
import com.armaldro.essence.data.Memory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class AgentException(message: String) : Exception(message)

/**
 * Runs Claude in a tool loop: send the task, run any tools Claude calls, send the
 * results back, and repeat until Claude answers without calling a tool.
 */
class ClaudeAgent(private val store: AgentStore) {
    private var client: AnthropicClient? = null
    private var clientKey: String? = null

    /** The tool every run gets: lets Claude save a fact about the user to memory. */
    val rememberTool = AgentTool(
        name = "remember",
        description = "Save a short, durable fact about the user (a preference, a person, a project, a routine) " +
            "so it is available in future conversations. Use it when the user asks you to remember something, " +
            "or when you learn something clearly useful later. One fact per call.",
        properties = mapOf("fact" to stringProp("The fact, written as one plain sentence.")),
        required = listOf("fact"),
    ) { input ->
        val fact = input.string("fact")?.takeIf { it.isNotBlank() }
            ?: return@AgentTool ToolOutput("Missing 'fact'.", isError = true)
        store.addMemory(fact)
        ToolOutput("Saved.")
    }

    /**
     * Runs one task to completion and returns Claude's final text.
     * [onProgress] receives short status lines (Claude's text between tool calls and tool names).
     */
    suspend fun run(
        task: String,
        instructions: String,
        tools: List<AgentTool> = emptyList(),
        effort: OutputConfig.Effort = OutputConfig.Effort.MEDIUM,
        maxSteps: Int = 50,
        onProgress: (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val client = client()
        val model = store.model.first()
        val allTools = tools + rememberTool
        val byName = allTools.associateBy { it.name }
        val system = systemBlocks(instructions, store.memories.first())
        val history = mutableListOf(
            MessageParam.builder().role(MessageParam.Role.USER).content(task).build()
        )

        repeat(maxSteps) {
            val params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .systemOfTextBlockParams(system)
                .apply { allTools.forEach { addTool(it.definition) } }
                .messages(history)
                // Cache the conversation so far; each step resends it with one more turn.
                .cacheControl(CacheControlEphemeral.builder().build())
                .outputConfig(OutputConfig.builder().effort(effort).build())
                // If a safety classifier declines, let the API retry on a suitable model.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build()

            val response = client.messages().create(params)
            val stop = response.stopReason().orElse(null)
            if (stop == StopReason.REFUSAL) {
                throw AgentException("Claude declined this request.")
            }
            history += response.toParam()

            val text = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n").trim()
            val toolUses = response.content().mapNotNull { it.toolUse().orElse(null) }

            if (stop == StopReason.MAX_TOKENS && toolUses.isEmpty()) {
                return@withContext text.ifEmpty { "(The response was cut off.)" }
            }
            if (toolUses.isEmpty()) {
                if (stop == StopReason.PAUSE_TURN) return@repeat
                return@withContext text
            }
            if (text.isNotEmpty()) onProgress(text)

            // Run every tool call, then return all results in one user message.
            val results = toolUses.map { use ->
                val tool = byName[use.name()]
                val output = if (tool == null) {
                    ToolOutput("Unknown tool ${use.name()}.", isError = true)
                } else {
                    onProgress(use.name().replace('_', ' '))
                    val input = parseInput(use._input())
                    if (input == null) ToolOutput("Invalid JSON input.", isError = true)
                    else runCatching { tool.run(input) }.getOrElse { ToolOutput("Failed: ${it.message}", isError = true) }
                }
                ContentBlockParam.ofToolResult(toolResult(use.id(), output))
            }
            history += MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(results).build()
        }
        throw AgentException("Stopped after $maxSteps steps.")
    }

    private suspend fun client(): AnthropicClient {
        val key = store.apiKey() ?: throw AgentException("Add your Claude API key in Settings first.")
        if (key != clientKey) {
            client = AnthropicOkHttpClient.builder().apiKey(key).build()
            clientKey = key
        }
        return client!!
    }

    /** Stable instructions first and cached; memories after, since they change more often. */
    private fun systemBlocks(instructions: String, memories: List<Memory>): List<TextBlockParam> {
        val base = TextBlockParam.builder().text(instructions).build()
        val memoryText = if (memories.isEmpty()) {
            "You have no saved memories about the user yet."
        } else {
            "What you remember about the user (saved earlier with the remember tool):\n" +
                memories.joinToString("\n") { "- ${it.text}" }
        }
        val memoryBlock = TextBlockParam.builder()
            .text(memoryText)
            .cacheControl(CacheControlEphemeral.builder().build())
            .build()
        return listOf(base, memoryBlock)
    }

    private fun parseInput(value: JsonValue): JsonObject? =
        runCatching { toJson(value.convert(Any::class.java)) as? JsonObject }.getOrNull()

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        is List<*> -> JsonArray(value.map(::toJson))
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    private fun toolResult(id: String, output: ToolOutput): ToolResultBlockParam {
        val builder = ToolResultBlockParam.builder().toolUseId(id).isError(output.isError)
        return if (output.jpegBase64 == null) {
            builder.content(output.text).build()
        } else {
            builder.contentOfBlocks(
                listOf(
                    ToolResultBlockParam.Content.Block.ofText(output.text),
                    ToolResultBlockParam.Content.Block.ofImage(
                        Base64ImageSource.builder()
                            .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                            .data(output.jpegBase64)
                            .build()
                    ),
                )
            ).build()
        }
    }
}
