package com.paradox.browser

/**
 * Deliberately tiny host blocklist.
 *
 * It is NOT a full EasyList implementation — shipping a 2 MB list would need a bundled
 * database and regex engine. This covers the highest-volume ad and telemetry hosts,
 * which is where most mobile page weight comes from, and it never breaks first-party
 * requests because it only matches exact hosts or their subdomains.
 */
object AdBlocker {

    private val hosts = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "googletagmanager.com",
        "google-analytics.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "amazon-adsystem.com",
        "adsrvr.org",
        "adnxs.com",
        "adcolony.com",
        "applovin.com",
        "unityads.unity3d.com",
        "facebook.net",
        "connect.facebook.net",
        "analytics.twitter.com",
        "ads.tiktok.com",
        "scorecardresearch.com",
        "quantserve.com",
        "outbrain.com",
        "taboola.com",
        "criteo.com",
        "criteo.net",
        "moatads.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "zedo.com",
        "adform.net",
        "smartadserver.com",
        "yandex.ru/clck",
        "telemetry.mozilla.org",
        "app-measurement.com",
        "crashlytics.com"
    )

    fun isBlocked(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val normalized = host.lowercase()
        return hosts.any { blocked ->
            normalized == blocked || normalized.endsWith(".$blocked")
        }
    }
}
