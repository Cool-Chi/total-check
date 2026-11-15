package com.example.totalcheck.data.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import com.example.totalcheck.data.local.AppDatabase
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.data.local.ReminderEntity
import com.example.totalcheck.data.preferences.ThemeConfig
import com.example.totalcheck.data.preferences.UserPreferencesRepository
import com.example.totalcheck.data.preferences.WidgetPreferences
import com.example.totalcheck.data.reminder.ReminderFormatter
import com.example.totalcheck.data.repository.ChecklistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class WidgetInstanceState(
    val selectedChecklistId: Long? = null,
    val showUncheckedOnly: Boolean = false,
    val isResetConfirmActive: Boolean = false,
    val checklistFlipSlot: Int = 0,
    val lastToggledItemId: Long? = null,
)

data class WidgetReminderSummary(
    val checklistReminder: ReminderEntity? = null,
    val activeItemRemindersByItemId: Map<Long, ReminderEntity> = emptyMap(),
) {
    val hasAnyActiveReminder: Boolean
        get() = (checklistReminder != null && checklistReminder.isEnabled) ||
            activeItemRemindersByItemId.isNotEmpty()

    val primaryTimeText: String?
        get() = checklistReminder?.takeIf { it.isEnabled }?.let {
            ReminderFormatter.formatTime(it.hour, it.minute)
        } ?: activeItemRemindersByItemId.values.firstOrNull()?.let {
            ReminderFormatter.formatTime(it.hour, it.minute)
        }
}

data class WidgetUiSnapshot(
    val checklists: List<ChecklistEntity>,
    val itemsByChecklist: Map<Long, List<CheckItemEntity>>,
    val widgetStates: Map<Int, WidgetInstanceState> = emptyMap(),
    val themeConfig: ThemeConfig = ThemeConfig(isDarkMode = true, useDynamicColor = false),
    val remindersByChecklist: Map<Long, WidgetReminderSummary> = emptyMap(),
) {
    fun stateForWidget(appWidgetId: Int): WidgetInstanceState =
        widgetStates[appWidgetId] ?: WidgetInstanceState()

    fun resolveActiveChecklist(appWidgetId: Int): ChecklistEntity? {
        val preferredId = widgetStates[appWidgetId]?.selectedChecklistId
        return checklists.firstOrNull { it.id == preferredId } ?: checklists.firstOrNull()
    }

    fun itemsForChecklist(checklistId: Long): List<CheckItemEntity> =
        itemsByChecklist[checklistId].orEmpty()

    fun reminderSummaryForChecklist(checklistId: Long): WidgetReminderSummary =
        remindersByChecklist[checklistId] ?: WidgetReminderSummary()
}

object WidgetSyncManager {

    private const val RESET_CONFIRM_WINDOW_MS = 5_000L
    private const val RESET_CONFIRM_REVERT_DELAY_MS = 5_050L
    private const val TOGGLE_RIPPLE_DELAY_MS = 260L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshotLock = Any()
    private val dbWriteMutex = Mutex()

    private val _snapshotFlow = MutableStateFlow<WidgetUiSnapshot?>(null)
    val snapshotFlow: StateFlow<WidgetUiSnapshot?> = _snapshotFlow.asStateFlow()

    @Volatile
    private var overrideThemeConfig: ThemeConfig? = null

    private val resetConfirmChecklistByWidget = mutableMapOf<Int, Long>()
    private val resetConfirmDeadlineByWidget = mutableMapOf<Int, Long>()
    private val resetGenerationByWidget = mutableMapOf<Int, Long>()
    private val resetRevertJobByWidget = mutableMapOf<Int, Job>()
    private val toggleGenerationByWidget = mutableMapOf<Int, Long>()

    fun getAllActiveWidgetIds(context: Context): IntArray {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val gridIds = manager.getAppWidgetIds(
            ComponentName(appContext, TotalCheckWidgetReceiver::class.java)
        )
        val counterIds = manager.getAppWidgetIds(
            ComponentName(appContext, TotalCheckCounterWidgetReceiver::class.java)
        )
        return gridIds + counterIds
    }

    suspend fun ensureInitialized(context: Context): WidgetUiSnapshot {
        _snapshotFlow.value?.let { return it }
        val appContext = context.applicationContext
        val loaded = loadSnapshotFromStorage(
            appContext = appContext,
            seedIfEmpty = true,
            preserveResetState = false,
        )
        synchronized(snapshotLock) {
            return _snapshotFlow.value ?: loaded.also { _snapshotFlow.value = it }
        }
    }

    fun onThemeChanged(context: Context, newThemeConfig: ThemeConfig) {
        val appContext = context.applicationContext
        overrideThemeConfig = newThemeConfig
        val updatedSnapshot = synchronized(snapshotLock) {
            _snapshotFlow.value?.let { current ->
                current.copy(themeConfig = newThemeConfig).also {
                    _snapshotFlow.value = it
                }
            }
        }
        if (updatedSnapshot != null) {
            scope.launch {
                DirectWidgetRenderer.renderAll(
                    context = appContext,
                    snapshot = updatedSnapshot,
                    isContentFlip = false,
                    isFilterFlip = false,
                )
            }
        } else {
            notifyDataChanged(appContext)
        }
    }

    fun notifyDataChanged(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            notifyDataChangedNow(appContext)
        }
    }

    suspend fun notifyDataChangedNow(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            val refreshed = loadSnapshotFromStorage(
                appContext = appContext,
                seedIfEmpty = false,
                preserveResetState = true,
            )
            val finalSnapshot = synchronized(snapshotLock) {
                val previous = _snapshotFlow.value
                val now = SystemClock.elapsedRealtime()
                val mergedStates = refreshed.widgetStates.mapValues { (widgetId, state) ->
                    val prevState = previous?.stateForWidget(widgetId)
                    val activeId = refreshed.resolveActiveChecklist(widgetId)?.id
                    val stillConfirming = activeId != null &&
                        resetConfirmChecklistByWidget[widgetId] == activeId &&
                        now < (resetConfirmDeadlineByWidget[widgetId] ?: 0L) &&
                        (prevState?.isResetConfirmActive == true)
                    if (!stillConfirming) {
                        clearResetConfirmLocked(widgetId)
                    }
                    state.copy(
                        isResetConfirmActive = stillConfirming,
                        checklistFlipSlot = prevState?.checklistFlipSlot ?: 0,
                    )
                }
                refreshed.copy(widgetStates = mergedStates).also {
                    _snapshotFlow.value = it
                }
            }
            DirectWidgetRenderer.renderAll(
                context = appContext,
                snapshot = finalSnapshot,
                isContentFlip = false,
                isFilterFlip = false,
            )
        }
    }

    fun onWidgetsDeleted(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext
        synchronized(snapshotLock) {
            for (id in appWidgetIds) {
                clearResetConfirmLocked(id)
                toggleGenerationByWidget.remove(id)
            }
            val current = _snapshotFlow.value
            if (current != null) {
                val nextMap = current.widgetStates.toMutableMap().apply {
                    for (id in appWidgetIds) remove(id)
                }
                _snapshotFlow.value = current.copy(widgetStates = nextMap)
            }
        }
        scope.launch {
            runCatching {
                UserPreferencesRepository(appContext).clearWidgetPreferences(appWidgetIds)
            }
        }
    }

    /**
     * Synchronous < 3ms hot path called directly inside BroadcastReceiver.onReceive().
     * Returns true if snapshot was already warm in memory and handled; false if a cold DB load is required.
     */
    fun tryHandleFastCommand(
        context: Context,
        appWidgetId: Int,
        command: String,
        itemId: Long,
        checklistId: Long,
        direction: Int,
        onScheduleAsync: (suspend (Context) -> Unit) -> Unit,
    ): Boolean {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return true
        val appContext = context.applicationContext
        val step = synchronized(snapshotLock) {
            val current = _snapshotFlow.value ?: return false
            computeCommandStepLocked(
                current = current,
                appWidgetId = appWidgetId,
                command = command,
                itemId = itemId,
                checklistId = checklistId,
                direction = direction,
            )
        } ?: return true // Command is a valid no-op on warm snapshot.

        if (step.delayForRippleMs > 0L) {
            // Allow Launcher's local <ripple> feedback to bloom before replacing RemoteViews.
            val expectedToggleGen = step.toggleGeneration
            val persistAction = step.persistAction
            onScheduleAsync { ctx ->
                val persistJob = persistAction?.let { persist ->
                    scope.launch {
                        dbWriteMutex.withLock {
                            persist(ctx)
                        }
                    }
                }
                delay(step.delayForRippleMs)
                val latestSnapshot = synchronized(snapshotLock) {
                    if (toggleGenerationByWidget[appWidgetId] == expectedToggleGen) {
                        _snapshotFlow.value
                    } else {
                        null
                    }
                }
                if (latestSnapshot != null) {
                    DirectWidgetRenderer.renderAll(
                        context = ctx,
                        snapshot = latestSnapshot,
                        isContentFlip = false,
                        isFilterFlip = false,
                    )
                }
                persistJob?.join()
            }
        } else {
            val targetId = step.targetAppWidgetId
            if (targetId != null) {
                // Pure panel control event: update ONLY the interacted widget instance.
                DirectWidgetRenderer.renderSingleWidgetById(
                    context = appContext,
                    appWidgetId = targetId,
                    snapshot = step.nextSnapshot,
                    isContentFlip = step.isContentFlip,
                    isFilterFlip = step.isFilterFlip,
                )
            } else {
                // Underlying data mutation event: sync all widgets on the home screen.
                DirectWidgetRenderer.renderAll(
                    context = appContext,
                    snapshot = step.nextSnapshot,
                    isContentFlip = false,
                    isFilterFlip = false,
                )
            }
            step.persistAction?.let { persist ->
                onScheduleAsync { ctx ->
                    dbWriteMutex.withLock {
                        persist(ctx)
                    }
                }
            }
        }

        if (step.stage1Generation != 0L) {
            synchronized(snapshotLock) {
                if (resetGenerationByWidget[appWidgetId] == step.stage1Generation &&
                    resetConfirmChecklistByWidget[appWidgetId] == step.stage1ChecklistId
                ) {
                    resetConfirmDeadlineByWidget[appWidgetId] =
                        SystemClock.elapsedRealtime() + RESET_CONFIRM_WINDOW_MS
                    resetRevertJobByWidget[appWidgetId]?.cancel()
                    val expectedGen = step.stage1Generation
                    resetRevertJobByWidget[appWidgetId] = scope.launch {
                        delay(RESET_CONFIRM_REVERT_DELAY_MS)
                        revertResetConfirmIfMatching(appContext, appWidgetId, expectedGen)
                    }
                }
            }
        }

        return true
    }

    suspend fun handleColdCommand(
        context: Context,
        appWidgetId: Int,
        command: String,
        itemId: Long,
        checklistId: Long,
        direction: Int,
    ) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        var pendingAsync: (suspend (Context) -> Unit)? = null
        val didRunFast = tryHandleFastCommand(
            context = appContext,
            appWidgetId = appWidgetId,
            command = command,
            itemId = itemId,
            checklistId = checklistId,
            direction = direction,
            onScheduleAsync = { work -> pendingAsync = work },
        )
        if (didRunFast) {
            pendingAsync?.invoke(appContext)
        }
    }

    fun launchAsyncWithPendingResult(
        context: Context,
        pendingResult: BroadcastReceiver.PendingResult,
        work: suspend (Context) -> Unit,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                work(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun computeCommandStepLocked(
        current: WidgetUiSnapshot,
        appWidgetId: Int,
        command: String,
        itemId: Long,
        checklistId: Long,
        direction: Int,
    ): FastCommandStep? {
        val widgetState = current.stateForWidget(appWidgetId)
        val activeChecklist = current.resolveActiveChecklist(appWidgetId)

        return when (command) {
            TotalCheckWidgetReceiver.CMD_TOGGLE_ITEM -> {
                if (itemId <= 0L) return null
                val activeId = activeChecklist?.id ?: return null
                val listItems = current.itemsByChecklist[activeId].orEmpty()
                var resolvedChecked: Boolean? = null
                val updatedList = listItems.map { item ->
                    if (item.id == itemId) {
                        val next = !item.isChecked
                        resolvedChecked = next
                        item.copy(isChecked = next)
                    } else {
                        item
                    }
                }
                val finalChecked = resolvedChecked ?: return null
                clearResetConfirmLocked(appWidgetId)
                val nextToggleGen = (toggleGenerationByWidget[appWidgetId] ?: 0L) + 1L
                toggleGenerationByWidget[appWidgetId] = nextToggleGen
                val nextItemsMap = current.itemsByChecklist.toMutableMap().apply {
                    put(activeId, updatedList)
                }
                val nextStatesMap = current.widgetStates.toMutableMap().apply {
                    put(
                        appWidgetId,
                        widgetState.copy(
                            isResetConfirmActive = false,
                            lastToggledItemId = itemId,
                        )
                    )
                }
                val nextSnapshot = current.copy(
                    itemsByChecklist = nextItemsMap,
                    widgetStates = nextStatesMap,
                )
                _snapshotFlow.value = nextSnapshot
                FastCommandStep(
                    nextSnapshot = nextSnapshot,
                    delayForRippleMs = TOGGLE_RIPPLE_DELAY_MS,
                    toggleGeneration = nextToggleGen,
                    persistAction = { ctx ->
                        AppDatabase.getInstance(ctx).checklistDao()
                            .updateItemChecked(itemId = itemId, isChecked = finalChecked)
                    },
                )
            }

            TotalCheckWidgetReceiver.CMD_TOGGLE_FILTER -> {
                val nextFilter = !widgetState.showUncheckedOnly
                clearResetConfirmLocked(appWidgetId)
                val nextFlipSlot = 1 - (widgetState.checklistFlipSlot and 1)
                val nextStatesMap = current.widgetStates.toMutableMap().apply {
                    put(
                        appWidgetId,
                        widgetState.copy(
                            showUncheckedOnly = nextFilter,
                            isResetConfirmActive = false,
                            checklistFlipSlot = nextFlipSlot,
                            lastToggledItemId = null,
                        )
                    )
                }
                val nextSnapshot = current.copy(widgetStates = nextStatesMap)
                _snapshotFlow.value = nextSnapshot
                FastCommandStep(
                    nextSnapshot = nextSnapshot,
                    targetAppWidgetId = appWidgetId,
                    isContentFlip = true,
                    isFilterFlip = true,
                    persistAction = { ctx ->
                        UserPreferencesRepository(ctx)
                            .setWidgetShowUncheckedOnly(appWidgetId, nextFilter)
                    },
                )
            }

            TotalCheckWidgetReceiver.CMD_SHOW_ALL -> {
                clearResetConfirmLocked(appWidgetId)
                val didChangeFilter = widgetState.showUncheckedOnly
                val nextFlipSlot = if (didChangeFilter) {
                    1 - (widgetState.checklistFlipSlot and 1)
                } else {
                    widgetState.checklistFlipSlot and 1
                }
                val nextStatesMap = current.widgetStates.toMutableMap().apply {
                    put(
                        appWidgetId,
                        widgetState.copy(
                            showUncheckedOnly = false,
                            isResetConfirmActive = false,
                            checklistFlipSlot = nextFlipSlot,
                            lastToggledItemId = null,
                        )
                    )
                }
                val nextSnapshot = current.copy(widgetStates = nextStatesMap)
                _snapshotFlow.value = nextSnapshot
                FastCommandStep(
                    nextSnapshot = nextSnapshot,
                    targetAppWidgetId = appWidgetId,
                    isContentFlip = didChangeFilter,
                    isFilterFlip = didChangeFilter,
                    persistAction = { ctx ->
                        UserPreferencesRepository(ctx)
                            .setWidgetShowUncheckedOnly(appWidgetId, false)
                    },
                )
            }

            TotalCheckWidgetReceiver.CMD_SWITCH_CHECKLIST -> {
                val checklists = current.checklists
                if (checklists.size <= 1) return null
                val referenceId = checklistId.takeIf { it > 0L } ?: activeChecklist?.id
                val currentIdx = checklists.indexOfFirst { it.id == referenceId }.let {
                    if (it < 0) 0 else it
                }
                val nextIdx = Math.floorMod(currentIdx + direction, checklists.size)
                val targetChecklist = checklists[nextIdx]
                val didSwitch = targetChecklist.id != activeChecklist?.id
                clearResetConfirmLocked(appWidgetId)
                val nextFlipSlot = if (didSwitch) {
                    1 - (widgetState.checklistFlipSlot and 1)
                } else {
                    widgetState.checklistFlipSlot and 1
                }
                val nextStatesMap = current.widgetStates.toMutableMap().apply {
                    put(
                        appWidgetId,
                        widgetState.copy(
                            selectedChecklistId = targetChecklist.id,
                            isResetConfirmActive = false,
                            checklistFlipSlot = nextFlipSlot,
                            lastToggledItemId = null,
                        )
                    )
                }
                val nextSnapshot = current.copy(widgetStates = nextStatesMap)
                _snapshotFlow.value = nextSnapshot
                FastCommandStep(
                    nextSnapshot = nextSnapshot,
                    targetAppWidgetId = appWidgetId,
                    isContentFlip = didSwitch,
                    persistAction = { ctx ->
                        UserPreferencesRepository(ctx)
                            .setWidgetSelectedChecklistId(appWidgetId, targetChecklist.id)
                    },
                )
            }

            TotalCheckWidgetReceiver.CMD_RESET_STAGE -> {
                val targetId = checklistId.takeIf { it > 0L } ?: activeChecklist?.id ?: return null
                val nowElapsed = SystemClock.elapsedRealtime()
                val isConfirming = resetConfirmChecklistByWidget[appWidgetId] == targetId &&
                    nowElapsed < (resetConfirmDeadlineByWidget[appWidgetId] ?: 0L) &&
                    widgetState.isResetConfirmActive

                if (isConfirming) {
                    clearResetConfirmLocked(appWidgetId)
                    val nextSnapshot = withResetChecklist(current, appWidgetId, targetId)
                    _snapshotFlow.value = nextSnapshot
                    FastCommandStep(
                        nextSnapshot = nextSnapshot,
                        persistAction = { ctx ->
                            AppDatabase.getInstance(ctx).checklistDao()
                                .resetChecklistItems(targetId)
                        },
                    )
                } else {
                    clearResetConfirmLocked(appWidgetId)
                    val gen = resetGenerationByWidget[appWidgetId] ?: 1L
                    resetConfirmChecklistByWidget[appWidgetId] = targetId
                    resetConfirmDeadlineByWidget[appWidgetId] = nowElapsed + RESET_CONFIRM_WINDOW_MS
                    val nextStatesMap = current.widgetStates.toMutableMap().apply {
                        put(appWidgetId, widgetState.copy(isResetConfirmActive = true))
                    }
                    val nextSnapshot = current.copy(widgetStates = nextStatesMap)
                    _snapshotFlow.value = nextSnapshot
                    FastCommandStep(
                        nextSnapshot = nextSnapshot,
                        targetAppWidgetId = appWidgetId,
                        stage1Generation = gen,
                        stage1ChecklistId = targetId,
                    )
                }
            }

            TotalCheckWidgetReceiver.CMD_FORCE_RESET -> {
                val targetId = checklistId.takeIf { it > 0L } ?: activeChecklist?.id ?: return null
                clearResetConfirmLocked(appWidgetId)
                val nextSnapshot = withResetChecklist(current, appWidgetId, targetId)
                _snapshotFlow.value = nextSnapshot
                FastCommandStep(
                    nextSnapshot = nextSnapshot,
                    persistAction = { ctx ->
                        AppDatabase.getInstance(ctx).checklistDao()
                            .resetChecklistItems(targetId)
                    },
                )
            }

            else -> null
        }
    }

    private fun withResetChecklist(
        snapshot: WidgetUiSnapshot,
        sourceWidgetId: Int,
        checklistId: Long,
    ): WidgetUiSnapshot {
        val listItems = snapshot.itemsByChecklist[checklistId].orEmpty()
        val resetItems = listItems.map { item ->
            if (item.isChecked) item.copy(isChecked = false) else item
        }
        val nextItemsMap = snapshot.itemsByChecklist.toMutableMap().apply {
            put(checklistId, resetItems)
        }
        val sourceState = snapshot.stateForWidget(sourceWidgetId)
        val nextStatesMap = snapshot.widgetStates.toMutableMap().apply {
            put(
                sourceWidgetId,
                sourceState.copy(isResetConfirmActive = false, lastToggledItemId = null)
            )
        }
        return snapshot.copy(
            itemsByChecklist = nextItemsMap,
            widgetStates = nextStatesMap,
        )
    }

    private fun revertResetConfirmIfMatching(
        appContext: Context,
        appWidgetId: Int,
        expectedGeneration: Long,
    ) {
        val nextSnapshot = synchronized(snapshotLock) {
            if (resetGenerationByWidget[appWidgetId] != expectedGeneration) return
            clearResetConfirmLocked(appWidgetId)
            val current = _snapshotFlow.value ?: return
            val state = current.stateForWidget(appWidgetId)
            if (!state.isResetConfirmActive) return
            val nextStatesMap = current.widgetStates.toMutableMap().apply {
                put(appWidgetId, state.copy(isResetConfirmActive = false))
            }
            current.copy(widgetStates = nextStatesMap).also {
                _snapshotFlow.value = it
            }
        }
        DirectWidgetRenderer.renderSingleWidgetById(
            context = appContext,
            appWidgetId = appWidgetId,
            snapshot = nextSnapshot,
            isContentFlip = false,
            isFilterFlip = false,
        )
    }

    private fun clearResetConfirmLocked(appWidgetId: Int) {
        resetGenerationByWidget[appWidgetId] = (resetGenerationByWidget[appWidgetId] ?: 0L) + 1L
        resetConfirmChecklistByWidget.remove(appWidgetId)
        resetConfirmDeadlineByWidget.remove(appWidgetId)
        resetRevertJobByWidget.remove(appWidgetId)?.cancel()
    }

    private suspend fun loadSnapshotFromStorage(
        appContext: Context,
        seedIfEmpty: Boolean,
        preserveResetState: Boolean,
    ): WidgetUiSnapshot = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(appContext)
        val dao = db.checklistDao()
        val reminderDao = db.reminderDao()
        val prefsRepo = UserPreferencesRepository(appContext)
        if (seedIfEmpty && dao.getChecklistCount() == 0) {
            ChecklistRepository(appContext, dao, reminderDao).seedInitialDataIfNeeded()
        }
        val activeWidgetIds = getAllActiveWidgetIds(appContext)
        val (checklists, prefsByWidget, allItems, loadedThemeConfig, enabledReminders) = dbWriteMutex.withLock {
            val loadedChecklists = dao.getAllChecklists()
            val loadedPrefs = prefsRepo.readAllWidgetStates(activeWidgetIds)
            val loadedItems = dao.getAllItems()
            val diskThemeConfig = prefsRepo.themeConfig.first()
            val loadedReminders = reminderDao.getEnabledReminders()
            SnapshotLoadTuple(loadedChecklists, loadedPrefs, loadedItems, diskThemeConfig, loadedReminders)
        }
        val pendingOverride = overrideThemeConfig
        val effectiveThemeConfig = if (pendingOverride != null) {
            if (loadedThemeConfig == pendingOverride) {
                overrideThemeConfig = null
            }
            pendingOverride
        } else {
            loadedThemeConfig
        }
        val itemsByChecklist = allItems.groupBy { it.checklistId }
        val remindersByChecklist = enabledReminders
            .groupBy { it.checklistId }
            .mapValues { (_, list) ->
                val checklistReminder = list.firstOrNull { it.itemId == null }
                val itemReminders = list.mapNotNull { r ->
                    r.itemId?.let { itemId -> itemId to r }
                }.toMap()
                WidgetReminderSummary(
                    checklistReminder = checklistReminder,
                    activeItemRemindersByItemId = itemReminders,
                )
            }
        val now = SystemClock.elapsedRealtime()
        val states = prefsByWidget.mapValues { (widgetId, prefs) ->
            val resolvedChecklist = checklists.firstOrNull { it.id == prefs.selectedChecklistId }
                ?: checklists.firstOrNull()
            val keepConfirm = preserveResetState &&
                resolvedChecklist != null &&
                resetConfirmChecklistByWidget[widgetId] == resolvedChecklist.id &&
                now < (resetConfirmDeadlineByWidget[widgetId] ?: 0L)
            WidgetInstanceState(
                selectedChecklistId = prefs.selectedChecklistId,
                showUncheckedOnly = prefs.showUncheckedOnly,
                isResetConfirmActive = keepConfirm,
            )
        }
        WidgetUiSnapshot(
            checklists = checklists,
            itemsByChecklist = itemsByChecklist,
            widgetStates = states,
            themeConfig = effectiveThemeConfig,
            remindersByChecklist = remindersByChecklist,
        )
    }

    private data class FastCommandStep(
        val nextSnapshot: WidgetUiSnapshot,
        val targetAppWidgetId: Int? = null,
        val isContentFlip: Boolean = false,
        val isFilterFlip: Boolean = false,
        val delayForRippleMs: Long = 0L,
        val toggleGeneration: Long = 0L,
        val persistAction: (suspend (Context) -> Unit)? = null,
        val stage1Generation: Long = 0L,
        val stage1ChecklistId: Long = 0L,
    )

    private data class SnapshotLoadTuple(
        val checklists: List<ChecklistEntity>,
        val prefsByWidget: Map<Int, WidgetPreferences>,
        val allItems: List<CheckItemEntity>,
        val themeConfig: ThemeConfig,
        val enabledReminders: List<ReminderEntity>,
    )
}
