package com.monoapps.monomusic.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monoapps.monomusic.MonoMusicViewModel
import com.monoapps.monomusic.tidal.TidalAlbum
import com.monoapps.monomusic.tidal.TidalArtist
import com.monoapps.monomusic.tidal.TidalPlaylist
import com.monoapps.monomusic.tidal.TidalTrack
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
import com.mudita.mmd.components.text.TextMMD

fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** A long-press context menu entry on a list row. */
data class RowMenuEntry(val label: String, val action: () -> Unit)

/** A ready-made long-press menu entry for keeping a single song offline. */
fun keepOfflineMenuEntry(viewModel: MonoMusicViewModel, track: TidalTrack): RowMenuEntry {
    val id = track.id.toString()
    return if (viewModel.isPinned("track", id)) {
        RowMenuEntry("Remove offline copy") { viewModel.unpinCollection("track", id) }
    } else {
        RowMenuEntry("Keep offline") { viewModel.pinTrack(track) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: TidalTrack,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    showTrackNumber: Boolean = false,
    isDownloaded: Boolean = false,
    isLocal: Boolean = false,
    menuEntries: List<RowMenuEntry> = emptyList(),
    showDivider: Boolean = true,
) {
    var showMenu by remember { mutableStateOf(false) }

    val subtitle = remember(track, showTrackNumber) {
        buildString {
            if (track.artistName.isNotBlank()) append(track.artistName)
            track.albumTitle?.takeIf { it.isNotBlank() && !showTrackNumber }?.let {
                if (isNotEmpty()) append(" • ")
                append(it)
            }
            if (track.durationSec > 0) {
                if (isNotEmpty()) append(" • ")
                append(formatDuration(track.durationSec.toLong()))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = { if (menuEntries.isNotEmpty()) showMenu = true },
            )
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCurrentlyPlaying) {
                Icon(
                    imageVector = Icons.Outlined.Headphones,
                    contentDescription = "Now playing",
                    modifier = Modifier.size(24.dp).padding(start = 4.dp),
                )
            } else if (showTrackNumber && track.trackNumber > 0) {
                TextMMD(
                    text = track.trackNumber.toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                TextMMD(
                    text = track.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Outlined.DownloadDone,
                            contentDescription = "Available offline",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (isLocal) {
                        Icon(
                            imageVector = Icons.Outlined.Smartphone,
                            contentDescription = "On this phone",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    TextMMD(
                        text = subtitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (showMenu) {
                Box(modifier = Modifier.wrapContentSize()) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Close menu",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { showMenu = false },
                    )
                    DropdownMenuMMD(
                        expanded = true,
                        onDismissRequest = { showMenu = false },
                    ) {
                        menuEntries.forEachIndexed { index, entry ->
                            if (index > 0) DashedDivider(thickness = 1.dp)
                            DropdownMenuItemMMD(
                                text = { TextMMD(text = entry.label) },
                                onClick = {
                                    showMenu = false
                                    entry.action()
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (showDivider) DashedDivider(thickness = 1.dp)
    }
}

@Composable
fun TwoLineRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(bottom = 8.dp),
    ) {
        TextMMD(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            TextMMD(
                text = subtitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (showDivider) DashedDivider(thickness = 1.dp)
    }
}

@Composable
fun AlbumRow(album: TidalAlbum, onClick: () -> Unit, showDivider: Boolean = true) {
    val subtitle = remember(album) {
        buildString {
            if (album.artistName.isNotBlank()) append(album.artistName)
            album.releaseYear?.let {
                if (isNotEmpty()) append(" • ")
                append(it)
            }
            if (album.numberOfTracks > 0) {
                if (isNotEmpty()) append(" • ")
                append(if (album.numberOfTracks == 1) "1 song" else "${album.numberOfTracks} songs")
            }
        }
    }
    TwoLineRow(title = album.title, subtitle = subtitle, onClick = onClick, showDivider = showDivider)
}

@Composable
fun ArtistRow(artist: TidalArtist, onClick: () -> Unit, showDivider: Boolean = true) {
    TwoLineRow(title = artist.name, subtitle = null, onClick = onClick, showDivider = showDivider)
}

@Composable
fun PlaylistRow(playlist: TidalPlaylist, onClick: () -> Unit, showDivider: Boolean = true) {
    val subtitle = remember(playlist) {
        buildString {
            playlist.creator?.let { append("By $it") }
            if (playlist.numberOfTracks > 0) {
                if (isNotEmpty()) append(" • ")
                append(
                    if (playlist.numberOfTracks == 1) "1 song"
                    else "${playlist.numberOfTracks} songs"
                )
            }
        }
    }
    TwoLineRow(
        title = playlist.title,
        subtitle = subtitle,
        onClick = onClick,
        showDivider = showDivider,
    )
}

/** Centered state for loading / error / empty list situations. */
@Composable
fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TextMMD(
            text = text,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

/** Icon button with the app's rounded-square outline, for primary row actions. */
@Composable
fun BorderedIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.border(2.dp, Color.Black, RoundedCornerShape(12.dp))) {
        androidx.compose.material3.IconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

/** Outlined rounded-square action button (MMD's FAB is solid black, which infills). */
@Composable
fun OutlinedActionButton(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(2.dp, Color.Black, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun CenteredError(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            TextMMD(text = message, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            com.mudita.mmd.components.buttons.OutlinedButtonMMD(onClick = onRetry) {
                TextMMD(text = "Retry")
            }
        }
    }
}

/**
 * "Add to playlist" dialog for a track: device playlists always, Tidal
 * playlists only for Tidal tracks (they cannot hold local files).
 */
@Composable
fun TrackPlaylistPicker(
    viewModel: MonoMusicViewModel,
    track: TidalTrack,
    onDismiss: () -> Unit,
) {
    val device = viewModel.devicePlaylists
    val tidal = if (track.id > 0) viewModel.playlists.data.orEmpty() else emptyList()
    val showSections = device.isNotEmpty() && tidal.isNotEmpty()

    MonoDialog(title = "Add to playlist", onDismissRequest = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (device.isEmpty() && tidal.isEmpty()) {
                TextMMD(text = "No playlists yet. Create one on the Playlists tab first.")
            }
            if (showSections) PickerSectionLabel("On this phone")
            device.forEach { playlist ->
                PickerRow(playlist.title) {
                    viewModel.addToDevicePlaylist(track, playlist)
                    onDismiss()
                }
            }
            if (showSections) PickerSectionLabel("Tidal")
            tidal.forEach { playlist ->
                PickerRow(playlist.title) {
                    viewModel.addToPlaylist(track, playlist)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun PickerSectionLabel(text: String) {
    TextMMD(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickerRow(title: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
    ) {
        TextMMD(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    DashedDivider(thickness = 1.dp)
}
