package nl.stoux.tfw.auth

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import retrofit2.HttpException

/** A passkey step failed with a message that's fit to show the user. */
class PasskeyException(message: String) : Exception(message)

/**
 * Thin wrapper around Credential Manager for passkeys. Needs an Activity context, Credential
 * Manager shows its own bottom sheet. Both calls return null when the user cancels.
 *
 * The request JSON comes straight from the backend's `options` (WebAuthn JSON, base64url).
 * Credential Manager only accepts it when tfw.stoux.nl's assetlinks.json lists this app
 * under `delegate_permission/common.get_login_creds`.
 */
class PasskeyCredentials(private val context: Context) {
    private val manager = CredentialManager.create(context)

    /** Sign in with a passkey; returns the authenticationResponseJson to send to the backend. */
    suspend fun signIn(requestJson: String): String? {
        val request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson)))
        return try {
            val credential = manager.getCredential(context, request).credential
            (credential as? PublicKeyCredential)?.authenticationResponseJson
                ?: throw PasskeyException("Unexpected credential type")
        } catch (e: GetCredentialCancellationException) {
            null
        } catch (e: NoCredentialException) {
            throw PasskeyException("No passkey for The Funky Wack found on this device")
        } catch (e: GetCredentialException) {
            throw PasskeyException(e.errorMessage?.toString() ?: "Passkey sign in failed")
        }
    }

    /** Create a passkey; returns the registrationResponseJson to send to the backend. */
    suspend fun create(requestJson: String): String? {
        return try {
            val response = manager.createCredential(context, CreatePublicKeyCredentialRequest(requestJson))
            (response as? CreatePublicKeyCredentialResponse)?.registrationResponseJson
                ?: throw PasskeyException("Unexpected credential type")
        } catch (e: CreateCredentialCancellationException) {
            null
        } catch (e: CreateCredentialException) {
            throw PasskeyException(e.errorMessage?.toString() ?: "Couldn't create a passkey")
        }
    }

    companion object {
        /** User-facing message for a failed passkey step, incl. backend errors. */
        fun messageFor(e: Throwable): String = when (e) {
            is PasskeyException -> e.message ?: "Passkey failed"
            is HttpException -> when (e.code()) {
                401 -> "Passkey not recognized"
                422 -> "The passkey request expired, try again"
                429 -> "Too many attempts, try again in a minute"
                else -> "Something went wrong (HTTP ${e.code()})"
            }
            else -> "Couldn't reach the server"
        }
    }
}
