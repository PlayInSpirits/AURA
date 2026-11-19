package com.eddyizm.tempus.ui.fragment

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.R
import com.eddyizm.tempus.hub.HubPrefs
import com.eddyizm.tempus.hub.HubWebHost
import com.eddyizm.tempus.hub.HubPlayback
import com.eddyizm.tempus.hub.HubWebPlayback
import com.eddyizm.tempus.ui.view.AuraMiniPlayer
import com.eddyizm.tempus.ui.login.AuraConnectActivity
import com.google.android.material.color.MaterialColors

/**
 * AURA "Hub": opens as a window of its own (the app's tab bar and mini player step aside, the hub's
 * own tab bar is the bottom bar, «Назад» leads back to the tab it was opened from).
 *
 * Originally the AURA_2 "Hub" tab: the HomeMusic hub web app (downloads, assistant, my files,
 * daily mix) inside the app. The hub runs on the PC and does all the work; this screen only shows it.
 * The session cookie obtained at sign-in is handed to the WebView, so no second login is needed.
 *
 * The WebView is kept by [HubWebHost] between visits, so the page (and anything it plays) survives
 * switching tabs.
 */
@UnstableApi
class HubFragment : Fragment() {
    private var webView: WebView? = null
    private var mediaBridge: HubWebPlayback? = null
    private var miniPlayer: AuraMiniPlayer? = null
    private var holderView: FrameLayout? = null
    private val fitListener = android.view.ViewTreeObserver.OnGlobalLayoutListener { fitAboveBottomBars() }
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val callback = fileCallback
        fileCallback = null
        callback?.onReceiveValue(if (uris.isNullOrEmpty()) null else uris.toTypedArray())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_hub, container, false)
        val holder = root.findViewById<FrameLayout>(R.id.hub_web_container)
        val empty = root.findViewById<View>(R.id.hub_empty)
        val progress = root.findViewById<ProgressBar>(R.id.hub_progress)
        val connect = root.findViewById<Button>(R.id.hub_connect_button)
        val back = root.findViewById<View>(R.id.hub_back_button)
        val mini = root.findViewById<AuraMiniPlayer>(R.id.hub_native_player)
        val playerSpace = root.findViewById<View>(R.id.hub_player_space)
        val loadingLogo = root.findViewById<View>(R.id.hub_splash_logo)
        val greeting = root.findViewById<android.widget.TextView>(R.id.hub_greeting)
        HubPrefs.userName?.let { name ->
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val message = when (hour) {
                in 5..11 -> R.string.aura_hub_good_morning
                in 12..17 -> R.string.aura_hub_good_day
                in 18..22 -> R.string.aura_hub_good_evening
                else -> R.string.aura_hub_good_night
            }
            greeting.text = getString(message, name)
            greeting.visibility = View.VISIBLE
        }
        miniPlayer = mini
        mini.onItemVisibility = { playerSpace.visibility = if (it) View.VISIBLE else View.GONE }

        back.setOnClickListener { leave() }
        back.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(120).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(380)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2.5f)).start()
            }
            false
        }
        // The window reaches the bottom edge: keep the hub's tab bar above the system navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, bars.bottom)
            insets
        }
        // Back from anywhere in the hub: the page's history first, then out of the window.
        connect.setOnClickListener {
            startActivity(Intent(requireContext(), AuraConnectActivity::class.java))
        }

        val hub = HubPrefs.hubUrl
        if (hub == null) {
            holder.visibility = View.GONE
            progress.visibility = View.GONE
            empty.visibility = View.VISIBLE
            return root
        }

        empty.visibility = View.GONE
        // The page ends exactly where the mini player / bottom bar begin (no fixed gap), so the hub's
        // own bottom bar sits right above them.
        holder.setPadding(0, 0, 0, 0)
        holderView = holder
        requireActivity().window.decorView.viewTreeObserver.addOnGlobalLayoutListener(fitListener)
        val web = HubWebHost.obtain(requireActivity())
        holder.addView(
            web,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        loadingLogo.bringToFront()
        webView = web
        mediaBridge = HubWebPlayback(web, requireActivity() as MainActivity).also { it.attach() }
        if (web.url != null) loadingLogo.visibility = View.GONE
        setUpWebView(web, progress, loadingLogo, hub)
        return root
    }

    private fun setUpWebView(web: WebView, progress: ProgressBar, loadingLogo: View, hub: String) {
        val origin = HubPlayback.origin()
        val light = MaterialColors.isColorLight(MaterialColors.getColor(web, com.google.android.material.R.attr.colorSurface))
        val themeScript = "document.documentElement?.classList.toggle('aura-light', $light);\n$AURA_HUB_JS"
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Pages of the hub stay inside; anything else (Navidrome next door too) goes to the browser.
                if (!request.isForMainFrame) return false
                if (request.url.buildUpon().encodedPath("").clearQuery().fragment(null).build().toString() == origin)
                    return false
                if (request.url.scheme !in setOf("http", "https")) return true
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (ignored: Exception) {
                    // no app can open it
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                view.evaluateJavascript(themeScript, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(themeScript, null)
                loadingLogo.visibility = View.GONE
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                loadingLogo.visibility = View.GONE
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                // "My files" uploads songs: hand the choice to the system picker.
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                filePicker.launch("*/*")
                return true
            }
        }

        HubWebHost.loadIfNeeded(web, hub)
        web.evaluateJavascript(themeScript, null)
    }

    override fun onStart() {
        super.onStart()
        val activity = activity as? MainActivity ?: return
        activity.setBottomNavigationBarVisibility(false)
        activity.setBottomSheetVisibility(true)
        val future = activity.mediaBrowserListenableFuture
        val mini = miniPlayer
        future?.addListener({
            if (mini != null && miniPlayer === mini &&
                lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) && !future.isCancelled) {
                runCatching { mini.bind(future.get()) }
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(activity))
        webView?.onResume()
    }

    override fun onStop() {
        miniPlayer?.bind(null)
        webView?.onPause()
        val activity = activity as? MainActivity
        if (activity != null && !activity.isChangingConfigurations) {
            activity.setBottomSheetVisibility(true)
            activity.toggleBottomNavigationBarVisibilityOnOrientationChange()
        }
        super.onStop()
    }

    private fun leave() {
        (activity as? MainActivity)?.leaveHub()
    }

    /** Bottom padding = how far the bottom navigation or the mini player reach into the page. */
    private fun fitAboveBottomBars() {
        val holder = holderView ?: return
        val activity = activity ?: return
        if (!holder.isAttachedToWindow) return
        val holderLoc = IntArray(2)
        holder.getLocationInWindow(holderLoc)
        val holderBottom = holderLoc[1] + holder.height
        var obstacleTop = holderBottom
        for (id in intArrayOf(R.id.bottom_navigation, R.id.player_bottom_sheet)) {
            val bar = activity.findViewById<View>(id) ?: continue
            if (bar.visibility != View.VISIBLE || bar.height == 0) continue
            val loc = IntArray(2)
            bar.getLocationInWindow(loc)
            // An expanded player covers the tab anyway: do not squeeze the page for it.
            if (loc[1] < holderLoc[1] + holder.height / 2) continue
            if (loc[1] < obstacleTop) obstacleTop = loc[1]
        }
        val padding = (holderBottom - obstacleTop).coerceAtLeast(0)
        if (holder.paddingBottom != padding) holder.setPadding(0, 0, 0, padding)
    }

    override fun onDestroyView() {
        mediaBridge?.detach()
        mediaBridge = null
        miniPlayer = null
        activity?.window?.decorView?.viewTreeObserver?.removeOnGlobalLayoutListener(fitListener)
        webView?.let {
            // A second tap on the tab may already have lent the WebView to a new Hub screen: then it is
            // left there untouched (taking it out blanked the Hub, leaving only the background).
            val parent = it.parent
            if (parent == null || parent === holderView) {
                // Detach, but do not stop: the hub page keeps running while the user is elsewhere.
                it.webChromeClient = WebChromeClient()
                it.webViewClient = WebViewClient()
                HubWebHost.release(requireContext(), holderView)
            }
        }
        holderView = null
        webView = null
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        super.onDestroyView()
    }

    private companion object {
        /**
         * AURA touches on the hub page, also after it re-renders:
         *  - Search lives in AURA's native Home screen, so omit that tab here;
         *  - HomeMusic is called AURA, the Android-app link leads to AURA's releases and the Navidrome link
         *    to the home Navidrome at http://192.168.1.129:4533.
         */
        const val AURA_HUB_JS = """
(function () {
  if (window.__aura) { window.__aura(); return; }
  var NAVIDROME = 'http://192.168.1.129:4533';
  var APP = 'https://github.com/PlayInSpirits/AURA-fork-HomeMusic/releases';
  function text(el) { return (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim(); }
  function leaveSearch() {
    if (!/^#\/search(?:\?|$)/.test(location.hash)) return;
    history.replaceState(history.state, '', '#/home');
    window.dispatchEvent(new Event('hm:route'));
  }
  function run() {
    if (!document.body) return;
    leaveSearch();
    if (!document.getElementById('aura-hub-theme')) {
      var css = document.createElement('style');
      css.id = 'aura-hub-theme';
      css.textContent = ':root{--color-accent:#d7c2ff;--color-accent-strong:#bd9ef5;--color-on-accent:#28193b;'
        + '--color-bg:#100c18;--color-surface:#21192c;--color-raised:#2d223b;--color-line:#45354f;--color-hover:#392c49}'
        + ':root.aura-light{color-scheme:light;--color-accent:#6b4ee6;--color-accent-strong:#5539bc;--color-on-accent:#fff;'
        + '--color-bg:#fcfaff;--color-surface:#f4effa;--color-raised:#eee6fa;--color-line:#d9cce8;--color-hover:#e9ddf8;'
        + '--color-fg:#241931;--color-muted:#574762;--color-dim:#6d5b7b}'
        + 'nav[aria-label="Разделы"]{background-color:var(--color-bg)}'
        + 'nav li.aura-search-tab,header h1.aura-greeting{display:none!important}'
        + 'img[src^="/icons/"],img[alt="HomeMusic"]{visibility:hidden}';
      document.head.appendChild(css);
    }
    document.querySelectorAll('header h1').forEach(function (heading) {
      if (/^Добро(й|е) |^Добрый /.test(text(heading))) heading.classList.add('aura-greeting');
    });
    document.querySelectorAll('nav a[href="#/search"],nav a[href^="#/search?"]').forEach(function (link) {
      link.closest('li')?.classList.add('aura-search-tab');
    });
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    var node;
    while ((node = walker.nextNode())) {
      if (node.nodeValue && node.nodeValue.indexOf('HomeMusic') >= 0) node.nodeValue = node.nodeValue.replace(/HomeMusic/g, 'AURA');
    }
    document.querySelectorAll('a[href]').forEach(function (a) {
      var href = a.getAttribute('href') || '';
      if (/navidrome/i.test(text(a)) && href !== NAVIDROME) a.setAttribute('href', NAVIDROME);
      if (href.indexOf('latest.apk') >= 0) a.setAttribute('href', APP);
    });
    if (document.title.indexOf('HomeMusic') >= 0) document.title = document.title.replace(/HomeMusic/g, 'AURA');
  }
  var queued = false;
  window.__aura = function () {
    if (queued) return;
    queued = true;
    requestAnimationFrame(function () { queued = false; try { run(); } catch (e) {} });
  };
  try { run(); } catch (e) {}
  window.addEventListener('hashchange', leaveSearch);
  window.addEventListener('hm:route', leaveSearch);
  if (document.documentElement) {
    new MutationObserver(function () { window.__aura(); })
      .observe(document.documentElement, { childList: true, subtree: true, characterData: true });
  }
})();
"""
    }
}
