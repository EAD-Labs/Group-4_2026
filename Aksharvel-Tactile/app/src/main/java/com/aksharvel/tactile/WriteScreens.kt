package com.aksharvel.tactile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The three ways to practise writing. */
enum class WriteMode { TRACE, SEE, HEAR }

private enum class PadState { WRITING, CHECKING, ACCEPTED, SKIPPED }

private const val WRITE_TRIES = 3
private const val WORK_SIZE = 320f   // the checker works on a copy of the pad at most this many px wide/tall
private const val LETTERS_PER_SESSION = 5

/** Letters for one writing round: learned letters not yet written first, then the next letters on the path. */
fun pickWriteLetters(store: Store, n: Int = LETTERS_PER_SESSION): List<String> {
    val learned = store.doneSet.sorted()
    val next = VARNAMALA.indices.filter { it !in store.doneSet }.take(n)
    val order = learned.filter { it !in store.writeDone }.shuffled() +
        learned.filter { it in store.writeDone }.shuffled() +
        next
    return order.take(n).map { VARNAMALA[it] }
}

// ---------------------------------------------------------------- write tab

@Composable
fun WriteHomeScreen(store: Store, onStart: (WriteMode) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Mascot(72.dp)
        Spacer(Modifier.height(12.dp))
        Text("लिहा", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(
            "✏️ लिहिलेली अक्षरे: ${store.written} / ${VARNAMALA.size}",
            fontSize = 16.sp, color = Muted, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(20.dp))
        ModeCard("🖍️ गिरवा", "अक्षरावरून बोट फिरवा", Purple, PurpleDark) { onStart(WriteMode.TRACE) }
        Spacer(Modifier.height(14.dp))
        ModeCard("👀 पाहा आणि लिहा", "अक्षर पाहा, मग स्वतः लिहा", Teal, TealDark) { onStart(WriteMode.SEE) }
        Spacer(Modifier.height(14.dp))
        ModeCard("👂 ऐका आणि लिहा", "अक्षर ऐका, मग स्वतः लिहा", Coral, CoralDark) { onStart(WriteMode.HEAR) }
    }
}

@Composable
private fun ModeCard(title: String, subtitle: String, face: Color, edge: Color, onClick: () -> Unit) {
    Chunky(
        Modifier
            .fillMaxWidth()
            .height(104.dp),
        face, edge, RoundedCornerShape(28.dp), depth = 8.dp, onClick = onClick
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 16.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        }
    }
}

// ---------------------------------------------------------------- grading glue

private class Graded(val verdict: Verdict, val sx: Float, val sy: Float)

/** Rebuilds the letter's mask at a small working size and grades the child's strokes against it. */
private fun gradeWriting(
    letter: String,
    padW: Int,
    padH: Int,
    strokes: List<List<Offset>>,
    freeWrite: Boolean
): Graded {
    val s = min(1f, WORK_SIZE / max(padW, padH))
    val ww = max(48, (padW * s).roundToInt())
    val hh = max(48, (padH * s).roundToInt())
    val sx = ww.toFloat() / padW
    val sy = hh.toFloat() / padH
    val mask = Glyph.mask(letter, ww, hh)
    var st = strokes.filter { it.size > 1 }.map { pts ->
        FloatArray(pts.size * 2) { i ->
            val p = pts[i / 2]
            if (i % 2 == 0) p.x * sx else p.y * sy
        }
    }
    if (freeWrite) st = Classifier.fitToGlyph(mask, ww, hh, st)
    return Graded(Classifier.classify(mask, ww, hh, st), sx, sy)
}

private fun feedback(band: Band, freeWrite: Boolean): String = when (band) {
    Band.ACCEPTED -> "छान! 🎉"
    Band.MISSING_SHIROREKHA -> "वरची रेघ राहिली"
    Band.INCOMPLETE -> "अजून थोडं राहिलं"
    Band.OFF_LINE -> if (freeWrite) "अक्षर पुन्हा लिहून पाहा" else "रेषेच्या आत लिही"
    Band.SCRIBBLE -> "हळू, अक्षर काढ"
    Band.EMPTY -> "अक्षर लिही"
}

private class Highlight(val x: Float, val y: Float, val w: Float, val h: Float)

// ---------------------------------------------------------------- session

@Composable
fun WriteSessionScreen(
    mode: WriteMode,
    speaker: Speaker,
    letters: List<String>,
    onWritten: (String) -> Unit,
    onExit: () -> Unit,
    onFinish: (passed: List<String>, missed: List<String>) -> Unit
) {
    val freeWrite = mode != WriteMode.TRACE
    var qi by remember { mutableIntStateOf(0) }
    var tries by remember { mutableIntStateOf(0) }
    var passed by remember { mutableStateOf(listOf<String>()) }
    var missed by remember { mutableStateOf(listOf<String>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf(PadState.WRITING) }
    var showing by remember { mutableStateOf(mode == WriteMode.SEE) }   // SEE: the big letter is on screen
    var highlight by remember { mutableStateOf<Highlight?>(null) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val ghostPaint = remember { Glyph.paint() }
    val letter = letters[qi]

    fun next() {
        if (qi + 1 >= letters.size) {
            onFinish(passed, missed)
        } else {
            qi++
            tries = 0
            message = null
            state = PadState.WRITING
            showing = mode == WriteMode.SEE
            highlight = null
            strokes.clear()
            current.clear()
        }
    }

    fun clearPad() {
        if (state != PadState.WRITING) return
        strokes.clear()
        current.clear()
        highlight = null
        message = null
    }

    fun check() {
        if (state != PadState.WRITING || showing) return
        if (strokes.isEmpty() || padSize.width == 0 || padSize.height == 0) {
            message = feedback(Band.EMPTY, freeWrite)
            return
        }
        state = PadState.CHECKING
        val snapshot = strokes.toList()
        val w = padSize.width
        val h = padSize.height
        scope.launch {
            val g = withContext(Dispatchers.Default) { gradeWriting(letter, w, h, snapshot, freeWrite) }
            val v = g.verdict
            message = feedback(v.band, freeWrite)
            if (v.band == Band.ACCEPTED) {
                state = PadState.ACCEPTED
                highlight = null
                passed = passed + letter
                onWritten(letter)
                delay(1300)
                next()
            } else {
                tries++
                highlight = null
                if (mode == WriteMode.TRACE) {
                    // Point at the part that is missing, so the feedback is spatial.
                    val sb = v.shiroBand
                    if (v.band == Band.MISSING_SHIROREKHA && sb != null) {
                        highlight = Highlight(0f, sb[0] / g.sy - 4f, w.toFloat(), (sb[1] - sb[0]) / g.sy + 8f)
                    } else if (v.weakestRegion >= 0) {
                        val gx = v.weakestRegion % 2
                        val gy = v.weakestRegion / 2
                        highlight = Highlight(gx * w / 2f, gy * h / 2f, w / 2f, h / 2f)
                    }
                }
                if (tries >= WRITE_TRIES) {
                    // A mis-grading checker may annoy a child, but it must never block her.
                    state = PadState.SKIPPED
                    missed = missed + letter
                    message = "ठीक आहे, पुढे जा"
                    delay(2200)
                    next()
                } else {
                    state = PadState.WRITING
                }
            }
        }
    }

    // Say the letter when a new one appears.
    LaunchedEffect(qi, speaker.status) {
        delay(450)
        if (speaker.isReady) speaker.speak(letter)
    }

    val instruction = when (mode) {
        WriteMode.TRACE -> "अक्षरावरून बोट फिरवा"
        WriteMode.SEE -> if (showing) "हे अक्षर नीट पाहा" else "आता हे अक्षर स्वतः लिहा"
        WriteMode.HEAR -> "ऐका आणि अक्षर लिहा"
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
                "✕", fontSize = 28.sp, color = Muted,
                modifier = Modifier
                    .clickable(onClick = onExit)
                    .padding(end = 12.dp)
            )
            ProgressBar(
                (qi + if (state == PadState.ACCEPTED || state == PadState.SKIPPED) 1 else 0).toFloat() / letters.size,
                Purple, Lavender, Modifier.weight(1f)
            )
            Spacer(Modifier.size(12.dp))
            Row {
                repeat(WRITE_TRIES) { i ->
                    Text("♥", fontSize = 24.sp, color = if (i < WRITE_TRIES - tries) Coral else Lavender)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            instruction, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            Pill(
                "🔊 ऐका", Color.White, Teal,
                fontSize = if (mode == WriteMode.HEAR) 22.sp else 16.sp,
                onClick = { if (speaker.isReady) speaker.speak(letter) }
            )
            Pill(
                "🐢 हळू", TealDark, TealTint,
                fontSize = if (mode == WriteMode.HEAR) 22.sp else 16.sp,
                onClick = { if (speaker.isReady) speaker.speak(letter, slow = true) }
            )
            if (mode == WriteMode.SEE && !showing && state == PadState.WRITING) {
                Pill("👀 पाहा", Purple, Lavender, fontSize = 16.sp, onClick = { showing = true })
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---- the writing pad
        val shape = RoundedCornerShape(28.dp)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(shape)
                .background(Color.White)
                .border(3.dp, Lavender, shape)
                .onSizeChanged { padSize = it }
        ) {
            val inkPx = with(density) { 12.dp.toPx() }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(qi, state, showing) {
                        if (state != PadState.WRITING || showing) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            current.clear()
                            current.add(down.position)
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                current.add(change.position)
                                change.consume()
                            }
                            if (current.size > 1) strokes.add(current.toList())
                            current.clear()
                        }
                    }
            ) {
                // faint letter: always when tracing; as a hint after misses; to celebrate; when skipping
                val ghostColor: Color? = when {
                    state == PadState.ACCEPTED -> Teal.copy(alpha = 0.30f)
                    state == PadState.SKIPPED -> Navy.copy(alpha = 0.18f)
                    mode == WriteMode.TRACE -> Navy.copy(alpha = 0.16f)
                    mode == WriteMode.HEAR && tries >= 2 -> Navy.copy(alpha = 0.14f)
                    else -> null
                }
                if (ghostColor != null) {
                    ghostPaint.color = ghostColor.toArgb()
                    drawIntoCanvas { c -> Glyph.draw(c.nativeCanvas, letter, size.width, size.height, ghostPaint) }
                }
                highlight?.let {
                    drawRect(Yellow.copy(alpha = 0.35f), Offset(it.x, it.y), Size(it.w, it.h))
                }
                strokes.forEach { drawInk(it, Navy, inkPx) }
                if (current.isNotEmpty()) drawInk(current.toList(), Navy, inkPx)
            }

            if (mode == WriteMode.SEE) {
                BigLetterOverlay(letter, showing, padSize, density.density)
            }
        }

        Text(
            message ?: "",
            fontSize = 24.sp, fontWeight = FontWeight.Bold,
            color = if (state == PadState.ACCEPTED) GreenDark else Navy,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(top = 8.dp)
        )

        if (mode == WriteMode.SEE && showing) {
            Chunky(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                Teal, TealDark, RoundedCornerShape(26.dp), onClick = { showing = false }
            ) {
                Text("आता लिहा ✏️", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            val idle = state == PadState.WRITING
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Chunky(
                    Modifier
                        .weight(1f)
                        .height(68.dp),
                    Lavender, Color(0xFFD5D0EA), RoundedCornerShape(26.dp),
                    enabled = idle, onClick = { clearPad() }
                ) {
                    Text("पुसा", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Muted)
                }
                Chunky(
                    Modifier
                        .weight(1f)
                        .height(68.dp),
                    if (idle) Teal else Lavender, if (idle) TealDark else Color(0xFFD5D0EA),
                    RoundedCornerShape(26.dp),
                    enabled = idle, onClick = { check() }
                ) {
                    Text(
                        "तपासा", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = if (idle) Color.White else LockedFg
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun DrawScope.drawInk(pts: List<Offset>, color: Color, width: Float) {
    if (pts.isEmpty()) return
    if (pts.size == 1) {
        drawCircle(color, width / 2f, pts[0])
        return
    }
    val path = Path()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** "See and write": the letter is shown big, then shrinks into the top-right corner while the child writes. */
@Composable
private fun BigLetterOverlay(letter: String, showing: Boolean, pad: IntSize, density: Float) {
    val t by animateFloatAsState(if (showing) 0f else 1f, tween(550), label = "corner")
    val w = pad.width.toFloat()
    val h = pad.height.toFloat()
    val fontPx = min(w, h) * 0.55f
    val margin = 14f * density
    val endScale = 0.24f
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 1f - t)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter,
            fontSize = (fontPx / density).sp,
            fontWeight = FontWeight.Bold,
            color = Navy,
            modifier = Modifier.graphicsLayer {
                val sc = 1f + (endScale - 1f) * t
                scaleX = sc
                scaleY = sc
                translationX = (w / 2f - margin - fontPx * 0.30f) * t
                translationY = -(h / 2f - margin - fontPx * 0.36f) * t
            }
        )
    }
}

// ---------------------------------------------------------------- session complete

@Composable
fun WriteResultScreen(passed: List<String>, missed: List<String>, total: Int, onNext: () -> Unit) {
    val ratio = if (total == 0) 0f else passed.size.toFloat() / total
    val stars = when {
        ratio >= 0.8f -> "⭐⭐⭐"
        ratio >= 0.5f -> "⭐⭐"
        else -> "⭐"
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("छान लिहिलंत! ✏️", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Navy)
            Spacer(Modifier.height(20.dp))
            Chunky(Modifier.size(170.dp), Yellow, YellowDark, CircleShape, depth = 10.dp) {
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(Cream),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${marathiNumber(passed.size)}/${marathiNumber(total)}",
                            fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Gold
                        )
                        Text(stars, fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            if (passed.isNotEmpty()) LetterRow("बरोबर लिहिलेली", passed, Color.White, Teal)
            if (missed.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LetterRow("यांचा पुन्हा सराव", missed, SoftYellow, Gold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Chunky(
            Modifier
                .fillMaxWidth()
                .height(72.dp),
            Teal, TealDark, RoundedCornerShape(28.dp), onClick = onNext
        ) {
            Text("पुढे चला", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun LetterRow(title: String, letters: List<String>, bg: Color, titleColor: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (bg == Color.White) TealTint else bg)
            .padding(18.dp)
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = titleColor)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            letters.forEach { l ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(l, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Navy)
                }
            }
        }
    }
}
