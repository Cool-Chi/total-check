package com.example.totalcheck.ui.main

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.totalcheck.data.local.AppDatabase
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.data.local.ReminderEntity
import com.example.totalcheck.data.preferences.ThemeConfig
import com.example.totalcheck.data.preferences.UserPreferencesRepository
import com.example.totalcheck.data.reminder.NotificationHelper
import com.example.totalcheck.data.repository.ChecklistRepository
import com.example.totalcheck.data.repository.QuickReminderResult
import com.example.totalcheck.data.widget.WidgetSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = ChecklistRepository(
        context = application,
        dao = database.checklistDao(),
        reminderDao = database.reminderDao(),
    )
    private val preferencesRepository = UserPreferencesRepository(application)

    val reminders: StateFlow<List<ReminderEntity>> = repository.observeAllReminders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val reorderMutex = Mutex()
    private val _latestDbChecklists = MutableStateFlow<List<ChecklistEntity>>(emptyList())
    private val _optimisticChecklistOrder = MutableStateFlow<List<Long>?>(null)

    val checklists: StateFlow<List<ChecklistEntity>> = combine(
        _latestDbChecklists,
        _optimisticChecklistOrder
    ) { dbList, optimisticOrder ->
        if (optimisticOrder == null || dbList.isEmpty()) {
            dbList
        } else {
            val dbIds = dbList.map { it.id }
            if (dbIds == optimisticOrder || dbIds.toSet() != optimisticOrder.toSet()) {
                dbList
            } else {
                val byId = dbList.associateBy { it.id }
                optimisticOrder.mapNotNull { byId[it] }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val itemReorderMutex = Mutex()
    private val _optimisticItemLabels = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _optimisticCheckedStates = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    private val _optimisticItemOrders = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())

    val itemsByChecklist: StateFlow<Map<Long, List<CheckItemEntity>>> = combine(
        repository.observeAllItems(),
        _optimisticItemLabels,
        _optimisticCheckedStates,
        _optimisticItemOrders
    ) { items, optimisticLabels, optimisticChecked, optimisticOrders ->
        val staleLabelKeys = if (optimisticLabels.isEmpty()) null else mutableSetOf<Long>()
        val staleCheckedKeys = if (optimisticChecked.isEmpty()) null else mutableSetOf<Long>()
        val mapped = if (optimisticLabels.isEmpty() && optimisticChecked.isEmpty()) {
            items
        } else {
            items.map { item ->
                val overrideLabel = optimisticLabels[item.id]
                val resolvedLabel = if (overrideLabel != null) {
                    if (item.label == overrideLabel) {
                        staleLabelKeys?.add(item.id)
                        item.label
                    } else {
                        overrideLabel
                    }
                } else {
                    item.label
                }
                val overrideChecked = optimisticChecked[item.id]
                val resolvedChecked = if (overrideChecked != null) {
                    if (item.isChecked == overrideChecked) {
                        staleCheckedKeys?.add(item.id)
                        item.isChecked
                    } else {
                        overrideChecked
                    }
                } else {
                    item.isChecked
                }
                if (resolvedLabel != item.label || resolvedChecked != item.isChecked) {
                    item.copy(label = resolvedLabel, isChecked = resolvedChecked)
                } else {
                    item
                }
            }
        }
        if (!staleLabelKeys.isNullOrEmpty()) {
            _optimisticItemLabels.update { it - staleLabelKeys }
        }
        if (!staleCheckedKeys.isNullOrEmpty()) {
            _optimisticCheckedStates.update { it - staleCheckedKeys }
        }

        val grouped = mapped.groupBy { it.checklistId }
        if (optimisticOrders.isEmpty()) {
            grouped
        } else {
            val staleOrderKeys = mutableSetOf<Long>()
            val reordered = grouped.mapValues { (checklistId, dbList) ->
                val targetOrder = optimisticOrders[checklistId]
                if (targetOrder == null) {
                    dbList
                } else {
                    val dbIds = dbList.map { it.id }
                    if (dbIds == targetOrder || dbIds.toSet() != targetOrder.toSet()) {
                        staleOrderKeys.add(checklistId)
                        dbList
                    } else {
                        val byId = dbList.associateBy { it.id }
                        targetOrder.mapNotNull { byId[it] }
                    }
                }
            }
            for (keyId in optimisticOrders.keys) {
                if (keyId !in grouped) {
                    staleOrderKeys.add(keyId)
                }
            }
            if (staleOrderKeys.isNotEmpty()) {
                _optimisticItemOrders.update { it - staleOrderKeys }
            }
            reordered
        }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap()
    )

    private val _selectedChecklistId = MutableStateFlow<Long?>(null)
    val selectedChecklistId: StateFlow<Long?> = _selectedChecklistId.asStateFlow()

    private val _pendingScrollToChecklistId = MutableStateFlow<Long?>(null)
    val pendingScrollToChecklistId: StateFlow<Long?> = _pendingScrollToChecklistId.asStateFlow()

    private val _pendingAutoFocusInput = MutableStateFlow(false)
    val pendingAutoFocusInput: StateFlow<Boolean> = _pendingAutoFocusInput.asStateFlow()

    private val _themeConfig = MutableStateFlow<ThemeConfig?>(null)
    val themeConfig: StateFlow<ThemeConfig?> = _themeConfig.asStateFlow()

    val isDarkMode: StateFlow<Boolean> = themeConfig
        .map { it?.isDarkMode ?: true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    val useDynamicColor: StateFlow<Boolean> = themeConfig
        .map { it?.useDynamicColor ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    private val _showUncheckedOnly = MutableStateFlow(false)
    val showUncheckedOnly: StateFlow<Boolean> = _showUncheckedOnly.asStateFlow()

    private val _isStartupLoading = MutableStateFlow(true)
    val isStartupLoading: StateFlow<Boolean> = _isStartupLoading.asStateFlow()

    init {
        val startupBeginMs = SystemClock.uptimeMillis()
        viewModelScope.launch {
            repository.observeAllChecklists().collect { dbList ->
                _latestDbChecklists.value = dbList
                val opt = _optimisticChecklistOrder.value
                if (opt != null) {
                    val dbIds = dbList.map { it.id }
                    if (dbIds == opt || dbIds.toSet() != opt.toSet()) {
                        _optimisticChecklistOrder.compareAndSet(opt, null)
                    }
                }
            }
        }
        viewModelScope.launch {
            preferencesRepository.themeConfig.collect { config ->
                _themeConfig.value = config
            }
        }
        viewModelScope.launch {
            repository.seedInitialDataIfNeeded()
        }
        viewModelScope.launch {
            combine(
                _themeConfig.filterNotNull(),
                checklists.filter { it.isNotEmpty() }
            ) { _, _ -> Unit }.first()
            val elapsed = SystemClock.uptimeMillis() - startupBeginMs
            val remainingHoldMs = (700L - elapsed).coerceAtLeast(0L)
            if (remainingHoldMs > 0L) {
                delay(remainingHoldMs)
            }
            _isStartupLoading.value = false
        }
    }

    fun selectChecklist(checklistId: Long) {
        _selectedChecklistId.value = checklistId
    }

    fun selectAndScrollToChecklist(checklistId: Long) {
        if (checklistId <= 0L) return
        _selectedChecklistId.value = checklistId
        _pendingScrollToChecklistId.value = checklistId
    }

    fun createChecklist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val newId = repository.addChecklist(trimmed)
            _pendingScrollToChecklistId.value = newId
        }
    }

    fun importChecklist(
        title: String,
        items: List<com.example.totalcheck.data.parser.ParsedItemDraft>,
        onImported: (ChecklistEntity, Int) -> Unit = { _, _ -> }
    ) {
        val trimmedTitle = title.trim()
        val validItems = items.mapNotNull { draft ->
            val cleanLabel = draft.label.trim()
            if (cleanLabel.isEmpty()) null else draft.copy(label = cleanLabel)
        }
        if (trimmedTitle.isEmpty() || validItems.isEmpty()) return
        viewModelScope.launch {
            val newId = repository.createChecklistWithItems(
                name = trimmedTitle,
                items = validItems
            )
            _pendingScrollToChecklistId.value = newId
            onImported(ChecklistEntity(id = newId, name = trimmedTitle), validItems.size)
        }
    }

    fun clearPendingScroll() {
        _pendingScrollToChecklistId.value = null
    }

    fun requestAutoFocusInput() {
        _pendingAutoFocusInput.value = true
    }

    fun consumeAutoFocusInput() {
        _pendingAutoFocusInput.value = false
    }

    fun renameChecklist(id: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        _latestDbChecklists.value = _latestDbChecklists.value.map {
            if (it.id == id) it.copy(name = trimmed) else it
        }
        viewModelScope.launch {
            repository.renameChecklist(id = id, newName = trimmed)
        }
    }

    fun deleteChecklist(
        checklist: ChecklistEntity,
        onDeleted: (ChecklistEntity, List<CheckItemEntity>) -> Unit = { _, _ -> }
    ) {
        if (checklists.value.size <= 1) return
        viewModelScope.launch {
            val itemsSnapshot = repository.deleteChecklistWithSnapshot(checklist)
            onDeleted(checklist, itemsSnapshot)
        }
    }

    fun restoreChecklist(checklist: ChecklistEntity, items: List<CheckItemEntity>) {
        viewModelScope.launch {
            repository.restoreChecklistWithItems(checklist, items)
            _pendingScrollToChecklistId.value = checklist.id
        }
    }

    fun reorderChecklists(orderedIds: List<Long>) {
        val current = checklists.value
        if (current.size <= 1 || orderedIds.isEmpty()) return
        if (orderedIds.toSet() != current.map { it.id }.toSet()) return
        _optimisticChecklistOrder.value = orderedIds
        viewModelScope.launch {
            reorderMutex.withLock {
                val latestOrder = _optimisticChecklistOrder.value ?: orderedIds
                val freshDb = repository.observeAllChecklists().first()
                repository.reorderChecklists(
                    orderedIds = latestOrder,
                    currentChecklists = freshDb
                )
            }
        }
    }

    fun moveChecklistByStep(checklistId: Long, delta: Int) {
        val current = checklists.value
        val fromIdx = current.indexOfFirst { it.id == checklistId }
        if (fromIdx < 0) return
        val toIdx = (fromIdx + delta).coerceIn(0, current.lastIndex)
        if (toIdx == fromIdx) return
        val mutableIds = current.map { it.id }.toMutableList()
        val moved = mutableIds.removeAt(fromIdx)
        mutableIds.add(toIdx, moved)
        reorderChecklists(mutableIds)
    }

    fun toggleDarkMode() {
        val current = _themeConfig.value ?: return
        val next = !current.isDarkMode
        val nextConfig = current.copy(isDarkMode = next)
        _themeConfig.value = nextConfig
        WidgetSyncManager.onThemeChanged(getApplication(), nextConfig)
        viewModelScope.launch {
            preferencesRepository.setDarkMode(next)
        }
    }

    fun toggleDynamicColor() {
        val current = _themeConfig.value ?: return
        val next = !current.useDynamicColor
        val nextConfig = current.copy(useDynamicColor = next)
        _themeConfig.value = nextConfig
        WidgetSyncManager.onThemeChanged(getApplication(), nextConfig)
        viewModelScope.launch {
            preferencesRepository.setUseDynamicColor(next)
        }
    }

    fun toggleShowUncheckedOnly() {
        _showUncheckedOnly.value = !_showUncheckedOnly.value
    }

    fun toggleItemChecked(item: CheckItemEntity) {
        val currentChecked = _optimisticCheckedStates.value[item.id] ?: item.isChecked
        val nextChecked = !currentChecked
        _optimisticCheckedStates.update { it + (item.id to nextChecked) }
        viewModelScope.launch {
            repository.setItemChecked(itemId = item.id, isChecked = nextChecked)
        }
    }

    fun addItem(checklistId: Long, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addItem(checklistId = checklistId, label = trimmed)
        }
    }

    fun renameItem(itemId: Long, newLabel: String) {
        val trimmed = newLabel.trim()
        if (trimmed.isEmpty()) return
        _optimisticItemLabels.value = _optimisticItemLabels.value + (itemId to trimmed)
        viewModelScope.launch {
            repository.updateItemLabel(itemId = itemId, newLabel = trimmed)
        }
    }

    fun deleteItem(item: CheckItemEntity, onDeleted: (CheckItemEntity) -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteItem(item)
            onDeleted(item)
        }
    }

    fun restoreItem(item: CheckItemEntity) {
        viewModelScope.launch {
            repository.restoreItem(item)
        }
    }

    fun resetChecklist(checklistId: Long, onReset: (List<Long>) -> Unit = {}) {
        val checklistItemIds = itemsByChecklist.value[checklistId].orEmpty().map { it.id }.toSet()
        if (checklistItemIds.isNotEmpty()) {
            _optimisticCheckedStates.update { it - checklistItemIds }
        }
        viewModelScope.launch {
            val checkedSnapshot = repository.resetChecklistItems(checklistId)
            onReset(checkedSnapshot)
        }
    }

    fun restoreCheckedStates(itemIds: List<Long>) {
        if (itemIds.isEmpty()) return
        _optimisticCheckedStates.update { current ->
            current + itemIds.associateWith { true }
        }
        viewModelScope.launch {
            repository.restoreCheckedStates(itemIds)
        }
    }

    fun reorderChecklistItems(
        checklistId: Long,
        reorderedVisibleIds: List<Long>,
        showUncheckedOnly: Boolean
    ) {
        val currentFullItems = itemsByChecklist.value[checklistId].orEmpty()
        if (currentFullItems.isEmpty() || reorderedVisibleIds.isEmpty()) return

        val reorderedSet = reorderedVisibleIds.toHashSet()
        val fullOrderedIds = if (!showUncheckedOnly) {
            val remaining = currentFullItems.filter { it.id !in reorderedSet }.map { it.id }
            reorderedVisibleIds + remaining
        } else {
            val iterator = reorderedVisibleIds.iterator()
            val mapped = currentFullItems.map { item ->
                if (item.id in reorderedSet && iterator.hasNext()) {
                    iterator.next()
                } else {
                    item.id
                }
            }.toMutableList()
            while (iterator.hasNext()) {
                mapped.add(iterator.next())
            }
            mapped
        }
        if (fullOrderedIds == currentFullItems.map { it.id }) return

        _optimisticItemOrders.update { it + (checklistId to fullOrderedIds) }
        viewModelScope.launch {
            itemReorderMutex.withLock {
                val latestOrder = _optimisticItemOrders.value[checklistId] ?: fullOrderedIds
                val freshItems = repository.getItemsByChecklist(checklistId)
                repository.reorderChecklistItems(
                    fullOrderedIds = latestOrder,
                    currentItems = freshItems
                )
            }
        }
    }

    fun moveItemByStep(
        checklistId: Long,
        itemId: Long,
        delta: Int,
        showUncheckedOnly: Boolean
    ) {
        val currentFullItems = itemsByChecklist.value[checklistId].orEmpty()
        val visibleItems = if (showUncheckedOnly) {
            currentFullItems.filter { !it.isChecked }
        } else {
            currentFullItems
        }
        val fromIdx = visibleItems.indexOfFirst { it.id == itemId }
        if (fromIdx < 0) return
        val toIdx = (fromIdx + delta).coerceIn(0, visibleItems.lastIndex)
        if (toIdx == fromIdx) return
        val mutableIds = visibleItems.map { it.id }.toMutableList()
        val moved = mutableIds.removeAt(fromIdx)
        mutableIds.add(toIdx, moved)
        reorderChecklistItems(
            checklistId = checklistId,
            reorderedVisibleIds = mutableIds,
            showUncheckedOnly = showUncheckedOnly
        )
    }

    fun saveReminder(
        checklistId: Long,
        itemId: Long?,
        hour: Int,
        minute: Int,
        repeatDaysBitmask: Int,
        onlyIfUnchecked: Boolean,
        onSaved: (ReminderEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            val saved = repository.upsertReminder(
                checklistId = checklistId,
                itemId = itemId,
                hour = hour,
                minute = minute,
                repeatDaysBitmask = repeatDaysBitmask,
                onlyIfUnchecked = onlyIfUnchecked,
            )
            onSaved(saved)
        }
    }

    fun scheduleQuickReminder15m(
        checklistId: Long,
        itemId: Long?,
        onScheduled: (QuickReminderResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.scheduleQuickReminderIn15Min(
                checklistId = checklistId,
                itemId = itemId,
            )
            onScheduled(result)
        }
    }

    fun undoQuickReminder(
        savedReminderId: Long,
        previousReminder: ReminderEntity?,
    ) {
        if (savedReminderId <= 0L) return
        viewModelScope.launch {
            repository.undoQuickReminder(
                savedReminderId = savedReminderId,
                previousReminder = previousReminder,
            )
        }
    }

    fun deleteReminder(
        reminderId: Long,
        onDeleted: () -> Unit = {}
    ) {
        if (reminderId <= 0L) return
        viewModelScope.launch {
            repository.deleteReminder(reminderId)
            onDeleted()
        }
    }

    fun sendTestReminderNotification(
        checklistId: Long,
        itemId: Long?,
        hour: Int,
        minute: Int,
        repeatDaysBitmask: Int,
        onlyIfUnchecked: Boolean,
        onSent: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val previewEntity = ReminderEntity(
                id = 0L,
                checklistId = checklistId,
                itemId = itemId,
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                repeatDaysBitmask = repeatDaysBitmask,
                onlyIfUnchecked = onlyIfUnchecked,
                isEnabled = true,
            )
            val posted = NotificationHelper.evaluateAndNotifyIfNeeded(
                context = getApplication(),
                reminder = previewEntity,
                isTestNotification = true,
            )
            onSent(posted)
        }
    }
}
