package com.paradox.gamingtools.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Deliberately GPU/CPU hungry canvas scene for the Lag Lab.
 * More objects = more draw calls = a measurable effect on weak devices.
 */
class StressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val palette = intArrayOf(
        Color.parseColor("#4C8DFF"),
        Color.parseColor("#22C55E"),
        Color.parseColor("#F59E0B"),
        Color.parseColor("#EF4444"),
        Color.parseColor("#A855F7")
    )

    private val objectPaints = palette.map {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = it
            style = Paint.Style.FILL
        }
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#182028")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val backgroundColor = Color.parseColor("#0B0E11")

    private var phase = 0f

    var objectCount: Int = 24
        set(value) {
            field = value.coerceIn(1, 80)
            invalidate()
        }

    fun advance() {
        phase += 0.012f
        if (phase > 1f) phase -= 1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = this.width.toFloat()
        val height = this.height.toFloat()
        if (width <= 0f || height <= 0f) return

        canvas.drawColor(backgroundColor)

        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height, gridPaint)
            x += 48f
        }
        var y = 0f
        while (y <= height) {
            canvas.drawLine(0f, y, width, y, gridPaint)
            y += 48f
        }

        val count = objectCount
        for (index in 0 until count) {
            val paint = objectPaints[index % objectPaints.size]
            val angle = phase * 6.28318f + index * 0.7f
            val speedA = 1f + (index % 3) * 0.25f
            val speedB = 1f + (index % 4) * 0.2f
            val cx = width / 2f + (width * 0.36f) * cos(angle * speedA)
            val cy = height / 2f + (height * 0.36f) * sin(angle * speedB)
            val radius = 8f + (index % 5) * 5f
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }
}
