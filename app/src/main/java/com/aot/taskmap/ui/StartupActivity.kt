package com.aot.taskmap.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SettingsPreferences
import com.aot.taskmap.data.local.ThemePreferences
import com.aot.taskmap.databinding.ActivityStartupBinding
import com.aot.taskmap.ui.tasks.TaskShareManager
import com.yalantis.ucrop.UCrop
import java.io.File

class StartupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStartupBinding
    private var pendingImportText: String? = null

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        launchAvatarCrop(uri)
    }

    private val avatarCropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val outputUri = UCrop.getOutput(result.data ?: return@registerForActivityResult)
                    ?: return@registerForActivityResult
                saveAvatarUri(outputUri)
                updateAvatar(outputUri)
                Toast.makeText(
                    this,
                    getString(R.string.profile_avatar_saved),
                    Toast.LENGTH_SHORT
                ).show()
            }

            UCrop.RESULT_ERROR -> {
                Toast.makeText(
                    this,
                    getString(R.string.profile_avatar_crop_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemePreferences.getThemeRes(this))
        super.onCreate(savedInstanceState)
        binding = ActivityStartupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        pendingImportText = TaskShareManager.extractShareTextFromIntent(intent)

        bindProfile()
        setupActions()

        if (!pendingImportText.isNullOrBlank()) {
            saveProfile()
            navigateToMain()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingImportText = TaskShareManager.extractShareTextFromIntent(intent)
    }

    private fun setupActions() {
        binding.buttonPickAvatar.setOnClickListener {
            avatarPicker.launch(arrayOf("image/*"))
        }

        binding.buttonRemoveAvatar.setOnClickListener {
            removeStoredAvatarIfOwned(SettingsPreferences.getProfileAvatarUri(this))
            SettingsPreferences.setProfileAvatarUri(this, null)
            updateAvatar(null)
            Toast.makeText(this, getString(R.string.profile_avatar_removed), Toast.LENGTH_SHORT).show()
        }

        binding.buttonRandomNick.setOnClickListener {
            val nickname = SettingsPreferences.generateAndSaveRandomNickname(this)
            binding.editProfileName.setText("")
            refreshProfileMeta()
            Toast.makeText(
                this,
                getString(R.string.profile_random_generated, nickname),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.buttonContinue.setOnClickListener {
            saveProfile()
            navigateToMain()
        }
    }

    private fun bindProfile() {
        binding.editProfileName.setText(SettingsPreferences.getProfileName(this).orEmpty())
        val avatarUri = SettingsPreferences.getProfileAvatarUri(this)?.let(Uri::parse)
        updateAvatar(avatarUri)
        refreshProfileMeta()
    }

    private fun saveProfile() {
        SettingsPreferences.setProfileName(
            this,
            binding.editProfileName.text?.toString().orEmpty()
        )
        // Если имя не задано, фиксируем авто-ник, чтобы профиль был готов сразу.
        SettingsPreferences.getEffectiveProfileName(this)
    }

    private fun navigateToMain() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_TRIGGER_UPDATE_CHECK, true)
            pendingImportText
                ?.takeIf { it.isNotBlank() }
                ?.let { putExtra(MainActivity.EXTRA_IMPORT_TASKS_TEXT, it) }
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(mainIntent)
        finish()
    }

    private fun launchAvatarCrop(sourceUri: Uri) {
        val avatarDir = File(filesDir, "avatars").apply { mkdirs() }
        val outputUri = Uri.fromFile(File(avatarDir, "avatar_current.jpg"))
        val options = UCrop.Options().apply {
            setCircleDimmedLayer(true)
            setShowCropFrame(true)
            setShowCropGrid(true)
            setCompressionQuality(92)
            setToolbarTitle(getString(R.string.profile_avatar_crop_title))
            setActiveControlsWidgetColor(ContextCompat.getColor(this@StartupActivity, R.color.primary))
        }
        val intent = UCrop.of(sourceUri, outputUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1024, 1024)
            .withOptions(options)
            .getIntent(this)
        avatarCropLauncher.launch(intent)
    }

    private fun saveAvatarUri(newUri: Uri) {
        val oldUriString = SettingsPreferences.getProfileAvatarUri(this)
        if (!oldUriString.isNullOrBlank() && oldUriString != newUri.toString()) {
            removeStoredAvatarIfOwned(oldUriString)
        }
        SettingsPreferences.setProfileAvatarUri(this, newUri.toString())
    }

    private fun removeStoredAvatarIfOwned(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        val path = uri.path ?: return
        val avatarRoot = File(filesDir, "avatars")
        if (!path.startsWith(avatarRoot.absolutePath)) return
        runCatching { File(path).delete() }
    }

    private fun refreshProfileMeta() {
        val effectiveName = SettingsPreferences.getEffectiveProfileName(this)
        binding.textEffectiveName.text =
            getString(R.string.profile_effective_name_value, effectiveName)
    }

    private fun updateAvatar(uri: Uri?) {
        if (uri == null) {
            binding.imageAvatar.scaleType = ImageView.ScaleType.CENTER_INSIDE
            binding.imageAvatar.setImageResource(R.drawable.ic_profile)
            binding.imageAvatar.imageTintList =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.primary)
                )
            return
        }

        val result = runCatching {
            binding.imageAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
            binding.imageAvatar.setImageURI(uri)
            binding.imageAvatar.imageTintList = null
        }
        if (result.isFailure) {
            removeStoredAvatarIfOwned(SettingsPreferences.getProfileAvatarUri(this))
            SettingsPreferences.setProfileAvatarUri(this, null)
            binding.imageAvatar.scaleType = ImageView.ScaleType.CENTER_INSIDE
            binding.imageAvatar.setImageResource(R.drawable.ic_profile)
            binding.imageAvatar.imageTintList =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.primary)
                )
        }
    }
}

