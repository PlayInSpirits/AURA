package com.eddyizm.tempus.hub

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import com.eddyizm.tempus.BuildConfig
import com.eddyizm.tempus.service.MediaManager
import com.eddyizm.tempus.service.MediaServiceExtension
import com.eddyizm.tempus.service.MediaServiceExtensionRegistry
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.util.MappingUtil
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * AURA_2 "My Wave" played by the app's own player, so it keeps going on every tab and in the
 * background (it used to live in the Hub web page, which stopped when the tab was left).
 *
 * The Hub picks each batch using playback events and the tracks still ahead in the queue.
 */
object HubWave : MediaServiceExtension {
    private const val TAG = "HubWave"
    private const val POLL_MS = 4000L
    private const val MAX_POLLS = 15
    private const val MAX_EVENTS = 500
    private val clientName = "app/${BuildConfig.VERSION_NAME}".take(32)

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private class Session(val id: String, val refs: MutableMap<String, String>) {
        val startedIds = mutableSetOf<String>()
        val startedRefs = mutableSetOf<String>()
        val finishedIds = mutableSetOf<String>()
        val unreported = mutableListOf<JSONObject>()
        var fetching = false
        var polls = 0
        var retry: Runnable? = null
    }
    private var active: Session? = null

    /** True while a wave started from the Home button owns the queue. */
    fun isActive(): Boolean = active != null

    fun ownsItem(item: MediaItem?): Boolean =
        active?.id?.let { item?.mediaMetadata?.extras?.getString(HubPlayback.WAVE_SESSION) == it } == true

    fun stop() {
        MediaManager.beginQueueSelection()
        active?.retry?.let(main::removeCallbacks)
        active = null
        if (MediaServiceExtensionRegistry.handler === this) MediaServiceExtensionRegistry.handler = null
    }

    /**
     * Starts a new wave and plays it. [done] runs on the main thread with null on success or a message
     * to show.
     */
    fun start(browser: ListenableFuture<MediaBrowser>, done: (String?) -> Unit) {
        startWith(browser, null, done)
    }

    fun startFromHub(browser: ListenableFuture<MediaBrowser>, answer: JSONObject, done: (String?) -> Unit) {
        startWith(browser, answer, done)
    }

    private fun startWith(browser: ListenableFuture<MediaBrowser>, initial: JSONObject?, done: (String?) -> Unit) {
        val selection = MediaManager.beginQueueSelection()
        worker.execute {
            val result = runCatching { startBlocking(selection, initial) }
            main.post {
                if (!MediaManager.isQueueSelectionCurrent(selection)) {
                    done(null)
                    return@post
                }
                result.fold(
                    onSuccess = { (session, batch) ->
                        active?.retry?.let(main::removeCallbacks)
                        active = Session(session, batch.refs.toMutableMap())
                        MediaServiceExtensionRegistry.handler = this
                        MediaManager.startSelectedQueue(browser, batch.songs, 0, 0L, selection)
                        done(null)
                    },
                    onFailure = { e ->
                        Log.w(TAG, "start failed", e)
                        done(e.message ?: "Не удалось запустить волну")
                    },
                )
            }
        }
    }

    private fun startBlocking(selection: Long, initial: JSONObject?): Pair<String, Batch> {
        val body = JSONObject().put("origin", "home_card").put("client", clientName)
        var answer = initial ?: post("/api/wave/start", body)
        val session = answer.optString("session_id").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Хаб не вернул сессию волны")
        var batch = toSongs(answer, session)
        var polls = 0
        while (batch.songs.isEmpty() && answer.optBoolean("pending", true) && polls < MAX_POLLS) {
            // The hub may still be choosing: ask again a little later.
            Thread.sleep(POLL_MS)
            check(MediaManager.isQueueSelectionCurrent(selection))
            polls++
            answer = post(nextPath(session), nextBody(emptyList(), emptyList()))
            batch = toSongs(answer, session)
        }
        if (batch.songs.isEmpty()) throw IllegalStateException("Хаб пока не нашёл подходящих треков")
        return session to batch
    }

    override fun handle(player: Player, item: MediaItem, queueTarget: MediaManager.QueueTarget): Boolean =
        ownsItem(item)

    override fun onPlaybackChanged(player: Player, queueTarget: MediaManager.QueueTarget) {
        val session = active ?: return
        val item = player.currentMediaItem
        val current = item != null && ownsItem(item)
        if (current && !player.isPlaying && player.playbackState != Player.STATE_ENDED) return
        if (item != null && current && player.isPlaying) {
            val ref = session.refs[item.mediaId] ?: return
            if (session.startedIds.add(item.mediaId)) {
                session.startedRefs.add(ref)
                session.unreported.add(event(ref, "start", player.currentPosition))
                session.polls = 0
            }
        }
        if (session.unreported.isNotEmpty()) requestNext(session, queueTarget)
    }

    override fun onTrackEnded(item: MediaItem, positionMs: Long, completed: Boolean) {
        val session = active ?: return
        val ref = session.refs[item.mediaId] ?: return
        if (!ownsItem(item) || item.mediaId !in session.startedIds || !session.finishedIds.add(item.mediaId)) return
        val duration = item.mediaMetadata.extras?.getInt("duration")?.takeIf { it > 0 }
        val finished = completed || (duration != null && positionMs >= duration * 900L)
        session.unreported.add(event(ref, if (finished) "finish" else "skip", positionMs))
    }

    private fun requestNext(session: Session, queueTarget: MediaManager.QueueTarget) {
        if (session.fetching || active !== session) return
        val player = queueTarget.livePlayer() ?: return
        session.retry?.let(main::removeCallbacks)
        session.retry = null
        val events = session.unreported.take(MAX_EVENTS)
        session.unreported.subList(0, events.size).clear()
        val queued = if (ownsItem(player.currentMediaItem)) {
            (player.currentMediaItemIndex + 1 until player.mediaItemCount).asSequence()
                .mapNotNull { session.refs[player.getMediaItemAt(it).mediaId] }.take(50).toList()
        } else emptyList()
        session.fetching = true
        worker.execute {
            val result = runCatching {
                val answer = post(nextPath(session.id), nextBody(events, queued))
                val batch = toSongs(answer, session.id)
                val songs = batch.songs.mapNotNull { song -> MappingUtil.mapMediaItem(song)?.let { song to it } }
                NextBatch(batch.refs, songs, answer.optBoolean("pending"))
            }.onFailure { Log.w(TAG, "next failed", it) }
            main.post {
                if (active !== session) return@post
                val reply = result.getOrNull()
                val expired = (result.exceptionOrNull() as? HubApi.HubException)?.code == 404
                if (reply == null && !expired) session.unreported.addAll(0, events)
                val live = queueTarget.livePlayer()
                if (live == null) {
                    session.fetching = false
                    return@post
                }
                if (ownsItem(live.currentMediaItem) && reply != null) applyBatch(session, live, queueTarget, reply)
                session.fetching = false
                if (reply != null && session.unreported.isNotEmpty()) {
                    requestNext(session, queueTarget)
                } else if (!expired && (reply == null || (reply.pending && reply.songs.isEmpty())) &&
                    ownsItem(live.currentMediaItem) && session.polls++ < MAX_POLLS) {
                    val retry = Runnable { requestNext(session, queueTarget) }
                    session.retry = retry
                    main.postDelayed(retry, POLL_MS)
                }
            }
        }
    }

    private fun applyBatch(session: Session, player: Player, target: MediaManager.QueueTarget, reply: NextBatch) {
        val start = player.currentMediaItemIndex + 1
        if (start <= 0 || (start until player.mediaItemCount).any { !ownsItem(player.getMediaItemAt(it)) }) return
        val fresh = reply.songs.filter { (song, _) ->
            song.id !in session.startedIds && reply.refs[song.id] !in session.startedRefs &&
                song.id != player.currentMediaItem?.mediaId
        }
        if (fresh.isEmpty()) return
        session.refs.putAll(reply.refs)
        val upcoming = (start until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        if (upcoming == fresh.map { it.first.id }) return
        val endedWhileWaiting = player.playbackState == Player.STATE_ENDED && player.playWhenReady
        MediaManager.replaceUpcoming(target, start, fresh.map { it.first }, fresh.map { it.second })
        if (endedWhileWaiting && player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    private fun event(ref: String, kind: String, positionMs: Long): JSONObject =
        JSONObject().put("event_id", UUID.randomUUID().toString())
            .put("ts", timestamp.format(Date())).put("client", clientName)
            .put(if (ref.startsWith("nd:")) "nd_song_id" else "ref", ref.removePrefix("nd:"))
            .put("kind", kind).apply {
                if (positionMs != C.TIME_UNSET) put("position_s", (positionMs / 1000.0).coerceIn(0.0, 86400.0))
            }

    private fun nextPath(session: String) = "/api/wave/${Uri.encode(session)}/next"

    private fun nextBody(events: List<JSONObject>, queued: List<String>): JSONObject =
        JSONObject().put("played", JSONArray(events)).put("queued", JSONArray(queued))

    private data class Batch(val songs: List<Child>, val refs: Map<String, String>)
    private data class NextBatch(val refs: Map<String, String>, val songs: List<Pair<Child, MediaItem>>, val pending: Boolean)

    private fun toSongs(answer: JSONObject, session: String): Batch {
        val tracks = answer.optJSONArray("tracks") ?: return Batch(emptyList(), emptyMap())
        val out = ArrayList<Child>()
        val batchRefs = LinkedHashMap<String, String>()
        for (i in 0 until tracks.length()) {
            val t = tracks.optJSONObject(i) ?: continue
            val song = HubPlayback.fromJson(t, session) ?: continue
            if (batchRefs.containsKey(song.id)) continue
            batchRefs[song.id] = t.optString("ref").takeUnless { it.isBlank() || it == "null" } ?: "nd:${song.id}"
            out.add(song)
        }
        return Batch(out, batchRefs)
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
            throw HubApi.HubException(if (detail.isNullOrBlank()) "Ошибка хаба ($code)" else detail, code)
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
