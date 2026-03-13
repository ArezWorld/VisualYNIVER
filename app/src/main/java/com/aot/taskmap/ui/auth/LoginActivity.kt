package com.aot.taskmap.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aot.taskmap.BuildConfig
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SessionManager
import com.aot.taskmap.data.local.ThemePreferences
import com.aot.taskmap.data.remote.ApiBaseUrlProvider
import com.aot.taskmap.data.remote.ApiClient
import com.aot.taskmap.databinding.ActivityLoginBinding
import com.aot.taskmap.ui.MainActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient
    private lateinit var apiBaseUrl: String
    private var googleSignInClient: GoogleSignInClient? = null

    private val googleSignInRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val signInResult = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = signInResult.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                showLoading(false)
                showError(getString(R.string.error_google_id_token_missing))
                return@registerForActivityResult
            }
            loginWithGoogle(idToken)
        } catch (_: ApiException) {
            showLoading(false)
            showError(getString(R.string.error_google_login_cancelled))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemePreferences.getThemeRes(this))
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        apiBaseUrl = ApiBaseUrlProvider.resolve()
        apiClient = ApiClient(apiBaseUrl)

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

        configureGoogleSignIn()
        setupListeners()
    }

    private fun configureGoogleSignIn() {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (clientId.isBlank()) {
            googleSignInClient = null
            return
        }

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(clientId)
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, options)
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

        binding.btnContinueGuest.setOnClickListener {
            continueAsGuest()
        }

        binding.btnGoogleLogin.setOnClickListener {
            val client = googleSignInClient
            if (client == null) {
                showError(getString(R.string.error_google_login_unavailable))
                return@setOnClickListener
            }
            showLoading(true)
            googleSignInRequest.launch(client.signInIntent)
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
                navigateToMain(triggerUpdateCheck = true)
            }

            result.onFailure { error ->
                showLoading(false)
                showError(
                    AuthErrorFormatter.format(
                        context = this@LoginActivity,
                        error = error,
                        fallbackMessage = getString(R.string.error_login_failed),
                        baseUrl = apiBaseUrl
                    )
                )
            }
        }
    }

    private fun loginWithGoogle(idToken: String) {
        lifecycleScope.launch {
            val result = apiClient.loginWithGoogle(idToken)

            result.onSuccess { response ->
                sessionManager.authToken = response.accessToken
                sessionManager.isLoggedIn = true
                apiClient.setToken(response.accessToken)
                navigateToMain(triggerUpdateCheck = true)
            }

            result.onFailure { error ->
                showLoading(false)
                showError(
                    AuthErrorFormatter.format(
                        context = this@LoginActivity,
                        error = error,
                        fallbackMessage = getString(R.string.error_google_login_failed),
                        baseUrl = apiBaseUrl
                    )
                )
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !show
        binding.btnGoogleLogin.isEnabled = !show
        binding.btnRegister.isEnabled = !show
        binding.btnContinueGuest.isEnabled = !show
    }

    private fun continueAsGuest() {
        sessionManager.clearSession()
        apiClient.setToken(null)
        navigateToMain(triggerUpdateCheck = false)
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
    }

    private fun navigateToMain(triggerUpdateCheck: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("api_token", apiClient.getToken())
        intent.putExtra(MainActivity.EXTRA_TRIGGER_UPDATE_CHECK, triggerUpdateCheck)
        startActivity(intent)
        finish()
    }
}
