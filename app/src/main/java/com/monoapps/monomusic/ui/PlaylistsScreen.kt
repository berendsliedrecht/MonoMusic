package com.monoapps.monomusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
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
import com.monoapps.monomusic.tidal.TidalPlaylist
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

@Composable
fun PlaylistsScreen(viewModel: MonoMusicViewModel, onOpenPlaylist: (TidalPlaylist) -> Unit) {
    val state = viewModel.playlists
    val playlists = state.data.orEmpty()
    var showCreateDialog by remember { mutableStateOf(false) }

    if (!viewModel.isLoggedIn) {
        CenteredMessage(
            "Playlists live in your Tidal account.\n\nLog in via Settings to use them."
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.data == null && state.isLoading -> CenteredMessage("Loading playlists…")

            state.data == null && state.error != null ->
                CenteredError(state.error) { viewModel.loadPlaylists() }

            playlists.isEmpty() -> CenteredMessage(
                "No playlists yet.\n\nCreate one with the button below."
            )

            else -> LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
                itemsIndexed(playlists, key = { _, playlist -> playlist.uuid }) { index, playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist) },
                        showDivider = index != playlists.lastIndex,
                    )
                }
            }
        }

        OutlinedActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            onClick = { showCreateDialog = true },
        ) {
            Icon(imageVector = Icons.Outlined.Add, contentDescription = "New playlist")
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        MonoDialog(title = "New playlist", onDismissRequest = { showCreateDialog = false }) {
            TextFieldMMD(
                value = name,
                onValueChange = { name = it },
                placeholder = { TextMMD(text = "Name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.padding(top = 16.dp))
            Row(modifier = Modifier.align(Alignment.End)) {
                OutlinedButtonMMD(onClick = { showCreateDialog = false }) {
                    TextMMD(text = "Cancel")
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButtonMMD(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.createPlaylist(name.trim())
                        showCreateDialog = false
                    }
                }) {
                    TextMMD(text = "Create")
                }
            }
        }
    }
}
