package com.monoapps.monomusic.local

import com.monoapps.monomusic.tidal.TidalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePlaylistsTest {

    private val localTrack = TidalTrack(
        id = -42,
        title = "Local Song",
        artistName = "Someone",
        artistId = -7,
        albumTitle = "An Album",
        albumId = -9,
        durationSec = 123,
        trackNumber = 3,
        volumeNumber = 2,
        explicit = false,
        audioQuality = null,
        addedAt = 1700000000000,
    )

    private val tidalTrack = TidalTrack(
        id = 99,
        title = "Streamed Song",
        artistName = "",
        artistId = null,
        albumTitle = null,
        albumId = null,
        durationSec = 200,
        trackNumber = 1,
        volumeNumber = 1,
        explicit = true,
        audioQuality = "LOSSLESS",
    )

    @Test
    fun `round trip keeps local and tidal tracks intact`() {
        val playlists = listOf(
            DevicePlaylist("a", "Mixed", listOf(localTrack, tidalTrack)),
            DevicePlaylist("b", "Empty", emptyList()),
        )
        assertEquals(playlists, parseDevicePlaylists(devicePlaylistsJson(playlists)))
    }

    @Test
    fun `round trip of empty list`() {
        assertTrue(parseDevicePlaylists(devicePlaylistsJson(emptyList())).isEmpty())
    }

    @Test
    fun `entries without an id are skipped`() {
        val parsed = parseDevicePlaylists("""[{"title":"no id","tracks":[]}]""")
        assertTrue(parsed.isEmpty())
    }
}
