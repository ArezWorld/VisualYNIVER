package com.aot.taskmap.ui.settings

import android.content.Context
import androidx.room.Room
import com.aot.taskmap.data.local.TaskDatabase
import java.io.File

object AppDataResetter {

    private const val TASK_DATABASE_NAME = "task_database"
    private val sharedPreferenceFiles = listOf(
        "settings_prefs",
        "map_search_history",
        "AOT_Preferences",
        "in_app_update_prefs"
    )

    fun clearAllLocalData(context: Context) {
        val appContext = context.applicationContext

        runCatching {
            // Принудительно удаляем все задачи из локальной БД.
            val db = Room.databaseBuilder(
                appContext,
                TaskDatabase::class.java,
                TASK_DATABASE_NAME
            ).build()
            db.clearAllTables()
            db.close()
        }

        sharedPreferenceFiles.forEach { prefsName ->
            runCatching {
                appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            }
        }

        runCatching {
            appContext.deleteDatabase(TASK_DATABASE_NAME)
        }
        runCatching {
            val walFile = File("${appContext.getDatabasePath(TASK_DATABASE_NAME).absolutePath}-wal")
            if (walFile.exists()) walFile.delete()
        }
        runCatching {
            val shmFile = File("${appContext.getDatabasePath(TASK_DATABASE_NAME).absolutePath}-shm")
            if (shmFile.exists()) shmFile.delete()
        }
        runCatching {
            val avatarsDir = File(appContext.filesDir, "avatars")
            if (avatarsDir.exists()) avatarsDir.deleteRecursively()
        }
    }
}
