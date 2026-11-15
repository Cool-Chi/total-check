package com.example.totalcheck.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChecklistEntity::class, CheckItemEntity::class, ReminderEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun checklistDao(): ChecklistDao

    abstract fun reminderDao(): ReminderDao

    companion object {
        private const val DATABASE_NAME = "totalcheck.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `checklistId` INTEGER NOT NULL,
                        `itemId` INTEGER,
                        `hour` INTEGER NOT NULL,
                        `minute` INTEGER NOT NULL,
                        `repeatDaysBitmask` INTEGER NOT NULL,
                        `triggerEpochMillis` INTEGER NOT NULL,
                        `onlyIfUnchecked` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`checklistId`) REFERENCES `checklists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`itemId`) REFERENCES `check_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminders_checklistId` ON `reminders` (`checklistId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminders_itemId` ON `reminders` (`itemId`)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
