package com.monoapps.monomusic.local

import com.monoapps.monomusic.tidal.TidalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLibraryTest {

    private fun track(
        id: Long,
        artistName: String = "Artist",
        artistId: Long? = -10,
        albumTitle: String? = "Album",
        albumId: Long? = -20,
    ) = TidalTrack(
        id = id,
        title = "Song $id",
        artistName = artistName,
        artistId = artistId,
        albumTitle = albumTitle,
        albumId = albumId,
        durationSec = 100,
        trackNumber = 1,
        volumeNumber = 1,
        explicit = false,
        audioQuality = null,
    )

    @Test
    fun `folderRoots groups by top-level folder per volume`() {
        val folders = folderRoots(
            listOf(
                "/storage/emulated/0/Music/Artist/song.mp3",
                "/storage/emulated/0/Music/other.flac",
                "/storage/emulated/0/Download/song.mp3",
                "/storage/ABCD-1234/Music/song.mp3",
            )
        )
        assertEquals(
            listOf(
                LocalFolder("/storage/emulated/0/Download", "Download", 1),
                LocalFolder("/storage/emulated/0/Music", "Music", 2),
                LocalFolder("/storage/ABCD-1234/Music", "Music (SD card)", 1),
            ),
            folders,
        )
    }

    @Test
    fun `folderRoots skips files at the storage root and unknown locations`() {
        val folders = folderRoots(
            listOf(
                "/storage/emulated/0/root.mp3",
                "/data/local/tmp/x.mp3",
            )
        )
        assertTrue(folders.isEmpty())
    }

    @Test
    fun `albumsFrom groups tracks and keeps a single artist`() {
        val albums = albumsFrom(
            listOf(
                track(-1, albumId = -20),
                track(-2, albumId = -20),
                track(-3, albumId = -21, albumTitle = "Other"),
            )
        )
        assertEquals(2, albums.size)
        val first = albums.first { it.id == -20L }
        assertEquals("Album", first.title)
        assertEquals("Artist", first.artistName)
        assertEquals(-10L, first.artistId)
        assertEquals(2, first.numberOfTracks)
    }

    @Test
    fun `albumsFrom marks mixed-artist albums as various artists`() {
        val albums = albumsFrom(
            listOf(
                track(-1, artistName = "A", artistId = -10),
                track(-2, artistName = "B", artistId = -11),
            )
        )
        assertEquals("Various artists", albums.single().artistName)
        assertNull(albums.single().artistId)
    }

    @Test
    fun `albumsFrom skips tracks without an album`() {
        assertTrue(albumsFrom(listOf(track(-1, albumId = null, albumTitle = null))).isEmpty())
    }

    @Test
    fun `artistsFrom dedupes and skips unknown artists`() {
        val artists = artistsFrom(
            listOf(
                track(-1),
                track(-2),
                track(-3, artistName = "", artistId = null),
                track(-4, artistName = "B", artistId = -11),
            )
        )
        assertEquals(2, artists.size)
        assertEquals(setOf(-10L, -11L), artists.map { it.id }.toSet())
    }
}
