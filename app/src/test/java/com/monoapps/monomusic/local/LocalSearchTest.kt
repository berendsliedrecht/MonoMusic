package com.monoapps.monomusic.local

import com.monoapps.monomusic.tidal.TidalAlbum
import com.monoapps.monomusic.tidal.TidalArtist
import com.monoapps.monomusic.tidal.TidalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchTest {

    private fun track(id: Long, title: String, artist: String = "", album: String? = null) =
        TidalTrack(
            id = id,
            title = title,
            artistName = artist,
            artistId = null,
            albumTitle = album,
            albumId = null,
            durationSec = 100,
            trackNumber = 1,
            volumeNumber = 1,
            explicit = false,
            audioQuality = null,
        )

    private val tracks = listOf(
        track(-1, "Morning Light", artist = "The Quiet Hours"),
        track(-2, "Static Bloom", artist = "Vera Lane", album = "Night Signals"),
    )
    private val albums = listOf(
        TidalAlbum(-10, "Night Signals", "Vera Lane", null, 2, null, null),
    )
    private val artists = listOf(TidalArtist(-20, "Vera Lane"))
    private val playlists = listOf(DevicePlaylist("p1", "Morning Drive", emptyList()))

    @Test
    fun `matches title artist and album case-insensitively`() {
        val byTitle = searchLocal("morning l", tracks, albums, artists, playlists)
        assertEquals(listOf(-1L), byTitle.tracks.map { it.id })

        val byArtist = searchLocal("vera", tracks, albums, artists, playlists)
        assertEquals(listOf(-2L), byArtist.tracks.map { it.id })
        assertEquals(listOf(-10L), byArtist.albums.map { it.id })
        assertEquals(listOf(-20L), byArtist.artists.map { it.id })

        val byAlbum = searchLocal("night sig", tracks, albums, artists, playlists)
        assertEquals(listOf(-2L), byAlbum.tracks.map { it.id })
    }

    @Test
    fun `matches playlists by title`() {
        val results = searchLocal("morning", tracks, albums, artists, playlists)
        assertEquals(listOf("p1"), results.playlists.map { it.id })
        assertEquals(listOf(-1L), results.tracks.map { it.id })
    }

    @Test
    fun `no match returns empty lists`() {
        val results = searchLocal("zzz", tracks, albums, artists, playlists)
        assertTrue(
            results.tracks.isEmpty() && results.albums.isEmpty() &&
                results.artists.isEmpty() && results.playlists.isEmpty()
        )
    }
}
