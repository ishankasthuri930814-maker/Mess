package com.example.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppUser
import com.example.data.DutyAssignment
import com.example.data.MessRecord
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceThemeColors
import com.example.util.DateTimeUtils
import com.example.util.DutySlotRules
import com.example.util.ReserveHolderInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBranchDashboardScreen(
    viewModel: PoliceViewModel
) {
    val context = LocalContext.current
    val appUser by viewModel.appUser.collectAsStateWithLifecycle()
    val subordinates by viewModel.subordinates.collectAsStateWithLifecycle()
    val stationContacts by viewModel.stationContacts.collectAsStateWithLifecycle()
    val stationAssignments by viewModel.stationDutyAssignments.collectAsStateWithLifecycle()
    val stationMessRecords by viewModel.stationMessRecords.collectAsStateWithLifecycle()
    val activeReserveHolder by viewModel.stationActiveReserveHolder.collectAsStateWithLifecycle()
    val isPasscodeUnlocked by viewModel.isAdminPasscodeUnlockedToday.collectAsStateWithLifecycle()
    val emergencyAlerts by viewModel.nearbyEmergencyAlerts.collectAsStateWithLifecycle()
    val myLocation by viewModel.myCurrentLocation.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var enteredPasscode by remember { mutableStateOf("") }
    var passcodeError by remember { mutableStateOf<String?>(null) }
    var isLogoutConfirmOpen by remember { mutableStateOf(false) }
    var isDirectoryOpen by remember { mutableStateOf(false) }
    var isSendSosDialogOpen by remember { mutableStateOf(false) }

    // Duty Assignment Form State
    var assigningOfficer by remember { mutableStateOf<AppUser?>(null) }
    var assigningDate by remember { mutableStateOf(DateTimeUtils.getTodayKey()) }
    var editingAssignment by remember { mutableStateOf<DutyAssignment?>(null) }

    // Mess filter state
    var selectedMessMonth by remember { mutableStateOf(DateTimeUtils.getCurrentMonthKey()) }

    val approvedOfficers = remember(subordinates) {
        subordinates.filter { it.status == "APPROVED" && it.role == "OFFICER" }
    }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun dialPhone(phone: String) {
        val clean = phone.replace(" ", "").replace("-", "")
        if (clean.isBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
        } catch (_: Exception) {
            Toast.makeText(context, "ඇමතුම් ලබාගත නොහැක", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareMealReportText(text: String) {
        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "මෙස් ආහාර වාර්තාව බෙදාගන්න (Share Mess Report)"))
        } catch (_: Exception) {
            Toast.makeText(context, "වාර්තාව බෙදාගත නොහැක", Toast.LENGTH_SHORT).show()
        }
    }

    // UNBYPASSABLE DAILY 6-DIGIT PASSCODE LOCK GATE
    if (!isPasscodeUnlocked) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "පාලන අංශය - ආරක්ෂිත පිවිසුම",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    actions = {
                        IconButton(onClick = { isLogoutConfirmOpen = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign Out",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
                )
            }
        ) { gatePadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(gatePadding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(BadgeGold.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = PoliceThemeColors.goldText,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "අද දින පාලන අංශ රහස් අංකය",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "දිනපතා ස්වයංක්‍රීයව වෙනස් වන ඉලක්කම් 6 ක රහස් අංකය ස්ථානාධිපති (OIC) හට පමණක් දිස්වේ. OIC විසින් අද දින අංකය ලබා දුන් පසු පමණක් ඔබට පාලන අංශයට පිවිසිය හැක.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        OutlinedTextField(
                            value = enteredPasscode,
                            onValueChange = { input ->
                                enteredPasscode = input.filter { it.isDigit() }.take(6)
                                passcodeError = null
                            },
                            label = { Text("OIC දුන් ඉලක්කම් 6 ක Passcode එක") },
                            placeholder = { Text("උදා: 748192") },
                            leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = PoliceThemeColors.goldText) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = passcodeError != null,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_daily_lock_input")
                        )

                        if (passcodeError != null) {
                            Text(
                                text = passcodeError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val success = viewModel.unlockAdminPasscodeToday(enteredPasscode)
                                if (success) {
                                    passcodeError = null
                                    enteredPasscode = ""
                                } else {
                                    passcodeError = "වැරදි රහස් අංකයකි! කරුණාකර ස්ථානාධිපති (OIC) වෙතින් අද දින අංක 6 ලබාගන්න."
                                }
                            },
                            enabled = enteredPasscode.length == 6,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PoliceNavyPrimary,
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF475569),
                                disabledContentColor = Color.White.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("admin_unlock_btn")
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("පාලන අංශය අගුළු හරින්න (Unlock)", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { isLogoutConfirmOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("ඉවත් වන්න (Sign Out)", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (isLogoutConfirmOpen) {
            AlertDialog(
                onDismissRequest = { isLogoutConfirmOpen = false },
                title = { Text("පාලන අංශයෙන් ඉවත් වීම (Sign Out)") },
                text = { Text("ඔබට පාලන අංශ ගිණුමෙන් ඉවත් වීමට අවශ්‍ය බව සහතිකද?") },
                confirmButton = {
                    Button(
                        onClick = {
                            isLogoutConfirmOpen = false
                            viewModel.logout()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        )
                    ) {
                        Text("ඉවත් වන්න")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isLogoutConfirmOpen = false }) {
                        Text("අවලංගු කරන්න")
                    }
                }
            )
        }
        return
    }

    if (isDirectoryOpen) {
        BackHandler { isDirectoryOpen = false }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("පොලිස් දුරකථන නාමාවලිය", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { isDirectoryOpen = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PoliceNavyDark)
                )
            }
        ) { dirPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(dirPadding)) {
                DirectoryScreen(viewModel = viewModel, initialTab = 0)
            }
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BadgeGold),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = PoliceNavyDark,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "පාලන අංශය (Admin Branch)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${appUser?.stationName.orEmpty().ifBlank { "පොලිස් ස්ථානය" }} • ${appUser?.oicStationCode ?: ""}",
                                fontSize = 11.sp,
                                color = Color(0xFFFCD34D)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isSendSosDialogOpen = true },
                        modifier = Modifier.testTag("admin_sos_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Emergency SOS",
                            tint = Color(0xFFFCA5A5)
                        )
                    }
                    IconButton(
                        onClick = { isDirectoryOpen = true },
                        modifier = Modifier.testTag("admin_dir_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContactPhone,
                            contentDescription = "Police Directory",
                            tint = Color(0xFFFCD34D)
                        )
                    }
                    IconButton(onClick = { isLogoutConfirmOpen = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (emergencyAlerts.isNotEmpty()) {
                EmergencySosBanner(
                    alerts = emergencyAlerts,
                    myLocation = myLocation,
                    myUid = appUser?.uid ?: "",
                    isOic = false,
                    onClickAlertLocation = { },
                    onResolveAlert = { viewModel.resolveEmergencyAlert(it) },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // Quick Metrics Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdminMetricCard(
                    title = "මුළු නිලධාරීන්",
                    value = approvedOfficers.size.toString(),
                    color = PoliceThemeColors.navyText,
                    modifier = Modifier.weight(1f)
                )
                val todayAssignmentsCount = stationAssignments.count { it.dateKey == DateTimeUtils.getTodayKey() }
                AdminMetricCard(
                    title = "අද රාජකාරි පැවරූ",
                    value = todayAssignmentsCount.toString(),
                    color = PoliceThemeColors.greenText,
                    modifier = Modifier.weight(1f)
                )
                val todayMealsCount = stationMessRecords
                    .distinctBy { Triple(it.officerId, it.date, it.mealType) }
                    .count { it.date == DateTimeUtils.getTodayKey() && it.isTaken }
                AdminMetricCard(
                    title = "අද මෙස් ආහාර",
                    value = todayMealsCount.toString(),
                    color = PoliceThemeColors.goldText,
                    modifier = Modifier.weight(1f)
                )
            }

            // Navigation Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PoliceThemeColors.navyText,
                edgePadding = 12.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AssignmentInd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("රාජකාරි පැවරීම", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ඉදිරි දින රාජකාරි", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("මෙස් ආහාර වාර්තාව", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    modifier = Modifier.testTag("admin_station_contacts_tab"),
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ස්ථානීය සබඳතා (${stationContacts.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    AssignDutyTabContent(
                        approvedOfficers = approvedOfficers,
                        stationAssignments = stationAssignments,
                        activeReserveHolder = activeReserveHolder,
                        onAssignDuty = { officer, date, existingAssign ->
                            assigningDate = date
                            editingAssignment = existingAssign
                            assigningOfficer = officer
                        },
                        onDeleteAssignment = { id -> viewModel.deleteDutyAssignment(id) },
                        onCall = { dialPhone(it) }
                    )
                }

                1 -> {
                    UpcomingScheduleTabContent(
                        approvedOfficers = approvedOfficers,
                        stationAssignments = stationAssignments,
                        onEditAssignment = { assign ->
                            val targetOfficer = approvedOfficers.firstOrNull { it.uid == assign.officerId }
                                ?: AppUser(
                                    uid = assign.officerId,
                                    displayName = assign.officerName,
                                    rank = assign.officerRank,
                                    badgeNumber = assign.officerBadge,
                                    phoneNumber = assign.officerPhone
                                )
                            assigningDate = assign.dateKey
                            editingAssignment = assign
                            assigningOfficer = targetOfficer
                        },
                        onDeleteAssignment = { id -> viewModel.deleteDutyAssignment(id) }
                    )
                }

                2 -> {
                    StationMessTabContent(
                        stationMessRecords = stationMessRecords,
                        approvedOfficers = approvedOfficers,
                        selectedMonth = selectedMessMonth,
                        onSelectMonth = { selectedMessMonth = it },
                        onShareReport = { shareMealReportText(it) }
                    )
                }

                3 -> {
                    StationContactsSection(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Assign / Edit Duty Dialog
    assigningOfficer?.let { officer ->
        val currentEdit = editingAssignment
        AssignDutyDialog(
            officer = officer,
            initialDate = currentEdit?.dateKey ?: assigningDate,
            initialDuty = currentEdit?.dutyNature ?: "Mobile Patrol (මුරසංචාරය)",
            initialShift = currentEdit?.shift ?: "08:00 - 16:00",
            initialRemarks = currentEdit?.remarks ?: "",
            isEditing = currentEdit != null,
            existingStationAssignments = stationAssignments,
            onDismiss = {
                assigningOfficer = null
                editingAssignment = null
            },
            onSubmit = { date, duty, shift, rem ->
                viewModel.assignDutyToOfficer(
                    officer = officer,
                    dateKey = date,
                    dutyNature = duty,
                    shift = shift,
                    remarks = rem,
                    existingAssignmentId = currentEdit?.id.orEmpty(),
                    onSuccess = {
                        assigningOfficer = null
                        editingAssignment = null
                    },
                    onError = {}
                )
            }
        )
    }

    // Logout Confirmation Dialog
    if (isLogoutConfirmOpen) {
        AlertDialog(
            onDismissRequest = { isLogoutConfirmOpen = false },
            title = { Text("පාලන අංශයෙන් ඉවත් වීම (Sign Out)") },
            text = { Text("ඔබට පාලන අංශ ගිණුමෙන් ඉවත් වීමට අවශ්‍ය බව සහතිකද?") },
            confirmButton = {
                Button(
                    onClick = {
                        isLogoutConfirmOpen = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text("ඉවත් වන්න")
                }
            },
            dismissButton = {
                TextButton(onClick = { isLogoutConfirmOpen = false }) {
                    Text("අවලංගු කරන්න")
                }
            }
        )
    }

    // Send SOS Dialog
    if (isSendSosDialogOpen) {
        SendEmergencySosDialog(
            viewModel = viewModel,
            onDismiss = { isSendSosDialogOpen = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignDutyTabContent(
    approvedOfficers: List<AppUser>,
    stationAssignments: List<DutyAssignment>,
    activeReserveHolder: ReserveHolderInfo?,
    onAssignDuty: (AppUser, String, DutyAssignment?) -> Unit,
    onDeleteAssignment: (String) -> Unit,
    onCall: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(DateTimeUtils.getTodayKey()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedRankFilter by remember { mutableStateOf("ALL") }
    var isRankGuideExpanded by remember { mutableStateOf(false) }

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
        },
        Calendar.getInstance().get(Calendar.YEAR),
        Calendar.getInstance().get(Calendar.MONTH),
        Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    )

    val rankFilters = listOf(
        "ALL" to "සියල්ල",
        "Inspector" to "IP / CI",
        "Sub Inspector" to "SI / WSI",
        "Sergeant" to "PS / WPS",
        "Constable" to "PC / WPC"
    )

    val assignmentsForDate = remember(stationAssignments, selectedDate) {
        stationAssignments.filter { it.dateKey == selectedDate }
    }

    val filteredOfficers = remember(approvedOfficers, searchQuery, selectedRankFilter) {
        approvedOfficers.filter { off ->
            val matchQuery = searchQuery.isBlank() ||
                off.displayName.contains(searchQuery, ignoreCase = true) ||
                off.badgeNumber.contains(searchQuery, ignoreCase = true) ||
                off.rank.contains(searchQuery, ignoreCase = true)
            val matchRank = when (selectedRankFilter) {
                "ALL" -> true
                "Inspector" -> off.rank.contains("Inspector", ignoreCase = true) && !off.rank.contains("Sub Inspector", ignoreCase = true)
                else -> off.rank.contains(selectedRankFilter, ignoreCase = true)
            }
            matchQuery && matchRank
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Active Station Reserve Live Status Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (activeReserveHolder != null) BadgeGold.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, if (activeReserveHolder != null) BadgeGold else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (activeReserveHolder != null) PoliceThemeColors.goldText else PoliceThemeColors.mutedText,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "සක්‍රීය උපසේවා නිලධාරියා (Live Station Reserve)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PoliceThemeColors.goldText
                        )
                        Text(
                            text = if (activeReserveHolder != null) {
                                "${activeReserveHolder.rank.ifBlank { "Officer" }} ${activeReserveHolder.officerName} • ආරම්භය: ${DateTimeUtils.formatTime(activeReserveHolder.sinceTimestamp)}"
                            } else {
                                "දැනට කිසිවෙකු උපසේවා (Station Reserve) රාජකාරියේ නොමැත"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Date Selector Row
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("රාජකාරි දිනය තෝරන්න", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (selectedDate == DateTimeUtils.getTodayKey()) "අද ($selectedDate)" else selectedDate,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PoliceThemeColors.navyText
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { selectedDate = DateTimeUtils.getTodayKey() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PoliceThemeColors.navyText)
                        ) {
                            Text("අද", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { datePickerDialog.show() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PoliceNavyPrimary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("දින තෝරන්න", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Collapsible Rank-Based Expected Duties Guide
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isRankGuideExpanded = !isRankGuideExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = PoliceThemeColors.navyText, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "නිලයන් අනුව කළ යුතු රාජකාරි මාර්ගෝපදේශය",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PoliceThemeColors.navyText
                            )
                        }
                        Icon(
                            imageVector = if (isRankGuideExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Rank Guide",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(visible = isRankGuideExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DutySlotRules.rankDutyGuides.forEach { guide ->
                                val countInRank = approvedOfficers.count { off ->
                                    if (guide.filterKey == "Inspector") {
                                        off.rank.contains("Inspector", ignoreCase = true) && !off.rank.contains("Sub Inspector", ignoreCase = true)
                                    } else {
                                        off.rank.contains(guide.filterKey, ignoreCase = true)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = guide.sinhalaTitle,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PoliceThemeColors.navyText,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = PoliceNavyPrimary
                                            ) {
                                                Text(
                                                    text = "නිලධාරීන් $countInRank",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        guide.expectedDuties.forEach { d ->
                                            Text(
                                                text = "• $d",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface
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

        // Search & Rank Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("නිලධාරියා සොයන්න (නම, නිලය හෝ අංකය)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(rankFilters) { (key, label) ->
                        val isSelected = selectedRankFilter == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedRankFilter = key },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PoliceNavyPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // Assigned duties list for this date
        if (assignmentsForDate.isNotEmpty()) {
            item {
                Text(
                    text = "$selectedDate දින පවරා ඇති රාජකාරි (${assignmentsForDate.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PoliceThemeColors.navyText
                )
            }
            items(assignmentsForDate) { assign ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, DutyActiveGreen.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (DutySlotRules.isReserveDuty(assign.dutyNature)) BadgeGold else DutyActiveGreen
                                ) {
                                    Text(
                                        text = assign.dutyNature,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (DutySlotRules.isReserveDuty(assign.dutyNature)) PoliceNavyDark else Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(assign.shift, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${assign.officerRank.ifBlank { "Officer" }} • ${assign.officerName} (${assign.officerBadge.ifBlank { "N/A" }})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (assign.remarks.isNotBlank()) {
                                Text(text = "සටහන: ${assign.remarks}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onDeleteAssignment(assign.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = PoliceThemeColors.errorText)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "සියලුම නිලධාරීන් (${filteredOfficers.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items(filteredOfficers) { officer ->
            val assignedToOfficer = assignmentsForDate.firstOrNull { it.officerId == officer.uid }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = officer.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${officer.rank.ifBlank { "Officer" }} • අංකය: ${officer.badgeNumber.ifBlank { "N/A" }}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (assignedToOfficer != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = DutyActiveGreen.copy(alpha = 0.16f),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = "පවරා ඇත: ${assignedToOfficer.dutyNature} (${assignedToOfficer.shift})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PoliceThemeColors.greenText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "තවමත් රාජකාරියක් පවරා නොමැත",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (officer.phoneNumber.isNotBlank()) {
                            IconButton(onClick = { onCall(officer.phoneNumber) }) {
                                Icon(Icons.Default.Call, contentDescription = "Call", tint = PoliceThemeColors.greenText)
                            }
                        }
                        Button(
                            onClick = { onAssignDuty(officer, selectedDate, assignedToOfficer) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PoliceNavyPrimary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (assignedToOfficer != null) "වෙනස් කරන්න" else "පවරන්න",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingScheduleTabContent(
    approvedOfficers: List<AppUser>,
    stationAssignments: List<DutyAssignment>,
    onEditAssignment: (DutyAssignment) -> Unit,
    onDeleteAssignment: (String) -> Unit
) {
    val today = DateTimeUtils.getTodayKey()
    val upcoming = remember(stationAssignments) {
        stationAssignments.filter { it.dateKey >= today }.sortedBy { it.dateKey }
    }

    if (upcoming.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ඉදිරි දින සඳහා පවරන ලද රාජකාරි නොමැත.\n'රාජකාරි පැවරීම' Tab එකෙන් ඉදිරි දිනයක් තෝරා රාජකාරි පවරන්න.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    } else {
        val groupedByDate = remember(upcoming) { upcoming.groupBy { it.dateKey } }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            groupedByDate.forEach { (dateKey, list) ->
                item {
                    Text(
                        text = if (dateKey == today) "📅 අද දින රාජකාරි ලේඛනය ($dateKey • ${list.size})" else "📅 $dateKey දින රාජකාරි ලේඛනය (${list.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PoliceThemeColors.navyText
                    )
                }
                items(list) { item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isReserve = DutySlotRules.isReserveDuty(item.dutyNature)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isReserve) BadgeGold else PoliceNavyPrimary
                                    ) {
                                        Text(
                                            text = item.dutyNature,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isReserve) PoliceNavyDark else Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(item.shift, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${item.officerRank.ifBlank { "Officer" }} • ${item.officerName} (${item.officerBadge.ifBlank { "No Badge" }})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (item.remarks.isNotBlank()) {
                                    Text(text = "සටහන: ${item.remarks}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row {
                                IconButton(onClick = { onEditAssignment(item) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Assignment", tint = PoliceThemeColors.navyText)
                                }
                                IconButton(onClick = { onDeleteAssignment(item.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = PoliceThemeColors.errorText)
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
private fun StationMessTabContent(
    stationMessRecords: List<MessRecord>,
    approvedOfficers: List<AppUser>,
    selectedMonth: String,
    onSelectMonth: (String) -> Unit,
    onShareReport: (String) -> Unit
) {
    var todayOnlyMode by remember { mutableStateOf(false) }
    val todayKey = DateTimeUtils.getTodayKey()

    fun shiftMonth(monthKey: String, delta: Int): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val cal = Calendar.getInstance()
            cal.time = sdf.parse(monthKey) ?: return DateTimeUtils.getCurrentMonthKey()
            cal.add(Calendar.MONTH, delta)
            sdf.format(cal.time)
        } catch (_: Exception) {
            DateTimeUtils.getCurrentMonthKey()
        }
    }

    val deduplicatedRecords = remember(stationMessRecords) {
        stationMessRecords
            .sortedByDescending { it.timestamp }
            .distinctBy { Triple(it.officerId, it.date, it.mealType) }
    }

    val filteredRecords = remember(deduplicatedRecords, selectedMonth, todayOnlyMode, todayKey) {
        deduplicatedRecords.filter {
            val dateMatches = if (todayOnlyMode) it.date == todayKey else it.date.startsWith(selectedMonth)
            dateMatches && it.isTaken
        }
    }

    val totalBreakfast = remember(filteredRecords) { filteredRecords.count { it.mealType == "BREAKFAST" } }
    val totalLunch = remember(filteredRecords) { filteredRecords.count { it.mealType == "LUNCH" } }
    val totalDinner = remember(filteredRecords) { filteredRecords.count { it.mealType == "DINNER" } }
    val totalCost = remember(filteredRecords) { filteredRecords.sumOf { it.price } }

    // Officer breakdown
    val officerSummaries = remember(filteredRecords, approvedOfficers) {
        approvedOfficers.map { off ->
            val offRecs = filteredRecords.filter { it.officerId == off.uid }
            val bCount = offRecs.count { it.mealType == "BREAKFAST" }
            val lCount = offRecs.count { it.mealType == "LUNCH" }
            val dCount = offRecs.count { it.mealType == "DINNER" }
            val sumCost = offRecs.sumOf { it.price }
            OfficerMealSummary(
                officer = off,
                breakfastCount = bCount,
                lunchCount = lCount,
                dinnerCount = dCount,
                totalMeals = offRecs.size,
                totalCost = sumCost
            )
        }.sortedByDescending { it.totalMeals }
    }

    fun generateShareText(): String {
        val periodLabel = if (todayOnlyMode) "අද ($todayKey)" else selectedMonth
        val sb = StringBuilder()
        sb.append("ශ්‍රී ලංකා පොලීසිය - මෙස් ආහාර සාරාංශ වාර්තාව ($periodLabel)\n")
        sb.append("=====================================\n")
        sb.append("මුළු උදෑසන ආහාර වේල්: $totalBreakfast\n")
        sb.append("මුළු දිවා ආහාර වේල්: $totalLunch\n")
        sb.append("මුළු රාත්‍රී ආහාර වේල්: $totalDinner\n")
        sb.append("මුළු ආහාර වේල් ගණන: ${filteredRecords.size}\n")
        sb.append("මුළු පිරිවැය: රු. ${"%.2f".format(totalCost)}\n")
        sb.append("=====================================\n")
        sb.append("නිලධාරීන් අනුව විස්තරය:\n")
        officerSummaries.forEachIndexed { idx, s ->
            if (s.totalMeals > 0) {
                sb.append("${idx + 1}. ${s.officer.displayName} (${s.officer.badgeNumber.ifBlank { s.officer.rank }}): ")
                sb.append("උදෑසන ${s.breakfastCount}, දිවා ${s.lunchCount}, රාත්‍රී ${s.dinnerCount} | වේල්: ${s.totalMeals} (රු. ${"%.0f".format(s.totalCost)})\n")
            }
        }
        sb.append("=====================================\n")
        sb.append("පාලන අංශය මගින් නිකුත් කරන ලදී - All In One Police App")
        return sb.toString()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Month Selector, Today Toggle & Share Button
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    todayOnlyMode = false
                                    onSelectMonth(shiftMonth(selectedMonth, -1))
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month", tint = PoliceThemeColors.navyText)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("වාර්තා කාලසීමාව", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (todayOnlyMode) "අද ($todayKey)" else selectedMonth,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PoliceThemeColors.navyText
                                )
                            }
                            IconButton(
                                onClick = {
                                    todayOnlyMode = false
                                    onSelectMonth(shiftMonth(selectedMonth, 1))
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month", tint = PoliceThemeColors.navyText)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = todayOnlyMode,
                                onClick = { todayOnlyMode = !todayOnlyMode },
                                label = { Text("අද", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PoliceNavyPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                            Button(
                                onClick = { onShareReport(generateShareText()) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DutyActiveGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Summary Counters
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BadgeGold.copy(alpha = 0.16f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("උදෑසන", fontSize = 11.sp, color = PoliceThemeColors.goldText, fontWeight = FontWeight.SemiBold)
                        Text(totalBreakfast.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DutyActiveGreen.copy(alpha = 0.16f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("දිවා", fontSize = 11.sp, color = PoliceThemeColors.greenText, fontWeight = FontWeight.SemiBold)
                        Text(totalLunch.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = PoliceNavyPrimary.copy(alpha = 0.16f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("රාත්‍රී", fontSize = 11.sp, color = PoliceThemeColors.navyText, fontWeight = FontWeight.SemiBold)
                        Text(totalDinner.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Card(
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("මුළු වියදම", fontSize = 11.sp, color = Color(0xFFFCD34D), fontWeight = FontWeight.SemiBold)
                        Text("රු. ${totalCost.toInt()}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        item {
            Text(
                text = "නිලධාරීන් අනුව ලබාගත් ආහාර ලැයිස්තුව (${officerSummaries.count { it.totalMeals > 0 }} / ${officerSummaries.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items(officerSummaries) { s ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.officer.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${s.officer.rank.ifBlank { "Officer" }} • ${s.officer.badgeNumber.ifBlank { "N/A" }}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("උදෑසන: ${s.breakfastCount}", fontSize = 11.sp, color = PoliceThemeColors.goldText, fontWeight = FontWeight.Medium)
                            Text("දිවා: ${s.lunchCount}", fontSize = 11.sp, color = PoliceThemeColors.greenText, fontWeight = FontWeight.Medium)
                            Text("රාත්‍රී: ${s.dinnerCount}", fontSize = 11.sp, color = PoliceThemeColors.navyText, fontWeight = FontWeight.Medium)
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DutyActiveGreen.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "වේල් ${s.totalMeals}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PoliceThemeColors.greenText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "රු. ${s.totalCost.toInt()}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private data class OfficerMealSummary(
    val officer: AppUser,
    val breakfastCount: Int,
    val lunchCount: Int,
    val dinnerCount: Int,
    val totalMeals: Int,
    val totalCost: Double
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignDutyDialog(
    officer: AppUser,
    initialDate: String,
    initialDuty: String,
    initialShift: String,
    initialRemarks: String,
    isEditing: Boolean = false,
    existingStationAssignments: List<DutyAssignment>,
    onDismiss: () -> Unit,
    onSubmit: (date: String, duty: String, shift: String, remarks: String) -> Unit
) {
    val context = LocalContext.current
    var dateKey by remember { mutableStateOf(initialDate) }
    var dutyNature by remember { mutableStateOf(initialDuty) }

    // Parse start / end time from initialShift if formatted like "08:00 - 16:00"
    var startTimeStr by remember {
        val match = Regex("(\\d{2}:\\d{2})\\s*-\\s*(\\d{2}:\\d{2})").find(initialShift)
        mutableStateOf(match?.groupValues?.getOrNull(1) ?: "08:00")
    }
    var endTimeStr by remember {
        val match = Regex("(\\d{2}:\\d{2})\\s*-\\s*(\\d{2}:\\d{2})").find(initialShift)
        mutableStateOf(match?.groupValues?.getOrNull(2) ?: "16:00")
    }
    var shiftLabel by remember { mutableStateOf(initialShift.ifBlank { "08:00 - 16:00" }) }
    var remarks by remember { mutableStateOf(initialRemarks) }
    var warningMessage by remember { mutableStateOf<String?>(null) }

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            dateKey = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
        },
        Calendar.getInstance().get(Calendar.YEAR),
        Calendar.getInstance().get(Calendar.MONTH),
        Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    )

    fun showTimePicker(currentTime: String, onPicked: (String) -> Unit) {
        val parts = currentTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                onPicked(String.format("%02d:%02d", hourOfDay, minute))
            },
            h,
            m,
            true
        ).show()
    }

    val dutySuggestions = listOf(
        "Station Reserve (උපසේවා)",
        "Duty Officer (රාජකාරි භාර නිලධාරී)",
        "Charge Room Officer (චෝදනාගාර භාර)",
        "Mobile Patrol (මුරසංචාරය)",
        "Check Point (මාර්ග බාධක)",
        "Guard Duty (මුර රාජකාරි)",
        "Traffic Duty (රථවාහන)",
        "Court Duty (උසාවි රාජකාරි)",
        "Investigation (විමර්ශන)",
        "Desk / Complaints (පැමිණිලි / ඩෙස්ක්)",
        "VIP Security (විශේෂ ආරක්ෂක)"
    )

    val shiftPresets = listOf(
        Triple("දිවා මුරය", "08:00", "16:00"),
        Triple("සවස් මුරය", "16:00", "00:00"),
        Triple("රාත්‍රී මුරය", "00:00", "08:00"),
        Triple("පැය 24 මුරය", "08:00", "08:00")
    )

    fun checkSingleSlotConflict(nature: String, date: String): Boolean {
        val category = DutySlotRules.getSingleSlotCategory(nature)
        if (category == null) {
            warningMessage = null
            return false
        }
        val existing = existingStationAssignments.firstOrNull {
            it.dateKey == date &&
                DutySlotRules.getSingleSlotCategory(it.dutyNature) == category &&
                it.officerId != officer.uid
        }
        return if (existing != null) {
            val slotTitle = DutySlotRules.getCategoryDisplayName(category)
            warningMessage = "අවවාදයයි: $date දින සඳහා දැනටමත් [${existing.officerName}] $slotTitle රාජකාරියට පත් කර ඇත. එක් පොලිස් ස්ථානයකට මෙම රාජකාරිය සඳහා එක් නිලධාරියෙකු පමණක් සිටිය හැක."
            true
        } else {
            warningMessage = null
            false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEditing) "රාජකාරිය වෙනස් කරන්න" else "රාජකාරි පැවරීම",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = PoliceThemeColors.navyText
                        )
                        Text(
                            text = "${officer.displayName} (${officer.rank.ifBlank { "Officer" }})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Date Selector
                OutlinedButton(
                    onClick = { datePickerDialog.show() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PoliceThemeColors.navyText)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("රාජකාරි දිනය: $dateKey", fontWeight = FontWeight.Bold)
                }

                // Duty Nature Chips + Custom Input
                Text("රාජකාරියේ ස්වභාවය (Duty Nature):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dutySuggestions.forEach { sug ->
                        val isSelected = dutyNature.equals(sug, ignoreCase = true)
                        val isSingleSlot = DutySlotRules.getSingleSlotCategory(sug) != null
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                dutyNature = sug
                                checkSingleSlotConflict(sug, dateKey)
                            },
                            label = { Text(sug, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (isSingleSlot) BadgeGold else PoliceNavyPrimary,
                                selectedLabelColor = if (isSingleSlot) PoliceNavyDark else Color.White
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = dutyNature,
                    onValueChange = {
                        dutyNature = it
                        checkSingleSlotConflict(it, dateKey)
                    },
                    label = { Text("රාජකාරිය (අවශ්‍ය නම් වෙනස් කර ලියන්න)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (warningMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(warningMessage!!, fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer, lineHeight = 15.sp)
                        }
                    }
                }

                // Exact Duty Time Pickers + Presets
                Text("රාජකාරි වේලාව සහ මුරය (Duty Time / Shift):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showTimePicker(startTimeStr) { picked ->
                                startTimeStr = picked
                                shiftLabel = "$picked - $endTimeStr"
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PoliceThemeColors.navyText),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ආරම්භය: $startTimeStr", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            showTimePicker(endTimeStr) { picked ->
                                endTimeStr = picked
                                shiftLabel = "$startTimeStr - $picked"
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PoliceThemeColors.navyText),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("අවසානය: $endTimeStr", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    shiftPresets.forEach { (title, sTime, eTime) ->
                        val full = "$title ($sTime - $eTime)"
                        FilterChip(
                            selected = shiftLabel == full || (startTimeStr == sTime && endTimeStr == eTime),
                            onClick = {
                                startTimeStr = sTime
                                endTimeStr = eTime
                                shiftLabel = full
                            },
                            label = { Text(full, fontSize = 11.sp) }
                        )
                    }
                }

                // Remarks
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("විශේෂ උපදෙස් හෝ ස්ථානය (Remarks / Instructions)") },
                    placeholder = { Text("උදා: අධිකරණ රාජකාරි සඳහා පිටත්ව යන්න / රථවාහන පරීක්ෂාව...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val hasConflict = checkSingleSlotConflict(dutyNature, dateKey)
                        if (!hasConflict && dutyNature.isNotBlank()) {
                            onSubmit(dateKey, dutyNature, shiftLabel, remarks)
                        }
                    },
                    enabled = dutyNature.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PoliceNavyPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEditing) "වෙනස්කම් සුරකින්න (Update & Notify)" else "රාජකාරිය පවරන්න (Assign & Send Notice)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminMetricCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )
        }
    }
}
