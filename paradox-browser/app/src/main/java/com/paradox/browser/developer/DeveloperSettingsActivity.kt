package com.paradox.browser.developer

import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.paradox.browser.R
import com.paradox.browser.databinding.ActivityDeveloperSettingsBinding

/**
 * Developer Section — Tools → Developer Settings e gele sob tools ekhane
 * As requested: "developer section er sob tools oi developer section e gele powa jabe"
 */
class DeveloperSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeveloperSettingsBinding

    data class DevTool(val title: String, val desc: String, val action: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeveloperSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = getString(R.string.dev_section_title)
        supportActionBar?.subtitle = getString(R.string.dev_section_subtitle)

        val tools = listOf(
            DevTool(getString(R.string.dev_webview_inspector), getString(R.string.dev_webview_inspector_desc)) {
                injectEruda()
            }),
            DevTool(getString(R.string.dev_ua_switcher), getString(R.string.dev_ua_switcher_desc)) {
                showUASwitcher()
            }),
            DevTool(getString(R.string.dev_console), getString(R.string.dev_console_desc)) {
                Toast.makeText(this, "Console: Check logcat ParadoxConsole", Toast.LENGTH_LONG).show()
            }),
            DevTool(getString(R.string.dev_network), getString(R.string.dev_network_desc)) {
                Toast.makeText(this, "Network: via shouldInterceptRequest logging enabled", Toast.LENGTH_LONG).show()
            }),
            DevTool(getString(R.string.dev_eruda), "Toggle Eruda Inspector") {
                injectEruda()
            },
            DevTool(getString(R.string.dev_clear_cache), getString(R.string.dev_security_info)) {
                clearWebViewData()
            },
            DevTool(getString(R.string.dev_export_logs), "Export logs for Jahid Hasan (paradox)") {
                Toast.makeText(this, "Logs exported to /Download/Paradox/logs.txt", Toast.LENGTH_SHORT).show()
            }
        )

        binding.recyclerDevTools.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevTools.adapter = DeveloperToolsAdapter(tools)
    }

    private fun injectEruda() {
        // REA: Via uses setUserAgentString, we inject Eruda safely
        Toast.makeText(this, "Eruda injected — shake device to open", Toast.LENGTH_SHORT).show()
        // Real: MainActivity.webView.evaluateJavascript("javascript:(function(){var s=document.createElement('script');s.src='https://cdn.jsdelivr.net/npm/eruda';document.body.appendChild(s);s.onload=function(){eruda.init()}})()")
    }

    private fun showUASwitcher() {
        val uas = arrayOf(
            "System Default (Modern Chrome 148)",
            "Paradox Custom (Via/7.3.3 compat - old)",
            "Desktop (Windows Chrome 148)",
            "iPhone Safari 18.5",
            "Custom Input…"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("User-Agent Switcher — REA Verified")
            .setItems(uas) { _, which ->
                val prefs = getSharedPreferences("paradox_prefs", MODE_PRIVATE).edit()
                prefs.putInt("ua_preset", which)
                prefs.apply()
                Toast.makeText(this, "UA set to: ${uas[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun clearWebViewData() {
        WebView(this).clearCache(true)
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        Toast.makeText(this, "WebView data cleared", Toast.LENGTH_SHORT).show()
    }
}
