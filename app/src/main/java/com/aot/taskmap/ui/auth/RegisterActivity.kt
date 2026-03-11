package com.aot.taskmap.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aot.taskmap.BuildConfig
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SessionManager
import com.aot.taskmap.data.remote.ApiClient
import com.aot.taskmap.databinding.ActivityRegisterBinding
import com.aot.taskmap.ui.MainActivity
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        apiClient = ApiClient(LoginActivity.BASE_URL)

        if (BuildConfig.DEBUG) {
            Toast.makeText(this, getString(R.string.debug_register_disabled), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnRegister.setOnClickListener {
            val username = binding.editUsername.text.toString().trim()
            val email = binding.editEmail.text.toString().trim()
            val password = binding.editPassword.text.toString()
            val confirmPassword = binding.editConfirmPassword.text.toString()

            if (validateInput(username, email, password, confirmPassword)) {
                register(username, email, password)
            }
        }

        binding.btnBackToLogin.setOnClickListener {
            finish()
        }
    }

    private fun validateInput(
        username: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        val errors = AuthValidator.validateRegister(username, email, password, confirmPassword)
        binding.editUsername.error = errors.username?.let { getString(it) }
        binding.editEmail.error = errors.email?.let { getString(it) }
        binding.editPassword.error = errors.password?.let { getString(it) }
        binding.editConfirmPassword.error = errors.confirmPassword?.let { getString(it) }
        return errors.isValid
    }

    private fun register(username: String, email: String, password: String) {
        showLoading(true)

        lifecycleScope.launch {
            val result = apiClient.register(username, email, password)

            result.onSuccess { user ->
                val loginResult = apiClient.login(username, password)

                loginResult.onSuccess { response ->
                    sessionManager.authToken = response.accessToken
                    sessionManager.userId = user.id.toLong()
                    sessionManager.username = user.username
                    sessionManager.isLoggedIn = true
                    apiClient.setToken(response.accessToken)

                    navigateToMain()
                }

                loginResult.onFailure {
                    showLoading(false)
                    showError(getString(R.string.error_register_login_failed))
                }
            }

            result.onFailure { error ->
                showLoading(false)
                showError(error.message ?: getString(R.string.error_register_failed))
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !show
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("api_token", apiClient.getToken())
        startActivity(intent)
        finishAffinity()
    }
}
