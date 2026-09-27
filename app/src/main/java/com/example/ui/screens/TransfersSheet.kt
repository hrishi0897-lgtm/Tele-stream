package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.FileStatus
import com.example.domain.ChecksumUtil
import com.example.domain.model.TransferProgress
import com.example.ui.theme.BodySansFont
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.NumericMonoFont

private val CardCornerShape = RoundedCornerShape(14.dp)
private val ProgressTrackCornerShape = RoundedCornerShape(100.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersSheet(
    transfers: List<TransferProgress>,
    onDismiss: () -> Unit,
    onPause: (fileId: String) -> Unit,
    onResume: (fileId: String, isUpload: Boolean) -> Unit,
    onCancel: (fileId: String) -> Unit,
    onRetry: (fileId: String) -> Unit
) {
    val colors = LocalTeleVaultColors.current
    val activeItems = remember(transfers) {
        transfers.filter { it.status != FileStatus.COMPLETED }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.line)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Row: Title and count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transfer Queue",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodySansFont,
                    color = colors.text
                )
                Text(
                    text = "${activeItems.size} items",
                    fontSize = 12.sp,
                    fontFamily = BodySansFont,
                    color = colors.textDim
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Overall Batch Progress banner when multiple transfers are present
            if (activeItems.size > 1) {
                val totalCount = activeItems.size
                val failedCount = activeItems.count { it.status == FileStatus.FAILED }
                val percent = 0
                val animatedProgress by animateFloatAsState(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    label = "sheet_batch_progress_anim"
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.bg),
                    shape = CardCornerShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Overall Batch Progress",
                                fontSize = 12.sp,
                                fontFamily = BodySansFont,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.text
                            )
                            Text(
                                text = "$totalCount items in queue",
                                fontSize = 11.sp,
                                fontFamily = NumericMonoFont,
                                fontWeight = FontWeight.Bold,
                                color = colors.violet
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(ProgressTrackCornerShape),
                            color = colors.violet,
                            trackColor = colors.surfaceHi
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (activeItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.mint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = colors.mint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "All Transfers Completed",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BodySansFont,
                            color = colors.text
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Queue clear",
                            fontSize = 12.sp,
                            fontFamily = BodySansFont,
                            color = colors.textDim
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(activeItems, key = { it.fileId }) { transfer ->
                        TransferItemCard(
                            transfer = transfer,
                            onPause = { onPause(transfer.fileId) },
                            onResume = { onResume(transfer.fileId, transfer.isUpload) },
                            onCancel = { onCancel(transfer.fileId) },
                            onRetry = { onRetry(transfer.fileId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferItemCard(
    transfer: TransferProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    val isFailed = transfer.status == FileStatus.FAILED
    val isDone = transfer.status == FileStatus.COMPLETED
    val percent = (transfer.progressFraction * 100).toInt().coerceIn(0, 100)

    val animatedProgress by animateFloatAsState(
        targetValue = transfer.progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
        label = "sheet_progress_${transfer.fileId}"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.bg),
        shape = CardCornerShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isFailed) colors.danger.copy(alpha = 0.5f) else colors.line
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circle icon container
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isFailed -> colors.danger.copy(alpha = 0.15f)
                                isDone -> colors.mint.copy(alpha = 0.15f)
                                else -> colors.violet.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isDone -> Icons.Default.Check
                            isFailed -> Icons.Default.Error
                            transfer.isUpload -> Icons.Default.CloudUpload
                            else -> Icons.Default.CloudDownload
                        },
                        contentDescription = null,
                        tint = when {
                            isFailed -> colors.danger
                            isDone -> colors.mint
                            else -> colors.violet
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = BodySansFont,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val statusDesc = when {
                        transfer.isChunking -> "Chunking…"
                        transfer.status == FileStatus.PENDING -> "Queued"
                        transfer.status == FileStatus.UPLOADING -> {
                            if (transfer.activeConcurrentChunks > 0) {
                                "Uploading · ${transfer.completedChunksCount}/${transfer.totalChunks} chunks · ${ChecksumUtil.formatSpeed(transfer.speedBytesPerSec)}"
                            } else {
                                "Uploading chunk ${transfer.currentChunk}/${transfer.totalChunks} · ${ChecksumUtil.formatSpeed(transfer.speedBytesPerSec)}"
                            }
                        }
                        transfer.status == FileStatus.DOWNLOADING -> "Downloading chunk ${transfer.currentChunk}/${transfer.totalChunks} · ${ChecksumUtil.formatSpeed(transfer.speedBytesPerSec)}"
                        transfer.status == FileStatus.PAUSED -> "Paused (Chunk ${transfer.currentChunk}/${transfer.totalChunks})"
                        transfer.status == FileStatus.FAILED -> "Failed: ${transfer.errorMessage ?: "Transfer error"}"
                        transfer.status == FileStatus.COMPLETED -> "Done"
                        else -> "Queued"
                    }
                    Text(
                        text = "${ChecksumUtil.formatBytes(transfer.totalBytes)} · $statusDesc",
                        fontSize = 11.sp,
                        fontFamily = NumericMonoFont,
                        color = when (transfer.status) {
                            FileStatus.FAILED -> colors.danger
                            FileStatus.COMPLETED -> colors.mint
                            FileStatus.UPLOADING, FileStatus.DOWNLOADING -> colors.violet
                            else -> colors.textDim
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls with 48dp minimum touch target
                when (transfer.status) {
                    FileStatus.UPLOADING, FileStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = colors.textDim, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = colors.textDim, modifier = Modifier.size(20.dp))
                        }
                    }
                    FileStatus.PAUSED -> {
                        IconButton(onClick = onResume, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = colors.violet, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = colors.textDim, modifier = Modifier.size(20.dp))
                        }
                    }
                    FileStatus.FAILED -> {
                        IconButton(onClick = onRetry, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Replay, contentDescription = "Retry", tint = colors.violet, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = colors.textDim, modifier = Modifier.size(20.dp))
                        }
                    }
                    FileStatus.COMPLETED -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.mint, modifier = Modifier.padding(12.dp).size(20.dp))
                    }
                    FileStatus.PENDING -> {
                        IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = colors.textDim, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Progress bar
            if (transfer.status == FileStatus.UPLOADING || transfer.status == FileStatus.DOWNLOADING || transfer.status == FileStatus.PAUSED) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(ProgressTrackCornerShape),
                    color = colors.violet,
                    trackColor = colors.surfaceHi
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val rate = if (transfer.speedBytesPerSec > 0) " · ${ChecksumUtil.formatSpeed(transfer.speedBytesPerSec)}" else ""
                    Text(
                        text = "$percent%$rate",
                        fontSize = 10.5.sp,
                        fontFamily = NumericMonoFont,
                        color = colors.violet,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val detail = if (transfer.etaSeconds != null && transfer.speedBytesPerSec > 0) {
                        "${ChecksumUtil.formatEta(transfer.etaSeconds)} · ${ChecksumUtil.formatFileSize(transfer.bytesTransferred)} / ${ChecksumUtil.formatFileSize(transfer.totalBytes)}"
                    } else {
                        "${ChecksumUtil.formatFileSize(transfer.bytesTransferred)} / ${ChecksumUtil.formatFileSize(transfer.totalBytes)}"
                    }
                    Text(
                        text = detail,
                        fontSize = 10.5.sp,
                        fontFamily = NumericMonoFont,
                        color = colors.textFaint,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
