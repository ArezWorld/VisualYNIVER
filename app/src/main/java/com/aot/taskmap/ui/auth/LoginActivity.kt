package com.aot.taskmap.ui.auth

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aot.taskmap.BuildConfig
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SettingsPreferences
import com.aot.taskmap.data.local.SessionManager
import com.aot.taskmap.data.local.ThemePreferences
import com.aot.taskmap.data.remote.ApiBaseUrlProvider
import com.aot.taskmap.data.remote.ApiClient
import com.aot.taskmap.databinding.ActivityLoginBinding
import com.aot.taskmap.ui.MainActivity
import com.aot.taskmap.ui.tasks.TaskShareManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient
    private lateinit var apiBaseUrl: String
    private var googleSignInClient: GoogleSignInClient? = null
    private var googleConfigRequestToken: Int = 0

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
        rebuildApiClient()
        val sharedTasksText = TaskShareManager.extractShareTextFromIntent(intent)

        // Active session shortcut.
        if (sessionManager.isSessionLoggedIn()) {
            val token = sessionManager.authToken
            if (token != null) {
                apiClient.setToken(token)
                navigateToMain(importTasksText = sharedTasksText)
                return
            }
        }

        if (!sharedTasksText.isNullOrBlank()) {
            continueAsGuest(importTasksText = sharedTasksText)
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        updateServerUrlLabel()

        configureGoogleSignIn()
        setupListeners()
    }

    private fun rebuildApiClient() {
        apiBaseUrl = ApiBaseUrlProvider.resolve(this)
        apiClient = ApiClient(apiBaseUrl)
        sessionManager.authToken?.let { apiClient.setToken(it) }
    }

    private fun updateServerUrlLabel() {
        binding.textServerUrl.text = getString(R.string.server_url_current, apiBaseUrl)
    }

    private fun configureGoogleSignIn() {
        val clientIdFromBuildConfig = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (clientIdFromBuildConfig.isNotBlank()) {
            applyGoogleClient(clientIdFromBuildConfig)
            return
        }

        googleSignInClient = null
        val requestToken = ++googleConfigRequestToken
        lifecycleScope.launch {
            val result = apiClient.getAuthConfig()
            if (requestToken != googleConfigRequestToken) return@launch
            val serverClientId = result.getOrNull()?.googleWebClientId.orEmpty()
            if (serverClientId.isNotBlank()) {
                applyGoogleClient(serverClientId)
            }
        }
    }

    private fun applyGoogleClient(clientId: String) {
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

        binding.btnContinueGuest.setOnClickListener {
            continueAsGuest()
        }

        binding.btnServerSettings.setOnClickListener {
            showServerSettingsDialog()
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

    private fun showServerSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_server_url, null)
        val inputLayout = view.findViewById<TextInputLayout>(R.id.inputServerUrlLayout)
        val editServerUrl = view.findViewById<TextInputEditText>(R.id.editServerUrl)
        editServerUrl.setText(apiBaseUrl)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.server_url_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.map_save_task, null)
            .setNeutralButton(R.string.action_reset, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                inputLayout.error = null
                val rawValue = editServerUrl.text?.toString().orEmpty().trim()
                val normalizedUrl = ApiBaseUrlProvider.normalize(rawValue)
                if (normalizedUrl.toHttpUrlOrNull() == null) {
                    inputLayout.error = getString(R.string.error_server_url_invalid)
                    return@setOnClickListener
                }

                SettingsPreferences.setApiBaseUrlOverride(this, normalizedUrl)
                rebuildApiClient()
                updateServerUrlLabel()
                configureGoogleSignIn()
                hideError()
                dialog.dismiss()
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                SettingsPreferences.setApiBaseUrlOverride(this, null)
                rebuildApiClient()
                updateServerUrlLabel()
                configureGoogleSignIn()
                hideError()
                dialog.dismiss()
            }
        }

        dialog.show()
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
        binding.btnContinueGuest.isEnabled = !show
        binding.btnServerSettings.isEnabled = !show
    }

    private fun continueAsGuest(importTasksText: String? = null) {
        sessionManager.clearSession()
        apiClient.setToken(null)
        navigateToMain(triggerUpdateCheck = false, importTasksText = importTasksText)
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.textError.visibility = View.GONE
    }

    private fun navigateToMain(
        triggerUpdateCheck: Boolean = false,
        importTasksText: String? = null
    ) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("api_token", apiClient.getToken())
        intent.putExtra(MainActivity.EXTRA_TRIGGER_UPDATE_CHECK, triggerUpdateCheck)
        if (!importTasksText.isNullOrBlank()) {
            intent.putExtra(MainActivity.EXTRA_IMPORT_TASKS_TEXT, importTasksText)
        }
        startActivity(intent)
        finish()
    }
}
