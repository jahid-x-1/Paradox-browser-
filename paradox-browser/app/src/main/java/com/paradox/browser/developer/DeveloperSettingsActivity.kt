package com.paradox.browser.developer

import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.paradox.browser.BrowserPrefs
import com.paradox.browser.DevConsole
import com.paradox.browser.R
import com.paradox.browser.UserAgents
import com.paradox.browser.WebViewRegistry
import com.paradox.browser.databinding.ActivityDeveloperSettingsBinding

/**
 * Developer Section — everything here talks to the live WebView, nothing is a placeholder.
 */
class DeveloperSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeveloperSettingsBinding
    private lateinit var prefs: BrowserPrefs

    data class DevTool(val title: String, val desc: String, val action: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeveloperSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = BrowserPrefs(this)

        binding.devToolbar.setNavigationOnClickListener { finish() }

        binding.recyclerDevTools.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevTools.adapter = DeveloperToolsAdapter(buildTools())
    }

    private fun buildTools(): List<DevTool> = listOf(
        DevTool(
            getString(R.string.dev_webview_inspector),
            getString(R.string.dev_webview_inspector_desc)
        ) { injectEruda(forceToggle = true) },

        DevTool(
            getString(R.string.dev_ua_switcher),
            getString(R.string.dev_ua_switcher_desc)
        ) { showUaSwitcher() },

        DevTool(
            getString(R.string.dev_console),
            getString(R.string.dev_console_desc)
        ) { showLogs(DevConsole.consoleText(), R.string.dev_title_console, R.string.dev_console_empty) },

        DevTool(
            getString(R.string.dev_network),
            getString(R.string.dev_network_desc)
        ) { showLogs(DevConsole.networkText(), R.string.dev_title_network, R.string.dev_network_empty) },

        DevTool(
            getString(R.string.dev_eruda),
            getString(R.string.dev_eruda_desc)
        ) { injectEruda(forceToggle = false) },

        DevTool(
            getString(R.string.dev_clear_cache),
            getString(R.string.dev_clear_cache_desc)
        ) { clearWebViewData() },

        DevTool(
            getString(R.string.dev_export_logs),
            getString(R.string.dev_export_logs_desc)
        ) { exportLogs() }
    )

    // ------------------------------------------------------------------ Eruda

    private fun injectEruda(forceToggle: Boolean) {
        val webView = WebViewRegistry.current()
        if (webView == null) {
            toast(R.string.dev_no_webview)
            return
        }
        val enable = if (forceToggle) true else !prefs.erudaEnabled
        prefs.erudaEnabled = enable
        if (!enable) {
            webView.evaluateJavascript("window.eruda && window.eruda.destroy()", null)
            toast(R.string.dev_done)
            return
        }
        webView.evaluateJavascript(ERUDA_SCRIPT, null)
        toast(R.string.dev_eruda_injected)
        DevConsole.info("Eruda", "injected into ${webView.url ?: "about:blank"}")
    }

    // ------------------------------------------------------------------ UA

    private fun showUaSwitcher() {
        val labels = UserAgents.labels()
        AlertDialog.Builder(this)
            .setTitle(R.string.dev_ua_switcher)
            .setSingleChoiceItems(labels, prefs.uaPreset) { dialog, which ->
                dialog.dismiss()
                if (which == UserAgents.PRESET_CUSTOM) {
                    askCustomUa()
                } else {
                    applyPreset(which)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun askCustomUa() {
        val input = EditText(this).apply {
            hint = "Mozilla/5.0 …"
            setText(prefs.customUa.ifBlank { UserAgents.resolve(this@DeveloperSettingsActivity, prefs) })
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.ua_custom)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val candidate = input.text.toString().trim()
                // FIX: Via accepted any length; Paradox validates.
                if (!UserAgents.isValid(candidate)) {
                    toast(R.string.dev_ua_applied)
                    return@setPositiveButton
                }
                prefs.customUa = candidate
                prefs.uaPreset = UserAgents.PRESET_CUSTOM
                applyToWebView()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyPreset(preset: Int) {
        prefs.uaPreset = preset
        applyToWebView()
    }

    private fun applyToWebView() {
        val webView = WebViewRegistry.current()
        if (webView == null) {
            toast(R.string.dev_no_webview)
            return
        }
        webView.settings.userAgentString = UserAgents.resolve(this, prefs)
        webView.reload()
        toast(R.string.dev_ua_applied)
        DevConsole.info("UserAgent", webView.settings.userAgentString ?: "")
    }

    // ------------------------------------------------------------------ logs

    private fun showLogs(content: String, titleRes: Int, emptyRes: Int) {
        val isConsole = titleRes == R.string.dev_title_console
        val empty = if (isConsole) {
            DevConsole.consoleSnapshot().isEmpty()
        } else {
            DevConsole.networkSnapshot().isEmpty()
        }
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setMessage(if (empty) getString(emptyRes) else content.take(MAX_DIALOG_CHARS))
            .setPositiveButton(R.string.dev_ok, null)
            .setNeutralButton(R.string.dev_clear_logs) { _, _ ->
                DevConsole.clear()
                toast(R.string.dev_done)
            }
            .show()
    }

    private fun exportLogs() {
        val file = DevConsole.export(this)
        if (file == null) {
            toast(R.string.dev_logs_failed)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dev_export_logs)
            .setMessage(getString(R.string.dev_logs_saved, file.absolutePath))
            .setPositiveButton(R.string.dev_ok, null)
            .show()
    }

    // ------------------------------------------------------------------ data

    private fun clearWebViewData() {
        WebStorage.getInstance().deleteAllData()
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies(null)
        cookies.flush()

        val webView = WebViewRegistry.current()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && webView != null) {
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
            webView.clearSslPreferences()
        } else {
            WebView(this).clearCache(true)
        }
        DevConsole.info("WebView", "cache, cookies and form data cleared")
        toast(R.string.dev_cleared)
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val MAX_DIALOG_CHARS = 12_000

        /** Eruda is injected from jsDelivr; the page CSP may still block it. */
        val ERUDA_SCRIPT = """
            (function(){
              if (window.eruda) { window.eruda.show(); return; }
              var s = document.createElement('script');
              s.src = 'https://cdn.jsdelivr.net/npm/eruda@3.0.1/eruda.min.js';
              s.onload = function(){ eruda.init(); eruda.show(); };
              s.onerror = function(){ console.error('Paradox: Eruda failed to load'); };
              document.body.appendChild(s);
            })();
        """.trimIndent()
    }
}
