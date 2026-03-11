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
import com.aot.taskmap.databinding.ActivityLoginBinding
import com.aot.taskmap.ui.MainActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient

    companion object {
        // Замените на IP вашего сервера
        const val BASE_URL = "http://10.0.2.2:8000"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        apiClient = ApiClient(BASE_URL)

        if (BuildConfig.DEBUG) {
            sessionManager.saveSession("debug-token", 0L, "debug")
            apiClient.setToken(sessionManager.authToken)
            Toast.makeText(this, getString(R.string.debug_skip_auth), Toast.LENGTH_SHORT).show()
            navigateToMain()
            return
        }

        // Если уже вошёл - переходим на главный экран
        if (sessionManager.isSessionLoggedIn()) {
            val token = sessionManager.authToken
            if (token != null) {
                apiClient.setToken(token)
                navigateToMain()
                return
            }
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val username = binding.editUsername.text.toString().trim()
            val password = binding.editPassword.text.toString()

            if (validateInput(username, password)) {
                login(username, password)
            }
        }

        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun validateInput(username: String, password: String): Boolean {
        val errors = AuthValidator.validateLogin(username, password)
        binding.editUsername.error = errors.username?.let { getString(it) }
        binding.editPassword.error = errors.password?.let { getString(it) }
        return errors.isValid
    }

    private fun login(username: String, password: String) {
        showLoading(true)

        lifecycleScope.launch {
            val result = apiClient.login(username, password)

            result.onSuccess { response ->
                sessionManager.authToken = response.accessToken
                sessionManager.isLoggedIn = true
                apiClient.setToken(response.accessToken)
                navigateToMain()
            }

            result.onFailure { error ->
                showLoading(false)
                showError(error.message ?: getString(R.string.error_login_failed))
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !show
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
        finish()
    }
}
