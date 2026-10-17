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
import com.armaldro.essence.assist.AssistService
import com.armaldro.essence.data.AgentCard
import com.armaldro.essence.ui.agent.AgentCardEditor
import com.armaldro.essence.ui.agent.AgentCards
import com.armaldro.essence.ui.agent.AgentViewModel
import com.armaldro.essence.ui.components.InkButton
import com.armaldro.essence.ui.components.InkDialog
import com.armaldro.essence.ui.settings.SettingsScreen
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

enum class Page { Home, Apps, Settings }

@Composable
fun LauncherRoot(
    viewModel: HomeViewModel,
    agentViewModel: AgentViewModel,
    page: Page,
    onPageChange: (Page) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cards by agentViewModel.cards.collectAsStateWithLifecycle()
    val refreshing by agentViewModel.refreshing.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var editing by remember { mutableStateOf<AgentCard?>(null) }
    var creating by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    BackHandler(enabled = page == Page.Apps) {
        viewModel.setQuery("")
        onPageChange(Page.Home)
    }

    val launch: (AppEntry) -> Unit = { app ->
        viewModel.launch(app)
        viewModel.setQuery("")
        onPageChange(Page.Home)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // No transitions: e-ink screens swap pages in one refresh.
        when (page) {
            Page.Apps -> AppDrawer(
                state = state,
                onQueryChange = viewModel::setQuery,
                onLaunch = launch,
                onLongPress = { menuFor = it },
                onClose = { viewModel.setQuery(""); onPageChange(Page.Home) },
            )
            Page.Settings -> SettingsScreen(agentViewModel, onClose = { onPageChange(Page.Home) })
            Page.Home -> Home(
                favorites = state.favorites,
                onLaunch = launch,
                onLongPress = { menuFor = it },
                onOpenDrawer = { onPageChange(Page.Apps) },
                onPullDown = { expandNotifications(context) },
                onSpeak = {
                    val assistant = AssistService.instance
                    if (assistant != null) assistant.listen()
                    else notice = "Turn on screen access for the assistant in Settings first."
                },
                onSettings = { onPageChange(Page.Settings) },
                cards = {
                    AgentCards(
                        cards = cards,
                        refreshing = refreshing,
                        onRefresh = agentViewModel::refresh,
                        onEdit = { card -> if (card == null) creating = true else editing = card },
                    )
                },
            )
        }
    }

    if (creating || editing != null) {
        AgentCardEditor(
            card = editing,
            onSave = { card ->
                agentViewModel.saveCard(card, refreshAfter = editing?.prompt != card.prompt)
                creating = false; editing = null
            },
            onDelete = { card -> agentViewModel.deleteCard(card.id); editing = null },
            onDismiss = { creating = false; editing = null },
        )
    }

    notice?.let { message ->
        InkDialog(onDismiss = { notice = null }) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Row {
                InkButton("Open settings") { notice = null; onPageChange(Page.Settings) }
                Spacer(Modifier.weight(1f))
                InkButton("OK") { notice = null }
            }
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
    onSpeak: () -> Unit,
    onSettings: () -> Unit,
    cards: @Composable () -> Unit,
) {
    // Swipes the scrolling middle can't use (it is already at its end) still open the drawer or the shade.
    val overscroll = remember {
        object : NestedScrollConnection {
            var total = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) total += available.y
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                when {
                    total < -SWIPE_THRESHOLD -> onOpenDrawer()
                    total > SWIPE_THRESHOLD -> onPullDown()
                }
                total = 0f
                return Velocity.Zero
            }
        }
    }

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
        Column(
            Modifier
                .weight(1f)
                .nestedScroll(overscroll)
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp)
        ) {
            cards()
            Spacer(Modifier.height(24.dp))
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
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth()) {
            BottomAction("↑  ALL APPS", onOpenDrawer)
            Spacer(Modifier.weight(1f))
            BottomAction("✦  SPEAK", onSpeak)
            Spacer(Modifier.weight(1f))
            BottomAction("SETTINGS", onSettings)
        }
    }
}

@Composable
private fun BottomAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .combinedClickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
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
