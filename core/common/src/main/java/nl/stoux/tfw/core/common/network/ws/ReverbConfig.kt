package nl.stoux.tfw.core.common.network.ws

/**
 * Connection settings for the Laravel Reverb server backing tfw.stoux.nl.
 *
 * Reverb speaks the Pusher protocol, so the official pusher-java-client connects
 * with the same options structure. APP_KEY is public — auth happens server-side
 * via /api/broadcast/auth with the user's Sanctum token.
 */
object ReverbConfig {
    // TODO: replace with the production REVERB_APP_KEY before shipping.
    const val APP_KEY = "tfw-reverb-key"
    const val HOST = "tfw.stoux.nl"
    const val PORT = 443
    const val USE_TLS = true
    const val ACTIVITY_TIMEOUT_MS = 120_000L
    const val AUTH_URL = "https://tfw.stoux.nl/api/broadcast/auth"
}
