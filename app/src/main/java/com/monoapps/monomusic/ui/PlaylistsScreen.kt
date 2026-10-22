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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
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
import com.monoapps.monomusic.local.DevicePlaylist
import com.monoapps.monomusic.tidal.TidalPlaylist
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD

@Composable
fun PlaylistsScreen(
    viewModel: MonoMusicViewModel,
    onOpenPlaylist: (TidalPlaylist) -> Unit,
    onOpenDevicePlaylist: (DevicePlaylist) -> Unit,
) {
    val state = viewModel.playlists
    val tidal = state.data.orEmpty()
    val device = viewModel.devicePlaylists
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            device.isEmpty() && !viewModel.isLoggedIn -> CenteredMessage(
                "No playlists yet.\n\nCreate one with the button below. " +
                    "Tidal playlists appear after logging in via Settings."
            )

            device.isEmpty() && state.data == null && state.isLoading ->
                CenteredMessage("Loading playlists…")

            device.isEmpty() && state.data == null && state.error != null ->
                CenteredError(state.error) { viewModel.loadPlaylists() }

            device.isEmpty() && tidal.isEmpty() -> CenteredMessage(
                "No playlists yet.\n\nCreate one with the button below."
            )

            else -> LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
                // Section labels only when both kinds can show up.
                if (device.isNotEmpty() && viewModel.isLoggedIn) {
                    item { PlaylistSectionLabel("On this phone") }
                }
                itemsIndexed(device, key = { _, playlist -> playlist.id }) { index, playlist ->
                    TwoLineRow(
                        title = playlist.title,
                        subtitle = if (playlist.tracks.size == 1) "1 song"
                        else "${playlist.tracks.size} songs",
                        onClick = { onOpenDevicePlaylist(playlist) },
                        showDivider = index != device.lastIndex || viewModel.isLoggedIn,
                    )
                }
                if (viewModel.isLoggedIn) {
                    if (device.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            PlaylistSectionLabel("Tidal")
                        }
                    }
                    when {
                        tidal.isNotEmpty() -> itemsIndexed(
                            tidal,
                            key = { _, playlist -> playlist.uuid },
                        ) { index, playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                onClick = { onOpenPlaylist(playlist) },
                                showDivider = index != tidal.lastIndex,
                            )
                        }

                        state.isLoading -> item {
                            TextMMD(text = "Loading Tidal playlists…", fontSize = 14.sp)
                        }

                        state.error != null -> item {
                            TextMMD(text = state.error, fontSize = 14.sp)
                        }

                        else -> item {
                            TextMMD(text = "No Tidal playlists yet.", fontSize = 14.sp)
                        }
                    }
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
            if (viewModel.isLoggedIn) {
                // Two destinations; dismiss by tapping outside or back.
                Row(modifier = Modifier.align(Alignment.End)) {
                    OutlinedButtonMMD(onClick = {
                        if (name.isNotBlank()) {
                            viewModel.createDevicePlaylist(name.trim())
                            showCreateDialog = false
                        }
                    }) {
                        TextMMD(text = "On this phone")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButtonMMD(onClick = {
                        if (name.isNotBlank()) {
                            viewModel.createPlaylist(name.trim())
                            showCreateDialog = false
                        }
                    }) {
                        TextMMD(text = "In Tidal")
                    }
                }
            } else {
                Row(modifier = Modifier.align(Alignment.End)) {
                    OutlinedButtonMMD(onClick = { showCreateDialog = false }) {
                        TextMMD(text = "Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButtonMMD(onClick = {
                        if (name.isNotBlank()) {
                            viewModel.createDevicePlaylist(name.trim())
                            showCreateDialog = false
                        }
                    }) {
                        TextMMD(text = "Create")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSectionLabel(text: String) {
    TextMMD(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}
