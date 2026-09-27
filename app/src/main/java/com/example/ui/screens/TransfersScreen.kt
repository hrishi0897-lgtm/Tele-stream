package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.FileStatus
import com.example.domain.ChecksumUtil
import com.example.domain.model.TransferProgress
import com.example.ui.components.TeleVaultBottomNav
import com.example.ui.theme.BodySansFont
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.LocalTeleVaultColors
import com.example.ui.theme.NumericMonoFont
import com.example.ui.theme.pressScale
import com.example.ui.viewmodel.AppScreen

private val CardCornerShape = RoundedCornerShape(14.dp)
private val ProgressTrackCornerShape = RoundedCornerShape(100.dp)

@Composable
fun TransfersScreen(
    transfers: List<TransferProgress>,
    recentlyCompleted: List<TransferProgress>,
    onBack: () -> Unit,
    onPause: (fileId: String) -> Unit,
    onResume: (fileId: String, isUpload: Boolean) -> Unit,
    onCancel: (fileId: String) -> Unit,
    onRetry: (fileId: String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onNavigateToVault: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    // Active transfers strictly contain only in-progress/queued/paused/failed items, never completed
    val activeItems = remember(transfers) {
        transfers.filter { it.status != FileStatus.COMPLETED }
    }
    val activeCount = activeItems.count {
        it.status == FileStatus.UPLOADING || it.status == FileStatus.DOWNLOADING || it.status == FileStatus.PENDING
    }
    val pausedCount = activeItems.count { it.status == FileStatus.PAUSED }
    val hasActiveTransfers = activeCount > 0
    val hasPausedTransfers = pausedCount > 0

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
        containerColor = colors.bg,
        topBar = {
            TransfersTopBar(
                activeCount = activeCount,
                totalCount = activeItems.size,
                hasActiveTransfers = hasActiveTransfers,
                hasPausedTransfers = hasPausedTransfers,
                onBack = onBack,
                onPauseAll = onPauseAll,
                onResumeAll = onResumeAll
            )
        },
        bottomBar = {
            TeleVaultBottomNav(
                currentScreen = AppScreen.TRANSFERS,
                activeTransferCount = activeItems.size,
                onVaultSelected = onNavigateToVault,
                onTransfersSelected = { /* Already on Transfers */ }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg)
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header_spacer") {
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (activeItems.isEmpty() && recentlyCompleted.isEmpty()) {
                item(key = "empty_transfers") {
                    EmptyTransfersView()
                }
            } else {
                if (activeItems.isNotEmpty()) {
                    item(key = "active_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE TRANSFERS (${activeItems.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodySansFont,
                                color = colors.textFaint,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Overall Batch Progress banner when multiple transfers are queued or in progress
                    if (activeItems.size + recentlyCompleted.size > 1 && activeItems.isNotEmpty()) {
                        val completedCount = recentlyCompleted.size
                        val totalCount = activeItems.size + recentlyCompleted.size
                        val failedCount = activeItems.count { it.status == FileStatus.FAILED }
                        item(key = "batch_progress_banner") {
                            BatchProgressCard(
                                completedCount = completedCount,
                                totalCount = totalCount,
                                failedCount = failedCount
                            )
                        }
                    }

                    items(activeItems, key = { it.fileId }) { transfer ->
                        TransferRowCard(
                            transfer = transfer,
                            onPause = { onPause(transfer.fileId) },
                            onResume = { onResume(transfer.fileId, transfer.isUpload) },
                            onCancel = { onCancel(transfer.fileId) },
                            onRetry = { onRetry(transfer.fileId) }
                        )
                    }
                }

                if (recentlyCompleted.isNotEmpty()) {
                    item(key = "completed_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RECENTLY COMPLETED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodySansFont,
                                color = colors.textFaint,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Clear",
                                fontSize = 12.sp,
                                color = colors.violet,
                                fontFamily = BodySansFont,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .pressScale(0.90f)
                                    .clickable(onClick = onClearCompleted)
                                    .padding(4.dp)
                                    .testTag("btn_clear_completed")
                            )
                        }
                    }

                    items(recentlyCompleted, key = { "completed_${it.fileId}" }) { item ->
                        CompletedTransferCard(item = item)
                    }
                }

                item(key = "footer_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun TransfersTopBar(
    activeCount: Int,
    totalCount: Int,
    hasActiveTransfers: Boolean,
    hasPausedTransfers: Boolean,
    onBack: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    Surface(
        color = colors.bg,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_back_to_vault")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Vault",
                    tint = colors.text,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = "Transfer Queue",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BodySansFont,
                    color = colors.text
                )
                Text(
                    text = if (totalCount == 0) {
                        "Queue clear"
                    } else if (activeCount > 0) {
                        "$activeCount in progress · $totalCount total"
                    } else {
                        "$totalCount items in queue"
                    },
                    fontSize = 12.sp,
                    fontFamily = BodySansFont,
                    color = colors.textDim
                )
            }

            if (hasActiveTransfers) {
                IconButton(
                    onClick = onPauseAll,
                    modifier = Modifier
                        .size(48.dp)
                        .pressScale(0.88f)
                        .testTag("btn_pause_all")
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause All",
                        tint = colors.textDim,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else if (hasPausedTransfers) {
                IconButton(
                    onClick = onResumeAll,
                    modifier = Modifier
                        .size(48.dp)
                        .pressScale(0.88f)
                        .testTag("btn_resume_all")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume All",
                        tint = colors.violet,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchProgressCard(
    completedCount: Int,
    totalCount: Int,
    failedCount: Int
) {
    val colors = LocalTeleVaultColors.current
    val percent = if (totalCount > 0) (completedCount * 100) / totalCount else 0
    val animatedProgress by animateFloatAsState(
        targetValue = (completedCount.toFloat() / totalCount.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "batch_progress_card_anim"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = CardCornerShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("batch_progress_banner")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.violet.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = colors.violet,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Overall Batch Progress",
                            fontSize = 13.sp,
                            fontFamily = BodySansFont,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$completedCount of $totalCount files uploaded${if (failedCount > 0) " · $failedCount failed" else ""}",
                            fontSize = 11.sp,
                            fontFamily = NumericMonoFont,
                            color = if (failedCount > 0) colors.danger else colors.textDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$percent%",
                    fontSize = 14.sp,
                    fontFamily = NumericMonoFont,
                    fontWeight = FontWeight.Bold,
                    color = colors.violet,
                    maxLines = 1
                )
            }
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
        }
    }
}

@Composable
private fun TransferRowCard(
    transfer: TransferProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = LocalTeleVaultColors.current
    val reduceMotion = LocalReduceMotion.current
    val isFailed = transfer.status == FileStatus.FAILED
    val isDone = transfer.status == FileStatus.COMPLETED
    val percent = (transfer.progressFraction * 100).toInt().coerceIn(0, 100)

    val animatedProgress by animateFloatAsState(
        targetValue = transfer.progressFraction.coerceIn(0f, 1f),
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 350, easing = LinearOutSlowInEasing),
        label = "transfer_progress_${transfer.fileId}"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = CardCornerShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isFailed) colors.danger.copy(alpha = 0.5f) else colors.line
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isFailed, onClick = onRetry)
            .testTag("transfer_item_${transfer.fileId}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Icon, File Name, Status, and Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular icon container matching SettingsScreen and HomeScreen bento styles
                Box(
                    modifier = Modifier
                        .size(40.dp)
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
                        contentDescription = if (transfer.isUpload) "Upload" else "Download",
                        tint = when {
                            isFailed -> colors.danger
                            isDone -> colors.mint
                            else -> colors.violet
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = BodySansFont,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                transfer.isChunking -> "Chunking…"
                                transfer.status == FileStatus.UPLOADING -> if (transfer.speedBytesPerSec > 0) "Uploading…" else "Connecting to Telegram…"
                                transfer.status == FileStatus.DOWNLOADING -> if (transfer.speedBytesPerSec > 0) "Downloading…" else "Connecting to Telegram…"
                                transfer.status == FileStatus.PAUSED -> "Paused"
                                transfer.status == FileStatus.PENDING -> "Queued"
                                transfer.status == FileStatus.FAILED -> "Failed — tap to retry"
                                transfer.status == FileStatus.COMPLETED -> "Done"
                                else -> "Queued"
                            },
                            fontSize = 12.sp,
                            fontFamily = BodySansFont,
                            fontWeight = if (isFailed) FontWeight.Medium else FontWeight.Normal,
                            color = when (transfer.status) {
                                FileStatus.FAILED -> colors.danger
                                FileStatus.COMPLETED -> colors.mint
                                FileStatus.UPLOADING, FileStatus.DOWNLOADING -> colors.violet
                                FileStatus.PAUSED -> colors.textDim
                                else -> colors.textFaint
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = " · ${ChecksumUtil.formatBytes(transfer.totalBytes)}",
                            fontSize = 11.sp,
                            fontFamily = NumericMonoFont,
                            color = colors.textDim,
                            maxLines = 1
                        )
                    }
                }

                // Action Controls with guaranteed 48dp minimum touch targets
                when (transfer.status) {
                    FileStatus.UPLOADING, FileStatus.DOWNLOADING -> {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_pause_${transfer.fileId}")
                        ) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "Pause transfer",
                                tint = colors.textDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_cancel_${transfer.fileId}")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel transfer",
                                tint = colors.textDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    FileStatus.PAUSED -> {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_resume_${transfer.fileId}")
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Resume transfer",
                                tint = colors.violet,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_cancel_${transfer.fileId}")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel transfer",
                                tint = colors.textDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    FileStatus.FAILED -> {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_retry_${transfer.fileId}")
                        ) {
                            Icon(
                                Icons.Default.Replay,
                                contentDescription = "Retry transfer",
                                tint = colors.violet,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_cancel_${transfer.fileId}")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = colors.textDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    FileStatus.PENDING -> {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_cancel_${transfer.fileId}")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel queued transfer",
                                tint = colors.textDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    FileStatus.COMPLETED -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colors.mint,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(24.dp)
                        )
                    }
                }
            }

            // Progress Bar: 4dp height with rounded clip, matching StorageBentoGrid meter
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(ProgressTrackCornerShape),
                color = when {
                    isFailed -> colors.danger
                    isDone -> colors.mint
                    else -> colors.violet
                },
                trackColor = colors.surfaceHi
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 1. Chunk Progress Row (Full-width row dedicated to chunk status)
            val chunkProgressLabel = when {
                transfer.isUpload && transfer.status == FileStatus.UPLOADING && transfer.activeConcurrentChunks > 0 -> {
                    "${transfer.activeConcurrentChunks} of ${transfer.totalChunks} chunks uploading, ${transfer.completedChunksCount} complete · $percent%"
                }
                transfer.isUpload && transfer.completedChunksCount > 0 -> {
                    "Chunk ${transfer.completedChunksCount} of ${transfer.totalChunks} complete · $percent%"
                }
                else -> "Chunk ${transfer.currentChunk} of ${transfer.totalChunks} · $percent%"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chunkProgressLabel,
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Byte Progress Row (Dedicated row below chunk-progress to prevent text collision)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${ChecksumUtil.formatBytes(transfer.bytesTransferred)} / ${ChecksumUtil.formatBytes(transfer.totalBytes)}",
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont,
                    fontWeight = FontWeight.Medium,
                    color = colors.textDim,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Secondary Metrics Row: Transfer speed and ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val speedText = when (transfer.status) {
                    FileStatus.UPLOADING, FileStatus.DOWNLOADING -> {
                        if (transfer.speedBytesPerSec > 0) {
                            "${if (transfer.isUpload) "↑" else "↓"} ${ChecksumUtil.formatSpeed(transfer.speedBytesPerSec)}"
                        } else {
                            "Connecting…"
                        }
                    }
                    FileStatus.PAUSED -> "Paused"
                    FileStatus.COMPLETED -> "Finished"
                    FileStatus.FAILED -> "Failed"
                    else -> "Queued"
                }

                Text(
                    text = speedText,
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont,
                    fontWeight = FontWeight.Medium,
                    color = if (transfer.status == FileStatus.UPLOADING || transfer.status == FileStatus.DOWNLOADING) colors.violet else colors.textFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                val etaText = when (transfer.status) {
                    FileStatus.UPLOADING, FileStatus.DOWNLOADING -> {
                        if (transfer.speedBytesPerSec > 0 && transfer.etaSeconds != null) {
                            ChecksumUtil.formatEta(transfer.etaSeconds)
                        } else if (transfer.bytesTransferred >= transfer.totalBytes && transfer.totalBytes > 0) {
                            "Finalizing…"
                        } else {
                            "Estimating…"
                        }
                    }
                    FileStatus.PAUSED -> {
                        val remaining = (transfer.totalBytes - transfer.bytesTransferred).coerceAtLeast(0L)
                        "${ChecksumUtil.formatBytes(remaining)} left"
                    }
                    FileStatus.COMPLETED -> "Verified SHA-256"
                    else -> ""
                }

                if (etaText.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = etaText,
                        fontSize = 11.sp,
                        fontFamily = NumericMonoFont,
                        fontWeight = FontWeight.Normal,
                        color = colors.textFaint,
                        maxLines = 1
                    )
                }
            }

            // Visible Failure Reason Banner if failed
            if (isFailed && !transfer.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.danger.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = colors.danger,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = transfer.errorMessage,
                        fontSize = 11.sp,
                        fontFamily = BodySansFont,
                        color = colors.danger,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedTransferCard(item: TransferProgress) {
    val colors = LocalTeleVaultColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = CardCornerShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.mint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = colors.mint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = BodySansFont,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${ChecksumUtil.formatBytes(item.totalBytes)} · Done",
                    fontSize = 11.sp,
                    fontFamily = NumericMonoFont,
                    color = colors.textDim
                )
            }
        }
    }
}

@Composable
private fun EmptyTransfersView() {
    val colors = LocalTeleVaultColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceHi),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = colors.textDim,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No active transfers",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BodySansFont,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Queue clear",
                fontSize = 13.sp,
                fontFamily = BodySansFont,
                color = colors.textDim,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
