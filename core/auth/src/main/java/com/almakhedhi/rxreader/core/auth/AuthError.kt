package com.almakhedhi.rxreader.core.auth

/** Backend-independent auth failures, so UI layers never touch Firebase types. */
enum class AuthError {
    InvalidCredentials,
    NoAccount,
    EmailInUse,
    WeakPassword,
    Network,
    NotConfigured,
    Unknown,
}

class AuthFailure(val error: AuthError, cause: Throwable? = null) :
    Exception(cause?.message, cause)
