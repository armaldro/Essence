package com.armaldro.essence.ui.agent

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.armaldro.essence.data.AgentCard
import com.armaldro.essence.ui.components.InkButton
import com.armaldro.essence.ui.components.InkDialog
import com.armaldro.essence.ui.components.InkField
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Claude-powered cards on the home page. Tap a card to run its prompt again; long-press to edit it.
 * Cards only refresh when asked, so the page stays still like e-paper.
 */
@Composable
fun AgentCards(
    cards: List<AgentCard>,
    refreshing: Set<String>,
    onRefresh: (AgentCard) -> Unit,
    onEdit: (AgentCard?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        cards.forEach { card ->
            AgentCardView(card, card.id in refreshing, onClick = { onRefresh(card) }, onLongClick = { onEdit(card) })
        }
        InkButton(if (cards.isEmpty()) "+ Add an agent card" else "+ Card", onClick = { onEdit(null) })
    }
}

@Composable
private fun AgentCardView(card: AgentCard, busy: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Row {
            Text(card.title.uppercase(locale), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                when {
                    busy -> "…"
                    card.updatedAt == 0L -> "TAP TO RUN"
                    else -> Instant.ofEpochMilli(card.updatedAt).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm", locale))
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (card.output.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                card.output,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(Modifier.padding(top = 10.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Create or edit a card. [card] is null for a new one. */
@Composable
fun AgentCardEditor(
    card: AgentCard?,
    onSave: (AgentCard) -> Unit,
    onDelete: (AgentCard) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(card?.title ?: "") }
    var prompt by remember { mutableStateOf(card?.prompt ?: "") }
    InkDialog(onDismiss) {
        Text(if (card == null) "New agent card" else "Edit card", style = MaterialTheme.typography.titleLarge)
        InkField("Title", title, { title = it }, placeholder = "Morning")
        InkField(
            "What should Claude do?", prompt, { prompt = it },
            placeholder = "A short plan for my day and one thing to look forward to.",
            singleLine = false,
        )
        Row {
            InkButton("Save", enabled = title.isNotBlank() && prompt.isNotBlank()) {
                onSave((card ?: AgentCard(title = title, prompt = prompt)).copy(title = title.trim(), prompt = prompt.trim()))
            }
            Spacer(Modifier.width(24.dp))
            if (card != null) InkButton("Delete") { onDelete(card) }
            Spacer(Modifier.weight(1f))
            InkButton("Cancel", onClick = onDismiss)
        }
    }
}
