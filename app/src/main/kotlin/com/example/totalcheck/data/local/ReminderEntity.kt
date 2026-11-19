package com.example.totalcheck.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CheckItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["checklistId"]),
        Index(value = ["itemId"])
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    val itemId: Long? = null,
    val hour: Int,
    val minute: Int,
    val repeatDaysBitmask: Int = 0,
    val triggerEpochMillis: Long = 0L,
    val onlyIfUnchecked: Boolean = true,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val scheduleKind: String = KIND_REMINDER
) {
    companion object {
        const val KIND_REMINDER = "REMINDER"
        const val KIND_AUTO_RESTORE = "AUTO_RESTORE"
    }
}
