package com.example.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppUser
import com.example.data.DutyRecord
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceThemeColors
import com.example.util.DateTimeUtils
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OICDashboardScreen(
    viewModel: PoliceViewModel
) {
    val context = LocalContext.current
    val appUser by viewModel.appUser.collectAsStateWithLifecycle()
    val subordinates by viewModel.subordinates.collectAsStateWithLifecycle()
    val stationContacts by viewModel.stationContacts.collectAsStateWithLifecycle()
    val stationDutyRecords by viewModel.stationDutyRecords.collectAsStateWithLifecycle()
    val stationLocationHistory by viewModel.stationLocationHistory.collectAsStateWithLifecycle()
    val emergencyAlerts by viewModel.nearbyEmergencyAlerts.collectAsStateWithLifecycle()
    val myLocation by viewModel.myCurrentLocation.collectAsStateWithLifecycle()
    val myLocationAccuracy by viewModel.myLocationAccuracyMeters.collectAsStateWithLifecycle()
    val activeRouteState by viewModel.activeRouteState.collectAsStateWithLifecycle()
    val selectedMapDate by viewModel.selectedMapDate.collectAsStateWithLifecycle()
    val activeEmergencyPopup by viewModel.activeEmergencyPopup.collectAsStateWithLifecycle()
    val isRouteMapDialogOpen by viewModel.isRouteMapDialogOpen.collectAsStateWithLifecycle()
    val unreadNotifCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadChatCount by viewModel.unreadChatCount.collectAsStateWithLifecycle()
    val isMultiDayShiftMode by viewModel.mapMultiDayShiftMode.collectAsStateWithLifecycle()
    val stationAdminSeed by viewModel.stationAdminSeed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val dashboardListState = rememberLazyListState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var isDetailDialogOpen by remember { mutableStateOf(false) }
    var isStationTransferDialogOpen by remember { mutableStateOf(false) }
    var isLogoutConfirmOpen by remember { mutableStateOf(false) }
    var isNotificationsDialogOpen by remember { mutableStateOf(false) }
    var isSendNotificationDialogOpen by remember { mutableStateOf(false) }
    var isSendSosDialogOpen by remember { mutableStateOf(false) }
    var isDirectoryOpen by remember { mutableStateOf(false) }
    var isChatDialogOpen by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<AppUser?>(null) }

    // Hoisted Tab 1 (Duty Roster) state
    val todayKey = DateTimeUtils.getTodayKey()
    var showPastDutyMode by remember { mutableStateOf(false) }
    val availablePastDates = remember(stationDutyRecords, todayKey) {
        stationDutyRecords
            .map { DateTimeUtils.formatDate(it.timestamp) }
            .filter { it.isNotBlank() && it < todayKey }
            .distinct()
            .sortedDescending()
    }
    var selectedPastDate by remember(availablePastDates) {
        mutableStateOf(availablePastDates.firstOrNull() ?: todayKey)
    }
    val calendar = remember { Calendar.getInstance() }
    val dutyDatePicker = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = "%04d-%02d-%02d".format(year, month + 1, dayOfMonth)
                selectedPastDate = formatted
                showPastDutyMode = true
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    // Hoisted Tab 4 (Station Contacts) state
    var contactSearchQuery by remember { mutableStateOf("") }
    var contactRoleFilter by remember { mutableStateOf(StationContactRoleFilter.ALL) }
    var isEditOwnContactOpen by remember { mutableStateOf(false) }
    val filteredContacts = remember(stationContacts, contactSearchQuery, contactRoleFilter) {
        filterStationContacts(stationContacts, contactSearchQuery, contactRoleFilter)
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Granted or denied gracefully */ }

    val oicLocationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.refreshCurrentLocation(showFeedback = false)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            oicLocationPermLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            viewModel.refreshCurrentLocation(showFeedback = false)
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 4) {
            viewModel.refreshCurrentLocation(showFeedback = false)
        }
    }

    val pendingList = remember(subordinates) {
        subordinates.filter { it.status == "PENDING" && !it.hiddenFromStationLists }
    }
    val approvedOfficers = remember(subordinates) {
        subordinates.filter {
            it.status == "APPROVED" && it.role != "ADMIN_BRANCH" && !it.hiddenFromStationLists
        }
    }
    val adminBranchMembers = remember(subordinates) {
        subordinates.filter {
            it.status == "APPROVED" && it.role == "ADMIN_BRANCH" && !it.hiddenFromStationLists
        }
    }
    val onDutyList = remember(approvedOfficers) {
        approvedOfficers.filter { it.isOnDuty && !it.hideDutyStatus }
    }

    var lastPendingCount by remember { mutableIntStateOf(pendingList.size) }

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
                DirectoryScreen(viewModel = viewModel, initialTab = 1)
            }
        }
        return
    }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(pendingList.size) {
        if (pendingList.size > lastPendingCount) {
            snackbarHostState.showSnackbar("🔔 නව නිලධාරියෙකුගේ ලියාපදිංචි ඉල්ලීමක් ලැබුණි!")
        }
        lastPendingCount = pendingList.size
    }

    val oicCode = appUser?.oicStationCode ?: "OIC-STATION"

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
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = PoliceNavyDark,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "OIC Dashboard",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (appUser?.stationName?.isNotBlank() == true) appUser?.stationName ?: "" else "ස්ථානාධිපති පාලක පුවරුව",
                                fontSize = 11.sp,
                                color = BadgeGold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isSendSosDialogOpen = true },
                        modifier = Modifier.testTag("oic_sos_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Emergency SOS",
                            tint = Color(0xFFFCA5A5)
                        )
                    }
                    IconButton(
                        onClick = { isDirectoryOpen = true },
                        modifier = Modifier.testTag("oic_directory_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContactPhone,
                            contentDescription = "Police Directory",
                            tint = BadgeGold
                        )
                    }
                    ChatTopBarButton(
                        unreadCount = unreadChatCount,
                        tint = BadgeGold,
                        onClick = { isChatDialogOpen = true }
                    )
                    IconButton(onClick = { isSendNotificationDialogOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Send Notification",
                            tint = BadgeGold
                        )
                    }
                    NotificationBellButton(
                        unreadCount = unreadNotifCount,
                        tint = BadgeGold,
                        onClick = { isNotificationsDialogOpen = true }
                    )
                    IconButton(onClick = { isStationTransferDialogOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Transfer & Handover",
                            tint = BadgeGold
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PoliceNavyDark
                )
            )
        }
    ) { padding ->
        val targetDateKey = if (!showPastDutyMode) todayKey else selectedPastDate
        val recordsOnTargetDate = remember(stationDutyRecords, targetDateKey) {
            stationDutyRecords.filter { DateTimeUtils.formatDate(it.timestamp) == targetDateKey }
        }
        val rosterEntries = remember(approvedOfficers, recordsOnTargetDate, showPastDutyMode) {
            val groupedByOfficer = recordsOnTargetDate.groupBy { it.officerId }
            val officerMap = approvedOfficers.associateBy { it.uid }
            val entries = mutableListOf<Pair<AppUser, List<DutyRecord>>>()

            groupedByOfficer.forEach { (uid, recs) ->
                val user = officerMap[uid]
                if (user != null) {
                    entries.add(user to recs.sortedByDescending { it.timestamp })
                }
            }
            if (!showPastDutyMode) {
                approvedOfficers.filter { it.isOnDuty && !it.hideDutyStatus && groupedByOfficer[it.uid] == null }.forEach { onDutyOfficer ->
                    entries.add(onDutyOfficer to emptyList())
                }
            }
            entries
        }

        LazyColumn(
            state = dashboardListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .testTag("oic_dashboard_scroll"),
            contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Prominent OIC Unique Code Banner
            item(key = "oic_header_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PoliceNavyDark),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = BadgeGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ඔබගේ ප්‍රධාන OIC කේතය (Station Code)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BadgeGold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = oicCode,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("OIC Station Code", oicCode)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "OIC කේතය ($oicCode) පිටපත් විය!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = BadgeGold,
                                        contentColor = PoliceNavyDark
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("පිටපත්", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "ශ්‍රී ලංකා පොලීසිය - අපගේ ස්ථානාධිපති (OIC) Station Code අංකය: $oicCode\nකරුණාකර All In One Police ඇප් එකේ ලියාපදිංචි වීමේදී මෙම කේතය ඇතුළත් කරන්න."
                                            )
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "OIC කේතය බෙදාගන්න"))
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "මෙම අංකය ඔබ යටතේ සේවය කරන නිලධාරීන්ට ලබා දෙන්න. ඔවුන් ලියාපදිංචි වීමේදී මෙම අංකය ඇතුළත් කළ යුතුය.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 14.sp
                        )

                        val todayAdminPasscode = remember(oicCode, stationAdminSeed, subordinates) {
                            viewModel.getTodayAdminPasscode(customStationCode = oicCode, customSeed = stationAdminSeed)
                        }

                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = BadgeGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "අද දින පාලන අංශ රහස් අංකය (Admin PIN)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (stationAdminSeed.isNotBlank()) {
                                            "පාලන අංශ මුරපදය මත දිනපතා වෙනස් වේ (OIC ට පමණි)"
                                        } else {
                                            "දිනපතා ස්වයංක්‍රීයව වෙනස් වේ (OIC ට පමණි)"
                                        },
                                        fontSize = 10.sp,
                                        color = Color(0xFFFCD34D)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DutyActiveGreen.copy(alpha = 0.25f),
                                    border = BorderStroke(1.dp, DutyActiveGreen)
                                ) {
                                    Text(
                                        text = todayAdminPasscode,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        letterSpacing = 2.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Admin Daily Passcode", todayAdminPasscode)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "පාලන අංශ රහස් අංකය ($todayAdminPasscode) පිටපත් විය!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Passcode", tint = BadgeGold, modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "ශ්‍රී ලංකා පොලීසිය - අද දින පාලන අංශ රහස් අංකය (Admin Branch Passcode): $todayAdminPasscode\nස්ථානාධිපති (OIC) මගින් නිකුත් කරන ලදී."
                                            )
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "පාලන අංශ රහස් අංකය බෙදාගන්න"))
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share Passcode", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { isStationTransferDialogOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BadgeGold),
                            border = BorderStroke(1.dp, BadgeGold.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ස්ථාන මාරුවීම් හා නිලධාරීන් පැවරීම (Station Handover)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. Quick Counter Metrics
            item(key = "oic_metrics_row") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = "මුළු නිලධාරීන්",
                        value = approvedOfficers.size.toString(),
                        color = PoliceThemeColors.navyText,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "දැනට රාජකාරියේ",
                        value = onDutyList.size.toString(),
                        color = PoliceThemeColors.greenText,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "අනුමැති ඉල්ලීම්",
                        value = pendingList.size.toString(),
                        color = if (pendingList.isNotEmpty()) PoliceThemeColors.orangeText else PoliceThemeColors.mutedText,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Google Sheet Directory Quick Access Button for OIC
            item(key = "oic_directory_btn") {
                Button(
                    onClick = { isDirectoryOpen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .testTag("oic_directory_banner_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PoliceNavyPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.ContactPhone, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFFCD34D))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("පොලිස් දුරකථන නාමාවලිය (Google Sheet Directory)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 3b. In-App Latest Notice Banner
            item(key = "oic_notice_banner") {
                LatestInAppNoticeBanner(
                    notifications = notifications,
                    unreadCount = unreadNotifCount,
                    onOpenInbox = { isNotificationsDialogOpen = true },
                    onSendNotice = { isSendNotificationDialogOpen = true },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            // 4. Emergency SOS Banner
            if (emergencyAlerts.isNotEmpty()) {
                item(key = "oic_sos_banner") {
                    EmergencySosBanner(
                        alerts = emergencyAlerts,
                        myLocation = myLocation,
                        myUid = appUser?.uid ?: "",
                        isOic = true,
                        onClickAlertLocation = { alert ->
                            selectedTab = 4
                            viewModel.navigateToLocationOnMap(
                                target = MapNavigationTarget(
                                    alertId = alert.id,
                                    title = "🚨 හදිසි SOS: ${alert.senderName}",
                                    subtitle = alert.message,
                                    lat = alert.lat,
                                    lng = alert.lng,
                                    phone = alert.senderPhone
                                ),
                                openDialog = false
                            )
                        },
                        onResolveAlert = { viewModel.resolveEmergencyAlert(it) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }

            // 5. Tab Navigation (6 Tabs: Officers, Admin Branch, Duty Roster, Requests, Map, Station Contacts)
            item(key = "oic_tabs_row") {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = PoliceThemeColors.navyText,
                    edgePadding = 12.dp
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("නිලධාරීන් (${approvedOfficers.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("oic_admin_branch_tab"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("පාලන අංශය (${adminBranchMembers.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("රාජකාරි දින", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ඉල්ලීම්", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                if (pendingList.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge(containerColor = DutyOffOrange) {
                                        Text(pendingList.size.toString(), color = Color.White)
                                    }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("සිතියම", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 5,
                        onClick = { selectedTab = 5 },
                        modifier = Modifier.testTag("oic_station_contacts_tab"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("සබඳතා (${stationContacts.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }

            // 6. Active Tab Content directly inside the single scrollable LazyColumn
            when (selectedTab) {
                0 -> {
                    if (approvedOfficers.isEmpty()) {
                        item(key = "approved_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "තවමත් සම්බන්ධ වූ නිලධාරීන් නොමැත.\nඔබගේ OIC කේතය ($oicCode) නිලධාරීන්ට ලබා දෙන්න.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(approvedOfficers, key = { "approved_${it.uid}" }) { officer ->
                            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                                SubordinateOfficerCard(
                                    officer = officer,
                                    onCall = { dialPhone(it) },
                                    onRemove = { memberToRemove = officer },
                                    onClick = {
                                        viewModel.selectOfficer(officer)
                                        isDetailDialogOpen = true
                                    }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    if (adminBranchMembers.isEmpty()) {
                        item(key = "admin_branch_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "දැනට මෙම පොලිස් ස්ථානයට අනුයුක්ත පාලන අංශ සාමාජිකයින් (Admin Branch) නොමැත.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(adminBranchMembers, key = { "admin_branch_${it.uid}" }) { member ->
                            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                                SubordinateOfficerCard(
                                    officer = member,
                                    isAdminBranch = true,
                                    onCall = { dialPhone(it) },
                                    onRemove = { memberToRemove = member },
                                    onClick = {
                                        viewModel.selectOfficer(member)
                                        isDetailDialogOpen = true
                                    }
                                )
                            }
                        }
                    }
                }

                2 -> {
                    dutyRosterByDateItems(
                        todayKey = todayKey,
                        showPastMode = showPastDutyMode,
                        selectedPastDate = selectedPastDate,
                        targetDateKey = targetDateKey,
                        availablePastDates = availablePastDates,
                        rosterEntries = rosterEntries,
                        onShowToday = { showPastDutyMode = false },
                        onShowPast = {
                            showPastDutyMode = true
                            if (selectedPastDate == todayKey && availablePastDates.isNotEmpty()) {
                                selectedPastDate = availablePastDates.first()
                            }
                        },
                        onSelectPastDate = { selectedPastDate = it },
                        onOpenDatePicker = { dutyDatePicker.show() },
                        onCall = { dialPhone(it) },
                        onSelectOfficer = { officer ->
                            viewModel.selectOfficer(officer)
                            isDetailDialogOpen = true
                        }
                    )
                }

                3 -> {
                    if (pendingList.isEmpty()) {
                        item(key = "pending_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "අනුමැතිය සඳහා නව ඉල්ලීම් නොමැත.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(pendingList, key = { "pending_${it.uid}" }) { officer ->
                            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                                PendingRequestCard(
                                    officer = officer,
                                    onApprove = { viewModel.approveOfficer(officer.uid) },
                                    onReject = { viewModel.rejectOfficer(officer.uid) }
                                )
                            }
                        }
                    }
                }

                4 -> {
                    item(key = "oic_live_map") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(520.dp)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            PoliceLiveMapView(
                                officers = approvedOfficers.filter { !it.hiddenOnMap },
                                locationHistory = stationLocationHistory,
                                emergencyAlerts = emergencyAlerts,
                                myLocation = myLocation,
                                myLocationAccuracyMeters = myLocationAccuracy,
                                activeRouteState = activeRouteState,
                                selectedDateKey = selectedMapDate,
                                isOicView = true,
                                isMultiDayShiftMode = isMultiDayShiftMode,
                                onToggleMultiDayShiftMode = { viewModel.setMapMultiDayShiftMode(it) },
                                onSelectDate = { viewModel.selectMapDate(it) },
                                onNavigateToTarget = { target ->
                                    viewModel.navigateToLocationOnMap(target, openDialog = false)
                                },
                                onClearRoute = { viewModel.clearMapRoute() },
                                onResolveEmergency = { viewModel.resolveEmergencyAlert(it) },
                                onRefreshGps = { viewModel.refreshCurrentLocation(showFeedback = true) },
                                onOpenEmergencySender = { isSendSosDialogOpen = true },
                                onExpandFullscreen = { viewModel.openRouteMapDialog() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                5 -> {
                    stationContactsItems(
                        appUser = appUser,
                        stationContacts = stationContacts,
                        filteredContacts = filteredContacts,
                        searchQuery = contactSearchQuery,
                        onSearchQueryChange = { contactSearchQuery = it },
                        roleFilter = contactRoleFilter,
                        onRoleFilterChange = { contactRoleFilter = it },
                        onCall = { dialStationContactPhone(context, it) },
                        onWhatsApp = { openStationContactWhatsApp(context, it) },
                        onCopy = { phone, ownerName -> copyStationContactPhone(context, phone, ownerName) },
                        onEditOwn = { isEditOwnContactOpen = true },
                        itemModifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }

    if (isEditOwnContactOpen && appUser != null) {
        EditOwnStationContactDialog(
            initialPhone = appUser?.phoneNumber.orEmpty(),
            initialBadge = appUser?.badgeNumber.orEmpty(),
            initialRank = appUser?.rank.orEmpty(),
            onDismiss = { isEditOwnContactOpen = false },
            onSave = { phone, badge, rank ->
                viewModel.updateOwnContactInfo(
                    phoneNumber = phone,
                    badgeNumber = badge,
                    rank = rank,
                    onSuccess = { isEditOwnContactOpen = false }
                )
            }
        )
    }

    if (isDetailDialogOpen) {
        OfficerDetailDialog(
            viewModel = viewModel,
            onDismiss = { isDetailDialogOpen = false }
        )
    }

    if (isStationTransferDialogOpen) {
        OICStationTransferDialog(
            viewModel = viewModel,
            onDismiss = { isStationTransferDialogOpen = false }
        )
    }

    if (isNotificationsDialogOpen) {
        NotificationsInboxDialog(
            viewModel = viewModel,
            isOic = true,
            onOpenSendDialog = { isSendNotificationDialogOpen = true },
            onDismiss = { isNotificationsDialogOpen = false }
        )
    }

    if (isSendNotificationDialogOpen) {
        SendNotificationDialog(
            viewModel = viewModel,
            subordinates = approvedOfficers + adminBranchMembers,
            onDismiss = { isSendNotificationDialogOpen = false }
        )
    }

    if (isChatDialogOpen) {
        StationChatDialog(
            viewModel = viewModel,
            onDismiss = { isChatDialogOpen = false }
        )
    }

    memberToRemove?.let { target ->
        OicPasscodeRemoveMemberDialog(
            member = target,
            onDismiss = { memberToRemove = null },
            onConfirm = { enteredPin, onErr ->
                viewModel.removeOfficerFromStationWithPasscode(
                    targetMember = target,
                    enteredPasscode = enteredPin,
                    onSuccess = { memberToRemove = null },
                    onError = onErr
                )
            }
        )
    }

    if (isSendSosDialogOpen) {
        SendEmergencySosDialog(
            viewModel = viewModel,
            onDismiss = { isSendSosDialogOpen = false }
        )
    }

    if (isRouteMapDialogOpen) {
        OfficerMapDialog(
            viewModel = viewModel,
            onOpenEmergencySender = { isSendSosDialogOpen = true },
            onDismiss = { viewModel.closeRouteMapDialog() }
        )
    }

    activeEmergencyPopup?.let { alert ->
        IncomingEmergencyPopupDialog(
            alert = alert,
            myLocation = myLocation,
            onShowRouteOnMap = {
                viewModel.dismissEmergencyPopup()
                selectedTab = 4
                viewModel.navigateToLocationOnMap(
                    target = MapNavigationTarget(
                        alertId = alert.id,
                        title = "🚨 හදිසි SOS: ${alert.senderName}",
                        subtitle = alert.message,
                        lat = alert.lat,
                        lng = alert.lng,
                        phone = alert.senderPhone
                    ),
                    openDialog = true
                )
            },
            onDismiss = { viewModel.dismissEmergencyPopup() }
        )
    }

    if (isLogoutConfirmOpen) {
        AlertDialog(
            onDismissRequest = { isLogoutConfirmOpen = false },
            title = { Text("ගිණුමෙන් ඉවත් වන්නද?", fontWeight = FontWeight.Bold) },
            text = {
                Text("ඔබ Sign Out වන තුරු ඔබගේ OIC ගිණුම ස්වයංක්‍රීයව සුරැකී පවතී. නැවත වෙනත් ගිණුමකින් පිවිසීමට අවශ්‍ය නම් පමණක් ඉවත් වන්න.")
            },
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
                    Text("ඉවත් වන්න (Sign Out)")
                }
            },
            dismissButton = {
                TextButton(onClick = { isLogoutConfirmOpen = false }) {
                    Text("රැඳී සිටින්න")
                }
            }
        )
    }
}

@Composable
private fun MetricCard(
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
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SubordinateOfficerCard(
    officer: AppUser,
    isAdminBranch: Boolean = false,
    onCall: (String) -> Unit,
    onRemove: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val dutyVisible = officer.isOnDuty && !officer.hideDutyStatus
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        when {
                            isAdminBranch -> BadgeGold.copy(alpha = 0.2f)
                            dutyVisible -> DutyActiveGreen.copy(alpha = 0.15f)
                            else -> Color.Gray.copy(alpha = 0.15f)
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAdminBranch) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                    contentDescription = null,
                    tint = when {
                        isAdminBranch -> PoliceThemeColors.goldText
                        dutyVisible -> PoliceThemeColors.greenText
                        else -> PoliceThemeColors.mutedText
                    },
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = officer.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isAdminBranch) {
                        "පාලන අංශය • ${officer.rank.ifBlank { "Admin Branch" }} • ${officer.badgeNumber.ifBlank { "N/A" }}"
                    } else {
                        "${officer.rank.ifBlank { "Officer" }} • ${officer.badgeNumber.ifBlank { "No badge" }}"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (officer.phoneNumber.isNotBlank()) {
                    Text(
                        text = "දුරකථන: ${officer.phoneNumber}",
                        fontSize = 11.sp,
                        color = PoliceThemeColors.greenText,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!officer.hiddenOnMap) {
                    officer.lastLocation?.let { loc ->
                        val lat = (loc["lat"] as? Number)?.toDouble() ?: 0.0
                        val lng = (loc["lng"] as? Number)?.toDouble() ?: 0.0
                        val ts = (loc["timestamp"] as? Number)?.toLong() ?: 0L
                        if (lat != 0.0) {
                            Text(
                                text = "GPS: ${String.format("%.4f", lat)}, ${String.format("%.4f", lng)} (${DateTimeUtils.formatTime(ts)})",
                                fontSize = 10.sp,
                                color = PoliceThemeColors.navyText
                            )
                        }
                    }
                }
            }

            if (officer.phoneNumber.isNotBlank()) {
                IconButton(
                    onClick = { onCall(officer.phoneNumber) },
                    modifier = Modifier
                        .size(40.dp)
                        .background(DutyActiveGreen.copy(alpha = 0.14f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call Officer",
                        tint = PoliceThemeColors.greenText,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), CircleShape)
                        .testTag("oic_remove_member_${officer.uid}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonRemove,
                        contentDescription = "Remove from station",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                    officer.hideDutyStatus -> Color(0xFF64748B)
                    isAdminBranch -> Color(0xFF0F766E)
                    officer.isOnDuty -> DutyActiveGreen
                    else -> Color(0xFF475569)
                }
            ) {
                Text(
                    text = when {
                        officer.hideDutyStatus -> "සඟවා ඇත"
                        isAdminBranch -> "පාලන අංශය"
                        officer.isOnDuty -> "ON DUTY"
                        else -> "OFF DUTY"
                    },
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun LazyListScope.dutyRosterByDateItems(
    todayKey: String,
    showPastMode: Boolean,
    selectedPastDate: String,
    targetDateKey: String,
    availablePastDates: List<String>,
    rosterEntries: List<Pair<AppUser, List<DutyRecord>>>,
    onShowToday: () -> Unit,
    onShowPast: () -> Unit,
    onSelectPastDate: (String) -> Unit,
    onOpenDatePicker: () -> Unit,
    onCall: (String) -> Unit,
    onSelectOfficer: (AppUser) -> Unit
) {
    item(key = "duty_roster_controls") {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !showPastMode,
                    onClick = onShowToday,
                    leadingIcon = { Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("අද දින ($todayKey)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = showPastMode,
                    onClick = onShowPast,
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("පසුගිය දින (${availablePastDates.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (showPastMode) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(availablePastDates) { dateStr ->
                            FilterChip(
                                selected = selectedPastDate == dateStr,
                                onClick = { onSelectPastDate(dateStr) },
                                label = { Text(dateStr, fontSize = 11.sp) }
                            )
                        }
                    }
                    IconButton(onClick = onOpenDatePicker) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date", tint = PoliceThemeColors.navyText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (!showPastMode) {
                    "අද දින රාජකාරි කළ / නියුතු නිලධාරීන් (${rosterEntries.size})"
                } else {
                    "$targetDateKey දින රාජකාරි කළ නිලධාරීන් (${rosterEntries.size})"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PoliceThemeColors.navyText
            )
        }
    }

    if (rosterEntries.isEmpty()) {
        item(key = "duty_roster_empty") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (!showPastMode) "අද දින තවමත් කිසිදු නිලධාරියෙකු රාජකාරි වාර්තා කර නොමැත." else "$targetDateKey දිනය සඳහා රාජකාරි වාර්තා හමු නොවීය.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
    } else {
        items(rosterEntries, key = { (officer, _) -> "roster_${targetDateKey}_${officer.uid}" }) { (officer, dayRecords) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clickable { onSelectOfficer(officer) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(
                    1.dp,
                    if (officer.isOnDuty && !officer.hideDutyStatus && !showPastMode) {
                        DutyActiveGreen.copy(alpha = 0.5f)
                    } else {
                        Color.Transparent
                    }
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(officer.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = "${officer.rank.ifBlank { "Officer" }} • ${officer.badgeNumber.ifBlank { "N/A" }}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (officer.phoneNumber.isNotBlank()) {
                            IconButton(
                                onClick = { onCall(officer.phoneNumber) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(DutyActiveGreen.copy(alpha = 0.14f), CircleShape)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Call Officer", tint = PoliceThemeColors.greenText, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (!showPastMode) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    officer.hideDutyStatus -> Color(0xFF64748B)
                                    officer.isOnDuty -> DutyActiveGreen
                                    else -> Color(0xFF475569)
                                }
                            ) {
                                Text(
                                    text = when {
                                        officer.hideDutyStatus -> "සඟවා ඇත"
                                        officer.isOnDuty -> "ON DUTY"
                                        else -> "COMPLETED"
                                    },
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    if (dayRecords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        dayRecords.take(3).forEach { rec ->
                            val lbl = when (rec.type) {
                                "ON_DUTY" -> "පැමිණීම"
                                "EXTRA_DUTY" -> "විශේෂ/අමතර"
                                else -> "බැහැරවීම"
                            }
                            Text(
                                text = "• ${DateTimeUtils.formatTime(rec.timestamp)} ($lbl): ${rec.dutyNature.ifBlank { "-" }}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingRequestCard(
    officer: AppUser,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DutyOffOrange.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(DutyOffOrange.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = PoliceThemeColors.orangeText,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = officer.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${officer.rank.ifBlank { "Officer" }} • ${officer.badgeNumber.ifBlank { "No badge" }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = officer.email,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (officer.phoneNumber.isNotBlank()) {
                        Text(
                            text = "දුරකථන: ${officer.phoneNumber}",
                            fontSize = 11.sp,
                            color = PoliceThemeColors.greenText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (officer.address.isNotBlank()) {
                        Text(
                            text = "ලිපිනය: ${officer.address}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (officer.role == "ADMIN_BRANCH") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BadgeGold.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "🏛️ පාලන අංශය (Admin)",
                                color = PoliceThemeColors.goldText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DutyOffOrange.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "අනුමැතිය අපේක්ෂිත",
                            color = PoliceThemeColors.orangeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ප්‍රතික්ෂේප කරන්න", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DutyActiveGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("අනුමත කරන්න (Approve)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
