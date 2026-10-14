package nl.stoux.tfw.feature.player.util

import android.text.format.DateUtils
import java.time.OffsetDateTime

/**
 * "5 min. ago" style label for a server ISO8601 timestamp (Laravel `toIso8601String`).
 * Returns null when [iso] is missing or unparseable.
 */
fun formatRelativeIso(iso: String?, nowMs: Long = System.currentTimeMillis()): String? {
    if (iso.isNullOrBlank()) return null
    val millis = runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull() ?: return null
    return DateUtils.getRelativeTimeSpanString(
        millis,
        nowMs,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    if (totalSec <= 0) return "00:00"
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}