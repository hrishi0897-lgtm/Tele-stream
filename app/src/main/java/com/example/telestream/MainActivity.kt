package com.example.telestream

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TeleStreamApp()
                }
            }
        }
    }
}

@Composable
fun TeleStreamApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("telestream_prefs", Context.MODE_PRIVATE) }
    val client = remember { TelegramClient() }
    val scope = rememberCoroutineScope()

    var botToken by remember { mutableStateOf(prefs.getString("bot_token", "") ?: "") }
    var chatId by remember { mutableStateOf(prefs.getString("chat_id", "") ?: "") }
    var showSetup by remember { mutableStateOf(botToken.isBlank() || chatId.isBlank()) }

    var movies by remember { mutableStateOf<List<FileManifest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedMovie by remember { mutableStateOf<FileManifest?>(null) }

    fun syncCatalog() {
        if (botToken.isBlank() || chatId.isBlank()) return
        scope.launch {
            isLoading = true
            errorMessage = null
            val res = client.fetchAllMovies(botToken, chatId)
            if (res.isSuccess) {
                movies = res.getOrDefault(emptyList())
                if (movies.isEmpty()) {
                    errorMessage = "No movies found. Make sure TeleVault has uploaded a movie and pinned VAULT_INDEX."
                }
            } else {
                errorMessage = res.exceptionOrNull()?.message ?: "Failed to sync with Telegram"
            }
            isLoading = false
        }
    }

    LaunchedEffect(showSetup) {
        if (!showSetup) syncCatalog()
    }

    when {
        selectedMovie != null -> {
            VideoPlayerScreen(
                movie = selectedMovie!!,
                botToken = botToken,
                defaultChatId = chatId,
                client = client,
                onBack = { selectedMovie = null }
            )
        }
        showSetup -> {
            SetupScreen(
                initialToken = botToken,
                initialChatId = chatId,
                onSave = { newToken, newChat ->
                    botToken = newToken.trim()
                    chatId = newChat.trim()
                    prefs.edit()
                        .putString("bot_token", botToken)
                        .putString("chat_id", chatId)
                        .apply()
                    showSetup = false
                }
            )
        }
        else -> {
            CatalogScreen(
                movies = movies,
                isLoading = isLoading,
                errorMessage = errorMessage,
                botToken = botToken,
                chatId = chatId,
                client = client,
                onRefresh = ::syncCatalog,
                onOpenSettings = { showSetup = true },
                onSelectMovie = { selectedMovie = it }
            )
        }
    }
}

@Composable
fun SetupScreen(
    initialToken: String,
    initialChatId: String,
    onSave: (String, String) -> Unit
) {
    var token by remember { mutableStateOf(initialToken) }
    var chat by remember { mutableStateOf(initialChatId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("TeleStream Setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter the same Bot Token and Storage Channel Chat ID used in your TeleVault uploader app.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Telegram Bot Token") },
            placeholder = { Text("123456789:ABCdef...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = chat,
            onValueChange = { chat = it },
            label = { Text("Channel / Chat ID") },
            placeholder = { Text("-1001234567890") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onSave(token, chat) },
            enabled = token.isNotBlank() && chat.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect & Load Movies")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    movies: List<FileManifest>,
    isLoading: Boolean,
    errorMessage: String?,
    botToken: String,
    chatId: String,
    client: TelegramClient,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectMovie: (FileManifest) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TeleStream Movies", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                errorMessage != null && movies.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRefresh) { Text("Retry Sync") }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(movies, key = { it.fileId }) { movie ->
                            MovieCard(
                                movie = movie,
                                botToken = botToken,
                                chatId = chatId,
                                client = client,
                                onClick = { onSelectMovie(movie) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MovieCard(
    movie: FileManifest,
    botToken: String,
    chatId: String,
    client: TelegramClient,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbFile by remember(movie.fileId) { mutableStateOf<File?>(null) }

    LaunchedEffect(movie.fileId) {
        thumbFile = client.downloadThumbnailToCache(context.cacheDir, botToken, movie, chatId)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF1E1E2C)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbFile != null) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = movie.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = movie.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val sizeMb = String.format("%.1f MB", movie.size / (1024.0 * 1024.0))
                Text(
                    text = "$sizeMb • ${movie.chunks.size} chunks",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    movie: FileManifest,
    botToken: String,
    defaultChatId: String,
    client: TelegramClient,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    val exoPlayer = remember(movie.fileId) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 45_000, 2_500, 5_000)
            .build()

        val dataSourceFactory = TeleVaultChunkDataSource.createFactory(
            context = context,
            movie = movie,
            botToken = botToken,
            defaultChatId = defaultChatId,
            client = client
        )

        val mime = when {
            movie.name.endsWith(".mkv", ignoreCase = true) -> MimeTypes.VIDEO_MATROSKA
            movie.name.endsWith(".webm", ignoreCase = true) -> MimeTypes.VIDEO_WEBM
            else -> movie.mimeType.ifBlank { MimeTypes.VIDEO_MP4 }
        }

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse("televault://stream/${movie.fileId}"))
            .setMimeType(mime)
            .build()

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}
