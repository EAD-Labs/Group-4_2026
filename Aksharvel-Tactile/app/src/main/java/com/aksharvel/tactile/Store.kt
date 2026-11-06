package com.aksharvel.tactile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.TimeZone

/** Learner progress saved on the phone (SharedPreferences). Nothing leaves the device. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("aksharvel", Context.MODE_PRIVATE)

    /** Indices of letters whose lesson has been finished. Any letter can be opened at any time. */
    var doneSet: Set<Int> by mutableStateOf(loadDone())
        private set

    val completed: Int get() = doneSet.size

    /** Indices of letters the child has written correctly in the writing mode. */
    var writeDone: Set<Int> by mutableStateOf(loadWritten())
        private set

    val written: Int get() = writeDone.size

    private fun loadWritten(): Set<Int> =
        prefs.getString("writeDone", null)?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    /** Indices of letters the child has said correctly in the speaking mode. */
    var spokeDone: Set<Int> by mutableStateOf(loadSpoken())
        private set

    val spoken: Int get() = spokeDone.size

    private fun loadSpoken(): Set<Int> =
        prefs.getString("spokeDone", null)?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    fun onSpoken(index: Int) {
        if (index >= 0 && index !in spokeDone) {
            spokeDone = spokeDone + index
            prefs.edit().putString("spokeDone", spokeDone.joinToString(",")).apply()
        }
        touchStreak()
    }

    fun onWritten(index: Int) {
        if (index >= 0 && index !in writeDone) {
            writeDone = writeDone + index
            prefs.edit().putString("writeDone", writeDone.joinToString(",")).apply()
        }
        touchStreak()
    }

    private fun loadDone(): Set<Int> {
        val raw = prefs.getString("done", null)
        if (raw != null) return raw.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        return (0 until prefs.getInt("completed", 0)).toSet() // from the earlier version
    }

    private var streak by mutableIntStateOf(prefs.getInt("streak", 0))
    private var lastDay: Long = prefs.getLong("lastDay", -1L)

    private fun today(): Long {
        val now = System.currentTimeMillis()
        return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L
    }

    fun currentStreak(): Int = if (lastDay >= today() - 1) streak else 0

    fun touchStreak() {
        val t = today()
        if (lastDay == t) return
        streak = if (lastDay == t - 1) streak + 1 else 1
        lastDay = t
        prefs.edit().putInt("streak", streak).putLong("lastDay", lastDay).apply()
    }

    fun onLessonFinished(index: Int) {
        if (index !in doneSet) {
            doneSet = doneSet + index
            prefs.edit().putString("done", doneSet.joinToString(",")).apply()
        }
        touchStreak()
    }

    fun reset() {
        doneSet = emptySet()
        writeDone = emptySet()
        spokeDone = emptySet()
        streak = 0
        lastDay = -1L
        prefs.edit().clear().apply()
    }
}
