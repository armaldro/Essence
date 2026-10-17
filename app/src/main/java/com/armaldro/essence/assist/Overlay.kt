package com.armaldro.essence.assist

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible

/**
 * The assistant's on-screen pieces, drawn as accessibility overlays in the e-ink style:
 * - a small bubble on the right edge (✦ to start, ■ to stop a run),
 * - a status strip at the top that touches pass through, so it never blocks the agent's taps,
 * - a panel at the bottom for listening, typing, questions and results.
 */
class Overlay(
    private val context: Context,
    private val onBubble: () -> Unit,
    private val onSpeak: () -> Unit,
    private val onSubmit: (String) -> Unit,
    private val onClose: () -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    private val paper = if (night) 0xFF0F0F0E.toInt() else 0xFFF2F0EB.toInt()
    private val ink = if (night) 0xFFE6E3DC.toInt() else 0xFF141414.toInt()
    private val muted = if (night) 0xFF8E8B85.toInt() else 0xFF5E5C58.toInt()

    private val bubble = label("✦", 20f, Typeface.SERIF).apply {
        gravity = Gravity.CENTER
        background = framed()
        setOnClickListener { onBubble() }
    }
    private val strip = label("", 12f, Typeface.MONOSPACE).apply {
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = framed()
        maxLines = 3
    }

    private val title = label("", 12f, Typeface.MONOSPACE).apply { setTextColor(muted) }
    private val body = label("", 18f, Typeface.SERIF)
    private val options = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val input = EditText(context).apply {
        hint = "Or type a command"
        setTextColor(ink)
        setHintTextColor(muted)
        typeface = Typeface.SERIF
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        background = null
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_ACTION_GO
        setOnEditorActionListener { v, _, _ ->
            val text = v.text.toString().trim()
            if (text.isNotEmpty()) { v.text = ""; onSubmit(text) }
            true
        }
    }
    private val speakButton = action("SPEAK") { onSpeak() }
    private val closeButton = action("CLOSE") { onClose() }

    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(20), dp(24), dp(16))
        background = framed()
        addView(title)
        addView(ScrollView(context).apply {
            addView(body)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        })
        addView(options)
        addView(input)
        addView(rule())
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(speakButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(closeButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
    }

    private var bubbleShown = false
    private var stripShown = false
    private var panelShown = false

    fun showBubble(running: Boolean) {
        bubble.text = if (running) "■" else "✦"
        if (!bubbleShown) {
            wm.addView(bubble, params(dp(48), dp(48), Gravity.END or Gravity.TOP, touchable = true, focusable = false).apply {
                y = context.resources.displayMetrics.heightPixels / 3
            })
            bubbleShown = true
        }
    }

    fun setStatus(text: String?) {
        if (text == null) {
            if (stripShown) { wm.removeView(strip); stripShown = false }
            return
        }
        strip.text = text
        if (!stripShown) {
            wm.addView(strip, params(LinearLayoutMatch, LinearLayoutWrap, Gravity.TOP, touchable = false, focusable = false).apply {
                y = dp(32)
            })
            stripShown = true
        }
    }

    /** Shows the panel. With [choices], the user answers by tapping one; the text box is hidden. */
    fun showPanel(heading: String, message: String, choices: List<String> = emptyList(), onChoice: (String) -> Unit = {}, allowInput: Boolean = true) {
        title.text = heading.uppercase()
        body.text = message
        body.isVisible = message.isNotEmpty()
        options.removeAllViews()
        choices.forEach { choice -> options.addView(action(choice.uppercase()) { onChoice(choice) }) }
        input.isVisible = allowInput && choices.isEmpty()
        speakButton.isVisible = choices.isEmpty()
        if (!panelShown) {
            wm.addView(panel, params(LinearLayoutMatch, LinearLayoutWrap, Gravity.BOTTOM, touchable = true, focusable = true))
            panelShown = true
        }
    }

    fun hidePanel() {
        if (panelShown) { wm.removeView(panel); panelShown = false }
    }

    fun removeAll() {
        hidePanel()
        setStatus(null)
        if (bubbleShown) { wm.removeView(bubble); bubbleShown = false }
    }

    private fun params(w: Int, h: Int, gravity: Int, touchable: Boolean, focusable: Boolean) =
        WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
                (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            if (gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.END) x = dp(4)
            if (w == LinearLayoutMatch) { horizontalMargin = 0f }
        }

    private fun label(text: String, sp: Float, face: Typeface) = TextView(context).apply {
        this.text = text
        setTextColor(ink)
        typeface = face
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (face == Typeface.MONOSPACE) letterSpacing = 0.1f
    }

    private fun action(text: String, onClick: () -> Unit) = label(text, 13f, Typeface.MONOSPACE).apply {
        setPadding(0, dp(14), 0, dp(14))
        setOnClickListener { onClick() }
    }

    private fun rule() = View(context).apply {
        setBackgroundColor(ink)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(8) }
    }

    private fun framed() = GradientDrawable().apply {
        setColor(paper)
        setStroke(dp(2), ink)
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val LinearLayoutMatch = WindowManager.LayoutParams.MATCH_PARENT
        const val LinearLayoutWrap = WindowManager.LayoutParams.WRAP_CONTENT
    }
}
