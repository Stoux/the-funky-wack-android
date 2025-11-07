package nl.stoux.tfw.feature.player.util

import android.content.Context
import android.content.Intent

fun shareLiveset(context: Context, livesetId: Long, timestamp: Long? = null) {
    val timeParam = if (timestamp != null && timestamp > 0) "&t=${formatTime(timestamp)}" else ""
    val url = "https://tfw.stoux.nl/#liveset=$livesetId$timeParam"
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }

    val chooser = Intent.createChooser(sendIntent, null)
    context.startActivity(chooser)
}