package com.paradox.gamingtools.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.paradox.gamingtools.R
import com.paradox.gamingtools.core.Fmt
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Real frame-timing measurement: the Android frame clock calls doFrame() once per
 * composited frame. The gap between two callbacks IS the frame time the user feels.
 */
class LagTestActivity : AppCompatActivity() {

    private lateinit var stressView: StressView
    private lateinit var txtLive: TextView
    private lateinit var txtAvg: TextView
    private lateinit var txtWorst: TextView
    private lateinit var txtJank: TextView
    private lateinit var txtVerdict: TextView
    private lateinit var txtDetail: TextView
    private lateinit var txtLoad: TextView
    private lateinit var txtTarget: TextView
    private lateinit var seekLoad: SeekBar
    private lateinit var btnTest: MaterialButton

    private val handler = Handler(Looper.getMainLooper())
    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }

    private var running = false
    private var lastFrameNanos = 0L
    private var frames = 0
    private var sumMs = 0.0
    private var worstMs = 0f
    private var jankFrames = 0
    private val deltas = ArrayList<Float>(4096)

    private var refreshHz = 60f
    private var targetFrameMs = 16.67f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (lastFrameNanos != 0L) {
                val deltaMs = (frameTimeNanos - lastFrameNanos) / 1_000_000f
                record(deltaMs)
            }
            lastFrameNanos = frameTimeNanos
            stressView.advance()
            choreographer.postFrameCallback(this)
        }
    }

    private val finishTask = Runnable { stopTest(showResult = true) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lag_test)

        stressView = findViewById(R.id.stressView)
        txtLive = findViewById(R.id.txtLive)
        txtAvg = findViewById(R.id.txtAvg)
        txtWorst = findViewById(R.id.txtWorst)
        txtJank = findViewById(R.id.txtJank)
        txtVerdict = findViewById(R.id.txtVerdict)
        txtDetail = findViewById(R.id.txtDetail)
        txtLoad = findViewById(R.id.txtLoad)
        txtTarget = findViewById(R.id.txtTarget)
        seekLoad = findViewById(R.id.seekLoad)
        btnTest = findViewById(R.id.btnTest)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        @Suppress("DEPRECATION")
        val detected = windowManager.defaultDisplay.refreshRate
        refreshHz = if (detected in 20f..200f) detected else 60f
        targetFrameMs = 1000f / refreshHz
        txtTarget.text = getString(R.string.lag_target, Fmt.whole(refreshHz))

        stressView.objectCount = seekLoad.progress + 1
        txtLoad.text = getString(R.string.lag_load_label, stressView.objectCount)
        seekLoad.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                stressView.objectCount = progress + 1
                txtLoad.text = getString(R.string.lag_load_label, stressView.objectCount)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })

        btnTest.setOnClickListener {
            if (running) stopTest(showResult = true) else startTest()
        }
    }

    private fun startTest() {
        resetCounters()
        running = true
        lastFrameNanos = 0L
        btnTest.text = getString(R.string.lag_stop)
        txtVerdict.setText(R.string.lag_verdict_running)
        txtVerdict.setTextColor(getColor(R.color.paradox_text))
        txtDetail.setText(R.string.lag_running_detail)
        choreographer.postFrameCallback(frameCallback)
        handler.postDelayed(finishTask, TEST_DURATION_MS)
    }

    private fun stopTest(showResult: Boolean) {
        if (!running) return
        running = false
        choreographer.removeFrameCallback(frameCallback)
        handler.removeCallbacks(finishTask)
        btnTest.text = getString(R.string.lag_start)
        if (showResult) showResult()
    }

    private fun resetCounters() {
        frames = 0
        sumMs = 0.0
        worstMs = 0f
        jankFrames = 0
        deltas.clear()
        txtLive.text = getString(R.string.value_dash)
        txtAvg.text = getString(R.string.value_dash)
        txtWorst.text = getString(R.string.value_dash)
        txtJank.text = getString(R.string.value_dash)
    }

    private fun record(deltaMs: Float) {
        if (deltaMs <= 0f || deltaMs > 2000f) return
        frames++
        sumMs += deltaMs
        if (deltaMs > worstMs) worstMs = deltaMs
        if (deltaMs > targetFrameMs * 1.5f) jankFrames++
        if (deltas.size < 4000) deltas.add(deltaMs)
        if (frames % 5 == 0) {
            val averageFps = if (sumMs > 0.0) (frames * 1000.0 / sumMs).toFloat() else 0f
            updateLive(1000f / deltaMs, averageFps)
        }
    }

    private fun updateLive(liveFps: Float, averageFps: Float) {
        txtLive.text = Fmt.whole(liveFps)
        txtAvg.text = Fmt.whole(averageFps)
        txtWorst.text = getString(R.string.ms_value, Fmt.oneDecimal(worstMs))
        val jankPercent = if (frames > 0) jankFrames * 100f / frames else 0f
        txtJank.text = getString(R.string.percent_value, jankPercent.roundToInt())
    }

    private fun showResult() {
        if (frames < 10 || sumMs <= 0.0) {
            txtVerdict.setText(R.string.lag_verdict_idle)
            return
        }

        val averageFps = (frames * 1000.0 / sumMs).toFloat()
        val jankPercent = jankFrames * 100f / frames

        val sorted = deltas.sortedDescending()
        val onePercentCount = max(1, sorted.size / 100)
        var worstSum = 0.0
        for (index in 0 until onePercentCount) worstSum += sorted[index]
        val onePercentLow = if (worstSum > 0.0) {
            (1000.0 / (worstSum / onePercentCount)).toFloat()
        } else {
            0f
        }

        val score = computeScore(averageFps, jankPercent)
        val smooth = averageFps >= refreshHz * 0.9f && jankPercent < 5f
        val playable = averageFps >= refreshHz * 0.6f

        val verdictText = when {
            smooth -> R.string.lag_verdict_smooth
            playable -> R.string.lag_verdict_ok
            else -> R.string.lag_verdict_lag
        }
        val verdictColor = when {
            smooth -> R.color.paradox_green
            playable -> R.color.paradox_amber
            else -> R.color.paradox_red
        }

        txtVerdict.setText(verdictText)
        txtVerdict.setTextColor(getColor(verdictColor))
        txtAvg.text = Fmt.whole(averageFps)
        txtWorst.text = getString(R.string.ms_value, Fmt.oneDecimal(worstMs))
        txtJank.text = getString(R.string.percent_value, jankPercent.roundToInt())
        txtDetail.text = getString(
            R.string.lag_detail,
            Fmt.oneDecimal(onePercentLow),
            frames,
            score
        )
    }

    private fun computeScore(averageFps: Float, jankPercent: Float): Int {
        val fpsPart = (averageFps / max(refreshHz, 1f)).coerceIn(0f, 1f)
        val jankPart = (1f - jankPercent / 100f).coerceIn(0f, 1f)
        return (fpsPart * 70f + jankPart * 30f).roundToInt().coerceIn(0, 100)
    }

    override fun onPause() {
        super.onPause()
        stopTest(showResult = false)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private companion object {
        const val TEST_DURATION_MS = 15_000L
    }
}
