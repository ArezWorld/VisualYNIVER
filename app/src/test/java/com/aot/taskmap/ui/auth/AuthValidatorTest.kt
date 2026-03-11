package com.aot.taskmap.ui.auth

import com.aot.taskmap.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun login_emptyFields_returnsErrors() {
        val result = AuthValidator.validateLogin("", "")

        assertEquals(R.string.error_username_required, result.username)
        assertEquals(R.string.error_password_required, result.password)
        assertTrue(!result.isValid)
    }

    @Test
    fun login_validFields_returnsValid() {
        val result = AuthValidator.validateLogin("user", "pass")
        assertTrue(result.isValid)
    }

    @Test
    fun register_invalidFields_returnsErrors() {
        val result = AuthValidator.validateRegister(
            username = "ab",
            email = "bad",
            password = "123",
            confirmPassword = "321"
        )

        assertEquals(R.string.error_username_short, result.username)
        assertEquals(R.string.error_email_invalid, result.email)
        assertEquals(R.string.error_password_short, result.password)
        assertEquals(R.string.error_password_mismatch, result.confirmPassword)
        assertTrue(!result.isValid)
    }

    @Test
    fun register_validFields_returnsValid() {
        val result = AuthValidator.validateRegister(
            username = "user",
            email = "user@example.com",
            password = "123456",
            confirmPassword = "123456"
        )

        assertTrue(result.isValid)
    }
}
