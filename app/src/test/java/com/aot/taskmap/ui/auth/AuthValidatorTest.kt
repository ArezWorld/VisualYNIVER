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

}
