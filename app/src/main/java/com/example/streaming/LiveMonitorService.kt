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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
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
import com.example.data.AppUser
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class LiveMonitorService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var analysisExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var audioJob: Job? = null
    private var commandCollectorJob: Job? = null
    private var sessionListener: ListenerRegistration? = null
    private var officerListener: ListenerRegistration? = null
    private var incomingMonitorListener: ListenerRegistration? = null

    private var streamWakeLock: PowerManager.WakeLock? = null

    private var standbyOfficerUid: String = ""
    private var standbyOfficerRole: String = ""
    private var standbyOfficerStatus: String = ""
    private var standbyIsOnDuty: Boolean = false
    private var standbyCamGranted: Boolean = false
    private var standbyMicGranted: Boolean = false
    private var isForegroundEntered: Boolean = false

    private var activeSessionId: String = ""
    private var activeOfficerUid: String = ""
    private var activeOicUid: String = ""
    private var activeOicName: String = ""
    private var activeStationCode: String = ""
    private var currentLensFacing: String = "BACK"
    private var wantCamera: Boolean = false
    private var wantMic: Boolean = false

    private val sessionGeneration = AtomicLong(0L)
    private val endedByOfficerSessionIds = mutableSetOf<String>()
    private val failedNeedsForegroundSessionIds = mutableSetOf<String>()

    private var videoSeq: Long = 0L
    private var lastFrameSentMs: Long = 0L
    private val isVideoUploading = AtomicBoolean(false)
    private var audioSeq: Long = 0L

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        analysisExecutor = Executors.newSingleThreadExecutor()
        commandCollectorJob = serviceScope.launch(Dispatchers.Main) {
            inProcessCommandFlow.collect { cmd ->
                when (cmd) {
                    is ServiceCommand.ActivateSession -> {
                        if (isForegroundEntered) {
                            handleSessionActivationRequest(cmd.session)
                        }
                    }
                    is ServiceCommand.EndSessionToStandby -> {
                        if (activeSessionId.isNotBlank() &&
                            (cmd.sessionId.isNullOrBlank() || cmd.sessionId == activeSessionId)
                        ) {
                            deactivateSessionToStandby()
                        }
                    }
                    is ServiceCommand.StopByOfficer -> {
                        val sid = cmd.sessionId?.takeIf { it.isNotBlank() } ?: activeSessionId
                        if (sid.isNotBlank()) {
                            endedByOfficerSessionIds.add(sid)
                            markSessionEnded(sid)
                        }
                        if (standbyIsOnDuty && standbyOfficerStatus == "APPROVED") {
                            deactivateSessionToStandby()
                        } else {
                            stopMonitoringAndSelf()
                        }
                    }
                    ServiceCommand.StopCompletely -> {
                        if (activeSessionId.isNotBlank()) {
                            markSessionEnded(activeSessionId)
                        }
                        stopMonitoringAndSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (activeSessionId.isNotBlank()) {
                    markSessionEnded(activeSessionId)
                }
                stopMonitoringAndSelf()
                return START_NOT_STICKY
            }

            ACTION_STOP_BY_OFFICER -> {
                val sid = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty().ifBlank { activeSessionId }
                if (sid.isNotBlank()) {
                    endedByOfficerSessionIds.add(sid)
                    markSessionEnded(sid)
                }
                if (standbyIsOnDuty && standbyOfficerStatus == "APPROVED") {
                    deactivateSessionToStandby()
                    return START_STICKY
                } else {
                    stopMonitoringAndSelf()
                    return START_NOT_STICKY
                }
            }

            ACTION_END_SESSION_TO_STANDBY -> {
                val sid = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
                if (activeSessionId.isNotBlank() && (sid.isBlank() || sid == activeSessionId)) {
                    deactivateSessionToStandby()
                }
                return if (isForegroundEntered && standbyIsOnDuty) START_STICKY else START_NOT_STICKY
            }
        }

        if (intent?.hasExtra(EXTRA_OFFICER_UID) == true && intent.action == ACTION_ENSURE_STANDBY) {
            persistStandbyExtras(intent)
        }
        loadStandbyStateFromPrefs()

        if (intent?.action == ACTION_ENSURE_STANDBY || intent == null) {
            if (intent?.action == ACTION_ENSURE_STANDBY) {
                failedNeedsForegroundSessionIds.clear()
            }
            val hasCam = hasCameraPermission(this)
            val hasMic = hasMicPermission(this)
            val plan = LiveMediaCodecUtils.resolveStandbyForegroundPlan(
                role = standbyOfficerRole,
                status = standbyOfficerStatus,
                isOnDuty = standbyIsOnDuty,
                hasCameraPermission = hasCam,
                hasMicPermission = hasMic
            )
            if (!plan.canEnterForeground || standbyOfficerUid.isBlank()) {
                stopMonitoringAndSelf()
                return START_NOT_STICKY
            }

            val needReenter = !isForegroundEntered ||
                standbyCamGranted != plan.useCameraType ||
                standbyMicGranted != plan.useMicrophoneType
            if (needReenter) {
                val entered = enterForegroundWithMediaTypes(
                    useCamera = plan.useCameraType,
                    useMic = plan.useMicrophoneType,
                    notification = buildStandbyNotification(plan.useCameraType, plan.useMicrophoneType)
                )
                if (!entered) {
                    stopMonitoringAndSelf()
                    return START_NOT_STICKY
                }
                isForegroundEntered = true
                standbyCamGranted = plan.useCameraType
                standbyMicGranted = plan.useMicrophoneType
                _isServiceRunning.value = true
                _standbyTypeMask.value = computeMediaMask(plan.useCameraType, plan.useMicrophoneType)
            }

            attachStandbyListeners(standbyOfficerUid)
            return START_STICKY
        }

        // ACTION_START for an explicit session request
        failedNeedsForegroundSessionIds.clear()
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val officerUid = intent.getStringExtra(EXTRA_OFFICER_UID).orEmpty()
            .ifBlank { standbyOfficerUid }
            .ifBlank { try { Firebase.auth.currentUser?.uid.orEmpty() } catch (_: Exception) { "" } }
        val oicUid = intent.getStringExtra(EXTRA_OIC_UID).orEmpty()
        val oicName = intent.getStringExtra(EXTRA_OIC_NAME).orEmpty()
        val stationCode = intent.getStringExtra(EXTRA_STATION_CODE).orEmpty()
        val reqCam = intent.getBooleanExtra(EXTRA_REQ_CAMERA, false)
        val reqMic = intent.getBooleanExtra(EXTRA_REQ_AUDIO, false)
        val lens = intent.getStringExtra(EXTRA_LENS_FACING).orEmpty().ifBlank { "BACK" }

        val hasCamPerm = hasCameraPermission(this)
        val hasMicPerm = hasMicPermission(this)
        val effCam = reqCam && hasCamPerm
        val effMic = reqMic && hasMicPerm

        if (sessionId.isBlank() || officerUid.isBlank() || oicUid.isBlank() || (!effCam && !effMic)) {
            if (!isForegroundEntered) stopMonitoringAndSelf()
            return if (isForegroundEntered) START_STICKY else START_NOT_STICKY
        }

        val session = LiveMonitorSession(
            sessionId = sessionId,
            oicStationCode = stationCode,
            oicUid = oicUid,
            oicName = oicName,
            officerUid = officerUid,
            status = "REQUESTED",
            requestedCamera = effCam,
            requestedAudio = effMic,
            lensFacing = lens
        )

        if (!isForegroundEntered) {
            val useCamType = hasCamPerm || effCam
            val useMicType = hasMicPerm || effMic
            val entered = enterForegroundWithMediaTypes(
                useCamera = useCamType,
                useMic = useMicType,
                notification = buildActiveNotification(effCam, effMic, oicName, sessionId)
            )
            if (!entered) {
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
            standbyOfficerUid = officerUid
            standbyCamGranted = useCamType
            standbyMicGranted = useMicType
            _isServiceRunning.value = true
            _standbyTypeMask.value = computeMediaMask(useCamType, useMicType)
            attachStandbyListeners(officerUid)
        }

        handleSessionActivationRequest(session)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        loadStandbyStateFromPrefs()
        if (!standbyIsOnDuty || standbyOfficerStatus != "APPROVED") {
            stopMonitoringAndSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun handleSessionActivationRequest(session: LiveMonitorSession) {
        if (session.sessionId.isBlank() || session.sessionId in endedByOfficerSessionIds) return
        val hasCamPerm = hasCameraPermission(this)
        val hasMicPerm = hasMicPermission(this)
        val effCam = session.requestedCamera && hasCamPerm
        val effMic = session.requestedAudio && hasMicPerm

        if (!effCam && !effMic) return

        // Check whether current FGS already holds the requested camera/microphone FGS types
        if ((effCam && !standbyCamGranted) || (effMic && !standbyMicGranted)) {
            val reentered = enterForegroundWithMediaTypes(
                useCamera = hasCamPerm || standbyCamGranted,
                useMic = hasMicPerm || standbyMicGranted,
                notification = buildActiveNotification(effCam, effMic, session.oicName, session.sessionId)
            )
            if (!reentered) {
                updateSessionNeedsForeground(session.sessionId, effCam, effMic)
                NotificationHelper.showLiveMonitorRequestNotification(
                    context = this,
                    sessionId = session.sessionId,
                    oicName = session.oicName,
                    requestedCamera = effCam,
                    requestedAudio = effMic
                )
                return
            }
            standbyCamGranted = hasCamPerm || standbyCamGranted
            standbyMicGranted = hasMicPerm || standbyMicGranted
            _standbyTypeMask.value = computeMediaMask(standbyCamGranted, standbyMicGranted)
        }

        if (activeSessionId == session.sessionId) {
            if (effCam && currentLensFacing != session.lensFacing) {
                currentLensFacing = session.lensFacing.ifBlank { "BACK" }
                bindCameraOnMain(sessionGeneration.get())
            }
            return
        }

        mainHandler.post {
            activateSessionOnMain(session, effCam, effMic)
        }
    }

    private fun activateSessionOnMain(
        session: LiveMonitorSession,
        effCam: Boolean,
        effMic: Boolean
    ) {
        if (!isForegroundEntered || session.sessionId in endedByOfficerSessionIds) return

        val gen = sessionGeneration.incrementAndGet()
        audioJob?.cancel()
        audioJob = null
        isVideoUploading.set(false)

        activeSessionId = session.sessionId
        activeOfficerUid = session.officerUid.ifBlank { standbyOfficerUid }
        activeOicUid = session.oicUid
        activeOicName = session.oicName
        activeStationCode = session.oicStationCode
        wantCamera = effCam
        wantMic = effMic
        currentLensFacing = session.lensFacing.ifBlank { "BACK" }
        videoSeq = 0L
        audioSeq = 0L
        _activeStreamingSessionId.value = session.sessionId

        acquireStreamWakeLock()
        updateServiceNotification(buildActiveNotification(effCam, effMic, activeOicName, session.sessionId))

        try {
            if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lifecycle transition warning", e)
        }

        db.collection("live_monitors").document(session.sessionId).update(
            mapOf(
                "status" to "ACTIVE",
                "cameraActive" to effCam,
                "micActive" to effMic,
                "updatedAtMs" to System.currentTimeMillis()
            )
        )

        attachActiveSessionListener(session.sessionId)

        if (effCam) {
            bindCameraOnMain(gen)
        } else {
            unbindCameraOnMain()
        }

        if (effMic) {
            startAudioCaptureLoop(gen, session.sessionId)
        }
    }

    private fun deactivateSessionToStandby() {
        mainHandler.post {
            val prevGen = sessionGeneration.incrementAndGet()
            Log.d(TAG, "Returning LiveMonitorService to standby (gen=$prevGen)")
            activeSessionId = ""
            wantCamera = false
            wantMic = false
            isVideoUploading.set(false)
            _activeStreamingSessionId.value = null

            sessionListener?.remove()
            sessionListener = null

            audioJob?.cancel()
            audioJob = null

            unbindCameraOnMain()
            releaseStreamWakeLock()

            try {
                if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    lifecycleRegistry.currentState = Lifecycle.State.CREATED
                }
            } catch (_: Exception) {
            }

            if (isForegroundEntered && standbyIsOnDuty && standbyOfficerStatus == "APPROVED") {
                updateServiceNotification(buildStandbyNotification(standbyCamGranted, standbyMicGranted))
            } else {
                stopMonitoringAndSelf()
            }
        }
    }

    private fun attachStandbyListeners(officerUid: String) {
        if (officerUid.isBlank()) return

        if (officerListener == null) {
            officerListener = db.collection("users").document(officerUid)
                .addSnapshotListener { snap, err ->
                    if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                    val onDuty = snap.getBoolean("isOnDuty") ?: false
                    val userStatus = snap.getString("status") ?: ""
                    val userRole = snap.getString("role") ?: "OFFICER"

                    standbyIsOnDuty = onDuty
                    standbyOfficerStatus = userStatus
                    standbyOfficerRole = userRole

                    getSharedPreferences("police_prefs", Context.MODE_PRIVATE).edit()
                        .putBoolean("cached_is_on_duty", onDuty)
                        .putString("cached_status", userStatus)
                        .putString("cached_role", userRole)
                        .apply()

                    if (!onDuty || userStatus != "APPROVED" || userRole != "OFFICER") {
                        if (activeSessionId.isNotBlank()) {
                            markSessionEnded(activeSessionId)
                        }
                        stopMonitoringAndSelf()
                    }
                }
        }

        if (incomingMonitorListener == null) {
            incomingMonitorListener = db.collection("live_monitors")
                .whereEqualTo("officerUid", officerUid)
                .addSnapshotListener { snap, err ->
                    if (err != null || snap == null) return@addSnapshotListener
                    if (!standbyIsOnDuty || standbyOfficerStatus != "APPROVED") return@addSnapshotListener

                    val now = System.currentTimeMillis()
                    val candidate = snap.documents.mapNotNull { doc ->
                        doc.toObject(LiveMonitorSession::class.java)?.copy(sessionId = doc.id)
                    }
                        .filter { sess ->
                            sess.sessionId !in endedByOfficerSessionIds &&
                                sess.sessionId !in failedNeedsForegroundSessionIds &&
                                (sess.status == "REQUESTED" || sess.status == "NEEDS_FOREGROUND") &&
                                (sess.expiresAtMs == 0L || sess.expiresAtMs > now)
                        }
                        .maxByOrNull { it.createdAtMs }
                        ?: return@addSnapshotListener

                    if (candidate.sessionId != activeSessionId) {
                        handleSessionActivationRequest(candidate)
                    }
                }
        }
    }

    private fun attachActiveSessionListener(sessionId: String) {
        sessionListener?.remove()
        sessionListener = db.collection("live_monitors").document(sessionId)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                if (activeSessionId != sessionId) return@addSnapshotListener

                if (snap == null || !snap.exists()) {
                    if (standbyIsOnDuty && standbyOfficerStatus == "APPROVED") {
                        deactivateSessionToStandby()
                    } else {
                        stopMonitoringAndSelf()
                    }
                    return@addSnapshotListener
                }

                val status = snap.getString("status") ?: "ENDED"
                val expiresAtMs = snap.getLong("expiresAtMs") ?: 0L
                val lens = snap.getString("lensFacing") ?: "BACK"
                val now = System.currentTimeMillis()

                if (status == "ENDED" || (expiresAtMs > 0L && now >= expiresAtMs)) {
                    if (standbyIsOnDuty && standbyOfficerStatus == "APPROVED") {
                        deactivateSessionToStandby()
                    } else {
                        stopMonitoringAndSelf()
                    }
                    return@addSnapshotListener
                }

                if (wantCamera && lens != currentLensFacing) {
                    currentLensFacing = lens
                    bindCameraOnMain(sessionGeneration.get())
                }
            }
    }

    private fun bindCameraOnMain(expectedGen: Long) {
        if (!wantCamera || !hasCameraPermission(this)) return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            if (sessionGeneration.get() != expectedGen || !wantCamera || activeSessionId.isBlank()) {
                return@addListener
            }
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
                        val sid = activeSessionId
                        if (sessionGeneration.get() == expectedGen &&
                            sid.isNotBlank() &&
                            (now - lastFrameSentMs) >= FRAME_INTERVAL_MS &&
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
                                db.collection("live_monitors").document(sid)
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
                val sid = activeSessionId
                if (sid.isNotBlank()) {
                    if (wantMic && hasMicPermission(this)) {
                        wantCamera = false
                        db.collection("live_monitors").document(sid).update(
                            mapOf(
                                "cameraActive" to false,
                                "updatedAtMs" to System.currentTimeMillis()
                            )
                        )
                    } else {
                        failedNeedsForegroundSessionIds.add(sid)
                        updateSessionNeedsForeground(sid, effCam = false, effMic = false)
                        NotificationHelper.showLiveMonitorRequestNotification(
                            context = this,
                            sessionId = sid,
                            oicName = activeOicName,
                            requestedCamera = true,
                            requestedAudio = wantMic
                        )
                        deactivateSessionToStandby()
                    }
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun unbindCameraOnMain() {
        val runUnbind = Runnable {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runUnbind.run()
        } else {
            mainHandler.post(runUnbind)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioCaptureLoop(expectedGen: Long, sessionId: String) {
        audioJob?.cancel()
        if (!wantMic || !hasMicPermission(this)) return
        audioJob = serviceScope.launch {
            val sampleRate = LiveMediaCodecUtils.AUDIO_SAMPLE_RATE
            val chunkSamples = (sampleRate * AUDIO_CHUNK_MS) / 1000
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
                    db.collection("live_monitors").document(sessionId).update(
                        mapOf("micActive" to false, "updatedAtMs" to System.currentTimeMillis())
                    )
                    return@launch
                }
                recorder.startRecording()
                val readBuffer = ShortArray(chunkSamples)
                while (isActive && sessionGeneration.get() == expectedGen && activeSessionId == sessionId) {
                    var filled = 0
                    while (filled < chunkSamples && isActive && sessionGeneration.get() == expectedGen) {
                        val r = recorder.read(readBuffer, filled, chunkSamples - filled)
                        if (r <= 0) break
                        filled += r
                    }
                    if (filled > 0 && sessionGeneration.get() == expectedGen && activeSessionId == sessionId) {
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
                        db.collection("live_monitors").document(sessionId)
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

    private fun enterForegroundWithMediaTypes(
        useCamera: Boolean,
        useMic: Boolean,
        notification: Notification
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val typeMask = computeMediaMask(useCamera, useMic)
                if (typeMask == 0) return false
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

    private fun computeMediaMask(useCamera: Boolean, useMic: Boolean): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        var mask = 0
        if (useCamera) mask = mask or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (useMic) mask = mask or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        return mask
    }

    private fun updateServiceNotification(notification: Notification) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }

    private fun buildStandbyNotification(camReady: Boolean, micReady: Boolean): Notification {
        createNotificationChannels()
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            914,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val mediaSummary = when {
            camReady && micReady -> "කැමරා හා මයික්‍රොෆෝන පසුබිම් නිරීක්ෂණයට සූදානම්"
            camReady -> "කැමරා පසුබිම් නිරීක්ෂණයට සූදානම්"
            else -> "මයික්‍රොෆෝන පසුබිම් නිරීක්ෂණයට සූදානම්"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID_STANDBY)
            .setContentTitle("All In One Police • ON DUTY සූදානම")
            .setContentText(mediaSummary)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openPendingIntent)
            .build()
    }

    private fun buildActiveNotification(
        useCamera: Boolean,
        useMic: Boolean,
        oicName: String,
        sessionId: String
    ): Notification {
        createNotificationChannels()
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
            useCamera && useMic -> "සජීවී කැමරාව සහ ශ්‍රව්‍ය විකාශනය වේ"
            useCamera -> "සජීවී කැමරාව විකාශනය වේ"
            else -> "සජීවී ශ්‍රව්‍ය විකාශනය වේ"
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

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID_STANDBY) == null) {
                val standbyCh = NotificationChannel(
                    CHANNEL_ID_STANDBY,
                    "ON DUTY සජීවී නිරීක්ෂණ සූදානම",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "නිලධාරියා ON DUTY සිටින විට OIC නිරීක්ෂණය පසුබිමෙන් ක්‍රියාත්මක කිරීමට සූදානමින් තබයි"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(standbyCh)
            }
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val activeCh = NotificationChannel(
                    CHANNEL_ID,
                    "සජීවී OIC කැමරා / ශ්‍රව්‍ය විකාශනය",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "OIC සජීවී කැමරා හා ශ්‍රව්‍ය නිරීක්ෂණය ක්‍රියාත්මක බව පෙන්වයි"
                }
                nm.createNotificationChannel(activeCh)
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireStreamWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (streamWakeLock == null) {
                streamWakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AllInOnePolice::LiveMonitorStreamWakeLock"
                ).apply { setReferenceCounted(false) }
            }
            streamWakeLock?.acquire(25 * 60 * 1000L)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire live stream wake lock", e)
        }
    }

    private fun releaseStreamWakeLock() {
        try {
            if (streamWakeLock?.isHeld == true) {
                streamWakeLock?.release()
            }
        } catch (_: Exception) {
        }
        streamWakeLock = null
    }

    private fun persistStandbyExtras(intent: Intent) {
        val uid = intent.getStringExtra(EXTRA_OFFICER_UID).orEmpty()
        if (uid.isBlank()) return
        getSharedPreferences("police_prefs", Context.MODE_PRIVATE).edit()
            .putString("cached_uid", uid)
            .putBoolean("cached_is_on_duty", intent.getBooleanExtra(EXTRA_IS_ON_DUTY, false))
            .putString("cached_status", intent.getStringExtra(EXTRA_USER_STATUS).orEmpty())
            .putString("cached_role", intent.getStringExtra(EXTRA_USER_ROLE).orEmpty())
            .commit()
    }

    private fun loadStandbyStateFromPrefs() {
        val prefs = getSharedPreferences("police_prefs", Context.MODE_PRIVATE)
        val authUid = try { Firebase.auth.currentUser?.uid } catch (_: Exception) { null }
        standbyOfficerUid = authUid?.takeIf { it.isNotBlank() } ?: prefs.getString("cached_uid", "").orEmpty()
        standbyIsOnDuty = prefs.getBoolean("cached_is_on_duty", false)
        standbyOfficerStatus = prefs.getString("cached_status", "").orEmpty()
        standbyOfficerRole = prefs.getString("cached_role", "").orEmpty()
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
        sessionGeneration.incrementAndGet()
        _activeStreamingSessionId.value = null
        _isServiceRunning.value = false
        _standbyTypeMask.value = 0
        isForegroundEntered = false
        standbyCamGranted = false
        standbyMicGranted = false
        activeSessionId = ""
        wantCamera = false
        wantMic = false
        isVideoUploading.set(false)

        sessionListener?.remove()
        sessionListener = null
        officerListener?.remove()
        officerListener = null
        incomingMonitorListener?.remove()
        incomingMonitorListener = null

        audioJob?.cancel()
        audioJob = null
        releaseStreamWakeLock()

        mainHandler.post {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
            cameraProvider = null
            try {
                if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                    lifecycleRegistry.currentState = Lifecycle.State.CREATED
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
        sessionGeneration.incrementAndGet()
        _activeStreamingSessionId.value = null
        _isServiceRunning.value = false
        _standbyTypeMask.value = 0
        isForegroundEntered = false
        sessionListener?.remove()
        officerListener?.remove()
        incomingMonitorListener?.remove()
        audioJob?.cancel()
        commandCollectorJob?.cancel()
        serviceScope.cancel()
        releaseStreamWakeLock()
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        analysisExecutor?.shutdownNow()
        analysisExecutor = null
        try {
            if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            }
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private sealed interface ServiceCommand {
        data class ActivateSession(val session: LiveMonitorSession) : ServiceCommand
        data class EndSessionToStandby(val sessionId: String?) : ServiceCommand
        data class StopByOfficer(val sessionId: String?) : ServiceCommand
        data object StopCompletely : ServiceCommand
    }

    companion object {
        private const val TAG = "LiveMonitorService"
        private const val CHANNEL_ID_STANDBY = "police_live_monitor_standby_channel"
        private const val CHANNEL_ID = "police_live_monitor_fgs_channel"
        private const val NOTIFICATION_ID = 4215
        private const val FRAME_INTERVAL_MS = 220L
        private const val VIDEO_SLOT_COUNT = 3
        private const val AUDIO_CHUNK_MS = 450
        private const val AUDIO_SLOT_COUNT = 8

        const val ACTION_ENSURE_STANDBY = "com.example.action.ENSURE_LIVE_MONITOR_STANDBY"
        const val ACTION_START = "com.example.action.START_LIVE_MONITOR"
        const val ACTION_END_SESSION_TO_STANDBY = "com.example.action.END_LIVE_MONITOR_SESSION"
        const val ACTION_STOP = "com.example.action.STOP_LIVE_MONITOR"
        const val ACTION_STOP_BY_OFFICER = "com.example.action.STOP_LIVE_MONITOR_BY_OFFICER"

        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val EXTRA_OFFICER_UID = "extra_officer_uid"
        private const val EXTRA_USER_ROLE = "extra_user_role"
        private const val EXTRA_USER_STATUS = "extra_user_status"
        private const val EXTRA_IS_ON_DUTY = "extra_is_on_duty"
        private const val EXTRA_OIC_UID = "extra_oic_uid"
        private const val EXTRA_OIC_NAME = "extra_oic_name"
        private const val EXTRA_STATION_CODE = "extra_station_code"
        private const val EXTRA_REQ_CAMERA = "extra_req_camera"
        private const val EXTRA_REQ_AUDIO = "extra_req_audio"
        private const val EXTRA_LENS_FACING = "extra_lens_facing"

        private val _activeStreamingSessionId = MutableStateFlow<String?>(null)
        val activeStreamingSessionId: StateFlow<String?> = _activeStreamingSessionId.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _standbyTypeMask = MutableStateFlow(0)
        private val inProcessCommandFlow = MutableSharedFlow<ServiceCommand>(extraBufferCapacity = 16)

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

        /**
         * Starts/keeps the LiveMonitorService in silent foreground Standby whenever an approved
         * officer is ON DUTY and has Camera and/or Microphone permission. Calling this while the
         * app is in the foreground pre-authorizes `camera` / `microphone` FGS types on Android 14+,
         * allowing OIC to activate camera/mic even after the officer backgrounds or locks the app.
         */
        fun ensureStandbyIfOnDuty(context: Context, user: AppUser?) {
            if (user == null) {
                stopCompletely(context)
                return
            }
            val hasCam = hasCameraPermission(context)
            val hasMic = hasMicPermission(context)
            val plan = LiveMediaCodecUtils.resolveStandbyForegroundPlan(
                role = user.role,
                status = user.status,
                isOnDuty = user.isOnDuty,
                hasCameraPermission = hasCam,
                hasMicPermission = hasMic
            )
            if (!plan.canEnterForeground || user.uid.isBlank()) {
                if (_isServiceRunning.value) {
                    stopCompletely(context)
                }
                return
            }

            val expectedMask = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                var m = 0
                if (plan.useCameraType) m = m or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                if (plan.useMicrophoneType) m = m or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                m
            } else {
                1
            }

            if (_isServiceRunning.value && _standbyTypeMask.value == expectedMask) {
                return
            }

            val intent = Intent(context, LiveMonitorService::class.java).apply {
                action = ACTION_ENSURE_STANDBY
                putExtra(EXTRA_OFFICER_UID, user.uid)
                putExtra(EXTRA_USER_ROLE, user.role)
                putExtra(EXTRA_USER_STATUS, user.status)
                putExtra(EXTRA_IS_ON_DUTY, user.isOnDuty)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to enter on-duty live monitor standby: ${e.message}")
            }
        }

        fun start(context: Context, session: LiveMonitorSession): Boolean {
            val wantCam = session.requestedCamera && hasCameraPermission(context)
            val wantMic = session.requestedAudio && hasMicPermission(context)
            if (!wantCam && !wantMic) return false

            if (_isServiceRunning.value) {
                if (inProcessCommandFlow.tryEmit(ServiceCommand.ActivateSession(session))) {
                    return true
                }
            }

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
                if (_isServiceRunning.value) {
                    context.startService(intent)
                } else {
                    ContextCompat.startForegroundService(context, intent)
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "Unable to launch LiveMonitorService: ${e.message}")
                markNeedsForegroundAndNotify(context, session, wantCam, wantMic)
                false
            }
        }

        private fun markNeedsForegroundAndNotify(
            context: Context,
            session: LiveMonitorSession,
            wantCam: Boolean,
            wantMic: Boolean
        ) {
            try {
                val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
                db.collection("live_monitors").document(session.sessionId).update(
                    mapOf(
                        "status" to "NEEDS_FOREGROUND",
                        "cameraActive" to false,
                        "micActive" to false,
                        "updatedAtMs" to System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) {
            }
            NotificationHelper.showLiveMonitorRequestNotification(
                context = context,
                sessionId = session.sessionId,
                oicName = session.oicName,
                requestedCamera = wantCam,
                requestedAudio = wantMic
            )
        }

        fun endSessionToStandby(context: Context, sessionId: String? = null) {
            if (!_isServiceRunning.value) return
            if (inProcessCommandFlow.tryEmit(ServiceCommand.EndSessionToStandby(sessionId))) {
                return
            }
            try {
                val intent = Intent(context, LiveMonitorService::class.java).apply {
                    action = ACTION_END_SESSION_TO_STANDBY
                    if (!sessionId.isNullOrBlank()) {
                        putExtra(EXTRA_SESSION_ID, sessionId)
                    }
                }
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun stopActiveSessionByOfficer(context: Context, sessionId: String? = null) {
            if (_isServiceRunning.value) {
                if (inProcessCommandFlow.tryEmit(ServiceCommand.StopByOfficer(sessionId))) {
                    return
                }
            }
            try {
                val intent = Intent(context, LiveMonitorService::class.java).apply {
                    action = ACTION_STOP_BY_OFFICER
                    if (!sessionId.isNullOrBlank()) {
                        putExtra(EXTRA_SESSION_ID, sessionId)
                    }
                }
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context, sessionId: String? = null) {
            stopActiveSessionByOfficer(context, sessionId)
        }

        fun stopCompletely(context: Context) {
            if (_isServiceRunning.value) {
                inProcessCommandFlow.tryEmit(ServiceCommand.StopCompletely)
            }
            try {
                context.stopService(Intent(context, LiveMonitorService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
