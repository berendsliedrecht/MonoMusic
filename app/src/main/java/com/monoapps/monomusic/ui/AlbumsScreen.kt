package com.monoapps.monomusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monoapps.monomusic.MonoMusicViewModel
import com.monoapps.monomusic.tidal.TidalAlbum
import com.mudita.mmd.components.lazy.LazyColumnMMD

@Composable
fun AlbumsScreen(viewModel: MonoMusicViewModel, onOpenAlbum: (TidalAlbum) -> Unit) {
    val state = viewModel.favoriteAlbums
    val albums = viewModel.libraryAlbums

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            albums.isEmpty() && state.data == null && state.isLoading ->
                CenteredMessage("Loading albums…")

            albums.isEmpty() && state.data == null && state.error != null ->
                CenteredError(state.error) { viewModel.loadFavoriteAlbums() }

            albums.isEmpty() -> CenteredMessage(
                "No albums yet.\n\nChoose a local music folder in Settings, or favorite " +
                    "an album or keep one offline on Tidal."
            )

            else -> LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
                itemsIndexed(albums, key = { _, album -> album.id }) { index, album ->
                    AlbumRow(
                        album = album,
                        onClick = { onOpenAlbum(album) },
                        showDivider = index != albums.lastIndex,
                    )
                }
            }
        }
    }
}
