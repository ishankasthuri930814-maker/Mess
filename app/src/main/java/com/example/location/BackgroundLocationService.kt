package com.example.location

import android.Manifest
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
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.util.DateTimeUtils
import com.example.util.GeoRouteUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class BackgroundLocationService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var gpsListener: LocationListener? = null
    private var userDocListener: ListenerRegistration? = null

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

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTrackingAndSelf()
            return START_NOT_STICKY
        }

        if (!hasForegroundLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        syncStateFromPrefs()
        if (cachedUid.isBlank() || cachedStatus != "APPROVED" || !cachedIsOnDuty) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundSafely()
        attachUserListenerIfNeeded()
        startLocationUpdates()
        return START_STICKY
    }

    private fun startForegroundSafely() {
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
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("All In One Police • සජීවී GPS")
            .setContentText("රාජකාරි ගමන් මග පසුබිමින් සටහන් වෙමින් පවතී")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
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

    private fun syncStateFromPrefs() {
        val prefs = getSharedPreferences("police_prefs", Context.MODE_PRIVATE)
        val authUid = try { Firebase.auth.currentUser?.uid } catch (_: Exception) { null }
        cachedUid = authUid?.takeIf { it.isNotBlank() } ?: prefs.getString("cached_uid", "").orEmpty()
        cachedName = prefs.getString("cached_display_name", "").orEmpty()
        cachedStation = prefs.getString("cached_oic_station_code", "").orEmpty()
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
                val station = snap.getString("oicStationCode") ?: cachedStation
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

                if (status != "APPROVED" || !onDuty) {
                    stopTrackingAndSelf()
                }
            }
    }

    private fun startLocationUpdates() {
        if (!hasForegroundLocationPermission(this)) {
            stopTrackingAndSelf()
            return
        }

        if (locationCallback == null) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(2000L)
                .setMinUpdateDistanceMeters(2.0f)
                .build()

            val cb = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val best = result.locations
                        .filter { it.latitude != 0.0 || it.longitude != 0.0 }
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
                        3000L,
                        2.0f,
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
        if (location.accuracy > 55f) return

        syncStateFromPrefs()
        if (cachedUid.isBlank() || cachedStatus != "APPROVED" || !cachedIsOnDuty) {
            stopTrackingAndSelf()
            return
        }
        if (stealthPauseLogging || stealthHiddenOnMap) return

        val nowMs = System.currentTimeMillis()
        val movedMeters = if (lastSavedTimeMs == 0L) Double.MAX_VALUE
        else GeoRouteUtils.distanceMeters(lastSavedLat, lastSavedLng, location.latitude, location.longitude)

        if (movedMeters < 5.0 && (nowMs - lastSavedTimeMs) < 35_000L) return

        lastSavedLat = location.latitude
        lastSavedLng = location.longitude
        lastSavedTimeMs = nowMs

        val today = DateTimeUtils.getTodayKey()
        val cleanStation = cachedStation.trim()
        val locPayload = mapOf(
            "officerId" to cachedUid,
            "officerName" to cachedName.ifBlank { "Officer" },
            "oicStationCode" to cleanStation,
            "dateKey" to today,
            "dayStationKey" to "${cleanStation}_$today",
            "officerDayKey" to "${cachedUid}_$today",
            "lat" to location.latitude,
            "lng" to location.longitude,
            "accuracy" to location.accuracy,
            "dutyType" to "ON_DUTY",
            "timestamp" to nowMs,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("location_history").document().set(locPayload)
            .addOnFailureListener { e -> Log.w(TAG, "Failed saving bg breadcrumb", e) }

        val userUpdates = mapOf(
            "isOnDuty" to true,
            "lastLocation" to mapOf(
                "lat" to location.latitude,
                "lng" to location.longitude,
                "timestamp" to nowMs
            ),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(cachedUid).update(userUpdates)
            .addOnFailureListener { e -> Log.w(TAG, "Failed updating lastLocation in bg", e) }
    }

    private fun stopTrackingAndSelf() {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        locationCallback?.let {
            try {
                fusedClient.removeLocationUpdates(it)
            } catch (_: Exception) {
            }
        }
        gpsListener?.let {
            try {
                val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                lm?.removeUpdates(it)
            } catch (_: Exception) {
            }
        }
        userDocListener?.remove()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "BgLocationService"
        private const val CHANNEL_ID = "police_bg_location_channel"
        private const val NOTIFICATION_ID = 4109
        const val ACTION_START = "com.example.action.START_BG_LOCATION"
        const val ACTION_STOP = "com.example.action.STOP_BG_LOCATION"

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

        fun startIfAllowed(context: Context) {
            if (!hasForegroundLocationPermission(context)) return
            val intent = Intent(context, BackgroundLocationService::class.java).apply {
                action = ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start foreground location service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackgroundLocationService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                try {
                    context.stopService(Intent(context, BackgroundLocationService::class.java))
                } catch (_: Exception) {
                }
            }
        }
    }
}
