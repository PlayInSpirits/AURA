package com.eddyizm.tempus.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eddyizm.tempus.hub.HubCatalog
import com.eddyizm.tempus.subsonic.models.Child
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a screen part shows while its data loads. */
sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Error(val message: String) : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
}

enum class SearchTab(val title: String) { CATALOG("Каталог"), FILES("Файлы"), REUPLOADS("Без цензуры") }

sealed interface SearchPage {
    data object Results : SearchPage
    data class ArtistScreen(val ref: String, val name: String) : SearchPage
    data class AlbumScreen(val ref: String, val title: String) : SearchPage
}

enum class DownloadState { BUSY, QUEUED }

/**
 * AURA: state of the «Поиск» tab, the hub's search page ported to the app. It lives as long as the
 * activity, so the query, the results and the open artist/album survive switching tabs.
 */
class AuraSearchViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    var tab by mutableStateOf(SearchTab.CATALOG)
        private set

    /** The query the results belong to (trimmed, after the pause in typing). */
    var activeQuery by mutableStateOf("")
        private set

    val pages = mutableStateListOf<SearchPage>(SearchPage.Results)

    var history by mutableStateOf<Load<List<String>>>(Load.Idle)
        private set
    var catalog by mutableStateOf<Load<HubCatalog.SearchResult>>(Load.Idle)
        private set
    val moreTracks = mutableStateListOf<HubCatalog.Track>()
    var nextOffset by mutableStateOf<Int?>(null)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var moreFailed by mutableStateOf(false)
        private set
    var files by mutableStateOf<Load<HubCatalog.FilesResult>>(Load.Idle)
        private set
    var artist by mutableStateOf<Load<HubCatalog.ArtistPage>>(Load.Idle)
        private set
    var album by mutableStateOf<Load<HubCatalog.AlbumPage>>(Load.Idle)
        private set

    /** Downloads asked for from this screen, by track ref / album ref / file key. */
    val downloads = mutableStateMapOf<String, DownloadState>()

    /** One-off messages for a toast; [messageId] changes with each one. */
    var message by mutableStateOf<String?>(null)
        private set
    var messageId by mutableStateOf(0)
        private set

    private var searchJob: Job? = null
    private var filesJob: Job? = null
    private var moreJob: Job? = null
    private var pageJob: Job? = null
    private var filesKey = ""

    fun onQueryChange(value: String) {
        query = value.take(512)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            applyQuery(query.trim())
        }
    }

    /** The keyboard's search key: no need to wait for the pause. */
    fun submit() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { applyQuery(query.trim()) }
    }

    fun pick(q: String) {
        query = q
        submit()
    }

    fun clearQuery() {
        query = ""
        searchJob?.cancel()
        activeQuery = ""
        loadHistory()
    }

    fun selectTab(value: SearchTab) {
        if (tab == value) return
        tab = value
        if (activeQuery.isNotEmpty()) loadTab(activeQuery, force = false)
    }

    private suspend fun applyQuery(q: String) {
        if (q == activeQuery && catalog !is Load.Error && files !is Load.Error) return
        activeQuery = q
        if (q.isEmpty()) {
            loadHistory()
            return
        }
        if (q.length >= 2) {
            viewModelScope.launch {
                runCatching { io { HubCatalog.remember(q) } }
            }
        }
        loadTab(q, force = true)
    }

    private fun loadTab(q: String, force: Boolean) {
        when (tab) {
            SearchTab.CATALOG -> if (force || catalog !is Load.Ready) loadCatalog(q)
            SearchTab.FILES, SearchTab.REUPLOADS -> loadFiles(q, tab)
        }
    }

    fun retry() {
        if (activeQuery.isEmpty()) loadHistory() else loadTab(activeQuery, force = true)
    }

    fun loadHistory() {
        if (history !is Load.Ready) history = Load.Loading
        viewModelScope.launch {
            history = try {
                Load.Ready(io { HubCatalog.history() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Error(e.message ?: "Не удалось загрузить историю")
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                io { HubCatalog.clearHistory() }
                history = Load.Ready(emptyList())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("Не удалось очистить историю. ${e.message ?: ""}")
            }
        }
    }

    private fun loadCatalog(q: String) {
        moreJob?.cancel()
        // The previous results stay on screen while the new ones load.
        if (catalog !is Load.Ready) catalog = Load.Loading
        viewModelScope.launch {
            try {
                val result = io { HubCatalog.search(q) }
                if (q != activeQuery) return@launch
                catalog = Load.Ready(result)
                moreTracks.clear()
                moreTracks.addAll(result.tracks.distinctBy { it.ref })
                nextOffset = result.tracksNext
                loadingMore = false
                moreFailed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (q == activeQuery) catalog = Load.Error(e.message ?: "Поиск не удался")
            }
        }
    }

    /** The next page of tracks, when the list is scrolled near its end. */
    fun loadMore() {
        val offset = nextOffset ?: return
        if (loadingMore || moreFailed) return
        val q = activeQuery
        loadingMore = true
        moreJob = viewModelScope.launch {
            try {
                val page = io { HubCatalog.moreTracks(q, offset) }
                if (q != activeQuery) return@launch
                val seen = moreTracks.map { it.ref }.toHashSet()
                moreTracks.addAll(page.tracks.filter { seen.add(it.ref) })
                nextOffset = page.next
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                moreFailed = true
            } finally {
                loadingMore = false
            }
        }
    }

    fun retryMore() {
        moreFailed = false
        loadMore()
    }

    private fun loadFiles(q: String, which: SearchTab) {
        val key = "$which|$q"
        if (key == filesKey && files is Load.Ready) return
        filesKey = key
        filesJob?.cancel()
        files = Load.Loading
        filesJob = viewModelScope.launch {
            files = try {
                Load.Ready(io { HubCatalog.files(q, if (which == SearchTab.REUPLOADS) "reuploads" else "sources") })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                filesKey = ""
                Load.Error(e.message ?: "Поиск файлов не удался")
            }
        }
    }

    // ---- artist and album screens ----

    fun openArtist(ref: String, name: String) {
        pages.add(SearchPage.ArtistScreen(ref, name))
        artist = Load.Loading
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            artist = try {
                Load.Ready(io { HubCatalog.artistPage(ref) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Error(e.message ?: "Не удалось открыть исполнителя")
            }
        }
    }

    fun openAlbum(ref: String, title: String) {
        pages.add(SearchPage.AlbumScreen(ref, title))
        album = Load.Loading
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            album = try {
                Load.Ready(io { HubCatalog.albumPage(ref) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Error(e.message ?: "Не удалось открыть альбом")
            }
        }
    }

    fun reloadPage() {
        when (val page = pages.lastOrNull()) {
            is SearchPage.ArtistScreen -> { pages.removeAt(pages.lastIndex); openArtist(page.ref, page.name) }
            is SearchPage.AlbumScreen -> { pages.removeAt(pages.lastIndex); openAlbum(page.ref, page.title) }
            else -> retry()
        }
    }

    /** Back inside the tab; false when already on the results. */
    fun back(): Boolean {
        if (pages.size <= 1) return false
        pages.removeAt(pages.lastIndex)
        // An artist under the album that is closed now: show it again.
        when (val top = pages.last()) {
            is SearchPage.ArtistScreen -> {
                val shown = (artist as? Load.Ready)?.data?.artist?.ref
                if (shown != top.ref) { pages.removeAt(pages.lastIndex); openArtist(top.ref, top.name) }
            }
            is SearchPage.AlbumScreen -> {
                val shown = (album as? Load.Ready)?.data?.album?.ref
                if (shown != top.ref) { pages.removeAt(pages.lastIndex); openAlbum(top.ref, top.title) }
            }
            else -> Unit
        }
        return true
    }

    // ---- downloads ----

    fun downloadTrack(t: HubCatalog.Track) = download(t.ref) { HubCatalog.downloadTrack(t.ref) }

    fun downloadAlbum(ref: String) = download(ref) { HubCatalog.downloadAlbum(ref) }

    fun downloadArtist(ref: String) = download(ref) { HubCatalog.downloadArtist(ref) }

    fun downloadFile(c: HubCatalog.Candidate) = download(c.key) { HubCatalog.downloadFile(c) }

    private fun download(key: String, call: () -> String) {
        if (downloads[key] != null) return
        downloads[key] = DownloadState.BUSY
        viewModelScope.launch {
            try {
                val text = io(call)
                downloads[key] = DownloadState.QUEUED
                say(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                downloads.remove(key)
                say("Не удалось скачать: ${e.message ?: ""}")
            }
        }
    }

    // ---- playing (songs the library has) ----

    /**
     * Plays [tracks] (those with a Navidrome song) from [start] in the app's player. [play] gets the
     * songs on the main thread.
     */
    fun play(tracks: List<Triple<String, String, String>>, durations: List<Int?>, start: Int, play: (List<Child>, Int) -> Unit) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            try {
                val songs = io { HubCatalog.toSongs(tracks, durations, start) }
                play(songs, start.coerceIn(0, songs.lastIndex))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("Не удалось включить: ${e.message ?: ""}")
            }
        }
    }

    fun say(text: String) {
        message = text
        messageId++
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
