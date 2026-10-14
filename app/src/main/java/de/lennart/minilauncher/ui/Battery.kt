package de.lennart.minilauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

data class BatteryState(val level: Float, val charging: Boolean)

/** Current battery level (0..1) and charging state, updated while in composition. */
@Composable
fun rememberBatteryState(): BatteryState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(BatteryState(level = 1f, charging = false)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                state = intent.toBatteryState()
            }
        }
        // ACTION_BATTERY_CHANGED is sticky, so registering returns the current state right away.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let { state = it.toBatteryState() }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

private fun Intent.toBatteryState(): BatteryState {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return BatteryState(
        level = if (level >= 0 && scale > 0) (level.toFloat() / scale).coerceIn(0f, 1f) else 1f,
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL,
    )
}
