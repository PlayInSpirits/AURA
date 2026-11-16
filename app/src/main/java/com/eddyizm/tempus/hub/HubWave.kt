package com.eddyizm.tempus.hub

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import com.eddyizm.tempus.App
import com.eddyizm.tempus.service.MediaManager
import com.eddyizm.tempus.service.MediaServiceExtension
import com.eddyizm.tempus.service.MediaServiceExtensionRegistry
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.util.Preferences
import com.google.common.util.concurrent.ListenableFuture
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AURA_2 "My Wave" played by the app's own player, so it keeps going on every tab and in the
 * background (it used to live in the Hub web page, which stopped when the tab was left).
 *
 * The hub picks the songs (`POST /api/wave/start`, then `POST /api/wave/{session}/next`); the songs
 * are streamed from Navidrome like any other track. When the queue is two songs from its end, the
 * next batch is asked for and appended.
 */
object HubWave : MediaServiceExtension {
    private const val TAG = "HubWave"
    private const val REFILL_WHEN_LEFT = 2
    private const val POLL_MS = 4000L
    private const val MAX_POLLS = 15

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Wave session on the hub, null when no wave is playing. */
    @Volatile
    private var sessionId: String? = null

    /** Navidrome ids of the songs this wave put in the queue, with the hub's ref of each. */
    private val refs = LinkedHashMap<String, String>()
    private val fetching = AtomicBoolean(false)

    /** When the last wave was handed to the player: the old song may still report in for a moment. */
    @Volatile
    private var startedAt = 0L

    /** True while a wave started from the Home button owns the queue. */
    fun isActive(): Boolean = sessionId != null

    /** True when [mediaId] is a song of the running wave. */
    fun owns(mediaId: String?): Boolean = mediaId != null && synchronized(refs) { refs.containsKey(mediaId) }

    fun stop() {
        sessionId = null
        synchronized(refs) { refs.clear() }
        if (MediaServiceExtensionRegistry.handler === this) MediaServiceExtensionRegistry.handler = null
    }

    /**
     * Starts a new wave and plays it. [done] runs on the main thread with null on success or a message
     * to show.
     */
    fun start(browser: ListenableFuture<MediaBrowser>, done: (String?) -> Unit) {
        worker.execute {
            val result = runCatching { startBlocking() }
            main.post {
                result.fold(
                    onSuccess = { songs ->
                        MediaServiceExtensionRegistry.handler = this
                        startedAt = System.currentTimeMillis()
                        MediaManager.startQueue(browser, songs, 0)
                        done(null)
                    },
                    onFailure = { e ->
                        Log.w(TAG, "start failed", e)
                        stop()
                        done(e.message ?: "Не удалось запустить волну")
                    },
                )
            }
        }
    }

    private fun startBlocking(): List<Child> {
        stop()
        val body = JSONObject().put("origin", "home_card")
        var answer = post("/api/wave/start", body)
        val session = answer.optString("session_id").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Хаб не вернул сессию волны")
        sessionId = session

        var songs = toSongs(answer)
        var polls = 0
        while (songs.isEmpty() && polls < MAX_POLLS) {
            // The hub may still be choosing: ask again a little later.
            Thread.sleep(POLL_MS)
            polls++
            answer = post("/api/wave/$session/next", nextBody(emptyList()))
            songs = toSongs(answer)
        }
        if (songs.isEmpty()) throw IllegalStateException("Волна пока пуста: в библиотеке не нашлось подходящих песен")
        return songs
    }

    /** Called by the player service on every track change. */
    override fun handle(player: Player, item: MediaItem, queueTarget: MediaManager.QueueTarget): Boolean {
        val session = sessionId ?: return false
        if (!owns(item.mediaId)) {
            // Something else is playing now: the wave is over (unless the queue is still switching).
            if (System.currentTimeMillis() - startedAt > 8000L) stop()
            return false
        }
        val left = player.mediaItemCount - 1 - player.currentMediaItemIndex
        if (left <= REFILL_WHEN_LEFT && fetching.compareAndSet(false, true)) {
            val queued = ArrayList<String>()
            for (i in player.currentMediaItemIndex + 1 until player.mediaItemCount) {
                synchronized(refs) { refs[player.getMediaItemAt(i).mediaId] }?.let { queued.add(it) }
            }
            worker.execute {
                val more = runCatching { toSongs(post("/api/wave/$session/next", nextBody(queued))) }
                    .onFailure { Log.w(TAG, "next failed", it) }
                    .getOrDefault(emptyList())
                main.post {
                    fetching.set(false)
                    if (sessionId == session && more.isNotEmpty()) {
                        // The hub's answer replaces the wave songs still ahead (it may hand some back).
                        val live = queueTarget.livePlayer()
                        if (live != null) {
                            var end = live.mediaItemCount
                            val from = live.currentMediaItemIndex + 1
                            while (end > from && owns(live.getMediaItemAt(end - 1).mediaId)) end--
                            if (end == from && from < live.mediaItemCount) {
                                MediaManager.removeRange(queueTarget, from, live.mediaItemCount)
                            }
                        }
                        MediaManager.enqueue(queueTarget, more, false)
                    }
                }
            }
        }
        // The wave fills the queue itself: no "continuous play" from similar songs.
        return true
    }

    private fun nextBody(queued: List<String>): JSONObject =
        JSONObject().put("played", JSONArray()).put("queued", JSONArray(queued))

    /** Songs of a hub answer that Navidrome can stream, in order. */
    private fun toSongs(answer: JSONObject): List<Child> {
        val tracks = answer.optJSONArray("tracks") ?: return emptyList()
        val out = ArrayList<Child>()
        for (i in 0 until tracks.length()) {
            val t = tracks.optJSONObject(i) ?: continue
            val id = t.optString("nd_song_id").takeIf { it.isNotBlank() && it != "null" } ?: continue
            val song = loadSong(id) ?: Child(id).apply {
                title = t.optString("title").takeIf { it.isNotBlank() }
                artist = t.optString("artist").takeIf { it.isNotBlank() }
                album = t.optString("album").takeIf { it.isNotBlank() }
                coverArtId = id
                type = "music"
                val seconds = t.optDouble("duration_s", 0.0)
                if (seconds > 0) duration = seconds.toInt()
            }
            synchronized(refs) { refs[id] = t.optString("ref", id) }
            out.add(song)
        }
        return out
    }

    /** Full song data from Navidrome (cover, album, artist ids), or null to fall back on the hub's. */
    private fun loadSong(id: String): Child? = try {
        App.getSubsonicClientInstance(false).browsingClient.getSong(id).execute()
            .body()?.subsonicResponse?.song
    } catch (e: Exception) {
        Log.w(TAG, "getSong $id failed", e)
        null
    }

    /** POSTs JSON to the hub with the session cookie, signing in again once if the session expired. */
    private fun post(path: String, body: JSONObject): JSONObject {
        val hub = HubPrefs.hubUrl ?: throw IllegalStateException("Хаб не подключён: войдите в аккаунт заново")
        var code = 0
        var text = ""
        for (attempt in 0..1) {
            val request = Request.Builder()
                .url(hub + path)
                .post(body.toString().toRequestBody(JSON_TYPE))
                .apply { HubPrefs.cookie?.let { header("Cookie", it) } }
                .build()
            client.newCall(request).execute().use { response ->
                code = response.code
                text = response.body?.string().orEmpty()
            }
            if (code != 401 || attempt == 1 || !relogin(hub)) break
        }
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull()
            throw IllegalStateException(if (detail.isNullOrBlank()) "Ошибка хаба ($code)" else detail)
        }
        return JSONObject(text)
    }

    private fun relogin(hub: String): Boolean {
        val user = HubPrefs.userName ?: Preferences.getUser() ?: return false
        val password = Preferences.getPassword() ?: return false
        val auth = HubClient.login(hub, user, password)
        if (!auth.ok || auth.cookie == null) return false
        HubPrefs.cookie = auth.cookie
        return true
    }
}
