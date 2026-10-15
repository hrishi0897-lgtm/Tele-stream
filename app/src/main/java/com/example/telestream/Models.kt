package com.example.telestream

import com.squareup.moshi.Json

data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val description: String? = null,
    @Json(name = "error_code") val errorCode: Int? = null
)

data class TelegramChat(
    val id: Long,
    val title: String? = null,
    @Json(name = "pinned_message") val pinnedMessage: TelegramMessage? = null
)

data class TelegramUpdate(
    @Json(name = "update_id") val updateId: Long,
    val message: TelegramMessage? = null,
    @Json(name = "channel_post") val channelPost: TelegramMessage? = null
)

data class TelegramMessage(
    @Json(name = "message_id") val messageId: Long,
    val text: String? = null,
    val caption: String? = null,
    val document: TelegramDocument? = null
)

data class TelegramDocument(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "mime_type") val mimeType: String? = null,
    @Json(name = "file_size") val fileSize: Long? = null
)

data class TelegramRemoteFile(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_path") val filePath: String? = null,
    @Json(name = "file_size") val fileSize: Long? = null
)

// Matches TeleVault's FileManifest & ManifestChunk
data class FileManifest(
    val fileId: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val overallSha256: String = "",
    val folderId: String? = null,
    val channelId: String? = null,
    val uploadDate: Long = 0L,
    val chunks: List<ManifestChunk> = emptyList(),
    val thumbnailMessageId: Long? = null,
    val thumbnailFileId: String? = null
)

data class ManifestChunk(
    val index: Int = 0,
    val chunkIndex: Int? = null, // Fallback if serialized under chunkIndex
    val messageId: Long = 0L,
    val telegramMessageId: Long? = null,
    val channelId: String? = null,
    val telegramFileId: String? = null,
    val sha256: String = "",
    val size: Long = 0L
) {
    val resolvedIndex: Int get() = chunkIndex ?: index
    val resolvedMessageId: Long get() = telegramMessageId ?: messageId
}

data class VaultIndex(
    val timestamp: Long = 0L,
    val files: List<FileManifest> = emptyList()
)
