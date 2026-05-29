package nl.stoux.tfw.core.common.device

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import nl.stoux.tfw.core.common.util.encryptedPrefs
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a persistent device/client ID for this installation.
 * Generated once on first launch and stored in EncryptedSharedPreferences.
 */
@Singleton
class DeviceIdManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy { context.encryptedPrefs("tfw_device_prefs") }

    val clientId: String by lazy {
        prefs.getString(KEY_CLIENT_ID, null) ?: run {
            val generated = "TFW-ANDROID-${UUID.randomUUID()}"
            prefs.edit().putString(KEY_CLIENT_ID, generated).apply()
            generated
        }
    }

    companion object {
        private const val KEY_CLIENT_ID = "client_id"
    }
}
