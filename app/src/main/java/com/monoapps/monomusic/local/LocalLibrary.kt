package com.monoapps.monomusic.local

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.monoapps.monomusic.tidal.TidalAlbum
import com.monoapps.monomusic.tidal.TidalArtist
import com.monoapps.monomusic.tidal.TidalTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A top-level folder on a storage volume that contains music files. */
data class LocalFolder(val path: String, val name: String, val trackCount: Int)

/**
 * Local music via MediaStore. The system media scanner has already indexed
 * files and their tags, so even a library of thousands of songs is one
 * indexed query instead of opening every file.
 *
 * Local ids are the negated MediaStore ids, disjoint from Tidal's positive
 * ids, so local songs reuse the Tidal models and every existing screen.
 */
class LocalLibrary(private val context: Context) {

    private val prefs = context.getSharedPreferences("local_library", Context.MODE_PRIVATE)

    val folderPath: String? get() = prefs.getString("folder_path", null)
    val folderName: String? get() = prefs.getString("folder_name", null)

    fun setFolder(path: String?, name: String?) {
        prefs.edit().putString("folder_path", path).putString("folder_name", name).apply()
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Top-level folders containing music, for the picker. */
    suspend fun listFolders(): List<LocalFolder> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val paths = mutableListOf<String>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.DATA),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) cursor.getString(0)?.let { paths.add(it) }
        }
        folderRoots(paths)
    }

    /** All music under the chosen folder; empty when none is set. */
    suspend fun scanFolder(): List<TidalTrack> = withContext(Dispatchers.IO) {
        val folder = folderPath ?: return@withContext emptyList()
        if (!hasPermission()) return@withContext emptyList()
        val tracks = mutableListOf<TidalTrack>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DISPLAY_NAME,
            ),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?",
            arrayOf("$folder/%"),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val artist = cursor.getString(2).knownOrNull()
                val artistId = cursor.getLong(3)
                val album = cursor.getString(4).knownOrNull()
                val albumId = cursor.getLong(5)
                // MediaStore encodes the disc in the thousands: CD2 track 3 = 2003.
                val trackNo = cursor.getInt(7)
                tracks.add(
                    TidalTrack(
                        id = -cursor.getLong(0),
                        title = cursor.getString(1).knownOrNull()
                            ?: cursor.getString(9)?.substringBeforeLast('.') ?: "Unknown",
                        artistName = artist.orEmpty(),
                        artistId = if (artist != null && artistId > 0) -artistId else null,
                        albumTitle = album,
                        albumId = if (album != null && albumId > 0) -albumId else null,
                        durationSec = (cursor.getLong(6) / 1000).toInt(),
                        trackNumber = trackNo % 1000,
                        volumeNumber = (trackNo / 1000).coerceAtLeast(1),
                        explicit = false,
                        audioQuality = null,
                        addedAt = cursor.getLong(8) * 1000,
                    )
                )
            }
        }
        tracks
    }

    companion object {
        /** Runtime permission MediaStore audio queries need on this SDK. */
        val permission: String =
            if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_AUDIO"
            else android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

private fun String?.knownOrNull(): String? =
    this?.takeUnless { it.isBlank() || it == "<unknown>" }

/** Groups file paths into their top-level storage folders. */
fun folderRoots(paths: List<String>): List<LocalFolder> {
    val counts = LinkedHashMap<LocalFolder, Int>()
    for (path in paths) {
        val root = when {
            path.startsWith("/storage/emulated/0/") -> "/storage/emulated/0/"
            path.startsWith("/storage/") ->
                "/storage/" + path.removePrefix("/storage/").substringBefore('/') + "/"
            else -> continue
        }
        val top = path.removePrefix(root).substringBefore('/', "")
        if (top.isEmpty()) continue // file directly in the storage root
        val sdCard = root != "/storage/emulated/0/"
        val key = LocalFolder(root + top, if (sdCard) "$top (SD card)" else top, 0)
        counts[key] = (counts[key] ?: 0) + 1
    }
    return counts.map { (folder, count) -> folder.copy(trackCount = count) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

/** Derives the album list from scanned tracks. */
fun albumsFrom(tracks: List<TidalTrack>): List<TidalAlbum> =
    tracks.filter { it.albumId != null }
        .groupBy { it.albumId!! }
        .map { (albumId, albumTracks) ->
            val artists = albumTracks.map { it.artistName }.filter { it.isNotBlank() }.distinct()
            TidalAlbum(
                id = albumId,
                title = albumTracks.first().albumTitle ?: "Unknown album",
                artistName = artists.singleOrNull()
                    ?: if (artists.isEmpty()) "" else "Various artists",
                artistId = albumTracks.mapNotNull { it.artistId }.distinct().singleOrNull(),
                numberOfTracks = albumTracks.size,
                releaseYear = null,
                audioQuality = null,
            )
        }

/** Derives the artist list from scanned tracks. */
fun artistsFrom(tracks: List<TidalTrack>): List<TidalArtist> =
    tracks.mapNotNull { track ->
        val id = track.artistId ?: return@mapNotNull null
        if (track.artistName.isBlank()) null else TidalArtist(id, track.artistName)
    }.distinct()
