package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.DutyRecord
import com.example.data.MessRecord
import com.example.data.OfficerProfile
import com.example.data.PoliceContact
import com.example.data.PoliceRepository
import com.example.data.RunningChartRecord
import com.example.util.DateTimeUtils
import com.example.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import android.location.Location
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.example.data.AppUser
import com.google.firebase.auth.FirebaseUser
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.math.max

enum class AuthPortal {
    CHOOSER,
    OIC,
    OFFICER
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

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _appUser = MutableStateFlow<AppUser?>(null)
    val appUser: StateFlow<AppUser?> = _appUser.asStateFlow()

    private var locationJob: Job? = null
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    // --- Subordinates (for OIC) ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val subordinates: StateFlow<List<AppUser>> = _appUser.flatMapLatest { user ->
        if (user?.role == "OIC" && user.oicStationCode != null) {
            repository.observeSubordinates(user.oicStationCode!!)
        } else {
            flow { emit(emptyList<AppUser>()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _selectedOfficer = MutableStateFlow<AppUser?>(null)
    val selectedOfficer: StateFlow<AppUser?> = _selectedOfficer.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedOfficerActivity = _selectedOfficer.flatMapLatest { officer ->
        if (officer != null) {
            repository.observeDutyRecordsForOfficer(officer.uid)
        } else {
            flow { emit(emptyList<DutyRecord>()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun selectOfficer(officer: AppUser?) {
        _selectedOfficer.value = officer
    }

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
            if (firebaseAuth.currentUser != null) {
                fetchAppUser(firebaseAuth.currentUser!!.uid)
            } else {
                _appUser.value = null
                _isRegistered.value = false
                _isOICLoggedIn.value = false
            }
        }
    }

    private fun fetchAppUser(uid: String) {
        viewModelScope.launch {
            repository.observeUser(uid).collect { user ->
                _appUser.value = user
                if (user != null) {
                    _isRegistered.value = user.status == "APPROVED" || user.status == "PENDING"
                    _isOICLoggedIn.value = user.role == "OIC"
                    prefs.edit().putBoolean("is_registered", _isRegistered.value).commit()
                    prefs.edit().putBoolean("is_oic_logged_in", _isOICLoggedIn.value).commit()
                    if (user.role == "OFFICER") {
                        repository.saveOfficerProfile(
                            OfficerProfile(
                                name = user.displayName,
                                rank = user.rank.ifBlank { "Police Constable" },
                                badgeNumber = user.badgeNumber,
                                station = user.oicStationCode ?: ""
                            )
                        )
                    }
                    updateLocationTracking(user)
                }
            }
        }
    }

    fun refreshCurrentLocation(showFeedback: Boolean = false) {
        val user = _appUser.value ?: return
        if (user.role != "OFFICER") return
        viewModelScope.launch(Dispatchers.IO) {
            val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) {
                if (showFeedback) {
                    _userMessage.emit("GPS අවසරය ලබාදී නොමැත.")
                }
                return@launch
            }

            try {
                var loc: android.location.Location? = null
                try {
                    loc = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                } catch (e: Exception) {
                    // ignore
                }
                if (loc == null) {
                    try {
                        loc = fusedLocationClient.lastLocation.await()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                if (loc != null) {
                    repository.updateLastLocation(user.uid, loc.latitude, loc.longitude, user.isOnDuty)
                    if (showFeedback) {
                        _userMessage.emit("GPS ස්ථානය සාර්ථකව යාවත්කාලීන විය (${String.format("%.4f", loc.latitude)}, ${String.format("%.4f", loc.longitude)}).")
                    }
                } else if (showFeedback) {
                    _userMessage.emit("GPS ස්ථානය ලබා ගැනීමට නොහැකි විය. කරුණාකර GPS සක්‍රිය කරන්න.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateLocationTracking(user: AppUser) {
        locationJob?.cancel()
        if (user.role == "OFFICER" && user.status == "APPROVED") {
            locationJob = viewModelScope.launch(Dispatchers.IO) {
                refreshCurrentLocation()
                while (user.isOnDuty) {
                    delay(30000) // Update every 30 seconds while on duty
                    refreshCurrentLocation()
                }
            }
        }
    }

    fun registerOfficer(officerName: String, badgeNumber: String = "", rank: String = "", stationCode: String) {
        val user = auth.currentUser ?: return
        val code = stationCode.trim().uppercase()
        viewModelScope.launch {
            val appUser = AppUser(
                uid = user.uid,
                displayName = officerName,
                email = user.email ?: "",
                role = "OFFICER",
                status = "PENDING",
                badgeNumber = badgeNumber.trim(),
                rank = rank.trim(),
                oicStationCode = code
            )
            repository.saveUser(appUser)
            repository.saveOfficerProfile(
                OfficerProfile(
                    name = officerName,
                    rank = rank.ifBlank { "Police Constable" },
                    badgeNumber = badgeNumber,
                    station = code
                )
            )
            _appUser.value = appUser
            _isRegistered.value = true
            prefs.edit().putBoolean("is_registered", true).commit()
            _userMessage.emit("ලියාපදිංචි වීමේ ඉල්ලීම OIC වෙත යොමු කරන ලදී ($code).")
        }
    }

    fun registerOIC(oicName: String, stationName: String = "", customCode: String = "") {
        val user = auth.currentUser ?: return
        val finalCode = if (customCode.isNotBlank()) customCode.trim().uppercase() else generateSuggestedOicCode(stationName)
        viewModelScope.launch {
            val appUser = AppUser(
                uid = user.uid,
                displayName = oicName,
                email = user.email ?: "",
                role = "OIC",
                status = "APPROVED",
                stationName = stationName.trim(),
                oicStationCode = finalCode
            )
            repository.saveUser(appUser)
            _appUser.value = appUser
            _isOICLoggedIn.value = true
            _isRegistered.value = true
            prefs.edit().putBoolean("is_registered", true).commit()
            prefs.edit().putBoolean("is_oic_logged_in", true).commit()
            _userMessage.emit("OIC ලෙස සාර්ථකව ලියාපදිංචි විය. ඔබගේ OIC කේතය: $finalCode")
        }
    }

    fun generateSuggestedOicCode(station: String): String {
        val clean = station.trim().uppercase().replace("[^A-Z]".toRegex(), "").take(4)
        val rand = (1000..9999).random()
        return if (clean.isNotBlank()) "OIC-$clean-$rand" else "OIC-$rand"
    }

    fun approveOfficer(officerUid: String) {
        viewModelScope.launch {
            repository.updateOfficerStatus(officerUid, "APPROVED")
            _userMessage.emit("නිලධාරියා සාර්ථකව අනුමත කරන ලදී.")
        }
    }

    fun rejectOfficer(officerUid: String) {
        viewModelScope.launch {
            repository.updateOfficerStatus(officerUid, "REJECTED")
            _userMessage.emit("නිලධාරියා ප්‍රතික්ෂේප කරන ලදී.")
        }
    }

    fun logout() {
        auth.signOut()
        _isRegistered.value = false
        _isOICLoggedIn.value = false
        _appUser.value = null
        _authPortal.value = AuthPortal.CHOOSER
        prefs.edit().clear().commit()
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

    val allDutyRecords: StateFlow<List<DutyRecord>> = repository.allDutyRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawDutyRecords: StateFlow<List<DutyRecord>> = _searchQuery
        .flatMapLatest { query -> repository.searchDutyRecords(query) }
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

    val latestDutyRecord: StateFlow<DutyRecord?> = repository.latestDutyRecord
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
    val selectedDayMeals: StateFlow<List<MessRecord>> = _selectedMessDate
        .flatMapLatest { date -> repository.getMessRecordsForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val currentMonthKey = DateTimeUtils.getCurrentMonthKey()
    val monthlyMessRecords: StateFlow<List<MessRecord>> = repository.getMessRecordsForMonth(currentMonthKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMessRecords: StateFlow<List<MessRecord>> = repository.allMessRecords
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

    // --- Storage Stats ---
    val storageStats: StateFlow<StorageStats> = combine(
        repository.allDutyRecords,
        repository.allMessRecords
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

    // --- Running Chart State ---
    private val _selectedVehicleFilter = MutableStateFlow("ALL")
    val selectedVehicleFilter: StateFlow<String> = _selectedVehicleFilter.asStateFlow()

    val distinctVehicles: StateFlow<List<String>> = repository.distinctVehicles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRunningChartRecords: StateFlow<List<RunningChartRecord>> = repository.allRunningChartRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val runningChartRecords: StateFlow<List<RunningChartRecord>> = _selectedVehicleFilter
        .flatMapLatest { vehicle -> repository.getRunningChartRecordsByVehicle(vehicle) }
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

    fun saveOfficerProfile(name: String, rank: String, badgeNumber: String, station: String) {
        viewModelScope.launch {
            val profile = OfficerProfile(
                id = 1,
                name = name.trim(),
                rank = rank.trim(),
                badgeNumber = badgeNumber.trim(),
                station = station.trim()
            )
            repository.saveOfficerProfile(profile)
            _userMessage.emit("නිලධාරී තොරතුරු සාර්ථකව සුරැකිණි!")
        }
    }

    // Submit duty with custom timestamp (user selected date and time), dual photos, and duration
    fun submitDutyReport(
        context: Context,
        type: String, // "ON_DUTY" or "OFF_DUTY"
        timestamp: Long,
        ibPage: String,
        ibParagraph: String,
        dutyNature: String,
        remarks: String,
        photoUri: Uri?,
        scenePhotoUri: Uri? = null,
        dutyDurationMinutes: Long = 0L,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
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

            // 2. Incident / Scene photo (සිද්ධියේ හෝ ස්ථානයේ ඡායාරූපය)
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
                officerId = auth.currentUser?.uid ?: "",
                oicStationCode = appUser.value?.oicStationCode ?: "",
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
                    repository.updateDutyStatus(user.uid, isOnDuty)
                    refreshCurrentLocation()
                }
            }

            val photoCount = (if (photoPath != null) 1 else 0) + (if (scenePhotoPath != null) 1 else 0)
            val msg = when (type) {
                "ON_DUTY" -> if (photoCount > 0) "රාජකාරිය වාර්තා විය! (ඡායාරූප $photoCount ක් සුරැකිණි - ${compKb}KB)" else "රාජකාරිය සාර්ථකව වාර්තා විය!"
                "EXTRA_DUTY" -> if (photoCount > 0) "අමතර රාජකාරිය සටහන් විය! (ඡායාරූප $photoCount ක් සුරැකිණි - ${compKb}KB)" else "අමතර රාජකාරිය සාර්ථකව සටහන් විය!"
                else -> if (photoCount > 0) "බැහැරවීම සටහන් විය! (ඡායාරූප $photoCount ක් සුරැකිණි - ${compKb}KB)" else "බැහැරවීම සාර්ථකව සටහන් විය!"
            }
            _userMessage.emit(msg)
            onSuccess()
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
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
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

            val record = DutyRecord(
                id = recordId,
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
            _userMessage.emit("රාජකාරි සටහන යාවත්කාලීන විය!")
            onSuccess()
        }
    }

    fun updateDutyRecord(record: DutyRecord) {
        viewModelScope.launch {
            repository.updateDutyRecord(record)
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
        viewModelScope.launch {
            val date = _selectedMessDate.value
            val defaultPrice = getDefaultPriceForType(mealType)
            repository.toggleMealTaken(date, mealType, isTaken, defaultPrice = defaultPrice)
        }
    }

    fun saveMealDetails(
        context: Context,
        mealType: String,
        isTaken: Boolean,
        notes: String,
        price: Double = 0.0,
        photoUri: Uri?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
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
                date = date,
                mealType = mealType,
                isTaken = isTaken,
                notes = notes.trim(),
                price = finalPrice,
                photoPath = photoPath,
                compressedSizeKb = compKb
            )
            
            val messRecord = MessRecord(
                officerId = auth.currentUser?.uid ?: "",
                oicStationCode = appUser.value?.oicStationCode ?: "",
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
                _userMessage.emit("ගමන් සටහන යාවත්කාලීන විය!")
            } else {
                repository.insertRunningChartRecord(record)
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
        return repository.getLatestRecordForVehicle(vehicleNumber.trim().uppercase())
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

    fun refreshContactsOnline() {
        viewModelScope.launch {
            _isContactsLoading.value = true
            val onlineList = repository.fetchLiveGoogleSheetContacts()
            if (onlineList != null && onlineList.isNotEmpty()) {
                _rawContacts.value = onlineList
                _userMessage.emit("දුරකථන නාමාවලිය සාර්ථකව යාවත්කාලීන විය (${onlineList.size} සබඳතා)")
            } else {
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
