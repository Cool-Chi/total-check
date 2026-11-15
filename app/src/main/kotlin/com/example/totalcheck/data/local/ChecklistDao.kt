package com.example.totalcheck.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    @Query("SELECT * FROM checklists ORDER BY sortOrder ASC, id ASC")
    fun observeAllChecklists(): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklists ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllChecklists(): List<ChecklistEntity>

    @Query("SELECT * FROM checklists WHERE id = :id LIMIT 1")
    suspend fun getChecklistById(id: Long): ChecklistEntity?

    @Query("SELECT * FROM check_items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: Long): CheckItemEntity?

    // Keep original item order when toggling checked state so cards stay in place.
    @Query("SELECT * FROM check_items ORDER BY sortOrder ASC, id DESC")
    fun observeAllItems(): Flow<List<CheckItemEntity>>

    // Keep original item order when toggling checked state so cards stay in place.
    @Query("SELECT * FROM check_items ORDER BY sortOrder ASC, id DESC")
    suspend fun getAllItems(): List<CheckItemEntity>

    // Keep original item order when toggling checked state so cards stay in place.
    @Query("SELECT * FROM check_items WHERE checklistId = :checklistId ORDER BY sortOrder ASC, id DESC")
    fun observeItemsByChecklist(checklistId: Long): Flow<List<CheckItemEntity>>

    @Query("SELECT COUNT(*) FROM checklists")
    suspend fun getChecklistCount(): Int

    @Query("SELECT MAX(sortOrder) FROM checklists")
    suspend fun getMaxChecklistSortOrder(): Int?

    @Query("SELECT MIN(sortOrder) FROM check_items WHERE checklistId = :checklistId")
    suspend fun getMinItemSortOrder(checklistId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklist(checklist: ChecklistEntity): Long

    @Query("UPDATE checklists SET name = :newName WHERE id = :id")
    suspend fun updateChecklistName(id: Long, newName: String)

    @Update
    suspend fun updateChecklist(checklist: ChecklistEntity)

    @Update
    suspend fun updateChecklists(checklists: List<ChecklistEntity>)

    @Query("DELETE FROM checklists WHERE id = :id")
    suspend fun deleteChecklist(id: Long)

    @Query("SELECT * FROM check_items WHERE checklistId = :checklistId ORDER BY sortOrder ASC, id DESC")
    suspend fun getItemsByChecklist(checklistId: Long): List<CheckItemEntity>

    @Delete
    suspend fun deleteChecklist(checklist: ChecklistEntity)

    @Transaction
    suspend fun restoreChecklistWithItems(
        checklist: ChecklistEntity,
        items: List<CheckItemEntity>
    ) {
        insertChecklist(checklist)
        if (items.isNotEmpty()) {
            insertItems(items)
        }
    }

    @Transaction
    suspend fun createChecklistWithItems(
        checklist: ChecklistEntity,
        items: List<CheckItemEntity>
    ): Long {
        val newChecklistId = insertChecklist(checklist)
        if (items.isNotEmpty()) {
            insertItems(items.map { it.copy(checklistId = newChecklistId) })
        }
        return newChecklistId
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: CheckItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<CheckItemEntity>)

    @Update
    suspend fun updateItem(item: CheckItemEntity)

    @Update
    suspend fun updateItems(items: List<CheckItemEntity>)

    @Query("UPDATE check_items SET label = :newLabel WHERE id = :itemId")
    suspend fun updateItemLabel(itemId: Long, newLabel: String)

    @Delete
    suspend fun deleteItem(item: CheckItemEntity)

    @Query("UPDATE check_items SET isChecked = :isChecked WHERE id = :itemId")
    suspend fun updateItemChecked(itemId: Long, isChecked: Boolean)

    @Query("UPDATE check_items SET isChecked = NOT isChecked WHERE id = :itemId")
    suspend fun toggleItemChecked(itemId: Long)

    @Query("SELECT id FROM check_items WHERE checklistId = :checklistId AND isChecked = 1")
    suspend fun getCheckedItemIds(checklistId: Long): List<Long>

    @Query("UPDATE check_items SET isChecked = 0 WHERE checklistId = :checklistId")
    suspend fun resetChecklistItems(checklistId: Long)

    @Query("UPDATE check_items SET isChecked = 1 WHERE checklistId = :checklistId")
    suspend fun checkAllChecklistItems(checklistId: Long)

    @Query("UPDATE check_items SET isChecked = 1 WHERE id IN (:itemIds)")
    suspend fun restoreCheckedStates(itemIds: List<Long>)
}
