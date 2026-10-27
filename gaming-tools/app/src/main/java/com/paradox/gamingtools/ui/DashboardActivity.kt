package com.paradox.gamingtools.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.paradox.gamingtools.R
import com.paradox.gamingtools.core.BoostEngine
import com.paradox.gamingtools.core.BoostResult
import com.paradox.gamingtools.core.DeviceMetrics
import com.paradox.gamingtools.core.Fmt
import com.paradox.gamingtools.core.Prefs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var metrics: DeviceMetrics
    private lateinit var engine: BoostEngine

    private lateinit var ringRam: RingView
    private lateinit var txtRamUsed: TextView
    private lateinit var txtRamTotal: TextView
    private lateinit var txtRamStatus: TextView
    private lateinit var txtCpu: TextView
    private lateinit var txtTemp: TextView
    private lateinit var txtTempStatus: TextView
    private lateinit var txtStorage: TextView
    private lateinit var btnBoost: MaterialButton
    private lateinit var txtBoostHint: TextView
    private lateinit var txtStats: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var boosting = false

    private val ticker = object : Runnable {
        override fun run() {
            refreshMetrics()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        prefs = Prefs(this)
        metrics = DeviceMetrics(this)
        engine = BoostEngine(this, prefs)

        ringRam = findViewById(R.id.ringRam)
        txtRamUsed = findViewById(R.id.txtRamUsed)
        txtRamTotal = findViewById(R.id.txtRamTotal)
        txtRamStatus = findViewById(R.id.txtRamStatus)
        txtCpu = findViewById(R.id.txtCpu)
        txtTemp = findViewById(R.id.txtTemp)
        txtTempStatus = findViewById(R.id.txtTempStatus)
        txtStorage = findViewById(R.id.txtStorage)
        btnBoost = findViewById(R.id.btnBoost)
        txtBoostHint = findViewById(R.id.txtBoostHint)
        txtStats = findViewById(R.id.txtStats)

        ringRam.labelText = getString(R.string.label_ram)

        btnBoost.setOnClickListener { runBoost() }
        findViewById<MaterialCardView>(R.id.cardLagTest).setOnClickListener {
            startActivity(Intent(this, LagTestActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardGames).setOnClickListener {
            startActivity(Intent(this, GamesActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardCleaner).setOnClickListener {
            startActivity(Intent(this, CleanerActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        updateStats()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun refreshMetrics() {
        val ram = metrics.ram()
        val ratio = if (ram.totalBytes > 0L) ram.usedBytes.toFloat() / ram.totalBytes else 0f
        ringRam.progress = ratio
        ringRam.centerText = getString(R.string.percent_value, ram.usedPercent)
        ringRam.ringColor = getColor(
            when {
                ram.usedPercent >= 85 -> R.color.paradox_red
                ram.usedPercent >= 70 -> R.color.paradox_amber
                else -> R.color.paradox_green
            }
        )
        txtRamUsed.text = Fmt.bytes(ram.usedBytes)
        txtRamTotal.text = getString(R.string.ram_of_total, Fmt.bytes(ram.totalBytes))

        val stressed = ram.lowMemory || ram.usedPercent >= 85
        txtRamStatus.text = getString(
            if (stressed) R.string.ram_status_low else R.string.ram_status_ok
        )
        txtRamStatus.setTextColor(
            getColor(if (stressed) R.color.paradox_amber else R.color.paradox_green)
        )

        val cpu = metrics.cpuPercent()
        txtCpu.text = if (cpu == null) getString(R.string.value_dash) else "$cpu%"

        val battery = metrics.battery()
        val temperature = metrics.thermalCelsius() ?: battery.temperatureC
        txtTemp.text = if (temperature <= 0f) {
            getString(R.string.value_dash)
        } else {
            "${Fmt.oneDecimal(temperature)}°C"
        }
        txtTempStatus.text = metrics.thermalStatusLabel()

        val storage = metrics.storage()
        txtStorage.text = Fmt.bytes(storage.freeBytes)
    }

    private fun runBoost() {
        if (boosting) return
        boosting = true
        btnBoost.isEnabled = false
        btnBoost.text = getString(R.string.boosting)

        lifecycleScope.launch {
            val result = engine.boost { step -> txtBoostHint.text = step }
            boosting = false
            btnBoost.isEnabled = true
            btnBoost.text = getString(R.string.boost_now)
            txtBoostHint.text = getString(R.string.boost_hint)
            refreshMetrics()
            updateStats()
            showResult(result)
        }
    }

    private fun showResult(result: BoostResult) {
        val message = getString(
            R.string.boost_done_body,
            Fmt.bytes(result.freedBytes),
            result.processesTrimmed,
            Fmt.bytes(result.cacheClearedBytes),
            getString(if (result.dndEnabled) R.string.option_yes else R.string.option_no),
            result.durationMs
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.boost_done_title)
            .setMessage(message)
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    private fun updateStats() {
        val count = prefs.boostCount
        if (count == 0) {
            txtStats.text = getString(R.string.stats_none)
            return
        }
        val stamp = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
            .format(Date(prefs.lastBoostMillis))
        txtStats.text = getString(
            R.string.stats_line,
            count,
            Fmt.bytes(prefs.totalFreedBytes),
            stamp
        )
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 2000L
    }
}
