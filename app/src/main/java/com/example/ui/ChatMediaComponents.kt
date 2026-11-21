package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.ui.theme.BadgeGoldBright
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.util.ChatAttachmentUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VoiceRecorderController(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    fun startRecording(): Result<File> {
        stopInternal(deleteFile = true)
        return try {
            val dir = File(context.cacheDir, "station_chat_voice").apply { mkdirs() }
            val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(48_000)
                setAudioSamplingRate(22_050)
                setMaxFileSize(9_500_000L)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            outputFile = file
            startedAtMs = System.currentTimeMillis()
            Result.success(file)
        } catch (e: Exception) {
            Log.w("VoiceRecorder", "Failed to start voice recording", e)
            stopInternal(deleteFile = true)
            Result.failure(e)
        }
    }

    fun finishRecording(): Pair<File, Long>? {
        val file = outputFile
        val duration = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(500L)
        stopInternal(deleteFile = false)
        return if (file != null && file.exists() && file.length() > 100L) {
            Pair(file, duration)
        } else {
            file?.delete()
            null
        }
    }

    fun cancelRecording() {
        stopInternal(deleteFile = true)
    }

    private fun stopInternal(deleteFile: Boolean) {
        try {
            recorder?.apply {
                stop()
            }
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        if (deleteFile) {
            outputFile?.delete()
        }
        outputFile = null
    }
}

data class AudioPlaybackState(
    val activeMessageId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

class ChatAudioPlayerController(private val scope: CoroutineScope) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    fun togglePlayPause(messageId: String, audioFile: File, onError: (String) -> Unit = {}) {
        val curr = _state.value
        val mp = mediaPlayer
        if (curr.activeMessageId == messageId && mp != null) {
            try {
                if (mp.isPlaying) {
                    mp.pause()
                    _state.value = curr.copy(isPlaying = false, positionMs = mp.currentPosition.toLong())
                } else {
                    mp.start()
                    _state.value = curr.copy(isPlaying = true)
                    startTicker()
                }
                return
            } catch (_: Exception) {
                release()
            }
        }

        release()
        try {
            val newPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(audioFile.absolutePath)
                setOnPreparedListener { player ->
                    val dur = player.duration.toLong().coerceAtLeast(0L)
                    player.start()
                    _state.value = AudioPlaybackState(
                        activeMessageId = messageId,
                        isPlaying = true,
                        positionMs = 0L,
                        durationMs = dur
                    )
                    startTicker()
                }
                setOnCompletionListener {
                    _state.value = _state.value.copy(isPlaying = false, positionMs = _state.value.durationMs)
                    progressJob?.cancel()
                }
                setOnErrorListener { _, _, _ ->
                    onError("හඬ පණිවිඩය ධාවනය කළ නොහැක.")
                    release()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = newPlayer
        } catch (e: Exception) {
            Log.w("ChatAudioPlayer", "Unable to play audio file", e)
            onError("හඬ පණිවිඩය ධාවනය කිරීමේ දෝෂයකි.")
            release()
        }
    }

    fun seekTo(messageId: String, targetMs: Long) {
        val mp = mediaPlayer ?: return
        if (_state.value.activeMessageId != messageId) return
        try {
            val safe = targetMs.toInt().coerceIn(0, mp.duration.coerceAtLeast(0))
            mp.seekTo(safe)
            _state.value = _state.value.copy(positionMs = safe.toLong())
        } catch (_: Exception) {
        }
    }

    private fun startTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val mp = mediaPlayer
                if (mp != null && mp.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = mp.currentPosition.toLong(),
                        durationMs = mp.duration.toLong().coerceAtLeast(_state.value.durationMs),
                        isPlaying = true
                    )
                }
                delay(200L)
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        _state.value = AudioPlaybackState()
    }
}

fun openFileWithSystemChooser(context: Context, file: File, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val resolvedMime = mimeType.ifBlank { "*/*" }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "ගොනුව විවෘත කරන්න"))
    } catch (_: Exception) {
        Toast.makeText(context, "මෙම ගොනුව විවෘත කිරීමට සුදුසු App එකක් හමු නොවීය.", Toast.LENGTH_SHORT).show()
    }
}

fun shareFileWithSystemChooser(context: Context, file: File, mimeType: String, title: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "ගොනුව බෙදාගන්න (Share)"))
    } catch (_: Exception) {
        Toast.makeText(context, "ගොනුව බෙදාගැනීමට නොහැකි විය.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun InAppPdfViewerDialog(
    file: File,
    title: String,
    expiresAtMs: Long = 0L,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pages by remember(file.absolutePath) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember(file.absolutePath) { mutableStateOf(true) }
    var errorMsg by remember(file.absolutePath) { mutableStateOf<String?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    DisposableEffect(file.absolutePath) {
        onDispose {
            pages.forEach { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
    }

    LaunchedEffect(file.absolutePath) {
        isLoading = true
        errorMsg = null
        val loaded = withContext(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                val count = renderer.pageCount
                val list = ArrayList<Bitmap>(count)
                val maxRenderPages = minOf(count, 40)
                for (i in 0 until maxRenderPages) {
                    val page = renderer.openPage(i)
                    val targetWidth = 1280
                    val ratio = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                    val targetHeight = (page.height * ratio).toInt().coerceAtLeast(100)
                    val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AndroidColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    list.add(bmp)
                }
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                try {
                    renderer?.close()
                } catch (_: Exception) {
                }
                try {
                    pfd?.close()
                } catch (_: Exception) {
                }
            }
        }
        isLoading = false
        if (loaded.isSuccess) {
            pages = loaded.getOrDefault(emptyList())
            if (pages.isEmpty()) errorMsg = "PDF ගොනුවේ පිටු කිසිවක් හමු නොවීය."
        } else {
            errorMsg = "PDF ගොනුව කියවීමට නොහැක: ${loaded.exceptionOrNull()?.localizedMessage ?: ""}"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F172A)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PoliceNavyDark)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFFCA5A5))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (pages.isNotEmpty()) {
                                "පිටු ${pages.size} • ${ChatAttachmentUtils.formatFileSize(file.length())}"
                            } else {
                                ChatAttachmentUtils.formatRemainingRetention(expiresAtMs)
                            },
                            color = BadgeGoldBright,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f) }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
                    }
                    IconButton(onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3f) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
                    }
                    IconButton(onClick = { openFileWithSystemChooser(context, file, "application/pdf") }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Externally", tint = BadgeGoldBright)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    when {
                        isLoading -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = BadgeGoldBright)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("PDF පිටු සකසමින් පවතී...", color = Color.White, fontSize = 13.sp)
                            }
                        }
                        errorMsg != null -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(errorMsg!!, color = Color(0xFFFCA5A5), fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { openFileWithSystemChooser(context, file, "application/pdf") }) {
                                    Text("බාහිර PDF App එකකින් විවෘත කරන්න")
                                }
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, _, zoom, _ ->
                                            zoomScale = (zoomScale * zoom).coerceIn(1f, 3f)
                                        }
                                    },
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(pages) { index, bmp ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            shadowElevation = 6.dp,
                                            color = Color.White
                                        ) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "PDF Page ${index + 1}",
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .graphicsLayer {
                                                        scaleX = zoomScale
                                                        scaleY = zoomScale
                                                    }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "පිටුව ${index + 1} / ${pages.size}",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InAppFileViewerDialog(
    file: File,
    title: String,
    mimeType: String,
    expiresAtMs: Long = 0L,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = remember(mimeType, title) { ChatAttachmentUtils.isVideoFile(mimeType, title) }
    val isText = remember(mimeType, title) { ChatAttachmentUtils.isTextFile(mimeType, title) }

    var textContent by remember(file.absolutePath) { mutableStateOf<String?>(null) }
    var textLoading by remember(file.absolutePath) { mutableStateOf(isText) }

    LaunchedEffect(file.absolutePath, isText) {
        if (isText) {
            textLoading = true
            textContent = withContext(Dispatchers.IO) {
                try {
                    val bytes = file.inputStream().use { input ->
                        val maxRead = 512 * 1024
                        val buf = ByteArray(maxRead)
                        val read = input.read(buf)
                        if (read > 0) buf.copyOf(read) else ByteArray(0)
                    }
                    String(bytes, Charsets.UTF_8)
                } catch (_: Exception) {
                    "ගොනුව කියවීමට නොහැකි විය."
                }
            }
            textLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PoliceNavyDark)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            isVideo -> Icons.Default.VideoFile
                            isText -> Icons.Default.Description
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = BadgeGoldBright
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${ChatAttachmentUtils.formatFileSize(file.length())} • ${ChatAttachmentUtils.formatRemainingRetention(expiresAtMs)}",
                            color = BadgeGoldBright,
                            fontSize = 10.5.sp
                        )
                    }
                    IconButton(onClick = { shareFileWithSystemChooser(context, file, mimeType, title) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share File", tint = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    when {
                        isVideo -> {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                                        setVideoURI(uri)
                                        val controller = MediaController(ctx)
                                        controller.setAnchorView(this)
                                        setMediaController(controller)
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = false
                                            start()
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            Toast.makeText(ctx, "වීඩියෝව app එක තුළ ධාවනය කළ නොහැක.", Toast.LENGTH_SHORT).show()
                                            false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center)
                                    .aspectRatio(16f / 9f)
                            )
                        }
                        isText -> {
                            if (textLoading) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            } else {
                                SelectionContainer {
                                    Text(
                                        text = textContent.orEmpty(),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(14.dp)
                                    )
                                }
                            }
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = PoliceNavyPrimary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(84.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = PoliceNavyPrimary,
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ප්‍රමාණය: ${ChatAttachmentUtils.formatFileSize(file.length())} • ${mimeType.ifBlank { "File" }}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ChatAttachmentUtils.formatRemainingRetention(expiresAtMs),
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFB45309),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { openFileWithSystemChooser(context, file, mimeType) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PoliceNavyPrimary),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(0.8f).height(48.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ගොනුව විවෘත කරන්න (Open File)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
