package com.example.totalcheck.ui.main

import androidx.compose.runtime.Immutable
import com.example.totalcheck.data.local.CheckItemEntity

/**
 * 清單篩選狀態架構模型。
 * 支援「只看未帶」、全域標籤（跨清單作用）、清單標籤（僅於所屬 [selectedTagChecklistId] 清單作用），
 * 並預留分類與關鍵字擴充欄位。
 */
@Immutable
data class ChecklistFilterState(
    val showUncheckedOnly: Boolean = false,
    val selectedCategories: Set<String> = emptySet(),
    val selectedTags: Set<String> = emptySet(),
    val selectedTagChecklistId: Long? = null,
    val searchQuery: String = ""
) {
    val isActive: Boolean
        get() = showUncheckedOnly ||
            selectedCategories.isNotEmpty() ||
            selectedTags.isNotEmpty() ||
            searchQuery.isNotBlank()

    fun effectiveSelectedTagsForChecklist(checklistId: Long): Set<String> {
        if (selectedTags.isEmpty()) return emptySet()
        return selectedTags.filterTo(LinkedHashSet()) { token ->
            if (TagCatalog.isChecklistTagToken(token)) {
                selectedTagChecklistId == null || selectedTagChecklistId == checklistId
            } else {
                true
            }
        }
    }

    fun isActiveForChecklist(checklistId: Long): Boolean {
        return showUncheckedOnly ||
            selectedCategories.isNotEmpty() ||
            effectiveSelectedTagsForChecklist(checklistId).isNotEmpty() ||
            searchQuery.isNotBlank()
    }

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
        val activeTagsForItemChecklist = effectiveSelectedTagsForChecklist(item.checklistId)
        if (activeTagsForItemChecklist.isNotEmpty()) {
            val itemTagTokens = TagCatalog.parseItemTags(item.tags).toSet()
            if (itemTagTokens.none { it in activeTagsForItemChecklist }) {
                return false
            }
        }
        if (searchQuery.isNotBlank() && !item.label.contains(searchQuery.trim(), ignoreCase = true)) {
            return false
        }
        return true
    }
}
