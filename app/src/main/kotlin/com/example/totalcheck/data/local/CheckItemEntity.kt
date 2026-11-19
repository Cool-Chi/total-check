package com.example.totalcheck.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "check_items",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("checklistId")]
)
data class CheckItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    val label: String,
    val isChecked: Boolean = false,
    val sortOrder: Int = 0,
    val category: String = "",
    val iconKey: String = "default",
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastCheckedAt: Long? = null,
    val archivedAt: Long? = null,
    val tags: String = "",
    val note: String = "",
    val quantity: Int = 1,
    val priority: Int = 0
)
