package com.example.totalcheck.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.totalcheck.data.local.AppDatabase
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.data.preferences.UserPreferencesRepository
import com.example.totalcheck.data.repository.ChecklistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChecklistWithItems(
    val checklist: ChecklistEntity,
    val items: List<CheckItemEntity>
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = ChecklistRepository(application, database.checklistDao())
    private val preferencesRepository = UserPreferencesRepository(application)

    val checklistsWithItems: StateFlow<List<ChecklistWithItems>> =
        repository.observeAllChecklists()
            .flatMapLatest { checklists ->
                if (checklists.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        checklists.map { checklist ->
                            repository.observeItemsByChecklist(checklist.id)
                                .map { items -> ChecklistWithItems(checklist, items) }
                        }
                    ) { array -> array.toList() }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val isDarkMode: StateFlow<Boolean> = preferencesRepository.isDarkMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    val useDynamicColor: StateFlow<Boolean> = preferencesRepository.useDynamicColor
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    private val _showUncheckedOnly = MutableStateFlow(false)
    val showUncheckedOnly: StateFlow<Boolean> = _showUncheckedOnly.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfNeeded()
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            preferencesRepository.setDarkMode(!isDarkMode.value)
        }
    }

    fun toggleDynamicColor() {
        viewModelScope.launch {
            preferencesRepository.setUseDynamicColor(!useDynamicColor.value)
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

    fun resetChecklist(checklistId: Long) {
        viewModelScope.launch {
            repository.resetChecklistItems(checklistId)
        }
    }
}
