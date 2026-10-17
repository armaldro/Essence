package com.armaldro.essence.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armaldro.essence.data.AppEntry
import com.armaldro.essence.data.FavoritesStore

@Composable
fun LauncherRoot(viewModel: HomeViewModel, drawerOpen: Boolean, onDrawerOpenChange: (Boolean) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    val context = LocalContext.current

    BackHandler(enabled = drawerOpen) {
        viewModel.setQuery("")
        onDrawerOpenChange(false)
    }

    val launch: (AppEntry) -> Unit = { app ->
        viewModel.launch(app)
        viewModel.setQuery("")
        onDrawerOpenChange(false)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // No transition: e-ink screens swap pages in one refresh.
        if (drawerOpen) {
            AppDrawer(
                state = state,
                onQueryChange = viewModel::setQuery,
                onLaunch = launch,
                onLongPress = { menuFor = it },
                onClose = { viewModel.setQuery(""); onDrawerOpenChange(false) },
            )
        } else {
            Home(
                favorites = state.favorites,
                onLaunch = launch,
                onLongPress = { menuFor = it },
                onOpenDrawer = { onDrawerOpenChange(true) },
                onPullDown = { expandNotifications(context) },
            )
        }
    }

    menuFor?.let { app ->
        AppMenu(
            app = app,
            isFavorite = app.key in state.favoriteKeys,
            favoritesFull = state.favoriteKeys.size >= FavoritesStore.MAX_FAVORITES,
            onToggleFavorite = { viewModel.toggleFavorite(app); menuFor = null },
            onInfo = { viewModel.openDetails(app); menuFor = null },
            onUninstall = {
                context.startActivity(
                    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                menuFor = null
            },
            onDismiss = { menuFor = null },
        )
    }
}

@Composable
private fun Home(
    favorites: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onOpenDrawer: () -> Unit,
    onPullDown: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total < -SWIPE_THRESHOLD -> onOpenDrawer()
                            total > SWIPE_THRESHOLD -> onPullDown()
                        }
                    },
                ) { _, dragAmount -> total += dragAmount }
            }
    ) {
        Glance(Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        if (favorites.isEmpty()) {
            Text(
                "Long-press any app in the list\nto pin it here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        favorites.forEach { app ->
            Text(
                text = app.label,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onLongClick = { onLongPress(app) }) { onLaunch(app) }
                    .padding(vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "↑  ALL APPS",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable { onOpenDrawer() }
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun AppMenu(
    app: AppEntry,
    isFavorite: Boolean,
    favoritesFull: Boolean,
    onToggleFavorite: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                when {
                    isFavorite -> MenuItem("REMOVE FROM HOME", onToggleFavorite)
                    favoritesFull -> MenuItem("HOME IS FULL (${FavoritesStore.MAX_FAVORITES})", null)
                    else -> MenuItem("PIN TO HOME", onToggleFavorite)
                }
                MenuItem("APP INFO", onInfo)
                MenuItem("UNINSTALL", onUninstall)
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: (() -> Unit)?) {
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.combinedClickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (onClick != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Pulls down the notification shade. There is no public API for this, so it goes through reflection. */
@SuppressLint("WrongConstant")
private fun expandNotifications(context: Context) {
    runCatching {
        val service = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
    }
}

private const val SWIPE_THRESHOLD = 120f
