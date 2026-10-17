package com.armaldro.essence.agent

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** What a tool hands back to Claude: text, optionally with a JPEG screenshot. */
data class ToolOutput(val text: String, val jpegBase64: String? = null, val isError: Boolean = false)

/** A client-side tool that Claude can call during an agent run. */
class AgentTool(
    val name: String,
    description: String,
    properties: Map<String, Map<String, Any>> = emptyMap(),
    required: List<String> = emptyList(),
    val run: suspend (JsonObject) -> ToolOutput,
) {
    val definition: Tool = Tool.builder()
        .name(name)
        .description(description)
        .inputSchema(
            Tool.InputSchema.builder()
                .properties(
                    Tool.InputSchema.Properties.builder().apply {
                        properties.forEach { (key, schema) -> putAdditionalProperty(key, JsonValue.from(schema)) }
                    }.build()
                )
                .required(required)
                .build()
        )
        .build()
}

fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

/** JSON-schema snippets for tool properties. */
fun stringProp(description: String) = mapOf("type" to "string", "description" to description)
fun intProp(description: String) = mapOf("type" to "integer", "description" to description)
fun enumProp(description: String, values: List<String>) =
    mapOf("type" to "string", "description" to description, "enum" to values)
fun stringListProp(description: String) =
    mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to description)
