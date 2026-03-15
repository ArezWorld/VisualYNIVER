package com.aot.taskmap.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SettingsPreferences
import com.aot.taskmap.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || !isAdded || _binding == null) return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        SettingsPreferences.setProfileAvatarUri(requireContext(), uri.toString())
        updateAvatar(uri)
        Toast.makeText(requireContext(), getString(R.string.profile_avatar_saved), Toast.LENGTH_SHORT).show()
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
            binding.editProfileName.setText("")
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

        binding.buttonRemoveAvatar.setOnClickListener {
            SettingsPreferences.setProfileAvatarUri(requireContext(), null)
            updateAvatar(null)
            Toast.makeText(requireContext(), getString(R.string.profile_avatar_removed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindProfile() {
        binding.editProfileName.setText(SettingsPreferences.getProfileName(requireContext()).orEmpty())
        val avatarUri = SettingsPreferences.getProfileAvatarUri(requireContext())?.let(Uri::parse)
        updateAvatar(avatarUri)
        refreshProfileMeta()
    }

    private fun refreshProfileMeta() {
        val context = requireContext()
        val effectiveName = SettingsPreferences.getEffectiveProfileName(context)
        val senderId = SettingsPreferences.getOrCreateSenderId(context)

        binding.textEffectiveName.text =
            getString(R.string.profile_effective_name_value, effectiveName)
        binding.textSenderId.text =
            getString(R.string.profile_sender_id_value, senderId)
    }

    private fun updateAvatar(uri: Uri?) {
        if (uri == null) {
            binding.imageAvatar.setImageResource(R.drawable.ic_profile)
            binding.imageAvatar.imageTintList =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.primary)
                )
            return
        }

        val result = runCatching {
            binding.imageAvatar.setImageURI(uri)
            binding.imageAvatar.imageTintList = null
        }
        if (result.isFailure) {
            SettingsPreferences.setProfileAvatarUri(requireContext(), null)
            binding.imageAvatar.setImageResource(R.drawable.ic_profile)
            binding.imageAvatar.imageTintList =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.primary)
                )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
