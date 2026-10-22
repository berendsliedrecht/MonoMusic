package com.monoapps.monomusic

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.monoapps.monomusic.local.LocalLibrary
import com.monoapps.monomusic.offline.OfflineManager
import com.monoapps.monomusic.tidal.LibraryCache
import com.monoapps.monomusic.tidal.TidalAuth
import com.monoapps.monomusic.tidal.TidalClient
import com.monoapps.monomusic.tidal.TidalSession
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.io.File

@UnstableApi
class MonoMusic : Application() {

    val httpClient: OkHttpClient by lazy { OkHttpClient() }
    val tidalSession: TidalSession by lazy { TidalSession(this) }
    val tidalAuth: TidalAuth by lazy { TidalAuth(tidalSession, httpClient) }
    val tidalClient: TidalClient by lazy { TidalClient(tidalSession, tidalAuth, httpClient) }
    /**
     * True while the player is audibly streaming from the network (not a
     * downloaded file). The download worker yields on it: stream resolution
     * and downloading compete with playback for Tidal's rate limiter and
     * the wifi link.
     */
    val streamingActive = MutableStateFlow(false)

    val offlineManager: OfflineManager by lazy {
        OfflineManager(this, tidalClient, tidalSession, httpClient, streamingActive)
    }
    val libraryCache: LibraryCache by lazy { LibraryCache(this) }
    val localLibrary: LocalLibrary by lazy { LocalLibrary(this) }

    override fun onCreate() {
        super.onCreate()
        // Warm the disk-touching singletons off the main thread: SimpleCache
        // scans its directory and OfflineManager probes external storage on
        // init, both of which otherwise happen on first main-thread access.
        Thread {
            offlineManager
            mediaCache
        }.start()
    }

    /**
     * Quality Tidal actually delivered, per track id (e.g. "LOSSLESS").
     * Keyed per track because ExoPlayer resolves the next song while the
     * previous one is still playing.
     */
    val resolvedQualities = MutableStateFlow<Map<Long, String>>(emptyMap())

    val mediaCache: SimpleCache by lazy {
        SimpleCache(
            File(cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
            StandaloneDatabaseProvider(this),
        )
    }
}
