package com.monoapps.monomusic.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.MoreVert
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
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun PlaylistDetailsScreen(viewModel: MonoMusicViewModel, onPlaylistRemoved: () -> Unit) {
    val playlist = viewModel.selectedPlaylist ?: return
    val state = viewModel.playlistTracks
    val tracks = state.data.orEmpty()
    var pickerTrack by remember { mutableStateOf<TidalTrack?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val isPinned = viewModel.isPinned("playlist", playlist.uuid)

    when {
        state.isLoading -> CenteredMessage("Loading playlist…")

        state.error != null -> CenteredError(state.error) { viewModel.openPlaylist(playlist) }

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
                    if (tracks.isNotEmpty()) {
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
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (tracks.isNotEmpty()) {
                        IconButton(onClick = {
                            if (isPinned) {
                                viewModel.unpinCollection("playlist", playlist.uuid)
                            } else {
                                viewModel.pinCollection(
                                    OfflineCollection(
                                        type = "playlist",
                                        id = playlist.uuid,
                                        title = playlist.title,
                                        subtitle = playlist.creator?.let { "By $it" },
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
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "Playlist options",
                            )
                        }
                        DropdownMenuMMD(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItemMMD(
                                text = { TextMMD(text = "Remove playlist") },
                                onClick = {
                                    showMenu = false
                                    viewModel.deletePlaylist(playlist)
                                    onPlaylistRemoved()
                                },
                            )
                        }
                    }
                }
                playlist.description?.let { description ->
                    TextMMD(text = description, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                DashedDivider(thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (tracks.isEmpty()) {
                item {
                    TextMMD(
                        text = "This playlist is empty. Long-press any song and choose " +
                            "\"Add to playlist\" to fill it.",
                        fontSize = 16.sp,
                    )
                }
            }

            itemsIndexed(tracks, key = { index, track -> "$index-${track.id}" }) { index, track ->
                val isTrackFavorite = track.id in viewModel.favoriteTrackIds
                val trackPinned = viewModel.isPinned("track", track.id.toString())
                TrackRow(
                    track = track,
                    isCurrentlyPlaying = track.id == viewModel.currentTrack?.id,
                    isDownloaded = track.id in viewModel.offlineTrackIds,
                    onClick = { viewModel.playTracks(tracks, index) },
                    menuEntries = remember(track, isTrackFavorite, trackPinned, index) {
                        listOf(
                            RowMenuEntry(
                                if (isTrackFavorite) "Remove from favorites" else "Add to favorites"
                            ) { viewModel.toggleFavoriteTrack(track) },
                            RowMenuEntry("Add to playlist") { pickerTrack = track },
                            keepOfflineMenuEntry(viewModel, track),
                            RowMenuEntry("Remove from this playlist") {
                                viewModel.removeFromPlaylist(index)
                            },
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
