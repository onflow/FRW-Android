package com.flowfoundation.wallet.utils.exoplayer

import android.content.Context
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSink
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.flowfoundation.wallet.utils.CACHE_VIDEO_PATH
import com.flowfoundation.wallet.utils.Env


class ExoplayerCacheFactory(
    private val context: Context,
    // 1G
    private val maxCacheSize: Long = 1024 * 1024 * 1024,
    // 200M
    private val maxFileSize: Long = 1024 * 1024 * 200,
) : DataSource.Factory {
    private val defaultDatasourceFactory by lazy { DefaultDataSource.Factory(context) }

    private val cache by lazy { SimpleCache(CACHE_VIDEO_PATH, LeastRecentlyUsedCacheEvictor(maxCacheSize)) }

    override fun createDataSource(): DataSource {
        return CacheDataSource(
            cache,
            defaultDatasourceFactory.createDataSource(),
            FileDataSource(),
            CacheDataSink(cache, maxFileSize),
            CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
            null
        )
    }
}

private val cacheFactory by lazy { ExoplayerCacheFactory(Env.getApp()) }

fun createExoPlayer(context: Context): ExoPlayer = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory)).build()