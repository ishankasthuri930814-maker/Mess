package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.data.StationChatMessage
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.BadgeGoldBright
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceThemeColors
import com.example.ui.theme.isDark
import com.example.util.ChatAttachmentUtils
import com.example.util.DateTimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private sealed class OpenedChatMedia {
    data class ImageViewer(val file: File, val title: String) : OpenedChatMedia()
    data class PdfViewer(val file: File, val title: String, val expiresAtMs: Long) : OpenedChatMedia()
    data class GenericViewer(val file: File, val title: String, val mimeType: String, val expiresAtMs: Long) : OpenedChatMedia()
}

@Composable
fun ChatTopBarButton(
    unreadCount: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.testTag("station_chat_top_btn")
    ) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(containerColor = Color(0xFF10B981)) {
                        Text(
                            text = if (unreadCount > 99) "99+" else "$unreadCount",
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Forum,
                contentDescription = "Station Chat Room",
                tint = tint
            )
        }
    }
}

@Composable
fun StationChatDialog(
    viewModel: PoliceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    val messages by viewModel.stationChatMessages.collectAsStateWithLifecycle()
    val chatLoadError by viewModel.stationChatError.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.chatUploadProgress.collectAsStateWithLifecycle()
    val appUser by viewModel.appUser.collectAsStateWithLifecycle()
    val myUid = appUser?.uid.orEmpty()
    val stationTitle = appUser?.stationName?.takeIf { it.isNotBlank() }
        ?: appUser?.oicStationCode?.takeIf { it.isNotBlank() }
        ?: "පොලිස් ස්ථානය"

    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var downloadingMessageId by remember { mutableStateOf<String?>(null) }
    var openedMedia by remember { mutableStateOf<OpenedChatMedia?>(null) }
    var cachedFilesVersion by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    val voiceRecorder = remember(context) { VoiceRecorderController(context) }
    val audioPlayer = remember(coroutineScope) { ChatAudioPlayerController(coroutineScope) }
    val playbackState by audioPlayer.state.collectAsStateWithLifecycle()

    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableLongStateOf(0L) }

    fun closeDialogSafe() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (isRecordingVoice) {
            voiceRecorder.cancelRecording()
            isRecordingVoice = false
        }
        audioPlayer.release()
        viewModel.setChatDialogOpen(false)
        onDismiss()
    }

    DisposableEffect(Unit) {
        viewModel.setChatDialogOpen(true)
        onDispose {
            voiceRecorder.cancelRecording()
            audioPlayer.release()
            viewModel.setChatDialogOpen(false)
        }
    }

    LaunchedEffect(messages) {
        viewModel.markChatAsRead()
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingSeconds = 0L
            while (isRecordingVoice) {
                delay(1000L)
                recordingSeconds++
            }
        }
    }

    fun startVoiceCapture() {
        sendError = null
        val startRes = voiceRecorder.startRecording()
        if (startRes.isSuccess) {
            isRecordingVoice = true
        } else {
            sendError = "හඬ පටිගත කිරීම ආරම්භ කළ නොහැක."
        }
    }

    val recordAudioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceCapture()
        } else {
            sendError = "හඬ පණිවිඩ යැවීමට Microphone අවසරය ලබාදිය යුතුය."
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isSending = true
            sendError = null
            val caption = inputText.trim()
            viewModel.sendStationChatAttachmentUri(
                uri = uri,
                captionText = caption,
                onSuccess = {
                    if (caption.isNotEmpty()) inputText = ""
                    isSending = false
                    sendError = null
                },
                onError = { err ->
                    isSending = false
                    sendError = err
                }
            )
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isSending = true
            sendError = null
            val caption = inputText.trim()
            viewModel.sendStationChatAttachmentUri(
                uri = uri,
                captionText = caption,
                onSuccess = {
                    if (caption.isNotEmpty()) inputText = ""
                    isSending = false
                    sendError = null
                },
                onError = { err ->
                    isSending = false
                    sendError = err
                }
            )
        }
    }

    fun sendCurrentMessage() {
        val trimmed = inputText.trim()
        if (trimmed.isBlank() || isSending) return
        isSending = true
        sendError = null
        viewModel.sendStationChatMessage(
            messageText = trimmed,
            onSuccess = {
                inputText = ""
                sendError = null
                isSending = false
            },
            onError = { err ->
                sendError = err
                isSending = false
            }
        )
    }

    fun finishAndSendVoice() {
        if (!isRecordingVoice) return
        isRecordingVoice = false
        val recorded = voiceRecorder.finishRecording()
        if (recorded == null) {
            sendError = "හඬ පණිවිඩය ඉතා කෙටි විය. නැවත උත්සාහ කරන්න."
            return
        }
        val (file, durationMs) = recorded
        isSending = true
        sendError = null
        viewModel.sendStationChatVoiceNote(
            voiceFile = file,
            durationMs = durationMs,
            onSuccess = {
                isSending = false
                sendError = null
            },
            onError = { err ->
                isSending = false
                sendError = err
            }
        )
    }

    fun openAttachmentInApp(msg: StationChatMessage) {
        if (downloadingMessageId == msg.id) return
        sendError = null
        coroutineScope.launch {
            downloadingMessageId = msg.id
            val res = viewModel.resolveChatAttachmentFile(msg)
            downloadingMessageId = null
            if (res.isSuccess) {
                cachedFilesVersion++
                val file = res.getOrThrow()
                val title = msg.attachmentName.ifBlank { file.name }
                when (msg.messageType.uppercase()) {
                    "AUDIO" -> {
                        audioPlayer.togglePlayPause(msg.uniqueKey(), file) { err ->
                            sendError = err
                        }
                    }
                    "IMAGE" -> {
                        openedMedia = OpenedChatMedia.ImageViewer(file = file, title = title)
                    }
                    "PDF" -> {
                        openedMedia = OpenedChatMedia.PdfViewer(
                            file = file,
                            title = title,
                            expiresAtMs = msg.expiresAtMs
                        )
                    }
                    else -> {
                        if (msg.attachmentMimeType.startsWith("audio/", ignoreCase = true)) {
                            audioPlayer.togglePlayPause(msg.uniqueKey(), file) { err ->
                                sendError = err
                            }
                        } else {
                            openedMedia = OpenedChatMedia.GenericViewer(
                                file = file,
                                title = title,
                                mimeType = msg.attachmentMimeType,
                                expiresAtMs = msg.expiresAtMs
                            )
                        }
                    }
                }
            } else {
                sendError = res.exceptionOrNull()?.localizedMessage ?: "ගොනුව විවෘත කිරීම අසාර්ථක විය."
            }
        }
    }

    Dialog(
        onDismissRequest = { closeDialogSafe() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.89f)
                .imePadding(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PoliceNavyDark)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BadgeGold.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = null,
                            tint = BadgeGoldBright,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ස්ථාන සංවාද කුටිය (Chat Room)",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "$stationTitle • OIC / පාලන අංශය / නිලධාරීන්",
                            fontSize = 11.sp,
                            color = BadgeGoldBright
                        )
                    }
                    IconButton(onClick = { closeDialogSafe() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Chat",
                            tint = Color.White
                        )
                    }
                }

                // 48h Cloud & App Auto-Retention Info Strip
                Surface(
                    color = PoliceThemeColors.navyText.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoDelete,
                            contentDescription = null,
                            tint = PoliceThemeColors.navyText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "හඬ, PDF, ඡායාරූප සහ 10 MB ට අඩු ඕනෑම ගොනුවක් යැවිය හැක • දින 2 කින් ස්වයංක්‍රීයව ඉවත් වේ",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }

                // Messages List
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "තවමත් පණිවිඩ කිසිවක් නොමැත.\nපෙළ, හඬ පණිවිඩ, ඡායාරූප, PDF හෝ 10 MB ට අඩු ගොනු යවන්න.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("station_chat_list"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = messages,
                                key = { it.uniqueKey() }
                            ) { msg ->
                                val cachedFile = remember(msg.id, cachedFilesVersion) {
                                    viewModel.getCachedChatAttachmentFile(msg)
                                }
                                ChatMessageBubble(
                                    message = msg,
                                    isMine = msg.senderUid == myUid,
                                    cachedFile = cachedFile,
                                    isDownloading = downloadingMessageId == msg.id,
                                    playbackState = playbackState,
                                    onSeekAudio = { targetMs -> audioPlayer.seekTo(msg.uniqueKey(), targetMs) },
                                    onOpenAttachment = { openAttachmentInApp(msg) }
                                )
                            }
                        }
                    }
                }

                // Upload Progress Bar
                if (uploadProgress != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            val pct = ((uploadProgress ?: 0f) * 100).toInt().coerceIn(0, 100)
                            Text(
                                text = "Cloud වෙත උඩුගත වෙමින් පවතී... $pct%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PoliceThemeColors.navyText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (uploadProgress ?: 0f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                val activeError = sendError ?: chatLoadError
                if (!activeError.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeError,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("station_chat_error_text")
                            )
                            if (sendError != null) {
                                IconButton(
                                    onClick = { sendError = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss error",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Composer (Normal mode vs Voice Recording mode)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp
                ) {
                    if (isRecordingVoice) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFDC2626).copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    voiceRecorder.cancelRecording()
                                    isRecordingVoice = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Cancel Voice",
                                    tint = Color(0xFFDC2626)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFDC2626))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "පටිගත වෙමින්... ${ChatAttachmentUtils.formatDuration(recordingSeconds * 1000L)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.weight(1f)
                            )
                            FilledIconButton(
                                onClick = { finishAndSendVoice() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("station_chat_send_voice_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Voice Note"
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        if (!isSending) {
                                            pickImageLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    },
                                    enabled = !isSending,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = PoliceThemeColors.blueText.copy(alpha = 0.18f),
                                        contentColor = PoliceThemeColors.blueText,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.size(42.dp).testTag("station_chat_image_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Send Image",
                                        modifier = Modifier.size(21.dp)
                                    )
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        if (!isSending) {
                                            pickFileLauncher.launch(arrayOf("*/*"))
                                        }
                                    },
                                    enabled = !isSending,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = PoliceThemeColors.goldText.copy(alpha = 0.18f),
                                        contentColor = PoliceThemeColors.goldText,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.size(42.dp).testTag("station_chat_attach_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Attach PDF or File",
                                        modifier = Modifier.size(21.dp)
                                    )
                                }

                                val micTint = if (MaterialTheme.colorScheme.isDark) Color(0xFFF87171) else Color(0xFFDC2626)
                                FilledTonalIconButton(
                                    onClick = {
                                        if (!isSending) {
                                            val hasMic = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasMic) {
                                                startVoiceCapture()
                                            } else {
                                                recordAudioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    },
                                    enabled = !isSending,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = micTint.copy(alpha = 0.16f),
                                        contentColor = micTint,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.size(42.dp).testTag("station_chat_mic_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Record Voice Message",
                                        modifier = Modifier.size(21.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = {
                                        if (it.length <= 1000) {
                                            inputText = it
                                            sendError = null
                                        }
                                    },
                                    placeholder = { Text("පණිවිඩයක් ලියන්න...", fontSize = 12.5.sp) },
                                    singleLine = true,
                                    maxLines = 1,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { sendCurrentMessage() }),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("station_chat_input")
                                )
                                FilledIconButton(
                                    onClick = { sendCurrentMessage() },
                                    enabled = inputText.isNotBlank() && !isSending,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier
                                        .size(46.dp)
                                        .testTag("station_chat_send_btn")
                                ) {
                                    if (isSending) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send Chat Message"
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

    // In-App Media / PDF / Image / File Viewers
    when (val media = openedMedia) {
        is OpenedChatMedia.ImageViewer -> {
            PhotoViewerDialog(
                photoPath = media.file.absolutePath,
                title = media.title,
                onDismiss = { openedMedia = null }
            )
        }
        is OpenedChatMedia.PdfViewer -> {
            InAppPdfViewerDialog(
                file = media.file,
                title = media.title,
                expiresAtMs = media.expiresAtMs,
                onDismiss = { openedMedia = null }
            )
        }
        is OpenedChatMedia.GenericViewer -> {
            InAppFileViewerDialog(
                file = media.file,
                title = media.title,
                mimeType = media.mimeType,
                expiresAtMs = media.expiresAtMs,
                onDismiss = { openedMedia = null }
            )
        }
        null -> Unit
    }
}

@Composable
private fun ChatMessageBubble(
    message: StationChatMessage,
    isMine: Boolean,
    cachedFile: File?,
    isDownloading: Boolean,
    playbackState: AudioPlaybackState,
    onSeekAudio: (Long) -> Unit,
    onOpenAttachment: () -> Unit
) {
    val darkTheme = MaterialTheme.colorScheme.isDark
    val roleColor = when (message.senderRole.uppercase()) {
        "OIC" -> if (darkTheme) Color(0xFFFBBF24) else Color(0xFFB45309)
        "ADMIN_BRANCH" -> if (darkTheme) Color(0xFF34D399) else Color(0xFF047857)
        else -> if (darkTheme) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
    }
    val bubbleContainer = if (isMine) {
        PoliceNavyPrimary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
    val subTextColor = if (isMine) BadgeGoldBright else roleColor
    val timeMs = message.effectiveTimestampMs()
    val msgType = message.messageType.uppercase()
    val msgKey = message.uniqueKey()
    val isThisAudioActive = playbackState.activeMessageId == msgKey
    val isThisAudioPlaying = isThisAudioActive && playbackState.isPlaying

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            color = bubbleContainer,
            border = if (isMine) null else BorderStroke(1.dp, roleColor.copy(alpha = 0.28f)),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 316.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.formatSenderHeader(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = subTextColor
                )

                if (message.hasAttachment()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    when {
                        msgType == "AUDIO" || message.attachmentMimeType.startsWith("audio/", ignoreCase = true) -> {
                            val totalMs = if (isThisAudioActive && playbackState.durationMs > 0L) {
                                playbackState.durationMs
                            } else {
                                message.attachmentDurationMs.coerceAtLeast(1000L)
                            }
                            val currMs = if (isThisAudioActive) playbackState.positionMs else 0L
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isMine) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isDownloading) { onOpenAttachment() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isMine) BadgeGoldBright else MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = if (isMine) PoliceNavyDark else MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isThisAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isThisAudioPlaying) "Pause Voice" else "Play Voice",
                                                tint = if (isMine) PoliceNavyDark else MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Slider(
                                            value = if (totalMs > 0L) (currMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f,
                                            onValueChange = { frac ->
                                                if (isThisAudioActive) onSeekAudio((frac * totalMs).toLong())
                                            },
                                            modifier = Modifier.height(22.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = if (isThisAudioActive) {
                                                    "${ChatAttachmentUtils.formatDuration(currMs)} / ${ChatAttachmentUtils.formatDuration(totalMs)}"
                                                } else {
                                                    "🎤 ${ChatAttachmentUtils.formatDuration(message.attachmentDurationMs)}"
                                                },
                                                fontSize = 10.sp,
                                                color = textColor.copy(alpha = 0.85f),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = ChatAttachmentUtils.formatFileSize(message.attachmentSizeBytes),
                                                fontSize = 10.sp,
                                                color = textColor.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        msgType == "IMAGE" -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isMine) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isDownloading) { onOpenAttachment() }
                            ) {
                                Column {
                                    if (cachedFile != null && cachedFile.exists()) {
                                        Image(
                                            painter = rememberAsyncImagePainter(
                                                ImageRequest.Builder(LocalContext.current)
                                                    .data(cachedFile)
                                                    .crossfade(true)
                                                    .build()
                                            ),
                                            contentDescription = message.attachmentName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(170.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = if (isMine) BadgeGoldBright else PoliceThemeColors.navyText,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Photo,
                                                contentDescription = null,
                                                tint = if (isMine) BadgeGoldBright else PoliceThemeColors.navyText,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = message.attachmentName.ifBlank { "ඡායාරූපය" },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${ChatAttachmentUtils.formatFileSize(message.attachmentSizeBytes)} • විශාල කර බැලීමට ඔබන්න",
                                                fontSize = 10.sp,
                                                color = textColor.copy(alpha = 0.78f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            val isPdf = msgType == "PDF"
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isMine) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isDownloading) { onOpenAttachment() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isPdf) Color(0xFFDC2626) else PoliceNavyDark
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = when {
                                                    isPdf -> Icons.Default.PictureAsPdf
                                                    ChatAttachmentUtils.isVideoFile(message.attachmentMimeType, message.attachmentName) ->
                                                        Icons.Default.VideoFile
                                                    else -> Icons.Default.InsertDriveFile
                                                },
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = message.attachmentName.ifBlank { if (isPdf) "Document.pdf" else "ගොනුව" },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${ChatAttachmentUtils.formatFileSize(message.attachmentSizeBytes)} • ${if (cachedFile != null) "විවෘත කරන්න" else "බාගෙන විවෘත කරන්න"}",
                                            fontSize = 10.sp,
                                            color = if (isMine) BadgeGoldBright else PoliceThemeColors.navyText,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Open Attachment",
                                        tint = textColor.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (message.message.isNotBlank() &&
                    !(msgType == "AUDIO" && message.message.startsWith("🎤"))
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.message,
                        fontSize = 13.5.sp,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }

                if (timeMs > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = DateTimeUtils.formatTime(timeMs),
                        fontSize = 10.sp,
                        color = if (isMine) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}
