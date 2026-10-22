package com.monoapps.monomusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monoapps.monomusic.MonoMusicViewModel
import com.monoapps.monomusic.tidal.TidalTrack
import com.mudita.mmd.components.lazy.LazyColumnMMD

/** Favorite, downloaded, and liked-album tracks; the "all songs" library. */
@Composable
fun SongsScreen(viewModel: MonoMusicViewModel) {
    val state = viewModel.favoriteTracks
    val tracks = viewModel.libraryTracks
    var pickerTrack by remember { mutableStateOf<TidalTrack?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            tracks.isNotEmpty() -> LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    val isFavorite = track.id in viewModel.favoriteTrackIds
                    val trackPinned = viewModel.isPinned("track", track.id.toString())
                    TrackRow(
                        track = track,
                        isCurrentlyPlaying = track.id == viewModel.currentTrack?.id,
                        isDownloaded = track.id in viewModel.offlineTrackIds,
                        isLocal = track.id < 0,
                        onClick = { viewModel.playTracks(tracks, index) },
                        // Remembered so the row can skip recomposition; a fresh
                        // list every pass defeats Compose's parameter check.
                        menuEntries = remember(track, isFavorite, trackPinned) {
                            // Local files have no Tidal actions.
                            if (track.id < 0) emptyList()
                            else listOf(
                                RowMenuEntry("Add to playlist") { pickerTrack = track },
                                RowMenuEntry(
                                    if (isFavorite) "Remove from favorites" else "Add to favorites"
                                ) { viewModel.toggleFavoriteTrack(track) },
                                keepOfflineMenuEntry(viewModel, track),
                            )
                        },
                        showDivider = index != tracks.lastIndex,
                    )
                }
            }

            state.data == null && state.isLoading -> CenteredMessage("Loading songs…")

            state.data == null && state.error != null ->
                CenteredError(state.error) { viewModel.loadFavoriteTracks() }

            else -> CenteredMessage(
                "No songs yet.\n\nChoose a local music folder in Settings, or favorite " +
                    "songs, like an album, or keep music offline on Tidal."
            )
        }

        if (tracks.isNotEmpty()) {
            OutlinedActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                onClick = { viewModel.playTracks(tracks, shuffled = true) },
            ) {
                Icon(imageVector = Icons.Outlined.Shuffle, contentDescription = "Shuffle songs")
            }
        }
    }

    pickerTrack?.let { track ->
        PlaylistPickerDialog(
            playlists = viewModel.playlists.data.orEmpty(),
            onPick = { playlist ->
                viewModel.addToPlaylist(track, playlist)
                pickerTrack = null
            },
            onDismiss = { pickerTrack = null },
        )
    }
}
