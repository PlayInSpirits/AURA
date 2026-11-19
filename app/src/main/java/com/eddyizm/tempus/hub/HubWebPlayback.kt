package com.eddyizm.tempus.hub

import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eddyizm.tempus.R
import com.eddyizm.tempus.service.MediaManager
import com.eddyizm.tempus.ui.activity.MainActivity
import com.google.android.material.snackbar.Snackbar
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

@UnstableApi
class HubWebPlayback(private val web: WebView, private val activity: MainActivity) {
    private var script: ScriptHandler? = null
    private var installed = false

    fun attach() {
        val origin = HubPlayback.origin() ?: return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) return
        WebViewCompat.addWebMessageListener(web, BRIDGE, setOf(origin)) { _, message, _, mainFrame, _ ->
            val body = message.data ?: return@addWebMessageListener
            if (!installed || !mainFrame || body.length > MAX_MESSAGE || HubPlayback.origin() != origin) return@addWebMessageListener
            val command = runCatching { JSONObject(body) }.getOrNull() ?: return@addWebMessageListener
            when (command.optString("op")) {
                "queue" -> queue(command, origin)
                "wave" -> {
                    val answer = runCatching { JSONObject(command.optString("items")) }.getOrNull()
                        ?: return@addWebMessageListener
                    val future = activity.mediaBrowserListenableFuture ?: return@addWebMessageListener
                    HubWave.startFromHub(future, answer) { error ->
                        if (activity.isDestroyed || HubPlayback.origin() != origin) return@startFromHub
                        if (error == null) {
                            activity.setBottomSheetInPeek(true)
                        } else if (installed) {
                            Snackbar.make(activity.findViewById(android.R.id.content), error, Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
                "pause" -> {
                    val future = activity.mediaBrowserListenableFuture ?: return@addWebMessageListener
                    future.addListener({
                        if (installed && !future.isCancelled) runCatching { future.get().pause() }
                    }, ContextCompat.getMainExecutor(activity))
                }
            }
        }
        script = WebViewCompat.addDocumentStartJavaScript(web, SHIM, setOf(origin))
        installed = true
        if (web.url != null) web.evaluateJavascript(SHIM, null)
    }

    private fun queue(command: JSONObject, origin: String) {
        val raw = runCatching { JSONArray(command.optString("items")) }.getOrNull() ?: return
        if (raw.length() !in 1..1000) return
        val requestedIndex = command.optInt("index").coerceIn(0, raw.length() - 1)
        val selection = MediaManager.beginQueueSelection()
        val future = activity.mediaBrowserListenableFuture ?: return
        worker.execute {
            if (!MediaManager.isQueueSelectionCurrent(selection)) return@execute
            val songs = (0 until raw.length()).mapNotNull { index ->
                if (!MediaManager.isQueueSelectionCurrent(selection) || HubPlayback.origin() != origin) return@execute
                val track = raw.optJSONObject(index) ?: raw.optString(index).takeIf { it.isNotBlank() }
                    ?.let { JSONObject().put("song_id", it) } ?: return@mapNotNull null
                HubPlayback.fromJson(track)?.let { index to it }
            }
            activity.runOnUiThread {
                if (activity.isDestroyed || HubPlayback.origin() != origin ||
                    !MediaManager.isQueueSelectionCurrent(selection)) return@runOnUiThread
                if (songs.isEmpty()) {
                    Snackbar.make(activity.findViewById(android.R.id.content), R.string.aura_hub_play_error, Snackbar.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val start = songs.indexOfFirst { it.first >= requestedIndex }.coerceAtLeast(0)
                MediaManager.startSelectedQueue(future, songs.map { it.second }, start, 0L, selection)
                activity.setBottomSheetInPeek(true)
            }
        }
    }

    fun detach() {
        if (!installed) return
        installed = false
        script?.remove()
        script = null
        WebViewCompat.removeWebMessageListener(web, BRIDGE)
    }

    private companion object {
        const val BRIDGE = "AuraMedia"
        const val MAX_MESSAGE = 1_000_000
        val worker = Executors.newSingleThreadExecutor()
        const val SHIM = """
            (function () {
                if (window.top !== window || !window.AuraMedia) return;
                const send = (op, items, index) => window.AuraMedia.postMessage(JSON.stringify({op, items, index}));
                const exposePlayback = () => {
                    window.HomeMusicAndroid = {
                        play: (ids, index) => send('queue', ids, index),
                        playQueue: (items, index) => send('queue', items, index),
                        playWave: (wave) => send('wave', wave),
                        pauseNative: () => send('pause')
                    };
                };
                if (!location.hash || /^#\/(?:search)?(?:\?|$)/.test(location.hash)) {
                    history.replaceState(history.state, '', '#/home');
                }
                // The Hub's startup modules use this property to choose their navigation layout.
                // Expose native playback once those modules have selected the full Hub.
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', exposePlayback, {once: true});
                } else {
                    exposePlayback();
                }
            })();
        """
    }
}
