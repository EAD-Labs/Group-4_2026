package com.aksharvel.tactile

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Stroke checker for the writing mode. A direct port of the classifier in the HTML prototype
 * (chamfer distance transform, pen-width tolerances, five gates). Pure Kotlin - no Android types -
 * so it can be tested on a laptop.
 *
 * A mask is w*h bytes, 1 where the target letter has ink. A stroke is a FloatArray [x0, y0, x1, y1, ...].
 */
enum class Band { EMPTY, SCRIBBLE, OFF_LINE, MISSING_SHIROREKHA, INCOMPLETE, ACCEPTED }

class Verdict(
    val band: Band,
    val coverage: Float = 0f,
    val spill: Float = 0f,
    val lenRatio: Float = 0f,
    /** Rows [y0, y1) of the top bar (shirorekha), in mask pixels. */
    val shiroBand: IntArray? = null,
    /** 0 = top-left, 1 = top-right, 2 = bottom-left, 3 = bottom-right, -1 = none. */
    val weakestRegion: Int = -1
)

object Classifier {
    private const val COVER_PASS = 0.70f
    private const val SPILL_FAIL = 0.25f
    private const val REGION_FLOOR = 0.55f
    private const val SHIRO_PASS = 0.60f
    private const val SCRIBBLE_RATIO = 2.5f
    private const val MIN_REGION_SHARE = 0.08f
    private const val INF = 1e9f

    fun chamferDT(mask: ByteArray, w: Int, h: Int): FloatArray {
        val d = FloatArray(w * h) { if (mask[it].toInt() != 0) 0f else INF }
        for (y in 0 until h) for (x in 0 until w) {
            val k = y * w + x
            var b = d[k]
            if (y > 0) {
                if (x > 0) b = min(b, d[k - w - 1] + 1.4142f)
                b = min(b, d[k - w] + 1f)
                if (x < w - 1) b = min(b, d[k - w + 1] + 1.4142f)
            }
            if (x > 0) b = min(b, d[k - 1] + 1f)
            d[k] = b
        }
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val k = y * w + x
            var b = d[k]
            if (y < h - 1) {
                if (x < w - 1) b = min(b, d[k + w + 1] + 1.4142f)
                b = min(b, d[k + w] + 1f)
                if (x > 0) b = min(b, d[k + w - 1] + 1.4142f)
            }
            if (x < w - 1) b = min(b, d[k + 1] + 1f)
            d[k] = b
        }
        return d
    }

    private fun innerDT(mask: ByteArray, w: Int, h: Int): FloatArray {
        val inv = ByteArray(mask.size) { if (mask[it].toInt() != 0) 0 else 1 }
        return chamferDT(inv, w, h)
    }

    /** Pen width = 2x the 90th-percentile inner distance-to-edge. Every tolerance derives from it. */
    fun strokeWidth(mask: ByteArray, w: Int, h: Int): Float {
        var n = 0
        for (v in mask) if (v.toInt() != 0) n++
        if (n == 0) return 8f
        val inner = innerDT(mask, w, h)
        val vals = FloatArray(n)
        var j = 0
        for (i in mask.indices) if (mask[i].toInt() != 0) vals[j++] = inner[i]
        vals.sort()
        return 2f * vals[floor(0.90 * (vals.size - 1)).toInt()]
    }

    /** Topmost band whose ink spans most of the glyph width. Detected, not hand-authored per letter. */
    fun findShirorekha(mask: ByteArray, w: Int, h: Int, sw: Float): IntArray? {
        var minX = w
        var maxX = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (mask[y * w + x].toInt() != 0) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
            }
        }
        if (maxX < 0) return null
        val width = max(1, maxX - minX + 1)
        for (y in 0 until h) {
            var run = 0
            for (x in minX..maxX) if (mask[y * w + x].toInt() != 0) run++
            if (run > 0.70f * width) {
                return intArrayOf(y, min(h, y + max(3, (sw * 1.6f).roundToInt())))
            }
            if (run > 0 && y > h * 0.35f) return null
        }
        return null
    }

    fun classify(mask: ByteArray, w: Int, h: Int, strokes: List<FloatArray>): Verdict {
        var glyphPx = 0
        for (v in mask) if (v.toInt() != 0) glyphPx++
        if (glyphPx == 0 || strokes.isEmpty()) return Verdict(Band.EMPTY)

        val sw = strokeWidth(mask, w, h)
        val distToGlyph = chamferDT(mask, w, h)
        // Asymmetric on purpose: forgiving about missing the line, strict about drawing where no letter is.
        val coverTol = 1.75f * sw
        val spillTol = 1.00f * sw

        val drawn = ByteArray(w * h)
        var pts = 0
        var outside = 0
        var drawnLen = 0.0
        for (s in strokes) {
            val n = s.size / 2
            for (i in 0 until n - 1) {
                val x0 = s[2 * i]
                val y0 = s[2 * i + 1]
                val x1 = s[2 * i + 2]
                val y1 = s[2 * i + 3]
                drawnLen += hypot((x1 - x0).toDouble(), (y1 - y0).toDouble())
                val steps = max(2, floor(max(abs(x1 - x0), abs(y1 - y0)).toDouble()).toInt() + 1)
                for (q in 0 until steps) {
                    val t = q.toFloat() / (steps - 1)
                    val x = (x0 + (x1 - x0) * t).roundToInt()
                    val y = (y0 + (y1 - y0) * t).roundToInt()
                    if (x < 0 || y < 0 || x >= w || y >= h) continue
                    val k = y * w + x
                    drawn[k] = 1
                    pts++
                    if (distToGlyph[k] > spillTol) outside++
                }
            }
        }
        if (pts == 0) return Verdict(Band.EMPTY)

        val distToDrawn = chamferDT(drawn, w, h)
        var covered = 0
        for (i in mask.indices) if (mask[i].toInt() != 0 && distToDrawn[i] <= coverTol) covered++
        val coverage = covered.toFloat() / glyphPx
        val spill = outside.toFloat() / pts

        // Scribble gate: a traced letter draws about its skeleton length; a scribble draws several times that.
        val lenRatio = (drawnLen / max(1f, glyphPx / max(1f, sw))).toFloat()

        // Per-region floor: a global mean cannot see a missing limb.
        var weakest = 1f
        var weakestIdx = -1
        for (gy in 0 until 2) for (gx in 0 until 2) {
            var sub = 0
            var hit = 0
            for (y in ((gy * h) shr 1) until (((gy + 1) * h) shr 1))
                for (x in ((gx * w) shr 1) until (((gx + 1) * w) shr 1)) {
                    val k = y * w + x
                    if (mask[k].toInt() != 0) {
                        sub++
                        if (distToDrawn[k] <= coverTol) hit++
                    }
                }
            if (sub < glyphPx * MIN_REGION_SHARE) continue
            val c = hit.toFloat() / sub
            if (c < weakest) {
                weakest = c
                weakestIdx = gy * 2 + gx
            }
        }
        val regionOk = weakest >= REGION_FLOOR

        var shiroOk: Boolean? = null
        val sb = findShirorekha(mask, w, h, sw)
        if (sb != null) {
            var bar = 0
            var hit = 0
            for (y in sb[0] until sb[1]) for (x in 0 until w) {
                val k = y * w + x
                if (mask[k].toInt() != 0) {
                    bar++
                    if (distToDrawn[k] <= coverTol) hit++
                }
            }
            if (bar > 0) shiroOk = (hit.toFloat() / bar) >= SHIRO_PASS
        }

        // Order matters: reject scribbles before praising coverage, check structure before the global average.
        val band = when {
            lenRatio > SCRIBBLE_RATIO -> Band.SCRIBBLE
            spill > SPILL_FAIL -> Band.OFF_LINE
            shiroOk == false -> Band.MISSING_SHIROREKHA
            coverage < COVER_PASS || !regionOk -> Band.INCOMPLETE
            else -> Band.ACCEPTED
        }
        return Verdict(
            band, coverage, spill, lenRatio, sb,
            if (weakest >= 0.75f) -1 else weakestIdx
        )
    }

    /**
     * For "write it yourself" modes there is no ghost to follow, so the child may write at any size or
     * position. Scale and shift the drawing so its bounding box matches the letter's bounding box,
     * then grade it exactly like a trace.
     */
    fun fitToGlyph(mask: ByteArray, w: Int, h: Int, strokes: List<FloatArray>): List<FloatArray> {
        var gx0 = w; var gx1 = -1; var gy0 = h; var gy1 = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (mask[y * w + x].toInt() != 0) {
                if (x < gx0) gx0 = x
                if (x > gx1) gx1 = x
                if (y < gy0) gy0 = y
                if (y > gy1) gy1 = y
            }
        }
        if (gx1 < 0 || strokes.isEmpty()) return strokes
        var dx0 = Float.MAX_VALUE; var dx1 = -Float.MAX_VALUE
        var dy0 = Float.MAX_VALUE; var dy1 = -Float.MAX_VALUE
        for (s in strokes) {
            var i = 0
            while (i + 1 < s.size) {
                dx0 = min(dx0, s[i]); dx1 = max(dx1, s[i])
                dy0 = min(dy0, s[i + 1]); dy1 = max(dy1, s[i + 1])
                i += 2
            }
        }
        val gw = (gx1 - gx0 + 1).toFloat()
        val gh = (gy1 - gy0 + 1).toFloat()
        // A very small drawing is only enlarged up to 4x, so a dot cannot pass as a letter.
        val sx = gw / max(dx1 - dx0, 0.25f * gw)
        val sy = gh / max(dy1 - dy0, 0.25f * gh)
        val gcx = (gx0 + gx1) / 2f
        val gcy = (gy0 + gy1) / 2f
        val dcx = (dx0 + dx1) / 2f
        val dcy = (dy0 + dy1) / 2f
        return strokes.map { s ->
            FloatArray(s.size) { i ->
                if (i % 2 == 0) gcx + (s[i] - dcx) * sx else gcy + (s[i] - dcy) * sy
            }
        }
    }
}
