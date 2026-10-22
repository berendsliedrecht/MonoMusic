package com.monoapps.monomusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monoapps.monomusic.MonoMusicViewModel
import com.monoapps.monomusic.local.DevicePlaylist
import com.monoapps.monomusic.tidal.TidalAlbum
import com.monoapps.monomusic.tidal.TidalArtist
import com.monoapps.monomusic.tidal.TidalPlaylist
import com.monoapps.monomusic.tidal.TidalTrack
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.tabs.PrimaryTabRowMMD
import com.mudita.mmd.components.tabs.TabMMD
import com.mudita.mmd.components.text.TextMMD

// Icon tabs: four text labels wrap mid-word on the 480px-wide screen.
// Same order as the nav bar tabs.
private val searchTabs = listOf(
    "Playlists" to Icons.Outlined.LibraryMusic,
    "Artists" to Icons.Outlined.PersonOutline,
    "Songs" to Icons.AutoMirrored.Outlined.QueueMusic,
    "Albums" to Icons.Outlined.Album,
)

/**
 * Local results come from the in-memory library and show instantly; the
 * Tidal section below them fills in when the network search returns.
 */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun SearchScreen(
    viewModel: MonoMusicViewModel,
    onOpenAlbum: (TidalAlbum) -> Unit,
    onOpenArtist: (TidalArtist) -> Unit,
    onOpenPlaylist: (TidalPlaylist) -> Unit,
    onOpenDevicePlaylist: (DevicePlaylist) -> Unit,
) {
    // Songs is the most common search, so it starts selected.
    var tab by remember { mutableIntStateOf(2) }
    var pickerTrack by remember { mutableStateOf<TidalTrack?>(null) }
    val local = viewModel.localSearchResults
    val tidal = viewModel.searchResults

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRowMMD(selectedTabIndex = tab) {
            searchTabs.forEachIndexed { index, (label, icon) ->
                TabMMD(
                    selected = tab == index,
                    onClick = { tab = index },
                    icon = { Icon(imageVector = icon, contentDescription = label) },
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (local == null && tidal == null && !viewModel.isSearching) {
                CenteredMessage(
                    if (viewModel.isLoggedIn) "Search your local music and Tidal."
                    else "Search the music on this phone."
                )
            } else when (tab) {
                0 -> SearchTab(
                    viewModel = viewModel,
                    localItems = local?.playlists.orEmpty(),
                    tidalItems = tidal?.playlists.orEmpty(),
                    emptyText = "No playlists found.",
                    localSection = { items ->
                        itemsIndexed(items, key = { _, p -> "local-${p.id}" }) { index, playlist ->
                            TwoLineRow(
                                title = playlist.title,
                                subtitle = if (playlist.tracks.size == 1) "1 song"
                                else "${playlist.tracks.size} songs",
                                onClick = { onOpenDevicePlaylist(playlist) },
                                showDivider = index != items.lastIndex,
                            )
                        }
                    },
                    tidalSection = { items ->
                        itemsIndexed(items, key = { _, p -> "tidal-${p.uuid}" }) { index, playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                onClick = { onOpenPlaylist(playlist) },
                                showDivider = index != items.lastIndex,
                            )
                        }
                    },
                )

                1 -> SearchTab(
                    viewModel = viewModel,
                    localItems = local?.artists.orEmpty(),
                    tidalItems = tidal?.artists.orEmpty(),
                    emptyText = "No artists found.",
                    localSection = { items ->
                        itemsIndexed(items, key = { _, a -> "local-${a.id}" }) { index, artist ->
                            ArtistRow(
                                artist = artist,
                                onClick = { onOpenArtist(artist) },
                                showDivider = index != items.lastIndex,
                            )
                        }
                    },
                    tidalSection = { items ->
                        itemsIndexed(items, key = { _, a -> "tidal-${a.id}" }) { index, artist ->
                            ArtistRow(
                                artist = artist,
                                onClick = { onOpenArtist(artist) },
                                showDivider = index != items.lastIndex,
                            )
                        }
                    },
                )

                2 -> SearchTab(
                    viewModel = viewModel,
                    localItems = local?.tracks.orEmpty(),
                    tidalItems = tidal?.tracks.orEmpty(),
                    emptyText = "No songs found.",
                    localSection = { items ->
                        itemsIndexed(items, key = { _, t -> "local-${t.id}" }) { index, track ->
                            TrackRow(
                                track = track,
                                isCurrentlyPlaying = track.id == viewModel.currentTrack?.id,
                                isLocal = true,
                                onClick = { viewModel.playTracks(items, index) },
                                menuEntries = remember(track) {
                                    listOf(
                                        RowMenuEntry("Add to playlist") { pickerTrack = track },
                                    )
                                },
                                showDivider = index != items.lastIndex,
                            )
                        }
                    },
                    tidalSection = { items ->
                        itemsIndexed(items, key = { _, t -> "tidal-${t.id}" }) { index, track ->
                            val isFavorite = track.id in viewModel.favoriteTrackIds
                            val trackPinned = viewModel.isPinned("track", track.id.toString())
                            TrackRow(
                                track = track,
                                isCurrentlyPlaying = track.id == viewModel.currentTrack?.id,
                                onClick = { viewModel.playTracks(items, index) },
                                menuEntries = remember(track, isFavorite, trackPinned) {
                                    listOf(
                                        RowMenuEntry(
                                            if (isFavorite) "Remove from favorites"
                                            else "Add to favorites"
                                        ) { viewModel.toggleFavoriteTrack(track) },
                                        RowMenuEntry("Add to playlist") { pickerTrack = track },
                                        keepOfflineMenuEntry(viewModel, track),
                                    )
                                },
                                showDivider = index != items.lastIndex,
                            )
                        }
                    },
                )

                else -> SearchTab(
                    viewModel = viewModel,
                    localItems = local?.albums.orEmpty(),
                    tidalItems = tidal?.albums.orEmpty(),
                    emptyText = "No albums found.",
                    localSection = { items ->
                        itemsIndexed(items, key = { _, a -> "local-${a.id}" }) { index, album ->
                            AlbumRow(
                                album = album,
                                onClick = { onOpenAlbum(album) },
                                showDivider = index != items.lastIndex,
                            )
                        }
                    },
                    tidalSection = { items ->
                        itemsIndexed(items, key = { _, a -> "tidal-${a.id}" }) { index, album ->
                            AlbumRow(
                                album = album,
                                onClick = { onOpenAlbum(album) },
                                showDivider = index != items.lastIndex,
                            )
                        }
                    },
                )
            }
        }
    }

    pickerTrack?.let { track ->
        TrackPlaylistPicker(viewModel, track) { pickerTrack = null }
    }
}

@Composable
private fun <L, T> SearchTab(
    viewModel: MonoMusicViewModel,
    localItems: List<L>,
    tidalItems: List<T>,
    emptyText: String,
    localSection: LazyListScope.(List<L>) -> Unit,
    tidalSection: LazyListScope.(List<T>) -> Unit,
) {
    val tidalPending = viewModel.isSearching || viewModel.searchError != null
    if (localItems.isEmpty() && tidalItems.isEmpty() && !tidalPending) {
        CenteredMessage(emptyText)
        return
    }
    LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
        if (localItems.isNotEmpty()) {
            if (viewModel.isLoggedIn) sectionLabel("On this phone")
            localSection(localItems)
        }
        if (viewModel.isLoggedIn) {
            if (localItems.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                sectionLabel("Tidal")
            }
            when {
                viewModel.isSearching -> statusLine("Searching Tidal…")
                viewModel.searchError != null -> statusLine(viewModel.searchError!!)
                tidalItems.isEmpty() -> statusLine(emptyText.replace("found", "found on Tidal"))
                else -> tidalSection(tidalItems)
            }
        }
    }
}

private fun LazyListScope.sectionLabel(text: String) {
    item {
        TextMMD(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

private fun LazyListScope.statusLine(text: String) {
    item { TextMMD(text = text, fontSize = 14.sp) }
}
