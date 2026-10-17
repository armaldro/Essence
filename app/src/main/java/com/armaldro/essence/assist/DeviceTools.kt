package com.armaldro.essence.assist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.armaldro.essence.agent.AgentTool
import com.armaldro.essence.agent.ToolOutput
import com.armaldro.essence.agent.enumProp
import com.armaldro.essence.agent.int
import com.armaldro.essence.agent.intProp
import com.armaldro.essence.agent.string
import com.armaldro.essence.agent.stringListProp
import com.armaldro.essence.agent.stringProp
import com.armaldro.essence.data.AppRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/** Tools that let Claude see and operate the phone through the accessibility service. */
class DeviceTools(
    private val service: AccessibilityService,
    private val apps: AppRepository,
    private val askUser: suspend (question: String, options: List<String>) -> String?,
) {
    private val reader = ScreenReader(service.packageName)
    private val mainExecutor: Executor get() = ContextCompat.getMainExecutor(service)

    private fun screenBounds(): Rect {
        val metrics = service.resources.displayMetrics
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private suspend fun snapshot(): String = withContext(Dispatchers.Main) {
        reader.describe(service.rootInActiveWindow, screenBounds())
    }

    /** Waits for the UI to settle after an action, then returns the new screen. */
    private suspend fun afterAction(result: String): ToolOutput {
        delay(SETTLE_MS)
        return ToolOutput("$result\n\nScreen now:\n${snapshot()}")
    }

    private fun idProp() = intProp("Element number from the latest screen outline, e.g. 12 for [12].")

    val tools: List<AgentTool> = listOf(
        AgentTool(
            "read_screen",
            "Read the foreground app as a numbered outline of its elements, with their text, " +
                "states and screen coordinates. Call this first, and whenever you are unsure what is showing.",
        ) { ToolOutput(snapshot()) },

        AgentTool(
            "screenshot",
            "Capture the screen as an image. Use it when read_screen shows too little, such as images, " +
                "canvases, games or maps. Some apps block screenshots.",
        ) { screenshot() },

        AgentTool(
            "tap",
            "Tap an element from the outline. Returns the screen afterwards.",
            mapOf("id" to idProp()), listOf("id"),
        ) { input -> withNode(input.int("id")) { node -> click(node, long = false) } },

        AgentTool(
            "long_press",
            "Long-press an element from the outline. Returns the screen afterwards.",
            mapOf("id" to idProp()), listOf("id"),
        ) { input -> withNode(input.int("id")) { node -> click(node, long = true) } },

        AgentTool(
            "tap_point",
            "Tap the screen at pixel coordinates. Use only when the target has no element number.",
            mapOf("x" to intProp("X in pixels."), "y" to intProp("Y in pixels.")), listOf("x", "y"),
        ) { input ->
            val x = input.int("x") ?: return@AgentTool ToolOutput("Missing x.", isError = true)
            val y = input.int("y") ?: return@AgentTool ToolOutput("Missing y.", isError = true)
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            gesture(path, 60)
            afterAction("Tapped ($x, $y).")
        },

        AgentTool(
            "type_text",
            "Replace the text in an editable field. This only types; it never sends or submits.",
            mapOf("id" to idProp(), "text" to stringProp("The full text the field should contain.")),
            listOf("id", "text"),
        ) { input ->
            val text = input.string("text") ?: return@AgentTool ToolOutput("Missing text.", isError = true)
            withNode(input.int("id")) { node ->
                withContext(Dispatchers.Main) {
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }.let { ok -> afterAction(if (ok) "Typed the text." else "The field did not accept text.") }
            }
        },

        AgentTool(
            "scroll",
            "Scroll the screen. 'down' reveals content further down, 'up' goes back up, " +
                "'left'/'right' move between pages or carousels.",
            mapOf("direction" to enumProp("Where to move the view.", listOf("up", "down", "left", "right"))),
            listOf("direction"),
        ) { input ->
            val screen = screenBounds()
            val cx = screen.exactCenterX()
            val cy = screen.exactCenterY()
            val dx = screen.width() * 0.35f
            val dy = screen.height() * 0.3f
            val path = Path()
            when (input.string("direction")) {
                "down" -> { path.moveTo(cx, cy + dy); path.lineTo(cx, cy - dy) }
                "up" -> { path.moveTo(cx, cy - dy); path.lineTo(cx, cy + dy) }
                "left" -> { path.moveTo(cx - dx, cy); path.lineTo(cx + dx, cy) }
                "right" -> { path.moveTo(cx + dx, cy); path.lineTo(cx - dx, cy) }
                else -> return@AgentTool ToolOutput("Unknown direction.", isError = true)
            }
            gesture(path, 350)
            afterAction("Scrolled ${input.string("direction")}.")
        },

        AgentTool(
            "press_key",
            "Press a system key.",
            mapOf("key" to enumProp("The key.", listOf("back", "home", "recents", "notifications", "quick_settings"))),
            listOf("key"),
        ) { input ->
            val action = when (input.string("key")) {
                "back" -> AccessibilityService.GLOBAL_ACTION_BACK
                "home" -> AccessibilityService.GLOBAL_ACTION_HOME
                "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
                "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                else -> return@AgentTool ToolOutput("Unknown key.", isError = true)
            }
            withContext(Dispatchers.Main) { service.performGlobalAction(action) }
            afterAction("Pressed ${input.string("key")}.")
        },

        AgentTool(
            "open_app",
            "Open an installed app by its name as shown in the launcher, e.g. \"WhatsApp\" or \"Chrome\".",
            mapOf("name" to stringProp("App name.")), listOf("name"),
        ) { input ->
            val name = input.string("name")?.trim().orEmpty()
            val all = apps.apps.first()
            val match = all.firstOrNull { it.label.equals(name, ignoreCase = true) }
                ?: all.firstOrNull { it.label.contains(name, ignoreCase = true) }
                ?: return@AgentTool ToolOutput(
                    "No app called \"$name\". Installed apps: ${all.joinToString { it.label }}", isError = true
                )
            withContext(Dispatchers.Main) { apps.launch(match) }
            delay(LAUNCH_MS)
            afterAction("Opened ${match.label}.")
        },

        AgentTool(
            "wait",
            "Wait for something to load, then read the screen again.",
            mapOf("seconds" to intProp("1 to 10 seconds.")), listOf("seconds"),
        ) { input ->
            delay((input.int("seconds") ?: 2).coerceIn(1, 10) * 1000L)
            ToolOutput("Screen now:\n${snapshot()}")
        },

        AgentTool(
            "ask_user",
            "Ask the user a question and wait for their answer. Use it to confirm before any step that sends, " +
                "posts, buys, deletes or changes an account or setting, showing the exact content; " +
                "to let them pick between reply drafts; or when the request is ambiguous.",
            mapOf(
                "question" to stringProp("The question, including the exact text you plan to send if confirming."),
                "options" to stringListProp("2 to 4 short answers the user can tap, e.g. [\"Send\", \"Cancel\"]."),
            ),
            listOf("question", "options"),
        ) { input ->
            val question = input.string("question").orEmpty()
            val options = (input["options"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
            val answer = askUser(question, options.ifEmpty { listOf("Yes", "No") })
            ToolOutput(if (answer == null) "The user dismissed the question without answering." else "The user chose: $answer")
        },
    )

    private suspend fun withNode(id: Int?, block: suspend (AccessibilityNodeInfo) -> ToolOutput): ToolOutput {
        val node = id?.let(reader::node)
            ?: return ToolOutput("No element [$id] in the latest outline. Call read_screen again.", isError = true)
        return block(node)
    }

    private suspend fun click(node: AccessibilityNodeInfo, long: Boolean): ToolOutput {
        val action = if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        val done = withContext(Dispatchers.Main) {
            var target: AccessibilityNodeInfo? = node
            while (target != null) {
                val able = if (long) target.isLongClickable else target.isClickable
                if (able) return@withContext target.performAction(action)
                target = target.parent
            }
            false
        }
        if (!done) {
            // Nothing in the tree accepts the click, so tap its center instead.
            val bounds = Rect().also(node::getBoundsInScreen)
            val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
            gesture(path, if (long) 800 else 60)
        }
        return afterAction(if (long) "Long-pressed." else "Tapped.")
    }

    private suspend fun gesture(path: Path, durationMs: Long): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }
            if (!service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), callback, null)) {
                cont.resume(false)
            }
        }
    }

    private suspend fun screenshot(): ToolOutput {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ToolOutput("Screenshots need Android 11 or newer.", isError = true)
        val result = CompletableDeferred<Bitmap?>()
        withContext(Dispatchers.Main) {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(shot: AccessibilityService.ScreenshotResult) {
                    val hardware = Bitmap.wrapHardwareBuffer(shot.hardwareBuffer, shot.colorSpace)
                    result.complete(hardware?.copy(Bitmap.Config.ARGB_8888, false))
                    shot.hardwareBuffer.close()
                }
                override fun onFailure(errorCode: Int) { result.complete(null) }
            })
        }
        val bitmap = result.await() ?: return ToolOutput("This screen can't be captured (the app may block screenshots).", isError = true)
        return withContext(Dispatchers.Default) {
            val scale = SHOT_MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else bitmap
            val bytes = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
            ToolOutput(
                "Screenshot ${scaled.width}x${scaled.height}. Screen coordinates are this image's ×${"%.2f".format(1 / minOf(scale, 1f))}.",
                jpegBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            )
        }
    }

    private companion object {
        const val SETTLE_MS = 700L
        const val LAUNCH_MS = 1200L
        const val SHOT_MAX_SIDE = 1280
    }
}
