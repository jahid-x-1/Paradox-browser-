package com.paradox.gamingtools.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.paradox.gamingtools.R
import com.paradox.gamingtools.core.AppCacheScanner
import com.paradox.gamingtools.core.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CleanerActivity : AppCompatActivity() {

    private lateinit var scanner: AppCacheScanner
    private lateinit var adapter: CacheAdapter
    private lateinit var txtOwnCache: TextView
    private lateinit var txtUsageStatus: TextView
    private lateinit var txtScanState: TextView
    private lateinit var btnGrant: MaterialButton
    private lateinit var btnClean: MaterialButton
    private lateinit var recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cleaner)

        scanner = AppCacheScanner(this)

        txtOwnCache = findViewById(R.id.txtOwnCache)
        txtUsageStatus = findViewById(R.id.txtUsageStatus)
        txtScanState = findViewById(R.id.txtScanState)
        btnGrant = findViewById(R.id.btnGrant)
        btnClean = findViewById(R.id.btnClean)
        recycler = findViewById(R.id.recyclerCache)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        adapter = CacheAdapter { info -> scanner.openAppInfo(info.packageName) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        btnGrant.setOnClickListener { scanner.openUsageAccessSettings() }

        btnClean.setOnClickListener {
            btnClean.isEnabled = false
            lifecycleScope.launch {
                val freed = withContext(Dispatchers.IO) { scanner.clearOwnCache() }
                btnClean.isEnabled = true
                Toast.makeText(
                    this@CleanerActivity,
                    getString(R.string.toast_cleaned, Fmt.bytes(freed)),
                    Toast.LENGTH_SHORT
                ).show()
                refresh()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val own = withContext(Dispatchers.IO) { scanner.ownCacheBytes() }
            txtOwnCache.text = Fmt.bytes(own)

            val granted = scanner.hasUsageAccess()
            txtUsageStatus.setText(
                if (granted) R.string.cleaner_usage_ok else R.string.cleaner_usage_missing
            )
            btnGrant.visibility = if (granted) View.GONE else View.VISIBLE

            if (!granted) {
                adapter.submit(emptyList())
                txtScanState.visibility = View.VISIBLE
                txtScanState.setText(R.string.cleaner_empty)
                return@launch
            }

            txtScanState.visibility = View.VISIBLE
            txtScanState.setText(R.string.cleaner_scanning)
            val apps = withContext(Dispatchers.IO) { scanner.scan() }
            adapter.submit(apps)

            if (apps.isEmpty()) {
                txtScanState.visibility = View.VISIBLE
                txtScanState.setText(R.string.cleaner_empty)
            } else {
                txtScanState.visibility = View.VISIBLE
                txtScanState.text = getString(R.string.cleaner_found, apps.size)
            }
        }
    }
}
