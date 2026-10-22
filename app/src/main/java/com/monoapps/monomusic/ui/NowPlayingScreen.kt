package com.monoapps.monomusic.ui

import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.monoapps.monomusic.MonoMusicViewModel
import com.monoapps.monomusic.tidal.TidalTrack
import com.mudita.mmd.components.slider.SliderMMD
import com.mudita.mmd.components.text.TextMMD
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(viewModel: MonoMusicViewModel) {
    val track = viewModel.currentTrack
    if (track == null) {
        CenteredMessage("Nothing is playing.")
        return
    }

    val isFavorite = track.id in viewModel.favoriteTrackIds

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        TextMMD(
            text = track.title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextMMD(
            text = track.artistName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        track.albumTitle?.let { albumTitle ->
            Spacer(modifier = Modifier.height(4.dp))
            TextMMD(
                text = albumTitle,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.id < 0) {
            Spacer(modifier = Modifier.height(8.dp))
            TextMMD(text = "Local file", fontSize = 13.sp)
        } else viewModel.streamQuality?.let { quality ->
            Spacer(modifier = Modifier.height(8.dp))
            TextMMD(text = quality, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        PositionBar(viewModel, track)

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.previous() }) {
                Icon(
                    imageVector = Icons.Outlined.SkipPrevious,
                    contentDescription = "Previous song",
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(modifier = Modifier.size(24.dp))
            OutlinedActionButton(size = 72.dp, onClick = { viewModel.playPause() }) {
                Icon(
                    imageVector = if (viewModel.isPlaying) Icons.Outlined.Pause
                    else Icons.Outlined.PlayArrow,
                    contentDescription = if (viewModel.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(modifier = Modifier.size(24.dp))
            IconButton(onClick = { viewModel.next() }) {
                Icon(
                    imageVector = Icons.Outlined.SkipNext,
                    contentDescription = "Next song",
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleIconButton(
                active = viewModel.repeatMode != Player.REPEAT_MODE_OFF,
                onClick = { viewModel.toggleRepeat() },
            ) {
                Icon(
                    imageVector = if (viewModel.repeatMode == Player.REPEAT_MODE_ONE) {
                        Icons.Outlined.RepeatOne
                    } else {
                        Icons.Outlined.Repeat
                    },
                    contentDescription = "Repeat",
                )
            }
            ToggleIconButton(
                active = viewModel.shuffleEnabled,
                onClick = { viewModel.toggleShuffle() },
            ) {
                Icon(imageVector = Icons.Outlined.Shuffle, contentDescription = "Shuffle")
            }
            if (track.id > 0) IconButton(onClick = { viewModel.toggleFavoriteTrack(track) }) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite
                    else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites"
                    else "Add to favorites",
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Slider plus time labels, polling the position itself. Keeping the poll in
 * this small composable confines the once-a-second recomposition to the
 * slider strip instead of the whole screen (a full-screen e-ink refresh).
 */
@Composable
private fun PositionBar(viewModel: MonoMusicViewModel, track: TidalTrack) {
    var positionMs by remember { mutableLongStateOf(0L) }
    var dragPositionMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(viewModel.isPlaying, track.id) {
        while (true) {
            if (dragPositionMs == null) positionMs = viewModel.currentPositionMs()
            delay(1000)
        }
    }

    val durationMs = viewModel.durationMs.takeIf { it > 0 }
        ?: (track.durationSec * 1000L).takeIf { it > 0 }
        ?: return
    SliderMMD(
        value = (dragPositionMs ?: positionMs).toFloat().coerceIn(0f, durationMs.toFloat()),
        onValueChange = { dragPositionMs = it.toLong() },
        onValueChangeFinished = {
            dragPositionMs?.let { viewModel.seekTo(it) }
            dragPositionMs = null
        },
        valueRange = 0f..durationMs.toFloat(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        TextMMD(text = formatDuration((dragPositionMs ?: positionMs) / 1000), fontSize = 14.sp)
        Spacer(modifier = Modifier.weight(1f))
        TextMMD(text = formatDuration(durationMs / 1000), fontSize = 14.sp)
    }
}

/** Icon button that shows its on-state with a black outline ring. */
@Composable
private fun ToggleIconButton(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (active) {
            Modifier.border(2.dp, Color.Black, RoundedCornerShape(12.dp))
        } else {
            Modifier
        },
    ) {
        IconButton(onClick = onClick) { content() }
    }
}
