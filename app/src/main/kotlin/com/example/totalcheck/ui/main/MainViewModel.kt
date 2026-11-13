package com.example.totalcheck.ui.main

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.totalcheck.data.local.AppDatabase
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.data.preferences.ThemeConfig
import com.example.totalcheck.data.preferences.UserPreferencesRepository
import com.example.totalcheck.data.repository.ChecklistRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = ChecklistRepository(application, database.checklistDao())
    private val preferencesRepository = UserPreferencesRepository(application)

    val checklists: StateFlow<List<ChecklistEntity>> = repository.observeAllChecklists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val itemsByChecklist: StateFlow<Map<Long, List<CheckItemEntity>>> =
        repository.observeAllItems()
            .map { items -> items.groupBy { it.checklistId } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap()
            )

    private val _selectedChecklistId = MutableStateFlow<Long?>(null)
    val selectedChecklistId: StateFlow<Long?> = _selectedChecklistId.asStateFlow()

    private val _pendingScrollToChecklistId = MutableStateFlow<Long?>(null)
    val pendingScrollToChecklistId: StateFlow<Long?> = _pendingScrollToChecklistId.asStateFlow()

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

    fun createChecklist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val newId = repository.addChecklist(trimmed)
            _pendingScrollToChecklistId.value = newId
        }
    }

    fun clearPendingScroll() {
        _pendingScrollToChecklistId.value = null
    }

    fun renameChecklist(id: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.renameChecklist(id = id, newName = trimmed)
        }
    }

    fun deleteChecklist(id: Long) {
        if (checklists.value.size <= 1) return
        viewModelScope.launch {
            repository.deleteChecklist(id)
        }
    }

    fun toggleDarkMode() {
        val current = _themeConfig.value ?: return
        val next = !current.isDarkMode
        _themeConfig.value = current.copy(isDarkMode = next)
        viewModelScope.launch {
            preferencesRepository.setDarkMode(next)
        }
    }

    fun toggleDynamicColor() {
        val current = _themeConfig.value ?: return
        val next = !current.useDynamicColor
        _themeConfig.value = current.copy(useDynamicColor = next)
        viewModelScope.launch {
            preferencesRepository.setUseDynamicColor(next)
        }
    }

    fun toggleShowUncheckedOnly() {
        _showUncheckedOnly.value = !_showUncheckedOnly.value
    }

    fun toggleItemChecked(item: CheckItemEntity) {
        viewModelScope.launch {
            repository.setItemChecked(itemId = item.id, isChecked = !item.isChecked)
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
        viewModelScope.launch {
            val checkedSnapshot = repository.resetChecklistItems(checklistId)
            onReset(checkedSnapshot)
        }
    }

    fun restoreCheckedStates(itemIds: List<Long>) {
        if (itemIds.isEmpty()) return
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

        viewModelScope.launch {
            repository.reorderChecklistItems(
                fullOrderedIds = fullOrderedIds,
                currentItems = currentFullItems
            )
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
}
