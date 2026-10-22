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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monoapps.monomusic.MonoMusicViewModel
import com.monoapps.monomusic.tidal.TidalAlbum
import com.monoapps.monomusic.tidal.TidalTrack
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun ArtistDetailsScreen(
    viewModel: MonoMusicViewModel,
    onOpenAlbum: (TidalAlbum) -> Unit,
) {
    val artist = viewModel.selectedArtist ?: return
    val topTracksState = viewModel.artistTopTracks
    val albumsState = viewModel.artistAlbums
    val topTracks = topTracksState.data.orEmpty()
    val albums = albumsState.data.orEmpty()
    val libraryTracks = viewModel.libraryTracks.filter { it.artistId == artist.id }
    var pickerTrack by remember { mutableStateOf<TidalTrack?>(null) }
    val isFavorite = artist.id in viewModel.favoriteArtistIds

    if (libraryTracks.isEmpty() && topTracksState.isLoading && albumsState.isLoading) {
        CenteredMessage("Loading artist…")
        return
    }

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
                // Local artists have no Tidal top songs; play their library songs.
                val playable = topTracks.ifEmpty { libraryTracks }
                if (playable.isNotEmpty()) {
                    BorderedIconButton(
                        icon = Icons.Outlined.PlayArrow,
                        contentDescription = "Play songs",
                        onClick = { viewModel.playTracks(playable) },
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    BorderedIconButton(
                        icon = Icons.Outlined.Shuffle,
                        contentDescription = "Shuffle songs",
                        onClick = { viewModel.playTracks(playable, shuffled = true) },
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                if (artist.id > 0) IconButton(onClick = { viewModel.toggleFavoriteArtist(artist) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite
                        else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove artist from favorites"
                        else "Add artist to favorites",
                    )
                }
            }
        }

        if (libraryTracks.isNotEmpty()) {
            item {
                TextMMD(
                    text = "In Your Library",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            itemsIndexed(libraryTracks, key = { _, track -> "lib-${track.id}" }) { index, track ->
                val isTrackFavorite = track.id in viewModel.favoriteTrackIds
                val trackPinned = viewModel.isPinned("track", track.id.toString())
                TrackRow(
                    track = track,
                    isCurrentlyPlaying = track.id == viewModel.currentTrack?.id,
                    isDownloaded = track.id in viewModel.offlineTrackIds,
                    isLocal = track.id < 0,
                    onClick = { viewModel.playTracks(libraryTracks, index) },
                    menuEntries = remember(track, isTrackFavorite, trackPinned) {
                        if (track.id < 0) emptyList()
                        else listOf(
                            RowMenuEntry(
                                if (isTrackFavorite) "Remove from favorites" else "Add to favorites"
                            ) { viewModel.toggleFavoriteTrack(track) },
                            RowMenuEntry("Add to playlist") { pickerTrack = track },
                            keepOfflineMenuEntry(viewModel, track),
                        )
                    },
                    showDivider = index != libraryTracks.lastIndex,
                )
            }
            if (topTracks.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }

        if (topTracks.isNotEmpty()) {
            item {
                TextMMD(
                    text = "Top Songs",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            itemsIndexed(topTracks, key = { _, track -> "top-${track.id}" }) { index, track ->
                val isTrackFavorite = track.id in viewModel.favoriteTrackIds
                val trackPinned = viewModel.isPinned("track", track.id.toString())
                TrackRow(
                    track = track,
                    isCurrentlyPlaying = track.id == viewModel.currentTrack?.id,
                    onClick = { viewModel.playTracks(topTracks, index) },
                    menuEntries = remember(track, isTrackFavorite, trackPinned) {
                        listOf(
                            RowMenuEntry(
                                if (isTrackFavorite) "Remove from favorites" else "Add to favorites"
                            ) { viewModel.toggleFavoriteTrack(track) },
                            RowMenuEntry("Add to playlist") { pickerTrack = track },
                            keepOfflineMenuEntry(viewModel, track),
                        )
                    },
                    showDivider = index != topTracks.lastIndex,
                )
            }
        } else if (topTracksState.error != null) {
            item { TextMMD(text = topTracksState.error, fontSize = 14.sp) }
        }

        if (albums.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                TextMMD(
                    text = "Albums",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            itemsIndexed(albums, key = { _, album -> "album-${album.id}" }) { index, album ->
                AlbumRow(
                    album = album,
                    onClick = { onOpenAlbum(album) },
                    showDivider = index != albums.lastIndex,
                )
            }
        } else if (albumsState.error != null) {
            item { TextMMD(text = albumsState.error, fontSize = 14.sp) }
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
