package com.aot.taskmap.ui.tasks

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
import com.aot.taskmap.databinding.FragmentTasksBinding
import com.aot.taskmap.domain.model.Task
import com.aot.taskmap.ui.map.MapViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by activityViewModels()
    private lateinit var adapter: TasksAdapter
    private var currentFilter: TaskFilter = TaskFilter.ACTIVE
    private var activeTasksCache = emptyList<Task>()
    private var completedTasksCache = emptyList<Task>()

    private enum class TaskFilter {
        ACTIVE,
        COMPLETED,
        ACTIVE_AND_COMPLETED
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
            popup.menu.findItem(R.id.filter_active_and_completed).isChecked =
                currentFilter == TaskFilter.ACTIVE_AND_COMPLETED

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
                    R.id.filter_active_and_completed -> {
                        currentFilter = TaskFilter.ACTIVE_AND_COMPLETED
                        renderTasks()
                        true
                    }
                    R.id.action_export_tasks -> {
                        shareTasksWithSelection()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun shareTasksWithSelection() {
        val tasksToShare = activeTasksCache
            .filterNot { it.isCompleted }
            .distinctBy { it.id }
        if (tasksToShare.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.tasks_export_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val checkedItems = BooleanArray(tasksToShare.size) { true }
        val itemLabels = tasksToShare.map { task ->
            val title = task.title.ifBlank { getString(R.string.tasks_import_default_title) }
            val address = task.address.trim()
            if (address.isBlank()) {
                title
            } else {
                "$title\n$address"
            }
        }.toTypedArray()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tasks_share_select_title)
            .setMultiChoiceItems(itemLabels, checkedItems) { _, index, isChecked ->
                checkedItems[index] = isChecked
            }
            .setPositiveButton(R.string.tasks_share_action) { _, _ ->
                val selectedTasks = tasksToShare.filterIndexed { index, _ -> checkedItems[index] }
                if (selectedTasks.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.tasks_share_none_selected),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                val shareMessage = TaskShareManager.buildShareMessage(requireContext(), selectedTasks)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareMessage)
                }
                runCatching {
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.tasks_export_chooser)))
                }.onFailure {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.tasks_share_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.map_add_task_negative, null)
            .show()
        dialog.setCanceledOnTouchOutside(false)
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
            TaskFilter.ACTIVE_AND_COMPLETED -> {
                val active = activeTasksCache
                val completed = completedTasksCache
                    .sortedByDescending { it.completedAt ?: it.createdAt }
                    .take(10)
                active + completed
            }
        }

        binding.textTitle.text = when (currentFilter) {
            TaskFilter.ACTIVE -> getString(R.string.tasks_title_active)
            TaskFilter.COMPLETED -> getString(R.string.tasks_title_completed)
            TaskFilter.ACTIVE_AND_COMPLETED -> getString(R.string.tasks_title_active_and_completed)
        }

        binding.textEmpty.text = when (currentFilter) {
            TaskFilter.ACTIVE -> getString(R.string.tasks_empty_active)
            TaskFilter.COMPLETED -> getString(R.string.tasks_empty_completed)
            TaskFilter.ACTIVE_AND_COMPLETED -> getString(R.string.tasks_empty_active_and_completed)
        }

        adapter.submitList(shown)
        binding.textEmpty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
