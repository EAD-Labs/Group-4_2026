package com.aksharvel.tactile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SPEAK_TRIES = 3
private const val SPEAK_LETTERS_PER_SESSION = 5

private enum class MicState { IDLE, LISTENING, CHECKING, DONE }

/** Letters for one speaking round: learned letters first, then the next few on the path. */
fun pickSpeakLetters(store: Store, n: Int = SPEAK_LETTERS_PER_SESSION): List<String> {
    val learned = store.doneSet.sorted()
    val next = VARNAMALA.indices.filter { it !in store.doneSet }.take(n)
    val order = learned.filter { it !in store.spokeDone }.shuffled() +
        learned.filter { it in store.spokeDone }.shuffled() +
        next
    return order.take(n).map { VARNAMALA[it] }
}

// ---------------------------------------------------------------- speak tab

@Composable
fun SpeakHomeScreen(store: Store, recogniser: Recogniser, onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(52.dp)
            Spacer(Modifier.size(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Lavender)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    "अक्षर मोठ्याने बोला!",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        if (recogniser.isReady) {
            Text(
                "तुम्ही बोललेले अक्षर अ‍ॅप तपासेल",
                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Muted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Chunky(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                Coral, CoralDark, RoundedCornerShape(26.dp), onClick = onStart
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎤  सुरू करा", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "$SPEAK_LETTERS_PER_SESSION अक्षरे",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "बोललेले फक्त फोनवरच तपासले जाते",
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Muted,
                textAlign = TextAlign.Center
            )
        } else {
            // No model bundled, or the mic was refused. Say so plainly instead of
            // offering a button that cannot work.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(SoftYellow)
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎤", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "बोलण्याचा सराव अजून तयार नाही",
                        fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Navy,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        recogniser.detail ?: "The speech model is still loading.",
                        fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------- speak session

/**
 * "Say this letter": the letter is shown, the child reads it aloud, the recogniser
 * transcribes it and [PronunciationScorer] grades it.
 *
 * Deliberately gentler than the tap questions. A child who says a letter slightly
 * off has still engaged with it, and the recogniser is imperfect too, so a near
 * miss asks for another go rather than counting as wrong.
 */
@Composable
fun SpeakSessionScreen(
    speaker: Speaker,
    listener: Listener,
    recogniser: Recogniser,
    letters: List<String>,
    onSpoken: (String) -> Unit,
    onExit: () -> Unit,
    onFinish: (passed: List<String>, missed: List<String>) -> Unit
) {
    var qi by remember { mutableIntStateOf(0) }
    var tries by remember { mutableIntStateOf(0) }
    var passed by remember { mutableStateOf(listOf<String>()) }
    var missed by remember { mutableStateOf(listOf<String>()) }
    var state by remember { mutableStateOf(MicState.IDLE) }
    var result by remember { mutableStateOf<PronunciationScorer.Result?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val letter = letters[qi]

    // Follow the mic level while recording so the button can breathe with the voice.
    LaunchedEffect(state) {
        if (state == MicState.LISTENING) {
            while (state == MicState.LISTENING) {
                level = listener.level
                delay(60)
            }
            level = 0f
        }
    }

    fun advance() {
        if (qi + 1 >= letters.size) {
            onFinish(passed, missed)
        } else {
            qi += 1
            tries = 0
            state = MicState.IDLE
            result = null
        }
    }

    fun listen() {
        state = MicState.LISTENING
        scope.launch {
            val samples = withContext(Dispatchers.IO) { listener.record() }
            state = MicState.CHECKING
            val heard = withContext(Dispatchers.Default) {
                samples?.let { recogniser.transcribe(it) }
            }
            val r = PronunciationScorer.score(heard ?: "", letter)
            result = r
            tries += 1
            state = MicState.DONE
            if (r.isCorrect) {
                passed = passed + letter
                onSpoken(letter)
            } else if (tries >= SPEAK_TRIES) {
                missed = missed + letter
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("✕", Muted, Lavender, fontSize = 18.sp, onClick = onExit)
            Spacer(Modifier.size(10.dp))
            ProgressBar(
                (qi + if (state == MicState.DONE && result?.isCorrect == true) 1 else 0)
                    .toFloat() / letters.size,
                Purple, Lavender, Modifier.weight(1f)
            )
            Spacer(Modifier.size(12.dp))
            Row {
                repeat(SPEAK_TRIES) { i ->
                    Text(
                        "♥", fontSize = 22.sp,
                        color = if (i < SPEAK_TRIES - tries) Coral else Lavender
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(46.dp)
            Spacer(Modifier.size(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Lavender)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    when {
                        state == MicState.DONE && result?.isCorrect == true -> "शाबास! बरोबर!"
                        state == MicState.DONE && tries >= SPEAK_TRIES -> "पुढच्या वेळी नक्की जमेल!"
                        state == MicState.DONE -> "पुन्हा प्रयत्न करा!"
                        state == MicState.LISTENING -> "ऐकत आहे…"
                        state == MicState.CHECKING -> "तपासत आहे…"
                        else -> "हे अक्षर मोठ्याने बोला"
                    },
                    fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Navy
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // The letter to read out.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(168.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(SoftYellow),
            contentAlignment = Alignment.Center
        ) {
            Text(letter, fontSize = 92.sp, fontWeight = FontWeight.Bold, color = Navy)
        }

        Spacer(Modifier.height(8.dp))

        // Hear it first - reuses the recorded clips.
        Row(
            Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Pill("▶ ऐका", Muted, Lavender, fontSize = 17.sp) { speaker.speak(letter, false) }
            Pill("🐢 हळू", Muted, SoftYellow, fontSize = 17.sp) { speaker.speak(letter, true) }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                MicState.IDLE, MicState.LISTENING -> {
                    val listening = state == MicState.LISTENING
                    val pulse by animateFloatAsState(
                        targetValue = if (listening) 1f + level.coerceIn(0f, 1f) * 0.16f else 1f,
                        label = "micPulse"
                    )
                    Box(Modifier.size(164.dp).scale(pulse), contentAlignment = Alignment.Center) {
                        Chunky(
                            Modifier.size(146.dp),
                            if (listening) Coral else Purple,
                            if (listening) CoralDark else PurpleDark,
                            CircleShape,
                            depth = 8.dp,
                            onClick = { if (listening) listener.stop() else listen() }
                        ) {
                            Text(
                                if (listening) "■" else "🎤",
                                fontSize = if (listening) 42.sp else 50.sp,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (listening) "बोलून झाल्यावर दाबा" else "दाबा आणि बोला",
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Muted
                    )
                }

                MicState.CHECKING -> {
                    Box(
                        Modifier.size(146.dp).clip(CircleShape).background(Lavender),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("…", fontSize = 54.sp, fontWeight = FontWeight.Bold, color = Muted)
                    }
                }

                MicState.DONE -> {
                    val correct = result?.isCorrect == true
                    Box(
                        Modifier
                            .size(146.dp)
                            .clip(CircleShape)
                            .background(if (correct) TealTint else Lavender),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (correct) "✓" else "↻",
                            fontSize = 70.sp, fontWeight = FontWeight.Bold,
                            color = if (correct) GreenDark else Muted
                        )
                    }
                    // Show what was heard when it differs, so a reading child can see
                    // the mistake. No bare percentage.
                    val heard = result?.heard
                    if (!correct && !heard.isNullOrBlank() && heard != letter) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "ऐकले: $heard",
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Muted
                        )
                    }
                }
            }
        }

        when {
            state == MicState.DONE && (result?.isCorrect == true || tries >= SPEAK_TRIES) ->
                Chunky(
                    Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .padding(bottom = 0.dp),
                    Teal, TealDark, RoundedCornerShape(24.dp), onClick = { advance() }
                ) {
                    Text(
                        if (qi + 1 >= letters.size) "पूर्ण" else "पुढे  ▶",
                        fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color.White
                    )
                }

            state == MicState.DONE ->
                Chunky(
                    Modifier
                        .fillMaxWidth()
                        .height(70.dp),
                    Yellow, YellowDark, RoundedCornerShape(24.dp),
                    onClick = { state = MicState.IDLE; result = null }
                ) {
                    Text(
                        "पुन्हा  ↻", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy
                    )
                }

            // Always leave a way past a question: a flaky mic must never trap a child.
            else -> Pill(
                "वगळा  ▶", Muted, Lavender,
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 10.dp),
                fontSize = 18.sp
            ) { missed = missed + letter; advance() }
        }

        Spacer(Modifier.height(10.dp))
    }
}

// -------------------------------------------------------------- speak result

@Composable
fun SpeakResultScreen(
    passed: List<String>,
    missed: List<String>,
    total: Int,
    onNext: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Mascot(96.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (passed.size == total) "सगळी अक्षरे बरोबर!" else "छान प्रयत्न!",
            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Navy
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${marathiNumber(passed.size)} / ${marathiNumber(total)} अक्षरे बोललात",
            fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Muted
        )

        Spacer(Modifier.height(20.dp))
        if (passed.isNotEmpty()) {
            SpokenRow("बरोबर बोललात", passed, TealTint, GreenDark)
            Spacer(Modifier.height(10.dp))
        }
        if (missed.isNotEmpty()) {
            SpokenRow("पुन्हा सराव करा", missed, SoftYellow, Gold)
        }

        Spacer(Modifier.weight(1f))
        Chunky(
            Modifier
                .fillMaxWidth()
                .height(72.dp),
            Teal, TealDark, RoundedCornerShape(24.dp), onClick = onNext
        ) {
            Text("पुढे  ▶", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SpokenRow(title: String, letters: List<String>, bg: Color, titleColor: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(14.dp)
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = titleColor)
        Spacer(Modifier.height(6.dp))
        Text(
            letters.joinToString("   "),
            fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Navy
        )
    }
}
