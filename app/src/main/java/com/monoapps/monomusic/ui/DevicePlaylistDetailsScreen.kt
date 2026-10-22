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
import com.monoapps.monomusic.tidal.TidalTrack
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
import com.mudita.mmd.components.text.TextMMD

/** A playlist stored on this phone; can mix local files and Tidal songs. */
@Composable
fun DevicePlaylistDetailsScreen(viewModel: MonoMusicViewModel, onPlaylistRemoved: () -> Unit) {
    val playlist = viewModel.selectedDevicePlaylist ?: return
    val tracks = playlist.tracks
    var pickerTrack by remember { mutableStateOf<TidalTrack?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    LazyColumnMMD(
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
                                viewModel.deleteDevicePlaylist(playlist.id)
                                onPlaylistRemoved()
                            },
                        )
                    }
                }
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
            TrackRow(
                track = track,
                isCurrentlyPlaying = track.id == viewModel.currentTrack?.id,
                isDownloaded = track.id in viewModel.offlineTrackIds,
                isLocal = track.id < 0,
                onClick = { viewModel.playTracks(tracks, index) },
                menuEntries = remember(track, index) {
                    listOf(
                        RowMenuEntry("Add to playlist") { pickerTrack = track },
                        RowMenuEntry("Remove from this playlist") {
                            viewModel.removeFromDevicePlaylist(playlist.id, index)
                        },
                    )
                },
                showDivider = index != tracks.lastIndex,
            )
        }
    }

    pickerTrack?.let { track ->
        TrackPlaylistPicker(viewModel, track) { pickerTrack = null }
    }
}
