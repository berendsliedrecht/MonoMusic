package com.monoapps.monomusic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Media3 playback service. Queue items carry tidal://track/<id> URIs; the
 * resolving data source swaps in a fresh signed stream URL at load time
 * (Tidal stream URLs expire, so they are never resolved up front).
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "monomusic_playback_channel"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 500, 1000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(createDataSourceFactory()))
            .setLoadControl(loadControl)
            .build()

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player.setAudioAttributes(audioAttributes, true)
        player.setHandleAudioBecomingNoisy(true)

        // Tell the download worker when playback streams from the network,
        // so it stays off Tidal's rate limiter and the wifi; playing a
        // downloaded file does not pause downloads.
        val app = application as MonoMusic
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = update()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = update()
            private fun update() {
                val trackId = player.currentMediaItem?.mediaId?.toLongOrNull()
                // Negative ids are local files; they never touch the network.
                app.streamingActive.value = player.isPlaying &&
                    (trackId == null ||
                        (trackId > 0 && app.offlineManager.fileFor(trackId) == null))
            }
        })

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Controllers can't send URIs across the binder, so rebuild the
        // tidal:// URI from the media id when items arrive. The cache key
        // includes the quality setting so a quality change doesn't replay
        // old cached audio under the new label.
        val callback = object : MediaSession.Callback {
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
            ): ListenableFuture<MutableList<MediaItem>> {
                val quality = app.tidalSession.audioQuality.name
                val resolved = mediaItems.map { item ->
                    val id = item.mediaId.toLongOrNull()
                    if (id != null && id < 0) {
                        // Local MediaStore track: play its content URI directly.
                        item.buildUpon()
                            .setUri(
                                ContentUris.withAppendedId(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, -id
                                )
                            )
                            .build()
                    } else {
                        item.buildUpon()
                            .setUri("tidal://track/${item.mediaId}")
                            .setCustomCacheKey("tidal/${item.mediaId}/$quality")
                            .build()
                    }
                }.toMutableList()
                return Futures.immediateFuture(resolved)
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(callback)
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        setMediaNotificationProvider(notificationProvider)
    }

    @OptIn(UnstableApi::class)
    private fun createDataSourceFactory(): DataSource.Factory {
        val app = application as MonoMusic

        val upstreamFactory = OkHttpDataSource.Factory(app.httpClient)

        val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
            if (dataSpec.uri.scheme != "tidal") return@Factory dataSpec

            val trackId = dataSpec.uri.lastPathSegment?.toLongOrNull()
                ?: return@Factory dataSpec
            val streamInfo = runBlocking(Dispatchers.IO) {
                app.tidalClient.streamInfo(trackId)
            }
            app.resolvedQualities.value =
                app.resolvedQualities.value + (trackId to streamInfo.audioQuality)
            dataSpec.withUri(streamInfo.url.toUri())
        }

        // Cache keyed on the stable tidal:// URI, so re-listens replay from
        // disk even though the resolved signed URLs differ every time.
        val networkAndCacheStack = CacheDataSource.Factory()
            .setCache(app.mediaCache)
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Routes file:// (pinned downloads) to a local reader and everything
        // else into the cache + network stack.
        val localAwareFactory = DefaultDataSource.Factory(this, networkAndCacheStack)

        // Outermost: pinned tracks swap to their local file before any
        // caching or network is involved, so they play fully offline.
        return ResolvingDataSource.Factory(localAwareFactory) { dataSpec ->
            val trackId = dataSpec.uri.takeIf { it.scheme == "tidal" }
                ?.lastPathSegment?.toLongOrNull()
                ?: return@Factory dataSpec
            val file = app.offlineManager.fileFor(trackId) ?: return@Factory dataSpec
            app.offlineManager.qualityFor(trackId)?.let { quality ->
                app.resolvedQualities.value =
                    app.resolvedQualities.value + (trackId to "${quality.name} (offline)")
            }
            dataSpec.withUri(Uri.fromFile(file))
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Music playback controls" }
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    // Deliberate: on this device, swiping the app away should silence it.
    // The default MediaSessionService behaviour (keep playing) is surprising
    // on an e-ink phone with no quick way back to the notification shade.
    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
