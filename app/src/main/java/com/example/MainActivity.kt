package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import com.example.ui.ExportReportDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.PhotoViewerDialog
import com.example.ui.PoliceViewModel
import com.example.ui.PoliceViewModelFactory
import com.example.ui.ProfileScreen
import com.example.ui.RunningChartScreen
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class Screen(val title: String, val icon: ImageVector, val label: String) {
    DASHBOARD("මුල් පිටුව", Icons.Default.Dashboard, "Home"),
    DUTY("රාජකාරි", Icons.Default.MenuBook, "Duty"),
    RUNNING_CHART("ගමන් සටහන්", Icons.Default.DirectionsCar, "Running Chart"),
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
        val repository = PoliceRepository(database.policeDao())
        PoliceViewModelFactory(repository, application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkModeOverride by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val systemInDark = isSystemInDarkTheme()
            val isDarkTheme = isDarkModeOverride ?: systemInDark

            MyApplicationTheme(darkTheme = isDarkTheme) {
                MainPoliceApp(viewModel = viewModel, isDarkTheme = isDarkTheme)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPoliceApp(viewModel: PoliceViewModel, isDarkTheme: Boolean) {
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

    var activePhotoView by remember { mutableStateOf<ActivePhotoView?>(null) }

    val officerProfile by viewModel.officerProfile.collectAsStateWithLifecycle()
    val latestDuty by viewModel.latestDutyRecord.collectAsStateWithLifecycle()
    val latestShift by viewModel.latestShiftRecord.collectAsStateWithLifecycle()
    val isOnDuty = latestShift?.type == "ON_DUTY"

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

                        // PDF / Report Export Button in top bar
                        IconButton(
                            onClick = { isExportReportDialogOpen = true },
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Export Report PDF",
                                tint = BadgeGold
                            )
                        }

                        // Dark Mode Toggle Icon
                        IconButton(
                            onClick = { viewModel.toggleDarkMode() },
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Dark Mode",
                                tint = BadgeGold
                            )
                        }

                        // Officer profile shortcut icon in top bar
                        IconButton(
                            onClick = { currentScreen = Screen.PROFILE },
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Officer Profile",
                                tint = if (currentScreen == Screen.PROFILE) BadgeGold else Color.White
                            )
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
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PoliceNavyPrimary,
                            selectedTextColor = PoliceNavyPrimary,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
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
                    }
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

    // --- Dialogs ---

    if (isDutyDialogOpen) {
        DutyEntryDialog(
            initialType = dutyDialogInitialType,
            editingRecord = editingDutyRecord,
            onDismiss = {
                isDutyDialogOpen = false
                editingDutyRecord = null
            },
            onSubmit = { type, timestamp, page, paragraph, nature, remarks, photoUri, scenePhotoUri, durationMinutes ->
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
                        }
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
                        onSuccess = {
                            isDutyDialogOpen = false
                            editingDutyRecord = null
                        }
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
            onSave = { type, isTaken, notes, price, photoUri ->
                viewModel.saveMealDetails(
                    context = context,
                    mealType = type,
                    isTaken = isTaken,
                    notes = notes,
                    price = price,
                    photoUri = photoUri,
                    onSuccess = {
                        isMealDialogOpen = false
                    }
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

    activePhotoView?.let { photo ->
        PhotoViewerDialog(
            photoPath = photo.path,
            title = photo.title,
            compressedSizeKb = photo.compKb,
            originalSizeKb = photo.origKb,
            onDismiss = { activePhotoView = null }
        )
    }
}
