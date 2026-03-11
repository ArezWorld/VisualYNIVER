package com.aot.taskmap.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aot.taskmap.domain.model.Task

@Database(
    entities = [Task::class],
    version = 2,
    exportSchema = false
)
abstract class TaskDatabase : RoomDatabase() {
    
    abstract fun taskDao(): TaskDao
    
    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null
        
        fun getDatabase(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
