package nl.stoux.tfw.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Key
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    // Credential Manager needs the Activity context for its sheet
    val context = LocalContext.current
    val passkeyCredentials = remember(context) { PasskeyCredentials(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (isLoggedIn && currentUser != null) {
            LoggedInContent(
                userName = currentUser!!.name,
                userEmail = currentUser!!.email,
                deviceCode = uiState.deviceCode,
                isAuthorizingDevice = uiState.isAuthorizingDevice,
                deviceAuthError = uiState.deviceAuthError,
                deviceAuthSuccess = uiState.deviceAuthSuccess,
                onDeviceCodeChanged = viewModel::updateDeviceCode,
                onAuthorizeDevice = viewModel::authorizeDevice,
                onClearDeviceAuthSuccess = viewModel::clearDeviceAuthSuccess,
                onLogout = viewModel::logout,
                passkeyCredentials = passkeyCredentials,
            )
        } else {
            LoginFormContent(
                email = uiState.email,
                password = uiState.password,
                isLoading = uiState.isLoading,
                error = uiState.error,
                onEmailChanged = viewModel::updateEmail,
                onPasswordChanged = viewModel::updatePassword,
                onLogin = viewModel::login,
                onPasskeyLogin = { viewModel.loginWithPasskey(passkeyCredentials) },
            )
        }
    }
}

@Composable
private fun LoggedInContent(
    userName: String,
    userEmail: String,
    deviceCode: String,
    isAuthorizingDevice: Boolean,
    deviceAuthError: String?,
    deviceAuthSuccess: Boolean,
    onDeviceCodeChanged: (String) -> Unit,
    onAuthorizeDevice: () -> Unit,
    onClearDeviceAuthSuccess: () -> Unit,
    onLogout: () -> Unit,
    passkeyCredentials: PasskeyCredentials,
) {
    Text("Profile", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(12.dp))
    Text("Logged in as $userName", style = MaterialTheme.typography.bodyLarge)
    Text(userEmail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(24.dp))

    OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
        Text("Log out")
    }

    Spacer(modifier = Modifier.height(32.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(24.dp))

    PasskeysSection(passkeyCredentials = passkeyCredentials)

    Spacer(modifier = Modifier.height(32.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(24.dp))

    // Link a Device section
    Text("Link a Device", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "Enter the code shown on your TV or car to link it to your account.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = deviceCode,
        onValueChange = onDeviceCodeChanged,
        label = { Text("Device code") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isAuthorizingDevice,
    )

    if (deviceAuthError != null) {
        Text(deviceAuthError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    if (deviceAuthSuccess) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Device linked successfully!", color = MaterialTheme.colorScheme.primary)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = onAuthorizeDevice,
        enabled = !isAuthorizingDevice && deviceCode.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isAuthorizingDevice) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
        } else {
            Text("Link Device")
        }
    }
}

@Composable
private fun LoginFormContent(
    email: String,
    password: String,
    isLoading: Boolean,
    error: String?,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onPasskeyLogin: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    Text("Login", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = email,
        onValueChange = onEmailChanged,
        label = { Text("Email") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        singleLine = true,
        enabled = !isLoading,
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChanged,
        label = { Text("Password") },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = {
            focusManager.clearFocus()
            onLogin()
        }),
        singleLine = true,
        enabled = !isLoading,
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                )
            }
        },
    )

    if (error != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onLogin,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
        } else {
            Text("Login")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = onPasskeyLogin,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Sign in with passkey")
    }

    Spacer(modifier = Modifier.height(16.dp))

    TextButton(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://tfw.stoux.nl/register"))
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Register on tfw.stoux.nl")
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        "No account? No problem. All livesets are free to stream. Log in to unlock favorites, playlists, and listening stats.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
