package com.pleaseinconvenienceme.pim

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlinx.coroutines.delay

private const val POST_REVEAL_DELAY_MS = 550L
private const val KEYPAD_BUTTON_WIDTH = 96
private const val KEYPAD_BUTTON_HEIGHT = 60
private const val KEYPAD_WIDTH = 320
private const val CONTENT_WIDTH = 260
private const val DOT_SIZE = 14

class TaskActivity : ComponentActivity() {
    companion object {
        val isActive = AtomicBoolean(false)
    }

    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onStart() {
        super.onStart()
        isActive.set(true)
        requestAudioFocus()
    }

    override fun onStop() {
        isActive.set(false)
        abandonAudioFocus()
        super.onStop()
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .build()
            )
            .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.abandonAudioFocusRequest(it)
        }
        audioFocusRequest = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName = intent.getStringExtra("APP_NAME") ?: "Unknown App"
        val packageName = intent.getStringExtra("PACKAGE_NAME") ?: ""
        // True when the session expired while the user was still in the app (back-to-back),
        // which switches the header to the reflective "Still need …?" wording.
        val isContinuation = intent.getBooleanExtra("IS_CONTINUATION", false)

        val resolver = AppSettingsResolver(this, appName)
        val difficulty = resolver.getInt(PrefsKeys.DIFFICULTY_LEVEL, PrefsKeys.DEFAULT_DIFFICULTY)

        val customConfig = if (difficulty == 4) {
            CustomConfig(
                operands = resolver.getInt(PrefsKeys.CUSTOM_OPERANDS, PrefsKeys.DEFAULT_OPERANDS),
                operations = resolver.getStringSet(PrefsKeys.CUSTOM_OPERATIONS, setOf("+")) ?: setOf("+"),
                rangeMin = resolver.getInt(PrefsKeys.CUSTOM_RANGE_MIN, PrefsKeys.DEFAULT_RANGE_MIN),
                rangeMax = resolver.getInt(PrefsKeys.CUSTOM_RANGE_MAX, PrefsKeys.DEFAULT_RANGE_MAX)
            )
        } else null

        val baseRevealSeconds = resolver.getInt(PrefsKeys.REVEAL_SECONDS, PrefsKeys.DEFAULT_REVEAL_SECONDS)
        val repeatIncrement = resolver.getInt(PrefsKeys.REPEAT_DELAY_INCREMENT, PrefsKeys.DEFAULT_REPEAT_DELAY_INCREMENT)
        // Read-only: the delay/label reflect completed sessions so far. The counter is only
        // advanced on success (recordCompletedSession below), so cancels don't escalate.
        val sessionCount = if (repeatIncrement > 0) peekSessionNumber(this, appName) else 1
        val revealSeconds = baseRevealSeconds + (sessionCount - 1) * repeatIncrement
        val taskType = resolver.getInt(PrefsKeys.TASK_TYPE, PrefsKeys.DEFAULT_TASK_TYPE)
        val typingCharSet = resolver.getInt(PrefsKeys.TYPING_CHAR_SET, PrefsKeys.DEFAULT_TYPING_CHAR_SET)
        val typingLength = resolver.getInt(PrefsKeys.TYPING_LENGTH, PrefsKeys.DEFAULT_TYPING_LENGTH)
        val tappingDotCount = resolver.getInt(PrefsKeys.TAPPING_DOT_COUNT, PrefsKeys.DEFAULT_TAPPING_DOTS)
        val tappingDotDelay = resolver.getInt(PrefsKeys.TAPPING_DOT_DELAY, PrefsKeys.DEFAULT_TAPPING_DOT_DELAY)

        val globalPrefs = getSharedPreferences(PrefsKeys.SETTINGS, Context.MODE_PRIVATE)
        val showNudge = !globalPrefs.getBoolean(PrefsKeys.TASK_NUDGE_SHOWN, false)

        val reviewHelper = ReviewHelperImpl()

        setContent {
            PimTheme {
                var showNudgeCard by remember { mutableStateOf(false) }
                var nudgeTriggered by remember { mutableStateOf(false) }
                var taskDone by remember { mutableStateOf(false) }
                var showReviewDialog by remember { mutableStateOf(false) }

                // Trial card: once per day, but only on days 2, 4, 6, and 7. Day 1 is
                // deliberately pitch-free — a purchase ask on the very first challenge is
                // jarring and its "Is PIM helping you?" question isn't answerable yet.
                // Alternating quiet days (3, 5) keep it from feeling like a daily toll; the
                // back-to-back 6+7 is the closing window alongside the home-screen countdown
                // banner, and the expiry lockout handles the hard ask after day 7.
                val showTrialCard = remember {
                    if (!BuildConfig.ENFORCE_LIMIT) false
                    else {
                        val trialState = TrialHelper.getTrialState(this@TaskActivity)
                        if (trialState != TrialState.IN_TRIAL) false
                        else if (TrialHelper.isPurchased(this@TaskActivity)) false
                        else if (TrialHelper.isLegacyUser(this@TaskActivity)) false
                        else {
                            val trialDay = TrialHelper.getTrialDay(this@TaskActivity)
                            if (trialDay !in setOf(2, 4, 6, 7)) false
                            else {
                                val today = java.time.LocalDate.now().toString()
                                val lastShown = globalPrefs.getString(PrefsKeys.TRIAL_CARD_LAST_SHOWN, "")
                                if (lastShown == today) false
                                else {
                                    globalPrefs.edit().putString(PrefsKeys.TRIAL_CARD_LAST_SHOWN, today).apply()
                                    true
                                }
                            }
                        }
                    }
                }
                var trialCardVisible by remember { mutableStateOf(showTrialCard) }

                val finishCancel = {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                    finish()
                }

                val finishSuccess = {
                    if (showNudge) {
                        globalPrefs.edit().putBoolean(PrefsKeys.TASK_NUDGE_SHOWN, true).apply()
                        nudgeTriggered = true
                    } else {
                        taskDone = true
                    }
                }

                val handleCancel: () -> Unit = {
                    recordInteraction(this@TaskActivity)
                    if (shouldPromptReview(this@TaskActivity)) {
                        showReviewDialog = true
                    } else {
                        finishCancel()
                    }
                }

                LaunchedEffect(nudgeTriggered) {
                    if (nudgeTriggered) {
                        showNudgeCard = true
                        delay(3000L)
                        showNudgeCard = false
                        delay(400L)
                        if (repeatIncrement > 0) recordCompletedSession(this@TaskActivity, appName)
                        grantAppSession(this@TaskActivity, appName)
                        finish()
                    }
                }
                LaunchedEffect(taskDone) {
                    if (taskDone) {
                        if (repeatIncrement > 0) recordCompletedSession(this@TaskActivity, appName)
                        grantAppSession(this@TaskActivity, appName)
                        finish()
                    }
                }
                val handleSuccess: () -> Unit = {
                    recordInteraction(this@TaskActivity)
                    finishSuccess()
                }

                if (showReviewDialog) {
                    ReviewPromptDialog(
                        onSure = {
                            showReviewDialog = false
                            markReviewNeverAsk(this@TaskActivity)
                            reviewHelper.launchReview(this@TaskActivity)
                            finishCancel()
                        },
                        onMaybeLater = {
                            showReviewDialog = false
                            markReviewMaybeLater(this@TaskActivity)
                            finishCancel()
                        },
                        onNeverAsk = {
                            showReviewDialog = false
                            markReviewNeverAsk(this@TaskActivity)
                            finishCancel()
                        },
                        onDismiss = {
                            showReviewDialog = false
                            markReviewMaybeLater(this@TaskActivity)
                            finishCancel()
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (taskType) {
                            0 -> MathChallengeScreen(
                                appName = appName,
                                packageName = packageName,
                                difficulty = difficulty,
                                revealSeconds = revealSeconds,
                                sessionCount = sessionCount,
                                customConfig = customConfig,
                                isContinuation = isContinuation,

                                onSuccess = handleSuccess,
                                onCancel = handleCancel
                            )
                            3 -> TappingTaskScreen(
                                appName = appName,
                                packageName = packageName,
                                dotCount = tappingDotCount,
                                dotDelaySeconds = tappingDotDelay,
                                revealSeconds = revealSeconds,
                                sessionCount = sessionCount,
                                isContinuation = isContinuation,

                                onSuccess = handleSuccess,
                                onCancel = handleCancel
                            )
                            else -> TypingTaskScreen(
                                appName = appName,
                                packageName = packageName,
                                charSet = typingCharSet,
                                length = typingLength,
                                revealSeconds = revealSeconds,
                                sessionCount = sessionCount,
                                isContinuation = isContinuation,

                                onSuccess = handleSuccess,
                                onCancel = handleCancel
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = showNudgeCard,
                        enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(400)
                        ),
                        exit = fadeOut(animationSpec = tween(400)),
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    ) {
                        Card(elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                            Text(
                                text = "Too easy? Customize in PIM settings.",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }

                    // Trial card overlay — once per day during trial
                    if (trialCardVisible) {
                        val trialDay = remember { TrialHelper.getTrialDay(this@TaskActivity) }
                        val cachedPrice = remember { globalPrefs.getString("cached_price", null) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { trialCardVisible = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 6.dp,
                                shadowElevation = 12.dp,
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { /* consume clicks on card */ }
                            ) {
                                Column {
                                    // Blue header
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                "PIM FREE TRIAL",
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = BrandFont,
                                                letterSpacing = 2.sp,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "Day $trialDay of 7",
                                                fontSize = 34.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = BrandFont,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    // Card body
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Is PIM helping you\navoid distraction?",
                                            fontSize = 20.sp,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                                val bullets = listOf(
                                                    "One-time purchase, not a subscription",
                                                    "No ads, no tracking",
                                                    "Restrict as many apps as you want",
                                                    "Support continued improvement"
                                                )
                                                bullets.forEach { bullet ->
                                                    Row(verticalAlignment = Alignment.Top) {
                                                        Text(
                                                            "\u2713",
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(end = 8.dp)
                                                        )
                                                        Text(bullet, style = MaterialTheme.typography.bodyMedium)
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Button(
                                            onClick = {
                                                trialCardVisible = false
                                                startActivity(Intent(this@TaskActivity, MainActivity::class.java).apply {
                                                    putExtra("open_upgrade", true)
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                })
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                if (cachedPrice != null) "Buy \u2014 $cachedPrice" else "Buy",
                                                fontSize = 17.sp,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedButton(
                                            onClick = { trialCardVisible = false },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                "Not now",
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

data class MathProblem(
    val display: String,
    val answer: Int
)

data class CustomConfig(
    val operands: Int,
    val operations: Set<String>,
    val rangeMin: Int,
    val rangeMax: Int
)

private fun isHighPrecedence(op: String) = op == "*" || op == "/"

private fun opSymbol(op: String) = when (op) {
    "+" -> "+"; "-" -> "-"; "*" -> "×"; "/" -> "÷"; else -> "+"
}

fun generateProblem(difficulty: Int, customConfig: CustomConfig? = null): MathProblem {
    return when (difficulty) {
        1 -> generateEasyProblem()
        2 -> generateMediumProblem()
        3 -> generateHardProblem()
        4 -> generateCustomProblem(customConfig)
        else -> generateEasyProblem()
    }
}

private fun generateEasyProblem(): MathProblem {
    val a = Random.nextInt(2, 21)
    val b = Random.nextInt(2, 21)
    return MathProblem("$a + $b = ?", a + b)
}

private fun generateMediumProblem(): MathProblem {
    val a = Random.nextInt(2, 51)
    val b = Random.nextInt(2, 51)
    val op = if (Random.nextBoolean()) "+" else "-"
    val result = if (op == "+") a + b else a - b
    return MathProblem("$a $op $b = ?", result)
}

private fun generateHardProblem(): MathProblem {
    val a = Random.nextInt(2, 101)
    val b = Random.nextInt(2, 101)
    val c = Random.nextInt(2, 101)
    val op1 = if (Random.nextBoolean()) "+" else "-"
    val op2 = if (Random.nextBoolean()) "+" else "-"
    val result = (if (op1 == "+") a + b else a - b).let {
        if (op2 == "+") it + c else it - c
    }
    return MathProblem("$a $op1 $b $op2 $c = ?", result)
}

private fun generateCustomProblem(customConfig: CustomConfig?): MathProblem {
    val cfg = customConfig ?: CustomConfig(2, setOf("+"), 0, 50)
    val ops = cfg.operations.toList()
    val min = cfg.rangeMin
    val max = if (cfg.rangeMax > cfg.rangeMin) cfg.rangeMax + 1 else cfg.rangeMin + 2

    val numbers = mutableListOf<Int>()
    val operators = mutableListOf<String>()

    numbers.add(randomExcludingTrivial(min, max))
    for (i in 1 until cfg.operands) {
        val op = ops[Random.nextInt(ops.size)]
        operators.add(op)

        if (op == "/") {
            numbers.add(generateCleanDivisor(numbers, operators, min, max))
        } else if (op == "*") {
            val num = randomExcludingTrivial(min, max)
            if (wouldOverflow(numbers, operators, num)) {
                val fallback = ops.firstOrNull { it != "*" } ?: "+"
                operators[operators.lastIndex] = fallback
            }
            numbers.add(num)
        } else {
            numbers.add(randomExcludingTrivial(min, max))
        }
    }

    val result = evaluatePEMDAS(numbers, operators)
    val display = buildMathDisplay(numbers, operators)
    return MathProblem(display, result)
}

private fun randomExcludingTrivial(min: Int, max: Int): Int {
    val trivial = setOf(-1, 0, 1)
    var num = Random.nextInt(min, max)
    if (max - min > 3) {
        while (num in trivial) num = Random.nextInt(min, max)
    }
    return num
}

private fun generateCleanDivisor(
    numbers: MutableList<Int>,
    operators: MutableList<String>,
    min: Int,
    max: Int
): Int {
    val groupVal = evaluateCurrentMulDivGroup(numbers, operators)
    if (groupVal != 0) {
        val absVal = kotlin.math.abs(groupVal)
        val divisors = (1..kotlin.math.min(absVal, kotlin.math.abs(max - 1).coerceAtLeast(1)))
            .filter { it in min until max && it != 0 && it != 1 && it != absVal && groupVal % it == 0 }
        if (divisors.isNotEmpty()) {
            return divisors[Random.nextInt(divisors.size)]
        }
    }
    operators[operators.lastIndex] = "+"
    return randomExcludingTrivial(min, max)
}

private fun wouldOverflow(
    numbers: MutableList<Int>,
    operators: MutableList<String>,
    newNum: Int
): Boolean {
    val groupVal = evaluateCurrentMulDivGroup(numbers, operators)
    val newResult = groupVal * newNum
    return newResult > 9999 || newResult < -9999
}

private fun evaluateCurrentMulDivGroup(
    numbers: List<Int>,
    operators: List<String>
): Int {
    var g = operators.size - 2
    val groupNums = mutableListOf(numbers.last())
    val groupOps = mutableListOf<String>()
    while (g >= 0 && isHighPrecedence(operators[g])) {
        groupNums.add(0, numbers[g])
        groupOps.add(0, operators[g])
        g--
    }
    var value = groupNums[0]
    for (j in groupOps.indices) {
        value = when (groupOps[j]) {
            "*" -> value * groupNums[j + 1]
            "/" -> if (groupNums[j + 1] != 0) value / groupNums[j + 1] else value
            else -> value
        }
    }
    return value
}

private fun evaluatePEMDAS(numbers: List<Int>, operators: List<String>): Int {
    val reducedNumbers = mutableListOf<Int>()
    val reducedOps = mutableListOf<String>()
    var idx = 0
    while (idx < numbers.size) {
        var value = numbers[idx]
        while (idx < operators.size && isHighPrecedence(operators[idx])) {
            value = when (operators[idx]) {
                "*" -> value * numbers[idx + 1]
                "/" -> if (numbers[idx + 1] != 0) value / numbers[idx + 1] else value
                else -> value
            }
            idx++
        }
        reducedNumbers.add(value)
        if (idx < operators.size) {
            reducedOps.add(operators[idx])
            idx++
        } else {
            break
        }
    }

    var result = reducedNumbers[0]
    for (j in reducedOps.indices) {
        result = when (reducedOps[j]) {
            "+" -> result + reducedNumbers[j + 1]
            "-" -> result - reducedNumbers[j + 1]
            else -> result
        }
    }
    return result
}

private fun buildMathDisplay(numbers: List<Int>, operators: List<String>): String {
    val hasAddSub = operators.any { it == "+" || it == "-" }
    val hasMulDiv = operators.any { it == "*" || it == "/" }
    val mixedPrec = hasAddSub && hasMulDiv

    return buildString {
        var i = 0
        while (i <= operators.size) {
            val isStart = i == 0 || !isHighPrecedence(operators[i - 1])
            val startsGroup = mixedPrec && i < operators.size && isHighPrecedence(operators[i])
            val endsGroup = mixedPrec && i > 0 && isHighPrecedence(operators[i - 1]) &&
                (i >= operators.size || !isHighPrecedence(operators[i]))

            if (startsGroup && isStart) append("(")
            append(numbers[i])
            if (endsGroup) append(")")

            if (i < operators.size) {
                append(" ${opSymbol(operators[i])} ")
            }
            i++
        }
        append(" = ?")
    }
}

@Composable
fun CustomNumberKeypad(
    onNumberClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onNegateClick: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    submitEnabled: Boolean,
    showNegate: Boolean,
    submitScale: Float = 1f
) {
    val buttonSize = Modifier.size(KEYPAD_BUTTON_WIDTH.dp, KEYPAD_BUTTON_HEIGHT.dp)

    Column(
        modifier = Modifier.width(KEYPAD_WIDTH.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1: 1 2 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NumberButton("1", onNumberClick, buttonSize)
            NumberButton("2", onNumberClick, buttonSize)
            NumberButton("3", onNumberClick, buttonSize)
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Row 2: 4 5 6
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NumberButton("4", onNumberClick, buttonSize)
            NumberButton("5", onNumberClick, buttonSize)
            NumberButton("6", onNumberClick, buttonSize)
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Row 3: 7 8 9
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NumberButton("7", onNumberClick, buttonSize)
            NumberButton("8", onNumberClick, buttonSize)
            NumberButton("9", onNumberClick, buttonSize)
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Row 4: +/- 0 Backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (showNegate) {
                Button(
                    onClick = onNegateClick,
                    modifier = buttonSize
                ) {
                    Text("+/−", fontSize = 20.sp)
                }
            } else {
                Spacer(modifier = buttonSize)
            }
            NumberButton("0", onNumberClick, buttonSize)
            Button(
                onClick = onBackspace,
                modifier = buttonSize
            ) {
                Text("⌫", fontSize = 24.sp)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Row 5: OK button full width
        Button(
            onClick = onSubmit,
            enabled = submitEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleX = submitScale, scaleY = submitScale),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("OK", fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cancel button
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

@Composable
fun NumberButton(number: String, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = { onClick(number) },
        modifier = modifier
    ) {
        Text(number, fontSize = 24.sp)
    }
}

@Composable
fun CountdownRing(totalSeconds: Int, secondsRemaining: Int) {
    val animatedProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    val pulseScale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) {
        animatedProgress.animateTo(
            1f,
            animationSpec = tween(durationMillis = totalSeconds * 1000, easing = androidx.compose.animation.core.LinearEasing)
        )
        pulseScale.animateTo(1.02f, animationSpec = tween(200))
        pulseScale.animateTo(1f, animationSpec = tween(300))
    }
    val timeText = "$secondsRemaining"
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(160.dp)
            .graphicsLayer(scaleX = pulseScale.value, scaleY = pulseScale.value),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress.value,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
        Text(
            text = timeText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PimTopBar(appName: String, isContinuation: Boolean = false) {
    val context = LocalContext.current
    val sessionMinutes = remember(appName) {
        context.getSharedPreferences(PrefsKeys.DURATIONS, Context.MODE_PRIVATE)
            .getInt(appName, PrefsKeys.DEFAULT_SESSION_MINUTES)
    }
    val usage7Days = remember(appName) { get7DayMinutes(context, appName) }
    val extraHeight = if (usage7Days > 0) 16.dp else 0.dp

    // Battery-exemption check (re-checked on resume so the strip clears right after a fix).
    // Heavy users see challenge screens far more often than PIM's own home screen, so this
    // is the warning surface that actually reaches them when enforcement can lapse.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var batteryExempt by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                batteryExempt = pm.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column {
        if (!batteryExempt) {
            val darkWarn = androidx.compose.foundation.isSystemInDarkTheme()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (darkWarn) Color(0xFF3A3323) else Color(0xFFFAF3DC))
                    .clickable {
                        context.startActivity(Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:${context.packageName}")
                        ))
                    }
                    // Padding after background: the tint extends up behind the status bar.
                    .statusBarsPadding()
            ) {
                // Wording mirrors the home-screen warning card so both surfaces speak
                // with one voice; "Tap to fix." is the strip's own action cue.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 8.dp)
                ) {
                    Text(
                        "PIM battery set to “Optimized”",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = if (darkWarn) Color(0xFFE8D9A0) else Color(0xFF6A5619)
                    )
                    Text(
                        "Restrictions may fail if PIM can't run in the background.",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = if (darkWarn) Color(0xFFBFB183) else Color(0xFF857337)
                    )
                    Text(
                        "Tap to fix.",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = if (darkWarn) Color(0xFFE8D9A0) else Color(0xFF6A5619),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        TopAppBar(
        expandedHeight = 96.dp + extraHeight,
        // The strip above already consumed the status-bar inset when visible.
        windowInsets = if (!batteryExempt) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
        title = {
            Column {
                Text(
                    // Back-to-back re-challenge (session expired while still in the app) gets a
                    // reflective header; a first open or a return after leaving keeps the neutral one.
                    if (isContinuation) "Still need $appName?" else "You restricted $appName",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "$sessionMinutes min sessions",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                )
                if (usage7Days > 0) {
                    Text(
                        "Last 7 days: ${formatUsageTime(usage7Days)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                    )
                }
            }
        },
        actions = {
            val context = LocalContext.current
            val pimIcon = remember {
                try { context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap() }
                catch (e: Exception) { null }
            }
            if (pimIcon != null) {
                Image(
                    bitmap = pimIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .alpha(0.75f)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary
        )
        )
    }
}

private fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

@Composable
fun DelayPhase(
    paddingValues: PaddingValues,
    revealSeconds: Int,
    dotsRemaining: Int,
    sessionCount: Int = 1,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Seconds until task…",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (sessionCount >= 2) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "(${ordinal(sessionCount)} session in 60 min)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        CountdownRing(totalSeconds = revealSeconds, secondsRemaining = dotsRemaining)
        Spacer(modifier = Modifier.height(48.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.width(CONTENT_WIDTH.dp)
        ) {
            Text("Cancel")
        }
    }
}

@Composable
fun TypingTaskScreen(
    appName: String,
    packageName: String = "",
    charSet: Int = PrefsKeys.DEFAULT_TYPING_CHAR_SET,
    length: Int = PrefsKeys.DEFAULT_TYPING_LENGTH,
    revealSeconds: Int = 0,
    sessionCount: Int = 1,
    isContinuation: Boolean = false,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val target = rememberSaveable {
        val letters = "abcdefghjkmnpqrstuvwxyz"
        val digits = "23456789"
        val specials = "!@#%&*"
        when (charSet) {
            1 -> {
                val all = letters + digits
                val chunkCount = (length + 4) / 5
                (0 until chunkCount).flatMap { i ->
                    val chunkLen = minOf(5, length - i * 5)
                    val required = listOf(letters.random(), digits.random()).take(chunkLen)
                    val extra = (required.size until chunkLen).map { all.random() }
                    (required + extra).shuffled()
                }.joinToString("")
            }
            2 -> {
                val all = letters + digits + specials
                val chunkCount = (length + 4) / 5
                (0 until chunkCount).flatMap { i ->
                    val chunkLen = minOf(5, length - i * 5)
                    val required = listOf(letters.random(), digits.random(), specials.random()).take(chunkLen)
                    val extra = (required.size until chunkLen).map { all.random() }
                    (required + extra).shuffled()
                }.joinToString("")
            }
            3 -> (1..length).map { "0123456789".random() }.joinToString("")
            else -> (1..length).map { letters.random() }.joinToString("")
        }
    }
    val displayTarget = target.chunked(5).joinToString(" ")

    var dotsRemaining by remember { mutableIntStateOf(revealSeconds) }
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (revealSeconds > 0) {
            repeat(revealSeconds) {
                delay(1000L)
                dotsRemaining--
            }
            delay(POST_REVEAL_DELAY_MS)
        }
        revealed = true
    }

    var userInput by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    var typingCompleted by remember { mutableStateOf(false) }
    val buttonPulse = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(typingCompleted) {
        if (typingCompleted) {
            buttonPulse.animateTo(1.05f, animationSpec = tween(150))
            buttonPulse.animateTo(1f, animationSpec = tween(200))
            onSuccess()
        }
    }

    Scaffold(topBar = { PimTopBar(appName, isContinuation) }) { paddingValues ->
        if (!revealed) {
            DelayPhase(
                paddingValues = paddingValues,
                revealSeconds = revealSeconds,
                dotsRemaining = dotsRemaining,
                sessionCount = sessionCount,
                onCancel = onCancel
            )
        }
        AnimatedVisibility(visible = revealed, enter = fadeIn(animationSpec = tween(2000))) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .padding(top = 48.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "Type this",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = displayTarget,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    lineHeight = 44.sp,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                @Suppress("DEPRECATION")
                OutlinedTextField(
                    value = userInput,
                    onValueChange = {
                        userInput = it.lowercase()
                        showError = false
                    },
                    label = { Text("Your answer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (charSet == 3) KeyboardType.Number else KeyboardType.Text,
                        autoCorrect = false,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.width(CONTENT_WIDTH.dp)
                )
                if (showError) {
                    Text(
                        text = "Wrong answer, try again!",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val clean = { s: String -> s.replace(" ", "").replace("-", "").trim().lowercase() }
                        if (clean(userInput) == clean(target)) {
                            typingCompleted = true
                        } else {
                            showError = true
                        }
                    },
                    enabled = userInput.isNotEmpty(),
                    modifier = Modifier
                        .width(CONTENT_WIDTH.dp)
                        .graphicsLayer(scaleX = buttonPulse.value, scaleY = buttonPulse.value),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("OK", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.width(CONTENT_WIDTH.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun MathChallengeScreen(
    appName: String,
    packageName: String = "",
    difficulty: Int,
    revealSeconds: Int = 0,
    sessionCount: Int = 1,
    customConfig: CustomConfig? = null,
    isContinuation: Boolean = false,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val initialProblem = remember { generateProblem(difficulty, customConfig) }
    var problemDisplay by rememberSaveable { mutableStateOf(initialProblem.display) }
    var problemAnswer by rememberSaveable { mutableStateOf(initialProblem.answer) }

    var userAnswer by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    var mathCompleted by remember { mutableStateOf(false) }
    val mathButtonPulse = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(mathCompleted) {
        if (mathCompleted) {
            mathButtonPulse.animateTo(1.05f, animationSpec = tween(150))
            mathButtonPulse.animateTo(1f, animationSpec = tween(200))
            onSuccess()
        }
    }

    var dotsRemaining by remember { mutableIntStateOf(revealSeconds) }
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (revealSeconds > 0) {
            repeat(revealSeconds) {
                delay(1000L)
                dotsRemaining--
            }
            delay(POST_REVEAL_DELAY_MS)
        }
        revealed = true
    }

    // Show negate button when answers could be negative
    val showNegate = difficulty >= 2

    Scaffold(topBar = { PimTopBar(appName, isContinuation) }) { paddingValues ->
        if (!revealed) {
            DelayPhase(
                paddingValues = paddingValues,
                revealSeconds = revealSeconds,
                dotsRemaining = dotsRemaining,
                sessionCount = sessionCount,
                onCancel = onCancel
            )
        }
        AnimatedVisibility(visible = revealed, enter = fadeIn(animationSpec = tween(2000))) {
            // Content area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp)
                    .padding(top = 48.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                    // Math problem — auto-scale to fit width
                    val baseFontSize = if (difficulty >= 3) 36f else 48f
                    var mathFontSize by remember { mutableFloatStateOf(baseFontSize) }
                    var readyToDraw by remember { mutableStateOf(false) }
                    Text(
                        text = problemDisplay,
                        fontSize = mathFontSize.sp,
                        maxLines = 1,
                        softWrap = false,
                        onTextLayout = { result ->
                            if (result.hasVisualOverflow && mathFontSize > 16f) {
                                mathFontSize *= 0.9f
                            } else {
                                readyToDraw = true
                            }
                        },
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .alpha(if (readyToDraw) 1f else 0f)
                    )

                    // Answer display with blinking cursor
                    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
                    val cursorAlpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "cursorBlink"
                    )

                    Card(
                        modifier = Modifier
                            .width(250.dp)
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayText = if (userAnswer.isEmpty()) "" else userAnswer
                            if (displayText.isNotEmpty()) {
                                Text(text = displayText, fontSize = 36.sp)
                            }
                            Text(
                                text = "|",
                                fontSize = 36.sp,
                                modifier = Modifier.alpha(cursorAlpha)
                            )
                        }
                    }

                    if (showError) {
                        Text(
                            text = "Wrong answer, try again!",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom number keypad
                    CustomNumberKeypad(
                        onNumberClick = { digit ->
                            if (userAnswer.replace("-", "").length < 4) {
                                userAnswer += digit
                                showError = false
                            }
                        },
                        onBackspace = {
                            if (userAnswer.isNotEmpty()) {
                                userAnswer = userAnswer.dropLast(1)
                                showError = false
                            }
                        },
                        onNegateClick = {
                            userAnswer = if (userAnswer.startsWith("-")) {
                                userAnswer.removePrefix("-")
                            } else {
                                "-$userAnswer"
                            }
                            showError = false
                        },
                        onSubmit = {
                            if (userAnswer.toIntOrNull() == problemAnswer) {
                                mathCompleted = true
                            } else {
                                showError = true
                            }
                        },
                        onCancel = onCancel,
                        submitEnabled = userAnswer.isNotEmpty() && userAnswer != "-",
                        submitScale = mathButtonPulse.value,
                        showNegate = showNegate
                    )
                }
            }
    }
}

@Composable
fun TappingTaskScreen(
    appName: String,
    packageName: String = "",
    dotCount: Int = PrefsKeys.DEFAULT_TAPPING_DOTS,
    dotDelaySeconds: Int = PrefsKeys.DEFAULT_TAPPING_DOT_DELAY,
    revealSeconds: Int = 0,
    sessionCount: Int = 1,
    isContinuation: Boolean = false,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    var dotsRemaining by remember { mutableIntStateOf(revealSeconds) }
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (revealSeconds > 0) {
            repeat(revealSeconds) { delay(1000L); dotsRemaining-- }
            delay(POST_REVEAL_DELAY_MS)
        }
        revealed = true
    }

    // Clock positions: 12, 3, 6, 9
    val clockPositions = listOf(
        Alignment.TopCenter,
        Alignment.CenterEnd,
        Alignment.BottomCenter,
        Alignment.CenterStart
    )

    var currentDot by remember { mutableIntStateOf(0) }
    var tappable by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var lastDotPosition by remember { mutableStateOf(Alignment.TopCenter as Alignment) }

    // Pulse animation on completion: scale up then back down over 500ms
    val pulseAnim = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(completed) {
        if (completed) {
            pulseAnim.animateTo(1.15f, animationSpec = tween(250))
            pulseAnim.animateTo(1f, animationSpec = tween(350))
            onSuccess()
        }
    }

    Scaffold(topBar = { PimTopBar(appName, isContinuation) }) { paddingValues ->
        if (!revealed) {
            DelayPhase(
                paddingValues = paddingValues,
                revealSeconds = revealSeconds,
                dotsRemaining = dotsRemaining,
                sessionCount = sessionCount,
                onCancel = onCancel
            )
        }
        AnimatedVisibility(visible = revealed, enter = fadeIn(animationSpec = tween(if (revealSeconds > 0) 2000 else 300))) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp)
                        .padding(top = 32.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(
                        text = "Tap the dot",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${currentDot} / $dotCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Clock face
                    Box(
                        modifier = Modifier.size(280.dp)
                    ) {
                        if (completed) {
                            // Show pulsing last dot
                            val dotColor = MaterialTheme.colorScheme.primary
                            val dotSize = 42f
                            Box(
                                modifier = Modifier
                                    .align(lastDotPosition)
                                    .size(64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Canvas(
                                    modifier = Modifier.size((dotSize * pulseAnim.value).dp)
                                ) {
                                    drawArc(
                                        color = dotColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = true
                                    )
                                }
                            }
                        } else if (currentDot < dotCount) {
                            val position = clockPositions[currentDot % 4]
                            key(currentDot) {
                                val sweep = remember { androidx.compose.animation.core.Animatable(0f) }
                                LaunchedEffect(Unit) {
                                    sweep.animateTo(360f, animationSpec = tween(durationMillis = dotDelaySeconds * 1000))
                                    tappable = true
                                }
                                val dotColor by animateColorAsState(
                                    targetValue = if (sweep.value >= 360f) MaterialTheme.colorScheme.primary else Color(0xFFAAAAAA),
                                    animationSpec = tween(durationMillis = 250),
                                    label = "dotColor"
                                )
                                val dotSize = 42f
                                Box(
                                    modifier = Modifier
                                        .align(position)
                                        .size(64.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            if (!tappable) return@clickable
                                            tappable = false
                                            currentDot++
                                            if (currentDot >= dotCount) {
                                                lastDotPosition = position
                                                completed = true
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Canvas(
                                        modifier = Modifier.size(dotSize.dp)
                                    ) {
                                        drawArc(
                                            color = dotColor,
                                            startAngle = -90f,
                                            sweepAngle = sweep.value,
                                            useCenter = true
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.width(CONTENT_WIDTH.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }
    }
}
