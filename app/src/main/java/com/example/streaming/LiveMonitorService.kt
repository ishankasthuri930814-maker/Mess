package com.example.streaming

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.MainActivity
import com.example.R
import com.example.data.LiveMonitorSession
import com.example.notifications.NotificationHelper
import com.example.util.LiveMediaCodecUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LiveMonitorService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var analysisExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var audioJob: Job? = null
    private var sessionListener: ListenerRegistration? = null
    private var officerListener: ListenerRegistration? = null

    private var activeSessionId: String = ""
    private var activeOfficerUid: String = ""
    private var activeOicUid: String = ""
    private var activeOicName: String = ""
    private var activeStationCode: String = ""
    private var currentLensFacing: String = "BACK"
    private var wantCamera: Boolean = false
    private var wantMic: Boolean = false
    private var isForegroundEntered: Boolean = false

    private var videoSeq: Long = 0L
    private var lastFrameSentMs: Long = 0L
    private val isVideoUploading = AtomicBoolean(false)

    private var audioSeq: Long = 0L

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        analysisExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || intent?.action == ACTION_STOP_BY_OFFICER) {
            val sid = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty().ifBlank { activeSessionId }
            if (sid.isNotBlank()) {
                markSessionEnded(sid)
            }
            stopMonitoringAndSelf()
            return START_NOT_STICKY
        }

        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val officerUid = intent?.getStringExtra(EXTRA_OFFICER_UID).orEmpty()
            .ifBlank { try { Firebase.auth.currentUser?.uid.orEmpty() } catch (_: Exception) { "" } }
        val oicUid = intent?.getStringExtra(EXTRA_OIC_UID).orEmpty()
        val oicName = intent?.getStringExtra(EXTRA_OIC_NAME).orEmpty()
        val stationCode = intent?.getStringExtra(EXTRA_STATION_CODE).orEmpty()
        val reqCam = intent?.getBooleanExtra(EXTRA_REQ_CAMERA, false) ?: false
        val reqMic = intent?.getBooleanExtra(EXTRA_REQ_AUDIO, false) ?: false
        val lens = intent?.getStringExtra(EXTRA_LENS_FACING).orEmpty().ifBlank { "BACK" }

        val hasCamPerm = hasCameraPermission(this)
        val hasMicPerm = hasMicPermission(this)
        val effCam = reqCam && hasCamPerm
        val effMic = reqMic && hasMicPerm

        if (sessionId.isBlank() || officerUid.isBlank() || oicUid.isBlank() || (!effCam && !effMic)) {
            stopMonitoringAndSelf()
            return START_NOT_STICKY
        }

        // If already running the exact same session and lens unchanged, keep running cleanly
        if (isForegroundEntered && activeSessionId == sessionId) {
            if (effCam && currentLensFacing != lens) {
                currentLensFacing = lens
                bindCameraIfNeeded()
            }
            return START_STICKY
        }

        val entered = enterForegroundWithMediaTypes(
            useCamera = effCam,
            useMic = effMic,
            oicName = oicName,
            sessionId = sessionId
        )
        if (!entered) {
            // Android 14+ blocks background start of while-in-use camera/mic FGS; notify officer to tap
            updateSessionNeedsForeground(sessionId, effCam, effMic)
            NotificationHelper.showLiveMonitorRequestNotification(
                context = this,
                sessionId = sessionId,
                oicName = oicName,
                requestedCamera = effCam,
                requestedAudio = effMic
            )
            stopMonitoringAndSelf()
            return START_NOT_STICKY
        }

        isForegroundEntered = true
        activeSessionId = sessionId
        activeOfficerUid = officerUid
        activeOicUid = oicUid
        activeOicName = oicName
        activeStationCode = stationCode
        wantCamera = effCam
        wantMic = effMic
        currentLensFacing = lens
        videoSeq = 0L
        audioSeq = 0L
        _activeStreamingSessionId.value = sessionId

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        db.collection("live_monitors").document(sessionId).update(
            mapOf(
                "status" to "ACTIVE",
                "cameraActive" to effCam,
                "micActive" to effMic,
                "updatedAtMs" to System.currentTimeMillis()
            )
        )

        attachListeners(sessionId, officerUid)
        if (effCam) bindCameraIfNeeded()
        if (effMic) startAudioCaptureLoop()

        return START_STICKY
    }

    private fun enterForegroundWithMediaTypes(
        useCamera: Boolean,
        useMic: Boolean,
        oicName: String,
        sessionId: String
    ): Boolean {
        val notification = buildNotification(useCamera, useMic, oicName, sessionId)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                var typeMask = 0
                if (useCamera) typeMask = typeMask or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                if (useMic) typeMask = typeMask or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                startForeground(NOTIFICATION_ID, notification, typeMask)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (se: SecurityException) {
            Log.w(TAG, "Blocked starting camera/mic foreground service from background", se)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Unable to enter foreground service for live monitor", e)
            false
        }
    }

    private fun buildNotification(
        useCamera: Boolean,
        useMic: Boolean,
        oicName: String,
        sessionId: String
    ): Notification {
        createNotificationChannel()
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            915,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LiveMonitorService::class.java).apply {
            action = ACTION_STOP_BY_OFFICER
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            916,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeLabel = when {
            useCamera && useMic -> "සජීවී කැමරාව සහ ශ්‍රව්‍ය ක්‍රියාත්මකයි"
            useCamera -> "සජීවී කැමරාව ක්‍රියාත්මකයි"
            else -> "සජීවී ශ්‍රව්‍ය ක්‍රියාත්මකයි"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔴 OIC සජීවී නිරීක්ෂණය • ${oicName.ifBlank { "OIC" }}")
            .setContentText(modeLabel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "නවත්වන්න (Stop)", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "සජීවී OIC කැමරා / ශ්‍රව්‍ය විකාශනය",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "OIC සජීවී කැමරා හා ශ්‍රව්‍ය නිරීක්ෂණය ක්‍රියාත්මක බව පෙන්වයි"
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun attachListeners(sessionId: String, officerUid: String) {
        sessionListener?.remove()
        sessionListener = db.collection("live_monitors").document(sessionId)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                if (snap == null || !snap.exists()) {
                    stopMonitoringAndSelf()
                    return@addSnapshotListener
                }
                val status = snap.getString("status") ?: "ENDED"
                val expiresAtMs = snap.getLong("expiresAtMs") ?: 0L
                val lens = snap.getString("lensFacing") ?: "BACK"
                val now = System.currentTimeMillis()
                if (status == "ENDED" || (expiresAtMs > 0L && now >= expiresAtMs)) {
                    stopMonitoringAndSelf()
                    return@addSnapshotListener
                }
                if (wantCamera && lens != currentLensFacing) {
                    currentLensFacing = lens
                    bindCameraIfNeeded()
                }
            }

        officerListener?.remove()
        officerListener = db.collection("users").document(officerUid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                val onDuty = snap.getBoolean("isOnDuty") ?: false
                val userStatus = snap.getString("status") ?: ""
                if (!onDuty || userStatus != "APPROVED") {
                    markSessionEnded(sessionId)
                    stopMonitoringAndSelf()
                }
            }
    }

    private fun bindCameraIfNeeded() {
        if (!wantCamera || !hasCameraPermission(this)) return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                val selector = if (currentLensFacing == "FRONT") {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val exec = analysisExecutor ?: Executors.newSingleThreadExecutor().also { analysisExecutor = it }
                analysis.setAnalyzer(exec) { image ->
                    try {
                        val now = System.currentTimeMillis()
                        if ((now - lastFrameSentMs) >= FRAME_INTERVAL_MS &&
                            activeSessionId.isNotBlank() &&
                            isVideoUploading.compareAndSet(false, true)
                        ) {
                            lastFrameSentMs = now
                            val jpegTriple = LiveMediaCodecUtils.imageProxyToCompactJpeg(image, quality = 44)
                            if (jpegTriple != null) {
                                val (jpegBytes, w, h) = jpegTriple
                                val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                                val seq = ++videoSeq
                                val slotIdx = (seq % VIDEO_SLOT_COUNT).toInt()
                                val payload = mapOf(
                                    "slotIndex" to slotIdx,
                                    "seq" to seq,
                                    "jpegBase64" to b64,
                                    "width" to w,
                                    "height" to h,
                                    "timestampMs" to now,
                                    "officerUid" to activeOfficerUid,
                                    "oicUid" to activeOicUid,
                                    "oicStationCode" to activeStationCode
                                )
                                db.collection("live_monitors").document(activeSessionId)
                                    .collection("video_slots").document("slot_$slotIdx")
                                    .set(payload)
                                    .addOnCompleteListener { isVideoUploading.set(false) }
                            } else {
                                isVideoUploading.set(false)
                            }
                        }
                    } catch (e: Exception) {
                        isVideoUploading.set(false)
                        Log.w(TAG, "Frame capture/upload error", e)
                    } finally {
                        image.close()
                    }
                }

                provider.bindToLifecycle(this, selector, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Failed binding CameraX in LiveMonitorService", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("MissingPermission")
    private fun startAudioCaptureLoop() {
        audioJob?.cancel()
        if (!wantMic || !hasMicPermission(this)) return
        audioJob = serviceScope.launch {
            val sampleRate = LiveMediaCodecUtils.AUDIO_SAMPLE_RATE
            val chunkSamples = (sampleRate * AUDIO_CHUNK_MS) / 1000 // ~7200 samples for 450ms
            val minBufBytes = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(chunkSamples * 2)

            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufBytes * 2
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord failed to initialize")
                    return@launch
                }
                recorder.startRecording()
                val readBuffer = ShortArray(chunkSamples)
                while (isActive && activeSessionId.isNotBlank()) {
                    var filled = 0
                    while (filled < chunkSamples && isActive) {
                        val r = recorder.read(readBuffer, filled, chunkSamples - filled)
                        if (r <= 0) break
                        filled += r
                    }
                    if (filled > 0 && activeSessionId.isNotBlank()) {
                        val compressed = LiveMediaCodecUtils.encodeImaAdpcm(readBuffer, filled)
                        val b64 = Base64.encodeToString(compressed, Base64.NO_WRAP)
                        val seq = ++audioSeq
                        val slotIdx = (seq % AUDIO_SLOT_COUNT).toInt()
                        val payload = mapOf(
                            "slotIndex" to slotIdx,
                            "seq" to seq,
                            "adpcmBase64" to b64,
                            "sampleRate" to sampleRate,
                            "sampleCount" to filled,
                            "timestampMs" to System.currentTimeMillis(),
                            "officerUid" to activeOfficerUid,
                            "oicUid" to activeOicUid,
                            "oicStationCode" to activeStationCode
                        )
                        db.collection("live_monitors").document(activeSessionId)
                            .collection("audio_chunks").document("slot_$slotIdx")
                            .set(payload)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Audio capture loop terminated", e)
            } finally {
                try {
                    recorder?.stop()
                } catch (_: Exception) {
                }
                try {
                    recorder?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun updateSessionNeedsForeground(sessionId: String, effCam: Boolean, effMic: Boolean) {
        try {
            db.collection("live_monitors").document(sessionId).update(
                mapOf(
                    "status" to "NEEDS_FOREGROUND",
                    "cameraActive" to effCam,
                    "micActive" to effMic,
                    "updatedAtMs" to System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun markSessionEnded(sessionId: String) {
        try {
            db.collection("live_monitors").document(sessionId).update(
                mapOf(
                    "status" to "ENDED",
                    "cameraActive" to false,
                    "micActive" to false,
                    "updatedAtMs" to System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun stopMonitoringAndSelf() {
        _activeStreamingSessionId.value = null
        isForegroundEntered = false
        activeSessionId = ""

        sessionListener?.remove()
        sessionListener = null
        officerListener?.remove()
        officerListener = null

        audioJob?.cancel()
        audioJob = null

        ContextCompat.getMainExecutor(this).execute {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
            cameraProvider = null
            try {
                if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                }
            } catch (_: Exception) {
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        _activeStreamingSessionId.value = null
        sessionListener?.remove()
        officerListener?.remove()
        audioJob?.cancel()
        serviceScope.cancel()
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        analysisExecutor?.shutdownNow()
        analysisExecutor = null
        try {
            if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LiveMonitorService"
        private const val CHANNEL_ID = "police_live_monitor_fgs_channel"
        private const val NOTIFICATION_ID = 4215
        private const val FRAME_INTERVAL_MS = 220L
        private const val VIDEO_SLOT_COUNT = 3
        private const val AUDIO_CHUNK_MS = 450
        private const val AUDIO_SLOT_COUNT = 8

        const val ACTION_START = "com.example.action.START_LIVE_MONITOR"
        const val ACTION_STOP = "com.example.action.STOP_LIVE_MONITOR"
        const val ACTION_STOP_BY_OFFICER = "com.example.action.STOP_LIVE_MONITOR_BY_OFFICER"

        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val EXTRA_OFFICER_UID = "extra_officer_uid"
        private const val EXTRA_OIC_UID = "extra_oic_uid"
        private const val EXTRA_OIC_NAME = "extra_oic_name"
        private const val EXTRA_STATION_CODE = "extra_station_code"
        private const val EXTRA_REQ_CAMERA = "extra_req_camera"
        private const val EXTRA_REQ_AUDIO = "extra_req_audio"
        private const val EXTRA_LENS_FACING = "extra_lens_facing"

        private val _activeStreamingSessionId = MutableStateFlow<String?>(null)
        val activeStreamingSessionId: StateFlow<String?> = _activeStreamingSessionId.asStateFlow()

        fun hasCameraPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun hasMicPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun start(context: Context, session: LiveMonitorSession): Boolean {
            val wantCam = session.requestedCamera && hasCameraPermission(context)
            val wantMic = session.requestedAudio && hasMicPermission(context)
            if (!wantCam && !wantMic) return false

            val intent = Intent(context, LiveMonitorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, session.sessionId)
                putExtra(EXTRA_OFFICER_UID, session.officerUid)
                putExtra(EXTRA_OIC_UID, session.oicUid)
                putExtra(EXTRA_OIC_NAME, session.oicName)
                putExtra(EXTRA_STATION_CODE, session.oicStationCode)
                putExtra(EXTRA_REQ_CAMERA, wantCam)
                putExtra(EXTRA_REQ_AUDIO, wantMic)
                putExtra(EXTRA_LENS_FACING, session.lensFacing)
            }
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Unable to launch LiveMonitorService: ${e.message}")
                NotificationHelper.showLiveMonitorRequestNotification(
                    context = context,
                    sessionId = session.sessionId,
                    oicName = session.oicName,
                    requestedCamera = wantCam,
                    requestedAudio = wantMic
                )
                false
            }
        }

        fun stop(context: Context, sessionId: String? = null) {
            try {
                val intent = Intent(context, LiveMonitorService::class.java).apply {
                    action = ACTION_STOP
                    if (!sessionId.isNullOrBlank()) {
                        putExtra(EXTRA_SESSION_ID, sessionId)
                    }
                }
                context.startService(intent)
            } catch (_: Exception) {
                try {
                    context.stopService(Intent(context, LiveMonitorService::class.java))
                } catch (_: Exception) {
                }
            }
        }
    }
}
