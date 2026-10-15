package com.almakhedhi.rxreader.core.auth.model

/** Signed-in user, independent of the auth backend. */
data class RxUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
)
