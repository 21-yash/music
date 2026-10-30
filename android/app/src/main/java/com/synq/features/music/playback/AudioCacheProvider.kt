package com.synq.features.music.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class AudioCacheProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamCacheStorage: StreamCacheStorage,
) {
    companion object {
        private const val CACHE_DIR = "audio_cache"
        const val MAX_CACHE_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB
    }

    private val databaseProvider by lazy {
        StandaloneDatabaseProvider(context)
    }

    private val evictor by lazy {
        LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
    }

    val cache: SimpleCache by lazy {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        SimpleCache(cacheDir, evictor, databaseProvider)
    }

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    fun updateCacheSize() {
        try {
            _cacheSizeBytes.value = cache.cacheSpace
        } catch (_: Exception) {}
    }

    fun getCacheSizeBytes(): Long {
        return try {
            cache.cacheSpace
        } catch (_: Exception) {
            0L
        }
    }

    fun buildCacheDataSourceFactory(): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val cacheSinkFactory = CacheDataSink.Factory()
            .setCache(cache)
            .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE)

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setCacheWriteDataSinkFactory(cacheSinkFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setCacheKeyFactory { dataSpec ->
                dataSpec.key ?: dataSpec.uri.toString()
            }
    }

    fun hasSpansForKey(key: String): Boolean {
        return try {
            cache.getCachedSpans(key).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun isFullyCached(songId: String): Boolean {
        return isKeyFullyCached(songId) || run {
            val savedUri = streamCacheStorage.getStreamUri(songId)
            !savedUri.isNullOrBlank() && isKeyFullyCached(savedUri)
        }
    }

    fun isKeyFullyCached(key: String): Boolean {
        return try {
            val metadata = cache.getContentMetadata(key)
            val contentLength = ContentMetadata.getContentLength(metadata)
            if (contentLength <= 0L) return false
            val spans = cache.getCachedSpans(key).map { it.position to it.length }
            isRangeFullyCovered(spans, contentLength)
        } catch (_: Exception) {
            false
        }
    }

    fun isSongCached(songId: String): Boolean {
        return isFullyCached(songId)
    }

    fun remove(songId: String): Boolean {
        return try {
            cache.removeResource(songId)
            val savedUri = streamCacheStorage.getStreamUri(songId)
            if (!savedUri.isNullOrBlank()) {
                cache.removeResource(savedUri)
            }
            updateCacheSize()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearCache(): Boolean {
        return try {
            val keys = cache.keys.toList()
            keys.forEach { key ->
                cache.removeResource(key)
            }
            streamCacheStorage.clear()
            updateCacheSize()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun release() {
        try {
            cache.release()
        } catch (_: Exception) {}
    }
}

/**
 * Pure helper function to verify if a set of cached spans fully covers
 * the byte range from 0 until totalLength without gaps.
 */
fun isRangeFullyCovered(spans: List<Pair<Long, Long>>, totalLength: Long): Boolean {
    if (totalLength <= 0L || spans.isEmpty()) return false

    val sorted = spans.sortedBy { it.first }
    var coveredUntil = 0L

    for ((start, length) in sorted) {
        if (length <= 0L) continue
        if (start > coveredUntil) {
            // Gap detected in cached spans
            return false
        }
        val end = start + length
        if (end > coveredUntil) {
            coveredUntil = end
        }
        if (coveredUntil >= totalLength) {
            return true
        }
    }

    return coveredUntil >= totalLength
}
