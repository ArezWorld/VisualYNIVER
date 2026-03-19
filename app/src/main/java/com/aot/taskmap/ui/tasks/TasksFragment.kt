package com.aot.taskmap.ui.tasks

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SettingsPreferences
import com.aot.taskmap.databinding.DialogImportTasksBinding
import com.aot.taskmap.databinding.FragmentTasksBinding
import com.aot.taskmap.domain.model.Task
import com.aot.taskmap.ui.map.MapViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by activityViewModels()
    private lateinit var adapter: TasksAdapter
    private var currentFilter: TaskFilter = TaskFilter.ACTIVE
    private var activeTasksCache = emptyList<Task>()
    private var completedTasksCache = emptyList<Task>()

    private enum class TaskFilter {
        ACTIVE, COMPLETED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFiltersMenu()
        observeTasks()
        renderTasks()
    }

    private fun setupRecyclerView() {
        adapter = TasksAdapter(
            onTaskClick = { _ ->
                // Показываем детали задачи при необходимости
            },
            onTaskToggleRequest = { task, newChecked, position ->
                if (SettingsPreferences.isConfirmCompleteEnabled(requireContext())) {
                    val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.tasks_confirm_title))
                        .setMessage(
                            if (newChecked) {
                                getString(R.string.tasks_confirm_message_complete)
                            } else {
                                getString(R.string.tasks_confirm_message_restore)
                            }
                        )
                        .setPositiveButton(getString(R.string.map_confirm_yes)) { _, _ ->
                            viewModel.toggleTaskCompletion(task)
                        }
                        .setNegativeButton(getString(R.string.map_confirm_no)) { _, _ ->
                            adapter.restoreCheckState(position)
                        }
                        .setOnCancelListener {
                            adapter.restoreCheckState(position)
                        }
                        .show()
                    dialog.setCanceledOnTouchOutside(false)
                } else {
                    viewModel.toggleTaskCompletion(task)
                }
            },
            onTaskDelete = { task ->
                viewModel.deleteTask(task)
            },
            onTaskNavigate = { task ->
                viewModel.selectTask(task)
                val navController = findNavController()
                if (navController.currentDestination?.id != R.id.mapFragment) {
                    navController.navigate(R.id.mapFragment)
                }
            }
        )

        binding.recyclerViewTasks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TasksFragment.adapter
        }
    }

    private fun setupFiltersMenu() {
        binding.buttonFilter.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            MenuInflater(requireContext()).inflate(R.menu.menu_tasks_filter, popup.menu)
            popup.menu.findItem(R.id.filter_active).isChecked = currentFilter == TaskFilter.ACTIVE
            popup.menu.findItem(R.id.filter_completed).isChecked = currentFilter == TaskFilter.COMPLETED
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.filter_active -> {
                        currentFilter = TaskFilter.ACTIVE
                        renderTasks()
                        true
                    }
                    R.id.filter_completed -> {
                        currentFilter = TaskFilter.COMPLETED
                        renderTasks()
                        true
                    }
                    R.id.action_export_tasks -> {
                        exportTasks()
                        true
                    }
                    R.id.action_import_tasks -> {
                        showImportDialog()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun exportTasks() {
        val tasksToShare = activeTasksCache
            .filterNot { it.isCompleted }
            .distinctBy { it.id }
        if (tasksToShare.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.tasks_export_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val shareMessage = TaskShareManager.buildShareMessage(requireContext(), tasksToShare)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareMessage)
        }
        runCatching {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.tasks_export_chooser)))
        }.onFailure {
            Toast.makeText(requireContext(), getString(R.string.tasks_import_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showImportDialog() {
        val dialogBinding = DialogImportTasksBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tasks_import_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.tasks_import_action, null)
            .setNeutralButton(R.string.tasks_import_from_clipboard, null)
            .setNegativeButton(R.string.map_action_close, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                val hasText = clip != null &&
                    (clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
                        clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true)
                val text = if (hasText && clip!!.itemCount > 0) {
                    clip.getItemAt(0).coerceToText(requireContext())?.toString().orEmpty()
                } else {
                    ""
                }
                if (text.isBlank()) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.tasks_import_clipboard_empty),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    dialogBinding.editImportLink.setText(text)
                }
            }

            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialogBinding.inputImportLinkLayout.error = null
                val raw = dialogBinding.editImportLink.text?.toString().orEmpty().trim()
                if (raw.isBlank()) {
                    dialogBinding.inputImportLinkLayout.error =
                        getString(R.string.tasks_import_error_invalid_link)
                    return@setOnClickListener
                }
                importTasksFromSharedText(raw)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun importTasksFromSharedText(rawText: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                TaskShareManager.importTasksFromShareText(requireContext(), rawText)
            }

            result.onSuccess { imported ->
                viewModel.loadTasks()
                Toast.makeText(
                    requireContext(),
                    getString(
                        R.string.tasks_import_success,
                        imported.addedCount,
                        imported.senderName
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    error.message ?: getString(R.string.tasks_import_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun observeTasks() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.activeTasks.collect { tasks ->
                        activeTasksCache = tasks
                        renderTasks()
                    }
                }
                launch {
                    viewModel.completedTasks.collect { tasks ->
                        completedTasksCache = tasks
                        renderTasks()
                    }
                }
            }
        }
    }

    private fun renderTasks() {
        if (_binding == null) return
        val shown = when (currentFilter) {
            TaskFilter.ACTIVE -> activeTasksCache
            TaskFilter.COMPLETED -> completedTasksCache
                .sortedByDescending { it.completedAt ?: it.createdAt }
                .take(10)
        }
        binding.textTitle.text = if (currentFilter == TaskFilter.ACTIVE) {
            getString(R.string.tasks_title_active)
        } else {
            getString(R.string.tasks_title_completed)
        }
        binding.textEmpty.text = if (currentFilter == TaskFilter.ACTIVE) {
            getString(R.string.tasks_empty_active)
        } else {
            getString(R.string.tasks_empty_completed)
        }
        adapter.submitList(shown)
        binding.textEmpty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
