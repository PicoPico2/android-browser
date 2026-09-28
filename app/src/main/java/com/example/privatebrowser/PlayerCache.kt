package com.example.privatebrowser

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/** Process-wide VOD cache. Survives PlayerActivity recreation and is bounded to 1 GiB. */
@UnstableApi
internal object PlayerCache {
    @Volatile private var cache: SimpleCache? = null

    @Synchronized
    fun get(context: Context): SimpleCache = cache ?: SimpleCache(
        File(context.applicationContext.cacheDir, "stream_media"),
        LeastRecentlyUsedCacheEvictor(1024L * 1024L * 1024L),
        StandaloneDatabaseProvider(context.applicationContext),
    ).also { cache = it }

    fun dataSource(context: Context, upstream: DataSource.Factory): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
