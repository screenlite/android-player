package org.screenlite.player.ui.timestamp

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Displays a timestamp.
 *
 * - When [remoteTimestampMs] is non-null, it is shown as-is (static — no local polling).
 * - When [remoteTimestampMs] is null, the local system clock is polled every 10 ms.
 *
 * @param remoteTimestampMs  Epoch milliseconds received from the sync server, or null.
 * @param formatter          How the epoch ms value is converted to a display string.
 * @param modifier           Standard Compose modifier.
 * @param style              Text style forwarded to the inner [Text].
 */
@Composable
fun TimestampDisplay(
    remoteTimestampMs: Long?,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    formatter: (Long) -> String = ::defaultTimestampFormatter,
) {
    val displayMs by produceTimestampMs(remoteTimestampMs)

    Text(
        text = formatter(displayMs),
        modifier = modifier,
        style = style,
    )
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Returns a [State<Long>] that either:
 *  - holds [remoteTimestampMs] unchanged (no coroutine launched), or
 *  - ticks the local clock every 10 ms when remote is null.
 *
 * Extracted so it can be reused / tested independently of the Text widget.
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
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        .format(Date(epochMs))