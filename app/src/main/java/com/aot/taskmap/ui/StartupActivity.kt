package com.aot.taskmap.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.aot.taskmap.BuildConfig
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
    private var pendingCameraUri: Uri? = null
    private var pendingCameraFile: File? = null
    private var pendingCropSourceFile: File? = null
    private var pendingCropOutputUri: Uri? = null

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val localSourceUri = prepareCropSourceUri(uri)
        if (localSourceUri == null) {
            Toast.makeText(this, getString(R.string.profile_avatar_crop_failed), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        launchAvatarCrop(localSourceUri)
    }

    private val avatarCameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { launchAvatarCrop(it) }
        } else {
            clearPendingCameraCapture(deleteFile = true)
        }
    }

    private val avatarCropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val outputUri = UCrop.getOutput(result.data ?: Intent())
                    ?: pendingCropOutputUri
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
                val error = UCrop.getError(result.data ?: Intent())
                android.util.Log.e(
                    "StartupActivity",
                    "Avatar crop failed: ${error?.message}",
                    error
                )
                Toast.makeText(
                    this,
                    getString(R.string.profile_avatar_crop_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        pendingCropOutputUri = null
        clearPendingCropSource(deleteFile = true)
        clearPendingCameraCapture(deleteFile = true)
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
        binding.buttonTakePhoto.setOnClickListener {
            launchCameraCapture()
        }

        binding.buttonRemoveAvatar.setOnClickListener {
            removeStoredAvatarIfOwned(SettingsPreferences.getProfileAvatarUri(this))
            SettingsPreferences.setProfileAvatarUri(this, null)
            updateAvatar(null)
            Toast.makeText(this, getString(R.string.profile_avatar_removed), Toast.LENGTH_SHORT).show()
        }

        binding.buttonRandomNick.setOnClickListener {
            val nickname = SettingsPreferences.generateAndSaveRandomNickname(this)
            binding.editProfileName.setText(nickname)
            SettingsPreferences.setProfileName(this, nickname)
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

        binding.buttonOpenAllVersions.setOnClickListener {
            openAllVersionsPage()
        }
    }

    private fun bindProfile() {
        val initialName = SettingsPreferences.getProfileName(this)
            ?: SettingsPreferences.getEffectiveProfileName(this)
        binding.editProfileName.setText(initialName)
        val avatarUri = SettingsPreferences.getProfileAvatarUri(this)?.let(Uri::parse)
        updateAvatar(avatarUri)
    }

    private fun saveProfile() {
        SettingsPreferences.setProfileName(
            this,
            binding.editProfileName.text?.toString().orEmpty()
        )
        // Если имя не задано, фиксируем авто-ник, чтобы профиль был готов сразу.
        SettingsPreferences.getEffectiveProfileName(this)
    }

    private fun openAllVersionsPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.UPDATE_RELEASES_PAGE))
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(
                    this,
                    getString(R.string.startup_open_all_versions_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
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

    private fun launchCameraCapture() {
        val outputUri = createCameraImageUri()
        if (outputUri == null) {
            Toast.makeText(this, getString(R.string.profile_avatar_crop_failed), Toast.LENGTH_SHORT).show()
            return
        }
        avatarCameraLauncher.launch(outputUri)
    }

    private fun createCameraImageUri(): Uri? {
        return runCatching {
            val cameraDir = File(cacheDir, "camera").apply { mkdirs() }
            val imageFile = File(cameraDir, "avatar_capture_${System.currentTimeMillis()}.jpg")
            pendingCameraFile = imageFile
            val authority = "${packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(this, authority, imageFile)
            pendingCameraUri = uri
            uri
        }.getOrNull()
    }

    private fun clearPendingCameraCapture(deleteFile: Boolean) {
        if (deleteFile) {
            runCatching { pendingCameraFile?.delete() }
        }
        pendingCameraUri = null
        pendingCameraFile = null
    }

    private fun launchAvatarCrop(sourceUri: Uri) {
        val preparedSourceUri = prepareCropSourceUri(sourceUri) ?: run {
            Toast.makeText(this, getString(R.string.profile_avatar_crop_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val outputUri = createAvatarOutputUri()
        if (outputUri == null) {
            Toast.makeText(this, getString(R.string.profile_avatar_crop_failed), Toast.LENGTH_SHORT).show()
            return
        }
        pendingCropOutputUri = outputUri
        val options = UCrop.Options().apply {
            setCircleDimmedLayer(true)
            setShowCropFrame(true)
            setShowCropGrid(true)
            setCompressionQuality(92)
            setToolbarTitle(getString(R.string.profile_avatar_crop_title))
            setActiveControlsWidgetColor(ContextCompat.getColor(this@StartupActivity, R.color.primary))
        }
        val intent = UCrop.of(preparedSourceUri, outputUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1024, 1024)
            .withOptions(options)
            .getIntent(this)
            .apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        runCatching { avatarCropLauncher.launch(intent) }
            .onFailure {
                pendingCropOutputUri = null
                Toast.makeText(
                    this,
                    getString(R.string.profile_avatar_crop_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun createAvatarOutputUri(): Uri? {
        return runCatching {
            val avatarDir = File(filesDir, "avatars").apply { mkdirs() }
            val outputFile = File(avatarDir, "avatar_current.jpg")
            if (outputFile.exists()) {
                outputFile.delete()
            }
            outputFile.createNewFile()
            Uri.fromFile(outputFile)
        }.getOrNull()
    }

    private fun prepareCropSourceUri(sourceUri: Uri): Uri? {
        if (sourceUri.scheme.equals("file", ignoreCase = true)) return sourceUri
        return runCatching {
            val tempDir = File(cacheDir, "avatar_source").apply { mkdirs() }
            clearPendingCropSource(deleteFile = true)
            val tempFile = File(tempDir, "avatar_source_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(sourceUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            pendingCropSourceFile = tempFile
            Uri.fromFile(tempFile)
        }.getOrNull()
    }

    private fun clearPendingCropSource(deleteFile: Boolean) {
        if (deleteFile) {
            runCatching { pendingCropSourceFile?.delete() }
        }
        pendingCropSourceFile = null
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
            val decodedBitmap = decodeAvatarPreview(uri)
                ?: error("Failed to decode avatar preview")
            binding.imageAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
            binding.imageAvatar.setImageBitmap(decodedBitmap)
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

    private fun decodeAvatarPreview(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            reqWidth = 360,
            reqHeight = 360
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var sampleSize = 1
        var halfWidth = width / 2
        var halfHeight = height / 2
        while ((halfWidth / sampleSize) >= reqWidth && (halfHeight / sampleSize) >= reqHeight) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingCropOutputUri = null
        clearPendingCropSource(deleteFile = true)
        clearPendingCameraCapture(deleteFile = true)
    }
}
