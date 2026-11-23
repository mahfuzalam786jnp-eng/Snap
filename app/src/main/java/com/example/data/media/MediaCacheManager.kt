package com.example.data.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.data.model.VideoQualityOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
object MediaCacheManager {
    private const val TAG = "MediaCache"
    private const val MAX_CACHE_BYTES = 180L * 1024L * 1024L // 180 MB LRU video cache
    private const val PRELOAD_BYTES = 1_500_000L // Preload first ~1.5 MB of next video

    @Volatile
    private var simpleCache: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: run {
                val cacheDir = File(context.applicationContext.cacheDir, "snap_reels_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
                val dbProvider = StandaloneDatabaseProvider(context.applicationContext)
                SimpleCache(cacheDir, evictor, dbProvider).also { simpleCache = it }
            }
        }
    }

    fun buildCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val appContext = context.applicationContext
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(12_000)
            .setAllowCrossProtocolRedirects(true)
        val upstreamFactory = DefaultDataSource.Factory(appContext, httpFactory)
        return CacheDataSource.Factory()
            .setCache(getCache(appContext))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun createReelsPlayer(
        context: Context,
        qualityOption: VideoQualityOption = VideoQualityOption.AUTO
    ): ExoPlayer {
        val appContext = context.applicationContext
        val trackSelector = DefaultTrackSelector(appContext).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, qualityOption.maxHeight)
                .setMaxVideoBitrate(qualityOption.maxBitrate)
                .build()
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1500,
                /* maxBufferMs = */ 12000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(buildCacheDataSourceFactory(appContext))

        return ExoPlayer.Builder(appContext)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    fun updatePlayerQuality(player: ExoPlayer, qualityOption: VideoQualityOption) {
        val selector = player.trackSelector as? DefaultTrackSelector ?: return
        selector.parameters = selector.buildUponParameters()
            .setMaxVideoSize(Int.MAX_VALUE, qualityOption.maxHeight)
            .setMaxVideoBitrate(qualityOption.maxBitrate)
            .build()
    }

    fun preloadNextVideo(context: Context, url: String, scope: CoroutineScope) {
        if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) return
        scope.launch(Dispatchers.IO) {
            try {
                val cacheDataSource = buildCacheDataSourceFactory(context).createDataSource()
                val dataSpec = DataSpec.Builder()
                    .setUri(Uri.parse(url))
                    .setLength(PRELOAD_BYTES)
                    .build()
                CacheWriter(cacheDataSource, dataSpec, null, null).cache()
            } catch (e: Exception) {
                Log.d(TAG, "Preload skipped for $url: ${e.message}")
            }
        }
    }

    fun getCacheSizeMb(context: Context): Double {
        return try {
            val bytes = getCache(context).cacheSpace
            bytes / (1024.0 * 1024.0)
        } catch (_: Exception) {
            0.0
        }
    }

    fun clearCache(context: Context) {
        try {
            val cacheDir = File(context.applicationContext.cacheDir, "snap_reels_cache")
            synchronized(this) {
                simpleCache?.release()
                simpleCache = null
            }
            cacheDir.deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear video cache: ${e.message}")
        }
    }
}
