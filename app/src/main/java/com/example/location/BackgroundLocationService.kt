package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppUser
import com.example.data.LiveMonitorSession
import com.example.streaming.LiveMonitorService
import com.example.util.DateTimeUtils
import com.example.util.GeoRouteUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackgroundLocationService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var gpsListener: LocationListener? = null
    private var userDocListener: ListenerRegistration? = null
    private var liveMonitorListener: ListenerRegistration? = null
    private val handledLiveMonitorRequests = mutableSetOf<String>()
    private var wakeLock: PowerManager.WakeLock? = null
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private var isWatchdogScheduled = false

    private var cachedUid: String = ""
    private var cachedName: String = ""
    private var cachedStation: String = ""
    private var cachedIsOnDuty: Boolean = false
    private var cachedStatus: String = ""
    private var cachedRole: String = ""
    private var stealthHiddenOnMap: Boolean = false
    private var stealthPauseLogging: Boolean = false

    private var lastSavedLat: Double = 0.0
    private var lastSavedLng: Double = 0.0
    private var lastSavedTimeMs: Long = 0L
    private var lastHeartbeatTimeMs: Long = 0L
    private var lastReceivedFixMs: Long = 0L
    private var pendingSpikeLat: Double = 0.0
    private var pendingSpikeLng: Double = 0.0
    private var pendingSpikeTimeMs: Long = 0L

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!isWatchdogScheduled) return
            syncStateFromPrefs()
            if (!hasForegroundLocationPermission(this@BackgroundLocationService) ||
                cachedUid.isBlank() ||
                cachedStatus != "APPROVED" ||
                !cachedIsOnDuty ||
                stealthPauseLogging ||
                stealthHiddenOnMap
            ) {
                stopTrackingAndSelf()
                return
            }

            acquireOrRenewWakeLock()
            val nowMs = System.currentTimeMillis()
            if ((nowMs - lastReceivedFixMs) >= 30_000L) {
                pollSingleFixForWatchdog()
            }
            watchdogHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Persist authoritative extras if passed with intent before reading prefs
        if (intent != null && intent.hasExtra(EXTRA_UID)) {
            persistIntentExtras(intent)
        }

        // CRITICAL on Android 8+/14+: Always call startForeground() first if foreground location permission exists
        if (hasForegroundLocationPermission(this)) {
            startForegroundSafely("රාජකාරි ගමන් මග පසුබිමින් සටහන් වෙමින් පවතී")
        }

        if (intent?.action == ACTION_STOP) {
            stopTrackingAndSelf()
            return START_NOT_STICKY
        }

        if (!hasForegroundLocationPermission(this)) {
            stopTrackingAndSelf()
            return START_NOT_STICKY
        }

        syncStateFromPrefs()
        if (cachedUid.isBlank() || cachedStatus != "APPROVED" || !cachedIsOnDuty || stealthPauseLogging || stealthHiddenOnMap) {
            stopTrackingAndSelf()
            return START_NOT_STICKY
        }

        cancelRestartAlarm(this)
        acquireOrRenewWakeLock()
        attachUserListenerIfNeeded()
        startLocationUpdates()
        startWatchdog()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        syncStateFromPrefs()
        if (hasForegroundLocationPermission(this) &&
            cachedUid.isNotBlank() &&
            cachedStatus == "APPROVED" &&
            cachedIsOnDuty &&
            !stealthPauseLogging &&
            !stealthHiddenOnMap
        ) {
            scheduleRestartAlarm(this)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun persistIntentExtras(intent: Intent) {
        val uid = intent.getStringExtra(EXTRA_UID).orEmpty()
        if (uid.isBlank()) return
        val prefs = getSharedPreferences("police_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("cached_uid", uid)
            .putString("cached_display_name", intent.getStringExtra(EXTRA_NAME).orEmpty())
            .putString("cached_oic_station_code", intent.getStringExtra(EXTRA_STATION).orEmpty().trim().uppercase())
            .putBoolean("cached_is_on_duty", intent.getBooleanExtra(EXTRA_IS_ON_DUTY, false))
            .putString("cached_status", intent.getStringExtra(EXTRA_STATUS).orEmpty())
            .putString("cached_role", intent.getStringExtra(EXTRA_ROLE).orEmpty())
            .putBoolean("cached_hidden_on_map", intent.getBooleanExtra(EXTRA_HIDDEN_ON_MAP, false))
            .putBoolean("cached_pause_location_logging", intent.getBooleanExtra(EXTRA_PAUSE_LOGGING, false))
            .commit()
    }

    private fun buildNotification(statusText: String): Notification {
        createNotificationChannel()
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            901,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("All In One Police • සජීවී GPS")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startForegroundSafely(statusText: String) {
        val notification = buildNotification(statusText)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error entering foreground service state", e)
        }
    }

    private fun updateOngoingNotification(statusText: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(statusText))
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Police Patrol Background GPS",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Records patrol breadcrumbs continuously while officer is on duty"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireOrRenewWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AllInOnePolice::PatrolLocationWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(30 * 60 * 1000L)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire partial wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun startWatchdog() {
        if (!isWatchdogScheduled) {
            isWatchdogScheduled = true
            watchdogHandler.removeCallbacks(watchdogRunnable)
            watchdogHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
        }
    }

    private fun stopWatchdog() {
        isWatchdogScheduled = false
        watchdogHandler.removeCallbacks(watchdogRunnable)
    }

    @SuppressLint("MissingPermission")
    private fun pollSingleFixForWatchdog() {
        if (!hasForegroundLocationPermission(this)) return
        try {
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        handleBackgroundLocation(loc)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Watchdog single-fix poll failed", e)
        }
    }

    private fun syncStateFromPrefs() {
        val prefs = getSharedPreferences("police_prefs", Context.MODE_PRIVATE)
        val authUid = try { Firebase.auth.currentUser?.uid } catch (_: Exception) { null }
        cachedUid = authUid?.takeIf { it.isNotBlank() } ?: prefs.getString("cached_uid", "").orEmpty()
        cachedName = prefs.getString("cached_display_name", "").orEmpty()
        cachedStation = prefs.getString("cached_oic_station_code", "").orEmpty().trim().uppercase()
        cachedIsOnDuty = prefs.getBoolean("cached_is_on_duty", false)
        cachedStatus = prefs.getString("cached_status", "").orEmpty()
        cachedRole = prefs.getString("cached_role", "").orEmpty()
        stealthHiddenOnMap = prefs.getBoolean("cached_hidden_on_map", false)
        stealthPauseLogging = prefs.getBoolean("cached_pause_location_logging", false)
    }

    private fun attachUserListenerIfNeeded() {
        val uid = cachedUid
        if (uid.isBlank() || userDocListener != null) return
        userDocListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                val status = snap.getString("status") ?: cachedStatus
                val onDuty = snap.getBoolean("isOnDuty") ?: cachedIsOnDuty
                val station = (snap.getString("oicStationCode") ?: cachedStation).trim().uppercase()
                val name = snap.getString("displayName") ?: cachedName
                val role = snap.getString("role") ?: cachedRole
                val hiddenMap = snap.getBoolean("hiddenOnMap") ?: stealthHiddenOnMap
                val pauseLog = snap.getBoolean("pauseLocationLogging") ?: stealthPauseLogging

                cachedStatus = status
                cachedIsOnDuty = onDuty
                cachedStation = station
                cachedName = name
                cachedRole = role
                stealthHiddenOnMap = hiddenMap
                stealthPauseLogging = pauseLog

                getSharedPreferences("police_prefs", Context.MODE_PRIVATE).edit()
                    .putString("cached_status", status)
                    .putBoolean("cached_is_on_duty", onDuty)
                    .putString("cached_oic_station_code", station)
                    .putString("cached_display_name", name)
                    .putString("cached_role", role)
                    .putBoolean("cached_hidden_on_map", hiddenMap)
                    .putBoolean("cached_pause_location_logging", pauseLog)
                    .apply()

                if (status != "APPROVED" || !onDuty || pauseLog || hiddenMap) {
                    stopTrackingAndSelf()
                }
            }

        if (liveMonitorListener == null) {
            liveMonitorListener = db.collection("live_monitors")
                .whereEqualTo("officerUid", uid)
                .addSnapshotListener { snap, err ->
                    if (err != null || snap == null) return@addSnapshotListener
                    if (!cachedIsOnDuty || cachedStatus != "APPROVED") return@addSnapshotListener
                    val now = System.currentTimeMillis()
                    val req = snap.documents.mapNotNull { doc ->
                        doc.toObject(LiveMonitorSession::class.java)?.copy(sessionId = doc.id)
                    }
                        .filter { it.status == "REQUESTED" && (it.expiresAtMs == 0L || it.expiresAtMs > now) }
                        .maxByOrNull { it.createdAtMs }
                        ?: return@addSnapshotListener
                    if (handledLiveMonitorRequests.add(req.sessionId)) {
                        LiveMonitorService.start(this, req)
                    }
                }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasForegroundLocationPermission(this)) {
            stopTrackingAndSelf()
            return
        }

        if (locationCallback == null) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
                .setMinUpdateIntervalMillis(2500L)
                .setMinUpdateDistanceMeters(5.0f)
                .setWaitForAccurateLocation(true)
                .build()

            val cb = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val best = result.locations
                        .filter { (it.latitude != 0.0 || it.longitude != 0.0) && it.accuracy in 0.1f..40f }
                        .minByOrNull { it.accuracy }
                        ?: result.lastLocation
                        ?: return
                    handleBackgroundLocation(best)
                }
            }
            try {
                fusedClient.requestLocationUpdates(request, cb, Looper.getMainLooper())
                locationCallback = cb
            } catch (e: SecurityException) {
                Log.w(TAG, "Missing location permission in background service", e)
            }
        }

        if (gpsListener == null &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                val listener = LocationListener { loc -> handleBackgroundLocation(loc) }
                try {
                    lm.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        4000L,
                        4.0f,
                        listener,
                        Looper.getMainLooper()
                    )
                    gpsListener = listener
                } catch (e: Exception) {
                    Log.w(TAG, "Hardware GPS listener error in background service", e)
                }
            }
        }
    }

    private fun handleBackgroundLocation(location: Location) {
        if (location.latitude == 0.0 && location.longitude == 0.0) return
        if (!location.hasAccuracy() || location.accuracy <= 0f) return
        val provider = location.provider?.lowercase(Locale.US).orEmpty()
        if (provider == LocationManager.NETWORK_PROVIDER || provider == LocationManager.PASSIVE_PROVIDER) return

        // Reject stale cached fixes (> 20s old)
        val fixAgeMs = ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L)
        if (fixAgeMs > 20_000L) return

        // Only true high-accuracy GPS fixes allowed for patrol trail logging
        if (location.accuracy > 32f) return

        lastReceivedFixMs = System.currentTimeMillis()
        acquireOrRenewWakeLock()

        syncStateFromPrefs()
        if (cachedUid.isBlank() || cachedStatus != "APPROVED" || !cachedIsOnDuty) {
            stopTrackingAndSelf()
            return
        }
        if (stealthPauseLogging || stealthHiddenOnMap) {
            stopTrackingAndSelf()
            return
        }

        val nowMs = System.currentTimeMillis()
        val movedMeters = if (lastSavedTimeMs == 0L) Double.MAX_VALUE
        else GeoRouteUtils.distanceMeters(lastSavedLat, lastSavedLng, location.latitude, location.longitude)

        // Two-fix confirmation against sudden GPS / cell teleport spikes (> 65m & > 36 m/s)
        if (lastSavedTimeMs > 0L) {
            val dtSec = ((nowMs - lastSavedTimeMs) / 1000.0).coerceAtLeast(1.0)
            val speedMps = movedMeters / dtSec
            if (movedMeters > 65.0 && speedMps > 36.0) {
                val confirmedNearPending = pendingSpikeTimeMs > 0L &&
                    (nowMs - pendingSpikeTimeMs) <= 20_000L &&
                    GeoRouteUtils.distanceMeters(pendingSpikeLat, pendingSpikeLng, location.latitude, location.longitude) <= 35.0
                if (!confirmedNearPending) {
                    pendingSpikeLat = location.latitude
                    pendingSpikeLng = location.longitude
                    pendingSpikeTimeMs = nowMs
                    return
                }
            }
            pendingSpikeTimeMs = 0L
        }

        val requiredMoveMeters = maxOf(10.0, (location.accuracy * 1.4).coerceAtMost(28.0))
        val isDopplerStationary = location.hasSpeed() && location.speed < 0.45f && movedMeters < maxOf(16.0, location.accuracy * 1.6)
        val movedMeaningfully = lastSavedTimeMs == 0L || (movedMeters >= requiredMoveMeters && !isDopplerStationary)

        if (!movedMeaningfully) {
            // Stationary anchored heartbeat (keeps dwell time & on-duty alive WITHOUT coordinate drift)
            val heartbeatDue = (nowMs - lastHeartbeatTimeMs) >= 90_000L
            if (!heartbeatDue || lastSavedTimeMs == 0L) return
            lastHeartbeatTimeMs = nowMs
            writeBreadcrumbAndUserPin(
                lat = lastSavedLat,
                lng = lastSavedLng,
                accuracy = location.accuracy,
                nowMs = nowMs,
                includeBreadcrumb = true
            )
            return
        }

        if (lastSavedTimeMs > 0L && (nowMs - lastSavedTimeMs) < 4_500L) return

        lastSavedLat = location.latitude
        lastSavedLng = location.longitude
        lastSavedTimeMs = nowMs
        lastHeartbeatTimeMs = nowMs

        writeBreadcrumbAndUserPin(
            lat = location.latitude,
            lng = location.longitude,
            accuracy = location.accuracy,
            nowMs = nowMs,
            includeBreadcrumb = true
        )
    }

    private fun writeBreadcrumbAndUserPin(
        lat: Double,
        lng: Double,
        accuracy: Float,
        nowMs: Long,
        includeBreadcrumb: Boolean
    ) {
        val today = DateTimeUtils.getTodayKey()
        val cleanStation = cachedStation.trim().uppercase()
        if (includeBreadcrumb) {
            val locPayload = mapOf(
                "officerId" to cachedUid,
                "officerName" to cachedName.ifBlank { "Officer" },
                "oicStationCode" to cleanStation,
                "dateKey" to today,
                "dayStationKey" to "${cleanStation}_$today",
                "officerDayKey" to "${cachedUid}_$today",
                "lat" to lat,
                "lng" to lng,
                "accuracy" to accuracy,
                "dutyType" to "ON_DUTY",
                "timestamp" to nowMs,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("location_history").document().set(locPayload)
                .addOnFailureListener { e -> Log.w(TAG, "Failed saving bg breadcrumb", e) }
        }

        val userUpdates = mapOf(
            "isOnDuty" to true,
            "lastLocation" to mapOf(
                "lat" to lat,
                "lng" to lng,
                "timestamp" to nowMs
            ),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(cachedUid).update(userUpdates)
            .addOnFailureListener { e -> Log.w(TAG, "Failed updating lastLocation in bg", e) }

        val timeText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(nowMs))
        updateOngoingNotification("සජීවී නිවැරදි GPS සක්‍රීයයි (±${accuracy.toInt()}m) • $timeText")
    }

    private fun stopTrackingAndSelf() {
        cancelRestartAlarm(this)
        stopWatchdog()
        releaseWakeLock()
        cleanupListeners()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun cleanupListeners() {
        locationCallback?.let {
            try {
                fusedClient.removeLocationUpdates(it)
            } catch (_: Exception) {
            }
        }
        locationCallback = null

        gpsListener?.let {
            try {
                val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                lm?.removeUpdates(it)
            } catch (_: Exception) {
            }
        }
        gpsListener = null

        userDocListener?.remove()
        userDocListener = null

        liveMonitorListener?.remove()
        liveMonitorListener = null
    }

    override fun onDestroy() {
        stopWatchdog()
        releaseWakeLock()
        cleanupListeners()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BgLocationService"
        private const val CHANNEL_ID = "police_bg_location_channel"
        private const val NOTIFICATION_ID = 4109
        private const val RESTART_ALARM_REQ_CODE = 4110
        private const val WATCHDOG_INTERVAL_MS = 45_000L

        const val ACTION_START = "com.example.action.START_BG_LOCATION"
        const val ACTION_STOP = "com.example.action.STOP_BG_LOCATION"

        private const val EXTRA_UID = "extra_uid"
        private const val EXTRA_NAME = "extra_name"
        private const val EXTRA_STATION = "extra_station"
        private const val EXTRA_IS_ON_DUTY = "extra_is_on_duty"
        private const val EXTRA_STATUS = "extra_status"
        private const val EXTRA_ROLE = "extra_role"
        private const val EXTRA_HIDDEN_ON_MAP = "extra_hidden_on_map"
        private const val EXTRA_PAUSE_LOGGING = "extra_pause_logging"

        fun hasForegroundLocationPermission(context: Context): Boolean {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return fine || coarse
        }

        fun hasBackgroundLocationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun startIfAllowed(context: Context, user: AppUser? = null) {
            if (!hasForegroundLocationPermission(context)) return
            val intent = Intent(context, BackgroundLocationService::class.java).apply {
                action = ACTION_START
                if (user != null) {
                    putExtra(EXTRA_UID, user.uid)
                    putExtra(EXTRA_NAME, user.displayName)
                    putExtra(EXTRA_STATION, user.oicStationCode ?: "")
                    putExtra(EXTRA_IS_ON_DUTY, user.isOnDuty)
                    putExtra(EXTRA_STATUS, user.status)
                    putExtra(EXTRA_ROLE, user.role)
                    putExtra(EXTRA_HIDDEN_ON_MAP, user.hiddenOnMap)
                    putExtra(EXTRA_PAUSE_LOGGING, user.pauseLocationLogging)
                }
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start foreground location service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            cancelRestartAlarm(context)
            try {
                context.stopService(Intent(context, BackgroundLocationService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Unable to stop background location service: ${e.message}")
            }
        }

        private fun buildRestartPendingIntent(context: Context): PendingIntent {
            val restartIntent = Intent(context, BackgroundLocationService::class.java).apply {
                action = ACTION_START
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, RESTART_ALARM_REQ_CODE, restartIntent, flags)
            } else {
                PendingIntent.getService(context, RESTART_ALARM_REQ_CODE, restartIntent, flags)
            }
        }

        private fun scheduleRestartAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val triggerAt = SystemClock.elapsedRealtime() + 1500L
                val pi = buildRestartPendingIntent(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                } else {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to schedule BG location restart alarm", e)
            }
        }

        private fun cancelRestartAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                alarmManager.cancel(buildRestartPendingIntent(context))
            } catch (_: Exception) {
            }
        }
    }
}
