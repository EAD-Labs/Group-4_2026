package com.aksharvel.tactile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.min

/**
 * Draws a letter the same way for the faint "ghost" the child sees and for the mask the checker
 * grades against, so the two can never drift apart.
 */
object Glyph {
    fun paint(argb: Int = Color.WHITE): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = argb
        textAlign = Paint.Align.LEFT
    }

    /** Draws [letter] centred in a w x h box, its ink filling about 80% of the box. */
    fun draw(canvas: Canvas, letter: String, w: Float, h: Float, p: Paint) {
        val r = Rect()
        p.textSize = 100f
        p.getTextBounds(letter, 0, letter.length, r)
        if (r.width() <= 0 || r.height() <= 0) return
        p.textSize = 100f * min(0.8f * w / r.width(), 0.8f * h / r.height())
        p.getTextBounds(letter, 0, letter.length, r)
        val x = (w - r.width()) / 2f - r.left
        val y = (h - r.height()) / 2f - r.top
        canvas.drawText(letter, x, y, p)
    }

    /** 1 where the letter has ink, 0 elsewhere. */
    fun mask(letter: String, w: Int, h: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), letter, w.toFloat(), h.toFloat(), paint(Color.WHITE))
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        return ByteArray(w * h) { if ((px[it] ushr 24) > 60) 1 else 0 }
    }
}
