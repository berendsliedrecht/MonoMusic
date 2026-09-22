package com.calmapps.calmmusic

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.calmapps.calmmusic.data.MonoMusicSettingsManager
import com.calmapps.calmmusic.data.NowPlayingStorage
import okhttp3.OkHttpClient
import java.io.File

@UnstableApi
class MonoMusic : Application() {

    val mediaCache: SimpleCache by lazy {
        val cacheDirectory = File(this.cacheDir, "media_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(256L * 1024L * 1024L) // 256 MB
        SimpleCache(cacheDirectory, evictor)
    }

    val cacheDataSourceFactory: CacheDataSource.Factory by lazy {
        val upstream = DefaultDataSource.Factory(this)
        CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    val youTubeSearchClient: YouTubeMusicSearchClient by lazy {
        YouTubeMusicSearchClientImpl.create()
    }

    val youTubeInnertubeClient: YouTubeMusicInnertubeClient by lazy {
        val client = OkHttpClient.Builder().build()
        YouTubeMusicInnertubeClientImpl(client)
    }

    val youTubeStreamResolver: YouTubeStreamResolver by lazy {
        YouTubeStreamResolver()
    }

    val youTubePrecacheManager: YouTubePrecacheManager by lazy {
        YouTubePrecacheManager(this)
    }

    /** Which resolver produced the current stream URL; shown on Now Playing. */
    val streamResolverLabel = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val nowPlayingStorage: NowPlayingStorage by lazy {
        NowPlayingStorage(this)
    }

    lateinit var settingsManager: MonoMusicSettingsManager
        private set

    lateinit var youTubeDownloadManager: YouTubeDownloadManager
        private set

    override fun onCreate() {
        super.onCreate()

        settingsManager = MonoMusicSettingsManager(this)
        youTubeDownloadManager = YouTubeDownloadManager(
            app = this,
            appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
        )

    }

}
