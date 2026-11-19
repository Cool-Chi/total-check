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
import com.example.totalcheck.data.repository.ReminderLongPressResult
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

data class DeletedTagSnapshot(
    val tag: TagDefinition,
    val checklistId: Long?,
    val poolIndex: Int,
    val previousTagsByItemId: Map<Long, String>,
    val wasActiveFilterTag: Boolean
)

sealed interface QuickTagToggleResult {
    data class Added(val tag: TagDefinition) : QuickTagToggleResult
    data class Removed(val tag: TagDefinition) : QuickTagToggleResult
    data object LimitReached : QuickTagToggleResult
    data object NoTagAvailable : QuickTagToggleResult
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = ChecklistRepository(
        context = application,
        dao = database.checklistDao(),
        reminderDao = database.reminderDao(),
        itemStatusLogDao = database.itemStatusLogDao(),
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
    private val _optimisticItemTags = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _optimisticCheckedStates = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    private val _optimisticArchivedStates = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    private val _optimisticItemOrders = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())

    private val resolvedAllItems: StateFlow<List<CheckItemEntity>> = combine(
        repository.observeAllItems(),
        _optimisticItemLabels,
        _optimisticItemTags,
        _optimisticCheckedStates,
        _optimisticArchivedStates
    ) { items, optimisticLabels, optimisticTags, optimisticChecked, optimisticArchived ->
        val staleLabelKeys = if (optimisticLabels.isEmpty()) null else mutableSetOf<Long>()
        val staleTagKeys = if (optimisticTags.isEmpty()) null else mutableSetOf<Long>()
        val staleCheckedKeys = if (optimisticChecked.isEmpty()) null else mutableSetOf<Long>()
        val staleArchivedKeys = if (optimisticArchived.isEmpty()) null else mutableSetOf<Long>()
        val mapped = if (
            optimisticLabels.isEmpty() &&
            optimisticTags.isEmpty() &&
            optimisticChecked.isEmpty() &&
            optimisticArchived.isEmpty()
        ) {
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
                val overrideTags = optimisticTags[item.id]
                val resolvedTags = if (overrideTags != null) {
                    if (item.tags == overrideTags) {
                        staleTagKeys?.add(item.id)
                        item.tags
                    } else {
                        overrideTags
                    }
                } else {
                    item.tags
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
                val overrideArchived = optimisticArchived[item.id]
                val resolvedArchived = if (overrideArchived != null) {
                    if (item.isArchived == overrideArchived) {
                        staleArchivedKeys?.add(item.id)
                        item.isArchived
                    } else {
                        overrideArchived
                    }
                } else {
                    item.isArchived
                }
                if (
                    resolvedLabel != item.label ||
                    resolvedTags != item.tags ||
                    resolvedChecked != item.isChecked ||
                    resolvedArchived != item.isArchived
                ) {
                    item.copy(
                        label = resolvedLabel,
                        tags = resolvedTags,
                        isChecked = resolvedChecked,
                        isArchived = resolvedArchived
                    )
                } else {
                    item
                }
            }
        }
        if (!staleLabelKeys.isNullOrEmpty()) {
            _optimisticItemLabels.update { it - staleLabelKeys }
        }
        if (!staleTagKeys.isNullOrEmpty()) {
            _optimisticItemTags.update { it - staleTagKeys }
        }
        if (!staleCheckedKeys.isNullOrEmpty()) {
            _optimisticCheckedStates.update { it - staleCheckedKeys }
        }
        if (!staleArchivedKeys.isNullOrEmpty()) {
            _optimisticArchivedStates.update { it - staleArchivedKeys }
        }
        mapped
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val itemsByChecklist: StateFlow<Map<Long, List<CheckItemEntity>>> = combine(
        resolvedAllItems,
        _optimisticItemOrders
    ) { items, optimisticOrders ->
        val grouped = items
            .filter { !it.isArchived }
            .groupBy { it.checklistId }
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

    val archivedItemsByChecklist: StateFlow<Map<Long, List<CheckItemEntity>>> = resolvedAllItems
        .map { items ->
            items
                .filter { it.isArchived }
                .groupBy { it.checklistId }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
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

    val tagPool: StateFlow<List<TagDefinition>> = preferencesRepository.tagPool
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TagCatalog.DEFAULT_SEED_TAGS
        )

    val checklistTagPools: StateFlow<Map<Long, List<TagDefinition>>> =
        preferencesRepository.checklistTagPools
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap()
            )

    private val lastAddedTagTokenByChecklist = HashMap<Long, String>()

    private val _filterState = MutableStateFlow(ChecklistFilterState())
    val filterState: StateFlow<ChecklistFilterState> = _filterState.asStateFlow()

    val showUncheckedOnly: StateFlow<Boolean> = _filterState
        .map { it.showUncheckedOnly }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

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
        clearForeignChecklistTagFilter(activeChecklistId = checklistId)
    }

    fun selectAndScrollToChecklist(checklistId: Long) {
        if (checklistId <= 0L) return
        _selectedChecklistId.value = checklistId
        _pendingScrollToChecklistId.value = checklistId
        clearForeignChecklistTagFilter(activeChecklistId = checklistId)
    }

    private fun clearForeignChecklistTagFilter(activeChecklistId: Long) {
        _filterState.update { current ->
            val ownerId = current.selectedTagChecklistId
            if (ownerId != null && ownerId != activeChecklistId) {
                val remaining = current.selectedTags.filterNotTo(LinkedHashSet()) {
                    TagCatalog.isChecklistTagToken(it)
                }
                current.copy(
                    selectedTags = remaining,
                    selectedTagChecklistId = null
                )
            } else {
                current
            }
        }
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

    fun toggleFilterUncheckedOnly() {
        _filterState.update { it.copy(showUncheckedOnly = !it.showUncheckedOnly) }
    }

    fun toggleShowUncheckedOnly() {
        toggleFilterUncheckedOnly()
    }

    fun updateCategoryFilter(categories: Set<String>) {
        _filterState.update { it.copy(selectedCategories = categories) }
    }

    fun updateTagFilter(tags: Set<String>) {
        _filterState.update { it.copy(selectedTags = tags) }
    }

    fun selectFilterTag(tagToken: String?, checklistId: Long? = null) {
        val clean = tagToken?.let { TagCatalog.sanitizeTagToken(it) }?.takeIf { it.isNotEmpty() }
        val scopedChecklistId = if (clean != null && TagCatalog.isChecklistTagToken(clean)) {
            checklistId ?: _selectedChecklistId.value
        } else {
            null
        }
        _filterState.update { current ->
            current.copy(
                selectedTags = if (clean == null) emptySet() else setOf(clean),
                selectedTagChecklistId = scopedChecklistId
            )
        }
    }

    fun updateSearchQueryFilter(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun clearAllFilters() {
        _filterState.value = ChecklistFilterState()
    }

    fun createTagInPool(
        name: String,
        iconKey: String,
        scope: TagScope = TagScope.GLOBAL,
        checklistId: Long? = null,
        onCreated: (TagDefinition) -> Unit = {}
    ) {
        val cleanName = TagCatalog.sanitizeTagName(name)
        if (cleanName.isEmpty()) return
        if (scope == TagScope.CHECKLIST && (checklistId == null || checklistId <= 0L)) return
        val newTag = TagDefinition(
            name = cleanName,
            iconKey = iconKey.ifBlank { TagCatalog.DEFAULT_ICON_KEY },
            scope = scope
        )
        viewModelScope.launch {
            if (scope == TagScope.CHECKLIST && checklistId != null) {
                preferencesRepository.addOrUpdateChecklistTag(checklistId, newTag)
            } else {
                preferencesRepository.addOrUpdateTag(newTag)
            }
            onCreated(newTag)
        }
    }

    fun deleteTagFromPool(
        tag: TagDefinition,
        checklistId: Long? = null,
        onDeleted: (DeletedTagSnapshot) -> Unit = {}
    ) {
        val cleanName = TagCatalog.sanitizeTagName(tag.name)
        if (cleanName.isEmpty()) return
        val scope = tag.scope
        val targetChecklistId = if (scope == TagScope.CHECKLIST) checklistId else null
        if (scope == TagScope.CHECKLIST && (targetChecklistId == null || targetChecklistId <= 0L)) return

        val targetToken = TagCatalog.encodeTagToken(cleanName, scope)
        val wasActiveFilterTag = targetToken in _filterState.value.selectedTags &&
            (targetChecklistId == null || _filterState.value.selectedTagChecklistId == targetChecklistId)
        if (wasActiveFilterTag) {
            _filterState.update { current ->
                val remaining = current.selectedTags - targetToken
                current.copy(
                    selectedTags = remaining,
                    selectedTagChecklistId = if (remaining.none { TagCatalog.isChecklistTagToken(it) }) {
                        null
                    } else {
                        current.selectedTagChecklistId
                    }
                )
            }
        }

        if (targetChecklistId != null) {
            if (lastAddedTagTokenByChecklist[targetChecklistId] == targetToken) {
                lastAddedTagTokenByChecklist.remove(targetChecklistId)
            }
        } else {
            lastAddedTagTokenByChecklist.entries.removeAll { it.value == targetToken }
        }

        val currentItems = resolvedAllItems.value
        val optimisticUpdated = LinkedHashMap<Long, String>()
        val optimisticPrevSnapshot = LinkedHashMap<Long, String>()
        for (item in currentItems) {
            if (targetChecklistId != null && item.checklistId != targetChecklistId) continue
            val parsed = TagCatalog.parseItemTags(item.tags)
            if (targetToken in parsed) {
                optimisticPrevSnapshot[item.id] = item.tags
                optimisticUpdated[item.id] = TagCatalog.serializeItemTags(
                    parsed.filterNot { it == targetToken }
                )
            }
        }
        if (optimisticUpdated.isNotEmpty()) {
            _optimisticItemTags.update { it + optimisticUpdated }
        }
        viewModelScope.launch {
            val (removedDef, removedIndex) = if (targetChecklistId != null) {
                preferencesRepository.removeChecklistTag(targetChecklistId, cleanName)
            } else {
                preferencesRepository.removeTag(cleanName)
            }
            val dbSnapshot = repository.stripTagFromAllItems(
                tagToken = targetToken,
                checklistId = targetChecklistId
            )
            val mergedPrev = if (dbSnapshot.isNotEmpty()) dbSnapshot else optimisticPrevSnapshot
            onDeleted(
                DeletedTagSnapshot(
                    tag = removedDef ?: tag.copy(name = cleanName, scope = scope),
                    checklistId = targetChecklistId,
                    poolIndex = if (removedIndex >= 0) removedIndex else 0,
                    previousTagsByItemId = mergedPrev,
                    wasActiveFilterTag = wasActiveFilterTag
                )
            )
        }
    }

    fun restoreDeletedTag(snapshot: DeletedTagSnapshot) {
        if (snapshot.previousTagsByItemId.isNotEmpty()) {
            _optimisticItemTags.update { it + snapshot.previousTagsByItemId }
        }
        if (snapshot.wasActiveFilterTag) {
            _filterState.update { current ->
                current.copy(
                    selectedTags = setOf(snapshot.tag.storageToken),
                    selectedTagChecklistId = snapshot.checklistId
                )
            }
        }
        viewModelScope.launch {
            if (snapshot.tag.scope == TagScope.CHECKLIST && snapshot.checklistId != null) {
                preferencesRepository.insertChecklistTagAt(
                    checklistId = snapshot.checklistId,
                    tag = snapshot.tag,
                    index = snapshot.poolIndex
                )
            } else {
                preferencesRepository.insertTagAt(snapshot.tag, snapshot.poolIndex)
            }
            repository.restoreItemTagsSnapshot(snapshot.previousTagsByItemId)
        }
    }

    fun updateItemTags(
        checklistId: Long,
        itemId: Long,
        previousTags: List<String>,
        tags: List<String>,
        onUpdated: () -> Unit = {}
    ) {
        val cleanPrev = previousTags.map { TagCatalog.sanitizeTagToken(it) }.filter { it.isNotEmpty() }
        val cleanNext = tags.map { TagCatalog.sanitizeTagToken(it) }.filter { it.isNotEmpty() }
            .distinct()
            .take(TagCatalog.MAX_TAGS_PER_ITEM)
        val newlyAdded = cleanNext.filterNot { it in cleanPrev }
        if (newlyAdded.isNotEmpty()) {
            lastAddedTagTokenByChecklist[checklistId] = newlyAdded.last()
        }
        val serialized = TagCatalog.serializeItemTags(cleanNext)
        _optimisticItemTags.update { it + (itemId to serialized) }
        viewModelScope.launch {
            repository.updateItemTags(itemId = itemId, serializedTags = serialized)
            onUpdated()
        }
    }

    fun quickToggleLastAddedTag(
        checklistId: Long,
        item: CheckItemEntity,
        onResult: (QuickTagToggleResult) -> Unit
    ) {
        val globalTags = tagPool.value
        val localTags = checklistTagPools.value[checklistId].orEmpty()
        val availableTags = globalTags + localTags

        val recordedToken = lastAddedTagTokenByChecklist[checklistId]
        val resolvedTag = recordedToken?.let { token ->
            availableTags.firstOrNull { it.storageToken == token }
        } ?: globalTags.firstOrNull { it.name == TagCatalog.DEFAULT_TAG_NAME }
            ?: globalTags.firstOrNull()

        if (resolvedTag == null) {
            onResult(QuickTagToggleResult.NoTagAvailable)
            return
        }

        val targetToken = resolvedTag.storageToken
        val currentTokens = TagCatalog.parseItemTags(item.tags)
        if (targetToken in currentTokens) {
            val nextTokens = currentTokens.filterNot { it == targetToken }
            val serialized = TagCatalog.serializeItemTags(nextTokens)
            _optimisticItemTags.update { it + (item.id to serialized) }
            viewModelScope.launch {
                repository.updateItemTags(itemId = item.id, serializedTags = serialized)
            }
            onResult(QuickTagToggleResult.Removed(resolvedTag))
        } else if (currentTokens.size >= TagCatalog.MAX_TAGS_PER_ITEM) {
            onResult(QuickTagToggleResult.LimitReached)
        } else {
            val nextTokens = currentTokens + targetToken
            val serialized = TagCatalog.serializeItemTags(nextTokens)
            _optimisticItemTags.update { it + (item.id to serialized) }
            viewModelScope.launch {
                repository.updateItemTags(itemId = item.id, serializedTags = serialized)
            }
            onResult(QuickTagToggleResult.Added(resolvedTag))
        }
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

    fun archiveItem(item: CheckItemEntity, onArchived: (CheckItemEntity) -> Unit = {}) {
        _optimisticArchivedStates.update { it + (item.id to true) }
        viewModelScope.launch {
            repository.archiveItem(item)
            onArchived(item)
        }
    }

    fun unarchiveItem(item: CheckItemEntity, onUnarchived: (CheckItemEntity) -> Unit = {}) {
        _optimisticArchivedStates.update { it + (item.id to false) }
        viewModelScope.launch {
            repository.unarchiveItem(item)
            onUnarchived(item)
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

        val isFiltered = showUncheckedOnly || _filterState.value.isActive
        val reorderedSet = reorderedVisibleIds.toHashSet()
        val fullOrderedIds = if (!isFiltered) {
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
        val activeFilter = _filterState.value
        val visibleItems = when {
            activeFilter.isActive -> currentFullItems.filter { activeFilter.matches(it) }
            showUncheckedOnly -> currentFullItems.filter { !it.isChecked }
            else -> currentFullItems
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

    fun moveItemToBottom(
        checklistId: Long,
        itemId: Long,
        showUncheckedOnly: Boolean
    ) {
        val currentFullItems = itemsByChecklist.value[checklistId].orEmpty()
        val activeFilter = _filterState.value
        val visibleItems = when {
            activeFilter.isActive -> currentFullItems.filter { activeFilter.matches(it) }
            showUncheckedOnly -> currentFullItems.filter { !it.isChecked }
            else -> currentFullItems
        }
        val fromIdx = visibleItems.indexOfFirst { it.id == itemId }
        if (fromIdx < 0 || fromIdx == visibleItems.lastIndex) return
        val mutableIds = visibleItems.map { it.id }.toMutableList()
        val moved = mutableIds.removeAt(fromIdx)
        mutableIds.add(moved)
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
        scheduleKind: String = ReminderEntity.KIND_REMINDER,
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
                scheduleKind = scheduleKind,
            )
            onSaved(saved)
        }
    }

    fun handleReminderLongPress(
        checklistId: Long,
        itemId: Long?,
        onHandled: (ReminderLongPressResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.handleReminderLongPress(
                checklistId = checklistId,
                itemId = itemId,
            )
            onHandled(result)
        }
    }

    fun undoReminderLongPress(result: ReminderLongPressResult) {
        viewModelScope.launch {
            repository.undoReminderLongPress(result)
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
