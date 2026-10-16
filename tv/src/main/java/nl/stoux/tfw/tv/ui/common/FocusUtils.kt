package nl.stoux.tfw.tv.ui.common

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Requests focus once [requester] is attached to a composed node. Lazy list items (or freshly
 * shown content) need a frame or two before they can take focus, so retry for a few frames.
 */
suspend fun requestFocusWhenAttached(requester: FocusRequester, frames: Int = 5): Boolean {
    repeat(frames) {
        withFrameNanos { }
        if (runCatching { requester.requestFocus() }.getOrDefault(false)) return true
    }
    return false
}
