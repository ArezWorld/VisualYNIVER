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
    private val onTaskToggle: (Task) -> Unit,
    private val onTaskDelete: (Task) -> Unit
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
    
    inner class TaskViewHolder(
        private val binding: ItemTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(task: Task) {
            binding.apply {
                textTitle.text = task.title
                textDescription.text = task.description
                textLocation.text = "${task.latitude}, ${task.longitude}"
                checkBox.isChecked = task.isCompleted
                
                // Strike through if completed
                textTitle.paint.isStrikeThruText = task.isCompleted
                textDescription.paint.isStrikeThruText = task.isCompleted
                
                root.setOnClickListener { onTaskClick(task) }
                checkBox.setOnClickListener { onTaskToggle(task) }
                buttonDelete.setOnClickListener { onTaskDelete(task) }
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
