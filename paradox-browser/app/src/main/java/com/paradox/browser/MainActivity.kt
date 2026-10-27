package com.paradox.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.paradox.browser.databinding.ActivityMainBinding
import com.paradox.browser.developer.DeveloperSettingsActivity
import com.paradox.browser.service.DownloadService

/**
 * Paradox Browser — main surface.
 *
 * Every fix from the Via REA lives here:
 *  - minSdk 21, no file:// access, no mixed content, Safe Browsing on
 *  - modern User-Agent with an opt-in (not default) Via suffix, length validated
 *  - real Tools: Adblock, Night Mode, Find in Page
 *  - Developer Section reachable from both the hamburger and the bottom bar
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: BrowserPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = BrowserPrefs(this)

        setSupportActionBar(binding.toolbar)
        setupWebView()
        setupChrome()
        setupBars()

        val incoming = intent?.data?.toString()
        val target = when {
            !incoming.isNullOrBlank() -> incoming
            savedInstanceState != null -> null
            else -> prefs.lastUrl
        }
        if (target != null) binding.webView.loadUrl(target)
    }

    // ---------------------------------------------------------------- WebView config

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val ws = binding.webView.settings

        // 1. File access OFF — Via allowed file://, which lets any page read local files.
        ws.allowFileAccess = false
        ws.allowContentAccess = false
        ws.allowFileAccessFromFileURLs = false
        ws.allowUniversalAccessFromFileURLs = false

        // 2. Mixed content never allowed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ws.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        // 3. Safe Browsing: API 26+ only, so guard it instead of crashing on 21-25.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ws.safeBrowsingEnabled = true
        }

        // 4. Normal browsing settings.
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.databaseEnabled = true
        ws.useWideViewPort = true
        ws.loadWithOverviewMode = true
        ws.setSupportZoom(true)
        ws.builtInZoomControls = true
        ws.displayZoomControls = false
        ws.mediaPlaybackRequiresUserGesture = true
        ws.cacheMode = WebSettings.LOAD_DEFAULT

        applyUserAgent()
        applyNightMode()
        applyThirdPartyCookies()

        binding.webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                // FIX: file:// and content:// pages are never navigated to.
                if (url.startsWith("file://")) return true
                // Hand non-web schemes to the system (mailto:, tel:, market:).
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return openExternal(url)
                }
                return false
            }

            @Deprecated("Required for API 21-23")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith("file://")) return true
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return openExternal(url)
                }
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url
                val host = url.host
                val blocked = prefs.adblockEnabled && AdBlocker.isBlocked(host)
                DevConsole.network(url.toString(), if (blocked) "BLOCKED" else "")
                if (blocked) {
                    return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), null)
                }
                return null
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler,
                error: android.net.http.SslError
            ) {
                // FIX: never proceed on a broken certificate.
                handler.cancel()
                Toast.makeText(
                    this@MainActivity,
                    "Connection is not secure — blocked",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                if (url.startsWith("http")) {
                    prefs.lastUrl = url
                    binding.urlBar.setText(url)
                }
            }
        }

        binding.webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            if (prefs.adblockEnabled && AdBlocker.isBlocked(Uri.parse(url).host)) {
                Toast.makeText(
                    this,
                    getString(R.string.download_blocked, url),
                    Toast.LENGTH_SHORT
                ).show()
                return@setDownloadListener
            }
            startDownload(url, contentDisposition, mimeType)
        }

        WebViewRegistry.attach(binding.webView)
    }

    private fun setupChrome() {
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(
                consoleMessage: android.webkit.ConsoleMessage
            ): Boolean {
                DevConsole.console(
                    consoleMessage.message(),
                    consoleMessage.sourceId() ?: "",
                    consoleMessage.lineNumber()
                )
                return true
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                val bar: ProgressBar = binding.webProgress
                if (newProgress >= 100) {
                    bar.visibility = View.GONE
                } else {
                    bar.visibility = View.VISIBLE
                    bar.progress = newProgress
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: android.webkit.GeolocationPermissions.Callback
            ) {
                // FIX: Via handed out location silently. Paradox declares no location
                // permission at all, so it can only refuse — and says so instead of
                // showing a dialog it could never honour.
                callback.invoke(origin, false, false)
                Toast.makeText(
                    this@MainActivity,
                    "Location is disabled in Paradox",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ---------------------------------------------------------------- UI wiring

    private fun setupBars() {
        binding.btnTools.setOnClickListener { showToolsMenu() }
        binding.toolDev.setOnClickListener {
            startActivity(Intent(this, DeveloperSettingsActivity::class.java))
        }
        binding.toolBack.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack()
        }
        binding.toolForward.setOnClickListener {
            if (binding.webView.canGoForward()) binding.webView.goForward()
        }
        binding.toolHome.setOnClickListener { binding.webView.loadUrl(BuildConfig.HOME_URL) }

        binding.urlBar.setOnEditorActionListener { _, _, _ ->
            navigateTo(binding.urlBar.text.toString())
            true
        }
    }

    private fun navigateTo(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val url = when {
            text.startsWith("http://") || text.startsWith("https://") -> text
            text.contains(" ") || !text.contains(".") ->
                "https://duckduckgo.com/?q=" + Uri.encode(text)
            else -> "https://$text"
        }
        binding.webView.loadUrl(url)
        hideKeyboard()
    }

    private fun showToolsMenu() {
        val items = arrayOf(
            getString(if (prefs.adblockEnabled) R.string.tools_adblock_on else R.string.tools_adblock_off),
            getString(if (prefs.nightMode) R.string.tools_night_on else R.string.tools_night_off),
            getString(R.string.tools_find),
            getString(R.string.menu_developer)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_tools)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> toggleAdblock()
                    1 -> toggleNightMode()
                    2 -> showFindInPage()
                    3 -> startActivity(Intent(this, DeveloperSettingsActivity::class.java))
                }
            }
            .show()
    }

    private fun toggleAdblock() {
        prefs.adblockEnabled = !prefs.adblockEnabled
        Toast.makeText(
            this,
            getString(
                if (prefs.adblockEnabled) R.string.tools_adblock_on else R.string.tools_adblock_off
            ),
            Toast.LENGTH_SHORT
        ).show()
        binding.webView.reload()
    }

    fun toggleNightMode() {
        prefs.nightMode = !prefs.nightMode
        applyNightMode()
        binding.webView.reload()
    }

    fun applyNightMode() {
        val enabled = prefs.nightMode
        val ws = binding.webView.settings
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(ws, enabled)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ws.forceDark = if (enabled) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        }
    }

    fun applyUserAgent() {
        binding.webView.settings.userAgentString = UserAgents.resolve(this, prefs)
    }

    private fun applyThirdPartyCookies() {
        try {
            val cookies = CookieManager.getInstance()
            cookies.setAcceptCookie(true)
            // FIX: third-party cookies are blocked by default (privacy).
            cookies.setAcceptThirdPartyCookies(binding.webView, false)
        } catch (e: Exception) {
            DevConsole.info("Cookies", "Could not configure CookieManager: ${e.message}")
        }
    }

    private fun showFindInPage() {
        val input = EditText(this).apply {
            hint = getString(R.string.find_hint)
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.tools_find)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) binding.webView.findAllAsync(query)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        binding.webView.setFindListener { activeMatch, totalMatches, _ ->
            val message = if (totalMatches == 0) {
                getString(R.string.find_none)
            } else {
                getString(R.string.find_count, activeMatch + 1, totalMatches)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun startDownload(url: String, contentDisposition: String?, mimeType: String?) {
        val intent = Intent(this, DownloadService::class.java).apply {
            putExtra(DownloadService.EXTRA_URL, url)
            putExtra(DownloadService.EXTRA_CONTENT_DISPOSITION, contentDisposition)
            putExtra(DownloadService.EXTRA_MIME_TYPE, mimeType)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
    }

    private fun openExternal(url: String): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (e: Exception) {
            DevConsole.info("Intent", "No handler for $url")
            false
        }
    }

    private fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(this, android.view.inputmethod.InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.urlBar.windowToken, 0)
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.toString()?.let { binding.webView.loadUrl(it) }
    }

    @Deprecated("Kept for API 21-32 back navigation")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        WebViewRegistry.detach(binding.webView)
        binding.webView.stopLoading()
        binding.webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val TAG = "Paradox"
    }
}
