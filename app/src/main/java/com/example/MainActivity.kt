package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import com.example.ui.ExportReportDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.DutyRecord
import com.example.data.MessRecord
import com.example.data.PoliceRepository
import com.example.ui.DashboardScreen
import com.example.ui.DirectoryScreen
import com.example.ui.DutyEntryDialog
import com.example.ui.DutyScreen
import com.example.ui.MealEntryDialog
import com.example.ui.MessScreen
import com.example.ui.AuthPortal
import com.example.ui.AuthPortalScreen
import com.example.ui.OICLoginScreen
import com.example.ui.OfficerLoginScreen
import com.example.ui.OICRegistrationScreen
import com.example.ui.OfficerRegistrationScreen
import com.example.ui.OICDashboardScreen
import com.example.ui.PendingApprovalScreen
import com.example.ui.PhotoViewerDialog
import com.example.ui.PoliceViewModel
import com.example.ui.PoliceViewModelFactory
import com.example.ui.ProfileScreen
import com.example.ui.RegistrationScreen
import com.example.ui.RunningChartScreen
import com.example.ui.TeamDutyScreen
import androidx.compose.material.icons.filled.Close
import androidx.credentials.CredentialManager
import com.example.ui.EmergencySosBanner
import com.example.ui.IncomingEmergencyPopupDialog
import com.example.ui.MapNavigationTarget
import com.example.ui.NotificationBellButton
import com.example.ui.NotificationsInboxDialog
import com.example.ui.OfficerMapDialog
import com.example.ui.SendEmergencySosDialog
import com.example.ui.attemptAutoSignIn
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceThemeColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import com.example.location.BackgroundLocationService
import com.example.ui.AdminBranchLoginScreen
import com.example.ui.AdminBranchRegistrationScreen
import com.example.ui.AdminBranchDashboardScreen
import com.example.ui.ChatTopBarButton
import com.example.ui.LatestInAppNoticeBanner
import com.example.ui.StationChatDialog
import com.example.data.DutyAssignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton

enum class Screen(val title: String, val icon: ImageVector, val label: String) {
    DASHBOARD("මුල්", Icons.Default.Dashboard, "Home"),
    DUTY("රාජකාරි", Icons.AutoMirrored.Filled.MenuBook, "Duty"),
    TEAM_DUTY("කණ්ඩායම්", Icons.Default.QrCode2, "Team"),
    RUNNING_CHART("ගමන්", Icons.Default.DirectionsCar, "Running Chart"),
    DIRECTORY("නාමාවලිය", Icons.Default.Phone, "Directory"),
    MESS("මෙස්", Icons.Default.Restaurant, "Mess"),
    PROFILE("නිලධාරියා", Icons.Default.Badge, "Profile")
}

data class ActivePhotoView(
    val path: String,
    val title: String,
    val compKb: Int,
    val origKb: Int
)

class MainActivity : ComponentActivity() {

    private val viewModel: PoliceViewModel by viewModels {
        val database = AppDatabase.getInstance(applicationContext)
        val firestoreId = getString(R.string.firestore_database_id)
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(firestoreId)
        val repository = PoliceRepository(database.policeDao(), firestore)
        PoliceViewModelFactory(repository, application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleEmergencyIntent(intent)
        setContent {
            val isDarkModeOverride by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val systemInDark = isSystemInDarkTheme()
            val isDarkTheme = isDarkModeOverride ?: systemInDark

            MyApplicationTheme(darkTheme = isDarkTheme) {
                MainPoliceApp(viewModel = viewModel, isDarkTheme = isDarkTheme)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEmergencyIntent(intent)
    }

    private fun handleEmergencyIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.hasExtra("open_sos_lat") && intent.hasExtra("open_sos_lng")) {
            val lat = intent.getDoubleExtra("open_sos_lat", 0.0)
            val lng = intent.getDoubleExtra("open_sos_lng", 0.0)
            val alertId = intent.getStringExtra("open_sos_alert_id") ?: ""
            val title = intent.getStringExtra("open_sos_title") ?: "🚨 හදිසි SOS ස්ථානය"
            val msg = intent.getStringExtra("open_sos_msg") ?: ""
            val phone = intent.getStringExtra("open_sos_phone") ?: ""
            if (lat != 0.0 || lng != 0.0) {
                viewModel.openEmergencyRouteFromNotification(
                    alertId = alertId,
                    lat = lat,
                    lng = lng,
                    title = title,
                    message = msg,
                    phone = phone
                )
            }
            intent.removeExtra("open_sos_lat")
            intent.removeExtra("open_sos_lng")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPoliceApp(viewModel: PoliceViewModel, isDarkTheme: Boolean) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val appUser by viewModel.appUser.collectAsStateWithLifecycle()
    val isProfileLoaded by viewModel.isProfileLoaded.collectAsStateWithLifecycle()
    val isRegistered by viewModel.isRegistered.collectAsStateWithLifecycle()
    val isOICLoggedIn by viewModel.isOICLoggedIn.collectAsStateWithLifecycle()
    val authPortal by viewModel.authPortal.collectAsStateWithLifecycle()

    // 1. Not signed in with Google -> Try silent auto sign-in first unless user logged out
    if (currentUser == null) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val credentialManager = remember(context) { CredentialManager.create(context) }
        var isCheckingAutoSignIn by remember { mutableStateOf(viewModel.shouldAttemptAutoSignIn()) }

        LaunchedEffect(currentUser) {
            if (viewModel.shouldAttemptAutoSignIn()) {
                attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    scope = scope,
                    onAuthSuccess = {
                        viewModel.markSignedIn()
                        isCheckingAutoSignIn = false
                    },
                    onUnauthenticated = {
                        isCheckingAutoSignIn = false
                    }
                )
            } else {
                isCheckingAutoSignIn = false
            }
        }

        if (isCheckingAutoSignIn) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            return
        }

        when (authPortal) {
            AuthPortal.CHOOSER -> {
                AuthPortalScreen(
                    isDarkTheme = isDarkTheme,
                    onSelectDarkMode = { viewModel.setDarkMode(it) },
                    onSelectOIC = { viewModel.setAuthPortal(AuthPortal.OIC) },
                    onSelectAdminBranch = { viewModel.setAuthPortal(AuthPortal.ADMIN_BRANCH) },
                    onSelectOfficer = { viewModel.setAuthPortal(AuthPortal.OFFICER) }
                )
            }
            AuthPortal.OIC -> {
                BackHandler { viewModel.setAuthPortal(AuthPortal.CHOOSER) }
                OICLoginScreen(
                    onBack = { viewModel.setAuthPortal(AuthPortal.CHOOSER) },
                    onLoginSuccess = { viewModel.markSignedIn() }
                )
            }
            AuthPortal.ADMIN_BRANCH -> {
                BackHandler { viewModel.setAuthPortal(AuthPortal.CHOOSER) }
                AdminBranchLoginScreen(
                    onBack = { viewModel.setAuthPortal(AuthPortal.CHOOSER) },
                    onLoginSuccess = { viewModel.markSignedIn() }
                )
            }
            AuthPortal.OFFICER -> {
                BackHandler { viewModel.setAuthPortal(AuthPortal.CHOOSER) }
                OfficerLoginScreen(
                    onBack = { viewModel.setAuthPortal(AuthPortal.CHOOSER) },
                    onLoginSuccess = { viewModel.markSignedIn() }
                )
            }
        }
        return
    }

    // 2. Signed in: wait for initial profile check, then show registration if needed
    if (appUser == null) {
        if (!isProfileLoaded) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (authPortal == AuthPortal.OIC) {
            BackHandler { viewModel.logout() }
            OICRegistrationScreen(viewModel = viewModel)
        } else if (authPortal == AuthPortal.ADMIN_BRANCH) {
            BackHandler { viewModel.logout() }
            AdminBranchRegistrationScreen(viewModel = viewModel)
        } else {
            BackHandler { viewModel.logout() }
            OfficerRegistrationScreen(viewModel = viewModel)
        }
        return
    }

    // 3. User is an OIC -> Dedicated OIC Dashboard Screen
    if (appUser?.role == "OIC") {
        OICDashboardScreen(viewModel = viewModel)
        return
    }

    // 3b. User is ADMIN_BRANCH -> Dedicated Admin Branch Portal
    if (appUser?.role == "ADMIN_BRANCH") {
        if (appUser?.status == "PENDING") {
            PendingApprovalScreen(viewModel = viewModel)
        } else if (appUser?.status == "REMOVED") {
            RemovedFromStationScreen(viewModel = viewModel)
        } else if (appUser?.status == "REJECTED") {
            Scaffold { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "පාලන අංශ ඉල්ලීම ප්‍රතික්ෂේප විය",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ඔබ ඇතුළත් කළ OIC කේතය හෝ විස්තර ප්‍රතික්ෂේප වී ඇත. කරුණාකර OIC ගෙන් විමසා නැවත උත්සාහ කරන්න.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.logout() }) {
                            Text("නැවත උත්සාහ කරන්න (Re-try / Sign Out)")
                        }
                    }
                }
            }
        } else {
            AdminBranchDashboardScreen(viewModel = viewModel)
        }
        return
    }

    // 4. User is an Officer awaiting OIC approval
    if (appUser?.role == "OFFICER" && appUser?.status == "PENDING") {
        PendingApprovalScreen(viewModel = viewModel)
        return
    }

    // 4b. User was removed from station by OIC
    if (appUser?.status == "REMOVED") {
        RemovedFromStationScreen(viewModel = viewModel)
        return
    }

    // 5. User is an Officer but rejected
    if (appUser?.role == "OFFICER" && appUser?.status == "REJECTED") {
        Scaffold { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "ලියාපදිංචිය ප්‍රතික්ෂේප විය",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ඔබ ඇතුළත් කළ OIC කේතය හෝ විස්තර ප්‍රතික්ෂේප වී ඇත. කරුණාකර නිවැරදි OIC කේතය සහිතව නැවත උත්සාහ කරන්න.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.logout() }) {
                        Text("නැවත උත්සාහ කරන්න (Re-try / Sign Out)")
                    }
                }
            }
        }
        return
    }

    // 5b. Approved Officer must complete mandatory full profile (name, badge, rank, phone, address)
    val userSnapshot = appUser
    val isOfficerProfileIncomplete = userSnapshot?.role == "OFFICER" &&
        userSnapshot.status == "APPROVED" &&
        (userSnapshot.displayName.isBlank() ||
            userSnapshot.badgeNumber.isBlank() ||
            userSnapshot.rank.isBlank() ||
            userSnapshot.phoneNumber.filter { it.isDigit() }.length < 9 ||
            userSnapshot.address.isBlank())
    if (isOfficerProfileIncomplete) {
        BackHandler { viewModel.logout() }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "නිලධාරී තොරතුරු සම්පූර්ණ කරන්න",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PoliceNavyPrimary
                    )
                )
            }
        ) { gatePadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(gatePadding)
            ) {
                ProfileScreen(
                    viewModel = viewModel,
                    isMandatoryCompletionMode = true
                )
            }
        }
        return
    }

    // 6. User is an Approved Police Officer: continues to regular officer interface below

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

    // Dialog states
    var isDutyDialogOpen by remember { mutableStateOf(false) }
    var dutyDialogInitialType by remember { mutableStateOf("ON_DUTY") }
    var editingDutyRecord by remember { mutableStateOf<DutyRecord?>(null) }

    var isMealDialogOpen by remember { mutableStateOf(false) }
    var mealDialogType by remember { mutableStateOf("LUNCH") }
    var mealDialogExisting by remember { mutableStateOf<MessRecord?>(null) }

    var isExportReportDialogOpen by remember { mutableStateOf(false) }
    var isNotificationsDialogOpen by remember { mutableStateOf(false) }
    var isChatDialogOpen by remember { mutableStateOf(false) }
    var isSendSosDialogOpen by remember { mutableStateOf(false) }
    var isMoreMenuExpanded by remember { mutableStateOf(false) }
    var showBgLocationDisclosureDialog by remember { mutableStateOf(false) }
    var hasPromptedBgDisclosure by remember { mutableStateOf(false) }

    var activePhotoView by remember { mutableStateOf<ActivePhotoView?>(null) }

    val officerProfile by viewModel.officerProfile.collectAsStateWithLifecycle()
    val latestDuty by viewModel.latestDutyRecord.collectAsStateWithLifecycle()
    val latestShift by viewModel.latestShiftRecord.collectAsStateWithLifecycle()
    val myTodayAssignedDuty by viewModel.myTodayAssignedDuty.collectAsStateWithLifecycle()
    val unreadNotifCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadChatCount by viewModel.unreadChatCount.collectAsStateWithLifecycle()
    val nearbyEmergencyAlerts by viewModel.nearbyEmergencyAlerts.collectAsStateWithLifecycle()
    val myCurrentLocation by viewModel.myCurrentLocation.collectAsStateWithLifecycle()
    val activeEmergencyPopup by viewModel.activeEmergencyPopup.collectAsStateWithLifecycle()
    val isRouteMapDialogOpen by viewModel.isRouteMapDialogOpen.collectAsStateWithLifecycle()
    val stationActiveSingleSlotHolders by viewModel.stationActiveSingleSlotHolders.collectAsStateWithLifecycle()
    val isOnDuty = appUser?.isOnDuty ?: (latestShift?.type == "ON_DUTY")

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied gracefully */ }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { bgGranted ->
        if (bgGranted && isOnDuty) {
            BackgroundLocationService.startIfAllowed(context)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.refreshCurrentLocation()
            if (isOnDuty &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !BackgroundLocationService.hasBackgroundLocationPermission(context) &&
                !hasPromptedBgDisclosure
            ) {
                hasPromptedBgDisclosure = true
                showBgLocationDisclosureDialog = true
            } else if (isOnDuty) {
                BackgroundLocationService.startIfAllowed(context)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotifPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotifPerm) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.refreshCurrentLocation()
        }
    }

    LaunchedEffect(isOnDuty) {
        if (isOnDuty && BackgroundLocationService.hasForegroundLocationPermission(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !BackgroundLocationService.hasBackgroundLocationPermission(context) &&
                !hasPromptedBgDisclosure
            ) {
                hasPromptedBgDisclosure = true
                showBgLocationDisclosureDialog = true
            } else {
                BackgroundLocationService.startIfAllowed(context)
            }
        }
    }

    // Back handler: return to Dashboard if on another screen
    BackHandler(enabled = currentScreen != Screen.DASHBOARD) {
        currentScreen = Screen.DASHBOARD
    }

    // Collect Toast/Snackbar feedback
    LaunchedEffect(Unit) {
        viewModel.userMessage.collectLatest { message ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(BadgeGold, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = PoliceNavyDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "All In One Police",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (officerProfile?.badgeNumber?.isNotBlank() == true) {
                                    "${officerProfile?.rank ?: "Officer"} • No: ${officerProfile?.badgeNumber}"
                                } else {
                                    "ශ්‍රී ලංකා පොලීසිය"
                                },
                                fontSize = 11.sp,
                                color = BadgeGold
                            )
                        }

                        // Duty status badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isOnDuty) DutyActiveGreen else DutyOffOrange,
                            modifier = Modifier.clickable {
                                currentScreen = Screen.DUTY
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isOnDuty) "ON DUTY" else "OFF DUTY",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Emergency SOS shortcut in top bar
                        IconButton(
                            onClick = { isSendSosDialogOpen = true },
                            modifier = Modifier.testTag("officer_sos_top_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Emergency SOS",
                                tint = Color(0xFFFCA5A5)
                            )
                        }

                        // Live Map shortcut in top bar
                        IconButton(
                            onClick = { viewModel.openRouteMapDialog() },
                            modifier = Modifier.testTag("officer_map_top_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Live Map",
                                tint = BadgeGold
                            )
                        }

                        // Chat Room shortcut in top bar
                        ChatTopBarButton(
                            unreadCount = unreadChatCount,
                            tint = BadgeGold,
                            onClick = { isChatDialogOpen = true }
                        )

                        // Notifications Bell in top bar
                        NotificationBellButton(
                            unreadCount = unreadNotifCount,
                            tint = BadgeGold,
                            onClick = { isNotificationsDialogOpen = true }
                        )

                        // Officer profile shortcut icon in top bar
                        IconButton(
                            onClick = { currentScreen = Screen.PROFILE }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Officer Profile",
                                tint = if (currentScreen == Screen.PROFILE) BadgeGold else Color.White
                            )
                        }

                        // Overflow Menu (PDF Export & Dark Mode) so TopBar stays clean
                        Box {
                            IconButton(
                                onClick = { isMoreMenuExpanded = true },
                                modifier = Modifier.testTag("officer_more_menu_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = isMoreMenuExpanded,
                                onDismissRequest = { isMoreMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("වාර්තා PDF (Export)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = PoliceThemeColors.navyText
                                        )
                                    },
                                    onClick = {
                                        isMoreMenuExpanded = false
                                        isExportReportDialogOpen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(if (isDarkTheme) "දිවා තේමාව (Light Mode)" else "රාත්‍රී තේමාව (Dark Mode)")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                            contentDescription = null,
                                            tint = PoliceThemeColors.navyText
                                        )
                                    },
                                    onClick = {
                                        isMoreMenuExpanded = false
                                        viewModel.toggleDarkMode()
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PoliceNavyPrimary,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                val navItems = listOf(
                    Screen.DASHBOARD,
                    Screen.DUTY,
                    Screen.TEAM_DUTY,
                    Screen.RUNNING_CHART,
                    Screen.DIRECTORY,
                    Screen.MESS
                )
                navItems.forEach { screen ->
                    val selected = currentScreen == screen
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(imageVector = screen.icon, contentDescription = screen.title)
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 9.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PoliceThemeColors.navyText,
                            selectedTextColor = PoliceThemeColors.navyText,
                            indicatorColor = BadgeGold.copy(alpha = 0.25f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_item_${screen.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (nearbyEmergencyAlerts.isNotEmpty()) {
                EmergencySosBanner(
                    alerts = nearbyEmergencyAlerts,
                    myLocation = myCurrentLocation,
                    myUid = appUser?.uid ?: "",
                    isOic = false,
                    onClickAlertLocation = { alert ->
                        viewModel.navigateToLocationOnMap(
                            target = MapNavigationTarget(
                                alertId = alert.id,
                                title = "🚨 SOS: ${alert.senderName.ifBlank { alert.senderRole }}",
                                subtitle = alert.message,
                                lat = alert.lat,
                                lng = alert.lng,
                                phone = alert.senderPhone
                            ),
                            openDialog = true
                        )
                    },
                    onResolveAlert = { viewModel.resolveEmergencyAlert(it) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            LatestInAppNoticeBanner(
                notifications = notifications,
                unreadCount = unreadNotifCount,
                onOpenInbox = { isNotificationsDialogOpen = true },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentScreen) {
                    Screen.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToDuty = { currentScreen = Screen.DUTY },
                        onNavigateToRunningChart = { currentScreen = Screen.RUNNING_CHART },
                        onNavigateToDirectory = { currentScreen = Screen.DIRECTORY },
                        onNavigateToMess = { currentScreen = Screen.MESS },
                        onNavigateToProfile = { currentScreen = Screen.PROFILE },
                        onOpenDutyDialog = { type ->
                            dutyDialogInitialType = type
                            isDutyDialogOpen = true
                        },
                        onOpenMealDialog = { type ->
                            mealDialogType = type
                            mealDialogExisting = null
                            isMealDialogOpen = true
                        },
                        onViewPhoto = { path, title, compKb, origKb ->
                            activePhotoView = ActivePhotoView(path, title, compKb, origKb)
                        },
                        onOpenMap = { viewModel.openRouteMapDialog() },
                        onOpenSos = { isSendSosDialogOpen = true }
                    )

                Screen.DUTY -> DutyScreen(
                    viewModel = viewModel,
                    onOpenDutyDialog = { type ->
                        editingDutyRecord = null
                        dutyDialogInitialType = type
                        isDutyDialogOpen = true
                    },
                    onEditDutyRecord = { record ->
                        editingDutyRecord = record
                        dutyDialogInitialType = record.type
                        isDutyDialogOpen = true
                    },
                    onViewPhoto = { path, title, compKb, origKb ->
                        activePhotoView = ActivePhotoView(path, title, compKb, origKb)
                    }
                )

                Screen.TEAM_DUTY -> TeamDutyScreen(
                    viewModel = viewModel,
                    onViewPhoto = { path, title, compKb, origKb ->
                        activePhotoView = ActivePhotoView(path, title, compKb, origKb)
                    }
                )

                Screen.RUNNING_CHART -> RunningChartScreen(
                    viewModel = viewModel
                )

                Screen.DIRECTORY -> DirectoryScreen(
                    viewModel = viewModel
                )

                Screen.MESS -> MessScreen(
                    viewModel = viewModel,
                    onOpenMealDialog = { type, existing ->
                        mealDialogType = type
                        mealDialogExisting = existing
                        isMealDialogOpen = true
                    },
                    onViewPhoto = { path, title, compKb, origKb ->
                        activePhotoView = ActivePhotoView(path, title, compKb, origKb)
                    }
                )

                    Screen.PROFILE -> ProfileScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // --- Dialogs ---

    if (isDutyDialogOpen) {
        DutyEntryDialog(
            initialType = dutyDialogInitialType,
            editingRecord = editingDutyRecord,
            assignedDuty = myTodayAssignedDuty,
            currentUserUid = appUser?.uid ?: "",
            activeSingleSlotHolders = stationActiveSingleSlotHolders,
            onVerifyAdminPasscode = { enteredPin -> viewModel.verifyAdminDailyPasscode(enteredPin) },
            onDismiss = {
                isDutyDialogOpen = false
                editingDutyRecord = null
            },
            onSubmit = { type, timestamp, page, paragraph, nature, remarks, photoUri, scenePhotoUri, durationMinutes, isHandoverConfirmed, onError ->
                val currentEdit = editingDutyRecord
                if (currentEdit != null) {
                    viewModel.updateDutyRecord(
                        context = context,
                        recordId = currentEdit.id,
                        type = type,
                        timestamp = timestamp,
                        ibPage = page,
                        ibParagraph = paragraph,
                        dutyNature = nature,
                        remarks = remarks,
                        photoUri = photoUri,
                        scenePhotoUri = scenePhotoUri,
                        dutyDurationMinutes = durationMinutes,
                        onSuccess = {
                            isDutyDialogOpen = false
                            editingDutyRecord = null
                        },
                        onError = onError
                    )
                } else {
                    viewModel.submitDutyReport(
                        context = context,
                        type = type,
                        timestamp = timestamp,
                        ibPage = page,
                        ibParagraph = paragraph,
                        dutyNature = nature,
                        remarks = remarks,
                        photoUri = photoUri,
                        scenePhotoUri = scenePhotoUri,
                        dutyDurationMinutes = durationMinutes,
                        isHandoverConfirmed = isHandoverConfirmed,
                        onSuccess = {
                            isDutyDialogOpen = false
                            editingDutyRecord = null
                        },
                        onError = onError
                    )
                }
            }
        )
    }

    if (isMealDialogOpen) {
        MealEntryDialog(
            initialMealType = mealDialogType,
            existingRecord = mealDialogExisting,
            onDismiss = { isMealDialogOpen = false },
            onSave = { type, isTaken, notes, price, photoUri, onError ->
                viewModel.saveMealDetails(
                    context = context,
                    mealType = type,
                    isTaken = isTaken,
                    notes = notes,
                    price = price,
                    photoUri = photoUri,
                    onSuccess = {
                        isMealDialogOpen = false
                    },
                    onError = onError
                )
            }
        )
    }

    if (isExportReportDialogOpen) {
        ExportReportDialog(
            viewModel = viewModel,
            onDismiss = { isExportReportDialogOpen = false }
        )
    }

    if (isNotificationsDialogOpen) {
        NotificationsInboxDialog(
            viewModel = viewModel,
            isOic = false,
            onDismiss = { isNotificationsDialogOpen = false }
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
            myLocation = myCurrentLocation,
            onShowRouteOnMap = {
                viewModel.dismissEmergencyPopup()
                viewModel.navigateToLocationOnMap(
                    target = MapNavigationTarget(
                        alertId = alert.id,
                        title = "🚨 SOS: ${alert.senderName.ifBlank { alert.senderRole }}",
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

    activePhotoView?.let { photo ->
        PhotoViewerDialog(
            photoPath = photo.path,
            title = photo.title,
            compressedSizeKb = photo.compKb,
            originalSizeKb = photo.origKb,
            onDismiss = { activePhotoView = null }
        )
    }

    if (isChatDialogOpen) {
        StationChatDialog(
            viewModel = viewModel,
            onDismiss = { isChatDialogOpen = false }
        )
    }

    if (showBgLocationDisclosureDialog && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        AlertDialog(
            onDismissRequest = { showBgLocationDisclosureDialog = false },
            title = {
                Text(
                    text = "පසුබිම් GPS සිතියම් පටිගත කිරීම",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "ඔබ ON DUTY වී සිටියදී යෙදුම වසා දැමුවද හෝ තිරය නිවා දැමුවද ඔබගේ මුරසංචාර ගමන් මග සහ රැඳී සිටින ස්ථාන සිතියමේ අඛණ්ඩව සටහන් කිරීමට 'Allow all the time (සෑම විටම අවසර දෙන්න)' තෝරන්න.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBgLocationDisclosureDialog = false
                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                ) {
                    Text("අවසර දෙන්න (Continue)")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBgLocationDisclosureDialog = false
                        if (isOnDuty) BackgroundLocationService.startIfAllowed(context)
                    }
                ) {
                    Text("පසුව")
                }
            }
        )
    }
}

@Composable
private fun RemovedFromStationScreen(viewModel: PoliceViewModel) {
    var newStationCode by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "ස්ථාන ලැයිස්තුවෙන් ඉවත් කර ඇත",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ස්ථානාධිපති (OIC) විසින් ඔබව එම පොලිස් ස්ථාන ලැයිස්තුවෙන් ඉවත් කර ඇත. වෙනත් ස්ථානයකට එක්වීමට නව OIC Station Code අංකය ඇතුළත් කරන්න.",
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                OutlinedTextField(
                    value = newStationCode,
                    onValueChange = {
                        newStationCode = it.uppercase()
                        errorMsg = null
                    },
                    label = { Text("නව OIC කේතය (New Station Code)") },
                    placeholder = { Text("e.g. OIC-CMB-1234") },
                    singleLine = true,
                    isError = errorMsg != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("removed_rejoin_code_input")
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        isSubmitting = true
                        viewModel.rejoinStationAfterRemoval(
                            newStationCode = newStationCode,
                            onSuccess = { isSubmitting = false },
                            onError = { err ->
                                isSubmitting = false
                                errorMsg = err
                            }
                        )
                    },
                    enabled = newStationCode.isNotBlank() && !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("removed_rejoin_submit_btn")
                ) {
                    Text("නව ස්ථානයට ඉල්ලීම යවන්න")
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("ඉවත් වන්න (Sign Out)")
                }
            }
        }
    }
}
