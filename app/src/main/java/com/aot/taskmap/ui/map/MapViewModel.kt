package com.aot.taskmap.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aot.taskmap.data.local.TaskDatabase
import com.aot.taskmap.data.repository.TaskRepository
import com.aot.taskmap.domain.model.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val localRepository: TaskRepository

    val activeTasks: StateFlow<List<Task>>
    val completedTasks: StateFlow<List<Task>>

    private val _selectedTask = MutableStateFlow<Task?>(null)
    val selectedTask: StateFlow<Task?> = _selectedTask

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        val taskDao = TaskDatabase.getDatabase(application).taskDao()
        localRepository = TaskRepository(taskDao)

        activeTasks = localRepository.activeTasks
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        completedTasks = localRepository.completedTasks
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        loadTasks()
    }

    fun loadTasks() {
        viewModelScope.launch {
            _isLoading.value = true
            _isLoading.value = false
        }
    }

    fun createTask(
        title: String,
        description: String,
        latitude: Double,
        longitude: Double,
        address: String,
        radius: Int = 100,
        enableNotification: Boolean = true,
        markerColor: Int = 0xFF2196F3.toInt(),
        markerIcon: String = "pin",
        category: String = "general",
        autoRemoveAfterTrigger: Boolean = false
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val localTask = Task(
                title = title,
                description = description,
                latitude = latitude,
                longitude = longitude,
                address = address,
                radius = radius,
                markerColor = markerColor,
                markerIcon = markerIcon,
                category = category,
                autoRemoveAfterTrigger = autoRemoveAfterTrigger,
                isNotificationEnabled = enableNotification
            )
            localRepository.insertTask(localTask)

            _isLoading.value = false
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            localRepository.updateTask(task)

            _isLoading.value = false
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            localRepository.deleteTask(task)

            if (_selectedTask.value?.id == task.id) {
                _selectedTask.value = null
            }

            _isLoading.value = false
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            localRepository.toggleTaskCompletion(task.id, !task.isCompleted)

            _isLoading.value = false
        }
    }

    fun selectTask(task: Task?) {
        _selectedTask.value = task
    }

    fun updateCurrentLocation(latitude: Double, longitude: Double) {
        _currentLocation.value = Pair(latitude, longitude)
    }

    fun clearError() {
        _error.value = null
    }
}
