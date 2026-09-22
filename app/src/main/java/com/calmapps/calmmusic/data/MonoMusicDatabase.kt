package com.calmapps.calmmusic.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Song::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class MonoMusicDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: MonoMusicDatabase? = null

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN discNumber INTEGER")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN releaseYear INTEGER")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN localLastModifiedMillis INTEGER")
                db.execSQL("ALTER TABLE songs ADD COLUMN localFileSizeBytes INTEGER")
            }
        }

        /**
         * Stable-id schema. Streamed YouTube rows already use the video id and map
         * over directly; local/downloaded rows keep their old uri-based ids here and
         * are merged into scanned rows (by localUri) on the first library sync, which
         * also recomputes the grouping keys. Playlists carry over untouched.
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE songs_new (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "title TEXT NOT NULL, " +
                        "artist TEXT NOT NULL, " +
                        "albumArtist TEXT, " +
                        "album TEXT, " +
                        "trackNumber INTEGER, " +
                        "discNumber INTEGER, " +
                        "durationMillis INTEGER, " +
                        "releaseYear INTEGER, " +
                        "artistKey TEXT, " +
                        "albumKey TEXT, " +
                        "localUri TEXT, " +
                        "localLastModified INTEGER, " +
                        "localSizeBytes INTEGER)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO songs_new (id, title, artist, albumArtist, album, " +
                        "trackNumber, discNumber, durationMillis, releaseYear, artistKey, albumKey, " +
                        "localUri, localLastModified, localSizeBytes) " +
                        "SELECT id, title, artist, NULL, album, trackNumber, discNumber, " +
                        "durationMillis, releaseYear, NULL, NULL, " +
                        "CASE WHEN sourceType IN ('LOCAL_FILE', 'YOUTUBE_DOWNLOAD') THEN audioUri ELSE NULL END, " +
                        "localLastModifiedMillis, localFileSizeBytes " +
                        "FROM songs"
                )
                db.execSQL("DROP TABLE songs")
                db.execSQL("ALTER TABLE songs_new RENAME TO songs")
                db.execSQL("DROP TABLE IF EXISTS albums")
                db.execSQL("DROP TABLE IF EXISTS artists")
            }
        }

        fun getDatabase(context: Context): MonoMusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MonoMusicDatabase::class.java,
                    "calmmusic.db",
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
