package com.almakhedhi.rxreader.core.auth

import android.content.Context
import com.almakhedhi.rxreader.core.auth.model.RxUser
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : AuthRepository {

    /** Null until google-services.json is added to the app module. */
    private fun authOrNull(): FirebaseAuth? =
        if (FirebaseApp.getApps(context).isEmpty()) {
            null
        } else {
            try {
                FirebaseAuth.getInstance()
            } catch (_: Exception) {
                null
            }
        }

    override val currentUser: Flow<RxUser?> = callbackFlow {
        val auth = authOrNull()
        if (auth == null) {
            trySend(null)
        } else {
            trySend(auth.currentUser?.toRxUser())
            val listener = FirebaseAuth.AuthStateListener {
                trySend(it.currentUser?.toRxUser())
            }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }
    }

    override val cachedUser: RxUser?
        get() = authOrNull()?.currentUser?.toRxUser()

    override suspend fun signIn(email: String, password: String): Result<RxUser> =
        firebaseCall {
            val auth = authOrNull() ?: throw FirebaseNotConfiguredException()
            auth.signInWithEmailAndPassword(email.trim(), password).await()
                .user?.toRxUser() ?: throw IllegalStateException("Empty user")
        }

    override suspend fun signUp(
        displayName: String,
        email: String,
        password: String,
    ): Result<RxUser> =
        firebaseCall {
            val auth = authOrNull() ?: throw FirebaseNotConfiguredException()
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: throw IllegalStateException("Empty user")
            val name = displayName.trim()
            if (name.isNotEmpty()) {
                user.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(name).build(),
                ).await()
            }
            auth.currentUser?.toRxUser() ?: user.toRxUser()
        }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        firebaseCall {
            val auth = authOrNull() ?: throw FirebaseNotConfiguredException()
            auth.sendPasswordResetEmail(email.trim()).await()
        }

    override suspend fun signInWithGoogle(idToken: String): Result<RxUser> =
        firebaseCall {
            val auth = authOrNull() ?: throw FirebaseNotConfiguredException()
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                .user?.toRxUser() ?: throw IllegalStateException("Empty user")
        }

    override fun signOut() {
        authOrNull()?.signOut()
    }

    private suspend fun <T> firebaseCall(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(AuthFailure(e.toAuthError(), e))
        }

    private fun Throwable.toAuthError(): AuthError =
        when (this) {
            is FirebaseNotConfiguredException -> AuthError.NotConfigured
            is FirebaseAuthInvalidCredentialsException -> AuthError.InvalidCredentials
            is FirebaseAuthInvalidUserException -> AuthError.NoAccount
            is FirebaseAuthUserCollisionException -> AuthError.EmailInUse
            is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword
            is FirebaseNetworkException -> AuthError.Network
            else -> AuthError.Unknown
        }

    private fun FirebaseUser.toRxUser() = RxUser(
        uid = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString(),
    )
}
