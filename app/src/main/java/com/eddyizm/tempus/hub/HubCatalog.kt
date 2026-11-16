package com.eddyizm.tempus.hub

import android.util.Log
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.models.Child
import org.json.JSONArray
import org.json.JSONObject

/**
 * AURA: the hub's catalog search (the «Поиск» tab of the hub, ported to the app).
 *
 * Same endpoints as the hub web app's Search page:
 *  - GET    /api/search?q=                      library songs + Deezer artists, albums and tracks
 *  - GET    /api/search/tracks?q=&offset=        more tracks of the same search
 *  - GET    /api/search/providers?q=&group=      raw files (sources / re-uploads)
 *  - GET    /api/catalog/artist/{ref}, /api/catalog/album/{ref}
 *  - GET/POST/DELETE /api/search/history         recent queries of the account
 *  - POST   /api/downloads                       download to the library
 */
object HubCatalog {
    private const val TAG = "HubCatalog"

    data class LibTrack(
        val id: Long,
        val ndSongId: String?,
        val artist: String,
        val title: String,
        val album: String?,
        val durationS: Int?,
    )

    data class Artist(val ref: String, val name: String, val picture: String?, val fans: Int?)

    data class Album(
        val ref: String,
        val title: String,
        val artist: String,
        val artistRef: String?,
        val recordType: String,
        val year: Int?,
        val cover: String?,
        val coverBig: String?,
        val nbTracks: Int?,
        val ownedStatus: String,
        val explicit: Boolean,
    )

    data class Track(
        val ref: String,
        val title: String,
        val artist: String,
        val artistRef: String?,
        val albumTitle: String?,
        val albumRef: String?,
        val durationS: Int?,
        val cover: String?,
        val explicit: Boolean,
        val owned: Boolean,
        val ndSongId: String?,
        val downloading: Boolean,
        val trackNo: Int?,
    )

    data class Candidate(
        val raw: JSONObject,
        val key: String,
        val artist: String,
        val title: String,
        val provider: String,
        val durationS: Int?,
        val bitrate: Int?,
        val cover: String?,
        val channel: String?,
        val uncensored: Boolean?,
    )

    data class SearchResult(
        val library: List<LibTrack>,
        val artists: List<Artist>,
        val albums: List<Album>,
        val tracks: List<Track>,
        val tracksNext: Int?,
        val errors: List<String>,
    )

    data class TracksPage(val tracks: List<Track>, val next: Int?)

    data class ArtistPage(
        val artist: Artist,
        val pictureBig: String?,
        val topTracks: List<Track>,
        val albums: List<Album>,
        val singles: List<Album>,
        val compilations: List<Album>,
    )

    data class AlbumPage(val album: Album, val tracks: List<Track>)

    data class FilesResult(val candidates: List<Candidate>, val errors: List<String>)

    // ---- calls (blocking) ----

    fun search(q: String): SearchResult {
        val o = HubApi.getJson("/api/search?q=${HubApi.enc(q)}")
        return SearchResult(
            library = o.optJSONArray("library").objects().map(::libTrack),
            artists = o.optJSONArray("artists").objects().map(::artist),
            albums = o.optJSONArray("albums").objects().map(::album),
            tracks = o.optJSONArray("tracks").objects().map(::track),
            tracksNext = o.optIntOrNull("tracks_next"),
            errors = o.optJSONArray("errors").strings(),
        )
    }

    fun moreTracks(q: String, offset: Int): TracksPage {
        val o = HubApi.getJson("/api/search/tracks?q=${HubApi.enc(q)}&offset=$offset")
        return TracksPage(o.optJSONArray("tracks").objects().map(::track), o.optIntOrNull("next_offset"))
    }

    /** [group] is `sources` (regular files) or `reuploads` (small YouTube / SoundCloud channels). */
    fun files(q: String, group: String): FilesResult {
        val o = HubApi.getJson("/api/search/providers?q=${HubApi.enc(q)}&group=$group")
        return FilesResult(o.optJSONArray("candidates").objects().map(::candidate), o.optJSONArray("errors").strings())
    }

    fun artistPage(ref: String): ArtistPage {
        val o = HubApi.getJson("/api/catalog/artist/${HubApi.enc(ref)}")
        val a = o.optJSONObject("artist") ?: JSONObject()
        return ArtistPage(
            artist = artist(a),
            pictureBig = HubApi.absolute(a.optStr("picture_url")),
            topTracks = o.optJSONArray("top_tracks").objects().map(::track),
            albums = o.optJSONArray("albums").objects().map(::album),
            singles = o.optJSONArray("singles").objects().map(::album),
            compilations = o.optJSONArray("compilations").objects().map(::album),
        )
    }

    fun albumPage(ref: String): AlbumPage {
        val o = HubApi.getJson("/api/catalog/album/${HubApi.enc(ref)}")
        return AlbumPage(album(o), o.optJSONArray("tracks").objects().map(::track))
    }

    fun history(): List<String> = HubApi.getJson("/api/search/history").optJSONArray("queries").strings()

    fun remember(q: String) {
        HubApi.post("/api/search/history", JSONObject().put("query", q))
    }

    fun clearHistory() {
        HubApi.delete("/api/search/history")
    }

    /** Puts a download in the hub's queue; returns what to tell the user. */
    fun download(body: JSONObject): String {
        val o = HubApi.postJson("/api/downloads", body)
        val queued = o.optInt("queued", 0)
        val owned = o.optInt("already_owned", 0)
        return when {
            o.optBoolean("expanding", false) -> "Загрузка поставлена в очередь"
            queued > 0 && owned > 0 -> "В очереди: $queued, уже в библиотеке: $owned"
            queued > 0 -> if (queued == 1) "Трек поставлен в очередь загрузки" else "В очереди загрузки: $queued"
            owned > 0 -> "Уже в вашей библиотеке"
            else -> "Загрузка поставлена в очередь"
        }
    }

    fun downloadTrack(ref: String) = download(JSONObject().put("track_refs", JSONArray().put(ref)))

    fun downloadAlbum(ref: String) = download(JSONObject().put("album_ref", ref))

    fun downloadArtist(ref: String) = download(JSONObject().put("artist_ref", ref))

    fun downloadFile(c: Candidate): String {
        val item = JSONObject()
            .put("artist", c.artist)
            .put("title", c.title)
            .put("explicit", c.raw.opt("explicit") ?: JSONObject.NULL)
            .put("duration_s", c.raw.opt("duration_s") ?: JSONObject.NULL)
            .put("candidate", c.raw)
        return download(JSONObject().put("items", JSONArray().put(item)))
    }

    /** Songs for the app's player: the first one with full data from Navidrome, the rest from the hub's names. */
    fun toSongs(items: List<Triple<String, String, String>>, durations: List<Int?>, first: Int): List<Child> {
        val out = ArrayList<Child>()
        for ((i, t) in items.withIndex()) {
            val (id, title, artist) = t
            val full = if (i == first) loadSong(id) else null
            out.add(full ?: Child(id).apply {
                this.title = title
                this.artist = artist
                coverArtId = id
                type = "music"
                durations.getOrNull(i)?.let { if (it > 0) duration = it }
            })
        }
        return out
    }

    private fun loadSong(id: String): Child? = try {
        App.getSubsonicClientInstance(false).browsingClient.getSong(id).execute()
            .body()?.subsonicResponse?.song
    } catch (e: Exception) {
        Log.w(TAG, "getSong $id failed", e)
        null
    }

    // ---- parsing ----

    private fun libTrack(o: JSONObject) = LibTrack(
        id = o.optLong("id"),
        ndSongId = o.optStr("nd_song_id"),
        artist = o.optString("artist"),
        title = o.optString("title"),
        album = o.optStr("album"),
        durationS = o.optIntOrNull("duration_s"),
    )

    private fun artist(o: JSONObject) = Artist(
        ref = o.optString("ref"),
        name = o.optString("name"),
        picture = HubApi.absolute(o.optStr("picture_small_url") ?: o.optStr("picture_url")),
        fans = o.optIntOrNull("nb_fans"),
    )

    private fun album(o: JSONObject) = Album(
        ref = o.optString("ref"),
        title = o.optString("title"),
        artist = o.optString("artist"),
        artistRef = o.optStr("artist_ref"),
        recordType = o.optString("record_type", "album"),
        year = o.optIntOrNull("year") ?: o.optStr("release_date")?.take(4)?.toIntOrNull(),
        cover = HubApi.absolute(o.optStr("cover_small_url") ?: o.optStr("cover_url")),
        coverBig = HubApi.absolute(o.optStr("cover_url") ?: o.optStr("cover_small_url")),
        nbTracks = o.optIntOrNull("nb_tracks"),
        ownedStatus = o.optString("owned_status", "none"),
        explicit = o.optBoolean("explicit", false),
    )

    private fun track(o: JSONObject) = Track(
        ref = o.optString("ref"),
        title = o.optString("title"),
        artist = o.optString("artist"),
        artistRef = o.optStr("artist_ref"),
        albumTitle = o.optStr("album_title"),
        albumRef = o.optStr("album_ref"),
        durationS = o.optIntOrNull("duration_s"),
        cover = HubApi.absolute(o.optStr("cover_small_url") ?: o.optStr("cover_url")),
        explicit = o.optBoolean("explicit", false),
        owned = o.optBoolean("owned", false),
        ndSongId = o.optStr("nd_song_id"),
        downloading = o.optBoolean("downloading", false),
        trackNo = o.optIntOrNull("track_no"),
    )

    private fun candidate(o: JSONObject): Candidate {
        val extra = o.optJSONObject("extra")
        return Candidate(
            raw = o,
            key = o.optString("provider") + ":" + o.optString("provider_id"),
            artist = o.optString("artist"),
            title = o.optString("title"),
            provider = o.optString("provider"),
            durationS = o.optIntOrNull("duration_s"),
            bitrate = o.optIntOrNull("bitrate_kbps"),
            cover = HubApi.absolute(o.optStr("cover_url")),
            channel = extra?.optStr("channel"),
            uncensored = if (extra != null && extra.has("uncensored") && !extra.isNull("uncensored")) extra.optBoolean("uncensored") else null,
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        val out = ArrayList<JSONObject>(length())
        for (i in 0 until length()) optJSONObject(i)?.let { out.add(it) }
        return out
    }

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        val out = ArrayList<String>(length())
        for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let { out.add(it) }
        return out
    }

    private fun JSONObject.optStr(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        val d = optDouble(key, Double.NaN)
        return if (d.isNaN()) null else d.toInt()
    }
}
