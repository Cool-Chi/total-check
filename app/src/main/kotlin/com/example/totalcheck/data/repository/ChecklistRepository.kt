package com.example.totalcheck.data.repository

import android.content.Context
import com.example.totalcheck.R
import com.example.totalcheck.data.local.AppDatabase
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistDao
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.data.local.ItemStatusLogDao
import com.example.totalcheck.data.local.ItemStatusLogEntity
import com.example.totalcheck.data.local.ReminderDao
import com.example.totalcheck.data.local.ReminderEntity
import com.example.totalcheck.data.parser.ParsedItemDraft
import com.example.totalcheck.data.reminder.NotificationHelper
import com.example.totalcheck.data.reminder.ReminderFormatter
import com.example.totalcheck.data.reminder.ReminderScheduler
import com.example.totalcheck.data.widget.WidgetSyncManager
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

data class QuickReminderResult(
    val saved: ReminderEntity,
    val previousReminder: ReminderEntity?
)

enum class ReminderLongPressOutcome {
    SET_QUICK_15M,
    SKIPPED_PERIODIC_ONCE,
    CANCELLED
}

data class ReminderLongPressResult(
    val outcome: ReminderLongPressOutcome,
    val reminder: ReminderEntity?,
    val previousReminder: ReminderEntity?
)

class ChecklistRepository(
    context: Context,
    private val dao: ChecklistDao,
    private val reminderDao: ReminderDao = AppDatabase.getInstance(context).reminderDao(),
    private val itemStatusLogDao: ItemStatusLogDao = AppDatabase.getInstance(context).itemStatusLogDao(),
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
        val now = System.currentTimeMillis()
        val minOrder = dao.getMinItemSortOrder(checklistId) ?: 1
        val id = dao.insertItem(
            CheckItemEntity(
                checklistId = checklistId,
                label = label,
                isChecked = false,
                sortOrder = minOrder - 1,
                createdAt = now,
                updatedAt = now
            )
        )
        recordStatusLog(
            itemId = id,
            checklistId = checklistId,
            actionType = ItemStatusLogEntity.ActionType.CREATED,
            newValue = label,
            occurredAt = now
        )
        WidgetSyncManager.notifyDataChanged(appContext)
        return id
    }

    suspend fun updateItem(item: CheckItemEntity) {
        val now = System.currentTimeMillis()
        dao.updateItem(item.copy(updatedAt = now))
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun updateItemLabel(itemId: Long, newLabel: String) {
        val now = System.currentTimeMillis()
        val existing = dao.getItemById(itemId)
        dao.updateItemLabel(itemId = itemId, newLabel = newLabel, updatedAt = now)
        if (existing != null && existing.label != newLabel) {
            recordStatusLog(
                itemId = itemId,
                checklistId = existing.checklistId,
                actionType = ItemStatusLogEntity.ActionType.RENAMED,
                previousValue = existing.label,
                newValue = newLabel,
                occurredAt = now
            )
        }
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun updateItemTags(itemId: Long, serializedTags: String) {
        val now = System.currentTimeMillis()
        val existing = dao.getItemById(itemId)
        dao.updateItemTags(itemId = itemId, newTags = serializedTags, updatedAt = now)
        if (existing != null && existing.tags != serializedTags) {
            recordStatusLog(
                itemId = itemId,
                checklistId = existing.checklistId,
                actionType = ItemStatusLogEntity.ActionType.TAGS_UPDATED,
                previousValue = existing.tags,
                newValue = serializedTags,
                occurredAt = now
            )
        }
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun restoreItem(item: CheckItemEntity) {
        val now = System.currentTimeMillis()
        dao.insertItem(item.copy(updatedAt = now))
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun archiveItem(item: CheckItemEntity) {
        val now = System.currentTimeMillis()
        reminderDao.getItemSchedules(item.id).forEach { schedule ->
            ReminderScheduler.cancelReminder(appContext, schedule.id)
            NotificationHelper.cancelNotificationForReminder(appContext, schedule.id)
        }
        dao.updateItemArchived(
            itemId = item.id,
            isArchived = true,
            updatedAt = now,
            archivedAt = now
        )
        recordStatusLog(
            itemId = item.id,
            checklistId = item.checklistId,
            actionType = ItemStatusLogEntity.ActionType.ARCHIVED,
            previousValue = "active",
            newValue = "archived",
            occurredAt = now
        )
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun unarchiveItem(item: CheckItemEntity) {
        val now = System.currentTimeMillis()
        dao.updateItemArchived(
            itemId = item.id,
            isArchived = false,
            updatedAt = now,
            archivedAt = null
        )
        reminderDao.getItemSchedules(item.id).forEach { schedule ->
            if (schedule.isEnabled) {
                val refreshedTrigger = if (schedule.triggerEpochMillis > now) {
                    schedule.triggerEpochMillis
                } else {
                    ReminderScheduler.computeNextTriggerEpochMillis(
                        hour = schedule.hour,
                        minute = schedule.minute,
                        repeatDaysBitmask = schedule.repeatDaysBitmask,
                    )
                }
                val refreshed = if (refreshedTrigger != schedule.triggerEpochMillis) {
                    schedule.copy(triggerEpochMillis = refreshedTrigger).also {
                        reminderDao.updateReminder(it)
                    }
                } else {
                    schedule
                }
                ReminderScheduler.scheduleReminder(appContext, refreshed)
            }
        }
        recordStatusLog(
            itemId = item.id,
            checklistId = item.checklistId,
            actionType = ItemStatusLogEntity.ActionType.UNARCHIVED,
            previousValue = "archived",
            newValue = "active",
            occurredAt = now
        )
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun deleteItem(item: CheckItemEntity) {
        reminderDao.getItemSchedules(item.id).forEach { schedule ->
            ReminderScheduler.cancelReminder(appContext, schedule.id)
            NotificationHelper.cancelNotificationForReminder(appContext, schedule.id)
        }
        dao.deleteItem(item)
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun setItemChecked(itemId: Long, isChecked: Boolean) {
        val now = System.currentTimeMillis()
        val existing = dao.getItemById(itemId)
        dao.updateItemChecked(itemId = itemId, isChecked = isChecked, updatedAt = now)
        if (existing != null && existing.isChecked != isChecked) {
            recordStatusLog(
                itemId = itemId,
                checklistId = existing.checklistId,
                actionType = if (isChecked) {
                    ItemStatusLogEntity.ActionType.CHECKED
                } else {
                    ItemStatusLogEntity.ActionType.UNCHECKED
                },
                previousValue = existing.isChecked.toString(),
                newValue = isChecked.toString(),
                occurredAt = now
            )
        }
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun toggleItemChecked(itemId: Long) {
        val existing = dao.getItemById(itemId) ?: return
        setItemChecked(itemId = itemId, isChecked = !existing.isChecked)
    }

    suspend fun resetChecklistItems(checklistId: Long): List<Long> {
        val now = System.currentTimeMillis()
        val checkedSnapshot = dao.getCheckedItemIds(checklistId)
        dao.resetChecklistItems(checklistId = checklistId, updatedAt = now)
        if (checkedSnapshot.isNotEmpty()) {
            val logs = checkedSnapshot.map { id ->
                ItemStatusLogEntity(
                    itemId = id,
                    checklistId = checklistId,
                    actionType = ItemStatusLogEntity.ActionType.RESET,
                    previousValue = "true",
                    newValue = "false",
                    occurredAt = now
                )
            }
            runCatching { itemStatusLogDao.insertLogs(logs) }
        }
        WidgetSyncManager.notifyDataChanged(appContext)
        return checkedSnapshot
    }

    suspend fun checkAllChecklistItems(checklistId: Long) {
        val now = System.currentTimeMillis()
        val uncheckedIds = dao.getItemsByChecklist(checklistId)
            .filter { !it.isArchived && !it.isChecked }
            .map { it.id }
        dao.checkAllChecklistItems(checklistId = checklistId, updatedAt = now)
        if (uncheckedIds.isNotEmpty()) {
            val logs = uncheckedIds.map { id ->
                ItemStatusLogEntity(
                    itemId = id,
                    checklistId = checklistId,
                    actionType = ItemStatusLogEntity.ActionType.CHECKED,
                    previousValue = "false",
                    newValue = "true",
                    occurredAt = now
                )
            }
            runCatching { itemStatusLogDao.insertLogs(logs) }
        }
        WidgetSyncManager.notifyDataChanged(appContext)
    }

    suspend fun applyAutoRestoreForItem(itemId: Long): Boolean {
        val item = dao.getItemById(itemId) ?: return false
        if (item.isArchived || !item.isChecked) return false
        val now = System.currentTimeMillis()
        dao.updateItemChecked(itemId = item.id, isChecked = false, updatedAt = now)
        recordStatusLog(
            itemId = item.id,
            checklistId = item.checklistId,
            actionType = ItemStatusLogEntity.ActionType.AUTO_RESTORED,
            previousValue = "true",
            newValue = "false",
            occurredAt = now
        )
        return true
    }

    fun observeAllReminders(): Flow<List<ReminderEntity>> = reminderDao.observeAllReminders()

    suspend fun upsertReminder(
        checklistId: Long,
        itemId: Long?,
        hour: Int,
        minute: Int,
        repeatDaysBitmask: Int,
        onlyIfUnchecked: Boolean,
        scheduleKind: String = ReminderEntity.KIND_REMINDER,
    ): ReminderEntity {
        val resolvedKind = if (itemId == null) ReminderEntity.KIND_REMINDER else scheduleKind
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
            reminderDao.getItemLevelReminder(itemId, resolvedKind)
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
                scheduleKind = resolvedKind,
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
                scheduleKind = resolvedKind,
            )
            val newId = reminderDao.insertReminder(created)
            created.copy(id = newId)
        }
        if (itemId != null) {
            recordStatusLog(
                itemId = itemId,
                checklistId = checklistId,
                actionType = ItemStatusLogEntity.ActionType.SCHEDULE_UPDATED,
                newValue = "$resolvedKind:${ReminderFormatter.formatTime(clampedHour, clampedMinute)}/$maskedDays"
            )
        }
        ReminderScheduler.scheduleReminder(appContext, saved)
        WidgetSyncManager.notifyDataChanged(appContext)
        return saved
    }

    suspend fun handleReminderLongPress(
        checklistId: Long,
        itemId: Long?,
    ): ReminderLongPressResult {
        val existing = if (itemId == null) {
            reminderDao.getChecklistLevelReminder(checklistId)
        } else {
            reminderDao.getItemLevelReminder(itemId)
        }
        if (existing == null || !existing.isEnabled) {
            val quick = scheduleQuickReminderIn15Min(
                checklistId = checklistId,
                itemId = itemId,
            )
            return ReminderLongPressResult(
                outcome = ReminderLongPressOutcome.SET_QUICK_15M,
                reminder = quick.saved,
                previousReminder = quick.previousReminder,
            )
        }

        val maskedDays = existing.repeatDaysBitmask and ReminderFormatter.DAILY_MASK
        val isOneTime = maskedDays == 0
        val isAlreadySkipped = !isOneTime && ReminderScheduler.isPeriodicReminderSkipped(existing)

        if (isOneTime || isAlreadySkipped) {
            deleteReminder(existing.id)
            return ReminderLongPressResult(
                outcome = ReminderLongPressOutcome.CANCELLED,
                reminder = null,
                previousReminder = existing,
            )
        }

        val nextAfterSkip = ReminderScheduler.computeNextOccurrenceAfterSkip(
            hour = existing.hour,
            minute = existing.minute,
            repeatDaysBitmask = maskedDays,
            currentTriggerEpochMillis = existing.triggerEpochMillis,
        )
        ReminderScheduler.cancelReminder(appContext, existing.id)
        NotificationHelper.cancelNotificationForReminder(appContext, existing.id)
        val skipped = existing.copy(
            triggerEpochMillis = nextAfterSkip,
            isEnabled = true,
        )
        reminderDao.updateReminder(skipped)
        ReminderScheduler.scheduleReminder(appContext, skipped)
        WidgetSyncManager.notifyDataChanged(appContext)
        return ReminderLongPressResult(
            outcome = ReminderLongPressOutcome.SKIPPED_PERIODIC_ONCE,
            reminder = skipped,
            previousReminder = existing,
        )
    }

    suspend fun undoReminderLongPress(result: ReminderLongPressResult) {
        val prev = result.previousReminder
        if (prev == null) {
            result.reminder?.id?.let { deleteReminder(it) }
            return
        }
        undoQuickReminder(
            savedReminderId = result.reminder?.id ?: prev.id,
            previousReminder = prev,
        )
    }

    suspend fun scheduleQuickReminderIn15Min(
        checklistId: Long,
        itemId: Long?,
    ): QuickReminderResult {
        val existing = if (itemId == null) {
            reminderDao.getChecklistLevelReminder(checklistId)
        } else {
            reminderDao.getItemLevelReminder(itemId)
        }
        val targetTime = LocalTime.now().plusMinutes(15)
        val saved = upsertReminder(
            checklistId = checklistId,
            itemId = itemId,
            hour = targetTime.hour,
            minute = targetTime.minute,
            repeatDaysBitmask = 0,
            onlyIfUnchecked = true,
        )
        return QuickReminderResult(
            saved = saved,
            previousReminder = existing,
        )
    }

    suspend fun undoQuickReminder(
        savedReminderId: Long,
        previousReminder: ReminderEntity?,
    ) {
        if (previousReminder == null) {
            deleteReminder(savedReminderId)
            return
        }
        ReminderScheduler.cancelReminder(appContext, savedReminderId)
        NotificationHelper.cancelNotificationForReminder(appContext, savedReminderId)
        val refreshedTriggerMillis = if (previousReminder.isEnabled) {
            if (previousReminder.triggerEpochMillis > System.currentTimeMillis()) {
                previousReminder.triggerEpochMillis
            } else {
                ReminderScheduler.computeNextTriggerEpochMillis(
                    hour = previousReminder.hour,
                    minute = previousReminder.minute,
                    repeatDaysBitmask = previousReminder.repeatDaysBitmask,
                )
            }
        } else {
            previousReminder.triggerEpochMillis
        }
        val restored = previousReminder.copy(triggerEpochMillis = refreshedTriggerMillis)
        reminderDao.insertReminder(restored)
        if (restored.isEnabled) {
            ReminderScheduler.scheduleReminder(appContext, restored)
        } else {
            ReminderScheduler.cancelReminder(appContext, restored.id)
        }
        WidgetSyncManager.notifyDataChanged(appContext)
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

    private suspend fun recordStatusLog(
        itemId: Long,
        checklistId: Long,
        actionType: String,
        previousValue: String = "",
        newValue: String = "",
        occurredAt: Long = System.currentTimeMillis()
    ) {
        if (itemId <= 0L || checklistId <= 0L) return
        runCatching {
            itemStatusLogDao.insertLog(
                ItemStatusLogEntity(
                    itemId = itemId,
                    checklistId = checklistId,
                    actionType = actionType,
                    previousValue = previousValue,
                    newValue = newValue,
                    occurredAt = occurredAt
                )
            )
        }
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
