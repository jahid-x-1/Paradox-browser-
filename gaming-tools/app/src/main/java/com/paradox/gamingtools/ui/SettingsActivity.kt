package com.paradox.gamingtools.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.paradox.gamingtools.R
import com.paradox.gamingtools.core.Fmt
import com.paradox.gamingtools.core.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var swKill: SwitchMaterial
    private lateinit var swDnd: SwitchMaterial
    private lateinit var swBoostGame: SwitchMaterial
    private lateinit var btnGrantDnd: MaterialButton
    private lateinit var txtStats: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = Prefs(this)
        swKill = findViewById(R.id.swKill)
        swDnd = findViewById(R.id.swDnd)
        swBoostGame = findViewById(R.id.swBoostGame)
        btnGrantDnd = findViewById(R.id.btnGrantDnd)
        txtStats = findViewById(R.id.txtStats)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        swKill.isChecked = prefs.killBackgroundApps
        swKill.setOnCheckedChangeListener { _, checked -> prefs.killBackgroundApps = checked }

        swDnd.isChecked = prefs.dndDuringBoost
        swDnd.setOnCheckedChangeListener { _, checked -> prefs.dndDuringBoost = checked }

        swBoostGame.isChecked = prefs.boostBeforeGame
        swBoostGame.setOnCheckedChangeListener { _, checked -> prefs.boostBeforeGame = checked }

        btnGrantDnd.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, R.string.dnd_grant, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<MaterialButton>(R.id.btnReset).setOnClickListener {
            prefs.resetStats()
            updateStats()
            Toast.makeText(this, R.string.stats_reset_done, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStats()
        updateDndState()
    }

    private fun updateDndState() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val granted = manager.isNotificationPolicyAccessGranted
        if (granted) {
            btnGrantDnd.visibility = View.GONE
            btnGrantDnd.text = getString(R.string.dnd_granted)
        } else {
            btnGrantDnd.visibility = View.VISIBLE
            btnGrantDnd.text = getString(R.string.dnd_grant)
        }
    }

    private fun updateStats() {
        val count = prefs.boostCount
        txtStats.text = if (count == 0) {
            getString(R.string.stats_none)
        } else {
            val stamp = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                .format(Date(prefs.lastBoostMillis))
            getString(R.string.stats_line, count, Fmt.bytes(prefs.totalFreedBytes), stamp)
        }
    }
}
