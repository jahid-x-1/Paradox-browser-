package com.paradox.browser

import android.webkit.WebView
import java.lang.ref.WeakReference

/**
 * Lets the Developer Section reach the live WebView (it lives in MainActivity's process,
 * so a WeakReference is enough and never leaks the activity).
 */
object WebViewRegistry {

    private var ref: WeakReference<WebView>? = null

    fun attach(webView: WebView) {
        ref = WeakReference(webView)
    }

    fun detach(webView: WebView) {
        if (ref?.get() === webView) ref = null
    }

    fun current(): WebView? = ref?.get()
}
