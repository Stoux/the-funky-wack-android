package nl.stoux.tfw.feature.player.waveforms

import android.util.Log
import androidx.collection.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Singleton

@Singleton
class WaveformDownloader @javax.inject.Inject constructor(
    private val api: WaveformApi,
) {

    private val cache = LruCache<String, List<Int>>(5)
    private val serviceIOScope = CoroutineScope(Dispatchers.IO)

    fun loadWaveform(url: String, callback: (List<Int>) -> Unit) {
        // Check if we've cached it before
        val cached = cache.get(url)
        if (cached != null) {
            callback(cached)
            return
        }

        // Download it
        serviceIOScope.launch {
            try {
                val resp = api.getWaveform(url)
                val data = resp.data
                cache.put(url, data)

                // Return on main thread for UI safety
                withContext(Dispatchers.Main) {
                    callback(data)
                }
            } catch (t: Throwable) {
                // TODO: Consider better error reporting/callback
                Log.e("WaveformDownloader", "Error downloading waveform", t)
                // Swallow for now; do not crash UI

            }
        }
    }

}