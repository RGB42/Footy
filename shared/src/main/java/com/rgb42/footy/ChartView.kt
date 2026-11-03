package com.rgb42.footy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Balken- (Woche/Monat) bzw. Liniendiagramm (Tag), ohne Fremdbibliothek. */
class ChartView(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    enum class Style { LINE, BARS }

    private var style = Style.BARS
    private var values = FloatArray(0)
    private var visibleCount = 0
    private var xLabels: List<Pair<Int, String>> = emptyList()
    private var highlight: String? = null
    private var topValue = 6f
    private var step = 1f
    private var decimals = true

    /** Kleinere Schrift und Ränder (Uhr). */
    var compact = false
        set(v) {
            field = v
            val f = if (v) 0.62f else 1f
            yLabelPaint.textSize = sp(15f) * f
            xLabelPaint.textSize = sp(15f) * f
            hiText.textSize = sp(16f) * f
            invalidate()
        }
    private val k get() = if (compact) 0.6f else 1f

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val plotBg = Paint().apply { color = Color.argb(0x3A, 255, 255, 255) }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0xB0, 0x4F, 0xBD, 0xEA); strokeWidth = dp(1.6f)
    }
    private val yLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x84, 0x94, 0xA8); textSize = sp(15f)
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT
    }
    private val xLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xAE, 0xA8, 0x8A); textSize = sp(15f)
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(0xE0, 255, 255, 255) }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = dp(2f); strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(0x40, 255, 255, 255) }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val hiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xFF, 0xD8, 0x3A) }
    private val hiText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xFF, 0xD8, 0x3A); textSize = sp(16f)
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val fillPath = Path()

    /**
     * @param values  Werte (LINE: Stunden 0..24, BARS: ein Wert pro Tag)
     * @param visible Anzahl gültiger Punkte (LINE), Rest wird nicht gezeichnet
     * @param xLabels Paare (Index, Text) für die X-Achse
     * @param minTop  Mindesthöhe der Y-Achse
     * @param highlight Text über dem letzten Punkt (nur LINE)
     * @param decimals Achse mit einer Nachkommastelle beschriften
     */
    fun setData(
        style: Style, values: FloatArray, visible: Int, xLabels: List<Pair<Int, String>>,
        minTop: Float, highlight: String?, decimals: Boolean = false
    ) {
        this.style = style
        this.values = values
        this.visibleCount = visible
        this.xLabels = xLabels
        this.highlight = highlight
        var maxV = 0f
        for (i in 0 until min(visible, values.size)) maxV = max(maxV, values[i])
        step = niceStep(max(maxV * 1.1f, minTop) / 6f)
        topValue = step * 6
        this.decimals = decimals || step < 1f || step % 1f != 0f
        invalidate()
    }

    private fun niceStep(raw: Float): Float {
        if (raw <= 0f) return 1f
        val exp = floor(log10(raw.toDouble()))
        val base = 10.0.pow(exp)
        val f = raw / base
        val nf = when {
            f <= 1.0 -> 1.0
            f <= 2.0 -> 2.0
            f <= 2.5 -> 2.5
            f <= 5.0 -> 5.0
            else -> 10.0
        }
        return (nf * base).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        var labelW = 0f
        for (i in 1..6) labelW = max(labelW, yLabelPaint.measureText(Fmt.axis(step * i, decimals)))
        val left = max(labelW + dp(10f * k), width * (if (compact) 0.1f else 0.15f))
        val right = width - dp(18f * k)
        val top = dp(14f * k)
        val bottom = height - dp(30f * k)
        if (bottom - top < dp(30f) || right <= left) return
        val plotH = bottom - top
        val plotW = right - left

        canvas.drawRect(left, top, right, bottom, plotBg)
        val textOffset = (yLabelPaint.descent() - yLabelPaint.ascent()) / 2 - yLabelPaint.descent()
        for (i in 1..6) {
            val y = bottom - plotH * i / 6f
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(Fmt.axis(step * i, decimals), left - dp(6f), y + textOffset, yLabelPaint)
        }
        val xLabelY = bottom + dp(8f * k) - xLabelPaint.ascent()
        fun yOf(v: Float) = bottom - plotH * (v / topValue).coerceIn(0f, 1f)

        if (style == Style.BARS) {
            val n = values.size
            if (n == 0) return
            val slot = plotW / n
            val bw = slot * 0.78f
            for (i in 0 until n) {
                val v = values[i]
                if (v <= 0f) continue
                val cx = left + slot * (i + 0.5f)
                canvas.drawRect(cx - bw / 2, yOf(v), cx + bw / 2, bottom, barPaint)
            }
            for ((i, t) in xLabels) canvas.drawText(t, left + slot * (i + 0.5f), xLabelY, xLabelPaint)
        } else {
            val n = values.size
            if (n < 2) return
            val dx = plotW / (n - 1)
            val count = min(visibleCount, n)
            if (count > 0) {
                path.reset(); fillPath.reset()
                for (i in 0 until count) {
                    val x = left + dx * i
                    val y = yOf(values[i])
                    if (i == 0) { path.moveTo(x, y); fillPath.moveTo(x, bottom); fillPath.lineTo(x, y) }
                    else { path.lineTo(x, y); fillPath.lineTo(x, y) }
                }
                val lastX = left + dx * (count - 1)
                val lastY = yOf(values[count - 1])
                fillPath.lineTo(lastX, bottom); fillPath.close()
                canvas.drawPath(fillPath, fillPaint)
                canvas.drawPath(path, linePaint)
                canvas.drawLine(lastX, lastY, lastX, bottom, linePaint)
                for (i in 0 until count - 1) canvas.drawCircle(left + dx * i, yOf(values[i]), dp(4.5f * k), pointPaint)
                canvas.drawCircle(lastX, lastY, dp(5f * k), hiPaint)
                highlight?.let {
                    val half = hiText.measureText(it) / 2
                    val tx = lastX.coerceIn(half, width - half)
                    val ty = if (lastY - dp(12f) + hiText.ascent() < 0) lastY + dp(12f) - hiText.ascent()
                             else lastY - dp(12f)
                    canvas.drawText(it, tx, ty, hiText)
                }
            }
            for ((i, t) in xLabels) canvas.drawText(t, left + dx * i, xLabelY, xLabelPaint)
        }
    }
}
