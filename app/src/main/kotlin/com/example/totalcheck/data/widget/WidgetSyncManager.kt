package com.example.totalcheck.data.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.os.SystemClock
import com.example.totalcheck.data.local.AppDatabase
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.data.preferences.UserPreferencesRepository
import com.example.totalcheck.data.preferences.WidgetPreferences
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

data class WidgetUiSnapshot(
    val checklists: List<ChecklistEntity>,
    val activeChecklist: ChecklistEntity?,
    val activeChecklistIndex: Int,
    val itemsByChecklist: Map<Long, List<CheckItemEntity>>,
    val showUncheckedOnly: Boolean,
    val isResetConfirmActive: Boolean,
    val checklistFlipSlot: Int = 0,
) {
    val activeChecklistItems: List<CheckItemEntity>
        get() = activeChecklist?.let { itemsByChecklist[it.id] }.orEmpty()
}

object WidgetSyncManager {

    private const val RESET_CONFIRM_WINDOW_MS = 5_000L
    private const val RESET_CONFIRM_REVERT_DELAY_MS = 5_050L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshotLock = Any()
    private val dbWriteMutex = Mutex()

    private val _snapshotFlow = MutableStateFlow<WidgetUiSnapshot?>(null)
    val snapshotFlow: StateFlow<WidgetUiSnapshot?> = _snapshotFlow.asStateFlow()

    private var resetConfirmChecklistId: Long? = null
    private var resetConfirmDeadlineElapsed: Long = 0L
    private var resetGeneration: Long = 0L
    private var resetRevertJob: Job? = null

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

    fun notifyDataChanged(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                val refreshed = loadSnapshotFromStorage(
                    appContext = appContext,
                    seedIfEmpty = false,
                    preserveResetState = true,
                )
                val finalSnapshot = synchronized(snapshotLock) {
                    val previous = _snapshotFlow.value
                    val activeId = refreshed.activeChecklist?.id
                    val stillConfirming = activeId != null &&
                        resetConfirmChecklistId == activeId &&
                        SystemClock.elapsedRealtime() < resetConfirmDeadlineElapsed &&
                        (previous?.isResetConfirmActive == true)
                    if (!stillConfirming) {
                        clearResetConfirmLocked()
                    }
                    refreshed.copy(
                        isResetConfirmActive = stillConfirming,
                        checklistFlipSlot = previous?.checklistFlipSlot ?: 0,
                    ).also {
                        _snapshotFlow.value = it
                    }
                }
                DirectWidgetRenderer.renderAllWidgets(appContext, finalSnapshot)
            }
        }
    }

    /**
     * Partially updates only the header bar via AppWidgetManager.partiallyUpdateAppWidget,
     * leaving tile slots and ViewFlipper untouched.
     */
    fun notifyHeaderOnlyChanged(context: Context) {
        val appContext = context.applicationContext
        val warmSnapshot = _snapshotFlow.value
        if (warmSnapshot != null) {
            DirectWidgetRenderer.updateHeaderOnly(appContext, warmSnapshot)
            return
        }
        scope.launch {
            runCatching {
                val loaded = ensureInitialized(appContext)
                DirectWidgetRenderer.updateHeaderOnly(appContext, loaded)
            }
        }
    }

    /**
     * Partially updates only the header bar and the single toggled `<CheckBox>` slot
     * (`widget_tile_k`) via `AppWidgetManager.partiallyUpdateAppWidget`, keeping sibling
     * tiles and ViewFlipper completely untouched.
     */
    fun notifySingleTileChanged(
        context: Context,
        itemId: Long,
        newChecked: Boolean,
    ) {
        val appContext = context.applicationContext
        val warmSnapshot = _snapshotFlow.value
        if (warmSnapshot != null) {
            DirectWidgetRenderer.updateSingleTileInPlace(appContext, itemId, newChecked)
            return
        }
        scope.launch {
            runCatching {
                ensureInitialized(appContext)
                DirectWidgetRenderer.updateSingleTileInPlace(appContext, itemId, newChecked)
            }
        }
    }

    /**
     * Synchronous < 3ms hot path called directly inside BroadcastReceiver.onReceive().
     * Returns true if snapshot was already warm in memory and the widget UI was updated
     * immediately on the caller thread; false if a cold DB load is required first.
     */
    fun tryHandleFastCommand(
        context: Context,
        command: String,
        itemId: Long,
        checklistId: Long,
        direction: Int,
        targetChecked: Boolean? = null,
        onSchedulePersist: (suspend (Context) -> Unit) -> Unit,
    ): Boolean {
        val appContext = context.applicationContext
        val step = synchronized(snapshotLock) {
            val current = _snapshotFlow.value ?: return false
            computeCommandStepLocked(
                current = current,
                command = command,
                itemId = itemId,
                checklistId = checklistId,
                direction = direction,
                targetChecked = targetChecked,
            )
        } ?: return true // Command is a valid no-op on warm snapshot.

        // Render & push RemoteViews immediately before BroadcastReceiver.onReceive() returns.
        if (step.singleTileItemId > 0L) {
            notifySingleTileChanged(appContext, step.singleTileItemId, step.singleTileNewChecked)
        } else {
            DirectWidgetRenderer.renderAllWidgets(appContext, step.nextSnapshot)
        }

        if (step.stage1Generation != 0L) {
            synchronized(snapshotLock) {
                if (resetGeneration == step.stage1Generation &&
                    resetConfirmChecklistId == step.stage1ChecklistId
                ) {
                    resetConfirmDeadlineElapsed =
                        SystemClock.elapsedRealtime() + RESET_CONFIRM_WINDOW_MS
                    resetRevertJob?.cancel()
                    val expectedGen = step.stage1Generation
                    resetRevertJob = scope.launch {
                        delay(RESET_CONFIRM_REVERT_DELAY_MS)
                        revertResetConfirmIfMatching(appContext, expectedGen)
                    }
                }
            }
        }

        step.persistAction?.let { persist ->
            onSchedulePersist(persist)
        }
        return true
    }

    suspend fun handleColdCommand(
        context: Context,
        command: String,
        itemId: Long,
        checklistId: Long,
        direction: Int,
        targetChecked: Boolean? = null,
    ) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        var pendingPersist: (suspend (Context) -> Unit)? = null
        val didRunFast = tryHandleFastCommand(
            context = appContext,
            command = command,
            itemId = itemId,
            checklistId = checklistId,
            direction = direction,
            targetChecked = targetChecked,
            onSchedulePersist = { persist -> pendingPersist = persist },
        )
        if (didRunFast) {
            pendingPersist?.let { persist ->
                dbWriteMutex.withLock {
                    persist(appContext)
                }
            }
        }
    }

    fun launchPersistWithPendingResult(
        context: Context,
        pendingResult: BroadcastReceiver.PendingResult,
        persist: suspend (Context) -> Unit,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                dbWriteMutex.withLock {
                    persist(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun computeCommandStepLocked(
        current: WidgetUiSnapshot,
        command: String,
        itemId: Long,
        checklistId: Long,
        direction: Int,
        targetChecked: Boolean? = null,
    ): FastCommandStep? {
        return when (command) {
            TotalCheckWidgetReceiver.CMD_TOGGLE_ITEM -> {
                if (itemId <= 0L) return null
                val activeId = current.activeChecklist?.id ?: return null
                val listItems = current.itemsByChecklist[activeId].orEmpty()
                var resolvedChecked: Boolean? = null
                val updatedList = listItems.map { item ->
                    if (item.id == itemId) {
                        val next = targetChecked ?: !item.isChecked
                        resolvedChecked = next
                        item.copy(isChecked = next)
                    } else {
                        item
                    }
                }
                val finalTargetChecked = resolvedChecked ?: return null
                val isSingleTileInPlace = !current.showUncheckedOnly &&
                    listItems.isNotEmpty() &&
                    updatedList.isNotEmpty()
                clearResetConfirmLocked()
                val nextMap = current.itemsByChecklist.toMutableMap().apply {
                    put(activeId, updatedList)
                }
                val nextSnapshot = current.copy(
                    itemsByChecklist = nextMap,
                    isResetConfirmActive = false,
                )
                _snapshotFlow.value = nextSnapshot
                FastCommandStep(
                    nextSnapshot = nextSnapshot,
                    singleTileItemId = if (isSingleTileInPlace) itemId else -1L,
                    singleTileNewChecked = finalTargetChecked,
                    persistAction = { ctx ->
                        AppDatabase.getInstance(ctx).checklistDao()
                            .updateItemChecked(itemId = itemId, isChecked = finalTargetChecked)
                    },
                )
            }

            TotalCheckWidgetReceiver.CMD_TOGGLE_FILTER -> {
                val nextFilter = !current.showUncheckedOnly
                clearResetConfirmLocked()
                val nextSnapshot = current.copy(
                    showUncheckedOnly = nextFilter,
                    isResetConfirmActive = false,
                )
                _snapshotFlow.value = nextSnapshot
                FastCommandStep(
                    nextSnapshot = nextSnapshot,
                    persistAction = { ctx ->
                        UserPreferencesRepository(ctx).setWidgetShowUncheckedOnly(nextFilter)
                    },
                )
            }

            TotalCheckWidgetReceiver.CMD_SHOW_ALL -> {
                clearResetConfirmLocked()
                val nextSnapshot = current.copy(
                    showUncheckedOnly = false,
                    isResetConfirmActive = false,
                )
                _snapshotFlow.value = nextSnapshot
                FastCommandStep(
                    nextSnapshot = nextSnapshot,
                    persistAction = { ctx ->
                        UserPreferencesRepository(ctx).setWidgetShowUncheckedOnly(false)
                    },
                )
            }

            TotalCheckWidgetReceiver.CMD_SWITCH_CHECKLIST -> {
                val checklists = current.checklists
                if (checklists.size <= 1) return null
                val referenceId = checklistId.takeIf { it > 0L } ?: current.activeChecklist?.id
                val currentIdx = checklists.indexOfFirst { it.id == referenceId }.let {
                    if (it < 0) 0 else it
                }
                val nextIdx = Math.floorMod(currentIdx + direction, checklists.size)
                val targetChecklist = checklists[nextIdx]
                clearResetConfirmLocked()
                val nextSnapshot = current.copy(
                    activeChecklist = targetChecklist,
                    activeChecklistIndex = nextIdx,
                    isResetConfirmActive = false,
                    checklistFlipSlot = 1 - (current.checklistFlipSlot and 1),
                )
                _snapshotFlow.value = nextSnapshot
                FastCommandStep(
                    nextSnapshot = nextSnapshot,
                    persistAction = { ctx ->
                        UserPreferencesRepository(ctx)
                            .setWidgetSelectedChecklistId(targetChecklist.id)
                    },
                )
            }

            TotalCheckWidgetReceiver.CMD_RESET_STAGE -> {
                val targetId = checklistId.takeIf { it > 0L } ?: current.activeChecklist?.id ?: return null
                val nowElapsed = SystemClock.elapsedRealtime()
                val isConfirming = resetConfirmChecklistId == targetId &&
                    nowElapsed < resetConfirmDeadlineElapsed &&
                    current.isResetConfirmActive

                if (isConfirming) {
                    clearResetConfirmLocked()
                    val nextSnapshot = withResetChecklist(current, targetId)
                    _snapshotFlow.value = nextSnapshot
                    FastCommandStep(
                        nextSnapshot = nextSnapshot,
                        persistAction = { ctx ->
                            AppDatabase.getInstance(ctx).checklistDao()
                                .resetChecklistItems(targetId)
                        },
                    )
                } else {
                    clearResetConfirmLocked()
                    val gen = resetGeneration
                    resetConfirmChecklistId = targetId
                    resetConfirmDeadlineElapsed = nowElapsed + RESET_CONFIRM_WINDOW_MS
                    val nextSnapshot = current.copy(isResetConfirmActive = true)
                    _snapshotFlow.value = nextSnapshot
                    FastCommandStep(
                        nextSnapshot = nextSnapshot,
                        stage1Generation = gen,
                        stage1ChecklistId = targetId,
                    )
                }
            }

            TotalCheckWidgetReceiver.CMD_FORCE_RESET -> {
                val targetId = checklistId.takeIf { it > 0L } ?: current.activeChecklist?.id ?: return null
                clearResetConfirmLocked()
                val nextSnapshot = withResetChecklist(current, targetId)
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
        checklistId: Long,
    ): WidgetUiSnapshot {
        val listItems = snapshot.itemsByChecklist[checklistId].orEmpty()
        val resetItems = listItems.map { item ->
            if (item.isChecked) item.copy(isChecked = false) else item
        }
        val nextMap = snapshot.itemsByChecklist.toMutableMap().apply {
            put(checklistId, resetItems)
        }
        return snapshot.copy(
            itemsByChecklist = nextMap,
            isResetConfirmActive = false,
        )
    }

    private fun revertResetConfirmIfMatching(
        appContext: Context,
        expectedGeneration: Long,
    ) {
        val nextSnapshot = synchronized(snapshotLock) {
            if (resetGeneration != expectedGeneration) return
            clearResetConfirmLocked()
            val current = _snapshotFlow.value ?: return
            if (!current.isResetConfirmActive) return
            current.copy(isResetConfirmActive = false).also {
                _snapshotFlow.value = it
            }
        }
        DirectWidgetRenderer.updateHeaderOnly(appContext, nextSnapshot)
    }

    private fun clearResetConfirmLocked() {
        resetGeneration++
        resetConfirmChecklistId = null
        resetConfirmDeadlineElapsed = 0L
        resetRevertJob?.cancel()
        resetRevertJob = null
    }

    private suspend fun loadSnapshotFromStorage(
        appContext: Context,
        seedIfEmpty: Boolean,
        preserveResetState: Boolean,
    ): WidgetUiSnapshot = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getInstance(appContext).checklistDao()
        val prefsRepo = UserPreferencesRepository(appContext)
        if (seedIfEmpty && dao.getChecklistCount() == 0) {
            ChecklistRepository(appContext, dao).seedInitialDataIfNeeded()
        }
        val (checklists, prefs, allItems) = dbWriteMutex.withLock {
            val loadedChecklists = dao.getAllChecklists()
            val loadedPrefs = prefsRepo.widgetPreferences.first()
            val loadedItems = dao.getAllItems()
            SnapshotLoadTuple(loadedChecklists, loadedPrefs, loadedItems)
        }
        val activeChecklist = checklists.firstOrNull { it.id == prefs.selectedChecklistId }
            ?: checklists.firstOrNull()
        val activeIdx = if (activeChecklist != null) {
            checklists.indexOf(activeChecklist).coerceAtLeast(0)
        } else {
            0
        }
        val itemsByChecklist = allItems.groupBy { it.checklistId }
        val keepConfirm = preserveResetState &&
            activeChecklist != null &&
            resetConfirmChecklistId == activeChecklist.id &&
            SystemClock.elapsedRealtime() < resetConfirmDeadlineElapsed
        WidgetUiSnapshot(
            checklists = checklists,
            activeChecklist = activeChecklist,
            activeChecklistIndex = activeIdx,
            itemsByChecklist = itemsByChecklist,
            showUncheckedOnly = prefs.showUncheckedOnly,
            isResetConfirmActive = keepConfirm,
        )
    }

    private data class FastCommandStep(
        val nextSnapshot: WidgetUiSnapshot,
        val singleTileItemId: Long = -1L,
        val singleTileNewChecked: Boolean = false,
        val persistAction: (suspend (Context) -> Unit)? = null,
        val stage1Generation: Long = 0L,
        val stage1ChecklistId: Long = 0L,
    )

    private data class SnapshotLoadTuple(
        val checklists: List<ChecklistEntity>,
        val prefs: WidgetPreferences,
        val allItems: List<CheckItemEntity>,
    )
}
