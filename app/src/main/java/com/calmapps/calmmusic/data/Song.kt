package com.calmapps.calmmusic.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One library song. The id is stable for the song's lifetime:
 *  - YouTube songs (streamed or downloaded): the video id.
 *  - Pure local files: "local:" + a content hash of the file.
 *
 * A download is not a separate kind of song; it is a YouTube song whose
 * [localUri] is set. Files may move freely: [localUri] is plain metadata,
 * so playlists and grouping never break when storage changes.
 */
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val albumArtist: String?,
    val album: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMillis: Long?,
    val releaseYear: Int?,
    /** Normalized grouping keys, computed once at write time. */
    val artistKey: String?,
    val albumKey: String?,
    /** Local copy, if any. null = stream-only. */
    val localUri: String?,
    val localLastModified: Long?,
    val localSizeBytes: Long?,
) {
    val isYouTube: Boolean get() = !id.startsWith(LOCAL_ID_PREFIX)
    val hasLocalCopy: Boolean get() = localUri != null

    companion object {
        const val LOCAL_ID_PREFIX = "local:"

        fun normalizeKey(value: String): String =
            value.trim().replace(Regex("\\s+"), " ").lowercase()

        fun artistKeyOf(artist: String, albumArtist: String?): String? =
            (albumArtist?.takeIf { it.isNotBlank() } ?: artist.takeIf { it.isNotBlank() })
                ?.let(::normalizeKey)

        fun albumKeyOf(artistKey: String?, album: String?): String? {
            val albumPart = album?.takeIf { it.isNotBlank() }?.let(::normalizeKey) ?: return null
            return "${artistKey.orEmpty()}/$albumPart"
        }
    }
}

/** Album row derived from songs; albums have no table of their own. */
data class AlbumRow(
    val albumKey: String,
    val album: String,
    val albumArtist: String,
    val songCount: Int,
    val releaseYear: Int?,
)

/** Artist row derived from songs. */
data class ArtistRow(
    val artistKey: String,
    val artist: String,
    val songCount: Int,
    val albumCount: Int,
)

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    suspend fun getAll(): List<Song>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: String): Song?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<Song>

    @Query("SELECT * FROM songs WHERE albumKey = :albumKey ORDER BY discNumber, trackNumber, title COLLATE NOCASE")
    suspend fun getByAlbumKey(albumKey: String): List<Song>

    @Query("SELECT * FROM songs WHERE artistKey = :artistKey ORDER BY albumKey, discNumber, trackNumber, title COLLATE NOCASE")
    suspend fun getByArtistKey(artistKey: String): List<Song>

    @Query(
        "SELECT albumKey, " +
            "MIN(album) AS album, " +
            "MIN(COALESCE(albumArtist, artist)) AS albumArtist, " +
            "COUNT(*) AS songCount, " +
            "MAX(releaseYear) AS releaseYear " +
            "FROM songs WHERE albumKey IS NOT NULL " +
            "GROUP BY albumKey ORDER BY album COLLATE NOCASE"
    )
    suspend fun getAlbums(): List<AlbumRow>

    @Query(
        "SELECT artistKey, " +
            "MIN(COALESCE(albumArtist, artist)) AS artist, " +
            "COUNT(*) AS songCount, " +
            "COUNT(DISTINCT albumKey) AS albumCount " +
            "FROM songs WHERE artistKey IS NOT NULL " +
            "GROUP BY artistKey ORDER BY artist COLLATE NOCASE"
    )
    suspend fun getArtists(): List<ArtistRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<Song>)

    @Query("UPDATE songs SET localUri = :localUri, localLastModified = :lastModified, localSizeBytes = :sizeBytes WHERE id = :id")
    suspend fun updateLocalCopy(id: String, localUri: String?, lastModified: Long?, sizeBytes: Long?)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
