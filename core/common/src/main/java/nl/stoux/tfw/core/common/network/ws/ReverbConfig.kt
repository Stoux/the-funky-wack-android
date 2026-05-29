package nl.stoux.tfw.core.common.network.ws

import nl.stoux.tfw.core.common.BuildConfig

/**
 * Connection settings for the Laravel Reverb server. Values come from
 * `secrets.properties` (debug) or `secrets.prod.properties` (release)
 * via `core/common/build.gradle.kts` buildConfigField wiring.
 *
 * Reverb speaks the Pusher protocol, so pusher-java-client connects with
 * the same options structure. APP_KEY is public — real auth happens
 * server-side through /api/broadcast/auth with the Sanctum bearer.
 */
object ReverbConfig {
    val APP_KEY: String = BuildConfig.REVERB_APP_KEY
    val HOST: String = BuildConfig.REVERB_HOST
    val PORT: Int = BuildConfig.REVERB_PORT
    val USE_TLS: Boolean = BuildConfig.REVERB_SCHEME.equals("https", ignoreCase = true)
    const val ACTIVITY_TIMEOUT_MS = 120_000L
    val AUTH_URL: String = "https://tfw.stoux.nl/api/broadcast/auth"

    /** True when all required values are present — listen-along no-ops otherwise. */
    val isConfigured: Boolean
        get() = APP_KEY.isNotBlank() && HOST.isNotBlank()
}
