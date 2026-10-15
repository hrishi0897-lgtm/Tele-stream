package com.example.telestream

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.File
import java.util.concurrent.TimeUnit

interface TelegramBotService {
    @GET
    suspend fun getChat(@Url url: String, @Query("chat_id") chatId: String): Response<TelegramResponse<TelegramChat>>

    @GET
    suspend fun getUpdates(@Url url: String, @Query("limit") limit: Int = 100): Response<TelegramResponse<List<TelegramUpdate>>>

    @GET
    suspend fun getFile(@Url url: String, @Query("file_id") fileId: String): Response<TelegramResponse<TelegramRemoteFile>>

    @POST
    suspend fun forwardMessage(
        @Url url: String,
        @Query("chat_id") chatId: String,
        @Query("from_chat_id") fromChatId: String,
        @Query("message_id") messageId: Long
    ): Response<TelegramResponse<TelegramMessage>>

    @POST
    suspend fun deleteMessage(
        @Url url: String,
        @Query("chat_id") chatId: String,
        @Query("message_id") messageId: Long
    ): Response<TelegramResponse<Boolean>>

    @Streaming
    @GET
    suspend fun downloadFile(@Url fileUrl: String): Response<ResponseBody>
}

class TelegramClient {
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val manifestAdapter = moshi.adapter(FileManifest::class.java)
    private val vaultIndexAdapter = moshi.adapter(VaultIndex::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    private val service: TelegramBotService = Retrofit.Builder()
        .baseUrl("https://api.telegram.org/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TelegramBotService::class.java)

    private fun botUrl(token: String, method: String) =
        "https://api.telegram.org/bot${token.trim()}/$method"

    fun fileDownloadUrl(token: String, filePath: String) =
        "https://api.telegram.org/file/bot${token.trim()}/${filePath.trim().removePrefix("/")}"

    suspend fun getFilePath(token: String, fileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val resp = service.getFile(botUrl(token, "getFile"), fileId)
            val path = resp.body()?.result?.filePath
            if (resp.isSuccessful && !path.isNullOrBlank()) {
                Result.success(path)
            } else {
                Result.failure(Exception(resp.body()?.description ?: "HTTP ${resp.code()} resolving file_id"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun regenerateFileId(token: String, channelId: String, messageId: Long): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val fwd = service.forwardMessage(
                    url = botUrl(token, "forwardMessage"),
                    chatId = channelId,
                    fromChatId = channelId,
                    messageId = messageId
                )
                val msg = fwd.body()?.result
                val newFileId = msg?.document?.fileId
                if (msg != null) {
                    try {
                        service.deleteMessage(botUrl(token, "deleteMessage"), channelId, msg.messageId)
                    } catch (_: Exception) {}
                }
                if (!newFileId.isNullOrBlank()) {
                    Result.success(newFileId)
                } else {
                    Result.failure(Exception("Could not regenerate file_id for message $messageId"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchAllMovies(token: String, chatId: String): Result<List<FileManifest>> =
        withContext(Dispatchers.IO) {
            try {
                val movies = mutableMapOf<String, FileManifest>()

                // 1. Check Pinned VAULT_INDEX message in the channel
                val chatResp = service.getChat(botUrl(token, "getChat"), chatId.trim())
                val pinnedDoc = chatResp.body()?.result?.pinnedMessage?.document
                if (pinnedDoc != null) {
                    val indexJson = downloadDocumentAsString(token, pinnedDoc.fileId)
                    if (indexJson != null) {
                        parseVaultIndexOrManifests(token, chatId, indexJson).forEach {
                            if (isVideoFile(it)) movies[it.fileId] = it
                        }
                    }
                }

                // 2. Also check recent getUpdates for MANIFEST| documents or VAULT_INDEX
                val updatesResp = service.getUpdates(botUrl(token, "getUpdates"))
                val updates = updatesResp.body()?.result ?: emptyList()
                for (u in updates) {
                    val msg = u.message ?: u.channelPost ?: continue
                    val doc = msg.document ?: continue
                    val caption = msg.caption ?: ""

                    if (caption.startsWith("MANIFEST|")) {
                        val json = downloadDocumentAsString(token, doc.fileId)
                        if (json != null) {
                            runCatching { manifestAdapter.fromJson(json) }.getOrNull()?.let {
                                if (isVideoFile(it)) movies[it.fileId] = it
                            }
                        }
                    } else if (caption.startsWith("VAULT_INDEX") || doc.fileName?.contains("index") == true) {
                        val indexJson = downloadDocumentAsString(token, doc.fileId)
                        if (indexJson != null) {
                            parseVaultIndexOrManifests(token, chatId, indexJson).forEach {
                                if (isVideoFile(it)) movies[it.fileId] = it
                            }
                        }
                    }
                }

                Result.success(movies.values.sortedByDescending { it.uploadDate })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun parseVaultIndexOrManifests(
        token: String,
        defaultChatId: String,
        json: String
    ): List<FileManifest> {
        val result = mutableListOf<FileManifest>()
        // Attempt direct VaultIndex parsing first
        runCatching {
            val direct = vaultIndexAdapter.fromJson(json)
            if (direct != null && direct.files.isNotEmpty() && direct.files.first().chunks.isNotEmpty()) {
                return direct.files
            }
        }

        // Flexible fallback if VaultIndex stores chunks in a separate root list or manifestMessageId
        runCatching {
            val rawMap = mapAdapter.fromJson(json) ?: return emptyList()
            val rawFiles = rawMap["files"] as? List<Map<String, Any?>> ?: return emptyList()
            val rawChunks = (rawMap["chunks"] as? List<Map<String, Any?>>) ?: emptyList()

            val chunksByFileId = rawChunks.groupBy { it["fileId"] as? String ?: "" }

            for (f in rawFiles) {
                val deletedAt = f["deletedAt"]
                if (deletedAt != null) continue // Skip trashed files

                val id = (f["fileId"] ?: f["id"]) as? String ?: continue
                val name = f["name"] as? String ?: "Movie"
                val size = (f["size"] as? Number)?.toLong() ?: 0L
                val mimeType = f["mimeType"] as? String ?: "video/mp4"
                val channelId = (f["channelId"] as? String) ?: defaultChatId
                val thumbFileId = f["thumbnailFileId"] as? String
                val thumbMsgId = (f["thumbnailMessageId"] as? Number)?.toLong()
                val manifestMsgId = (f["manifestMessageId"] as? Number)?.toLong()

                // Check embedded chunks or root chunks array
                val embeddedChunks = (f["chunks"] as? List<Map<String, Any?>>) ?: chunksByFileId[id] ?: emptyList()
                var parsedChunks = embeddedChunks.mapIndexed { idx, c ->
                    ManifestChunk(
                        index = (c["index"] as? Number ?: c["chunkIndex"] as? Number)?.toInt() ?: idx,
                        messageId = (c["messageId"] as? Number ?: c["telegramMessageId"] as? Number)?.toLong() ?: 0L,
                        channelId = (c["channelId"] as? String) ?: channelId,
                        telegramFileId = c["telegramFileId"] as? String,
                        sha256 = (c["sha256"] ?: c["checksum"]) as? String ?: "",
                        size = (c["size"] as? Number)?.toLong() ?: 0L
                    )
                }

                // If chunks aren't inside VaultIndex, pull the file's manifestMessageId from Telegram
                if (parsedChunks.isEmpty() && manifestMsgId != null && manifestMsgId > 0L) {
                    val manifestFileId = regenerateFileId(token, channelId, manifestMsgId).getOrNull()
                    if (manifestFileId != null) {
                        val manifestJson = downloadDocumentAsString(token, manifestFileId)
                        if (manifestJson != null) {
                            val fullManifest = runCatching { manifestAdapter.fromJson(manifestJson) }.getOrNull()
                            if (fullManifest != null) {
                                result.add(fullManifest)
                                continue
                            }
                        }
                    }
                }

                result.add(
                    FileManifest(
                        fileId = id,
                        name = name,
                        size = size,
                        mimeType = mimeType,
                        channelId = channelId,
                        chunks = parsedChunks,
                        thumbnailFileId = thumbFileId,
                        thumbnailMessageId = thumbMsgId
                    )
                )
            }
        }
        return result
    }

    suspend fun downloadThumbnailToCache(
        cacheDir: File,
        token: String,
        movie: FileManifest,
        defaultChatId: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val outFile = File(cacheDir, "thumb_${movie.fileId}.jpg")
            if (outFile.exists() && outFile.length() > 0L) return@withContext outFile

            var fileId = movie.thumbnailFileId
            val channelId = movie.channelId?.ifBlank { defaultChatId } ?: defaultChatId

            if (fileId.isNullOrBlank() && movie.thumbnailMessageId != null) {
                fileId = regenerateFileId(token, channelId, movie.thumbnailMessageId).getOrNull()
            }
            if (fileId.isNullOrBlank()) return@withContext null

            var pathResult = getFilePath(token, fileId)
            if (pathResult.isFailure && movie.thumbnailMessageId != null) {
                fileId = regenerateFileId(token, channelId, movie.thumbnailMessageId).getOrNull()
                if (!fileId.isNullOrBlank()) pathResult = getFilePath(token, fileId)
            }

            val path = pathResult.getOrNull() ?: return@withContext null
            val resp = service.downloadFile(fileDownloadUrl(token, path))
            val body = resp.body() ?: return@withContext null

            body.byteStream().use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            outFile
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun downloadDocumentAsString(token: String, fileId: String): String? {
        val path = getFilePath(token, fileId).getOrNull() ?: return null
        val resp = service.downloadFile(fileDownloadUrl(token, path))
        return if (resp.isSuccessful) resp.body()?.string() else null
    }

    private fun isVideoFile(manifest: FileManifest): Boolean {
        val n = manifest.name.lowercase()
        return manifest.mimeType.startsWith("video/", ignoreCase = true) ||
                n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".avi") || n.endsWith(".mov")
    }
}
