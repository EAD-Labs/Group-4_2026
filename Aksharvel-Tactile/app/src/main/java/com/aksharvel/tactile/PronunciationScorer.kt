package com.aksharvel.tactile

import java.text.Normalizer

/**
 * Scores a spoken attempt against the letter or word on screen.
 *
 * Works on letters as a reader counts them, not Unicode code points:
 * क्ष is one letter made of three code points, and नमस्ते is three letters
 * made of six. Counting code points would spread one real mistake across
 * more units and flatter the score.
 *
 * Independent of which recogniser produced [heard].
 */
object PronunciationScorer {

    /** Pass mark for words of 4+ letters. */
    const val PASS = 80
    /** "Close, try again" mark for words of 4+ letters. */
    const val CLOSE = 55

    /**
     * Short targets get relaxed marks. On a single letter — which is most of
     * Aksharvel — one wrong letter is 100% wrong, and on three letters it is a
     * 33% hit, so the strict mark would read a near miss as total failure.
     */
    private const val SHORT_LEN = 3
    private const val SHORT_PASS = 65
    private const val SHORT_CLOSE = 40

    enum class Verdict { CORRECT, CLOSE, RETRY, NO_SPEECH }

    data class Result(
        val heard: String,
        val target: String,
        /** 0.0 to 100.0 */
        val match: Double,
        /** Letter edits needed to turn [heard] into [target]. */
        val distance: Int,
        val heardLen: Int,
        val targetLen: Int,
        val verdict: Verdict,
        /** The pass mark actually applied, after the short-target relaxation. */
        val passMark: Int,
    ) {
        val isCorrect: Boolean get() = verdict == Verdict.CORRECT
    }

    private val PUNCT = Regex("[\u0964\u0965.,!?;:\"']")
    private val SPACES = Regex("\\s+")

    /** NFC-normalise, drop danda and punctuation, collapse whitespace. */
    fun normalise(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
            .replace(PUNCT, "")
            .replace(SPACES, " ")
            .trim()

    fun letters(text: String): List<String> = Devanagari.clusters(text)

    /** Levenshtein distance over two sequences. */
    fun <T> editDistance(a: List<T>, b: List<T>): Int {
        if (a.isEmpty()) return b.size
        if (b.isEmpty()) return a.size
        var prev = IntArray(b.size + 1) { it }
        var cur = IntArray(b.size + 1)
        for (i in 1..a.size) {
            cur[0] = i
            for (j in 1..b.size) {
                val sub = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + sub)
            }
            val swap = prev; prev = cur; cur = swap
        }
        return prev[b.size]
    }

    fun score(
        heard: String,
        target: String,
        passAt: Int = PASS,
        closeAt: Int = CLOSE,
    ): Result {
        val tNorm = normalise(target)
        val hNorm = normalise(heard)
        val t = letters(tNorm)
        val h = letters(hNorm)

        require(t.isNotEmpty()) { "target must not be empty" }

        var pass = passAt
        var close = closeAt
        if (t.size <= SHORT_LEN) { pass = SHORT_PASS; close = SHORT_CLOSE }

        if (h.isEmpty()) {
            return Result("", tNorm, 0.0, t.size, 0, t.size,
                Verdict.NO_SPEECH, pass)
        }

        val dist = editDistance(h, t)
        val match = (100.0 - dist.toDouble() / maxOf(h.size, t.size) * 100.0)
            .coerceAtLeast(0.0)

        val verdict = when {
            match >= pass -> Verdict.CORRECT
            match >= close -> Verdict.CLOSE
            else -> Verdict.RETRY
        }

        return Result(hNorm, tNorm, Math.round(match * 10.0) / 10.0,
            dist, h.size, t.size, verdict, pass)
    }
}
