package com.aot.taskmap.data.repository

import com.aot.taskmap.data.local.TaskDao
import com.aot.taskmap.domain.model.Task
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {
    
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    val activeTasks: Flow<List<Task>> = taskDao.getActiveTasks()
    val completedTasks: Flow<List<Task>> = taskDao.getCompletedTasks()
    
    suspend fun getTaskById(taskId: Long): Task? {
        return taskDao.getTaskById(taskId)
    }
    
    suspend fun getTasksWithNotifications(): List<Task> {
        return taskDao.getTasksWithNotifications()
    }

    fun getTasksWithNotificationsFlow(): Flow<List<Task>> {
        return taskDao.getTasksWithNotificationsFlow()
    }
    
    suspend fun insertTask(task: Task): Long {
        return taskDao.insertTask(task)
    }

    suspend fun insertTasks(tasks: List<Task>) {
        taskDao.insertTasks(tasks)
    }
    
    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task)
    }
    
    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
    }
    
    suspend fun deleteTaskById(taskId: Long) {
        taskDao.deleteTaskById(taskId)
    }
    
    suspend fun toggleTaskCompletion(taskId: Long, isCompleted: Boolean) {
        val completedAt = if (isCompleted) System.currentTimeMillis() else null
        taskDao.updateTaskCompletion(taskId, isCompleted, completedAt)
    }
}
