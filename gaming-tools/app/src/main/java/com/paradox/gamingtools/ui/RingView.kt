package com.paradox.gamingtools.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Dependency-free circular gauge. Used for the RAM meter on the dashboard.
 * All Paint objects are allocated once — nothing is created inside onDraw.
 */
class RingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#1E252E")
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#4C8DFF")
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8EDF2")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A97A6")
        textAlign = Paint.Align.CENTER
    }

    private val arcBounds = RectF()

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var ringColor: Int = Color.parseColor("#4C8DFF")
        set(value) {
            field = value
            arcPaint.color = value
            invalidate()
        }

    var centerText: String = "0%"
        set(value) {
            field = value
            invalidate()
        }

    var labelText: String = ""
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = this.width.toFloat()
        val height = this.height.toFloat()
        if (width <= 0f || height <= 0f) return

        val size = min(width, height)
        val stroke = size * 0.085f
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke

        val inset = stroke / 2f + 2f
        arcBounds.set(inset, inset, width - inset, height - inset)

        canvas.drawArc(arcBounds, 0f, 360f, false, trackPaint)
        canvas.drawArc(arcBounds, -90f, 360f * progress, false, arcPaint)

        valuePaint.textSize = size * 0.21f
        labelPaint.textSize = size * 0.085f

        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawText(centerText, centerX, centerY + valuePaint.textSize * 0.34f, valuePaint)
        if (labelText.isNotEmpty()) {
            canvas.drawText(
                labelText,
                centerX,
                centerY + valuePaint.textSize * 0.34f + labelPaint.textSize * 1.7f,
                labelPaint
            )
        }
    }
}
