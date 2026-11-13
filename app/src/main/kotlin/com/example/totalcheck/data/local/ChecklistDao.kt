package com.example.totalcheck.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    @Query("SELECT * FROM checklists ORDER BY sortOrder ASC, id ASC")
    fun observeAllChecklists(): Flow<List<ChecklistEntity>>

    // Keep original item order when toggling checked state so cards stay in place.
    @Query("SELECT * FROM check_items WHERE checklistId = :checklistId ORDER BY sortOrder ASC, id DESC")
    fun observeItemsByChecklist(checklistId: Long): Flow<List<CheckItemEntity>>

    @Query("SELECT COUNT(*) FROM checklists")
    suspend fun getChecklistCount(): Int

    @Query("SELECT MIN(sortOrder) FROM check_items WHERE checklistId = :checklistId")
    suspend fun getMinItemSortOrder(checklistId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklist(checklist: ChecklistEntity): Long

    @Update
    suspend fun updateChecklist(checklist: ChecklistEntity)

    @Delete
    suspend fun deleteChecklist(checklist: ChecklistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: CheckItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<CheckItemEntity>)

    @Update
    suspend fun updateItem(item: CheckItemEntity)

    @Delete
    suspend fun deleteItem(item: CheckItemEntity)

    @Query("UPDATE check_items SET isChecked = :isChecked WHERE id = :itemId")
    suspend fun updateItemChecked(itemId: Long, isChecked: Boolean)

    @Query("UPDATE check_items SET isChecked = 0 WHERE checklistId = :checklistId")
    suspend fun resetChecklistItems(checklistId: Long)

    @Query("UPDATE check_items SET isChecked = 1 WHERE id IN (:itemIds)")
    suspend fun restoreCheckedStates(itemIds: List<Long>)
}
