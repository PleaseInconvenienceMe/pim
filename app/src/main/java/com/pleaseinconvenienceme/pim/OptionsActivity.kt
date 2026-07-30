package com.pleaseinconvenienceme.pim

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val CUSTOM_RANGE_LIMIT = 999

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(modifier: Modifier = Modifier, appName: String? = null, packageName: String = "", showPreviewState: MutableState<Boolean> = mutableStateOf(false), enabled: Boolean = true) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val writePrefs = if (appName != null)
        context.getSharedPreferences(PrefsKeys.APP_OVERRIDES, Context.MODE_PRIVATE)
    else
        context.getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
    fun key(k: String) = if (appName != null) "$appName::$k" else k

    var selectedTaskType by remember { mutableStateOf(writePrefs.getInt(key(PrefsKeys.TASK_TYPE), PrefsKeys.DEFAULT_TASK_TYPE)) }
    var selectedLevel by remember { mutableStateOf(writePrefs.getInt(key(PrefsKeys.DIFFICULTY_LEVEL), PrefsKeys.DEFAULT_DIFFICULTY)) }
    var typingDifficulty by remember { mutableStateOf(writePrefs.getInt(key(PrefsKeys.TYPING_DIFFICULTY), PrefsKeys.DEFAULT_TYPING_DIFFICULTY)) }
    var typingCharSet by remember { mutableStateOf(writePrefs.getInt(key(PrefsKeys.TYPING_CHAR_SET), PrefsKeys.DEFAULT_TYPING_CHAR_SET)) }
    var typingLength by remember { mutableStateOf(writePrefs.getInt(key(PrefsKeys.TYPING_LENGTH), PrefsKeys.DEFAULT_TYPING_LENGTH)) }

    // Custom math difficulty
    val initOps = writePrefs.getStringSet(key(PrefsKeys.CUSTOM_OPERATIONS), setOf("+")) ?: setOf("+")
    var customOperands by remember { mutableStateOf(writePrefs.getInt(key(PrefsKeys.CUSTOM_OPERANDS), PrefsKeys.DEFAULT_OPERANDS)) }
    var customAdd by remember { mutableStateOf(initOps.contains("+")) }
    var customSub by remember { mutableStateOf(initOps.contains("-")) }
    var customMul by remember { mutableStateOf(initOps.contains("*")) }
    var customDiv by remember { mutableStateOf(initOps.contains("/")) }
    var customMin by remember { mutableStateOf(writePrefs.getInt(key(PrefsKeys.CUSTOM_RANGE_MIN), PrefsKeys.DEFAULT_RANGE_MIN)) }
    var customMax by remember { mutableStateOf(writePrefs.getInt(key(PrefsKeys.CUSTOM_RANGE_MAX), PrefsKeys.DEFAULT_RANGE_MAX)) }

    var revealSeconds by remember { mutableStateOf(writePrefs.getInt(key(PrefsKeys.REVEAL_SECONDS), PrefsKeys.DEFAULT_REVEAL_SECONDS)) }
    var repeatIncrement by remember { mutableStateOf(writePrefs.getInt(key(PrefsKeys.REPEAT_DELAY_INCREMENT), PrefsKeys.DEFAULT_REPEAT_DELAY_INCREMENT)) }
    var tappingDifficulty by remember { mutableStateOf(writePrefs.getInt(key(PrefsKeys.TAPPING_DIFFICULTY), PrefsKeys.DEFAULT_TAPPING_DIFFICULTY)) }
    var tappingDotCount by remember { mutableStateOf(writePrefs.getInt(key(PrefsKeys.TAPPING_DOT_COUNT), PrefsKeys.DEFAULT_TAPPING_DOTS)) }
    var tappingDotDelay by remember { mutableStateOf(writePrefs.getInt(key(PrefsKeys.TAPPING_DOT_DELAY), PrefsKeys.DEFAULT_TAPPING_DOT_DELAY)) }
    var overlayTimer by remember { mutableStateOf(writePrefs.getBoolean(key(PrefsKeys.OVERLAY_TIMER), PrefsKeys.DEFAULT_OVERLAY_TIMER)) }
    var overlaySize by remember { mutableStateOf(writePrefs.getInt(key(PrefsKeys.OVERLAY_TIMER_SIZE), PrefsKeys.DEFAULT_OVERLAY_SIZE)) }

    // Auto-save simple int prefs
    LaunchedEffect(selectedTaskType) { writePrefs.edit().putInt(key(PrefsKeys.TASK_TYPE), selectedTaskType).apply() }
    LaunchedEffect(typingDifficulty) {
        writePrefs.edit().putInt(key(PrefsKeys.TYPING_DIFFICULTY), typingDifficulty).apply()
        when (typingDifficulty) {
            0 -> { typingCharSet = 3; typingLength = 5 }
            1 -> { typingCharSet = 0; typingLength = 8 }
            2 -> { typingCharSet = 2; typingLength = 10 }
        }
    }
    LaunchedEffect(typingCharSet) { writePrefs.edit().putInt(key(PrefsKeys.TYPING_CHAR_SET), typingCharSet).apply() }
    LaunchedEffect(typingLength) { writePrefs.edit().putInt(key(PrefsKeys.TYPING_LENGTH), typingLength).apply() }
    LaunchedEffect(selectedLevel) { writePrefs.edit().putInt(key(PrefsKeys.DIFFICULTY_LEVEL), selectedLevel).apply() }
    LaunchedEffect(customOperands, customAdd, customSub, customMul, customDiv) {
        if (selectedLevel == 4) {
            val ops = buildSet {
                if (customAdd) add("+")
                if (customSub) add("-")
                if (customMul) add("*")
                if (customDiv) add("/")
            }
            writePrefs.edit()
                .putInt(key(PrefsKeys.CUSTOM_OPERANDS), customOperands)
                .putStringSet(key(PrefsKeys.CUSTOM_OPERATIONS), ops)
                .apply()
        }
    }
    LaunchedEffect(customMin, customMax) {
        if (selectedLevel == 4 && customMin < customMax) {
            writePrefs.edit()
                .putInt(key(PrefsKeys.CUSTOM_RANGE_MIN), customMin)
                .putInt(key(PrefsKeys.CUSTOM_RANGE_MAX), customMax)
                .apply()
        }
    }
    LaunchedEffect(revealSeconds) { writePrefs.edit().putInt(key(PrefsKeys.REVEAL_SECONDS), revealSeconds).apply() }
    LaunchedEffect(repeatIncrement) { writePrefs.edit().putInt(key(PrefsKeys.REPEAT_DELAY_INCREMENT), repeatIncrement).apply() }
    LaunchedEffect(tappingDifficulty) {
        writePrefs.edit().putInt(key(PrefsKeys.TAPPING_DIFFICULTY), tappingDifficulty).apply()
        when (tappingDifficulty) {
            0 -> { tappingDotCount = 4; tappingDotDelay = 1 }
            1 -> { tappingDotCount = 6; tappingDotDelay = 1 }
            2 -> { tappingDotCount = 10; tappingDotDelay = 2 }
        }
    }
    LaunchedEffect(tappingDotCount) { writePrefs.edit().putInt(key(PrefsKeys.TAPPING_DOT_COUNT), tappingDotCount).apply() }
    LaunchedEffect(tappingDotDelay) { writePrefs.edit().putInt(key(PrefsKeys.TAPPING_DOT_DELAY), tappingDotDelay).apply() }
    LaunchedEffect(overlayTimer) { writePrefs.edit().putBoolean(key(PrefsKeys.OVERLAY_TIMER), overlayTimer).apply() }
    LaunchedEffect(overlaySize) { writePrefs.edit().putInt(key(PrefsKeys.OVERLAY_TIMER_SIZE), overlaySize).apply() }


    var showPreview by showPreviewState
    // Auto-expand if any hidden setting has a non-default value
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().alpha(if (enabled) 1f else 0.5f)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
                .imePadding()
        ) {
        // Consistent selected color for all segmented buttons
        val selectedColors = SegmentedButtonDefaults.colors(
            activeContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // ── TASK SETTINGS ──
        Column {
        // ── TASK TYPE ──
        Text(
            text = "Task Type",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedTaskType == 0,
                onClick = { selectedTaskType = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                colors = selectedColors
            ) { Text("Math") }
            SegmentedButton(
                selected = selectedTaskType == 1,
                onClick = { selectedTaskType = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                colors = selectedColors
            ) { Text("Typing") }
            SegmentedButton(
                selected = selectedTaskType == 3,
                onClick = { selectedTaskType = 3 },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                colors = selectedColors
            ) { Text("Tapping") }
        }

        // ── MATH SETTINGS ──
        if (selectedTaskType == 0) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    DifficultyOption(
                        title = "Easy",
                        subtitle = "Addition, 2 numbers, 1–20",
                        level = 1,
                        selected = selectedLevel == 1,
                        onClick = { selectedLevel = 1 }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DifficultyOption(
                        title = "Medium",
                        subtitle = "Addition & subtraction, 2 numbers, 1–50",
                        level = 2,
                        selected = selectedLevel == 2,
                        onClick = { selectedLevel = 2 }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DifficultyOption(
                        title = "Hard",
                        subtitle = "Addition & subtraction, 3 numbers, 1–100",
                        level = 3,
                        selected = selectedLevel == 3,
                        onClick = { selectedLevel = 3 }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CustomDifficultyOption(
                        selected = selectedLevel == 4,
                        onClick = { selectedLevel = 4 },
                        operands = customOperands,
                        onOperandsChange = { customOperands = it },
                        addChecked = customAdd,
                        onAddChange = { customAdd = it },
                        subChecked = customSub,
                        onSubChange = { customSub = it },
                        mulChecked = customMul,
                        onMulChange = { customMul = it },
                        divChecked = customDiv,
                        onDivChange = { customDiv = it },
                        rangeMin = customMin,
                        onRangeMinChange = { customMin = it },
                        rangeMax = customMax,
                        onRangeMaxChange = { customMax = it }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── TYPING SETTINGS ──
        if (selectedTaskType == 1) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    DifficultyOption(
                        title = "Easy",
                        subtitle = "Numbers only, length 5",
                        level = 0,
                        selected = typingDifficulty == 0,
                        onClick = { typingDifficulty = 0 }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DifficultyOption(
                        title = "Medium",
                        subtitle = "Letters only, length 8",
                        level = 1,
                        selected = typingDifficulty == 1,
                        onClick = { typingDifficulty = 1 }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DifficultyOption(
                        title = "Hard",
                        subtitle = "Letters, numbers & special, length 10",
                        level = 2,
                        selected = typingDifficulty == 2,
                        onClick = { typingDifficulty = 2 }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CustomTypingOption(
                        selected = typingDifficulty == 3,
                        onClick = { typingDifficulty = 3 },
                        charSet = typingCharSet,
                        onCharSetChange = { typingCharSet = it },
                        length = typingLength,
                        onLengthChange = { typingLength = it }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── TAPPING SETTINGS ──
        if (selectedTaskType == 3) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    DifficultyOption(
                        title = "Easy",
                        subtitle = "4 dots, 1 second delay",
                        level = 0,
                        selected = tappingDifficulty == 0,
                        onClick = { tappingDifficulty = 0 }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DifficultyOption(
                        title = "Medium",
                        subtitle = "6 dots, 1 second delay",
                        level = 1,
                        selected = tappingDifficulty == 1,
                        onClick = { tappingDifficulty = 1 }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DifficultyOption(
                        title = "Hard",
                        subtitle = "10 dots, 2 seconds delay",
                        level = 2,
                        selected = tappingDifficulty == 2,
                        onClick = { tappingDifficulty = 2 }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CustomTappingOption(
                        selected = tappingDifficulty == 3,
                        onClick = { tappingDifficulty = 3 },
                        dotCount = tappingDotCount,
                        onDotCountChange = { tappingDotCount = it },
                        dotDelay = tappingDotDelay,
                        onDotDelayChange = { tappingDotDelay = it }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(12.dp))

        // ── TASK DELAY ──
        val presetDelays = listOf(0, 5, 10, 15)
        val repeatIncrementOptions = listOf(0, 5, 10, 15)
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = "Task Delay",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = " — $revealSeconds ${if (revealSeconds == 1) "second" else "seconds"}${if (repeatIncrement > 0) ", plus $repeatIncrement per session" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Initial delay",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            presetDelays.forEachIndexed { index, secs ->
                SegmentedButton(
                    selected = revealSeconds == secs,
                    onClick = { revealSeconds = secs },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = presetDelays.size),
                    colors = selectedColors,
                    modifier = Modifier.weight(1f)
                ) { Text("$secs") }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Per additional session",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            repeatIncrementOptions.forEachIndexed { index, secs ->
                SegmentedButton(
                    selected = repeatIncrement == secs,
                    onClick = { repeatIncrement = secs },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = repeatIncrementOptions.size),
                    colors = selectedColors,
                    modifier = Modifier.weight(1f)
                ) { Text("$secs") }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // ── PREVIEW DIALOG ──
        if (showPreview) {
            val previewName = appName ?: "Default"
            val previewCustomConfig = if (selectedLevel == 4) {
                CustomConfig(
                    operands = customOperands,
                    operations = buildSet {
                        if (customAdd) add("+")
                        if (customSub) add("-")
                        if (customMul) add("*")
                        if (customDiv) add("/")
                    },
                    rangeMin = customMin,
                    rangeMax = customMax
                )
            } else null
            Dialog(
                onDismissRequest = { showPreview = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (selectedTaskType) {
                        0 -> MathChallengeScreen(
                            appName = previewName,
                            packageName = packageName,
                            difficulty = selectedLevel,
                            revealSeconds = revealSeconds,
                            customConfig = previewCustomConfig,
                            onSuccess = { showPreview = false },
                            onCancel = { showPreview = false }
                        )
                        3 -> TappingTaskScreen(
                            appName = previewName,
                            packageName = packageName,
                            dotCount = tappingDotCount,
                            dotDelaySeconds = tappingDotDelay,
                            revealSeconds = revealSeconds,
                            onSuccess = { showPreview = false },
                            onCancel = { showPreview = false }
                        )
                        else -> TypingTaskScreen(
                            appName = previewName,
                            packageName = packageName,
                            charSet = typingCharSet,
                            length = typingLength,
                            revealSeconds = revealSeconds,
                            onSuccess = { showPreview = false },
                            onCancel = { showPreview = false }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(16.dp))



                // ── FLOATING TIMER ──
                Text(
                    text = "Floating timer",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = overlaySize == PrefsKeys.OVERLAY_SIZE_OFF,
                        onClick = { overlaySize = PrefsKeys.OVERLAY_SIZE_OFF },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        colors = selectedColors
                    ) { Text("Off") }
                    SegmentedButton(
                        selected = overlaySize == PrefsKeys.OVERLAY_SIZE_SMALL,
                        onClick = { overlaySize = PrefsKeys.OVERLAY_SIZE_SMALL },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        colors = selectedColors
                    ) { Text("Small") }
                    SegmentedButton(
                        selected = overlaySize == PrefsKeys.OVERLAY_SIZE_LARGE,
                        onClick = { overlaySize = PrefsKeys.OVERLAY_SIZE_LARGE },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        colors = selectedColors
                    ) { Text("Large") }
                }

                Spacer(modifier = Modifier.height(16.dp))
        } // end Column

        Spacer(modifier = Modifier.height(24.dp))

        } // end scrollable column
    } // end outer column
    if (!enabled) {
        // Invisible overlay that intercepts all touches when locked
        Box(modifier = Modifier.matchParentSize().clickable(
            indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        ) {})
    }
    } // end Box
}

/**
 * Reusable card with selected/unselected styling for settings options.
 */
@Composable
fun SettingsCard(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandableTypingOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    expandedContent: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) { expanded = selected }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = { if (selected) expanded = !expanded else onClick() })
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.clickable { if (selected) expanded = !expanded else onClick() }) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (expanded) {
            Column(modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                expandedContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDifficultyOption(
    selected: Boolean,
    onClick: () -> Unit,
    operands: Int,
    onOperandsChange: (Int) -> Unit,
    addChecked: Boolean,
    onAddChange: (Boolean) -> Unit,
    subChecked: Boolean,
    onSubChange: (Boolean) -> Unit,
    mulChecked: Boolean,
    onMulChange: (Boolean) -> Unit,
    divChecked: Boolean,
    onDivChange: (Boolean) -> Unit,
    rangeMin: Int,
    onRangeMinChange: (Int) -> Unit,
    rangeMax: Int,
    onRangeMaxChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) { expanded = selected }

    fun isLastChecked(vararg others: Boolean) = others.none { it }

    val rangePresets = listOf(
        1 to 20,
        1 to 50,
        1 to 100
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = { if (selected) expanded = !expanded else onClick() })
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier
                .weight(1f)
                .clickable { if (selected) expanded = !expanded else onClick() }
            ) {
                Text("Custom", style = MaterialTheme.typography.titleMedium)
                Text("Configure the difficulty", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (expanded) {
            val selectedColors = SegmentedButtonDefaults.colors(
                activeContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            )
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text("How many numbers?", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    for ((index, n) in (2..5).withIndex()) {
                        SegmentedButton(
                            selected = operands == n,
                            onClick = { onOperandsChange(n) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 4),
                            colors = selectedColors
                        ) {
                            Text("$n")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Number range", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    rangePresets.forEachIndexed { index, (lo, hi) ->
                        SegmentedButton(
                            selected = rangeMin == lo && rangeMax == hi,
                            onClick = {
                                onRangeMinChange(lo)
                                onRangeMaxChange(hi)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = rangePresets.size),
                            colors = selectedColors
                        ) { Text("$lo–$hi") }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Operations", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = addChecked, onCheckedChange = { if (addChecked && isLastChecked(subChecked, mulChecked, divChecked)) return@Checkbox; onAddChange(it) })
                    Text("Add (+)", modifier = Modifier.weight(1f))
                    Checkbox(checked = subChecked, onCheckedChange = { if (subChecked && isLastChecked(addChecked, mulChecked, divChecked)) return@Checkbox; onSubChange(it) })
                    Text("Subtract (−)", modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = mulChecked, onCheckedChange = { if (mulChecked && isLastChecked(addChecked, subChecked, divChecked)) return@Checkbox; onMulChange(it) })
                    Text("Multiply (×)", modifier = Modifier.weight(1f))
                    Checkbox(checked = divChecked, onCheckedChange = { if (divChecked && isLastChecked(addChecked, subChecked, mulChecked)) return@Checkbox; onDivChange(it) })
                    Text("Divide (÷)", modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DifficultyOption(
    title: String,
    subtitle: String = "",
    level: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TypingCharSetOption(
    title: String,
    subtitle: String = "",
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTypingOption(
    selected: Boolean,
    onClick: () -> Unit,
    charSet: Int,
    onCharSetChange: (Int) -> Unit,
    length: Int,
    onLengthChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) { expanded = selected }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = { if (selected) expanded = !expanded else onClick() })
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier
                .weight(1f)
                .clickable { if (selected) expanded = !expanded else onClick() }
            ) {
                Text("Custom", style = MaterialTheme.typography.titleMedium)
                Text("Configure the difficulty", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (expanded) {
            val selectedColors = SegmentedButtonDefaults.colors(
                activeContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            )
            Column(modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                Text("Character set", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                TypingCharSetOption(
                    title = "Numbers only",
                    subtitle = "0–9",
                    selected = charSet == 3,
                    onClick = { onCharSetChange(3) }
                )
                TypingCharSetOption(
                    title = "Letters only",
                    subtitle = "a–z",
                    selected = charSet == 0,
                    onClick = { onCharSetChange(0) }
                )
                TypingCharSetOption(
                    title = "Letters and numbers",
                    subtitle = "a–z, 0–9",
                    selected = charSet == 1,
                    onClick = { onCharSetChange(1) }
                )
                TypingCharSetOption(
                    title = "Letters, numbers, and special",
                    subtitle = "a–z, 0–9, !@#%&*",
                    selected = charSet == 2,
                    onClick = { onCharSetChange(2) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Length", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(5, 7, 10, 15, 20).forEachIndexed { index, len ->
                        SegmentedButton(
                            selected = length == len,
                            onClick = { onLengthChange(len) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 5),
                            colors = selectedColors
                        ) { Text("$len") }
                    }
                }
            }
        }
    }
}

@Composable
fun SecondPickerDialog(
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val steps = (4..12).map { it * 5 }
    var selected by remember { mutableStateOf((initial / 5 * 5).coerceIn(20, 60)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom delay seconds") },
        text = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        android.widget.NumberPicker(ctx).apply {
                            minValue = 1
                            maxValue = 9
                            displayedValues = steps.map { "$it" }.toTypedArray()
                            value = ((selected - 20) / 5 + 1).coerceIn(1, 9)
                            wrapSelectorWheel = false
                            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            setOnValueChangedListener { _, _, newVal -> selected = steps[newVal - 1] }
                        }
                    },
                    modifier = Modifier.height(150.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTappingOption(
    selected: Boolean,
    onClick: () -> Unit,
    dotCount: Int,
    onDotCountChange: (Int) -> Unit,
    dotDelay: Int,
    onDotDelayChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) { expanded = selected }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = { if (selected) expanded = !expanded else onClick() })
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier
                .weight(1f)
                .clickable { if (selected) expanded = !expanded else onClick() }
            ) {
                Text("Custom", style = MaterialTheme.typography.titleMedium)
                Text("Configure the difficulty", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (expanded) {
            val selectedColors = SegmentedButtonDefaults.colors(
                activeContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            )
            Column(modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                Text("Number of dots", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(2, 4, 6, 8, 10).forEachIndexed { index, count ->
                        SegmentedButton(
                            selected = dotCount == count,
                            onClick = { onDotCountChange(count) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 5),
                            colors = selectedColors
                        ) { Text("$count") }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Dot delay — $dotDelay ${if (dotDelay == 1) "second" else "seconds"}", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(0, 1, 2, 3, 4, 5).forEachIndexed { index, secs ->
                        SegmentedButton(
                            selected = dotDelay == secs,
                            onClick = { onDotDelayChange(secs) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 6),
                            colors = selectedColors
                        ) { Text("$secs") }
                    }
                }
            }
        }
    }
}
