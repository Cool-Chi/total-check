package com.example.totalcheck.ui.main

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.example.totalcheck.R
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.data.parser.ChecklistTextParser
import com.example.totalcheck.data.parser.ImportFormatMode
import com.example.totalcheck.data.parser.ParsedItemDraft
import com.example.totalcheck.ui.theme.LocalReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.hypot

private val MODAL_ENTRANCE_EASING = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private const val MODAL_SCRIM_ENTER_MS = 200
private const val MODAL_PANEL_ENTER_MS = 250
private const val MODAL_EXIT_MS = 150
private const val MODAL_START_SCALE = 0.92f
private const val MODAL_SCRIM_MAX_ALPHA = 0.48f
private val MODAL_TRANSLATE_Y = 20.dp

@Immutable
internal sealed interface ChecklistModalContent {
    data object CreateChecklist : ChecklistModalContent
    data class RenameChecklist(val checklist: ChecklistEntity) : ChecklistModalContent
    data class DeleteChecklistConfirm(
        val checklist: ChecklistEntity,
        val itemCount: Int
    ) : ChecklistModalContent
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScopedModalHost(
    modalContent: ChecklistModalContent?,
    isOpen: Boolean,
    sessionKey: Int,
    onRequestClose: (restoreFocus: Boolean) -> Unit,
    onConfirmCreate: (String) -> Unit,
    onConfirmImport: (title: String, items: List<ParsedItemDraft>) -> Unit = { _, _ -> },
    onConfirmRename: (ChecklistEntity, String) -> Unit,
    onConfirmDelete: (ChecklistEntity) -> Unit,
    onExited: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val navBarInsets = WindowInsets.navigationBars
    val isImeVisible = WindowInsets.isImeVisible
    val isImeVisibleState = rememberUpdatedState(isImeVisible)

    // 跨會話記憶裝置軟鍵盤實測高度，使輸入型彈窗開啟第一幀即精準停駐於鍵盤上方最終位置，完全不產生二次上移位移
    val defaultImeExtraPx = with(density) { 300.dp.toPx() }
    var rememberedImeExtraPx by remember { mutableFloatStateOf(defaultImeExtraPx) }
    LaunchedEffect(density) {
        val minKeyboardPx = with(density) { 120.dp.toPx() }
        snapshotFlow {
            (imeInsets.getBottom(density) - navBarInsets.getBottom(density)).toFloat()
        }.collectLatest { imeExtra ->
            if (imeExtra > minKeyboardPx) {
                delay(180L)
                rememberedImeExtraPx = imeExtra
            }
        }
    }

    if (modalContent == null) return

    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isReducedMotion = LocalReducedMotion.current
    val translateYPx = with(density) { MODAL_TRANSLATE_Y.toPx() }

    val isTextInputModal = modalContent is ChecklistModalContent.CreateChecklist ||
        modalContent is ChecklistModalContent.RenameChecklist
    var expectImeForModal by remember(sessionKey) { mutableStateOf(isTextInputModal) }
    val currentImeExtraPx = (imeInsets.getBottom(density) - navBarInsets.getBottom(density))
        .coerceAtLeast(0)
        .toFloat()
    val sessionAnchorImePx = remember(sessionKey) {
        if (isTextInputModal) {
            maxOf(rememberedImeExtraPx, currentImeExtraPx)
        } else {
            0f
        }
    }
    val modalImeLiftAnim = remember(sessionKey) { Animatable(sessionAnchorImePx) }

    val scrimProgress = remember { Animatable(0f) }
    val panelProgress = remember { Animatable(0f) }

    val primaryFocus = remember(sessionKey) { FocusRequester() }
    val cancelFocus = remember(sessionKey) { FocusRequester() }
    val confirmFocus = remember(sessionKey) { FocusRequester() }

    LaunchedEffect(isOpen, sessionKey, isReducedMotion) {
        if (isOpen) {
            if (modalContent is ChecklistModalContent.DeleteChecklistConfirm) {
                runCatching { cancelFocus.requestFocus() }
            }
            // 輸入型彈窗於首幀立即同步請求焦點與喚起軟鍵盤，與遮罩及面板入場動效同時展開
            if (isTextInputModal) {
                expectImeForModal = true
                runCatching {
                    primaryFocus.requestFocus()
                    keyboardController?.show()
                }
            }
            if (isReducedMotion) {
                scrimProgress.snapTo(1f)
                panelProgress.snapTo(1f)
            } else {
                coroutineScope {
                    launch {
                        scrimProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = MODAL_SCRIM_ENTER_MS,
                                easing = LinearEasing
                            )
                        )
                    }
                    launch {
                        panelProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = MODAL_PANEL_ENTER_MS,
                                easing = MODAL_ENTRANCE_EASING
                            )
                        )
                    }
                }
            }
        } else {
            keyboardController?.hide()
            if (isReducedMotion) {
                scrimProgress.snapTo(0f)
                panelProgress.snapTo(0f)
            } else {
                coroutineScope {
                    launch {
                        scrimProgress.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = MODAL_EXIT_MS,
                                easing = LinearEasing
                            )
                        )
                    }
                    launch {
                        panelProgress.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = MODAL_EXIT_MS,
                                easing = MODAL_ENTRANCE_EASING
                            )
                        )
                    }
                }
            }
            onExited()
        }
    }

    // 追蹤鍵盤於當前彈窗會話中的實際展開與退場：若使用者以系統操作直接收起鍵盤，即刻解除上方鎖定並平滑歸位
    LaunchedEffect(isOpen, sessionKey, isTextInputModal) {
        if (!isOpen || !isTextInputModal) return@LaunchedEffect
        var hasSeenImeOpen = false
        var peakImeExtraPx = 0f
        val openThresholdPx = with(density) { 40.dp.toPx() }
        val dropTriggerPx = with(density) { 12.dp.toPx() }

        snapshotFlow {
            val imeExtra = (imeInsets.getBottom(density) - navBarInsets.getBottom(density))
                .coerceAtLeast(0)
                .toFloat()
            imeExtra to isImeVisibleState.value
        }.collect { (imeExtra, imeVisible) ->
            if (imeExtra > openThresholdPx && imeVisible) {
                hasSeenImeOpen = true
                if (imeExtra > peakImeExtraPx) {
                    peakImeExtraPx = imeExtra
                }
            } else if (hasSeenImeOpen && (!imeVisible || imeExtra < peakImeExtraPx - dropTriggerPx)) {
                hasSeenImeOpen = false
                peakImeExtraPx = 0f
                if (expectImeForModal) {
                    expectImeForModal = false
                    focusManager.clearFocus()
                }
            }
        }
    }

    // 協調輸入型彈窗垂直落點：開啟時鎖定於鍵盤上方零位移展開；收起鍵盤時平滑回落至畫面正中央
    LaunchedEffect(isOpen, expectImeForModal, isReducedMotion, sessionKey) {
        if (!isOpen || !isTextInputModal) return@LaunchedEffect
        if (expectImeForModal) {
            val targetLift = maxOf(
                sessionAnchorImePx,
                rememberedImeExtraPx,
                (imeInsets.getBottom(density) - navBarInsets.getBottom(density))
                    .coerceAtLeast(0)
                    .toFloat()
            )
            if (modalImeLiftAnim.value < targetLift - 1f) {
                if (isReducedMotion) {
                    modalImeLiftAnim.snapTo(targetLift)
                } else {
                    modalImeLiftAnim.animateTo(
                        targetValue = targetLift,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    )
                }
            }
        } else {
            if (isReducedMotion) {
                modalImeLiftAnim.snapTo(0f)
            } else {
                modalImeLiftAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    BackHandler(enabled = isOpen) {
        val imeExtraPx = (imeInsets.getBottom(density) - navBarInsets.getBottom(density)).coerceAtLeast(0)
        if (imeExtraPx > with(density) { 24.dp.roundToPx() } || isImeVisibleState.value) {
            expectImeForModal = false
            keyboardController?.hide()
            focusManager.clearFocus()
        } else {
            onRequestClose(true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(50f)
    ) {
        // 局部遮罩層：200ms 線性淡入 / 150ms 線性淡出，攔截背景觸控與翻頁
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("modal_scrim")
                .graphicsLayer {
                    alpha = scrimProgress.value.coerceIn(0f, 1f)
                }
                .drawBehind {
                    drawRect(color = Color.Black.copy(alpha = MODAL_SCRIM_MAX_ALPHA))
                }
                .pointerInput(isOpen) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val slop = viewConfiguration.touchSlop
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) {
                                val dist = hypot(
                                    change.position.x - down.position.x,
                                    change.position.y - down.position.y
                                )
                                if (isOpen && dist <= slop) {
                                    onRequestClose(true)
                                }
                                break
                            }
                        }
                    }
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .graphicsLayer {
                    translationY = -modalImeLiftAnim.value * 0.5f
                },
            contentAlignment = Alignment.Center
        ) {
            when (modalContent) {
                is ChecklistModalContent.CreateChecklist -> {
                    val defaultImportTitle = stringResource(R.string.import_default_checklist_title)
                    var isQuickImportMode by remember(sessionKey) { mutableStateOf(false) }
                    var name by remember(sessionKey) { mutableStateOf("") }
                    var rawImportText by remember(sessionKey) { mutableStateOf("") }
                    var fileFallbackTitle by remember(sessionKey) { mutableStateOf<String?>(null) }
                    var previewTitle by remember(sessionKey) { mutableStateOf("") }
                    var hasEditedTitleManually by remember(sessionKey) { mutableStateOf(false) }
                    var previewItems by remember(sessionKey) { mutableStateOf<List<ParsedItemDraft>>(emptyList()) }

                    val parsedDraft = remember(rawImportText) {
                        ChecklistTextParser.parse(rawImportText)
                    }

                    val applyImportSource: (newText: String, fallbackDocTitle: String?) -> Unit = { newText, fallbackDocTitle ->
                        rawImportText = newText
                        fileFallbackTitle = fallbackDocTitle
                        val parsed = ChecklistTextParser.parse(newText)
                        previewItems = parsed.items
                        if (!hasEditedTitleManually || previewTitle.isBlank()) {
                            previewTitle = parsed.title.ifBlank {
                                fallbackDocTitle?.takeIf { it.isNotBlank() } ?: defaultImportTitle
                            }
                        }
                    }

                    val documentLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            val loaded = readImportDocumentFromUri(context, uri)
                            if (loaded != null) {
                                isQuickImportMode = true
                                expectImeForModal = false
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                hasEditedTitleManually = false
                                applyImportSource(loaded.content, loaded.titleFallback)
                            }
                        }
                    }

                    val switchToQuickImportWithClipboard: (hideImeWhenClipboardHasText: Boolean) -> Unit = { hideOnHit ->
                        isQuickImportMode = true
                        val clipText = clipboardManager.getText()?.text.orEmpty()
                        if (clipText.isNotBlank()) {
                            hasEditedTitleManually = false
                            applyImportSource(clipText, fileFallbackTitle)
                            if (hideOnHit) {
                                expectImeForModal = false
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        } else {
                            expectImeForModal = false
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    }

                    val confirmEnabled = if (isQuickImportMode) {
                        previewItems.isNotEmpty() && previewTitle.isNotBlank()
                    } else {
                        name.isNotBlank()
                    }

                    ModalPanelContainer(
                        panelProgressProvider = { panelProgress.value },
                        translateYPx = translateYPx,
                        isReducedMotion = isReducedMotion,
                        hasTextInput = true,
                        confirmEnabled = confirmEnabled,
                        primaryFocus = primaryFocus,
                        cancelFocus = cancelFocus,
                        confirmFocus = confirmFocus,
                        onDismissRequest = { onRequestClose(true) }
                    ) { onFocusIndexChanged ->
                        Text(
                            text = stringResource(R.string.dialog_create_checklist_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Style C Segmented Toggle: 空白清單 vs 快速匯入
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(colorScheme.surfaceContainerHighest)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SegmentedModePill(
                                label = stringResource(R.string.modal_tab_blank_list),
                                selected = !isQuickImportMode,
                                onClick = {
                                    isQuickImportMode = false
                                    expectImeForModal = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                            SegmentedModePill(
                                label = stringResource(R.string.modal_tab_quick_import),
                                selected = isQuickImportMode,
                                onClick = {
                                    isQuickImportMode = true
                                    expectImeForModal = false
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        if (!isQuickImportMode) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(primaryFocus)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            onFocusIndexChanged(0)
                                            expectImeForModal = true
                                        }
                                    },
                                placeholder = {
                                    Text(stringResource(R.string.dialog_create_checklist_placeholder))
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (isOpen && name.isNotBlank()) onConfirmCreate(name)
                                    }
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .slideOutCancelClickable(
                                        shape = RoundedCornerShape(50),
                                        role = Role.Button,
                                        onClick = { switchToQuickImportWithClipboard(true) }
                                    ),
                                shape = RoundedCornerShape(50),
                                color = colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                contentColor = colorScheme.onSecondaryContainer,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.action_import_from_clipboard_shortcut),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            ModalActionRow(
                                cancelFocus = cancelFocus,
                                confirmFocus = confirmFocus,
                                confirmLabel = stringResource(R.string.action_create),
                                confirmEnabled = confirmEnabled,
                                isDestructive = false,
                                onFocusIndexChanged = { btnIdx -> onFocusIndexChanged(1 + btnIdx) },
                                onCancel = { if (isOpen) onRequestClose(true) },
                                onConfirm = { if (isOpen) onConfirmCreate(name) }
                            )
                        } else {
                            // 快速匯入來源列（貼上剪貼簿 + 選擇文檔）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TactileButton(
                                    onClick = { switchToQuickImportWithClipboard(true) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(50),
                                    containerColor = colorScheme.secondaryContainer,
                                    contentColor = colorScheme.onSecondaryContainer,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.action_paste_clipboard),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TactileButton(
                                    onClick = {
                                        documentLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(50),
                                    containerColor = colorScheme.tertiaryContainer,
                                    contentColor = colorScheme.onTertiaryContainer,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.action_select_document),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = rawImportText,
                                onValueChange = { newText ->
                                    applyImportSource(newText, fileFallbackTitle)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(primaryFocus)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            onFocusIndexChanged(0)
                                            expectImeForModal = true
                                        }
                                    },
                                placeholder = {
                                    Text(text = stringResource(R.string.import_raw_text_placeholder))
                                },
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(16.dp)
                            )

                            if (rawImportText.isBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val aiPromptText = stringResource(R.string.import_ai_prompt_text)
                                var promptCopied by remember(sessionKey) { mutableStateOf(false) }
                                LaunchedEffect(promptCopied) {
                                    if (promptCopied) {
                                        delay(1500L)
                                        promptCopied = false
                                    }
                                }
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.import_ai_prompt_title),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = aiPromptText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        TactileIconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(aiPromptText))
                                                promptCopied = true
                                            },
                                            modifier = Modifier.size(34.dp),
                                            shape = CircleShape,
                                            containerColor = if (promptCopied) {
                                                colorScheme.primaryContainer
                                            } else {
                                                colorScheme.surfaceContainerHighest
                                            },
                                            contentColor = if (promptCopied) {
                                                colorScheme.onPrimaryContainer
                                            } else {
                                                colorScheme.onSurfaceVariant
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (promptCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                                contentDescription = stringResource(
                                                    if (promptCopied) R.string.import_ai_prompt_copied else R.string.cd_copy_ai_prompt
                                                ),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (rawImportText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = previewTitle,
                                    onValueChange = { updatedTitle ->
                                        hasEditedTitleManually = true
                                        previewTitle = updatedTitle
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                expectImeForModal = true
                                            }
                                        },
                                    label = { Text(stringResource(R.string.dialog_checklist_name_label)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            expectImeForModal = false
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                        }
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val statsText = when {
                                    previewItems.isEmpty() || parsedDraft.detectedMode == ImportFormatMode.EMPTY ->
                                        stringResource(R.string.import_preview_empty_hint)
                                    parsedDraft.detectedMode == ImportFormatMode.SINGLE_LINE ->
                                        stringResource(R.string.import_preview_stats_single_line, previewItems.size)
                                    else ->
                                        stringResource(R.string.import_preview_stats_multiline, previewItems.size)
                                }
                                val statsContainer = if (previewItems.isNotEmpty()) {
                                    colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    colorScheme.errorContainer.copy(alpha = 0.65f)
                                }
                                val statsColor = if (previewItems.isNotEmpty()) {
                                    colorScheme.onPrimaryContainer
                                } else {
                                    colorScheme.onErrorContainer
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = statsContainer
                                ) {
                                    Text(
                                        text = statsText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = statsColor,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }

                                if (previewItems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = if (expectImeForModal) 112.dp else 188.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        previewItems.forEachIndexed { idx, draft ->
                                            PreviewDraftCapsuleRow(
                                                draft = draft,
                                                onToggleChecked = {
                                                    previewItems = previewItems.toMutableList().also { list ->
                                                        list[idx] = list[idx].copy(isChecked = !list[idx].isChecked)
                                                    }
                                                },
                                                onRemove = {
                                                    previewItems = previewItems.toMutableList().also { list ->
                                                        list.removeAt(idx)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))
                            ModalActionRow(
                                cancelFocus = cancelFocus,
                                confirmFocus = confirmFocus,
                                confirmLabel = stringResource(R.string.action_import_create_count, previewItems.size),
                                confirmEnabled = confirmEnabled,
                                isDestructive = false,
                                onFocusIndexChanged = { btnIdx -> onFocusIndexChanged(1 + btnIdx) },
                                onCancel = { if (isOpen) onRequestClose(true) },
                                onConfirm = {
                                    if (isOpen && previewItems.isNotEmpty() && previewTitle.isNotBlank()) {
                                        onConfirmImport(previewTitle.trim(), previewItems)
                                    }
                                }
                            )
                        }
                    }
                }

                is ChecklistModalContent.RenameChecklist -> {
                    var nameInput by remember(sessionKey) {
                        mutableStateOf(
                            TextFieldValue(
                                text = modalContent.checklist.name,
                                selection = TextRange(modalContent.checklist.name.length)
                            )
                        )
                    }
                    val confirmEnabled = nameInput.text.isNotBlank()
                    ModalPanelContainer(
                        panelProgressProvider = { panelProgress.value },
                        translateYPx = translateYPx,
                        isReducedMotion = isReducedMotion,
                        hasTextInput = true,
                        confirmEnabled = confirmEnabled,
                        primaryFocus = primaryFocus,
                        cancelFocus = cancelFocus,
                        confirmFocus = confirmFocus,
                        onDismissRequest = { onRequestClose(true) }
                    ) { onFocusIndexChanged ->
                        Text(
                            text = stringResource(R.string.dialog_rename_checklist_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(primaryFocus)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        onFocusIndexChanged(0)
                                        expectImeForModal = true
                                    }
                                },
                            label = { Text(stringResource(R.string.dialog_checklist_name_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (isOpen && nameInput.text.isNotBlank()) {
                                        onConfirmRename(modalContent.checklist, nameInput.text)
                                    }
                                }
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        ModalActionRow(
                            cancelFocus = cancelFocus,
                            confirmFocus = confirmFocus,
                            confirmLabel = stringResource(R.string.action_save),
                            confirmEnabled = confirmEnabled,
                            isDestructive = false,
                            onFocusIndexChanged = { btnIdx -> onFocusIndexChanged(1 + btnIdx) },
                            onCancel = { if (isOpen) onRequestClose(true) },
                            onConfirm = { if (isOpen) onConfirmRename(modalContent.checklist, nameInput.text) }
                        )
                    }
                }

                is ChecklistModalContent.DeleteChecklistConfirm -> {
                    ModalPanelContainer(
                        panelProgressProvider = { panelProgress.value },
                        translateYPx = translateYPx,
                        isReducedMotion = isReducedMotion,
                        hasTextInput = false,
                        confirmEnabled = true,
                        primaryFocus = primaryFocus,
                        cancelFocus = cancelFocus,
                        confirmFocus = confirmFocus,
                        onDismissRequest = { onRequestClose(true) }
                    ) { onFocusIndexChanged ->
                        Text(
                            text = stringResource(R.string.dialog_delete_checklist_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (modalContent.itemCount > 0) {
                                stringResource(
                                    R.string.dialog_delete_checklist_message,
                                    modalContent.checklist.name,
                                    modalContent.itemCount
                                )
                            } else {
                                stringResource(
                                    R.string.dialog_delete_checklist_empty_message,
                                    modalContent.checklist.name
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        ModalActionRow(
                            cancelFocus = cancelFocus,
                            confirmFocus = confirmFocus,
                            confirmLabel = stringResource(R.string.action_delete),
                            confirmEnabled = true,
                            isDestructive = true,
                            onFocusIndexChanged = onFocusIndexChanged,
                            onCancel = { if (isOpen) onRequestClose(true) },
                            onConfirm = { if (isOpen) onConfirmDelete(modalContent.checklist) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModalPanelContainer(
    panelProgressProvider: () -> Float,
    translateYPx: Float,
    isReducedMotion: Boolean,
    hasTextInput: Boolean,
    confirmEnabled: Boolean,
    primaryFocus: FocusRequester,
    cancelFocus: FocusRequester,
    confirmFocus: FocusRequester,
    onDismissRequest: () -> Unit,
    content: @Composable (onFocusIndexChanged: (Int) -> Unit) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var focusedCycleIndex by remember { mutableIntStateOf(0) }

    val focusCycle = remember(hasTextInput, confirmEnabled, primaryFocus, cancelFocus, confirmFocus) {
        buildList {
            if (hasTextInput) add(0 to primaryFocus)
            val cancelIdx = if (hasTextInput) 1 else 0
            add(cancelIdx to cancelFocus)
            if (confirmEnabled) {
                add((cancelIdx + 1) to confirmFocus)
            }
        }
    }

    Surface(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 400.dp)
            .fillMaxWidth()
            .testTag("modal_panel")
            .graphicsLayer {
                val p = panelProgressProvider().coerceIn(0f, 1f)
                alpha = p
                if (!isReducedMotion) {
                    val sc = lerp(MODAL_START_SCALE, 1f, p)
                    scaleX = sc
                    scaleY = sc
                    translationY = lerp(translateYPx, 0f, p)
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
            }
            .pointerInput(Unit) {
                // 避免點擊面板內非互動空白區域穿透至背後遮罩
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                }
            }
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        onDismissRequest()
                        true
                    }
                    Key.Tab -> {
                        if (focusCycle.isNotEmpty()) {
                            val currentPos = focusCycle.indexOfFirst { it.first == focusedCycleIndex }
                            val nextPos = if (event.isShiftPressed) {
                                if (currentPos <= 0) focusCycle.lastIndex else currentPos - 1
                            } else {
                                if (currentPos < 0 || currentPos >= focusCycle.lastIndex) 0 else currentPos + 1
                            }
                            runCatching { focusCycle[nextPos].second.requestFocus() }
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        if (hasTextInput && focusedCycleIndex == 0) {
                            runCatching { cancelFocus.requestFocus() }
                        }
                        true
                    }
                    Key.DirectionUp -> {
                        if (hasTextInput && focusedCycleIndex > 0) {
                            runCatching { primaryFocus.requestFocus() }
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (hasTextInput && focusedCycleIndex == 0) {
                            false
                        } else {
                            val cancelIdx = if (hasTextInput) 1 else 0
                            if (focusedCycleIndex > cancelIdx) {
                                runCatching { cancelFocus.requestFocus() }
                            }
                            true
                        }
                    }
                    Key.DirectionRight -> {
                        if (hasTextInput && focusedCycleIndex == 0) {
                            false
                        } else {
                            val cancelIdx = if (hasTextInput) 1 else 0
                            if (focusedCycleIndex == cancelIdx && confirmEnabled) {
                                runCatching { confirmFocus.requestFocus() }
                            }
                            true
                        }
                    }
                    else -> false
                }
            }
            .semantics { isTraversalGroup = true },
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            content { idx -> focusedCycleIndex = idx }
        }
    }
}

@Composable
private fun SegmentedModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .slideOutCancelClickable(
                shape = RoundedCornerShape(50),
                role = Role.Tab,
                onClick = onClick
            ),
        shape = RoundedCornerShape(50),
        color = if (selected) colorScheme.primary else Color.Transparent,
        contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PreviewDraftCapsuleRow(
    draft: ParsedItemDraft,
    onToggleChecked: () -> Unit,
    onRemove: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (draft.isChecked) {
        colorScheme.surfaceContainerLow
    } else {
        colorScheme.surfaceContainer
    }
    val borderColor = if (draft.isChecked) {
        colorScheme.outlineVariant.copy(alpha = 0.45f)
    } else {
        colorScheme.tertiary.copy(alpha = 0.5f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .slideOutCancelClickable(
                shape = RoundedCornerShape(16.dp),
                role = Role.Checkbox,
                onClick = onToggleChecked
            ),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (draft.isChecked) {
                    colorScheme.surfaceContainerHighest
                } else {
                    colorScheme.tertiaryContainer
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (draft.isChecked) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = stringResource(R.string.status_checked),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.status_unchecked),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = draft.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (draft.isChecked) {
                    colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    colorScheme.onSurface
                },
                textDecoration = if (draft.isChecked) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TactileIconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                contentColor = colorScheme.onSurfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cd_remove_preview_item, draft.label),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private data class LoadedDocument(
    val titleFallback: String?,
    val content: String
)

private fun readImportDocumentFromUri(context: Context, uri: Uri): LoadedDocument? {
    val content = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        }
    }.getOrNull() ?: return null

    var displayName: String? = null
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    displayName = cursor.getString(idx)
                }
            }
        }
    }
    val rawName = displayName ?: uri.lastPathSegment?.substringAfterLast('/')
    val baseTitle = rawName
        ?.substringBeforeLast('.')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    return LoadedDocument(
        titleFallback = baseTitle,
        content = content
    )
}

@Composable
private fun ModalActionRow(
    cancelFocus: FocusRequester,
    confirmFocus: FocusRequester,
    confirmLabel: String,
    confirmEnabled: Boolean,
    isDestructive: Boolean,
    onFocusIndexChanged: (Int) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TactileTextButton(
            onClick = onCancel,
            modifier = Modifier
                .focusRequester(cancelFocus)
                .onFocusChanged { if (it.isFocused) onFocusIndexChanged(0) }
                .testTag("modal_cancel_button")
        ) {
            Text(stringResource(R.string.action_cancel))
        }
        TactileButton(
            onClick = onConfirm,
            enabled = confirmEnabled,
            shape = RoundedCornerShape(50),
            containerColor = if (isDestructive) colorScheme.error else colorScheme.primary,
            contentColor = if (isDestructive) colorScheme.onError else colorScheme.onPrimary,
            modifier = Modifier
                .focusRequester(confirmFocus)
                .onFocusChanged { if (it.isFocused) onFocusIndexChanged(1) }
                .testTag("modal_confirm_button")
        ) {
            Text(confirmLabel)
        }
    }
}
