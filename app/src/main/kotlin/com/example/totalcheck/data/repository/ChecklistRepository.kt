package com.example.totalcheck.data.repository

import android.content.Context
import com.example.totalcheck.R
import com.example.totalcheck.data.local.AppDatabase
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistDao
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.data.local.ReminderDao
import com.example.totalcheck.data.local.ReminderEntity
import com.example.totalcheck.data.parser.ParsedItemDraft
import com.example.totalcheck.data.reminder.NotificationHelper
import com.example.totalcheck.data.reminder.ReminderFormatter
import com.example.totalcheck.data.reminder.ReminderScheduler
import com.example.totalcheck.data.widget.WidgetSyncManager
import kotlinx.coroutines.flow.Flow

class ChecklistRepository(
    context: Context,
    private val dao: ChecklistDao,
    private val reminderDao: ReminderDao = AppDatabase.getInstance(context).reminderDao(),
) {
    private val appContext = context.applicationContext

    fun observeAllChecklists(): Flow<List<ChecklistEntity>> = dao.observeAllChecklists()

    suspend fun getAllChecklists(): List<ChecklistEntity> = dao.getAllChecklists()

    fun observeAllItems(): Flow<List<CheckItemEntity>> = dao.observeAllItems()

    suspend fun getAllItems(): List<CheckItemEntity> = dao.getAllItems()

    fun observeItemsByChecklist(checklistId: Long): Flow<List<CheckItemEntity>> =
        dao.observeItemsByChecklist(checklistId)

    suspend fun getItemsByChecklist(checklistId: Long): List<CheckItemEntity> =
        dao.getItemsByChecklist(checklistId)

    suspend fun addChecklist(name: String): Long {
        val nextSortOrder = (dao.getMaxChecklistSortOrder() ?: -1) + 1
        val id = dao.insertChecklist(ChecklistEntity(name = name, sortOrder = nextSortOrder))
        WidgetSyncManager.notifyDataChanged(appContext)
        return id
    }

    suspend fun createChecklistWithItems(
        name: String,
        items: List<ParsedItemDraft>
    ): Long {
        val nextSortOrder = (dao.getMaxChecklistSortOrder() ?: -1) + 1
        val checklist = ChecklistEntity(name = name, sortOrder = nextSortOrder)
        val entities = items.mapIndexed { index, draft ->
            CheckItemEntity(
                checklistId = 0L,
                label = draft.label,
                isChecked = draft.isChecked,
                sortOrder = index
            )
        }
        val newId = dao.createChecklistWithItems(checklist = checklist, items = entities)
        WidgetSyncManager.notifyDataChanged(appContext)
        return newId
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
        cancelChecklistReminders(id)
        dao.deleteChecklist(id)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun deleteChecklist(checklist: ChecklistEntity) {
        cancelChecklistReminders(checklist.id)
        dao.deleteChecklist(checklist)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun deleteChecklistWithSnapshot(checklist: ChecklistEntity): List<CheckItemEntity> {
        val itemsSnapshot = dao.getItemsByChecklist(checklist.id)
        cancelChecklistReminders(checklist.id)
        dao.deleteChecklist(checklist.id)
        WidgetSyncManager.notifyDataChanged(appContext)
        return itemsSnapshot
    }

    suspend fun restoreChecklistWithItems(
        checklist: ChecklistEntity,
        items: List<CheckItemEntity>
    ) {
        dao.restoreChecklistWithItems(checklist = checklist, items = items)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun reorderChecklists(
        orderedIds: List<Long>,
        currentChecklists: List<ChecklistEntity>
    ) {
        if (orderedIds.isEmpty() || currentChecklists.isEmpty()) return
        val byId = currentChecklists.associateBy { it.id }
        val updated = orderedIds.mapIndexedNotNull { index, id ->
            val entity = byId[id] ?: return@mapIndexedNotNull null
            if (entity.sortOrder != index) entity.copy(sortOrder = index) else null
        }
        if (updated.isNotEmpty()) {
            dao.updateChecklists(updated)
            WidgetSyncManager.notifyDataChanged(appContext)
        }
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
        reminderDao.getItemLevelReminder(item.id)?.let { reminder ->
            ReminderScheduler.cancelReminder(appContext, reminder.id)
            NotificationHelper.cancelNotificationForReminder(appContext, reminder.id)
        }
        dao.deleteItem(item)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun setItemChecked(itemId: Long, isChecked: Boolean) {
        dao.updateItemChecked(itemId = itemId, isChecked = isChecked)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun toggleItemChecked(itemId: Long) {
        dao.toggleItemChecked(itemId)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun resetChecklistItems(checklistId: Long): List<Long> {
        val checkedSnapshot = dao.getCheckedItemIds(checklistId)
        dao.resetChecklistItems(checklistId)
        WidgetSyncManager.notifyDataChanged(appContext)
        return checkedSnapshot
    }

    suspend fun checkAllChecklistItems(checklistId: Long) {
        dao.checkAllChecklistItems(checklistId)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    fun observeAllReminders(): Flow<List<ReminderEntity>> = reminderDao.observeAllReminders()

    suspend fun upsertReminder(
        checklistId: Long,
        itemId: Long?,
        hour: Int,
        minute: Int,
        repeatDaysBitmask: Int,
        onlyIfUnchecked: Boolean,
    ): ReminderEntity {
        val clampedHour = hour.coerceIn(0, 23)
        val clampedMinute = minute.coerceIn(0, 59)
        val maskedDays = repeatDaysBitmask and ReminderFormatter.DAILY_MASK
        val triggerEpochMillis = ReminderScheduler.computeNextTriggerEpochMillis(
            hour = clampedHour,
            minute = clampedMinute,
            repeatDaysBitmask = maskedDays,
        )
        val existing = if (itemId == null) {
            reminderDao.getChecklistLevelReminder(checklistId)
        } else {
            reminderDao.getItemLevelReminder(itemId)
        }
        val saved = if (existing != null) {
            ReminderScheduler.cancelReminder(appContext, existing.id)
            NotificationHelper.cancelNotificationForReminder(appContext, existing.id)
            val updated = existing.copy(
                hour = clampedHour,
                minute = clampedMinute,
                repeatDaysBitmask = maskedDays,
                triggerEpochMillis = triggerEpochMillis,
                onlyIfUnchecked = onlyIfUnchecked,
                isEnabled = true,
            )
            reminderDao.updateReminder(updated)
            updated
        } else {
            val created = ReminderEntity(
                checklistId = checklistId,
                itemId = itemId,
                hour = clampedHour,
                minute = clampedMinute,
                repeatDaysBitmask = maskedDays,
                triggerEpochMillis = triggerEpochMillis,
                onlyIfUnchecked = onlyIfUnchecked,
                isEnabled = true,
            )
            val newId = reminderDao.insertReminder(created)
            created.copy(id = newId)
        }
        ReminderScheduler.scheduleReminder(appContext, saved)
        WidgetSyncManager.notifyDataChanged(appContext)
        return saved
    }

    suspend fun deleteReminder(reminderId: Long) {
        ReminderScheduler.cancelReminder(appContext, reminderId)
        NotificationHelper.cancelNotificationForReminder(appContext, reminderId)
        reminderDao.deleteReminderById(reminderId)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun syncWidgetsNow() {
        WidgetSyncManager.notifyDataChangedNow(appContext)
    }

    private suspend fun cancelChecklistReminders(checklistId: Long) {
        val associated = reminderDao.getRemindersByChecklist(checklistId)
        if (associated.isNotEmpty()) {
            ReminderScheduler.cancelReminders(appContext, associated)
            associated.forEach { reminder ->
                NotificationHelper.cancelNotificationForReminder(appContext, reminder.id)
            }
        }
    }

    suspend fun restoreCheckedStates(itemIds: List<Long>) {
        if (itemIds.isEmpty()) return
        dao.restoreCheckedStates(itemIds)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun reorderChecklistItems(
        fullOrderedIds: List<Long>,
        currentItems: List<CheckItemEntity>
    ) {
        if (fullOrderedIds.isEmpty() || currentItems.isEmpty()) return
        val byId = currentItems.associateBy { it.id }
        val updated = fullOrderedIds.mapIndexedNotNull { index, id ->
            val entity = byId[id] ?: return@mapIndexedNotNull null
            if (entity.sortOrder != index) entity.copy(sortOrder = index) else null
        }
        if (updated.isNotEmpty()) {
            dao.updateItems(updated)
            WidgetSyncManager.notifyDataChanged(appContext)
        }
    }

    suspend fun seedInitialDataIfNeeded() {
        if (dao.getChecklistCount() > 0) return

        val res = appContext.resources
        val carryOnId = dao.insertChecklist(
            ChecklistEntity(name = appContext.getString(R.string.seed_checklist_carry_on), sortOrder = 0)
        )
        val labId = dao.insertChecklist(
            ChecklistEntity(name = appContext.getString(R.string.seed_checklist_lab), sortOrder = 1)
        )
        val travelId = dao.insertChecklist(
            ChecklistEntity(name = appContext.getString(R.string.seed_checklist_travel), sortOrder = 2)
        )

        val carryOnItems = res.getStringArray(R.array.seed_items_carry_on).mapIndexed { idx, label ->
            CheckItemEntity(checklistId = carryOnId, label = label, isChecked = idx >= 4, sortOrder = idx)
        }
        val labItems = res.getStringArray(R.array.seed_items_lab).mapIndexed { idx, label ->
            CheckItemEntity(checklistId = labId, label = label, isChecked = idx >= 4, sortOrder = idx)
        }
        val travelItems = res.getStringArray(R.array.seed_items_travel).mapIndexed { idx, label ->
            CheckItemEntity(checklistId = travelId, label = label, isChecked = idx >= 4, sortOrder = idx)
        }

        dao.insertItems(carryOnItems + labItems + travelItems)
        WidgetSyncManager.notifyDataChanged(appContext)
    }
}
