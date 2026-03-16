package com.aot.taskmap.ui.profile

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SettingsPreferences
import com.aot.taskmap.databinding.FragmentProfileBinding
import com.yalantis.ucrop.UCrop
import java.io.File

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var pendingCameraUri: Uri? = null
    private var pendingCameraFile: File? = null
    private var pendingCropSourceFile: File? = null
    private var pendingCropOutputUri: Uri? = null

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || !isAdded || _binding == null) return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val localSourceUri = prepareCropSourceUri(uri)
        if (localSourceUri == null) {
            Toast.makeText(requireContext(), getString(R.string.profile_avatar_crop_failed), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        launchAvatarCrop(localSourceUri)
    }

    private val avatarCameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (!isAdded || _binding == null) return@registerForActivityResult
        if (success) {
            val cameraFile = pendingCameraFile
            if (cameraFile != null && cameraFile.exists() && cameraFile.length() > 0L) {
                launchAvatarCrop(Uri.fromFile(cameraFile))
            } else {
                Toast.makeText(requireContext(), getString(R.string.profile_avatar_crop_failed), Toast.LENGTH_SHORT).show()
                clearPendingCameraCapture(deleteFile = true)
            }
        } else {
            clearPendingCameraCapture(deleteFile = true)
        }
    }

    private val avatarCropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (!isAdded || _binding == null) return@registerForActivityResult
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val outputUri = UCrop.getOutput(result.data ?: Intent())
                    ?: pendingCropOutputUri
                    ?: return@registerForActivityResult
                saveAvatarUri(outputUri)
                updateAvatar(outputUri)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.profile_avatar_saved),
                    Toast.LENGTH_SHORT
                ).show()
            }
            UCrop.RESULT_ERROR -> {
                val error = UCrop.getError(result.data ?: Intent())
                android.util.Log.e(
                    "ProfileFragment",
                    "Avatar crop failed: ${error?.message}",
                    error
                )
                Toast.makeText(
                    requireContext(),
                    getString(R.string.profile_avatar_crop_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        pendingCropOutputUri = null
        clearPendingCropSource(deleteFile = true)
        clearPendingCameraCapture(deleteFile = true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActions()
        bindProfile()
    }

    private fun setupActions() {
        binding.buttonSaveProfile.setOnClickListener {
            SettingsPreferences.setProfileName(
                requireContext(),
                binding.editProfileName.text?.toString().orEmpty()
            )
            refreshProfileMeta()
            Toast.makeText(requireContext(), getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
        }

        binding.buttonRandomNick.setOnClickListener {
            val nickname = SettingsPreferences.generateAndSaveRandomNickname(requireContext())
            binding.editProfileName.setText(nickname)
            SettingsPreferences.setProfileName(requireContext(), nickname)
            refreshProfileMeta()
            Toast.makeText(
                requireContext(),
                getString(R.string.profile_random_generated, nickname),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.buttonPickAvatar.setOnClickListener {
            avatarPicker.launch(arrayOf("image/*"))
        }

        binding.buttonTakePhoto.setOnClickListener {
            launchCameraCapture()
        }

        binding.buttonRemoveAvatar.setOnClickListener {
            removeStoredAvatarIfOwned(SettingsPreferences.getProfileAvatarUri(requireContext()))
            SettingsPreferences.setProfileAvatarUri(requireContext(), null)
            updateAvatar(null)
            Toast.makeText(requireContext(), getString(R.string.profile_avatar_removed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindProfile() {
        val initialName = SettingsPreferences.getProfileName(requireContext())
            ?: SettingsPreferences.getEffectiveProfileName(requireContext())
        binding.editProfileName.setText(initialName)
        val avatarUri = SettingsPreferences.getProfileAvatarUri(requireContext())?.let(Uri::parse)
        updateAvatar(avatarUri)
        refreshProfileMeta()
    }

    private fun launchAvatarCrop(sourceUri: Uri) {
        val context = requireContext()
        val preparedSourceUri = prepareCropSourceUri(sourceUri) ?: run {
            Toast.makeText(context, getString(R.string.profile_avatar_crop_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val outputUri = createAvatarOutputUri()
        if (outputUri == null) {
            Toast.makeText(context, getString(R.string.profile_avatar_crop_failed), Toast.LENGTH_SHORT).show()
            return
        }
        pendingCropOutputUri = outputUri
        val options = UCrop.Options().apply {
            setCircleDimmedLayer(true)
            setShowCropFrame(true)
            setShowCropGrid(true)
            setCompressionQuality(92)
            setToolbarTitle(getString(R.string.profile_avatar_crop_title))
            setActiveControlsWidgetColor(ContextCompat.getColor(context, R.color.primary))
        }
        val intent = UCrop.of(preparedSourceUri, outputUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1024, 1024)
            .withOptions(options)
            .getIntent(context)
            .apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        runCatching { avatarCropLauncher.launch(intent) }
            .onFailure {
                pendingCropOutputUri = null
                Toast.makeText(
                    context,
                    getString(R.string.profile_avatar_crop_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun launchCameraCapture() {
        val outputUri = createCameraImageUri()
        if (outputUri == null) {
            Toast.makeText(requireContext(), getString(R.string.profile_avatar_crop_failed), Toast.LENGTH_SHORT).show()
            return
        }
        avatarCameraLauncher.launch(outputUri)
    }

    private fun createAvatarOutputUri(): Uri? {
        val context = requireContext()
        return runCatching {
            val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
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
        val context = requireContext()
        return runCatching {
            val tempDir = File(context.cacheDir, "avatar_source").apply { mkdirs() }
            clearPendingCropSource(deleteFile = true)
            val tempFile = File(tempDir, "avatar_source_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            pendingCropSourceFile = tempFile
            Uri.fromFile(tempFile)
        }.getOrNull()
    }

    private fun createCameraImageUri(): Uri? {
        val context = requireContext()
        return runCatching {
            val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
            val imageFile = File(cameraDir, "avatar_capture_${System.currentTimeMillis()}.jpg")
            if (imageFile.exists()) {
                imageFile.delete()
            }
            imageFile.createNewFile()
            pendingCameraFile = imageFile
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, imageFile)
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

    private fun clearPendingCropSource(deleteFile: Boolean) {
        if (deleteFile) {
            runCatching { pendingCropSourceFile?.delete() }
        }
        pendingCropSourceFile = null
    }

    private fun saveAvatarUri(newUri: Uri) {
        val context = requireContext()
        val oldUriString = SettingsPreferences.getProfileAvatarUri(context)
        if (!oldUriString.isNullOrBlank() && oldUriString != newUri.toString()) {
            removeStoredAvatarIfOwned(oldUriString)
        }
        SettingsPreferences.setProfileAvatarUri(context, newUri.toString())
    }

    private fun removeStoredAvatarIfOwned(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        val context = requireContext()
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        val path = uri.path ?: return
        val avatarRoot = File(context.filesDir, "avatars")
        if (!path.startsWith(avatarRoot.absolutePath)) return
        runCatching { File(path).delete() }
    }

    private fun refreshProfileMeta() {
        val context = requireContext()
        val senderId = SettingsPreferences.getOrCreateSenderId(context)

        binding.textSenderId.text =
            getString(R.string.profile_sender_id_value, senderId)
    }

    private fun updateAvatar(uri: Uri?) {
        if (uri == null) {
            binding.imageAvatar.scaleType = ImageView.ScaleType.CENTER_INSIDE
            binding.imageAvatar.setImageResource(R.drawable.ic_profile)
            binding.imageAvatar.imageTintList =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.primary)
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
            removeStoredAvatarIfOwned(SettingsPreferences.getProfileAvatarUri(requireContext()))
            SettingsPreferences.setProfileAvatarUri(requireContext(), null)
            binding.imageAvatar.scaleType = ImageView.ScaleType.CENTER_INSIDE
            binding.imageAvatar.setImageResource(R.drawable.ic_profile)
            binding.imageAvatar.imageTintList =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.primary)
                )
        }
    }

    private fun decodeAvatarPreview(uri: Uri): Bitmap? {
        val context = requireContext()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
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
        return context.contentResolver.openInputStream(uri)?.use { input ->
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
        val halfWidth = width / 2
        val halfHeight = height / 2
        while ((halfWidth / sampleSize) >= reqWidth && (halfHeight / sampleSize) >= reqHeight) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    override fun onDestroyView() {
        pendingCropOutputUri = null
        clearPendingCropSource(deleteFile = true)
        clearPendingCameraCapture(deleteFile = true)
        super.onDestroyView()
        _binding = null
    }
}
