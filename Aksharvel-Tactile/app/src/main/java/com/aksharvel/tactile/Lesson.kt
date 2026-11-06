package com.aksharvel.tactile

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MAX_TRIES = 3

/** One question: hear [answer], pick it from [options]. [drag] = drag-and-drop style (3 tiles). */
data class Q(val answer: String, val options: List<String>, val drag: Boolean)

fun buildQuestions(targets: List<String>, dragAt: (Int) -> Boolean): List<Q> =
    targets.mapIndexed { i, answer ->
        val drag = dragAt(i)
        val distractors = (VARNAMALA - answer).shuffled().take(if (drag) 2 else 3)
        Q(answer, (distractors + answer).shuffled(), drag)
    }

/** Lesson for letter [index]: the letter 3 times, mixed with 3 review letters (finished ones first). */
fun nodeTargets(index: Int, done: Set<Int>): List<String> {
    val focus = VARNAMALA[index]
    val learned = (done - index).map { VARNAMALA[it] }.shuffled()
    val others = (VARNAMALA - focus - learned.toSet()).shuffled()
    val review = learned + others
    return listOf(focus, review[0], focus, review[1], focus, review[2])
}

private enum class TileState { NORMAL, SELECTED, WRONG, CORRECT, REVEALED, DIMMED }

@Composable
fun LessonScreen(
    speaker: Speaker,
    questions: List<Q>,
    onExit: () -> Unit,
    onFinish: (firstTry: Int, missed: List<String>) -> Unit
) {
    var qi by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf(setOf<String>()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var solved by remember { mutableStateOf(false) }
    var firstTry by remember { mutableIntStateOf(0) }
    var missed by remember { mutableStateOf(listOf<String>()) }
    var playing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val q = questions[qi]
    val hearts = MAX_TRIES - wrong.size
    val revealed = !solved && hearts <= 0
    val done = solved || revealed

    fun play(slow: Boolean = false) {
        if (!speaker.isReady) return
        speaker.speak(q.answer, slow)
        scope.launch {
            playing = true
            delay(if (slow) 2200L else 1400L)
            playing = false
        }
    }

    fun onWrong(letter: String) {
        wrong = wrong + letter
        if (q.answer !in missed) missed = missed + q.answer
    }

    fun onCorrect() {
        solved = true
        if (wrong.isEmpty()) firstTry++
    }

    fun next() {
        if (qi + 1 >= questions.size) {
            onFinish(firstTry, missed)
        } else {
            qi++
            wrong = emptySet()
            selected = null
            solved = false
        }
    }

    // Say the letter automatically when a new question appears.
    LaunchedEffect(qi, speaker.status) {
        delay(500)
        play()
    }

    val message = when {
        solved -> "शाबास! बरोबर!"
        revealed -> "हे बरोबर उत्तर आहे."
        wrong.isNotEmpty() -> "पुन्हा प्रयत्न करा!"
        q.drag -> "ऐकलेले अक्षर वर ओढा"
        else -> "तुम्ही कोणते अक्षर ऐकले?"
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "✕",
                fontSize = 28.sp,
                color = Muted,
                modifier = Modifier
                    .clickable(onClick = onExit)
                    .padding(end = 12.dp)
            )
            ProgressBar(
                (qi + if (done) 1 else 0).toFloat() / questions.size,
                Teal, Lavender, Modifier.weight(1f)
            )
            Spacer(Modifier.size(12.dp))
            Row {
                repeat(MAX_TRIES) { i ->
                    Text("♥", fontSize = 24.sp, color = if (i < hearts) Coral else Lavender)
                }
            }
        }

        if (q.drag) {
            DragBody(
                q = q, wrong = wrong, solved = solved, done = done, playing = playing,
                message = message,
                onPlay = { play() },
                onDrop = { letter -> if (letter == q.answer) onCorrect() else onWrong(letter) },
                onNext = { next() },
                isLast = qi + 1 >= questions.size
            )
        } else {
            ChooseBody(
                q = q, wrong = wrong, selected = selected, solved = solved, revealed = revealed,
                done = done, message = message,
                onPlay = { slow -> play(slow) },
                onSelect = { selected = it },
                onCheck = {
                    selected?.let { s ->
                        if (s == q.answer) onCorrect() else onWrong(s)
                        selected = null
                    }
                },
                onNext = { next() },
                isLast = qi + 1 >= questions.size
            )
        }
    }
}

@Composable
private fun NextButton(isLast: Boolean, onNext: () -> Unit) {
    Chunky(
        Modifier
            .fillMaxWidth()
            .height(72.dp),
        Teal, TealDark, RoundedCornerShape(24.dp), onClick = onNext
    ) {
        Text(
            if (isLast) "पूर्ण" else "पुढे  ▶",
            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White
        )
    }
}

// ---------------------------------------------------------------- choose (2x2)

@Composable
private fun ColumnScope.ChooseBody(
    q: Q,
    wrong: Set<String>,
    selected: String?,
    solved: Boolean,
    revealed: Boolean,
    done: Boolean,
    message: String,
    onPlay: (slow: Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onCheck: () -> Unit,
    onNext: () -> Unit,
    isLast: Boolean
) {
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Mascot(48.dp)
        Spacer(Modifier.size(10.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Lavender)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(message, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
        }
    }

    Spacer(Modifier.height(10.dp))
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .size(164.dp)
            .clip(CircleShape)
            .background(Color(0xFFE6DDF3)),
        contentAlignment = Alignment.Center
    ) {
        Chunky(
            Modifier.size(142.dp), Purple, PurpleDark, CircleShape, depth = 8.dp,
            onClick = { onPlay(false) }
        ) {
            Text("▶", fontSize = 44.sp, color = Color.White)
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "ऐकण्यासाठी दाबा",
        modifier = Modifier.align(Alignment.CenterHorizontally),
        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Muted
    )
    Spacer(Modifier.height(6.dp))
    Pill(
        "🐢 हळू", Muted, SoftYellow,
        Modifier.align(Alignment.CenterHorizontally),
        fontSize = 17.sp, onClick = { onPlay(true) }
    )

    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (row in 0..1) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (col in 0..1) {
                    val letter = q.options[row * 2 + col]
                    val state = when {
                        solved && letter == q.answer -> TileState.CORRECT
                        revealed && letter == q.answer -> TileState.REVEALED
                        letter in wrong -> TileState.WRONG
                        done -> TileState.DIMMED
                        letter == selected -> TileState.SELECTED
                        else -> TileState.NORMAL
                    }
                    ChooseTile(
                        letter, state,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) { onSelect(letter) }
                }
            }
        }
    }

    if (done) {
        NextButton(isLast, onNext)
    } else {
        Chunky(
            Modifier
                .fillMaxWidth()
                .height(72.dp),
            if (selected != null) Yellow else Lavender,
            if (selected != null) YellowDark else Color(0xFFD5D0EA),
            RoundedCornerShape(24.dp),
            enabled = selected != null,
            onClick = onCheck
        ) {
            Text(
                "तपासा", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = if (selected != null) Navy else LockedFg
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ChooseTile(letter: String, state: TileState, modifier: Modifier, onClick: () -> Unit) {
    val face: Color
    val edge: Color
    val fg: Color
    when (state) {
        TileState.NORMAL -> { face = Color.White; edge = Lavender; fg = Navy }
        TileState.SELECTED -> { face = TealTint; edge = Teal; fg = TealDark }
        TileState.WRONG -> { face = Color(0xFFFFE3DF); edge = Coral; fg = Coral }
        TileState.CORRECT, TileState.REVEALED -> { face = Green; edge = GreenDark; fg = Color.White }
        TileState.DIMMED -> { face = Color(0xFFF1EEF8); edge = Lavender; fg = LockedFg }
    }
    val mark = when (state) {
        TileState.WRONG -> "✗"
        TileState.CORRECT, TileState.REVEALED -> "✓"
        else -> ""
    }
    Chunky(
        modifier, face, edge, RoundedCornerShape(28.dp), depth = 8.dp,
        enabled = state == TileState.NORMAL || state == TileState.SELECTED,
        onClick = onClick
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            val glyph = (side.value * (if (letter.length > 1) 0.42f else 0.55f)).sp
            Text(letter, fontSize = glyph, fontWeight = FontWeight.Bold, color = fg, textAlign = TextAlign.Center)
            if (mark.isNotEmpty()) {
                Text(
                    mark, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = fg,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- drag & drop

@Composable
private fun ColumnScope.DragBody(
    q: Q,
    wrong: Set<String>,
    solved: Boolean,
    done: Boolean,
    playing: Boolean,
    message: String,
    onPlay: () -> Unit,
    onDrop: (String) -> Unit,
    onNext: () -> Unit,
    isLast: Boolean
) {
    var dropRect by remember { mutableStateOf(Rect.Zero) }

    Spacer(Modifier.height(16.dp))
    Pill(
        if (playing) "🔊 वाजत आहे…" else "🔊 ऐका",
        Purple, Lavender,
        Modifier.align(Alignment.CenterHorizontally),
        onClick = onPlay
    )

    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .onGloballyPositioned { dropRect = it.boundsInRoot() }
            .drawBehind {
                val sw = 4.dp.toPx()
                drawRoundRect(
                    color = Yellow,
                    topLeft = Offset(sw / 2, sw / 2),
                    size = Size(size.width - sw, size.height - sw),
                    cornerRadius = CornerRadius(28.dp.toPx()),
                    style = Stroke(width = sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f)))
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (solved) {
            Chunky(Modifier.size(140.dp), Green, GreenDark, RoundedCornerShape(32.dp), depth = 8.dp) {
                Text(q.answer, fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            HandDemo()
        }
    }

    Text(
        message,
        modifier = Modifier.align(Alignment.CenterHorizontally),
        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Muted
    )
    Spacer(Modifier.height(16.dp))

    val faces = listOf(Coral to CoralDark, Teal to TealDark, Purple to PurpleDark)
    val tilts = listOf(-4f, 0f, 4f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(112.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        q.options.forEachIndexed { i, letter ->
            val hidden = solved && letter == q.answer
            if (hidden) {
                Spacer(Modifier.size(96.dp))
            } else {
                DragTile(
                    letter = letter,
                    face = if (letter in wrong) Color(0xFFCFCBDD) else faces[i].first,
                    edge = if (letter in wrong) Color(0xFFB4AFC8) else faces[i].second,
                    tilt = tilts[i],
                    enabled = !done && letter !in wrong,
                    dropRect = dropRect,
                    onDrop = { onDrop(letter) }
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    if (done) {
        NextButton(isLast, onNext)
    } else {
        Pill(
            "🔁 पुन्हा ऐका", Muted, SoftYellow,
            Modifier.align(Alignment.CenterHorizontally),
            onClick = onPlay
        )
        Spacer(Modifier.height(22.dp))
    }
    Spacer(Modifier.height(12.dp))
}

/** Drag the tile onto the drop zone, or just tap it (easier for very young children). */
@Composable
private fun DragTile(
    letter: String,
    face: Color,
    edge: Color,
    tilt: Float,
    enabled: Boolean,
    dropRect: Rect,
    onDrop: () -> Unit
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Rect.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val currentDrop by rememberUpdatedState(onDrop)
    val zone by rememberUpdatedState(dropRect)

    Box(
        Modifier
            .size(96.dp)
            .zIndex(if (dragging) 1f else 0f)
            .onGloballyPositioned { origin = it.boundsInRoot() }
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .graphicsLayer {
                rotationZ = if (dragging) 0f else tilt
                scaleX = if (dragging) 1.12f else 1f
                scaleY = if (dragging) 1.12f else 1f
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDrag = { change, amount ->
                            change.consume()
                            offset += amount
                        },
                        onDragEnd = {
                            dragging = false
                            val centre = origin.center + offset
                            offset = Offset.Zero
                            if (zone.inflate(30f).contains(centre)) currentDrop()
                        },
                        onDragCancel = {
                            dragging = false
                            offset = Offset.Zero
                        }
                    )
                }
            }
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onTap = { currentDrop() })
            }
    ) {
        Chunky(Modifier.fillMaxSize(), face, edge, RoundedCornerShape(26.dp), depth = 8.dp) {
            Text(
                letter,
                fontSize = if (letter.length > 1) 34.sp else 46.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else Color(0xFF8A85A3)
            )
        }
    }
}

/** Looping preview: a hand drags a tile up into the box (replaces the "इथे ठेवा" text). */
@Composable
private fun HandDemo() {
    val transition = rememberInfiniteTransition(label = "hand")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "t"
    )
    Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(32.dp))
                .background(SoftYellow)
        )
        Box(
            Modifier
                .size(72.dp)
                .offset { IntOffset(0, ((1f - t) * 150.dp.toPx()).roundToInt()) }
                .graphicsLayer {
                    alpha = when {
                        t < 0.12f -> t / 0.12f
                        t > 0.88f -> (1f - t) / 0.12f
                        else -> 1f
                    }
                }
        ) {
            Chunky(Modifier.fillMaxSize(), Coral, CoralDark, RoundedCornerShape(20.dp), depth = 6.dp) {}
            Text(
                "👆", fontSize = 46.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 18.dp, y = 30.dp)
            )
        }
    }
}
