package nl.stoux.tfw.feature.player.util


fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    if (totalSec <= 0) return "00:00"
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}