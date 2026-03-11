package com.aot.taskmap.domain.usecase

import com.aot.taskmap.data.repository.TaskRepository
import com.aot.taskmap.domain.model.Task
import kotlinx.coroutines.flow.Flow

class GetAllTasksUseCase(private val repository: TaskRepository) {
    operator fun invoke(): Flow<List<Task>> = repository.allTasks
}

class GetActiveTasksUseCase(private val repository: TaskRepository) {
    operator fun invoke(): Flow<List<Task>> = repository.activeTasks
}

class GetCompletedTasksUseCase(private val repository: TaskRepository) {
    operator fun invoke(): Flow<List<Task>> = repository.completedTasks
}

class GetTaskByIdUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskId: Long): Task? = repository.getTaskById(taskId)
}

class CreateTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task): Long = repository.insertTask(task)
}

class UpdateTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task) = repository.updateTask(task)
}

class DeleteTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task) = repository.deleteTask(task)
}

class ToggleTaskCompletionUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskId: Long, isCompleted: Boolean) {
        repository.toggleTaskCompletion(taskId, isCompleted)
    }
}

class GetTasksForGeofencingUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(): List<Task> = repository.getTasksWithNotifications()
}
