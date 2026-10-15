package com.almakhedhi.rxreader.feature.auth

import android.app.Activity
import android.content.Context
import android.util.Patterns
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almakhedhi.rxreader.core.auth.AuthError
import com.almakhedhi.rxreader.core.auth.AuthFailure
import com.almakhedhi.rxreader.core.auth.AuthRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

data class AuthUiState(
    val loading: Boolean = false,
    val googleLoading: Boolean = false,
    val error: String? = null,
    val resetSent: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    @ApplicationContext private val context: Context,
    @Named("google-web-client-id") private val googleWebClientId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun clearFeedback() {
        _uiState.update { it.copy(error = null, resetSent = false) }
    }

    fun signIn(email: String, password: String) {
        if (!isValidEmail(email)) return setErrorRes(R.string.auth_error_email)
        if (password.length < PASSWORD_MIN) return setErrorRes(R.string.auth_error_password)
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            repository.signIn(email, password).fold(
                onSuccess = { _uiState.update { it.copy(loading = false) } },
                onFailure = { error ->
                    _uiState.update { it.copy(loading = false, error = error.userMessage()) }
                },
            )
        }
    }

    fun signUp(name: String, email: String, password: String, confirm: String) {
        if (!isValidEmail(email)) return setErrorRes(R.string.auth_error_email)
        if (password.length < PASSWORD_MIN) return setErrorRes(R.string.auth_error_password)
        if (password != confirm) return setErrorRes(R.string.auth_error_mismatch)
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            repository.signUp(name, email, password).fold(
                onSuccess = { _uiState.update { it.copy(loading = false) } },
                onFailure = { error ->
                    _uiState.update { it.copy(loading = false, error = error.userMessage()) }
                },
            )
        }
    }

    fun sendReset(email: String) {
        if (!isValidEmail(email)) return setErrorRes(R.string.auth_error_email)
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, resetSent = false) }
            repository.sendPasswordReset(email).fold(
                onSuccess = { _uiState.update { it.copy(loading = false, resetSent = true) } },
                onFailure = { error ->
                    _uiState.update { it.copy(loading = false, error = error.userMessage()) }
                },
            )
        }
    }

    fun signInWithGoogle(activity: Activity) {
        if (googleWebClientId.isBlank()) {
            return setErrorRes(R.string.auth_error_not_configured)
        }
        viewModelScope.launch {
            _uiState.update { it.copy(googleLoading = true, error = null) }
            try {
                val idToken = requestGoogleIdToken(activity)
                if (idToken == null) {
                    _uiState.update { it.copy(googleLoading = false) }
                    return@launch
                }
                repository.signInWithGoogle(idToken).fold(
                    onSuccess = { _uiState.update { it.copy(googleLoading = false) } },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(googleLoading = false, error = error.userMessage())
                        }
                    },
                )
            } catch (_: GetCredentialCancellationException) {
                _uiState.update { it.copy(googleLoading = false) }
            } catch (e: GetCredentialException) {
                _uiState.update { it.copy(googleLoading = false, error = e.userMessage()) }
            }
        }
    }

    /**
     * Google ID token via Credential Manager. Returning users first, then all
     * accounts (the recommended flow). Null means the user cancelled.
     */
    private suspend fun requestGoogleIdToken(activity: Activity): String? {
        val manager = CredentialManager.create(context)
        var lastError: GetCredentialException? = null
        for (filterByAuthorized in listOf(true, false)) {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(filterByAuthorized)
                .setServerClientId(googleWebClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            try {
                val result = manager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    return GoogleIdTokenCredential.createFrom(credential.data).idToken
                }
                return null
            } catch (_: GetCredentialCancellationException) {
                return null
            } catch (e: GetCredentialException) {
                lastError = e
            }
        }
        lastError?.let { throw it }
        return null
    }

    private fun setErrorRes(resId: Int) {
        _uiState.update { it.copy(error = context.getString(resId)) }
    }

    private fun Throwable.userMessage(): String {
        val error = (this as? AuthFailure)?.error
        val resId = when (error) {
            AuthError.InvalidCredentials -> R.string.auth_error_invalid
            AuthError.NoAccount -> R.string.auth_error_no_account
            AuthError.EmailInUse -> R.string.auth_error_exists
            AuthError.WeakPassword -> R.string.auth_error_weak
            AuthError.Network -> R.string.auth_error_network
            AuthError.NotConfigured -> R.string.auth_error_not_configured
            AuthError.Unknown, null -> R.string.auth_error_generic
        }
        return if (error == null || error == AuthError.Unknown) {
            localizedMessage?.takeIf { it.isNotBlank() } ?: context.getString(resId)
        } else {
            context.getString(resId)
        }
    }

    private fun isValidEmail(email: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    private companion object {
        const val PASSWORD_MIN = 6
    }
}
