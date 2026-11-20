package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.DutyAssignment
import com.example.data.DutyRecord
import com.example.data.MessRecord
import com.example.data.OfficerProfile
import com.example.data.PoliceContact
import com.example.data.PoliceRepository
import com.example.data.EmergencyAlert
import com.example.data.LocationHistoryPoint
import com.example.data.RunningChartRecord
import com.example.data.TeamDutyMember
import com.example.data.TeamDutySession
import android.util.Log
import com.example.util.DateTimeUtils
import com.example.util.DutySlotRules
import com.example.util.GeoRouteUtils
import com.example.util.ImageCompressor
import com.example.util.ReserveHolderInfo
import com.example.util.StationContactUtils
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.example.data.AppNotification
import com.example.data.AppUser
import com.example.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessaging
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.max

enum class AuthPortal {
    CHOOSER,
    OIC,
    OFFICER,
    ADMIN_BRANCH
}

enum class DutyFilter {
    ALL,
    ON_DUTY,
    EXTRA_DUTY,
    OFF_DUTY
}

data class StorageStats(
    val totalOriginalKb: Int = 0,
    val totalCompressedKb: Int = 0,
    val totalSavedKb: Int = 0,
    val totalPhotos: Int = 0
)

data class MonthlyMessSummary(
    val breakfastCount: Int = 0,
    val lunchCount: Int = 0,
    val dinnerCount: Int = 0,
    val totalMeals: Int = 0,
    val breakfastCost: Double = 0.0,
    val lunchCost: Double = 0.0,
    val dinnerCost: Double = 0.0,
    val monthlyTotalCost: Double = 0.0,
    val todayTotalCost: Double = 0.0,
    val allTimeTotalCost: Double = 0.0,
    val allTimeTotalMeals: Int = 0
)

data class MonthlyVehicleSummary(
    val vehicleNumber: String = "",
    val monthName: String = "",
    val previousMonthRemainingFuel: Double = 0.0,
    val totalFuelAdded: Double = 0.0,
    val totalFuelConsumed: Double = 0.0,
    val totalKmDriven: Double = 0.0,
    val currentRemainingFuel: Double = 0.0
)

data class DailyDutySummary(
    val dateKey: String, // "YYYY-MM-DD"
    val formattedDate: String, // "2026-11-09 • සඳුදා (Mon)"
    val onDutyRecord: DutyRecord?,
    val offDutyRecord: DutyRecord?,
    val extraDutyRecords: List<DutyRecord>,
    val allRecords: List<DutyRecord>,
    val totalDutyMinutes: Long,
    val totalHoursFormatted: String,
    val isOngoing: Boolean,
    val totalPhotosCount: Int
)

data class DailyMessSummary(
    val dateKey: String, // "YYYY-MM-DD"
    val formattedDate: String, // "2026-11-09 • සඳුදා (Mon)"
    val breakfast: MessRecord?,
    val lunch: MessRecord?,
    val dinner: MessRecord?,
    val allRecords: List<MessRecord>,
    val takenCount: Int,
    val totalPhotosCount: Int,
    val isCurrentDay: Boolean
)

data class MapNavigationTarget(
    val alertId: String = "",
    val title: String,
    val subtitle: String = "",
    val lat: Double,
    val lng: Double,
    val phone: String = ""
)

data class ActiveRouteState(
    val target: MapNavigationTarget,
    val originLat: Double,
    val originLng: Double,
    val routePoints: List<Pair<Double, Double>> = emptyList(),
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val isFallbackDirect: Boolean = false,
    val isLoading: Boolean = false
)

class PoliceViewModel(private val repository: PoliceRepository, application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("police_prefs", Context.MODE_PRIVATE)

    // --- Dark Mode State ---
    private val _isDarkMode = MutableStateFlow<Boolean?>(
        if (prefs.contains("is_dark_mode")) prefs.getBoolean("is_dark_mode", false) else null
    )
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    // --- User Registration State ---
    private val _isRegistered = MutableStateFlow(prefs.getBoolean("is_registered", false))
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    private val _isOICLoggedIn = MutableStateFlow(prefs.getBoolean("is_oic_logged_in", false))
    val isOICLoggedIn: StateFlow<Boolean> = _isOICLoggedIn.asStateFlow()

    // Portal chooser: CHOOSER, OIC, OFFICER
    private val _authPortal = MutableStateFlow(AuthPortal.CHOOSER)
    val authPortal: StateFlow<AuthPortal> = _authPortal.asStateFlow()

    fun setAuthPortal(portal: AuthPortal) {
        _authPortal.value = portal
    }

    private val auth = Firebase.auth
    private val STOP_TIMEOUT_MILLIS = 5000L

    private fun cacheAppUser(user: AppUser) {
        prefs.edit()
            .putString("cached_uid", user.uid)
            .putString("cached_display_name", user.displayName)
            .putString("cached_email", user.email)
            .putString("cached_role", user.role)
            .putString("cached_status", user.status)
            .putString("cached_oic_email", user.oicEmail)
            .putString("cached_oic_station_code", user.oicStationCode ?: "")
            .putString("cached_station_name", user.stationName)
            .putString("cached_badge_number", user.badgeNumber)
            .putString("cached_rank", user.rank)
            .putString("cached_phone_number", user.phoneNumber)
            .putString("cached_address", user.address)
            .putBoolean("cached_is_on_duty", user.isOnDuty)
            .putString("cached_admin_secret_seed", user.adminSecretSeed)
            .putBoolean("explicit_logout", false)
            .apply()
    }

    private fun loadCachedAppUser(uid: String): AppUser? {
        if (uid.isBlank()) return null
        val cachedUid = prefs.getString("cached_uid", null) ?: return null
        if (cachedUid != uid) return null
        val stationCode = prefs.getString("cached_oic_station_code", "")?.takeIf { it.isNotBlank() }
        return AppUser(
            uid = cachedUid,
            displayName = prefs.getString("cached_display_name", "") ?: "",
            email = prefs.getString("cached_email", "") ?: "",
            role = prefs.getString("cached_role", "OFFICER") ?: "OFFICER",
            status = prefs.getString("cached_status", "PENDING") ?: "PENDING",
            oicEmail = prefs.getString("cached_oic_email", "") ?: "",
            oicStationCode = stationCode,
            stationName = prefs.getString("cached_station_name", "") ?: "",
            badgeNumber = prefs.getString("cached_badge_number", "") ?: "",
            rank = prefs.getString("cached_rank", "") ?: "",
            phoneNumber = prefs.getString("cached_phone_number", "") ?: "",
            address = prefs.getString("cached_address", "") ?: "",
            isOnDuty = prefs.getBoolean("cached_is_on_duty", false),
            adminSecretSeed = prefs.getString("cached_admin_secret_seed", "") ?: ""
        )
    }

    private fun teamDutyPrefKey(uid: String) = "active_team_duty_id_$uid"
    private fun lastReadNotifPrefKey(uid: String) = "last_read_notif_ms_$uid"
    private fun adminUnlockedPrefKey(uid: String) = "admin_unlocked_date_$uid"

    private fun clearCachedAppUser() {
        val prevUid = prefs.getString("cached_uid", null) ?: auth.currentUser?.uid
        val editor = prefs.edit()
            .remove("cached_uid")
            .remove("cached_display_name")
            .remove("cached_email")
            .remove("cached_role")
            .remove("cached_status")
            .remove("cached_oic_email")
            .remove("cached_oic_station_code")
            .remove("cached_station_name")
            .remove("cached_badge_number")
            .remove("cached_rank")
            .remove("cached_phone_number")
            .remove("cached_address")
            .remove("cached_is_on_duty")
            .remove("cached_admin_secret_seed")
            .remove("active_team_duty_id")
            .remove("admin_unlocked_date")
            .remove("is_registered")
            .remove("is_oic_logged_in")
        if (!prevUid.isNullOrBlank()) {
            editor.remove(adminUnlockedPrefKey(prevUid))
        }
        editor.apply()
    }

    fun shouldAttemptAutoSignIn(): Boolean = !prefs.getBoolean("explicit_logout", false)

    fun markSignedIn() {
        prefs.edit().putBoolean("explicit_logout", false).apply()
    }

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val initialCachedUser = auth.currentUser?.uid?.let { loadCachedAppUser(it) }
    private val _appUser = MutableStateFlow<AppUser?>(initialCachedUser)
    val appUser: StateFlow<AppUser?> = _appUser.asStateFlow()

    private val activeOfficerUid: StateFlow<String> = combine(_currentUser, _appUser) { fbUser, app ->
        when {
            fbUser == null -> ""
            app != null && app.uid == fbUser.uid -> app.uid
            else -> fbUser.uid
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, auth.currentUser?.uid.orEmpty())

    private val _isProfileLoaded = MutableStateFlow(auth.currentUser == null || initialCachedUser != null)
    val isProfileLoaded: StateFlow<Boolean> = _isProfileLoaded.asStateFlow()

    private var fetchUserJob: Job? = null
    private var locationJob: Job? = null
    private var activeTrackingKey: String? = null
    private var activeLocationCallback: LocationCallback? = null
    private var activePlatformLocationListener: android.location.LocationListener? = null
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    // --- Subordinates (for OIC & Admin Branch) ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val subordinates: StateFlow<List<AppUser>> = _appUser.flatMapLatest { user ->
        val stationCode = user?.oicStationCode
        if ((user?.role == "OIC" || user?.role == "ADMIN_BRANCH") && !stationCode.isNullOrBlank()) {
            repository.observeSubordinates(stationCode).catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceViewModel", "Error observing subordinates", e)
                emit(emptyList())
            }
        } else {
            flow { emit(emptyList<AppUser>()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val stationDutyRecords: StateFlow<List<DutyRecord>> = _appUser.flatMapLatest { user ->
        val stationCode = user?.oicStationCode
        if (user?.status == "APPROVED" && !stationCode.isNullOrBlank()) {
            repository.observeStationDutyRecords(stationCode).catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceViewModel", "Error observing station duty records", e)
                emit(emptyList())
            }
        } else {
            flow { emit(emptyList<DutyRecord>()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val stationAllUsers: StateFlow<List<AppUser>> = _appUser.flatMapLatest { user ->
        val stationCode = user?.oicStationCode
        if (user?.status == "APPROVED" && !stationCode.isNullOrBlank()) {
            repository.observeStationUsers(stationCode)
        } else {
            flow { emit(emptyList<AppUser>()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val stationContacts: StateFlow<List<AppUser>> = combine(
        _appUser,
        stationAllUsers
    ) { current, stationUsers ->
        StationContactUtils.filterAndSortStationContacts(current, stationUsers)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val stationActiveSingleSlotHolders: StateFlow<Map<String, ReserveHolderInfo>> = combine(
        stationAllUsers,
        stationDutyRecords
    ) { users, records ->
        DutySlotRules.resolveActiveSingleSlotHolders(users, records)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyMap())

    val stationActiveReserveHolder: StateFlow<ReserveHolderInfo?> = stationActiveSingleSlotHolders
        .map { it[DutySlotRules.SLOT_RESERVE] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val stationAdminSeed: StateFlow<String> = _appUser.flatMapLatest { user ->
        val stationCode = user?.oicStationCode
        if (!stationCode.isNullOrBlank()) {
            repository.observeStationAdminSeed(stationCode)
        } else {
            flow { emit("") }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    @OptIn(ExperimentalCoroutinesApi::class)
    val stationDutyAssignments: StateFlow<List<DutyAssignment>> = _appUser.flatMapLatest { user ->
        val stationCode = user?.oicStationCode
        if ((user?.role == "OIC" || user?.role == "ADMIN_BRANCH") && !stationCode.isNullOrBlank()) {
            repository.observeStationDutyAssignments(stationCode).catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceViewModel", "Error observing station duty assignments", e)
                emit(emptyList())
            }
        } else {
            flow { emit(emptyList<DutyAssignment>()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val stationMessRecords: StateFlow<List<MessRecord>> = _appUser.flatMapLatest { user ->
        val stationCode = user?.oicStationCode
        if ((user?.role == "OIC" || user?.role == "ADMIN_BRANCH") && !stationCode.isNullOrBlank()) {
            repository.observeStationMessRecords(stationCode).catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceViewModel", "Error observing station mess records", e)
                emit(emptyList())
            }
        } else {
            flow { emit(emptyList<MessRecord>()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val myDutyAssignments: StateFlow<List<DutyAssignment>> = activeOfficerUid.flatMapLatest { uid ->
        if (uid.isNotBlank()) {
            repository.observeOfficerDutyAssignments(uid, DateTimeUtils.getTodayKey()).catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceViewModel", "Error observing officer duty assignments", e)
                emit(emptyList())
            }
        } else {
            flow { emit(emptyList<DutyAssignment>()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val myTodayAssignedDuty: StateFlow<DutyAssignment?> = myDutyAssignments.map { list ->
        val today = DateTimeUtils.getTodayKey()
        list.firstOrNull { it.dateKey == today }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _isAdminPasscodeUnlockedToday = MutableStateFlow(
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }?.let { uid ->
            prefs.getString(adminUnlockedPrefKey(uid), "") == DateTimeUtils.getTodayKey()
        } ?: false
    )
    val isAdminPasscodeUnlockedToday: StateFlow<Boolean> = _isAdminPasscodeUnlockedToday.asStateFlow()

    private val _selectedOfficer = MutableStateFlow<AppUser?>(null)
    val selectedOfficer: StateFlow<AppUser?> = _selectedOfficer.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedOfficerActivity = _selectedOfficer.flatMapLatest { officer ->
        if (officer != null && officer.uid.isNotBlank()) {
            repository.observeDutyRecordsForOfficer(officer.uid).catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceViewModel", "Error observing officer activity", e)
                emit(emptyList())
            }
        } else {
            flow { emit(emptyList<DutyRecord>()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedOfficerMessRecords: StateFlow<List<MessRecord>> = _selectedOfficer.flatMapLatest { officer ->
        if (officer != null && officer.uid.isNotBlank()) {
            repository.observeMessRecordsForOfficer(officer.uid).catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceViewModel", "Error observing officer mess records", e)
                emit(emptyList())
            }
        } else {
            flow { emit(emptyList<MessRecord>()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedOfficerRunningCharts: StateFlow<List<RunningChartRecord>> = _selectedOfficer.flatMapLatest { officer ->
        if (officer != null && officer.uid.isNotBlank()) {
            repository.observeRunningChartRecordsForOfficer(officer.uid).catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceViewModel", "Error observing officer running charts", e)
                emit(emptyList())
            }
        } else {
            flow { emit(emptyList<RunningChartRecord>()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun selectOfficer(officer: AppUser?) {
        _selectedOfficer.value = officer
    }

    // --- Live Map, Location History Trails & Emergency SOS ---
    private val _myCurrentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val myCurrentLocation: StateFlow<Pair<Double, Double>?> = _myCurrentLocation.asStateFlow()

    private val _myLocationAccuracyMeters = MutableStateFlow<Float?>(null)
    val myLocationAccuracyMeters: StateFlow<Float?> = _myLocationAccuracyMeters.asStateFlow()

    private val _liveLocalTrail = MutableStateFlow<List<LocationHistoryPoint>>(emptyList())

    private val _selectedMapDate = MutableStateFlow(DateTimeUtils.getTodayKey())
    val selectedMapDate: StateFlow<String> = _selectedMapDate.asStateFlow()

    fun selectMapDate(dateKey: String) {
        if (dateKey.isNotBlank()) {
            _selectedMapDate.value = dateKey
        }
    }

    private fun mergeTrailWithLiveLocal(
        remote: List<LocationHistoryPoint>,
        local: List<LocationHistoryPoint>,
        dateKey: String
    ): List<LocationHistoryPoint> {
        if (dateKey != DateTimeUtils.getTodayKey() || local.isEmpty()) return remote
        val merged = (remote + local.filter { it.dateKey == dateKey })
            .distinctBy { "${it.officerId}_${it.timestamp / 2000L}_${(it.lat * 100_000).toLong()}_${(it.lng * 100_000).toLong()}" }
            .sortedBy { it.timestamp }
        return merged
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val remoteStationLocationHistory = combine(
        _appUser,
        _selectedMapDate
    ) { user, dateKey ->
        Pair(user, dateKey)
    }.flatMapLatest { (user, dateKey) ->
        val stationCode = user?.oicStationCode
        if (user?.role == "OIC" && !stationCode.isNullOrBlank()) {
            repository.observeStationLocationHistory(stationCode, dateKey).catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceViewModel", "Error observing station location history", e)
                emit(emptyList())
            }
        } else {
            flow { emit(emptyList<LocationHistoryPoint>()) }
        }
    }

    val stationLocationHistory: StateFlow<List<LocationHistoryPoint>> = combine(
        remoteStationLocationHistory,
        _liveLocalTrail,
        _selectedMapDate
    ) { remote, local, dateKey ->
        mergeTrailWithLiveLocal(remote, local, dateKey)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val remoteMyLocationHistory = combine(
        _appUser,
        _selectedMapDate
    ) { user, dateKey ->
        Pair(user, dateKey)
    }.flatMapLatest { (user, dateKey) ->
        if (user != null && user.uid.isNotBlank()) {
            repository.observeOfficerLocationHistory(user.uid, dateKey).catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceViewModel", "Error observing own location history", e)
                emit(emptyList())
            }
        } else {
            flow { emit(emptyList<LocationHistoryPoint>()) }
        }
    }

    val myLocationHistory: StateFlow<List<LocationHistoryPoint>> = combine(
        remoteMyLocationHistory,
        _liveLocalTrail,
        _selectedMapDate
    ) { remote, local, dateKey ->
        mergeTrailWithLiveLocal(remote, local, dateKey)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allActiveEmergencyAlerts: StateFlow<List<EmergencyAlert>> =
        _appUser.flatMapLatest { user ->
            if (user != null && user.uid.isNotBlank()) {
                repository.observeActiveEmergencyAlerts().catch { e ->
                    if (e is CancellationException && e.cause == null) throw e
                    emit(emptyList())
                }
            } else {
                flow { emit(emptyList<EmergencyAlert>()) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val nearbyEmergencyAlerts: StateFlow<List<EmergencyAlert>> = combine(
        allActiveEmergencyAlerts,
        _appUser,
        _myCurrentLocation
    ) { alerts, user, myLoc ->
        if (user == null || user.uid.isBlank() || user.status != "APPROVED") {
            emptyList()
        } else {
            val nowMs = System.currentTimeMillis()
            val myStation = user.oicStationCode?.trim()?.uppercase().orEmpty()
            alerts.filter { alert ->
                val ageMs = nowMs - (alert.createdAt?.toDate()?.time ?: alert.createdAtMs)
                if (ageMs > 24 * 60 * 60 * 1000L) return@filter false
                if (alert.senderUid == user.uid) return@filter true
                if (user.role == "OIC" && myStation.isNotBlank() && alert.oicStationCode == myStation) {
                    return@filter true
                }
                if (myLoc != null && (alert.lat != 0.0 || alert.lng != 0.0)) {
                    val distKm = GeoRouteUtils.distanceKm(myLoc.first, myLoc.second, alert.lat, alert.lng)
                    distKm <= alert.radiusKm || (myStation.isNotBlank() && alert.oicStationCode == myStation && alert.radiusKm >= 25.0)
                } else {
                    myStation.isNotBlank() && alert.oicStationCode == myStation
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _activeEmergencyPopup = MutableStateFlow<EmergencyAlert?>(null)
    val activeEmergencyPopup: StateFlow<EmergencyAlert?> = _activeEmergencyPopup.asStateFlow()

    fun dismissEmergencyPopup() {
        _activeEmergencyPopup.value = null
    }

    private val _activeRouteState = MutableStateFlow<ActiveRouteState?>(null)
    val activeRouteState: StateFlow<ActiveRouteState?> = _activeRouteState.asStateFlow()

    private val _isRouteMapDialogOpen = MutableStateFlow(false)
    val isRouteMapDialogOpen: StateFlow<Boolean> = _isRouteMapDialogOpen.asStateFlow()

    fun openRouteMapDialog() {
        _isRouteMapDialogOpen.value = true
        refreshCurrentLocation(showFeedback = false)
    }

    fun closeRouteMapDialog() {
        _isRouteMapDialogOpen.value = false
    }

    fun clearMapRoute() {
        _activeRouteState.value = null
    }

    private var baselineEmergencyUid: String? = null
    private val alertedEmergencyIds = mutableSetOf<String>()
    private var lastBreadcrumbLat: Double = 0.0
    private var lastBreadcrumbLng: Double = 0.0
    private var lastBreadcrumbTimeMs: Long = 0L
    private var lastBreadcrumbDutyType: String = ""
    private var lastLocalTrailLat: Double = 0.0
    private var lastLocalTrailLng: Double = 0.0
    private var lastLocalTrailTimeMs: Long = 0L
    private var lastLocationFixTimeMs: Long = 0L

    // --- Team Duty QR Sessions (Strictly scoped per UID & verified against membership) ---
    private val _activeTeamDutyId = MutableStateFlow(
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }?.let { prefs.getString(teamDutyPrefKey(it), "") } ?: ""
    )
    val activeTeamDutyId: StateFlow<String> = _activeTeamDutyId.asStateFlow()
    private val handledEndedTeamSessionIds = mutableSetOf<String>()
    private var hydratedUid: String? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeTeamDutySession: StateFlow<TeamDutySession?> = combine(
        _activeTeamDutyId,
        activeOfficerUid
    ) { sid, uid -> Pair(sid, uid) }
        .flatMapLatest { (sid, uid) ->
            if (sid.isNotBlank() && uid.isNotBlank()) {
                combine(
                    repository.observeTeamDutySession(sid),
                    repository.observeMyTeamMembership(sid, uid)
                ) { session, myMember ->
                    when {
                        session == null -> null
                        session.status == "ENDED" -> session
                        session.leaderUid == uid -> session
                        myMember != null && myMember.status == "ACTIVE" -> session
                        else -> null
                    }
                }.catch { e ->
                    if (e is CancellationException && e.cause == null) throw e
                    Log.w("PoliceViewModel", "Error observing team duty session", e)
                    emit(null)
                }
            } else {
                flow { emit(null) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeTeamDutyMembers: StateFlow<List<TeamDutyMember>> = activeTeamDutySession.flatMapLatest { session ->
        if (session != null && session.status == "ACTIVE") {
            repository.observeTeamDutyMembers(session.sessionId).catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceViewModel", "Error observing team duty members", e)
                emit(emptyList())
            }
        } else {
            flow { emit(emptyList<TeamDutyMember>()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeTeamTeammateUsers: StateFlow<List<AppUser>> = combine(
        activeTeamDutySession,
        activeTeamDutyMembers
    ) { session, members ->
        if (session != null && session.status == "ACTIVE") {
            members.filter { it.status == "ACTIVE" }.map { it.uid }.distinct()
        } else {
            emptyList()
        }
    }.flatMapLatest { uids ->
        if (uids.isEmpty()) {
            flow { emit(emptyList<AppUser>()) }
        } else {
            repository.observeTeamTeammatesUsers(uids).catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                emit(emptyList())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    // --- Real-time Notifications & FCM Sync ---
    private var lastFcmSyncKey: String? = null
    private var lastSubscribedStationTopic: String? = null
    private var baselineNotifUid: String? = null
    private val alertedNotificationIds = mutableSetOf<String>()

    private val _lastReadNotifMs = MutableStateFlow(
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }?.let { prefs.getLong(lastReadNotifPrefKey(it), 0L) } ?: 0L
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val notifications: StateFlow<List<AppNotification>> = _appUser.flatMapLatest { user ->
        if (user != null && user.uid.isNotBlank() && user.status == "APPROVED") {
            repository.observeNotifications(user.uid, user.oicStationCode).catch { e ->
                if (e is CancellationException && e.cause == null) throw e
                Log.w("PoliceViewModel", "Error observing notifications", e)
                emit(emptyList())
            }
        } else {
            flow { emit(emptyList<AppNotification>()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val unreadNotificationCount: StateFlow<Int> = combine(
        notifications,
        _lastReadNotifMs,
        _appUser
    ) { list, lastReadMs, user ->
        val myUid = user?.uid ?: ""
        val baseline = max(lastReadMs, 0L)
        list.count { notif ->
            val time = notif.createdAt?.toDate()?.time ?: notif.createdAtMs
            notif.senderUid != myUid && time > baseline
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    fun markNotificationsAsRead() {
        val now = System.currentTimeMillis()
        _lastReadNotifMs.value = now
        val uid = activeOfficerUid.value
        if (uid.isNotBlank()) {
            prefs.edit().putLong(lastReadNotifPrefKey(uid), now).apply()
        }
    }

    fun sendNotification(
        title: String,
        message: String,
        targetType: String,
        targetUid: String = "",
        priority: String = "NORMAL",
        onSuccess: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        val user = _appUser.value
        if (user == null) {
            onError()
            return
        }
        viewModelScope.launch {
            val stationCode = user.oicStationCode ?: ""
            val result = repository.sendNotification(
                title = title,
                message = message,
                senderName = user.displayName.ifBlank { user.email },
                senderRole = user.role,
                targetType = targetType,
                targetStationCode = if (targetType == "STATION" || targetType == "USER") stationCode else "",
                targetUid = if (targetType == "USER") targetUid else "",
                priority = priority
            )
            if (result.isSuccess) {
                _userMessage.emit("දැනුම්දීම සාර්ථකව යවන ලදී!")
                onSuccess()
            } else {
                val errMsg = result.exceptionOrNull()?.localizedMessage ?: "දැනුම්දීම යැවීම අසාර්ථක විය."
                _userMessage.emit(errMsg)
                onError()
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            repository.deleteNotification(notificationId)
        }
    }

    private fun syncFcmTokenAndTopics(user: AppUser) {
        val syncKey = "${user.uid}:${user.role}:${user.oicStationCode ?: ""}"
        if (syncKey == lastFcmSyncKey) return
        lastFcmSyncKey = syncKey

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val messaging = FirebaseMessaging.getInstance()
                val token = try {
                    messaging.token.await()
                } catch (_: Exception) {
                    prefs.getString("pending_fcm_token", null)
                }
                if (!token.isNullOrBlank() && token != user.fcmToken) {
                    repository.saveFcmToken(user.uid, token)
                    prefs.edit().remove("pending_fcm_token").apply()
                }

                messaging.subscribeToTopic("all_users")
                val roleTopic = if (user.role == "OIC") "role_oic" else "role_officer"
                messaging.subscribeToTopic(roleTopic)

                val rawStation = user.oicStationCode?.trim().orEmpty()
                val newStationTopic = if (rawStation.isNotBlank()) {
                    "station_" + rawStation.replace("[^A-Za-z0-9_.~%-]".toRegex(), "_")
                } else {
                    null
                }
                if (lastSubscribedStationTopic != null && lastSubscribedStationTopic != newStationTopic) {
                    messaging.unsubscribeFromTopic(lastSubscribedStationTopic!!)
                }
                if (newStationTopic != null) {
                    messaging.subscribeToTopic(newStationTopic)
                }
                lastSubscribedStationTopic = newStationTopic
            } catch (e: Exception) {
                Log.w("PoliceViewModel", "FCM sync/topic subscription skipped", e)
            }
        }
    }

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val fbUser = firebaseAuth.currentUser
        _currentUser.value = fbUser
        if (fbUser != null) {
            prefs.edit().putBoolean("explicit_logout", false).apply()
            _activeTeamDutyId.value = prefs.getString(teamDutyPrefKey(fbUser.uid), "") ?: ""
            _lastReadNotifMs.value = prefs.getLong(lastReadNotifPrefKey(fbUser.uid), 0L)
            _isAdminPasscodeUnlockedToday.value =
                prefs.getString(adminUnlockedPrefKey(fbUser.uid), "") == DateTimeUtils.getTodayKey()
            fetchAppUser(fbUser.uid)
        } else {
            fetchUserJob?.cancel()
            fetchUserJob = null
            locationJob?.cancel()
            locationJob = null
            activeTrackingKey = null
            hydratedUid = null
            _activeTeamDutyId.value = ""
            _lastReadNotifMs.value = 0L
            _isAdminPasscodeUnlockedToday.value = false
            _appUser.value = null
            _selectedOfficer.value = null
            _isProfileLoaded.value = true
            _isRegistered.value = false
            _isOICLoggedIn.value = false
        }
    }

    init {
        NotificationHelper.createNotificationChannels(application)
        auth.addAuthStateListener(authStateListener)
        viewModelScope.launch {
            notifications.collect { list ->
                val user = _appUser.value ?: return@collect
                if (baselineNotifUid != user.uid) {
                    baselineNotifUid = user.uid
                    list.forEach { if (it.id.isNotBlank()) alertedNotificationIds.add(it.id) }
                    return@collect
                }
                val lastRead = _lastReadNotifMs.value
                list.forEach { notif ->
                    if (notif.id.isNotBlank() &&
                        notif.id !in alertedNotificationIds &&
                        notif.senderUid != user.uid
                    ) {
                        alertedNotificationIds.add(notif.id)
                        val ts = notif.createdAt?.toDate()?.time ?: notif.createdAtMs
                        if (ts > lastRead) {
                            NotificationHelper.showSystemNotification(
                                context = getApplication(),
                                title = notif.title,
                                body = "${notif.senderName}: ${notif.message}",
                                notificationId = notif.id.hashCode(),
                                isUrgent = notif.priority == "URGENT"
                            )
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            activeTeamDutySession.collect { session ->
                val uid = activeOfficerUid.value.ifBlank { auth.currentUser?.uid ?: "" }
                if (uid.isBlank()) return@collect
                if (session != null && session.status == "ENDED" && session.sessionId.isNotBlank()) {
                    if (session.leaderUid != uid &&
                        session.sessionId !in handledEndedTeamSessionIds &&
                        _activeTeamDutyId.value == session.sessionId
                    ) {
                        handledEndedTeamSessionIds.add(session.sessionId)
                        autoLeaveEndedTeamDuty(session)
                    } else if (session.leaderUid == uid && _activeTeamDutyId.value == session.sessionId) {
                        prefs.edit().remove(teamDutyPrefKey(uid)).remove("active_team_duty_id").apply()
                        _activeTeamDutyId.value = ""
                    }
                }
            }
        }
        viewModelScope.launch {
            nearbyEmergencyAlerts.collect { alerts ->
                val user = _appUser.value ?: return@collect
                val now = System.currentTimeMillis()
                if (baselineEmergencyUid != user.uid) {
                    baselineEmergencyUid = user.uid
                    alerts.forEach { alert ->
                        if (alert.id.isNotBlank()) {
                            val ts = alert.createdAt?.toDate()?.time ?: alert.createdAtMs
                            if (alert.senderUid != user.uid && (now - ts) <= 180_000L) {
                                alertedEmergencyIds.add(alert.id)
                                _activeEmergencyPopup.value = alert
                                NotificationHelper.showEmergencyNotification(
                                    context = getApplication(),
                                    alertId = alert.id,
                                    senderName = alert.senderName.ifBlank { alert.senderRole },
                                    message = alert.message,
                                    lat = alert.lat,
                                    lng = alert.lng,
                                    phone = alert.senderPhone
                                )
                            } else {
                                alertedEmergencyIds.add(alert.id)
                            }
                        }
                    }
                    return@collect
                }
                alerts.forEach { alert ->
                    if (alert.id.isNotBlank() &&
                        alert.id !in alertedEmergencyIds &&
                        alert.senderUid != user.uid
                    ) {
                        alertedEmergencyIds.add(alert.id)
                        _activeEmergencyPopup.value = alert
                        NotificationHelper.showEmergencyNotification(
                            context = getApplication(),
                            alertId = alert.id,
                            senderName = alert.senderName.ifBlank { alert.senderRole },
                            message = alert.message,
                            lat = alert.lat,
                            lng = alert.lng,
                            phone = alert.senderPhone
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        stopRealtimeLocationUpdates()
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
        fetchUserJob?.cancel()
        locationJob?.cancel()
    }

    private fun fetchAppUser(uid: String) {
        fetchUserJob?.cancel()
        val cached = if (_appUser.value?.uid == uid) _appUser.value else loadCachedAppUser(uid)
        if (cached != null) {
            _appUser.value = cached
            _isRegistered.value = cached.status == "APPROVED" || cached.status == "PENDING"
            _isOICLoggedIn.value = cached.role == "OIC"
            _isProfileLoaded.value = true
            updateLocationTracking(cached)
        } else {
            _isProfileLoaded.value = false
        }
        fetchUserJob = viewModelScope.launch {
            repository.observeUser(uid)
                .catch { e ->
                    if (e is CancellationException && e.cause == null) throw e
                    Log.w("PoliceViewModel", "Error observing user profile", e)
                    _isProfileLoaded.value = true
                }
                .collect { user ->
                    _appUser.value = user
                    _isProfileLoaded.value = true
                    if (user != null) {
                        cacheAppUser(user)
                        _isRegistered.value = user.status == "APPROVED" || user.status == "PENDING"
                        _isOICLoggedIn.value = user.role == "OIC"
                        prefs.edit()
                            .putBoolean("is_registered", _isRegistered.value)
                            .putBoolean("is_oic_logged_in", _isOICLoggedIn.value)
                            .apply()
                        if (user.role == "OFFICER") {
                            repository.saveOfficerProfile(
                                OfficerProfile(
                                    name = user.displayName,
                                    rank = user.rank.ifBlank { "Police Constable" },
                                    badgeNumber = user.badgeNumber,
                                    station = user.stationName.ifBlank { user.oicStationCode ?: "" },
                                    phoneNumber = user.phoneNumber,
                                    address = user.address
                                )
                            )
                            if (user.status == "APPROVED" && hydratedUid != user.uid) {
                                hydratedUid = user.uid
                                viewModelScope.launch(Dispatchers.IO) {
                                    repository.hydrateOfficerLocalDataFromFirestoreIfEmpty(user.uid)
                                }
                            }
                        } else {
                            // OIC should never carry an officer team duty session
                            _activeTeamDutyId.value = ""
                        }
                        updateLocationTracking(user)
                        syncFcmTokenAndTopics(user)
                    } else {
                        clearCachedAppUser()
                        _activeTeamDutyId.value = ""
                        prefs.edit().remove(teamDutyPrefKey(uid)).apply()
                        repository.clearLocalProfile()
                        locationJob?.cancel()
                        locationJob = null
                        stopRealtimeLocationUpdates()
                        _liveLocalTrail.value = emptyList()
                        activeTrackingKey = null
                        lastFcmSyncKey = null
                    }
                }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    private fun getBestPlatformLastKnownLocation(): android.location.Location? {
        if (!hasLocationPermission()) return null
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            ?: return null
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val providers = buildList {
            if (hasFine) add(android.location.LocationManager.GPS_PROVIDER)
            add(android.location.LocationManager.NETWORK_PROVIDER)
            add(android.location.LocationManager.PASSIVE_PROVIDER)
        }
        val candidates = providers.mapNotNull { provider ->
            try {
                lm.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                null
            }
        }.filter { it.latitude != 0.0 || it.longitude != 0.0 }
        val now = System.currentTimeMillis()
        return candidates
            .filter { (now - it.time) <= 30 * 60_000L }
            .minWithOrNull(compareBy<android.location.Location> { it.accuracy }.thenByDescending { it.time })
            ?: candidates.maxByOrNull { it.time }
    }

    private suspend fun obtainFreshLocation(): android.location.Location? {
        if (!hasLocationPermission()) return null

        // 1. Instant cached seed so blue dot & accuracy appear immediately (<100ms) while acquiring fresh GPS
        val fusedLast = try {
            fusedLocationClient.lastLocation.await()
        } catch (_: Exception) {
            null
        }?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
        val platformLast = getBestPlatformLastKnownLocation()
        val bestCached = listOfNotNull(fusedLast, platformLast)
            .minWithOrNull(compareBy<android.location.Location> { it.accuracy }.thenByDescending { it.time })
        if (bestCached != null && _myCurrentLocation.value == null) {
            processIncomingLocation(bestCached, overrideIsOnDuty = null, persistToFirestore = false)
        }

        // 2. Request fresh high-accuracy fix
        var loc: android.location.Location? = null
        try {
            withTimeoutOrNull(5000) {
                loc = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            }
        } catch (_: Exception) {
            // ignore
        }
        if (loc == null || loc!!.accuracy > 65f) {
            val freshPlatform = getBestPlatformLastKnownLocation()
            val chosenLast = listOfNotNull(fusedLast, freshPlatform)
                .filter { (System.currentTimeMillis() - it.time) <= 120_000L }
                .minByOrNull { it.accuracy }
            if (chosenLast != null && (loc == null || chosenLast.accuracy < loc!!.accuracy)) {
                loc = chosenLast
            }
        }
        if (loc == null) {
            try {
                withTimeoutOrNull(3500) {
                    loc = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                }
            } catch (_: Exception) {
                // ignore
            }
        }
        val finalLoc = loc ?: bestCached
        finalLoc?.let { processIncomingLocation(it, overrideIsOnDuty = null, persistToFirestore = false) }
        return finalLoc
    }

    private fun processIncomingLocation(
        location: android.location.Location,
        overrideIsOnDuty: Boolean? = null,
        persistToFirestore: Boolean = true
    ) {
        if (location.latitude == 0.0 && location.longitude == 0.0) return
        val nowMs = System.currentTimeMillis()
        val prevFix = _myCurrentLocation.value
        val prevAcc = _myLocationAccuracyMeters.value

        // Reject noisy cell-tower jumps (> 75m inaccuracy) if we already have a recent accurate fix
        if (prevFix != null && prevAcc != null && prevAcc <= 35f &&
            location.accuracy > 75f && (nowMs - lastLocationFixTimeMs) < 90_000L &&
            overrideIsOnDuty == null
        ) {
            return
        }

        _myCurrentLocation.value = Pair(location.latitude, location.longitude)
        _myLocationAccuracyMeters.value = location.accuracy
        lastLocationFixTimeMs = nowMs

        val user = _appUser.value ?: return
        val latestDuty = overrideIsOnDuty ?: user.isOnDuty
        val dutyTypeStr = if (latestDuty) "ON_DUTY" else "OFF_DUTY"
        val today = DateTimeUtils.getTodayKey()

        // High-precision local live trail (records tiny movements >= 3 meters cleanly)
        val localMovedMeters = if (lastLocalTrailTimeMs == 0L) Double.MAX_VALUE
        else GeoRouteUtils.distanceMeters(lastLocalTrailLat, lastLocalTrailLng, location.latitude, location.longitude)

        val acceptLocalPoint = when {
            overrideIsOnDuty != null -> true
            lastLocalTrailTimeMs == 0L -> location.accuracy <= 65f || prevFix == null
            location.accuracy <= 25f && localMovedMeters >= 3.0 -> true
            location.accuracy <= 45f && localMovedMeters >= 6.0 -> true
            else -> false
        }

        if (acceptLocalPoint && user.status == "APPROVED") {
            lastLocalTrailLat = location.latitude
            lastLocalTrailLng = location.longitude
            lastLocalTrailTimeMs = nowMs
            val pt = LocationHistoryPoint(
                officerId = user.uid,
                officerName = user.displayName.ifBlank { user.email },
                oicStationCode = user.oicStationCode ?: "",
                dateKey = today,
                dayStationKey = "${user.oicStationCode ?: ""}_$today",
                officerDayKey = "${user.uid}_$today",
                lat = location.latitude,
                lng = location.longitude,
                accuracy = location.accuracy,
                dutyType = dutyTypeStr,
                timestamp = nowMs
            )
            val existingToday = _liveLocalTrail.value.filter { it.dateKey == today && it.officerId == user.uid }
            _liveLocalTrail.value = (existingToday + pt).takeLast(400)
        }

        if (!persistToFirestore || user.status != "APPROVED") return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (user.role == "OFFICER") {
                    val isExplicitOffDuty = overrideIsOnDuty == false
                    val distFromLastSaved = if (lastBreadcrumbTimeMs == 0L) Double.MAX_VALUE
                    else GeoRouteUtils.distanceMeters(lastBreadcrumbLat, lastBreadcrumbLng, location.latitude, location.longitude)
                    val shouldUpdateUserPin = isExplicitOffDuty ||
                        overrideIsOnDuty != null ||
                        distFromLastSaved >= 4.0 ||
                        (nowMs - lastBreadcrumbTimeMs) >= 15_000L
                    if (shouldUpdateUserPin) {
                        repository.updateLastLocation(
                            uid = user.uid,
                            lat = location.latitude,
                            lng = location.longitude,
                            isOnDuty = latestDuty,
                            recordOffDutySpot = isExplicitOffDuty
                        )
                    }
                }

                // Record cloud GPS trail breadcrumb (5m fine movement or 45s while On Duty / OIC)
                val movedMeters = if (lastBreadcrumbTimeMs == 0L) Double.MAX_VALUE
                else GeoRouteUtils.distanceMeters(lastBreadcrumbLat, lastBreadcrumbLng, location.latitude, location.longitude)
                val throttleElapsed = (nowMs - lastBreadcrumbTimeMs) >= 4_500L
                val isAccurateForTrail = location.accuracy <= 45f || overrideIsOnDuty != null
                val shouldLogBreadcrumb = overrideIsOnDuty != null || (
                    isAccurateForTrail && throttleElapsed &&
                        (latestDuty || user.role == "OIC") &&
                        (movedMeters >= 5.0 || (nowMs - lastBreadcrumbTimeMs) >= 45_000L || lastBreadcrumbDutyType != dutyTypeStr)
                    )

                if (shouldLogBreadcrumb) {
                    lastBreadcrumbLat = location.latitude
                    lastBreadcrumbLng = location.longitude
                    lastBreadcrumbTimeMs = nowMs
                    lastBreadcrumbDutyType = dutyTypeStr
                    repository.addLocationBreadcrumb(
                        LocationHistoryPoint(
                            officerId = user.uid,
                            officerName = user.displayName.ifBlank { user.email },
                            oicStationCode = user.oicStationCode ?: "",
                            dateKey = today,
                            dayStationKey = "${user.oicStationCode ?: ""}_$today",
                            officerDayKey = "${user.uid}_$today",
                            lat = location.latitude,
                            lng = location.longitude,
                            accuracy = location.accuracy,
                            dutyType = dutyTypeStr,
                            timestamp = nowMs
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w("PoliceViewModel", "Error syncing GPS point", e)
            }
        }
    }

    private fun startRealtimeLocationUpdates() {
        if (!hasLocationPermission()) return

        if (activeLocationCallback == null) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500L)
                .setMinUpdateIntervalMillis(1500L)
                .setMinUpdateDistanceMeters(2.0f)
                .setWaitForAccurateLocation(false)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val best = result.locations
                        .filter { it.latitude != 0.0 || it.longitude != 0.0 }
                        .minByOrNull { it.accuracy }
                        ?: result.lastLocation
                        ?: return
                    processIncomingLocation(best, overrideIsOnDuty = null, persistToFirestore = true)
                }
            }

            try {
                fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                activeLocationCallback = callback
            } catch (e: SecurityException) {
                Log.w("PoliceViewModel", "Location permission missing when starting real-time updates", e)
            } catch (e: Exception) {
                Log.w("PoliceViewModel", "Could not start real-time location updates", e)
            }
        }

        // Parallel hardware GPS_PROVIDER listener for devices/emulators where Fused lags
        if (activePlatformLocationListener == null) {
            val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasFine) {
                val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                if (lm != null && lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                    val listener = android.location.LocationListener { loc ->
                        processIncomingLocation(loc, overrideIsOnDuty = null, persistToFirestore = true)
                    }
                    try {
                        lm.requestLocationUpdates(
                            android.location.LocationManager.GPS_PROVIDER,
                            2000L,
                            1.5f,
                            listener,
                            Looper.getMainLooper()
                        )
                        activePlatformLocationListener = listener
                    } catch (e: Exception) {
                        Log.w("PoliceViewModel", "Hardware GPS_PROVIDER listener unavailable", e)
                    }
                }
            }
        }
    }

    private fun stopRealtimeLocationUpdates() {
        activeLocationCallback?.let { cb ->
            try {
                fusedLocationClient.removeLocationUpdates(cb)
            } catch (_: Exception) {
                // ignore
            }
        }
        activeLocationCallback = null

        activePlatformLocationListener?.let { listener ->
            try {
                val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                lm?.removeUpdates(listener)
            } catch (_: Exception) {
                // ignore
            }
        }
        activePlatformLocationListener = null
    }

    fun refreshCurrentLocation(showFeedback: Boolean = false, overrideIsOnDuty: Boolean? = null) {
        val user = _appUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (!hasLocationPermission()) {
                if (showFeedback) {
                    _userMessage.emit("GPS අවසරය ලබාදී නොමැත.")
                }
                return@launch
            }

            // Ensure continuous real-time GPS listener is active once permission is granted
            withContext(Dispatchers.Main) {
                startRealtimeLocationUpdates()
            }

            try {
                val finalLoc = obtainFreshLocation()
                if (finalLoc != null) {
                    processIncomingLocation(finalLoc, overrideIsOnDuty = overrideIsOnDuty, persistToFirestore = true)
                    if (showFeedback) {
                        _userMessage.emit(
                            "GPS ස්ථානය යාවත්කාලීන විය (±${finalLoc.accuracy.toInt()}m • ${String.format("%.5f", finalLoc.latitude)}, ${String.format("%.5f", finalLoc.longitude)})"
                        )
                    }
                } else if (showFeedback) {
                    _userMessage.emit("GPS ස්ථානය ලබා ගැනීමට නොහැකි විය. කරුණාකර දුරකථනයේ Location (GPS) සක්‍රිය කර ඇත්දැයි බලන්න.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendEmergencySos(
        message: String,
        radiusKm: Double = 10.0,
        onSuccess: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        val user = _appUser.value
        val uid = auth.currentUser?.uid ?: user?.uid
        if (user == null || uid.isNullOrBlank()) {
            onError()
            return
        }
        viewModelScope.launch {
            try {
                val fresh = withContext(Dispatchers.IO) { obtainFreshLocation() }
                val fallback = GeoRouteUtils.extractLatLng(user.lastLocation)
                val cachedPair = _myCurrentLocation.value
                val lat = fresh?.latitude ?: cachedPair?.first ?: fallback?.first ?: 0.0
                val lng = fresh?.longitude ?: cachedPair?.second ?: fallback?.second ?: 0.0

                if (lat == 0.0 && lng == 0.0) {
                    _userMessage.emit("GPS ස්ථානය ලබාගත නොහැකි විය. කරුණාකර Location (GPS) සක්‍රිය කර නැවත උත්සාහ කරන්න.")
                    onError()
                    return@launch
                }

                _myCurrentLocation.value = Pair(lat, lng)
                if (user.role == "OFFICER") {
                    repository.updateLastLocation(uid, lat, lng, user.isOnDuty)
                }

                val alert = EmergencyAlert(
                    senderUid = uid,
                    senderName = user.displayName.ifBlank { user.email },
                    senderRank = user.rank,
                    senderBadge = user.badgeNumber,
                    senderRole = user.role,
                    senderPhone = user.phoneNumber,
                    oicStationCode = user.oicStationCode ?: "",
                    message = message.trim(),
                    lat = lat,
                    lng = lng,
                    radiusKm = radiusKm,
                    status = "ACTIVE",
                    createdAtMs = System.currentTimeMillis()
                )
                val res = repository.sendEmergencyAlert(alert)
                if (res.isSuccess) {
                    _userMessage.emit("🚨 හදිසි GPS පණිවිඩය ආසන්නයේ සිටින සියලු දෙනාට යවන ලදී!")
                    onSuccess()
                } else {
                    _userMessage.emit("හදිසි පණිවිඩය යැවීම අසාර්ථක විය: ${res.exceptionOrNull()?.localizedMessage ?: ""}")
                    onError()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Failed to send Emergency SOS", e)
                _userMessage.emit("හදිසි පණිවිඩය යැවීම අසාර්ථක විය.")
                onError()
            }
        }
    }

    fun resolveEmergencyAlert(alertId: String) {
        viewModelScope.launch {
            val res = repository.resolveEmergencyAlert(alertId)
            if (res.isSuccess) {
                if (_activeEmergencyPopup.value?.id == alertId) {
                    _activeEmergencyPopup.value = null
                }
                if (_activeRouteState.value?.target?.alertId == alertId) {
                    _activeRouteState.value = null
                }
                _userMessage.emit("හදිසි අවස්ථාව අවසන් ලෙස සටහන් කරන ලදී.")
            } else {
                _userMessage.emit("අවසන් කිරීම අසාර්ථක විය.")
            }
        }
    }

    fun navigateToLocationOnMap(
        target: MapNavigationTarget,
        openDialog: Boolean = true
    ) {
        if (target.lat == 0.0 && target.lng == 0.0) return
        if (openDialog) {
            _isRouteMapDialogOpen.value = true
        }
        viewModelScope.launch {
            val user = _appUser.value
            val cached = _myCurrentLocation.value
            val savedFallback = GeoRouteUtils.extractLatLng(user?.lastLocation)
            val initOriginLat = cached?.first ?: savedFallback?.first ?: 0.0
            val initOriginLng = cached?.second ?: savedFallback?.second ?: 0.0

            _activeRouteState.value = ActiveRouteState(
                target = target,
                originLat = initOriginLat,
                originLng = initOriginLng,
                isLoading = true
            )

            val fresh = withContext(Dispatchers.IO) { obtainFreshLocation() }
            val originLat = fresh?.latitude ?: initOriginLat
            val originLng = fresh?.longitude ?: initOriginLng

            val route = if (originLat != 0.0 || originLng != 0.0) {
                GeoRouteUtils.fetchDrivingRoute(originLat, originLng, target.lat, target.lng)
            } else {
                GeoRouteUtils.fetchDrivingRoute(target.lat, target.lng, target.lat, target.lng)
            }

            _activeRouteState.value = ActiveRouteState(
                target = target,
                originLat = originLat,
                originLng = originLng,
                routePoints = route.points,
                distanceMeters = route.distanceMeters,
                durationSeconds = route.durationSeconds,
                isFallbackDirect = route.isFallbackDirect,
                isLoading = false
            )
        }
    }

    fun openEmergencyRouteFromNotification(
        alertId: String,
        lat: Double,
        lng: Double,
        title: String,
        message: String,
        phone: String
    ) {
        if (lat == 0.0 && lng == 0.0) return
        navigateToLocationOnMap(
            target = MapNavigationTarget(
                alertId = alertId,
                title = title.ifBlank { "🚨 හදිසි SOS ස්ථානය" },
                subtitle = message,
                lat = lat,
                lng = lng,
                phone = phone
            ),
            openDialog = true
        )
    }

    private fun updateLocationTracking(user: AppUser) {
        val newKey = if (user.status == "APPROVED") {
            "${user.uid}:${user.role}:${user.status}:${user.isOnDuty}"
        } else {
            null
        }
        if (newKey == activeTrackingKey) return
        activeTrackingKey = newKey
        locationJob?.cancel()
        locationJob = null
        stopRealtimeLocationUpdates()
        if (newKey != null) {
            startRealtimeLocationUpdates()
            locationJob = viewModelScope.launch(Dispatchers.IO) {
                refreshCurrentLocation()
                while (isActive && _appUser.value?.status == "APPROVED") {
                    delay(12_000L) // Backup heartbeat every 12s alongside real-time 2.5s LocationCallback
                    if (hasLocationPermission() && activeLocationCallback == null) {
                        withContext(Dispatchers.Main) {
                            startRealtimeLocationUpdates()
                        }
                    }
                    if ((System.currentTimeMillis() - lastLocationFixTimeMs) >= 12_000L) {
                        refreshCurrentLocation()
                    }
                }
            }
        }
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        return StationContactUtils.isValidPhoneNumber(phone)
    }

    fun registerOfficer(
        officerName: String,
        badgeNumber: String = "",
        rank: String = "",
        stationName: String = "",
        phoneNumber: String = "",
        address: String = "",
        stationCode: String,
        onError: () -> Unit = {}
    ) {
        val user = auth.currentUser
        if (user == null) {
            onError()
            return
        }
        val code = stationCode.trim().uppercase()
        val cleanPhone = phoneNumber.trim()
        if (!isValidPhoneNumber(cleanPhone)) {
            viewModelScope.launch {
                _userMessage.emit("ලියාපදිංචි වීම සඳහා වලංගු දුරකථන අංකයක් (අවම ඉලක්කම් 9) ඇතුළත් කිරීම අනිවාර්ය වේ.")
            }
            onError()
            return
        }
        val cleanAddress = address.trim()
        val cleanStation = stationName.trim()
        viewModelScope.launch {
            try {
                // Ensure brand-new registration starts with zero active team duty and clean filters
                _activeTeamDutyId.value = ""
                prefs.edit()
                    .remove("active_team_duty_id")
                    .remove(teamDutyPrefKey(user.uid))
                    .apply()
                val appUser = AppUser(
                    uid = user.uid,
                    displayName = officerName.trim(),
                    email = user.email ?: "",
                    role = "OFFICER",
                    status = "PENDING",
                    stationName = cleanStation,
                    badgeNumber = badgeNumber.trim(),
                    rank = rank.trim(),
                    phoneNumber = cleanPhone,
                    address = cleanAddress,
                    oicStationCode = code,
                    isOnDuty = false
                )
                repository.saveUser(appUser)
                repository.saveOfficerProfile(
                    OfficerProfile(
                        name = officerName.trim(),
                        rank = rank.ifBlank { "Police Constable" },
                        badgeNumber = badgeNumber.trim(),
                        station = cleanStation.ifBlank { code },
                        phoneNumber = cleanPhone,
                        address = cleanAddress
                    )
                )
                _appUser.value = appUser
                cacheAppUser(appUser)
                _isRegistered.value = true
                prefs.edit().putBoolean("is_registered", true).apply()
                _userMessage.emit("ලියාපදිංචි වීමේ ඉල්ලීම OIC වෙත යොමු කරන ලදී ($code).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Officer registration failed", e)
                _userMessage.emit("ලියාපදිංචිය අසාර්ථක විය: ${e.localizedMessage ?: "කරුණාකර නැවත උත්සාහ කරන්න"}")
                onError()
            }
        }
    }

    fun registerOIC(
        oicName: String,
        stationName: String = "",
        customCode: String = "",
        phoneNumber: String = "",
        badgeNumber: String = "",
        rank: String = "OIC (ස්ථානාධිපති)",
        onError: () -> Unit = {}
    ) {
        val user = auth.currentUser
        if (user == null) {
            onError()
            return
        }
        val cleanPhone = phoneNumber.trim()
        if (!isValidPhoneNumber(cleanPhone)) {
            viewModelScope.launch {
                _userMessage.emit("OIC ලියාපදිංචිය සඳහා වලංගු දුරකථන අංකයක් (අවම ඉලක්කම් 9) ඇතුළත් කිරීම අනිවාර්ය වේ.")
            }
            onError()
            return
        }
        val finalCode = if (customCode.isNotBlank()) customCode.trim().uppercase() else generateSuggestedOicCode(stationName)
        val cleanRank = rank.trim().ifBlank { "OIC (ස්ථානාධිපති)" }
        viewModelScope.launch {
            try {
                _activeTeamDutyId.value = ""
                prefs.edit()
                    .remove("active_team_duty_id")
                    .remove(teamDutyPrefKey(user.uid))
                    .apply()
                val appUser = AppUser(
                    uid = user.uid,
                    displayName = oicName.trim(),
                    email = user.email ?: "",
                    role = "OIC",
                    status = "APPROVED",
                    stationName = stationName.trim(),
                    badgeNumber = badgeNumber.trim(),
                    rank = cleanRank,
                    phoneNumber = cleanPhone,
                    oicStationCode = finalCode
                )
                repository.saveUser(appUser)
                _appUser.value = appUser
                cacheAppUser(appUser)
                _isOICLoggedIn.value = true
                _isRegistered.value = true
                prefs.edit()
                    .putBoolean("is_registered", true)
                    .putBoolean("is_oic_logged_in", true)
                    .apply()
                _userMessage.emit("OIC ලෙස සාර්ථකව ලියාපදිංචි විය. ඔබගේ OIC කේතය: $finalCode")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "OIC registration failed", e)
                _userMessage.emit("OIC ලියාපදිංචිය අසාර්ථක විය: ${e.localizedMessage ?: "කරුණාකර නැවත උත්සාහ කරන්න"}")
                onError()
            }
        }
    }

    fun registerAdminBranch(
        adminName: String,
        stationName: String = "",
        stationCode: String,
        phoneNumber: String = "",
        badgeNumber: String = "",
        adminPinSeed: String = "",
        onError: () -> Unit = {}
    ) {
        val user = auth.currentUser
        if (user == null) {
            onError()
            return
        }
        val cleanPhone = phoneNumber.trim()
        if (!isValidPhoneNumber(cleanPhone)) {
            viewModelScope.launch {
                _userMessage.emit("පාලන අංශ ලියාපදිංචිය සඳහා වලංගු දුරකථන අංකයක් (අවම ඉලක්කම් 9) ඇතුළත් කිරීම අනිවාර්ය වේ.")
            }
            onError()
            return
        }
        val cleanSeed = adminPinSeed.trim()
        if (cleanSeed.length != 6 || !cleanSeed.all { it.isDigit() }) {
            viewModelScope.launch {
                _userMessage.emit("කරුණාකර පාලන අංශය සඳහා ඉලක්කම් 6 ක රහස් මුරපදයක් ඇතුළත් කරන්න.")
            }
            onError()
            return
        }
        val code = stationCode.trim().uppercase()
        val cleanStation = stationName.trim()
        viewModelScope.launch {
            try {
                _activeTeamDutyId.value = ""
                _isAdminPasscodeUnlockedToday.value = false
                prefs.edit()
                    .remove("active_team_duty_id")
                    .remove(teamDutyPrefKey(user.uid))
                    .remove(adminUnlockedPrefKey(user.uid))
                    .apply()
                val appUser = AppUser(
                    uid = user.uid,
                    displayName = adminName.trim(),
                    email = user.email ?: "",
                    role = "ADMIN_BRANCH",
                    status = "PENDING",
                    stationName = cleanStation,
                    badgeNumber = badgeNumber.trim(),
                    rank = "Admin Branch (පාලන අංශය)",
                    phoneNumber = cleanPhone,
                    address = "",
                    oicStationCode = code,
                    isOnDuty = false,
                    adminSecretSeed = cleanSeed
                )
                repository.saveUser(appUser)
                _appUser.value = appUser
                cacheAppUser(appUser)
                _isRegistered.value = true
                prefs.edit().putBoolean("is_registered", true).apply()
                _userMessage.emit("පාලන අංශ ලියාපදිංචි ඉල්ලීම OIC වෙත යොමු කරන ලදී ($code).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Admin branch registration failed", e)
                _userMessage.emit("ලියාපදිංචිය අසාර්ථක විය: ${e.localizedMessage ?: "නැවත උත්සාහ කරන්න"}")
                onError()
            }
        }
    }

    fun updateOwnContactInfo(
        phoneNumber: String,
        badgeNumber: String,
        rank: String = "",
        onSuccess: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        val current = _appUser.value
        val uid = auth.currentUser?.uid ?: current?.uid
        if (uid.isNullOrBlank()) {
            onError()
            return
        }
        val cleanPhone = phoneNumber.trim()
        if (!isValidPhoneNumber(cleanPhone)) {
            viewModelScope.launch {
                _userMessage.emit("කරුණාකර වලංගු දුරකථන අංකයක් (අවම ඉලක්කම් 9) ඇතුළත් කරන්න.")
            }
            onError()
            return
        }
        val cleanBadge = badgeNumber.trim()
        val cleanRank = rank.trim()
        viewModelScope.launch {
            try {
                repository.updateOwnContactInfo(
                    uid = uid,
                    phoneNumber = cleanPhone,
                    badgeNumber = cleanBadge,
                    rank = cleanRank
                )
                if (current != null) {
                    val updated = current.copy(
                        phoneNumber = cleanPhone,
                        badgeNumber = cleanBadge,
                        rank = if (cleanRank.isNotBlank()) cleanRank else current.rank
                    )
                    _appUser.value = updated
                    cacheAppUser(updated)
                }
                _userMessage.emit("ඔබගේ දුරකථන හා නිල තොරතුරු සාර්ථකව යාවත්කාලීන විය.")
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Failed to update own contact info", e)
                _userMessage.emit("තොරතුරු යාවත්කාලීන කිරීම අසාර්ථක විය: ${e.localizedMessage ?: ""}")
                onError()
            }
        }
    }

    private fun resolveEffectiveAdminSeed(): String {
        val fromOwn = _appUser.value?.adminSecretSeed?.takeIf { it.isNotBlank() }
        val fromStation = stationAdminSeed.value.takeIf { it.isNotBlank() }
        val fromSubordinates = subordinates.value
            .firstOrNull { it.role == "ADMIN_BRANCH" && it.adminSecretSeed.isNotBlank() }
            ?.adminSecretSeed
        return fromOwn ?: fromStation ?: fromSubordinates ?: ""
    }

    fun unlockAdminPasscodeToday(enteredPin: String): Boolean {
        val user = _appUser.value ?: return false
        val stationCode = user.oicStationCode ?: ""
        val today = DateTimeUtils.getTodayKey()
        val seed = resolveEffectiveAdminSeed()
        val isValid = com.example.util.AdminPasscodeUtils.isValidDailyAdminPin(stationCode, enteredPin, today, seed)
        if (isValid) {
            _isAdminPasscodeUnlockedToday.value = true
            prefs.edit().putString(adminUnlockedPrefKey(user.uid), today).apply()
        }
        return isValid
    }

    fun verifyAdminDailyPasscode(enteredPin: String): Boolean {
        val user = _appUser.value ?: return false
        val stationCode = user.oicStationCode ?: ""
        val today = DateTimeUtils.getTodayKey()
        val seed = resolveEffectiveAdminSeed()
        return com.example.util.AdminPasscodeUtils.isValidDailyAdminPin(stationCode, enteredPin, today, seed)
    }

    fun getTodayAdminPasscode(customStationCode: String? = null, customSeed: String? = null): String {
        val code = customStationCode?.takeIf { it.isNotBlank() } ?: _appUser.value?.oicStationCode ?: ""
        val seed = customSeed?.takeIf { it.isNotBlank() } ?: resolveEffectiveAdminSeed()
        return com.example.util.AdminPasscodeUtils.generateDailyAdminPin(code, DateTimeUtils.getTodayKey(), seed)
    }

    fun assignDutyToOfficer(
        officer: AppUser,
        dateKey: String,
        dutyNature: String,
        shift: String,
        remarks: String,
        existingAssignmentId: String = "",
        onSuccess: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        val user = _appUser.value ?: return
        val stationCode = user.oicStationCode ?: ""
        if (stationCode.isBlank() || officer.uid.isBlank()) {
            onError()
            return
        }
        val cleanNature = dutyNature.trim().ifBlank { "General Duty" }
        val slotCategory = DutySlotRules.getSingleSlotCategory(cleanNature)

        viewModelScope.launch {
            try {
                if (slotCategory != null) {
                    val existing = stationDutyAssignments.value.firstOrNull {
                        it.dateKey == dateKey.trim() &&
                            DutySlotRules.getSingleSlotCategory(it.dutyNature) == slotCategory &&
                            it.officerId != officer.uid
                    }
                    if (existing != null) {
                        val slotTitle = DutySlotRules.getCategoryDisplayName(slotCategory)
                        _userMessage.emit("අවවාදයයි: $dateKey දින සඳහා දැනටමත් [${existing.officerName}] $slotTitle රාජකාරියට පත් කර ඇත! එක් පොලිසියකට මෙම රාජකාරිය සඳහා එක් නිලධාරියෙකු පමණක් සිටිය හැක.")
                        onError()
                        return@launch
                    }
                }

                val targetId = existingAssignmentId.ifBlank {
                    stationDutyAssignments.value.firstOrNull {
                        it.officerId == officer.uid && it.dateKey == dateKey.trim()
                    }?.id.orEmpty()
                }

                val assignment = DutyAssignment(
                    id = targetId,
                    officerId = officer.uid,
                    officerName = officer.displayName.ifBlank { officer.badgeNumber },
                    officerBadge = officer.badgeNumber,
                    officerRank = officer.rank,
                    officerPhone = officer.phoneNumber,
                    oicStationCode = stationCode,
                    dateKey = dateKey.trim(),
                    dutyNature = cleanNature,
                    shift = shift.trim(),
                    remarks = remarks.trim(),
                    assignedByUid = user.uid,
                    assignedByName = user.displayName.ifBlank { "පාලන අංශය" },
                    status = if (targetId.isNotBlank()) "MODIFIED" else "ASSIGNED"
                )
                val res = repository.saveDutyAssignment(assignment)
                if (res.isSuccess) {
                    val remarksSuffix = if (remarks.isNotBlank()) "\nවිශේෂ උපදෙස්: ${remarks.trim()}" else ""
                    sendNotification(
                        title = "රාජකාරි පැවරීම: $cleanNature",
                        message = "ඔබව $dateKey දින [$cleanNature] ($shift) රාජකාරිය සඳහා පාලන අංශය මගින් යොදවා ඇත.$remarksSuffix",
                        targetType = "USER",
                        targetUid = officer.uid,
                        priority = "URGENT"
                    )
                    _userMessage.emit("${officer.displayName} වෙත රාජකාරිය සාර්ථකව පවරන ලදී!")
                    onSuccess()
                } else {
                    _userMessage.emit("රාජකාරිය පැවරීම අසාර්ථක විය: ${res.exceptionOrNull()?.localizedMessage ?: ""}")
                    onError()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Error assigning duty", e)
                _userMessage.emit("රාජකාරිය පැවරීම අසාර්ථක විය.")
                onError()
            }
        }
    }

    fun deleteDutyAssignment(assignmentId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.deleteDutyAssignment(assignmentId)
            if (res.isSuccess) {
                _userMessage.emit("පැවරූ රාජකාරිය ඉවත් කරන ලදී.")
                onSuccess()
            } else {
                _userMessage.emit("ඉවත් කිරීම අසාර්ථක විය.")
            }
        }
    }

    fun generateSuggestedOicCode(station: String): String {
        val clean = station.trim().uppercase().replace("[^A-Z]".toRegex(), "").take(4)
        val rand = (1000..9999).random()
        return if (clean.isNotBlank()) "OIC-$clean-$rand" else "OIC-$rand"
    }

    fun approveOfficer(officerUid: String) {
        viewModelScope.launch {
            try {
                repository.updateOfficerStatus(officerUid, "APPROVED")
                _userMessage.emit("නිලධාරියා සාර්ථකව අනුමත කරන ලදී.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Approve officer failed", e)
                _userMessage.emit("අනුමත කිරීම අසාර්ථක විය: ${e.localizedMessage ?: ""}")
            }
        }
    }

    fun rejectOfficer(officerUid: String) {
        viewModelScope.launch {
            try {
                repository.updateOfficerStatus(officerUid, "REJECTED")
                _userMessage.emit("නිලධාරියා ප්‍රතික්ෂේප කරන ලදී.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Reject officer failed", e)
                _userMessage.emit("ප්‍රතික්ෂේප කිරීම අසාර්ථක විය: ${e.localizedMessage ?: ""}")
            }
        }
    }

    fun transferOfficerToNewOic(newStationCode: String) {
        val user = auth.currentUser ?: return
        val cleanCode = newStationCode.trim().uppercase()
        if (cleanCode.isBlank()) return
        viewModelScope.launch {
            try {
                repository.transferOfficer(user.uid, cleanCode, "PENDING")
                val current = _appUser.value
                if (current != null) {
                    val updated = current.copy(oicStationCode = cleanCode, status = "PENDING")
                    _appUser.value = updated
                    cacheAppUser(updated)
                    repository.saveOfficerProfile(
                        OfficerProfile(
                            name = updated.displayName,
                            rank = updated.rank.ifBlank { "Police Constable" },
                            badgeNumber = updated.badgeNumber,
                            station = cleanCode
                        )
                    )
                }
                _userMessage.emit("නව OIC වෙත මාරු විය ($cleanCode). අනුමැතිය සඳහා යොමු කරන ලදී.")
            } catch (e: Exception) {
                e.printStackTrace()
                _userMessage.emit("ස්ථාන මාරු කිරීම අසාර්ථක විය: ${e.localizedMessage}")
            }
        }
    }

    fun transferSubordinatesToNewOic(officerUids: List<String>, newStationCode: String) {
        val cleanCode = newStationCode.trim().uppercase()
        if (cleanCode.isBlank() || officerUids.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.transferSubordinatesBatch(officerUids, cleanCode, "APPROVED")
                _userMessage.emit("නිලධාරීන් ${officerUids.size} දෙනෙකු සාර්ථකව නව OIC ($cleanCode) වෙත මාරු කරන ලදී.")
            } catch (e: Exception) {
                e.printStackTrace()
                _userMessage.emit("නිලධාරීන් මාරු කිරීම අසාර්ථක විය: ${e.localizedMessage}")
            }
        }
    }

    fun transferOicToNewStation(newStationName: String, newStationCode: String) {
        val user = auth.currentUser ?: return
        val cleanCode = if (newStationCode.isNotBlank()) newStationCode.trim().uppercase() else generateSuggestedOicCode(newStationName)
        viewModelScope.launch {
            try {
                repository.updateOicStation(user.uid, newStationName.trim(), cleanCode)
                val current = _appUser.value
                if (current != null) {
                    val updated = current.copy(stationName = newStationName.trim(), oicStationCode = cleanCode)
                    _appUser.value = updated
                    cacheAppUser(updated)
                }
                _userMessage.emit("ස්ථානාධිපති තොරතුරු සාර්ථකව යාවත්කාලීන විය ($cleanCode - $newStationName).")
            } catch (e: Exception) {
                e.printStackTrace()
                _userMessage.emit("ස්ථානය යාවත්කාලීන කිරීම අසාර්ථක විය: ${e.localizedMessage}")
            }
        }
    }

    fun logout() {
        fetchUserJob?.cancel()
        fetchUserJob = null
        locationJob?.cancel()
        locationJob = null
        activeTrackingKey = null
        hydratedUid = null
        _activeTeamDutyId.value = ""
        _activeEmergencyPopup.value = null
        _activeRouteState.value = null
        _isRouteMapDialogOpen.value = false
        _searchQuery.value = ""
        _dutyFilter.value = DutyFilter.ALL
        _selectedVehicleFilter.value = "ALL"
        _selectedMessDate.value = DateTimeUtils.getTodayKey()
        baselineEmergencyUid = null
        baselineNotifUid = null
        alertedEmergencyIds.clear()
        alertedNotificationIds.clear()
        handledEndedTeamSessionIds.clear()
        lastBreadcrumbTimeMs = 0L
        lastBreadcrumbDutyType = ""
        _isAdminPasscodeUnlockedToday.value = false
        clearCachedAppUser()
        prefs.edit().putBoolean("explicit_logout", true).apply()
        _isRegistered.value = false
        _isOICLoggedIn.value = false
        _appUser.value = null
        _selectedOfficer.value = null
        _isProfileLoaded.value = true
        _authPortal.value = AuthPortal.CHOOSER
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    repository.clearLocalProfile()
                    clearGoogleCredentialState(getApplication())
                } finally {
                    auth.signOut()
                }
            }
        }
    }

    fun toggleDarkMode() {
        val next = when (_isDarkMode.value) {
            true -> false
            false -> true
            else -> true
        }
        _isDarkMode.value = next
        prefs.edit().putBoolean("is_dark_mode", next).commit()
    }

    fun setDarkMode(dark: Boolean?) {
        _isDarkMode.value = dark
        if (dark != null) {
            prefs.edit().putBoolean("is_dark_mode", dark).commit()
        } else {
            prefs.edit().remove("is_dark_mode").commit()
        }
    }

    // --- Officer Profile ---
    val officerProfile: StateFlow<OfficerProfile?> = repository.officerProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Duty Records & Filter ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _dutyFilter = MutableStateFlow(DutyFilter.ALL)
    val dutyFilter: StateFlow<DutyFilter> = _dutyFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val allDutyRecords: StateFlow<List<DutyRecord>> = activeOfficerUid
        .flatMapLatest { uid -> repository.getAllDutyRecords(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawDutyRecords: StateFlow<List<DutyRecord>> = combine(
        activeOfficerUid,
        _searchQuery
    ) { uid, query -> Pair(uid, query) }
        .flatMapLatest { (uid, query) -> repository.searchDutyRecords(uid, query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredDutyRecords: StateFlow<List<DutyRecord>> = combine(
        rawDutyRecords,
        _dutyFilter
    ) { records, filter ->
        when (filter) {
            DutyFilter.ALL -> records
            DutyFilter.ON_DUTY -> records.filter { it.type == "ON_DUTY" }
            DutyFilter.EXTRA_DUTY -> records.filter { it.type == "EXTRA_DUTY" }
            DutyFilter.OFF_DUTY -> records.filter { it.type == "OFF_DUTY" }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestDutyRecord: StateFlow<DutyRecord?> = activeOfficerUid
        .flatMapLatest { uid -> repository.getLatestDutyRecord(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val latestShiftRecord: StateFlow<DutyRecord?> = allDutyRecords.map { records ->
        records.firstOrNull { it.type == "ON_DUTY" || it.type == "OFF_DUTY" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayDutyMinutes: StateFlow<Long> = allDutyRecords.map { records ->
        calculateDutyMinutesForDate(records, DateTimeUtils.getTodayKey())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val todayDutyHoursFormatted: StateFlow<String> = todayDutyMinutes.map { mins ->
        formatHoursMinutesSinhala(mins)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0 පැය 0 මිනි")

    val monthlyDutyHoursFormatted: StateFlow<String> = allDutyRecords.map { records ->
        val monthKey = DateTimeUtils.getCurrentMonthKey()
        val monthRecords = records.filter {
            val dateKey = DateTimeUtils.formatDate(it.timestamp)
            dateKey.startsWith(monthKey)
        }
        val recordsByDay = monthRecords.groupBy { DateTimeUtils.formatDate(it.timestamp) }
        var totalMins = 0L
        for ((dayKey, dayRecs) in recordsByDay) {
            totalMins += calculateDutyMinutesForDate(dayRecs, dayKey)
        }
        formatHoursMinutesSinhala(totalMins)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0 පැය 0 මිනි")

    // --- Daily Duty Summaries (Grouped by Date) ---
    val dailyDutySummaries: StateFlow<List<DailyDutySummary>> = allDutyRecords.map { records ->
        val grouped = records.groupBy { DateTimeUtils.formatDate(it.timestamp) }
        grouped.map { (dateKey, dayRecords) ->
            val onDuty = dayRecords.firstOrNull { it.type == "ON_DUTY" }
            val offDuty = dayRecords.firstOrNull { it.type == "OFF_DUTY" }
            val extraDuties = dayRecords.filter { it.type == "EXTRA_DUTY" }
            val mins = calculateDutyMinutesForDate(dayRecords, dateKey)
            val photosCount = dayRecords.count { !it.photoPath.isNullOrBlank() } + dayRecords.count { !it.scenePhotoPath.isNullOrBlank() }
            val isOngoing = onDuty != null && offDuty == null && dateKey == DateTimeUtils.getTodayKey()
            DailyDutySummary(
                dateKey = dateKey,
                formattedDate = DateTimeUtils.formatSinhalaDisplayDate(dateKey),
                onDutyRecord = onDuty,
                offDutyRecord = offDuty,
                extraDutyRecords = extraDuties,
                allRecords = dayRecords,
                totalDutyMinutes = mins,
                totalHoursFormatted = formatHoursMinutesSinhala(mins),
                isOngoing = isOngoing,
                totalPhotosCount = photosCount
            )
        }.sortedByDescending { it.dateKey }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Mess Rates State ---
    private val _breakfastDefaultPrice = MutableStateFlow(150.0)
    val breakfastDefaultPrice: StateFlow<Double> = _breakfastDefaultPrice.asStateFlow()

    private val _lunchDefaultPrice = MutableStateFlow(220.0)
    val lunchDefaultPrice: StateFlow<Double> = _lunchDefaultPrice.asStateFlow()

    private val _dinnerDefaultPrice = MutableStateFlow(180.0)
    val dinnerDefaultPrice: StateFlow<Double> = _dinnerDefaultPrice.asStateFlow()

    fun updateDefaultMealPrices(bPrice: Double, lPrice: Double, dPrice: Double) {
        _breakfastDefaultPrice.value = maxOf(0.0, bPrice)
        _lunchDefaultPrice.value = maxOf(0.0, lPrice)
        _dinnerDefaultPrice.value = maxOf(0.0, dPrice)
        viewModelScope.launch {
            _userMessage.emit("ආහාර වල පෙරනිමි මිල ගණන් යාවත්කාලීන විය!")
        }
    }

    fun getDefaultPriceForType(mealType: String): Double {
        return when (mealType) {
            "BREAKFAST" -> _breakfastDefaultPrice.value
            "LUNCH" -> _lunchDefaultPrice.value
            "DINNER" -> _dinnerDefaultPrice.value
            else -> 180.0
        }
    }

    // --- Mess Records ---
    private val _selectedMessDate = MutableStateFlow(DateTimeUtils.getTodayKey())
    val selectedMessDate: StateFlow<String> = _selectedMessDate.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDayMeals: StateFlow<List<MessRecord>> = combine(
        activeOfficerUid,
        _selectedMessDate
    ) { uid, date -> Pair(uid, date) }
        .flatMapLatest { (uid, date) -> repository.getMessRecordsForDate(uid, date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val currentMonthKey = DateTimeUtils.getCurrentMonthKey()
    @OptIn(ExperimentalCoroutinesApi::class)
    val monthlyMessRecords: StateFlow<List<MessRecord>> = activeOfficerUid
        .flatMapLatest { uid -> repository.getMessRecordsForMonth(uid, currentMonthKey) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allMessRecords: StateFlow<List<MessRecord>> = activeOfficerUid
        .flatMapLatest { uid -> repository.getAllMessRecords(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlySummary: StateFlow<MonthlyMessSummary> = combine(
        monthlyMessRecords,
        allMessRecords,
        combine(_breakfastDefaultPrice, _lunchDefaultPrice, _dinnerDefaultPrice) { b, l, d -> Triple(b, l, d) }
    ) { monthRecords: List<MessRecord>, allRecords: List<MessRecord>, prices: Triple<Double, Double, Double> ->
        val (bPrice, lPrice, dPrice) = prices
        fun getRecordPrice(r: MessRecord): Double {
            return if (r.price > 0.0) r.price else when (r.mealType) {
                "BREAKFAST" -> bPrice
                "LUNCH" -> lPrice
                "DINNER" -> dPrice
                else -> 180.0
            }
        }

        val monthTaken = monthRecords.filter { it.isTaken }
        val bList = monthTaken.filter { it.mealType == "BREAKFAST" }
        val lList = monthTaken.filter { it.mealType == "LUNCH" }
        val dList = monthTaken.filter { it.mealType == "DINNER" }

        val bCost = bList.sumOf { getRecordPrice(it) }
        val lCost = lList.sumOf { getRecordPrice(it) }
        val dCost = dList.sumOf { getRecordPrice(it) }
        val monthTotalCost = bCost + lCost + dCost

        val todayKey = DateTimeUtils.getTodayKey()
        val todayTaken = allRecords.filter { it.date == todayKey && it.isTaken }
        val todayCost = todayTaken.sumOf { getRecordPrice(it) }

        val allTaken = allRecords.filter { it.isTaken }
        val allTimeCost = allTaken.sumOf { getRecordPrice(it) }

        MonthlyMessSummary(
            breakfastCount = bList.size,
            lunchCount = lList.size,
            dinnerCount = dList.size,
            totalMeals = monthTaken.size,
            breakfastCost = bCost,
            lunchCost = lCost,
            dinnerCost = dCost,
            monthlyTotalCost = monthTotalCost,
            todayTotalCost = todayCost,
            allTimeTotalCost = allTimeCost,
            allTimeTotalMeals = allTaken.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthlyMessSummary())

    val dailyMessSummaries: StateFlow<List<DailyMessSummary>> = allMessRecords.map { records ->
        val grouped = records.groupBy { it.date }
        grouped.map { (dateKey, dayRecords) ->
            val b = dayRecords.firstOrNull { it.mealType == "BREAKFAST" }
            val l = dayRecords.firstOrNull { it.mealType == "LUNCH" }
            val d = dayRecords.firstOrNull { it.mealType == "DINNER" }
            val takenCount = dayRecords.count { it.isTaken }
            val photosCount = dayRecords.count { !it.photoPath.isNullOrBlank() }
            DailyMessSummary(
                dateKey = dateKey,
                formattedDate = DateTimeUtils.formatSinhalaDisplayDate(dateKey),
                breakfast = b,
                lunch = l,
                dinner = d,
                allRecords = dayRecords,
                takenCount = takenCount,
                totalPhotosCount = photosCount,
                isCurrentDay = dateKey == DateTimeUtils.getTodayKey()
            )
        }.sortedByDescending { it.dateKey }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Storage Stats (Per Logged-In Officer) ---
    val storageStats: StateFlow<StorageStats> = combine(
        allDutyRecords,
        allMessRecords
    ) { duties, meals ->
        var origKb = 0
        var compKb = 0
        var count = 0

        for (d in duties) {
            if (d.photoPath != null) {
                count++
                origKb += if (d.originalSizeKb > 0) d.originalSizeKb else d.compressedSizeKb
                compKb += d.compressedSizeKb
            }
            if (d.scenePhotoPath != null) {
                count++
                origKb += if (d.originalSizeKb > 0) d.originalSizeKb else d.compressedSizeKb
                compKb += d.compressedSizeKb
            }
        }
        for (m in meals) {
            if (m.photoPath != null) {
                count++
                origKb += (m.compressedSizeKb * 4)
                compKb += m.compressedSizeKb
            }
        }
        val saved = if (origKb > compKb) origKb - compKb else 0
        StorageStats(
            totalOriginalKb = origKb,
            totalCompressedKb = compKb,
            totalSavedKb = saved,
            totalPhotos = count
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StorageStats())

    // --- Running Chart State (Per Logged-In Officer) ---
    private val _selectedVehicleFilter = MutableStateFlow("ALL")
    val selectedVehicleFilter: StateFlow<String> = _selectedVehicleFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val distinctVehicles: StateFlow<List<String>> = activeOfficerUid
        .flatMapLatest { uid -> repository.getDistinctVehicles(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allRunningChartRecords: StateFlow<List<RunningChartRecord>> = activeOfficerUid
        .flatMapLatest { uid -> repository.getAllRunningChartRecords(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val runningChartRecords: StateFlow<List<RunningChartRecord>> = combine(
        activeOfficerUid,
        _selectedVehicleFilter
    ) { uid, vehicle -> Pair(uid, vehicle) }
        .flatMapLatest { (uid, vehicle) -> repository.getRunningChartRecordsByVehicle(uid, vehicle) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Police Contacts & Directory State ---
    private val _rawContacts = MutableStateFlow<List<PoliceContact>>(emptyList())
    private val _contactSearchQuery = MutableStateFlow("")
    val contactSearchQuery: StateFlow<String> = _contactSearchQuery.asStateFlow()

    private val _contactFilter = MutableStateFlow("ALL")
    val contactFilter: StateFlow<String> = _contactFilter.asStateFlow()

    private val _isContactsLoading = MutableStateFlow(false)
    val isContactsLoading: StateFlow<Boolean> = _isContactsLoading.asStateFlow()

    val filteredContacts: StateFlow<List<PoliceContact>> = combine(
        _rawContacts,
        _contactSearchQuery,
        _contactFilter
    ) { contacts, query, filter ->
        val q = query.trim().lowercase()
        contacts.filter { contact ->
            val matchesQuery = q.isEmpty() ||
                contact.designation.lowercase().contains(q) ||
                contact.name.lowercase().contains(q) ||
                contact.rank.lowercase().contains(q) ||
                contact.locationAddress.lowercase().contains(q) ||
                contact.officeNo.contains(q) ||
                contact.mobileNo.contains(q) ||
                contact.email.lowercase().contains(q) ||
                contact.oicTraffic.contains(q) ||
                contact.oicCrime.contains(q)

            val matchesFilter = when (filter) {
                "ALL" -> true
                "POLICE" -> contact.designation.contains("Police", ignoreCase = true) || contact.rank.isNotBlank()
                "EMERGENCY" -> contact.designation.contains("Emergency", ignoreCase = true) || contact.oicCrime.isNotBlank()
                "HQ" -> contact.designation.contains("HQ", ignoreCase = true) || contact.designation.contains("Division", ignoreCase = true) || contact.rank.contains("DIG", ignoreCase = true)
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Feedback Messages
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    // --- Actions ---

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setDutyFilter(filter: DutyFilter) {
        _dutyFilter.value = filter
    }

    fun selectMessDate(date: String) {
        _selectedMessDate.value = date
    }

    fun setVehicleFilter(vehicle: String) {
        _selectedVehicleFilter.value = vehicle
    }

    fun setContactSearchQuery(query: String) {
        _contactSearchQuery.value = query
    }

    fun setContactFilter(filter: String) {
        _contactFilter.value = filter
    }

    fun saveOfficerProfile(
        name: String,
        rank: String,
        badgeNumber: String,
        station: String,
        phoneNumber: String = "",
        address: String = "",
        onSuccess: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val cleanName = name.trim()
                val cleanRank = rank.trim()
                val cleanBadge = badgeNumber.trim()
                val cleanStation = station.trim()
                val cleanPhone = phoneNumber.trim()
                val cleanAddress = address.trim()

                val profile = OfficerProfile(
                    id = 1,
                    name = cleanName,
                    rank = cleanRank,
                    badgeNumber = cleanBadge,
                    station = cleanStation,
                    phoneNumber = cleanPhone,
                    address = cleanAddress
                )
                repository.saveOfficerProfile(profile)

                val current = _appUser.value
                val uid = auth.currentUser?.uid ?: current?.uid
                if (!uid.isNullOrBlank() && current != null) {
                    repository.updateOfficerFullProfile(
                        uid = uid,
                        displayName = cleanName,
                        rank = cleanRank,
                        badgeNumber = cleanBadge,
                        stationName = cleanStation,
                        phoneNumber = cleanPhone,
                        address = cleanAddress
                    )
                    val updated = current.copy(
                        displayName = cleanName,
                        rank = cleanRank,
                        badgeNumber = cleanBadge,
                        stationName = cleanStation,
                        phoneNumber = cleanPhone,
                        address = cleanAddress
                    )
                    _appUser.value = updated
                    cacheAppUser(updated)
                }
                _userMessage.emit("නිලධාරී තොරතුරු සාර්ථකව සුරැකිණි!")
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Failed to save officer profile", e)
                _userMessage.emit("තොරතුරු සුරැකීම අසාර්ථක විය: ${e.localizedMessage ?: ""}")
                onError()
            }
        }
    }

    private fun applyDutyStateLocallyAndRemote(uid: String, isOnDuty: Boolean) {
        val current = _appUser.value
        if (current != null && current.uid == uid) {
            val updated = current.copy(isOnDuty = isOnDuty)
            _appUser.value = updated
            cacheAppUser(updated)
            updateLocationTracking(updated)
        }
    }

    // Submit duty with custom timestamp, dual photos, duration, and single-slot / Station Reserve relief rules
    fun submitDutyReport(
        context: Context,
        type: String, // "ON_DUTY", "EXTRA_DUTY", or "OFF_DUTY"
        timestamp: Long,
        ibPage: String,
        ibParagraph: String,
        dutyNature: String,
        remarks: String,
        photoUri: Uri?,
        scenePhotoUri: Uri? = null,
        dutyDurationMinutes: Long = 0L,
        isHandoverConfirmed: Boolean = false,
        onSuccess: () -> Unit,
        onError: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val uid = activeOfficerUid.value.ifBlank { auth.currentUser?.uid ?: "" }
                if (uid.isBlank()) {
                    _userMessage.emit("කරුණාකර නැවත පුරනය වන්න.")
                    onError()
                    return@launch
                }

                val stationCode = appUser.value?.oicStationCode?.trim()?.uppercase().orEmpty()
                var relievedReserveHolder: ReserveHolderInfo? = null

                if (stationCode.isNotBlank()) {
                    val (stationUsers, stationRecords) = withContext(Dispatchers.IO) {
                        repository.fetchStationDutyAndUsersState(stationCode)
                    }
                    val activeHolders = DutySlotRules.resolveActiveSingleSlotHolders(stationUsers, stationRecords)
                    val activeReserve = activeHolders[DutySlotRules.SLOT_RESERVE]
                    val targetSlotCategory = DutySlotRules.getSingleSlotCategory(dutyNature)

                    when (type) {
                        "OFF_DUTY" -> {
                            if (activeReserve != null && activeReserve.officerUid == uid) {
                                _userMessage.emit("උපසේවා (Station Reserve) රාජකාරිය වෙනත් නිලධාරියෙකු විසින් භාර ගන්නා තෙක් ඔබට Off Duty (බැහැරවීම) විය නොහැක! නව නිලධාරියෙකු Station Reserve වාර්තා කළ පසු බැහැරවන්න.")
                                onError()
                                return@launch
                            }
                        }
                        "EXTRA_DUTY" -> {
                            val holder = targetSlotCategory?.let { activeHolders[it] }
                            if (holder != null && holder.officerUid != uid) {
                                val slotTitle = DutySlotRules.getCategoryDisplayName(targetSlotCategory)
                                _userMessage.emit("අවවාදයයි: මෙම පොලිස් ස්ථානයේ [$slotTitle] රාජකාරිය දැනටමත් [${holder.rank.ifBlank { "Officer" }} ${holder.officerName}] විසින් සිදු කරමින් පවතී! එක් පොලිසියකට එක් අයෙකු පමණක් සිටිය හැක.")
                                onError()
                                return@launch
                            }
                        }
                        "ON_DUTY" -> {
                            if (activeReserve != null && activeReserve.officerUid == uid && !DutySlotRules.isReserveDuty(dutyNature)) {
                                _userMessage.emit("ඔබ දැනට උපසේවා (Station Reserve) රාජකාරියේ සිටින බැවින් වෙනත් නිලධාරියෙකු එම රාජකාරිය භාරගන්නා තෙක් වෙනත් රාජකාරියකට මාරු විය නොහැක.")
                                onError()
                                return@launch
                            }
                            val holder = targetSlotCategory?.let { activeHolders[it] }
                            if (holder != null && holder.officerUid != uid) {
                                if (targetSlotCategory == DutySlotRules.SLOT_RESERVE) {
                                    if (!isHandoverConfirmed) {
                                        _userMessage.emit("දැනට [${holder.rank.ifBlank { "Officer" }} ${holder.officerName}] උපසේවා රාජකාරියේ සිටී. කරුණාකර රාජකාරිය භාරගැනීම (Relieve / Handover) ස්ථිර කර නැවත ඉදිරිපත් කරන්න.")
                                        onError()
                                        return@launch
                                    } else {
                                        relievedReserveHolder = holder
                                    }
                                } else {
                                    val slotTitle = DutySlotRules.getCategoryDisplayName(targetSlotCategory)
                                    _userMessage.emit("අවවාදයයි: මෙම පොලිස් ස්ථානයේ [$slotTitle] රාජකාරිය දැනටමත් [${holder.rank.ifBlank { "Officer" }} ${holder.officerName}] විසින් සිදු කරමින් පවතී!")
                                    onError()
                                    return@launch
                                }
                            }
                        }
                    }
                }

                var photoPath: String? = null
                var scenePhotoPath: String? = null
                var origKb = 0
                var compKb = 0

                // 1. Information book photo
                if (photoUri != null) {
                    val compression = ImageCompressor.compressAndSave(context, photoUri, prefix = "duty_ib")
                    if (compression != null) {
                        photoPath = compression.filePath
                        origKb += compression.originalSizeKb
                        compKb += compression.compressedSizeKb
                    }
                }

                // 2. Incident / Scene photo
                if (scenePhotoUri != null) {
                    val sceneCompression = ImageCompressor.compressAndSave(context, scenePhotoUri, prefix = "duty_scene")
                    if (sceneCompression != null) {
                        scenePhotoPath = sceneCompression.filePath
                        origKb += sceneCompression.originalSizeKb
                        compKb += sceneCompression.compressedSizeKb
                    }
                }

                val record = DutyRecord(
                    type = type,
                    timestamp = timestamp,
                    officerId = uid,
                    oicStationCode = stationCode,
                    ibPage = ibPage.trim(),
                    ibParagraph = ibParagraph.trim(),
                    dutyNature = dutyNature.trim(),
                    remarks = remarks.trim(),
                    photoPath = photoPath,
                    scenePhotoPath = scenePhotoPath,
                    dutyDurationMinutes = dutyDurationMinutes,
                    originalSizeKb = origKb,
                    compressedSizeKb = compKb
                )
                repository.insertDutyRecord(record)
                repository.syncDutyRecordToFirestore(record)

                if (type == "ON_DUTY" || type == "OFF_DUTY") {
                    val isOnDuty = type == "ON_DUTY"
                    auth.currentUser?.let { user ->
                        applyDutyStateLocallyAndRemote(user.uid, isOnDuty)
                        repository.updateDutyStatus(user.uid, isOnDuty)
                        refreshCurrentLocation(overrideIsOnDuty = isOnDuty)
                    }
                }

                if (relievedReserveHolder != null) {
                    val myName = appUser.value?.displayName?.ifBlank { "නව නිලධාරියා" } ?: "නව නිලධාරියා"
                    repository.sendNotification(
                        title = "උපසේවා රාජකාරිය භාරගන්නා ලදී",
                        message = "$myName විසින් උපසේවා (Station Reserve) රාජකාරිය භාරගත් බැවින් ඔබට දැන් රාජකාරියෙන් බැහැරවීමට (Off Duty) හැකිය.",
                        senderName = myName,
                        senderRole = appUser.value?.role ?: "OFFICER",
                        targetType = "USER",
                        targetStationCode = stationCode,
                        targetUid = relievedReserveHolder.officerUid,
                        priority = "URGENT"
                    )
                }

                val photoCount = (if (photoPath != null) 1 else 0) + (if (scenePhotoPath != null) 1 else 0)
                val msg = when (type) {
                    "ON_DUTY" -> if (relievedReserveHolder != null) {
                        "උපසේවා රාජකාරිය භාරගැනීම සාර්ථකයි! (${relievedReserveHolder.officerName} වෙත දැනුම් දෙන ලදී)"
                    } else if (photoCount > 0) {
                        "රාජකාරිය වාර්තා විය! (ඡායාරූප $photoCount ක් සුරැකිණි - ${compKb}KB)"
                    } else {
                        "රාජකාරිය සාර්ථකව වාර්තා විය!"
                    }
                    "EXTRA_DUTY" -> if (photoCount > 0) "අමතර රාජකාරිය සටහන් විය! (ඡායාරූප $photoCount ක් සුරැකිණි - ${compKb}KB)" else "අමතර රාජකාරිය සාර්ථකව සටහන් විය!"
                    else -> if (photoCount > 0) "බැහැරවීම සටහන් විය! (ඡායාරූප $photoCount ක් සුරැකිණි - ${compKb}KB)" else "බැහැරවීම සාර්ථකව සටහන් විය!"
                }
                _userMessage.emit(msg)
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Failed to submit duty report", e)
                _userMessage.emit("රාජකාරි සටහන සුරැකීම අසාර්ථක විය: ${e.localizedMessage ?: ""}")
                onError()
            }
        }
    }

    fun updateDutyRecord(
        context: Context,
        recordId: Long,
        type: String,
        timestamp: Long,
        ibPage: String,
        ibParagraph: String,
        dutyNature: String,
        remarks: String,
        photoUri: Uri?,
        scenePhotoUri: Uri?,
        dutyDurationMinutes: Long,
        onSuccess: () -> Unit,
        onError: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                var photoPath: String? = null
                var scenePhotoPath: String? = null
                var origKb = 0
                var compKb = 0

                if (photoUri != null) {
                    val compression = ImageCompressor.compressAndSave(context, photoUri, prefix = "duty")
                    if (compression != null) {
                        photoPath = compression.filePath
                        origKb = compression.originalSizeKb
                        compKb = compression.compressedSizeKb
                    }
                }

                if (scenePhotoUri != null) {
                    val sceneCompression = ImageCompressor.compressAndSave(context, scenePhotoUri, prefix = "duty_scene")
                    if (sceneCompression != null) {
                        scenePhotoPath = sceneCompression.filePath
                        origKb += sceneCompression.originalSizeKb
                        compKb += sceneCompression.compressedSizeKb
                    }
                }

                val uid = activeOfficerUid.value.ifBlank { auth.currentUser?.uid ?: "" }
                if (uid.isBlank()) {
                    _userMessage.emit("කරුණාකර නැවත පුරනය වන්න.")
                    onError()
                    return@launch
                }

                val record = DutyRecord(
                    id = recordId,
                    officerId = uid,
                    oicStationCode = appUser.value?.oicStationCode ?: "",
                    type = type,
                    timestamp = timestamp,
                    ibPage = ibPage.trim(),
                    ibParagraph = ibParagraph.trim(),
                    dutyNature = dutyNature.trim(),
                    remarks = remarks.trim(),
                    photoPath = photoPath,
                    scenePhotoPath = scenePhotoPath,
                    dutyDurationMinutes = dutyDurationMinutes,
                    originalSizeKb = origKb,
                    compressedSizeKb = compKb
                )
                repository.updateDutyRecord(record)
                repository.syncDutyRecordToFirestore(record)
                _userMessage.emit("රාජකාරි සටහන යාවත්කාලීන විය!")
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Failed to update duty record", e)
                _userMessage.emit("රාජකාරි සටහන යාවත්කාලීන කිරීම අසාර්ථක විය: ${e.localizedMessage ?: ""}")
                onError()
            }
        }
    }

    fun updateDutyRecord(record: DutyRecord) {
        viewModelScope.launch {
            repository.updateDutyRecord(record)
            repository.syncDutyRecordToFirestore(record)
            _userMessage.emit("රාජකාරි සටහන යාවත්කාලීන විය!")
        }
    }

    private fun calculateDutyMinutesForDate(dayRecords: List<DutyRecord>, targetDateKey: String): Long {
        if (dayRecords.isEmpty()) return 0L
        var explicitSum = 0L
        for (r in dayRecords) {
            if (r.dutyDurationMinutes > 0) {
                explicitSum += r.dutyDurationMinutes
            }
        }
        if (explicitSum > 0) return explicitSum

        val sorted = dayRecords.sortedBy { it.timestamp }
        var totalMillis = 0L
        var lastOnDuty: Long? = null
        for (r in sorted) {
            if (r.type == "ON_DUTY") {
                lastOnDuty = r.timestamp
            } else if (r.type == "OFF_DUTY" && lastOnDuty != null) {
                val diff = r.timestamp - lastOnDuty
                if (diff > 0) totalMillis += diff
                lastOnDuty = null
            }
        }
        if (lastOnDuty != null && targetDateKey == DateTimeUtils.getTodayKey()) {
            val ongoing = System.currentTimeMillis() - lastOnDuty
            if (ongoing in 1..86400000) totalMillis += ongoing
        }
        return totalMillis / (1000 * 60)
    }

    private fun formatHoursMinutesSinhala(totalMinutes: Long): String {
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return if (hours > 0) {
            "${hours}h ${mins}m (${hours} පැය ${mins} මිනි)"
        } else {
            "${mins}m (${mins} මිනි)"
        }
    }

    fun deleteDutyRecord(record: DutyRecord) {
        viewModelScope.launch {
            repository.deleteDutyRecord(record)
            _userMessage.emit("රාජකාරි සටහන ඉවත් කරන ලදී")
        }
    }

    fun toggleMealTaken(mealType: String, isTaken: Boolean) {
        val uid = activeOfficerUid.value
        val stationCode = appUser.value?.oicStationCode ?: ""
        if (uid.isBlank()) return
        viewModelScope.launch {
            val date = _selectedMessDate.value
            val defaultPrice = getDefaultPriceForType(mealType)
            repository.toggleMealTaken(
                officerId = uid,
                oicStationCode = stationCode,
                date = date,
                mealType = mealType,
                isTaken = isTaken,
                defaultPrice = defaultPrice
            )
            val messRecord = MessRecord(
                officerId = uid,
                oicStationCode = stationCode,
                date = date,
                mealType = mealType,
                isTaken = isTaken,
                price = defaultPrice
            )
            repository.syncMessRecordToFirestore(messRecord)
        }
    }

    fun saveMealDetails(
        context: Context,
        mealType: String,
        isTaken: Boolean,
        notes: String,
        price: Double = 0.0,
        photoUri: Uri?,
        onSuccess: () -> Unit,
        onError: () -> Unit = {}
    ) {
        val uid = activeOfficerUid.value
        val stationCode = appUser.value?.oicStationCode ?: ""
        if (uid.isBlank()) {
            onError()
            return
        }
        viewModelScope.launch {
            try {
                val date = _selectedMessDate.value
                var photoPath: String? = null
                var compKb = 0

                if (photoUri != null) {
                    val compression = ImageCompressor.compressAndSave(context, photoUri, prefix = "meal")
                    if (compression != null) {
                        photoPath = compression.filePath
                        compKb = compression.compressedSizeKb
                    }
                }

                val finalPrice = if (price > 0.0) price else getDefaultPriceForType(mealType)

                repository.saveMealRecord(
                    officerId = uid,
                    oicStationCode = stationCode,
                    date = date,
                    mealType = mealType,
                    isTaken = isTaken,
                    notes = notes.trim(),
                    price = finalPrice,
                    photoPath = photoPath,
                    compressedSizeKb = compKb
                )

                val messRecord = MessRecord(
                    officerId = uid,
                    oicStationCode = stationCode,
                    date = date,
                    mealType = mealType,
                    isTaken = isTaken,
                    mealNotes = notes.trim(),
                    price = finalPrice,
                    photoPath = photoPath,
                    compressedSizeKb = compKb
                )
                repository.syncMessRecordToFirestore(messRecord)

                val priceFormatted = "රු. %.2f".format(finalPrice)
                val msg = if (compKb > 0) "ආහාර සටහන ($priceFormatted) සුරැකිණි! Photo compress විය."
                else "ආහාර සටහන ($priceFormatted) සුරැකිණි!"
                _userMessage.emit(msg)
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Failed to save meal details", e)
                _userMessage.emit("ආහාර සටහන සුරැකීම අසාර්ථක විය: ${e.localizedMessage ?: ""}")
                onError()
            }
        }
    }

    fun deleteMealRecord(record: MessRecord) {
        viewModelScope.launch {
            repository.deleteMessRecord(record)
            _userMessage.emit("ආහාර සටහන ඉවත් කරන ලදී")
        }
    }

    // --- Running Chart Actions ---

    fun saveRunningChartRecord(
        date: String,
        vehicleNumber: String,
        startMeter: Double,
        endMeter: Double,
        kmPerLiter: Double,
        startFuel: Double,
        addedFuel: Double,
        notes: String,
        customRemainingFuel: Double? = null,
        existingId: Long = 0,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val distanceKm = max(0.0, endMeter - startMeter)
            val effectiveKmpL = if (kmPerLiter > 0) kmPerLiter else 1.0
            val consumedFuel = distanceKm / effectiveKmpL
            val remainingFuel = customRemainingFuel ?: (startFuel + addedFuel - consumedFuel)

            val record = RunningChartRecord(
                id = existingId,
                officerId = activeOfficerUid.value,
                oicStationCode = appUser.value?.oicStationCode ?: "",
                date = date.trim(),
                vehicleNumber = vehicleNumber.trim().uppercase(),
                startMeter = startMeter,
                endMeter = endMeter,
                distanceKm = distanceKm,
                kmPerLiter = kmPerLiter,
                startFuel = startFuel,
                addedFuel = addedFuel,
                consumedFuel = consumedFuel,
                remainingFuel = remainingFuel,
                notes = notes.trim(),
                timestamp = System.currentTimeMillis()
            )

            if (existingId > 0) {
                repository.updateRunningChartRecord(record)
                repository.syncRunningChartRecordToFirestore(record)
                _userMessage.emit("ගමන් සටහන යාවත්කාලීන විය!")
            } else {
                repository.insertRunningChartRecord(record)
                repository.syncRunningChartRecordToFirestore(record)
                _userMessage.emit("ගමන් සටහන සාර්ථකව සුරැකිණි!")
            }
            onSuccess()
        }
    }

    fun deleteRunningChartRecord(record: RunningChartRecord) {
        viewModelScope.launch {
            repository.deleteRunningChartRecord(record)
            _userMessage.emit("ගමන් සටහන ඉවත් කරන ලදී")
        }
    }

    suspend fun getLatestRecordForVehicle(vehicleNumber: String): RunningChartRecord? {
        return repository.getLatestRecordForVehicle(activeOfficerUid.value, vehicleNumber.trim().uppercase())
    }

    // --- Team Duty QR Session Actions ---

    fun startTeamDuty(
        dutyNature: String,
        ibPage: String,
        ibParagraph: String,
        remarks: String,
        onSuccess: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        val user = _appUser.value
        val uid = auth.currentUser?.uid ?: user?.uid
        if (uid.isNullOrBlank() || user == null) {
            onError()
            return
        }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val sessionId = UUID.randomUUID().toString()
                val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                val joinCode = (1..6).map { chars.random() }.joinToString("")
                val session = TeamDutySession(
                    sessionId = sessionId,
                    joinCode = joinCode,
                    leaderUid = uid,
                    leaderName = user.displayName.ifBlank { user.email },
                    leaderBadge = user.badgeNumber,
                    leaderRank = user.rank,
                    oicStationCode = user.oicStationCode ?: "",
                    dutyNature = dutyNature.trim().ifBlank { "Group Patrol Duty" },
                    ibPage = ibPage.trim(),
                    ibParagraph = ibParagraph.trim(),
                    remarks = remarks.trim(),
                    status = "ACTIVE",
                    startedAtMs = now
                )
                val leaderMember = TeamDutyMember(
                    uid = uid,
                    displayName = user.displayName.ifBlank { user.email },
                    rank = user.rank,
                    badgeNumber = user.badgeNumber,
                    phoneNumber = user.phoneNumber,
                    roleInTeam = "LEADER",
                    status = "ACTIVE",
                    joinedAtMs = now
                )
                val res = repository.createTeamDutySession(session, leaderMember)
                if (res.isFailure) {
                    _userMessage.emit("කණ්ඩායම් රාජකාරිය ඇරඹීම අසාර්ථක විය: ${res.exceptionOrNull()?.localizedMessage ?: ""}")
                    onError()
                    return@launch
                }

                prefs.edit().putString(teamDutyPrefKey(uid), sessionId).apply()
                _activeTeamDutyId.value = sessionId

                val record = DutyRecord(
                    type = "ON_DUTY",
                    timestamp = now,
                    officerId = uid,
                    oicStationCode = user.oicStationCode ?: "",
                    ibPage = ibPage.trim(),
                    ibParagraph = ibParagraph.trim(),
                    dutyNature = "[කණ්ඩායම් $joinCode] ${session.dutyNature}",
                    remarks = "ප්‍රධාන නිලධාරියා ලෙස කණ්ඩායම් රාජකාරිය ආරම්භ කරන ලදී. ${remarks.trim()}".trim()
                )
                repository.insertDutyRecord(record)
                repository.syncDutyRecordToFirestore(record)
                applyDutyStateLocallyAndRemote(uid, true)
                repository.updateDutyStatus(uid, true)
                refreshCurrentLocation(overrideIsOnDuty = true)

                _userMessage.emit("කණ්ඩායම් රාජකාරිය ඇරඹිණි! කේතය: $joinCode")
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Failed to start team duty", e)
                _userMessage.emit("කණ්ඩායම් රාජකාරිය ඇරඹීම අසාර්ථක විය.")
                onError()
            }
        }
    }

    fun joinTeamDuty(
        rawQrOrCode: String,
        onSuccess: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        val user = _appUser.value
        val uid = auth.currentUser?.uid ?: user?.uid
        if (uid.isNullOrBlank() || user == null) {
            onError()
            return
        }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val member = TeamDutyMember(
                    uid = uid,
                    displayName = user.displayName.ifBlank { user.email },
                    rank = user.rank,
                    badgeNumber = user.badgeNumber,
                    phoneNumber = user.phoneNumber,
                    roleInTeam = "MEMBER",
                    status = "ACTIVE",
                    joinedAtMs = now
                )
                val result = repository.findAndJoinTeamDuty(rawQrOrCode, member)
                if (result.isFailure) {
                    _userMessage.emit(result.exceptionOrNull()?.message ?: "QR කේතය හඳුනාගත නොහැකි විය.")
                    onError()
                    return@launch
                }
                val session = result.getOrThrow()
                if (_activeTeamDutyId.value != session.sessionId) {
                    val record = DutyRecord(
                        type = "ON_DUTY",
                        timestamp = now,
                        officerId = uid,
                        oicStationCode = user.oicStationCode ?: "",
                        ibPage = session.ibPage,
                        ibParagraph = session.ibParagraph,
                        dutyNature = "[කණ්ඩායම් ${session.joinCode}] ${session.dutyNature}",
                        remarks = "ප්‍රධාන නිලධාරී ${session.leaderName} සමඟ QR මගින් එක්විය. ${session.remarks}".trim()
                    )
                    repository.insertDutyRecord(record)
                    repository.syncDutyRecordToFirestore(record)
                    applyDutyStateLocallyAndRemote(uid, true)
                    repository.updateDutyStatus(uid, true)
                    refreshCurrentLocation(overrideIsOnDuty = true)
                }
                prefs.edit().putString(teamDutyPrefKey(uid), session.sessionId).apply()
                _activeTeamDutyId.value = session.sessionId
                _userMessage.emit("${session.leaderName} ගේ කණ්ඩායම් රාජකාරියට එක්විය (${session.joinCode})!")
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Failed to join team duty", e)
                _userMessage.emit("කණ්ඩායම් රාජකාරියට එක්වීම අසාර්ථක විය.")
                onError()
            }
        }
    }

    fun endTeamDutyAsLeader(onSuccess: () -> Unit = {}, onError: () -> Unit = {}) {
        val session = activeTeamDutySession.value
        val uid = auth.currentUser?.uid ?: _appUser.value?.uid
        val stationCode = _appUser.value?.oicStationCode ?: ""
        if (session == null || uid.isNullOrBlank()) {
            onError()
            return
        }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val res = repository.endTeamDutyAsLeader(session.sessionId, uid, now)
                if (res.isFailure) {
                    _userMessage.emit("රාජකාරිය අවසන් කිරීම අසාර්ථක විය.")
                    onError()
                    return@launch
                }
                val durationMins = max(0L, (now - session.startedAtMs) / 60000L)
                val record = DutyRecord(
                    type = "OFF_DUTY",
                    timestamp = now,
                    officerId = uid,
                    oicStationCode = stationCode,
                    ibPage = session.ibPage,
                    ibParagraph = session.ibParagraph,
                    dutyNature = "[කණ්ඩායම් ${session.joinCode}] ${session.dutyNature}",
                    remarks = "ප්‍රධාන නිලධාරියා කණ්ඩායම් රාජකාරිය අවසන් කළේය. සියලු සාමාජිකයන්ද ඉවත් කරන ලදී.",
                    dutyDurationMinutes = durationMins
                )
                repository.insertDutyRecord(record)
                repository.syncDutyRecordToFirestore(record)
                applyDutyStateLocallyAndRemote(uid, false)
                repository.updateDutyStatus(uid, false)
                refreshCurrentLocation(overrideIsOnDuty = false)

                prefs.edit().remove(teamDutyPrefKey(uid)).remove("active_team_duty_id").apply()
                _activeTeamDutyId.value = ""
                _userMessage.emit("කණ්ඩායම් රාජකාරිය අවසන් විය! සම්බන්ධ වී සිටි සියලු නිලධාරීන් ස්වයංක්‍රීයව ඉවත් වනු ඇත.")
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Failed to end team duty", e)
                onError()
            }
        }
    }

    fun leaveTeamDutyAsMember(onSuccess: () -> Unit = {}) {
        val session = activeTeamDutySession.value
        val uid = auth.currentUser?.uid ?: _appUser.value?.uid ?: return
        val stationCode = _appUser.value?.oicStationCode ?: ""
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                if (session != null) {
                    repository.updateMyTeamMembershipStatus(session.sessionId, uid, "LEFT")
                    val durationMins = max(0L, (now - session.startedAtMs) / 60000L)
                    val record = DutyRecord(
                        type = "OFF_DUTY",
                        timestamp = now,
                        officerId = uid,
                        oicStationCode = stationCode,
                        ibPage = session.ibPage,
                        ibParagraph = session.ibParagraph,
                        dutyNature = "[කණ්ඩායම් ${session.joinCode}] ${session.dutyNature}",
                        remarks = "කණ්ඩායම් රාජකාරියෙන් ස්වේච්ඡාවෙන් ඉවත් විය.",
                        dutyDurationMinutes = durationMins
                    )
                    repository.insertDutyRecord(record)
                    repository.syncDutyRecordToFirestore(record)
                }
                applyDutyStateLocallyAndRemote(uid, false)
                repository.updateDutyStatus(uid, false)
                refreshCurrentLocation(overrideIsOnDuty = false)
                prefs.edit().remove(teamDutyPrefKey(uid)).remove("active_team_duty_id").apply()
                _activeTeamDutyId.value = ""
                _userMessage.emit("ඔබ කණ්ඩායම් රාජකාරියෙන් ඉවත් විය.")
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Failed to leave team duty", e)
            }
        }
    }

    private suspend fun autoLeaveEndedTeamDuty(session: TeamDutySession) {
        val uid = auth.currentUser?.uid ?: _appUser.value?.uid ?: return
        val stationCode = _appUser.value?.oicStationCode ?: ""
        try {
            val now = System.currentTimeMillis()
            repository.updateMyTeamMembershipStatus(session.sessionId, uid, "ENDED_BY_LEADER")
            val durationMins = max(0L, (now - session.startedAtMs) / 60000L)
            val record = DutyRecord(
                type = "OFF_DUTY",
                timestamp = now,
                officerId = uid,
                oicStationCode = stationCode,
                ibPage = session.ibPage,
                ibParagraph = session.ibParagraph,
                dutyNature = "[කණ්ඩායම් ${session.joinCode}] ${session.dutyNature}",
                remarks = "ප්‍රධාන නිලධාරියා (${session.leaderName}) රාජකාරිය අවසන් කළ බැවින් ස්වයංක්‍රීයව බැහැරවීම සටහන් විය.",
                dutyDurationMinutes = durationMins
            )
            repository.insertDutyRecord(record)
            repository.syncDutyRecordToFirestore(record)
            applyDutyStateLocallyAndRemote(uid, false)
            repository.updateDutyStatus(uid, false)
            refreshCurrentLocation(overrideIsOnDuty = false)
            prefs.edit().remove(teamDutyPrefKey(uid)).remove("active_team_duty_id").apply()
            _activeTeamDutyId.value = ""

            val msg = "ප්‍රධාන නිලධාරී ${session.leaderName} කණ්ඩායම් රාජකාරිය අවසන් කළ බැවින් ඔබද රාජකාරියෙන් ඉවත් විය."
            NotificationHelper.showSystemNotification(
                context = getApplication(),
                title = "කණ්ඩායම් රාජකාරිය අවසන් විය",
                body = msg,
                notificationId = session.sessionId.hashCode(),
                isUrgent = true
            )
            _userMessage.emit(msg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("PoliceViewModel", "Failed to auto-leave ended team duty", e)
        }
    }

    fun saveTeamIncidentPhotoNote(
        context: Context,
        noteTitle: String,
        noteDetails: String,
        photoUri: Uri?,
        onSuccess: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                var scenePhotoPath: String? = null
                var origKb = 0
                var compKb = 0
                if (photoUri != null) {
                    val compressed = ImageCompressor.compressAndSave(context, photoUri, prefix = "team_incident")
                    if (compressed != null) {
                        scenePhotoPath = compressed.filePath
                        origKb = compressed.originalSizeKb
                        compKb = compressed.compressedSizeKb
                    }
                }
                val uid = activeOfficerUid.value.ifBlank { auth.currentUser?.uid ?: "" }
                if (uid.isBlank()) {
                    _userMessage.emit("කරුණාකර නැවත පුරනය වන්න.")
                    onError()
                    return@launch
                }
                val session = activeTeamDutySession.value
                val joinTag = session?.joinCode?.takeIf { it.isNotBlank() }?.let { "[කණ්ඩායම් $it] " } ?: "[විශේෂ සිද්ධි] "
                val record = DutyRecord(
                    type = "EXTRA_DUTY",
                    timestamp = System.currentTimeMillis(),
                    officerId = uid,
                    oicStationCode = _appUser.value?.oicStationCode ?: "",
                    ibPage = session?.ibPage ?: "",
                    ibParagraph = session?.ibParagraph ?: "",
                    dutyNature = "$joinTag${noteTitle.trim().ifBlank { "විශේෂ සටහන / සිද්ධිය" }}",
                    remarks = noteDetails.trim(),
                    scenePhotoPath = scenePhotoPath,
                    originalSizeKb = origKb,
                    compressedSizeKb = compKb
                )
                repository.insertDutyRecord(record)
                repository.syncDutyRecordToFirestore(record)
                val msg = if (scenePhotoPath != null) {
                    "විශේෂ සටහන හා ඡායාරූපය Local Storage හි සුරැකිණි (${compKb}KB)!"
                } else {
                    "විශේෂ සටහන සාර්ථකව සුරැකිණි!"
                }
                _userMessage.emit(msg)
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PoliceViewModel", "Failed to save team incident photo note", e)
                _userMessage.emit("විශේෂ සටහන සුරැකීම අසාර්ථක විය.")
                onError()
            }
        }
    }

    // --- Contacts Actions ---

    fun loadContacts(context: Context) {
        if (_rawContacts.value.isNotEmpty()) return
        viewModelScope.launch {
            _isContactsLoading.value = true
            val offlineList = repository.loadOfflineContacts(context)
            _rawContacts.value = offlineList
            _isContactsLoading.value = false
        }
    }

    fun refreshContactsOnline(silent: Boolean = false) {
        viewModelScope.launch {
            _isContactsLoading.value = true
            val onlineList = repository.fetchLiveGoogleSheetContacts()
            if (onlineList != null && onlineList.isNotEmpty()) {
                _rawContacts.value = onlineList
                if (!silent) {
                    _userMessage.emit("දුරකථන නාමාවලිය සාර්ථකව යාවත්කාලීන විය (${onlineList.size} සබඳතා)")
                }
            } else if (!silent) {
                _userMessage.emit("මාර්ගගත යාවත්කාලීන කිරීම අසාර්ථක විය. නොබැඳි දත්ත භාවිතා කරයි.")
            }
            _isContactsLoading.value = false
        }
    }
}

class PoliceViewModelFactory(
    private val repository: PoliceRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PoliceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PoliceViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
