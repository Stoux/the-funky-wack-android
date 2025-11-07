package nl.stoux.tfw

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.repository.EditionRepository

/**
 * Holds deep-link state and resolves liveset details for UI.
 * No persistence: each incoming link can trigger the dialog again.
 */
@HiltViewModel
class AppLinkViewModel @Inject constructor(
    private val editionRepository: EditionRepository,
) : ViewModel() {

    data class DeepLink(val livesetId: Long, val positionMs: Long?)
    data class ResolvedDeepLink(val deepLink: DeepLink, val liveset: LivesetWithDetails)

    private val _resolved = MutableStateFlow<ResolvedDeepLink?>(null)
    val resolved: StateFlow<ResolvedDeepLink?> = _resolved.asStateFlow()

    fun handleIncomingDeepLink(uri: Uri) {
        val deepLink = parseDeepLink(uri)

        if (deepLink == null) {
            _resolved.value = null
            return
        }
        // Resolve liveset and expose if playable
        CoroutineScope(Dispatchers.IO).launch {
            val lwd = runCatching { editionRepository.findLiveset(deepLink.livesetId).first() }.getOrNull()
            _resolved.value = if (lwd != null && hasPlayable(lwd)) {
                ResolvedDeepLink(deepLink, lwd)
            } else {
                null
            }
        }
    }

    fun shouldLetBrowserHandle(uri: Uri): Boolean {
        val path = uri.path.orEmpty()
        return path.startsWith("/admin") || path.startsWith("/login")
    }

    private fun parseDeepLink(uri: Uri): DeepLink? {
        // Prefer fragment params like: #liveset=39&t=09:18, but also accept query params ?liveset=..&t=..
        val params: Map<String, String> = run {
            val frag = uri.fragment
            if (!frag.isNullOrBlank()) {
                frag.split('&')
                    .mapNotNull { part ->
                        val i = part.indexOf('=')
                        if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
                    }
                    .toMap()
            } else {
                // Fallback to query parameters
                uri.queryParameterNames.associateWith { key -> uri.getQueryParameter(key).orEmpty() }
            }
        }

        if (params.isEmpty()) return null
        val livesetId = params["liveset"]?.toLongOrNull() ?: return null
        val tRaw = params["t"]
        val positionMs = tRaw?.let { parseTimestampToMsSafe(it) }
        return DeepLink(livesetId = livesetId, positionMs = positionMs)
    }

    // Strict time regex: supports mm:ss or hh:mm:ss with mm/ss in 00-59
    private val TIME_REGEX = Regex("^(?:(\\d{1,2}):)?([0-5]\\d):([0-5]\\d)$")

    private fun parseTimestampToMsSafe(input: String): Long? {
        // First try formatted hh:mm:ss or mm:ss
        TIME_REGEX.matchEntire(input)?.let { m ->
            val hours = m.groups[1]?.value?.toLongOrNull() ?: 0L
            val minutes = m.groups[2]?.value?.toLongOrNull() ?: return null
            val seconds = m.groups[3]?.value?.toLongOrNull() ?: return null
            val totalSeconds = hours * 3600 + minutes * 60 + seconds
            return (totalSeconds * 1000L).coerceAtMost(Long.MAX_VALUE)
        }
        // Fallback: plain seconds (1–7 digits) to avoid excessively long inputs
        if (input.matches(Regex("^[0-9]{1,7}$"))) {
            val sec = input.toLongOrNull() ?: return null
            return (sec * 1000L).coerceAtMost(Long.MAX_VALUE)
        }
        return null
    }

    fun markConsumed() {
        _resolved.value = null
    }

    private fun hasPlayable(lwd: LivesetWithDetails): Boolean {
        val ls = lwd.liveset
        return !ls.lqUrl.isNullOrBlank() || !ls.hqUrl.isNullOrBlank() || !ls.losslessUrl.isNullOrBlank()
    }
}
