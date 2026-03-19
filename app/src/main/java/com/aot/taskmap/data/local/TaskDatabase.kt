package com.aot.taskmap.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aot.taskmap.domain.model.Task

@Database(
    entities = [Task::class],
    version = 4,
    exportSchema = false
)
abstract class TaskDatabase : RoomDatabase() {
    
    abstract fun taskDao(): TaskDao
    
    companion object {
        private const val DEFAULT_MARKER_COLOR = -14575885 // 0xFF2196F3.toInt()

        @Volatile
        private var INSTANCE: TaskDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureTasksSchema(database)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureTasksSchema(database)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureTasksSchema(database)
            }
        }
        
        fun getDatabase(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun ensureTasksSchema(database: SupportSQLiteDatabase) {
            val existingColumns = linkedSetOf<String>()
            database.query("PRAGMA table_info(`tasks`)").use { cursor ->
                val columnIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext() && columnIndex >= 0) {
                    existingColumns += cursor.getString(columnIndex)
                }
            }

            if (existingColumns.isEmpty()) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `address` TEXT NOT NULL DEFAULT '',
                        `radius` INTEGER NOT NULL DEFAULT 100,
                        `markerColor` INTEGER NOT NULL DEFAULT $DEFAULT_MARKER_COLOR,
                        `markerIcon` TEXT NOT NULL DEFAULT 'pin',
                        `category` TEXT NOT NULL DEFAULT 'general',
                        `autoRemoveAfterTrigger` INTEGER NOT NULL DEFAULT 0,
                        `isCompleted` INTEGER NOT NULL DEFAULT 0,
                        `isNotificationEnabled` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        `completedAt` INTEGER
                    )
                    """.trimIndent()
                )
                return
            }

            addColumnIfMissing(
                database,
                existingColumns,
                "description",
                "ALTER TABLE `tasks` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''"
            )
            addColumnIfMissing(
                database,
                existingColumns,
                "address",
                "ALTER TABLE `tasks` ADD COLUMN `address` TEXT NOT NULL DEFAULT ''"
            )
            addColumnIfMissing(
                database,
                existingColumns,
                "radius",
                "ALTER TABLE `tasks` ADD COLUMN `radius` INTEGER NOT NULL DEFAULT 100"
            )
            addColumnIfMissing(
                database,
                existingColumns,
                "markerColor",
                "ALTER TABLE `tasks` ADD COLUMN `markerColor` INTEGER NOT NULL DEFAULT $DEFAULT_MARKER_COLOR"
            )
            addColumnIfMissing(
                database,
                existingColumns,
                "markerIcon",
                "ALTER TABLE `tasks` ADD COLUMN `markerIcon` TEXT NOT NULL DEFAULT 'pin'"
            )
            addColumnIfMissing(
                database,
                existingColumns,
                "category",
                "ALTER TABLE `tasks` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'general'"
            )
            addColumnIfMissing(
                database,
                existingColumns,
                "autoRemoveAfterTrigger",
                "ALTER TABLE `tasks` ADD COLUMN `autoRemoveAfterTrigger` INTEGER NOT NULL DEFAULT 0"
            )
            addColumnIfMissing(
                database,
                existingColumns,
                "isCompleted",
                "ALTER TABLE `tasks` ADD COLUMN `isCompleted` INTEGER NOT NULL DEFAULT 0"
            )
            addColumnIfMissing(
                database,
                existingColumns,
                "isNotificationEnabled",
                "ALTER TABLE `tasks` ADD COLUMN `isNotificationEnabled` INTEGER NOT NULL DEFAULT 1"
            )
            addColumnIfMissing(
                database,
                existingColumns,
                "createdAt",
                "ALTER TABLE `tasks` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0"
            )
            addColumnIfMissing(
                database,
                existingColumns,
                "completedAt",
                "ALTER TABLE `tasks` ADD COLUMN `completedAt` INTEGER"
            )
        }

        private fun addColumnIfMissing(
            database: SupportSQLiteDatabase,
            existingColumns: MutableSet<String>,
            columnName: String,
            alterSql: String
        ) {
            if (existingColumns.contains(columnName)) return
            database.execSQL(alterSql)
            existingColumns += columnName
        }
    }
}
