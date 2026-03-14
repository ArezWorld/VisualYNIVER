package com.aot.taskmap.ui.auth

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SettingsPreferences
import com.aot.taskmap.data.local.SessionManager
import com.aot.taskmap.data.local.ThemePreferences
import com.aot.taskmap.data.remote.ApiBaseUrlProvider
import com.aot.taskmap.data.remote.ApiClient
import com.aot.taskmap.databinding.ActivityRegisterBinding
import com.aot.taskmap.ui.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient
    private lateinit var apiBaseUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemePreferences.getThemeRes(this))
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        rebuildApiClient()
        updateServerUrlLabel()

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

        binding.btnServerSettings.setOnClickListener {
            showServerSettingsDialog()
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
                hideError()
                dialog.dismiss()
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                SettingsPreferences.setApiBaseUrlOverride(this, null)
                rebuildApiClient()
                updateServerUrlLabel()
                hideError()
                dialog.dismiss()
            }
        }

        dialog.show()
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

                    navigateToMain(triggerUpdateCheck = true)
                }

                loginResult.onFailure {
                    showLoading(false)
                    showError(getString(R.string.error_register_login_failed))
                }
            }

            result.onFailure { error ->
                showLoading(false)
                showError(
                    AuthErrorFormatter.format(
                        context = this@RegisterActivity,
                        error = error,
                        fallbackMessage = getString(R.string.error_register_failed),
                        baseUrl = apiBaseUrl
                    )
                )
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !show
        binding.btnBackToLogin.isEnabled = !show
        binding.btnServerSettings.isEnabled = !show
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.textError.visibility = View.GONE
    }

    private fun navigateToMain(triggerUpdateCheck: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("api_token", apiClient.getToken())
        intent.putExtra(MainActivity.EXTRA_TRIGGER_UPDATE_CHECK, triggerUpdateCheck)
        startActivity(intent)
        finishAffinity()
    }
}
