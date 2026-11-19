package com.example.totalcheck.ui.main

import androidx.compose.runtime.Immutable
import com.example.totalcheck.data.local.CheckItemEntity

/**
 * 清單篩選狀態架構模型。
 * 目前由資訊卡「篩選」按鈕切換 [showUncheckedOnly]，並預留分類、標籤與關鍵字擴充欄位。
 */
@Immutable
data class ChecklistFilterState(
    val showUncheckedOnly: Boolean = false,
    val selectedCategories: Set<String> = emptySet(),
    val selectedTags: Set<String> = emptySet(),
    val searchQuery: String = ""
) {
    val isActive: Boolean
        get() = showUncheckedOnly ||
            selectedCategories.isNotEmpty() ||
            selectedTags.isNotEmpty() ||
            searchQuery.isNotBlank()

    fun matches(
        item: CheckItemEntity,
        stagingCheckedIds: Set<Long> = emptySet()
    ): Boolean {
        if (item.isArchived) return false
        if (showUncheckedOnly && item.isChecked && item.id !in stagingCheckedIds) {
            return false
        }
        if (selectedCategories.isNotEmpty() && item.category !in selectedCategories) {
            return false
        }
        if (selectedTags.isNotEmpty()) {
            val itemTagTokens = item.tags
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            if (itemTagTokens.none { it in selectedTags }) {
                return false
            }
        }
        if (searchQuery.isNotBlank() && !item.label.contains(searchQuery.trim(), ignoreCase = true)) {
            return false
        }
        return true
    }
}
