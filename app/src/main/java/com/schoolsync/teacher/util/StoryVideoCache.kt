package com.schoolsync.teacher.util

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Process-wide disk cache for story video playback.
 *
 * Ported from the Grader_S reference viewer (Video_cache /
 * PlayerManager_gallery): a single 100 MB LRU [SimpleCache] shared
 * across every story video so a clip the user already watched (or
 * swiped past) replays instantly without re-downloading. This is the
 * main reason the reference viewer feels smoother than a fresh
 * ExoPlayer-per-URL with no cache.
 *
 * SimpleCache is a singleton by contract — only ONE instance may point
 * at a given folder per process, so this is an object.
 */
@UnstableApi
object StoryVideoCache {

    private const val MAX_BYTES = 100L * 1024 * 1024 // 100 MB
    private const val FOLDER = "story_media_cache"

    @Volatile private var cache: SimpleCache? = null

    private fun simpleCache(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.cacheDir, FOLDER),
                LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(context.applicationContext)
            ).also { cache = it }
        }
    }

    /**
     * A [CacheDataSource.Factory] backed by the shared cache, with the
     * network as the upstream source. Pass this to a
     * ProgressiveMediaSource.Factory when building the ExoPlayer media
     * source so reads are served from disk when available.
     */
    fun dataSourceFactory(context: Context): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(simpleCache(context.applicationContext))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context.applicationContext))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
