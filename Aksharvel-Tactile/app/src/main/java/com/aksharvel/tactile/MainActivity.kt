package com.aksharvel.tactile

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var speaker: Speaker
    private lateinit var listener: Listener
    private lateinit var recogniser: Recogniser

    /** A refusal only means the speaking tab stays off; nothing else changes. */
    private val askMic = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        speaker = Speaker(this)
        listener = Listener(this)
        recogniser = Recogniser(this)
        val store = Store(this)

        // Loading the speech model takes seconds - never on the main thread.
        lifecycleScope.launch(Dispatchers.IO) { recogniser.load() }

        if (!Listener.hasPermission(this)) {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent { App(speaker, listener, recogniser, store) }
    }

    override fun onDestroy() {
        speaker.shutdown()
        recogniser.release()
        super.onDestroy()
    }
}

private sealed interface Screen {
    object Home : Screen
    /** node = path index of the letter, or null for free practice (learnedOnly = only finished letters). */
    data class Lesson(val node: Int?, val learnedOnly: Boolean = false) : Screen
    data class Result(val node: Int?, val firstTry: Int, val total: Int, val missed: List<String>) : Screen
    data class WriteSession(val mode: WriteMode, val letters: List<String>) : Screen
    data class WriteResult(val passed: List<String>, val missed: List<String>, val total: Int) : Screen
    data class SpeakSession(val letters: List<String>) : Screen
    data class SpeakResult(val passed: List<String>, val missed: List<String>, val total: Int) : Screen
}

@Composable
private fun App(
    speaker: Speaker,
    listener: Listener,
    recogniser: Recogniser,
    store: Store
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var tab by remember { mutableIntStateOf(0) }

    BackHandler(enabled = screen !is Screen.Home) { screen = Screen.Home }

    Column(
        Modifier
            .fillMaxSize()
            .background(Cream)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        when (val s = screen) {
            is Screen.Home -> {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> PathScreen(store) { screen = Screen.Lesson(it) }
                        1 -> WriteHomeScreen(store) { mode ->
                            screen = Screen.WriteSession(mode, pickWriteLetters(store))
                        }
                        2 -> SpeakHomeScreen(store, recogniser) {
                            screen = Screen.SpeakSession(pickSpeakLetters(store))
                        }
                        3 -> PracticeScreen(
                            store,
                            onRandom = { screen = Screen.Lesson(null, false) },
                            onLearned = { screen = Screen.Lesson(null, true) }
                        )
                        else -> ProfileScreen(store)
                    }
                }
                BottomNav(tab) { tab = it }
            }

            is Screen.Lesson -> {
                val node = s.node
                val questions = remember(s) {
                    if (node != null) {
                        buildQuestions(nodeTargets(node, store.doneSet)) { it == 1 || it == 4 }
                    } else {
                        val pool = if (s.learnedOnly) store.doneSet.sorted().map { VARNAMALA[it] } else VARNAMALA
                        val order = pool.ifEmpty { VARNAMALA }.shuffled()
                        buildQuestions(List(8) { order[it % order.size] }) { it % 3 == 1 }
                    }
                }
                LessonScreen(
                    speaker = speaker,
                    questions = questions,
                    onExit = { screen = Screen.Home },
                    onFinish = { firstTry, missed ->
                        if (node != null) store.onLessonFinished(node) else store.touchStreak()
                        screen = Screen.Result(node, firstTry, questions.size, missed)
                    }
                )
            }

            is Screen.WriteSession -> WriteSessionScreen(
                mode = s.mode,
                speaker = speaker,
                letters = s.letters,
                onWritten = { letter -> store.onWritten(VARNAMALA.indexOf(letter)) },
                onExit = { screen = Screen.Home },
                onFinish = { passed, missed -> screen = Screen.WriteResult(passed, missed, s.letters.size) }
            )

            is Screen.SpeakSession -> SpeakSessionScreen(
                speaker = speaker,
                listener = listener,
                recogniser = recogniser,
                letters = s.letters,
                onSpoken = { letter -> store.onSpoken(VARNAMALA.indexOf(letter)) },
                onExit = { screen = Screen.Home },
                onFinish = { passed, missed ->
                    screen = Screen.SpeakResult(passed, missed, s.letters.size)
                }
            )

            is Screen.SpeakResult -> SpeakResultScreen(
                passed = s.passed,
                missed = s.missed,
                total = s.total,
                onNext = { screen = Screen.Home }
            )

            is Screen.WriteResult -> WriteResultScreen(
                passed = s.passed,
                missed = s.missed,
                total = s.total,
                onNext = { screen = Screen.Home }
            )

            is Screen.Result -> ResultScreen(
                letter = s.node?.let { VARNAMALA[it] },
                firstTry = s.firstTry,
                total = s.total,
                missed = s.missed,
                onNext = { screen = Screen.Home }
            )
        }
    }
}
