package com.monoapps.monomusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monoapps.monomusic.MonoMusicViewModel
import com.monoapps.monomusic.tidal.TidalArtist
import com.mudita.mmd.components.lazy.LazyColumnMMD

@Composable
fun ArtistsScreen(viewModel: MonoMusicViewModel, onOpenArtist: (TidalArtist) -> Unit) {
    val state = viewModel.favoriteArtists
    val artists = viewModel.libraryArtists

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            artists.isEmpty() && state.data == null && state.isLoading ->
                CenteredMessage("Loading artists…")

            artists.isEmpty() && state.data == null && state.error != null ->
                CenteredError(state.error) { viewModel.loadFavoriteArtists() }

            artists.isEmpty() -> CenteredMessage(
                "No artists yet.\n\nChoose a local music folder in Settings, or favorite " +
                    "an artist or like an album on Tidal."
            )

            else -> LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
                itemsIndexed(artists, key = { _, artist -> artist.id }) { index, artist ->
                    ArtistRow(
                        artist = artist,
                        onClick = { onOpenArtist(artist) },
                        showDivider = index != artists.lastIndex,
                    )
                }
            }
        }
    }
}
