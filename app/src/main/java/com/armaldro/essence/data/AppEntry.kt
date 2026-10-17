package com.armaldro.essence.data

import android.content.ComponentName
import android.os.UserHandle

/** One launchable activity, as shown in the app list. */
data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
) {
    /** Stable key used for favorites and list items. */
    val key: String = "${component.flattenToString()}#${user.hashCode()}"

    /** Index letter for the A–Z rail; anything not A–Z goes under '#'. */
    val letter: Char = label.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'
}
