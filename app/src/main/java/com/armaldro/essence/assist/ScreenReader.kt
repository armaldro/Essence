package com.armaldro.essence.assist

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Turns the accessibility tree of the foreground app into a numbered text outline
 * that Claude can read, and remembers which number maps to which node.
 */
class ScreenReader(private val ownPackage: String) {
    private val nodes = mutableMapOf<Int, AccessibilityNodeInfo>()

    fun node(id: Int): AccessibilityNodeInfo? = nodes[id]

    fun describe(root: AccessibilityNodeInfo?, screen: Rect): String {
        nodes.clear()
        if (root == null) return "No readable window is in the foreground."
        val lines = mutableListOf<String>()
        lines += "App: ${root.packageName}"
        walk(root, screen, depth = 0, lines)
        if (lines.size == 1) lines += "(This screen exposes no readable elements. Use screenshot to look at it.)"
        if (lines.size > MAX_LINES) {
            return (lines.take(MAX_LINES) + "… (${lines.size - MAX_LINES} more elements; scroll or use screenshot)").joinToString("\n")
        }
        return lines.joinToString("\n")
    }

    private fun walk(node: AccessibilityNodeInfo, screen: Rect, depth: Int, out: MutableList<String>) {
        if (depth > MAX_DEPTH || node.packageName == ownPackage || !node.isVisibleToUser) return
        val bounds = Rect().also(node::getBoundsInScreen)
        if (!Rect.intersects(bounds, screen)) return

        val text = when {
            node.isPassword -> "••••"
            else -> node.text?.toString()?.trim()?.take(MAX_TEXT)
        }
        val desc = node.contentDescription?.toString()?.trim()?.take(MAX_TEXT)
        val hint = node.hintText?.toString()?.trim()?.take(MAX_TEXT)
        val flags = buildList {
            if (node.isClickable) add("clickable")
            if (node.isLongClickable) add("long-clickable")
            if (node.isEditable) add("editable")
            if (node.isScrollable) add("scrollable")
            if (node.isCheckable) add(if (node.isChecked) "checked" else "unchecked")
            if (node.isSelected) add("selected")
            if (node.isFocused) add("focused")
            if (!node.isEnabled) add("disabled")
            if (node.isPassword) add("password")
        }
        val worthListing = !text.isNullOrEmpty() || !desc.isNullOrEmpty() || node.isClickable ||
            node.isEditable || node.isScrollable || node.isCheckable
        if (worthListing) {
            val id = nodes.size + 1
            nodes[id] = node
            val label = buildString {
                append("[").append(id).append("] ").append(node.className?.toString()?.substringAfterLast('.') ?: "View")
                if (!text.isNullOrEmpty()) append(" \"").append(text).append("\"")
                if (!desc.isNullOrEmpty() && desc != text) append(" desc=\"").append(desc).append("\"")
                if (node.isEditable && !hint.isNullOrEmpty() && hint != text) append(" hint=\"").append(hint).append("\"")
                if (flags.isNotEmpty()) append(" {").append(flags.joinToString(", ")).append("}")
                append(" @").append(bounds.centerX()).append(",").append(bounds.centerY())
            }
            out += "  ".repeat(depth.coerceAtMost(8)) + label
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { walk(it, screen, depth + 1, out) }
        }
    }

    private companion object {
        const val MAX_DEPTH = 40
        const val MAX_LINES = 400
        const val MAX_TEXT = 300
    }
}
