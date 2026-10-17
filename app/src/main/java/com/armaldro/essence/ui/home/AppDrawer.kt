package com.armaldro.essence.ui.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.armaldro.essence.data.AppEntry
import kotlinx.coroutines.launch

/** A–Z list of every app, with a search line on top and a letter rail on the right edge. */
@Composable
fun AppDrawer(
    state: HomeState,
    onQueryChange: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    val apps = state.filtered
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Row index of the first app for each letter, used by the rail.
    val letterIndex = remember(apps) {
        buildMap { apps.forEachIndexed { i, app -> putIfAbsent(app.letter, i) } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(start = 28.dp, top = 24.dp)
    ) {
        Row(Modifier.padding(end = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { apps.firstOrNull()?.let(onLaunch) }),
                modifier = Modifier.weight(1f),
                decorationBox = { field ->
                    if (state.query.isEmpty()) {
                        Text(
                            "Search",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    field()
                },
            )
            Text(
                "CLOSE",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .combinedClickable(onClick = onClose)
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 12.dp, end = 28.dp),
        )

        Row(Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(apps, key = { it.key }) { app ->
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onLongClick = { onLongPress(app) }) { onLaunch(app) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
            if (state.query.isBlank() && letterIndex.size > 1) {
                LetterRail(
                    letters = letterIndex.keys.toList(),
                    onLetter = { letter ->
                        letterIndex[letter]?.let { scope.launch { listState.scrollToItem(it) } }
                    },
                    modifier = Modifier.fillMaxHeight().width(36.dp),
                )
            }
        }
    }
}

@Composable
private fun LetterRail(letters: List<Char>, onLetter: (Char) -> Unit, modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf<Char?>(null) }
    var height by remember { mutableIntStateOf(1) }

    fun pick(y: Float) {
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
        if (letters[i] != active) {
            active = letters[i]
            onLetter(letters[i])
        }
    }

    Column(
        modifier
            .padding(vertical = 8.dp)
            .pointerInput(letters) { detectTapGestures { pick(it.y) } }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { pick(it.y) },
                    onDragEnd = { active = null },
                    onDragCancel = { active = null },
                ) { change, _ -> pick(change.position.y) }
            }
            .onSizeChanged { height = it.height.coerceAtLeast(1) },
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        letters.forEach { letter ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = if (letter == active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

