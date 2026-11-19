package com.eddyizm.tempus.hub

import android.net.Uri
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.models.Child
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import org.json.JSONObject

object HubPlayback {
    private const val CATALOG_PREFIX = "aura-hub:"
    const val WAVE_SESSION = "aura_wave_session"
    private const val STREAM = "/api/listen/stream"

    @JvmStatic
    fun isCatalogSong(id: String?) = id?.startsWith(CATALOG_PREFIX) == true

    fun origin(): String? = HubPrefs.hubUrl?.toHttpUrlOrNull()?.newBuilder()
        ?.encodedPath("/")?.query(null)?.fragment(null)?.build()?.toString()?.removeSuffix("/")

    @JvmStatic
    fun streamUri(id: String?): Uri? {
        if (!isCatalogSong(id)) return null
        val hub = HubPrefs.hubUrl ?: return null
        return Uri.parse(hub + STREAM).buildUpon()
            .appendQueryParameter("ref", id!!.removePrefix(CATALOG_PREFIX)).build()
    }

    @JvmStatic
    fun artwork(song: Child): Uri? {
        if (!isCatalogSong(song.id)) return null
        val hub = HubPrefs.hubUrl?.toHttpUrlOrNull() ?: return null
        val url = song.coverArtId?.let(hub::resolve) ?: return null
        return Uri.parse(url.toString()).takeIf { url.scheme == hub.scheme && url.host == hub.host && url.port == hub.port }
    }

    @JvmStatic
    fun isHubStream(raw: String): Boolean = isHubStream(raw, HubPrefs.hubUrl)

    internal fun isHubStream(raw: String, pairedHub: String?): Boolean {
        val url = raw.toHttpUrlOrNull() ?: return false
        val hub = pairedHub?.toHttpUrlOrNull() ?: return false
        return url.scheme == hub.scheme && url.host == hub.host && url.port == hub.port &&
            url.encodedPath == hub.encodedPath.trimEnd('/') + STREAM
    }

    @JvmStatic
    fun streamHeaders() = Interceptor { chain ->
        val request = chain.request()
        val cookie = HubPrefs.cookie
        if (cookie != null && isHubStream(request.url.toString())) {
            chain.proceed(request.newBuilder().header("Cookie", cookie).build())
        } else chain.proceed(request)
    }

    fun fromJson(track: JSONObject, waveSession: String? = null): Child? {
        val id = track.clean("song_id") ?: track.clean("nd_song_id")
        val ref = track.clean("ref")
        val song = when {
            id != null -> runCatching {
                App.getSubsonicClientInstance(false).browsingClient.getSong(id).execute().body()?.subsonicResponse?.song
            }.getOrNull() ?: Child(id, coverArtId = id)
            ref != null && ref.length <= 512 -> Child(CATALOG_PREFIX + ref, coverArtId = track.clean("cover_url"))
            else -> return null
        }
        if (song.title == null) song.title = track.clean("title") ?: song.id
        if (song.artist == null) song.artist = track.clean("artist")
        if (song.album == null) song.album = track.clean("album_title") ?: track.clean("album")
        if (song.duration == null) song.duration = track.optInt("duration_s").takeIf { it > 0 }
        if (song.type == null) song.type = "music"
        song.auraWaveSession = waveSession
        return song
    }

    private fun JSONObject.clean(key: String) = optString(key).takeIf { it.isNotBlank() && it != "null" }
}
