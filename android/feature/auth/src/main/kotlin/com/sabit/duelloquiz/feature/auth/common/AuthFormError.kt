package com.sabit.duelloquiz.feature.auth.common

import androidx.annotation.StringRes
import com.sabit.duelloquiz.core.auth.AuthError
import com.sabit.duelloquiz.core.auth.DisplayNameError
import com.sabit.duelloquiz.core.auth.EmailError
import com.sabit.duelloquiz.core.auth.PasswordError
import com.sabit.duelloquiz.core.designsystem.R

/** Form-level errors of the auth screens (inline error slot under the headline). */
enum class AuthFormError {
    WrongCredentials,
    UserDisabled,
    TooManyAttempts,
    TooManyRequests,
    EmailInUse,
    Offline,
    Generic,
}

/** Sign-in mapping (AUTH-03): unknown email and wrong password give the same message. */
internal fun AuthError.toSignInFormError(): AuthFormError = when (this) {
    AuthError.InvalidCredentials, AuthError.InvalidEmail -> AuthFormError.WrongCredentials
    AuthError.UserDisabled -> AuthFormError.UserDisabled
    AuthError.TooManyRequests -> AuthFormError.TooManyAttempts
    AuthError.Network -> AuthFormError.Offline
    else -> AuthFormError.Generic
}

@StringRes
internal fun AuthFormError.messageRes(): Int = when (this) {
    AuthFormError.WrongCredentials -> R.string.auth_error_wrong_credentials
    AuthFormError.UserDisabled -> R.string.auth_error_user_disabled
    AuthFormError.TooManyAttempts -> R.string.auth_error_too_many_attempts
    AuthFormError.TooManyRequests -> R.string.auth_error_too_many_requests
    AuthFormError.EmailInUse -> R.string.auth_error_email_in_use
    AuthFormError.Offline -> R.string.error_offline
    AuthFormError.Generic -> R.string.error_generic
}

@StringRes
internal fun EmailError.messageRes(): Int = when (this) {
    EmailError.Required -> R.string.auth_error_email_required
    EmailError.Invalid -> R.string.auth_error_email_invalid
}

@StringRes
internal fun PasswordError.messageRes(): Int = when (this) {
    PasswordError.Required -> R.string.auth_error_password_required
    PasswordError.TooShort -> R.string.auth_error_password_short
}

@StringRes
internal fun DisplayNameError.messageRes(): Int = when (this) {
    DisplayNameError.Required -> R.string.auth_error_name_required
    DisplayNameError.Length -> R.string.auth_error_name_length
    DisplayNameError.InvalidCharacters -> R.string.auth_error_name_characters
}
