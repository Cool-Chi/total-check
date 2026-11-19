package com.example.totalcheck.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChecklistEntity::class,
        CheckItemEntity::class,
        ReminderEntity::class,
        ItemStatusLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun checklistDao(): ChecklistDao

    abstract fun reminderDao(): ReminderDao

    abstract fun itemStatusLogDao(): ItemStatusLogDao

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

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `check_items` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `check_items` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `check_items` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `check_items` ADD COLUMN `lastCheckedAt` INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE `check_items` ADD COLUMN `archivedAt` INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE `check_items` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `check_items` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `check_items` ADD COLUMN `quantity` INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE `check_items` ADD COLUMN `priority` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE `check_items` SET `createdAt` = (strftime('%s', 'now') * 1000), " +
                        "`updatedAt` = (strftime('%s', 'now') * 1000) WHERE `createdAt` = 0"
                )
                db.execSQL(
                    "ALTER TABLE `reminders` ADD COLUMN `scheduleKind` TEXT NOT NULL DEFAULT 'REMINDER'"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `item_status_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemId` INTEGER NOT NULL,
                        `checklistId` INTEGER NOT NULL,
                        `actionType` TEXT NOT NULL,
                        `previousValue` TEXT NOT NULL,
                        `newValue` TEXT NOT NULL,
                        `occurredAt` INTEGER NOT NULL,
                        FOREIGN KEY(`itemId`) REFERENCES `check_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`checklistId`) REFERENCES `checklists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_item_status_logs_itemId` ON `item_status_logs` (`itemId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_item_status_logs_checklistId` ON `item_status_logs` (`checklistId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_item_status_logs_occurredAt` ON `item_status_logs` (`occurredAt`)"
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
