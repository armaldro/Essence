package com.armaldro.essence.ui.settings

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armaldro.essence.assist.AssistService
import com.armaldro.essence.data.DEFAULT_MODEL
import com.armaldro.essence.ui.agent.AgentViewModel
import com.armaldro.essence.ui.components.InkButton
import com.armaldro.essence.ui.components.InkField

@Composable
fun SettingsScreen(viewModel: AgentViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val hasKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()

    var keyDraft by remember { mutableStateOf("") }
    var modelDraft by remember(model) { mutableStateOf(model) }
    var memoryDraft by remember { mutableStateOf("") }
    var serviceOn by remember { mutableStateOf(false) }
    var micGranted by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        serviceOn = isAssistEnabled(context)
        micGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }

    BackHandler(onBack = onClose)

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Row {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            InkButton("Close", onClick = onClose)
        }

        Section("Claude")
        Text(
            if (hasKey) "An API key is saved, encrypted on this phone." else "Add an API key from console.anthropic.com to turn on the agents.",
            style = MaterialTheme.typography.bodyLarge,
        )
        InkField("API key", keyDraft, { keyDraft = it }, placeholder = if (hasKey) "•••• saved" else "sk-ant-…", secret = true)
        InkField("Model", modelDraft, { modelDraft = it }, placeholder = DEFAULT_MODEL)
        Row {
            InkButton("Save", enabled = keyDraft.isNotBlank() || modelDraft != model) {
                if (keyDraft.isNotBlank()) viewModel.setApiKey(keyDraft)
                if (modelDraft != model) viewModel.setModel(modelDraft)
                keyDraft = ""
            }
            Spacer(Modifier.weight(1f))
            if (hasKey) InkButton("Remove key") { viewModel.setApiKey("") }
        }

        Section("Assistant")
        Text(
            "Give Essence a spoken or typed command and it operates your phone to do it. It reads the screen and " +
                "taps, types, scrolls and opens apps. It asks before sending, buying, deleting or changing settings, " +
                "and ■ stops it at any time. Screen content is sent to Claude only while a command runs.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        StatusRow("Screen access", if (serviceOn) "ON" else "OFF") {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        if (!serviceOn) {
            Text(
                "In Accessibility, open Essence assistant and turn it on. If Android says the setting is restricted, " +
                    "go to App info → ⋮ → Allow restricted settings first.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusRow("Microphone", if (micGranted) "ALLOWED" else "ALLOW") {
            if (!micGranted) micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        Section("Memory")
        Text(
            if (memories.isEmpty()) "Nothing yet. Claude saves facts here when you ask it to remember something, or you can add them."
            else "Claude sees these in every request. Tap × to forget one.",
            style = MaterialTheme.typography.bodyLarge,
        )
        memories.forEach { memory ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(memory.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    "×",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.combinedClickable { viewModel.deleteMemory(memory.id) }.padding(start = 16.dp),
                )
            }
        }
        InkField("Add a memory", memoryDraft, { memoryDraft = it }, placeholder = "I prefer short, friendly replies.")
        InkButton("Remember", enabled = memoryDraft.isNotBlank()) {
            viewModel.addMemory(memoryDraft)
            memoryDraft = ""
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(28.dp))
    Text(title.uppercase(), style = MaterialTheme.typography.labelLarge)
    HorizontalDivider(Modifier.padding(top = 6.dp, bottom = 12.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun StatusRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

fun isAssistEnabled(context: Context): Boolean {
    if (AssistService.instance != null) return true
    val manager = context.getSystemService(AccessibilityManager::class.java)
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}
