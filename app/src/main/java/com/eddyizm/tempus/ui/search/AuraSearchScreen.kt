package com.eddyizm.tempus.ui.search

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.eddyizm.tempus.R
import com.eddyizm.tempus.hub.HubCatalog
import com.eddyizm.tempus.hub.HubPrefs
import com.eddyizm.tempus.subsonic.models.Child

/** Colors of the screen, taken from the app's View theme so both look the same. */
@Immutable
data class AuraPalette(
    val fg: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val card: Color,
    val line: Color,
)

private val LocalPalette = staticCompositionLocalOf {
    AuraPalette(Color.White, Color(0xFFB7B2C8), Color(0xFFB9A4FF), Color(0xFF1C0F47), Color(0x14FFFFFF), Color(0x1FFFFFFF))
}

private val BOTTOM_SPACE = 190.dp

@Composable
fun AuraSearchScreen(
    vm: AuraSearchViewModel,
    palette: AuraPalette,
    onPlay: (List<Child>, Int) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(vm.messageId) {
        val text = vm.message
        if (vm.messageId > 0 && text != null) Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(Unit) {
        if (vm.activeQuery.isEmpty()) vm.loadHistory()
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        AnimatedContent(
            targetState = vm.pages.last(),
            transitionSpec = {
                val forward = vm.pages.size > 1 && targetState != SearchPage.Results
                if (forward) {
                    (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it / 6 } + fadeOut(tween(180)))
                } else {
                    (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 6 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(180)))
                }
            },
            label = "searchPage",
        ) { page ->
            when (page) {
                SearchPage.Results -> ResultsPage(vm, onPlay)
                is SearchPage.ArtistScreen -> ArtistPageView(vm, page, onPlay)
                is SearchPage.AlbumScreen -> AlbumPageView(vm, page, onPlay)
            }
        }
    }
}

// ---------------------------------------------------------------- results

@Composable
private fun ResultsPage(vm: AuraSearchViewModel, onPlay: (List<Child>, Int) -> Unit) {
    val p = LocalPalette.current
    val list = rememberLazyListState()
    val tracksCount = vm.moreTracks.size
    LaunchedEffect(list, tracksCount, vm.tab, vm.activeQuery) {
        snapshotFlow {
            val info = list.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (last, total) ->
            if (vm.tab == SearchTab.CATALOG && total > 0 && last >= total - 8) vm.loadMore()
        }
    }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = BOTTOM_SPACE),
    ) {
        item(key = "header") { SearchHeader(vm) }
        if (vm.activeQuery.isEmpty()) {
            historySection(vm)
        } else when (vm.tab) {
            SearchTab.CATALOG -> catalogSection(vm, onPlay)
            SearchTab.FILES, SearchTab.REUPLOADS -> filesSection(vm)
        }
    }
}

@Composable
private fun SearchHeader(vm: AuraSearchViewModel) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 8.dp)) {
        Text("Поиск", color = p.fg, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(p.card)
                .border(1.dp, p.line, RoundedCornerShape(26.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_search), null, tint = p.muted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (vm.query.isEmpty()) {
                    Text("Исполнитель, альбом или трек", color = p.muted.copy(alpha = 0.8f), fontSize = 16.sp, maxLines = 1)
                }
                BasicTextField(
                    value = vm.query,
                    onValueChange = vm::onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = p.fg, fontSize = 16.sp),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        vm.submit()
                        focus.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(visible = vm.query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    "Очистить",
                    tint = p.muted,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { vm.clearQuery() }
                        .padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchTab.entries.forEach { t -> TabChip(t.title, vm.tab == t) { vm.selectTab(t) } }
        }
    }
}

@Composable
private fun TabChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bg by animateColorAsState(if (selected) p.accent else p.card, tween(220), label = "chipBg")
    val fg by animateColorAsState(if (selected) p.onAccent else p.fg, tween(220), label = "chipFg")
    Box(
        Modifier
            .bouncy(onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(title, color = fg, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

private fun LazyListScope.historySection(vm: AuraSearchViewModel) {
    when (val h = vm.history) {
        Load.Idle, Load.Loading -> item(key = "hist-load") { SkeletonRows(4) }
        is Load.Error -> item(key = "hist-err") { ErrorBox(h.message) { vm.loadHistory() } }
        is Load.Ready -> {
            if (h.data.isEmpty()) {
                item(key = "hist-empty") {
                    EmptyBox("Что будем слушать?", "Ищите по каталогу Deezer и hitmoz, по своей библиотеке или по файлам в источниках.")
                }
            } else {
                item(key = "hist-title") { SectionTitle("Недавние запросы", action = "Очистить") { vm.clearHistory() } }
                items(h.data.take(12), key = { "h:$it" }) { q ->
                    val p = LocalPalette.current
                    Row(
                        Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clickable { vm.pick(q) }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_history), null, tint = p.muted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(q, color = p.fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.catalogSection(vm: AuraSearchViewModel, onPlay: (List<Child>, Int) -> Unit) {
    when (val c = vm.catalog) {
        Load.Idle, Load.Loading -> item(key = "cat-load") { SkeletonRows(8) }
        is Load.Error -> item(key = "cat-err") { ErrorBox(c.message) { vm.retry() } }
        is Load.Ready -> {
            val data = c.data
            if (data.errors.isNotEmpty()) item(key = "cat-notice") { Notice(data.errors.joinToString(". ")) }
            if (data.library.isEmpty() && data.artists.isEmpty() && data.albums.isEmpty() && vm.moreTracks.isEmpty()) {
                item(key = "cat-empty") {
                    EmptyBox("Ничего не найдено", "Попробуйте другое написание или поищите среди файлов в источниках.")
                }
            }
            if (data.library.isNotEmpty()) {
                item(key = "lib-title") { SectionTitle("В библиотеке") }
                val shown = data.library.take(5)
                val playable = shown.filter { it.ndSongId != null }
                items(shown, key = { "lib:${it.id}" }) { t ->
                    LibraryRow(t) {
                        val idx = playable.indexOf(t)
                        if (idx >= 0) {
                            vm.play(
                                playable.map { Triple(it.ndSongId!!, it.title, it.artist) },
                                playable.map { it.durationS }, idx, onPlay,
                            )
                        }
                    }
                }
            }
            if (data.artists.isNotEmpty()) {
                item(key = "art-title") { SectionTitle("Исполнители") }
                item(key = "art-row") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(data.artists, key = { it.ref }) { a -> ArtistBubble(a) { vm.openArtist(a.ref, a.name) } }
                    }
                }
            }
            if (data.albums.isNotEmpty()) {
                item(key = "alb-title") { SectionTitle("Альбомы") }
                item(key = "alb-row") { AlbumRow(data.albums, vm) }
            }
            if (vm.moreTracks.isNotEmpty()) {
                item(key = "trk-title") { SectionTitle("Треки") }
                trackItems(vm, vm.moreTracks, "t", onPlay, showAlbum = true)
                if (vm.loadingMore) item(key = "trk-more") { SmallSpinner() }
                if (vm.moreFailed) {
                    item(key = "trk-fail") {
                        val p = LocalPalette.current
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text("Не удалось загрузить ещё", color = p.muted, fontSize = 14.sp)
                            Spacer(Modifier.width(12.dp))
                            Pill("Повторить", filled = false) { vm.retryMore() }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.trackItems(
    vm: AuraSearchViewModel,
    tracks: List<HubCatalog.Track>,
    prefix: String,
    onPlay: (List<Child>, Int) -> Unit,
    showAlbum: Boolean,
    numbered: Boolean = false,
) {
    itemsIndexed(tracks, key = { _, t -> "$prefix:${t.ref}" }) { i, t ->
        TrackRow(
            t,
            index = if (numbered) (t.trackNo ?: (i + 1)) else null,
            showAlbum = showAlbum,
            state = vm.downloads[t.ref],
            onDownload = { vm.downloadTrack(t) },
            onClick = {
                if (t.ndSongId != null) {
                    val playable = tracks.filter { it.ndSongId != null }
                    vm.play(
                        playable.map { Triple(it.ndSongId!!, it.title, it.artist) },
                        playable.map { it.durationS }, playable.indexOf(t), onPlay,
                    )
                } else if (vm.downloads[t.ref] == null && !t.downloading) {
                    vm.say("Этого трека ещё нет в библиотеке: нажмите ⤓, чтобы скачать")
                }
            },
        )
    }
}

private fun LazyListScope.filesSection(vm: AuraSearchViewModel) {
    item(key = "files-note") {
        val p = LocalPalette.current
        Text(
            if (vm.tab == SearchTab.FILES) {
                "Сырые файлы из hitmoz, hitmos.fm и YouTube. Выберите конкретный файл, если автоматический выбор не подходит."
            } else {
                "Копии с маленьких каналов YouTube и SoundCloud: там выкладывают треки, которые после закона 2026 года отредактировали в официальных версиях."
            },
            color = p.muted, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }
    when (val f = vm.files) {
        Load.Idle, Load.Loading -> item(key = "files-load") { SkeletonRows(6) }
        is Load.Error -> item(key = "files-err") { ErrorBox(f.message) { vm.retry() } }
        is Load.Ready -> {
            if (f.data.errors.isNotEmpty()) item(key = "files-notice") { Notice(f.data.errors.joinToString(". ")) }
            if (f.data.candidates.isEmpty()) {
                item(key = "files-empty") {
                    EmptyBox(if (vm.tab == SearchTab.FILES) "Файлы не найдены" else "Перезаливов не нашлось", null)
                }
            }
            items(f.data.candidates, key = { "f:${it.key}" }) { c -> CandidateRow(c, vm.downloads[c.key]) { vm.downloadFile(c) } }
        }
    }
}

// ---------------------------------------------------------------- artist / album

@Composable
private fun ArtistPageView(vm: AuraSearchViewModel, page: SearchPage.ArtistScreen, onPlay: (List<Child>, Int) -> Unit) {
    val p = LocalPalette.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BOTTOM_SPACE)) {
        when (val a = vm.artist) {
            Load.Idle, Load.Loading -> {
                item(key = "a-top") { PageTopBar(page.name) { vm.back() } }
                item(key = "a-load") { SkeletonRows(8) }
            }
            is Load.Error -> {
                item(key = "a-top") { PageTopBar(page.name) { vm.back() } }
                item(key = "a-err") { ErrorBox(a.message) { vm.reloadPage() } }
            }
            is Load.Ready -> {
                val d = a.data
                item(key = "a-hero") {
                    Box(Modifier.fillMaxWidth().height(300.dp)) {
                        Cover(d.pictureBig ?: d.artist.picture, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(Color(0x55000000), Color.Transparent, Color(0xF2000000)))
                            )
                        )
                        BackButton(Modifier.padding(12.dp).align(Alignment.TopStart)) { vm.back() }
                        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                            Text(d.artist.name, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            d.artist.fans?.let { Text("${formatCount(it)} слушателей в Deezer", color = Color(0xCCFFFFFF), fontSize = 13.sp) }
                        }
                    }
                }
                item(key = "a-actions") {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val owned = d.topTracks.filter { it.ndSongId != null }
                        if (owned.isNotEmpty()) {
                            Pill("Слушать", filled = true, icon = R.drawable.ic_play) {
                                vm.play(owned.map { Triple(it.ndSongId!!, it.title, it.artist) }, owned.map { it.durationS }, 0, onPlay)
                            }
                        }
                        DownloadPill("Скачать всё", vm.downloads[d.artist.ref]) { vm.downloadArtist(d.artist.ref) }
                    }
                }
                if (d.topTracks.isNotEmpty()) {
                    item(key = "a-top-title") { SectionTitle("Популярные треки") }
                    trackItems(vm, d.topTracks.take(10), "at", onPlay, showAlbum = true)
                }
                listOf("Альбомы" to d.albums, "Синглы и EP" to d.singles, "Сборники" to d.compilations).forEach { (title, list) ->
                    if (list.isNotEmpty()) {
                        item(key = "a-$title") { SectionTitle(title) }
                        item(key = "a-$title-row") { AlbumRow(list, vm) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumPageView(vm: AuraSearchViewModel, page: SearchPage.AlbumScreen, onPlay: (List<Child>, Int) -> Unit) {
    val p = LocalPalette.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BOTTOM_SPACE)) {
        item(key = "al-top") { PageTopBar("") { vm.back() } }
        when (val a = vm.album) {
            Load.Idle, Load.Loading -> item(key = "al-load") { SkeletonRows(8) }
            is Load.Error -> item(key = "al-err") { ErrorBox(a.message) { vm.reloadPage() } }
            is Load.Ready -> {
                val d = a.data
                item(key = "al-hero") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Cover(d.album.coverBig ?: d.album.cover, Modifier.size(232.dp), RoundedCornerShape(20.dp))
                        Spacer(Modifier.height(18.dp))
                        Text(d.album.title, color = p.fg, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            d.album.artist,
                            color = p.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = d.album.artistRef != null) {
                                d.album.artistRef?.let { vm.openArtist(it, d.album.artist) }
                            }.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        Text(
                            listOfNotNull(recordTypeTitle(d.album.recordType), d.album.year?.toString(), "${d.tracks.size} ${plural(d.tracks.size, "трек", "трека", "треков")}").joinToString(" · "),
                            color = p.muted, fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val owned = d.tracks.filter { it.ndSongId != null }
                            if (owned.isNotEmpty()) {
                                Pill("Слушать", filled = true, icon = R.drawable.ic_play) {
                                    vm.play(owned.map { Triple(it.ndSongId!!, it.title, it.artist) }, owned.map { it.durationS }, 0, onPlay)
                                }
                            }
                            if (d.album.ownedStatus != "full") {
                                DownloadPill("Скачать альбом", vm.downloads[d.album.ref]) { vm.downloadAlbum(d.album.ref) }
                            } else {
                                Pill("В библиотеке", filled = false, icon = R.drawable.ic_check_circle) {}
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                trackItems(vm, d.tracks, "al", onPlay, showAlbum = false, numbered = true)
            }
        }
    }
}

@Composable
private fun PageTopBar(title: String, onBack: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        BackButton(Modifier, onBack)
        Spacer(Modifier.width(8.dp))
        Text(title, color = p.fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BackButton(modifier: Modifier, onBack: () -> Unit) {
    Box(
        modifier
            .size(42.dp)
            .bouncy(onBack)
            .clip(CircleShape)
            .background(Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_arrow_back), "Назад", tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------- rows and cards

@Composable
private fun LibraryRow(t: HubCatalog.LibTrack, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = t.ndSongId != null, onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(p.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            if (t.ndSongId != null) {
                Cover(navidromeCover(t.ndSongId), Modifier.size(52.dp), RoundedCornerShape(12.dp))
            } else {
                Icon(painterResource(R.drawable.ic_check_circle), null, tint = p.accent, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = p.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(t.artist, t.album).joinToString(" · "), color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(duration(t.durationS), color = p.muted, fontSize = 12.sp)
        if (t.ndSongId != null) {
            Spacer(Modifier.width(10.dp))
            Icon(painterResource(R.drawable.ic_play), "Слушать", tint = p.accent, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun TrackRow(
    t: HubCatalog.Track,
    index: Int?,
    showAlbum: Boolean,
    state: DownloadState?,
    onDownload: () -> Unit,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(index.toString(), color = p.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
            Spacer(Modifier.width(8.dp))
        } else {
            Cover(t.cover, Modifier.size(52.dp), RoundedCornerShape(12.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.title, color = if (t.owned) p.fg else p.fg.copy(alpha = 0.92f), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (t.explicit) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "E", color = p.fg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(p.muted.copy(alpha = 0.35f)).padding(horizontal = 4.dp),
                    )
                }
            }
            val sub = listOfNotNull(t.artist, if (showAlbum) t.albumTitle else null).joinToString(" · ")
            Text(sub, color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(duration(t.durationS), color = p.muted, fontSize = 12.sp)
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            when {
                t.owned -> Icon(painterResource(R.drawable.ic_check_circle), "В библиотеке", tint = p.accent, modifier = Modifier.size(20.dp))
                state == DownloadState.BUSY -> CircularProgressIndicator(Modifier.size(20.dp), color = p.accent, strokeWidth = 2.dp)
                state == DownloadState.QUEUED || t.downloading ->
                    Icon(painterResource(R.drawable.ic_done), "В очереди", tint = p.accent, modifier = Modifier.size(22.dp))
                else -> Icon(
                    painterResource(R.drawable.ic_file_download), "Скачать", tint = p.fg,
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDownload).padding(9.dp),
                )
            }
        }
    }
}

@Composable
private fun CandidateRow(c: HubCatalog.Candidate, state: DownloadState?, onDownload: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(c.cover, Modifier.size(48.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, color = p.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(c.artist, color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                providerTitle(c.provider),
                c.bitrate?.let { "$it кбит/с" },
                c.durationS?.let { duration(it) },
                c.channel,
                when (c.uncensored) { true -> "без цензуры"; false -> "без мата"; null -> null },
            ).joinToString(" · ")
            Text(meta, color = p.muted.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        DownloadPill("Скачать", state, onDownload)
    }
}

@Composable
private fun ArtistBubble(a: HubCatalog.Artist, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.width(104.dp).bouncy(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(a.picture, Modifier.size(104.dp), CircleShape)
        Spacer(Modifier.height(8.dp))
        Text(a.name, color = p.fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlbumRow(list: List<HubCatalog.Album>, vm: AuraSearchViewModel) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(list, key = { it.ref }) { a -> AlbumCard(a) { vm.openAlbum(a.ref, a.title) } }
    }
}

@Composable
private fun AlbumCard(a: HubCatalog.Album, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.width(148.dp).bouncy(onClick)) {
        Box {
            Cover(a.cover, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(16.dp))
            if (a.ownedStatus != "none") {
                Box(
                    Modifier.padding(8.dp).align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(p.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_done), null, tint = p.onAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(a.title, color = p.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(a.artist, a.year?.toString()).joinToString(" · "), color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- small parts

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun Cover(url: String?, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
    val p = LocalPalette.current
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(p.accent.copy(alpha = 0.30f), p.card)))) {
        if (url != null) {
            GlideImage(
                model = glideModel(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Hub pictures need the session cookie; others (Deezer CDN) are public. */
private fun glideModel(url: String): Any {
    val hub = HubPrefs.hubUrl
    val cookie = HubPrefs.cookie
    if (hub != null && cookie != null && url.startsWith(hub)) {
        return GlideUrl(url, LazyHeaders.Builder().addHeader("Cookie", cookie).build())
    }
    return url
}

private fun navidromeCover(songId: String): String? = try {
    com.eddyizm.tempus.glide.CustomGlideRequest.createUrl(songId, 160)
} catch (e: Exception) {
    null
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = p.fg, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action, color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Pill(text: String, filled: Boolean, icon: Int? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .bouncy(onClick)
            .clip(RoundedCornerShape(22.dp))
            .background(if (filled) p.accent else p.card)
            .border(1.dp, if (filled) Color.Transparent else p.line, RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = if (filled) p.onAccent else p.fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (filled) p.onAccent else p.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DownloadPill(text: String, state: DownloadState?, onClick: () -> Unit) {
    val p = LocalPalette.current
    when (state) {
        DownloadState.BUSY -> Box(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            CircularProgressIndicator(Modifier.size(22.dp), color = p.accent, strokeWidth = 2.dp)
        }
        DownloadState.QUEUED -> Pill("В очереди", filled = false, icon = R.drawable.ic_done) {}
        null -> Pill(text, filled = false, icon = R.drawable.ic_file_download, onClick = onClick)
    }
}

@Composable
private fun SmallSpinner() {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), color = p.accent, strokeWidth = 2.dp)
    }
}

@Composable
private fun SkeletonRows(n: Int) {
    val p = LocalPalette.current
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "skeletonAlpha")
    Column(Modifier.padding(top = 8.dp).graphicsLayer { this.alpha = alpha }) {
        repeat(n) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(p.card))
                Spacer(Modifier.width(14.dp))
                Column {
                    Box(Modifier.width(180.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(p.card))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(120.dp).height(11.dp).clip(RoundedCornerShape(6.dp)).background(p.card))
                }
            }
        }
    }
}

@Composable
private fun EmptyBox(title: String, text: String?) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(p.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_search), null, tint = p.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = p.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (text != null) {
            Spacer(Modifier.height(6.dp))
            Text(text, color = p.muted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = p.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Pill("Повторить", filled = true) { onRetry() }
    }
}

@Composable
private fun Notice(text: String) {
    val p = LocalPalette.current
    Text(
        text, color = p.fg, fontSize = 13.sp,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.card)
            .padding(12.dp),
    )
}

/** Press feedback: a springy shrink, no ripple. */
@Composable
private fun Modifier.bouncy(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.93f else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

private fun duration(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return ""
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1f млн".format(n / 1_000_000.0)
    n >= 1_000 -> "%.0f тыс.".format(n / 1_000.0)
    else -> n.toString()
}

private fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

private fun recordTypeTitle(type: String): String = when (type) {
    "single" -> "Сингл"
    "ep" -> "EP"
    "compile" -> "Сборник"
    else -> "Альбом"
}

private fun providerTitle(provider: String): String = when (provider) {
    "hitmoz" -> "hitmoz"
    "hitmos_fm" -> "hitmos.fm"
    "ytdlp" -> "YouTube"
    "yt_reupload" -> "YouTube"
    "soundcloud" -> "SoundCloud"
    "soulseek" -> "Soulseek"
    else -> provider
}
