package com.example.totalcheck.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "item_status_logs",
    foreignKeys = [
        ForeignKey(
            entity = CheckItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["checklistId"]),
        Index(value = ["occurredAt"])
    ]
)
data class ItemStatusLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val checklistId: Long,
    val actionType: String,
    val previousValue: String = "",
    val newValue: String = "",
    val occurredAt: Long = System.currentTimeMillis()
) {
    object ActionType {
        const val CREATED = "CREATED"
        const val CHECKED = "CHECKED"
        const val UNCHECKED = "UNCHECKED"
        const val RENAMED = "RENAMED"
        const val ARCHIVED = "ARCHIVED"
        const val UNARCHIVED = "UNARCHIVED"
        const val AUTO_RESTORED = "AUTO_RESTORED"
        const val RESET = "RESET"
        const val SCHEDULE_UPDATED = "SCHEDULE_UPDATED"
    }
}
