package com.aot.taskmap.ui.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aot.taskmap.data.local.SettingsPreferences
import com.aot.taskmap.databinding.FragmentTasksBinding
import com.aot.taskmap.domain.model.Task
import com.aot.taskmap.ui.map.MapViewModel
import kotlinx.coroutines.launch

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by activityViewModels()
    private lateinit var adapter: TasksAdapter

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
        observeTasks()
    }

    private fun setupRecyclerView() {
        adapter = TasksAdapter(
            onTaskClick = { task ->
                // Показываем детали задачи при необходимости
            },
            onTaskToggle = { task ->
                if (SettingsPreferences.isConfirmCompleteEnabled(requireContext())) {
                    val action = if (task.isCompleted) "вернуть в работу" else "отметить выполненной"
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Подтвердить действие")
                        .setMessage("Вы уверены, что хотите $action?")
                        .setPositiveButton("Да") { _, _ ->
                            viewModel.toggleTaskCompletion(task)
                        }
                        .setNegativeButton("Отмена", null)
                        .show()
                } else {
                    viewModel.toggleTaskCompletion(task)
                }
            },
            onTaskDelete = { task ->
                viewModel.deleteTask(task)
            }
        )

        binding.recyclerViewTasks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TasksFragment.adapter
        }
    }

    private fun observeTasks() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.activeTasks.collect { tasks ->
                        adapter.submitList(tasks)
                        binding.textEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
