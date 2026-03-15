package com.aot.taskmap.ui.auth

import com.aot.taskmap.R

data class AuthFieldErrors(
    val username: Int? = null,
    val password: Int? = null
) {
    val isValid: Boolean
        get() = username == null && password == null
}

object AuthValidator {
    fun validateLogin(username: String, password: String): AuthFieldErrors {
        val usernameError = if (username.isBlank()) R.string.error_username_required else null
        val passwordError = if (password.isBlank()) R.string.error_password_required else null
        return AuthFieldErrors(username = usernameError, password = passwordError)
    }
}
