package com.almakhedhi.rxreader.core.auth

import com.almakhedhi.rxreader.core.auth.model.RxUser
import kotlinx.coroutines.flow.Flow

/** Thrown when Firebase was not configured (no google-services.json yet). */
class FirebaseNotConfiguredException : IllegalStateException("Firebase is not configured yet")

interface AuthRepository {
    /** Currently signed-in user (null when signed out). Emits on every change. */
    val currentUser: Flow<RxUser?>

    /** Synchronous snapshot, so cold start never flashes the login screen. */
    val cachedUser: RxUser?

    suspend fun signIn(email: String, password: String): Result<RxUser>
    suspend fun signUp(displayName: String, email: String, password: String): Result<RxUser>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun signInWithGoogle(idToken: String): Result<RxUser>
    fun signOut()
}
