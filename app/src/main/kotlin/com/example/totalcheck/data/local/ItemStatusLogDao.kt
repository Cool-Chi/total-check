package com.example.totalcheck.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemStatusLogDao {

    @Insert
    suspend fun insertLog(log: ItemStatusLogEntity): Long

    @Insert
    suspend fun insertLogs(logs: List<ItemStatusLogEntity>)

    @Query(
        "SELECT * FROM item_status_logs WHERE itemId = :itemId ORDER BY occurredAt DESC, id DESC LIMIT :limit"
    )
    fun observeLogsForItem(itemId: Long, limit: Int = 50): Flow<List<ItemStatusLogEntity>>

    @Query(
        "SELECT * FROM item_status_logs WHERE checklistId = :checklistId ORDER BY occurredAt DESC, id DESC LIMIT :limit"
    )
    fun observeLogsForChecklist(checklistId: Long, limit: Int = 100): Flow<List<ItemStatusLogEntity>>

    @Query("SELECT * FROM item_status_logs ORDER BY occurredAt DESC, id DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 100): List<ItemStatusLogEntity>
}
