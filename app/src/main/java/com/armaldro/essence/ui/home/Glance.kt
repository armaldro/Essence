package com.armaldro.essence.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


/**
 * Clock, date and battery. Updates once a minute on the system time tick,
 * like an e-ink display that only refreshes when something changes.
 */
@Composable
fun Glance(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var battery by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) battery = level * 100 / scale
                } else {
                    now = LocalDateTime.now()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        now = LocalDateTime.now()
        onDispose { context.unregisterReceiver(receiver) }
    }

    val locale = LocalConfiguration.current.locales[0]
    Column(modifier) {
        Text(
            text = now.format(DateTimeFormatter.ofPattern("HH:mm", locale)),
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)).uppercase(locale),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            battery?.let {
                Text(text = "BATT $it%", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
