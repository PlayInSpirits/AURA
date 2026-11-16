package com.eddyizm.tempus.hub

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView

/**
 * AURA_2: keeps one hub WebView for the whole app run.
 *
 * The Hub tab used to build its WebView each time and destroy it on leaving, which also stopped
 * anything the hub page was playing. Now the same WebView is lent to the tab while it is shown and
 * kept (detached, still running) while the user is elsewhere in the app.
 */
object HubWebHost {
    private var webView: WebView? = null
    private var context: MutableContextWrapper? = null
    private var loadedHub: String? = null

    /** The WebView, created on first use, now living in [activityContext]. */
    @SuppressLint("SetJavaScriptEnabled")
    fun obtain(activityContext: Context): WebView {
        val existing = webView
        if (existing != null) {
            context?.baseContext = activityContext
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        val wrapper = MutableContextWrapper(activityContext)
        val web = WebView(wrapper)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.mediaPlaybackRequiresUserGesture = false
        CookieManager.getInstance().setAcceptCookie(true)
        context = wrapper
        webView = web
        return web
    }

    /** Loads [hub] with the session cookie unless that page is already open. */
    fun loadIfNeeded(web: WebView, hub: String) {
        if (loadedHub == hub && web.url != null) return
        val session = HubPrefs.cookie
        if (session != null) {
            CookieManager.getInstance().setCookie(hub, "$session; Path=/")
            CookieManager.getInstance().flush()
        }
        loadedHub = hub
        web.loadUrl(hub)
    }

    /**
     * The tab is gone: keep the page running, but stop holding on to the activity. When the WebView has
     * already moved on to another holder (a new Hub screen built before this one was destroyed), it is
     * left where it is: taking it out there blanked the Hub after a second tap on its tab.
     */
    fun release(appContext: Context, holder: ViewGroup?) {
        val web = webView ?: return
        val parent = web.parent as? ViewGroup
        if (parent != null && holder != null && parent !== holder) return
        parent?.removeView(web)
        context?.baseContext = appContext.applicationContext
    }

    /** Signing out or pairing another hub: the page and its session go away. */
    fun destroy() {
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        webView = null
        context = null
        loadedHub = null
    }
}
