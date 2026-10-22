package com.monoapps.monomusic.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monoapps.monomusic.MonoMusicViewModel
import com.monoapps.monomusic.offline.OfflineCollection
import com.monoapps.monomusic.tidal.TidalTrack
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun AlbumDetailsScreen(viewModel: MonoMusicViewModel) {
    val album = viewModel.selectedAlbum ?: return
    val state = viewModel.albumTracks
    val tracks = state.data.orEmpty()
    var pickerTrack by remember { mutableStateOf<TidalTrack?>(null) }
    val isFavorite = album.id in viewModel.favoriteAlbumIds
    val isPinned = viewModel.isPinned("album", album.id.toString())
    val isLocal = album.id < 0

    when {
        state.isLoading -> CenteredMessage("Loading album…")

        state.error != null -> CenteredError(state.error) { viewModel.openAlbum(album) }

        tracks.isEmpty() -> CenteredMessage("This album has no playable songs.")

        else -> LazyColumnMMD(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BorderedIconButton(
                        icon = Icons.Outlined.PlayArrow,
                        contentDescription = "Play",
                        onClick = { viewModel.playTracks(tracks) },
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    BorderedIconButton(
                        icon = Icons.Outlined.Shuffle,
                        contentDescription = "Shuffle",
                        onClick = { viewModel.playTracks(tracks, shuffled = true) },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (!isLocal) IconButton(onClick = {
                        if (isPinned) {
                            viewModel.unpinCollection("album", album.id.toString())
                        } else {
                            viewModel.pinCollection(
                                OfflineCollection(
                                    type = "album",
                                    id = album.id.toString(),
                                    title = album.title,
                                    subtitle = album.artistName.takeIf { it.isNotBlank() },
                                    quality = viewModel.downloadQuality,
                                    tracks = tracks,
                                )
                            )
                        }
                    }) {
                        Icon(
                            imageVector = if (isPinned) Icons.Outlined.DownloadDone
                            else Icons.Outlined.DownloadForOffline,
                            contentDescription = if (isPinned) "Remove offline copy"
                            else "Keep offline",
                        )
                    }
                    if (!isLocal) IconButton(onClick = { viewModel.toggleFavoriteAlbum(album) }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove album from favorites"
                            else "Add album to favorites",
                        )
                    }
                }
                album.audioQuality?.let { quality ->
                    TextMMD(text = quality, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                DashedDivider(thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
            }

            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                val isTrackFavorite = track.id in viewModel.favoriteTrackIds
                val trackPinned = viewModel.isPinned("track", track.id.toString())
                TrackRow(
                    track = track,
                    isCurrentlyPlaying = track.id == viewModel.currentTrack?.id,
                    isDownloaded = track.id in viewModel.offlineTrackIds,
                    onClick = { viewModel.playTracks(tracks, index) },
                    showTrackNumber = true,
                    menuEntries = remember(track, isTrackFavorite, trackPinned) {
                        if (track.id < 0) listOf(
                            RowMenuEntry("Add to playlist") { pickerTrack = track },
                        )
                        else listOf(
                            RowMenuEntry(
                                if (isTrackFavorite) "Remove from favorites" else "Add to favorites"
                            ) { viewModel.toggleFavoriteTrack(track) },
                            RowMenuEntry("Add to playlist") { pickerTrack = track },
                            keepOfflineMenuEntry(viewModel, track),
                        )
                    },
                    showDivider = index != tracks.lastIndex,
                )
            }
        }
    }

    pickerTrack?.let { track ->
        TrackPlaylistPicker(viewModel, track) { pickerTrack = null }
    }
}
