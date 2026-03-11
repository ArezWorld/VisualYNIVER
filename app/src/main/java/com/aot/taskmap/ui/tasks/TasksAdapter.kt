package com.aot.taskmap.ui.tasks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aot.taskmap.databinding.ItemTaskBinding
import com.aot.taskmap.domain.model.Task

class TasksAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onTaskToggleRequest: (task: Task, newChecked: Boolean, position: Int) -> Unit,
    private val onTaskDelete: (Task) -> Unit,
    private val onTaskNavigate: (Task) -> Unit
) : ListAdapter<Task, TasksAdapter.TaskViewHolder>(TaskDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TaskViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun restoreCheckState(position: Int) {
        if (position in 0 until itemCount) {
            notifyItemChanged(position)
        }
    }
    
    inner class TaskViewHolder(
        private val binding: ItemTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(task: Task) {
            binding.apply {
                textTitle.text = task.title
                textDescription.text = task.description
                textLocation.text = "${task.latitude}, ${task.longitude}"
                checkBox.setOnCheckedChangeListener(null)
                checkBox.isChecked = task.isCompleted

                // Strike through if completed
                textTitle.paint.isStrikeThruText = task.isCompleted
                textDescription.paint.isStrikeThruText = task.isCompleted

                root.setOnClickListener { onTaskClick(task) }
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION && isChecked != task.isCompleted) {
                        onTaskToggleRequest(task, isChecked, position)
                    }
                }
                buttonDelete.setOnClickListener { onTaskDelete(task) }
                buttonNavigate.setOnClickListener { onTaskNavigate(task) }
            }
        }
    }
    
    class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem == newItem
        }
    }
}
