package com.example.telestream

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

@UnstableApi
class TeleVaultChunkDataSource(
    private val movie: FileManifest,
    private val botToken: String,
    private val defaultChatId: String,
    private val client: TelegramClient
) : BaseDataSource(/* isNetwork = */ true) {

    private val sortedChunks = movie.chunks.sortedBy { it.resolvedIndex }
    private val totalFileSize = if (movie.size > 0L) movie.size else sortedChunks.sumOf { it.size }
    private val pathCache = ConcurrentHashMap<Int, String>()

    private var uri: Uri? = null
    private var globalPosition: Long = 0L
    private var bytesRemaining: Long = 0L
    private var bytesLeftInCurrentChunk: Long = 0L
    private var currentStream: InputStream? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)

        if (sortedChunks.isEmpty()) {
            throw IOException("No chunks found for ${movie.name}")
        }

        if (dataSpec.position >= totalFileSize) {
            bytesRemaining = 0L
            return 0L
        }

        globalPosition = dataSpec.position
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            min(dataSpec.length, totalFileSize - globalPosition)
        } else {
            totalFileSize - globalPosition
        }

        openChunkForOffset(globalPosition)
        transferStarted(dataSpec)
        return bytesRemaining
    }

    private fun openChunkForOffset(targetOffset: Long) {
        closeStream()

        var accumulated = 0L
        var chosenChunk: ManifestChunk? = null
        var offsetInChunk = 0L

        for (chunk in sortedChunks) {
            if (targetOffset < accumulated + chunk.size) {
                chosenChunk = chunk
                offsetInChunk = targetOffset - accumulated
                break
            }
            accumulated += chunk.size
        }

        val chunk = chosenChunk ?: throw IOException("Byte offset $targetOffset out of bounds")
        bytesLeftInCurrentChunk = chunk.size - offsetInChunk

        val remotePath = pathCache.getOrPut(chunk.resolvedIndex) {
            runBlocking { resolveChunkFilePath(chunk) }
        }

        val url = client.fileDownloadUrl(botToken, remotePath)
        val reqBuilder = Request.Builder().url(url)
        if (offsetInChunk > 0L) {
            reqBuilder.header("Range", "bytes=$offsetInChunk-${chunk.size - 1}")
        }

        val response = client.okHttpClient.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful || response.body == null) {
            pathCache.remove(chunk.resolvedIndex)
            response.close()
            throw IOException("Telegram CDN HTTP ${response.code} on chunk ${chunk.resolvedIndex}")
        }

        currentStream = response.body!!.byteStream()
    }

    private suspend fun resolveChunkFilePath(chunk: ManifestChunk): String {
        val channelId = chunk.channelId?.ifBlank { null }
            ?: movie.channelId?.ifBlank { null }
            ?: defaultChatId

        var fileId = chunk.telegramFileId
        if (fileId.isNullOrBlank() && chunk.resolvedMessageId > 0L) {
            fileId = client.regenerateFileId(botToken, channelId, chunk.resolvedMessageId).getOrThrow()
        }

        var pathResult = client.getFilePath(botToken, fileId ?: "")
        if (pathResult.isFailure && chunk.resolvedMessageId > 0L) {
            val freshFileId = client.regenerateFileId(botToken, channelId, chunk.resolvedMessageId).getOrThrow()
            pathResult = client.getFilePath(botToken, freshFileId)
        }

        return pathResult.getOrThrow()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        if (bytesLeftInCurrentChunk == 0L) {
            if (globalPosition >= totalFileSize) return C.RESULT_END_OF_INPUT
            openChunkForOffset(globalPosition)
        }

        val stream = currentStream ?: return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), bytesRemaining, bytesLeftInCurrentChunk).toInt()
        val readCount = stream.read(buffer, offset, toRead)

        if (readCount == -1) {
            bytesLeftInCurrentChunk = 0L
            if (globalPosition < totalFileSize && bytesRemaining > 0L) {
                openChunkForOffset(globalPosition)
                return read(buffer, offset, length)
            }
            return C.RESULT_END_OF_INPUT
        }

        globalPosition += readCount
        bytesLeftInCurrentChunk -= readCount
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= readCount
        }
        bytesTransferred(readCount)
        return readCount
    }

    override fun getUri(): Uri? = uri

    private fun closeStream() {
        try {
            currentStream?.close()
        } catch (_: Exception) {}
        currentStream = null
    }

    override fun close() {
        uri = null
        closeStream()
        transferEnded()
    }

    companion object {
        private var simpleCache: SimpleCache? = null

        @Synchronized
        private fun getCache(context: Context): SimpleCache {
            if (simpleCache == null) {
                val dir = File(context.cacheDir, "telestream_exo_cache")
                simpleCache = SimpleCache(
                    dir,
                    LeastRecentlyUsedCacheEvictor(500L * 1024L * 1024L), // 500MB cache
                    StandaloneDatabaseProvider(context)
                )
            }
            return simpleCache!!
        }

        fun createFactory(
            context: Context,
            movie: FileManifest,
            botToken: String,
            defaultChatId: String,
            client: TelegramClient
        ): DataSource.Factory {
            val upstream = DataSource.Factory {
                TeleVaultChunkDataSource(movie, botToken, defaultChatId, client)
            }
            return CacheDataSource.Factory()
                .setCache(getCache(context))
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
    }
}
