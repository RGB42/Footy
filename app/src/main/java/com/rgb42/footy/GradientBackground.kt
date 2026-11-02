package com.rgb42.footy

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Hintergrund: Blau oben → Hellblau → Gelb unten, mit hellem Schein hinter dem Knopf. */
class GradientBackground : Drawable() {
    private val paint = Paint()
    private val glow = Paint()

    private val colors = intArrayOf(
        Color.rgb(0x3E, 0x55, 0x92), Color.rgb(0x4A, 0x80, 0xBC), Color.rgb(0x55, 0xAA, 0xDB),
        Color.rgb(0x7D, 0xD0, 0xF4), Color.rgb(0xC6, 0xED, 0xFB), Color.rgb(0xF2, 0xF8, 0xEE),
        Color.rgb(0xFA, 0xE7, 0xA0), Color.rgb(0xF4, 0xCD, 0x48), Color.rgb(0xE3, 0xB2, 0x1F)
    )
    private val stops = floatArrayOf(0f, 0.25f, 0.45f, 0.6f, 0.72f, 0.78f, 0.84f, 0.9f, 1f)

    override fun onBoundsChange(b: Rect) {
        paint.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), colors, stops, Shader.TileMode.CLAMP)
        val r = b.width() * 0.5f
        glow.shader = RadialGradient(
            b.exactCenterX(), b.bottom - b.height() * 0.035f, r,
            intArrayOf(Color.argb(0xC0, 0xFF, 0xF8, 0xD8), Color.argb(0, 0xFF, 0xF8, 0xD8)),
            null, Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawRect(b, paint)
        canvas.drawRect(b, glow)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
