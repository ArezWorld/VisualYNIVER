package com.aot.taskmap.ui.auth

import com.aot.taskmap.R

data class AuthFieldErrors(
    val username: Int? = null,
    val email: Int? = null,
    val password: Int? = null,
    val confirmPassword: Int? = null
) {
    val isValid: Boolean
        get() = username == null && email == null && password == null && confirmPassword == null
}

object AuthValidator {
    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

    fun validateLogin(username: String, password: String): AuthFieldErrors {
        val usernameError = if (username.isBlank()) R.string.error_username_required else null
        val passwordError = if (password.isBlank()) R.string.error_password_required else null
        return AuthFieldErrors(username = usernameError, password = passwordError)
    }

    fun validateRegister(
        username: String,
        email: String,
        password: String,
        confirmPassword: String
    ): AuthFieldErrors {
        val usernameError = when {
            username.isBlank() -> R.string.error_username_required
            username.length < 3 -> R.string.error_username_short
            else -> null
        }

        val emailError = when {
            email.isBlank() -> R.string.error_email_required
            !emailRegex.matches(email) -> R.string.error_email_invalid
            else -> null
        }

        val passwordError = when {
            password.isBlank() -> R.string.error_password_required
            password.length < 6 -> R.string.error_password_short
            else -> null
        }

        val confirmPasswordError = if (confirmPassword != password) {
            R.string.error_password_mismatch
        } else {
            null
        }

        return AuthFieldErrors(
            username = usernameError,
            email = emailError,
            password = passwordError,
            confirmPassword = confirmPasswordError
        )
    }
}
