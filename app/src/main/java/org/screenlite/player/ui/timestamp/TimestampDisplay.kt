package org.screenlite.player.ui.timestamp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.screenlite.player.data.TimestampState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 10 evenly-spaced hues (36° apart, full saturation). Index = second % 10.
private val secondIndicatorColors = List(10) { i -> Color.hsl(i * 36f, 1f, 0.5f) }

/**
 * Displays a timestamp with a second-indicator square.
 *
 * The colored square cycles through the full hue spectrum once every 60 seconds
 * (each second advances the hue by 6°), making it easy to visually verify the
 * clock is ticking and to distinguish adjacent seconds at a glance.
 *
 * - When [state] has a timestamp, it is shown.
 * - If [state] is null or doesn't have a timestamp, the local system clock is shown.
 * - If [state] is disabled or timestamp is missing, the indicator and text turn RED.
 *
 * @param state              The current timestamp state from the repository.
 * @param formatter          How the epoch ms value is converted to a display string.
 * @param modifier           Standard Compose modifier.
 * @param style              Text style forwarded to the inner [Text].
 */
@Composable
fun TimestampDisplay(
    state: TimestampState?,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    formatter: (Long) -> String = ::defaultTimestampFormatter,
) {
    val displayMs by produceTimestampMs(state?.timestamp)
    
    // The repository now handles staleness/timeout by emitting isEnabled=false and timestamp=null
    val isDisconnected = state == null || !state.isEnabled || state.timestamp == null

    val second = (displayMs / 1000L % 60L).toInt()
    val indicatorColor = if (isDisconnected) Color.Red else secondIndicatorColors[second % secondIndicatorColors.size]

    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(1f)
                .aspectRatio(1f)
                .background(indicatorColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatter(displayMs),
            style = if (isDisconnected) style.copy(color = Color.Red) else style,
        )
    }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Returns a [State<Long>] that either:
 *  - holds [remoteTimestampMs] unchanged (no coroutine launched), or
 *  - ticks the local clock every 10 ms when remote is null.
 */
@Composable
fun produceTimestampMs(remoteTimestampMs: Long?): State<Long> =
    produceState(
        initialValue = remoteTimestampMs ?: System.currentTimeMillis(),
        key1 = remoteTimestampMs,
    ) {
        if (remoteTimestampMs != null) {
            // Remote value available — just set it once, no polling.
            value = remoteTimestampMs
        } else {
            // No remote value — poll local clock every 10 ms.
            while (true) {
                delay(10L)
                value = System.currentTimeMillis()
            }
        }
    }

/** Default formatter — e.g. "2026-03-28 14:05:23.417" */
fun defaultTimestampFormatter(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S", Locale.getDefault())
        .format(Date(epochMs))
