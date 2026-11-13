package com.example.totalcheck.data.repository

import android.content.Context
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistDao
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.data.widget.WidgetSyncManager
import kotlinx.coroutines.flow.Flow

class ChecklistRepository(
    context: Context,
    private val dao: ChecklistDao
) {
    private val appContext = context.applicationContext

    fun observeAllChecklists(): Flow<List<ChecklistEntity>> = dao.observeAllChecklists()

    fun observeAllItems(): Flow<List<CheckItemEntity>> = dao.observeAllItems()

    fun observeItemsByChecklist(checklistId: Long): Flow<List<CheckItemEntity>> =
        dao.observeItemsByChecklist(checklistId)

    suspend fun addChecklist(name: String): Long {
        val nextSortOrder = (dao.getMaxChecklistSortOrder() ?: -1) + 1
        val id = dao.insertChecklist(ChecklistEntity(name = name, sortOrder = nextSortOrder))
        WidgetSyncManager.notifyDataChanged(appContext)
        return id
    }

    suspend fun renameChecklist(id: Long, newName: String) {
        dao.updateChecklistName(id = id, newName = newName)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun updateChecklist(checklist: ChecklistEntity) {
        dao.updateChecklist(checklist)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun deleteChecklist(id: Long) {
        dao.deleteChecklist(id)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun deleteChecklist(checklist: ChecklistEntity) {
        dao.deleteChecklist(checklist)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun addItem(checklistId: Long, label: String): Long {
        val minOrder = dao.getMinItemSortOrder(checklistId) ?: 1
        val id = dao.insertItem(
            CheckItemEntity(
                checklistId = checklistId,
                label = label,
                isChecked = false,
                sortOrder = minOrder - 1
            )
        )
        WidgetSyncManager.notifyDataChanged(appContext)
        return id
    }

    suspend fun updateItem(item: CheckItemEntity) {
        dao.updateItem(item)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun updateItemLabel(itemId: Long, newLabel: String) {
        dao.updateItemLabel(itemId = itemId, newLabel = newLabel)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun restoreItem(item: CheckItemEntity) {
        dao.insertItem(item)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun deleteItem(item: CheckItemEntity) {
        dao.deleteItem(item)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun setItemChecked(itemId: Long, isChecked: Boolean) {
        dao.updateItemChecked(itemId = itemId, isChecked = isChecked)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun resetChecklistItems(checklistId: Long): List<Long> {
        val checkedSnapshot = dao.getCheckedItemIds(checklistId)
        dao.resetChecklistItems(checklistId)
        WidgetSyncManager.notifyDataChanged(appContext)
        return checkedSnapshot
    }

    suspend fun restoreCheckedStates(itemIds: List<Long>) {
        if (itemIds.isEmpty()) return
        dao.restoreCheckedStates(itemIds)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun seedInitialDataIfNeeded() {
        if (dao.getChecklistCount() > 0) return

        val carryOnId = dao.insertChecklist(ChecklistEntity(name = "隨身包", sortOrder = 0))
        val labId = dao.insertChecklist(ChecklistEntity(name = "實驗室", sortOrder = 1))
        val travelId = dao.insertChecklist(ChecklistEntity(name = "返鄉行李", sortOrder = 2))

        val initialItems = listOf(
            CheckItemEntity(checklistId = carryOnId, label = "鑰匙與門禁卡", isChecked = false, sortOrder = 0),
            CheckItemEntity(checklistId = carryOnId, label = "行動電源 (10000mAh)", isChecked = false, sortOrder = 1),
            CheckItemEntity(checklistId = carryOnId, label = "Type-C 充電線", isChecked = false, sortOrder = 2),
            CheckItemEntity(checklistId = carryOnId, label = "抗噪耳機", isChecked = false, sortOrder = 3),
            CheckItemEntity(checklistId = carryOnId, label = "錢包與悠遊卡", isChecked = true, sortOrder = 4),
            CheckItemEntity(checklistId = carryOnId, label = "摺疊傘", isChecked = true, sortOrder = 5),

            CheckItemEntity(checklistId = labId, label = "筆記型電腦", isChecked = false, sortOrder = 0),
            CheckItemEntity(checklistId = labId, label = "實驗紀錄本", isChecked = false, sortOrder = 1),
            CheckItemEntity(checklistId = labId, label = "防靜電手環", isChecked = false, sortOrder = 2),
            CheckItemEntity(checklistId = labId, label = "USB-C 擴充塢", isChecked = false, sortOrder = 3),
            CheckItemEntity(checklistId = labId, label = "識別證", isChecked = true, sortOrder = 4),
            CheckItemEntity(checklistId = labId, label = "白袍", isChecked = true, sortOrder = 5),

            CheckItemEntity(checklistId = travelId, label = "換洗衣物 (3 天份)", isChecked = false, sortOrder = 0),
            CheckItemEntity(checklistId = travelId, label = "筆電充電器", isChecked = false, sortOrder = 1),
            CheckItemEntity(checklistId = travelId, label = "個人盥洗包", isChecked = false, sortOrder = 2),
            CheckItemEntity(checklistId = travelId, label = "高鐵車票 / 證件", isChecked = false, sortOrder = 3),
            CheckItemEntity(checklistId = travelId, label = "備用眼鏡", isChecked = true, sortOrder = 4),
            CheckItemEntity(checklistId = travelId, label = "常備藥品", isChecked = true, sortOrder = 5),
        )
        dao.insertItems(initialItems)
        WidgetSyncManager.notifyDataChanged(appContext)
    }
}
