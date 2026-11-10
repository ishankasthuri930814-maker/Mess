package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppUser
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.util.DateTimeUtils
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OICDashboardScreen(
    viewModel: PoliceViewModel
) {
    val context = LocalContext.current
    val appUser by viewModel.appUser.collectAsStateWithLifecycle()
    val subordinates by viewModel.subordinates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var isDetailDialogOpen by remember { mutableStateOf(false) }

    val pendingList = remember(subordinates) { subordinates.filter { it.status == "PENDING" } }
    val approvedList = remember(subordinates) { subordinates.filter { it.status == "APPROVED" } }
    val onDutyList = remember(subordinates) { subordinates.filter { it.status == "APPROVED" && it.isOnDuty } }

    var lastPendingCount by remember { mutableIntStateOf(pendingList.size) }

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
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(
                            imageVector = Icons.Default.Person,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Prominent OIC Unique Code Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PoliceNavyDark),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ඔබගේ ප්‍රධාන OIC කේතය (Station Code)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BadgeGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        Row {
                            // Copy button
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

                            Spacer(modifier = Modifier.width(6.dp))

                            // Share button
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
                }
            }

            // Quick Counter Metrics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "මුළු නිලධාරීන්",
                    value = approvedList.size.toString(),
                    color = PoliceNavyPrimary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "දැනට රාජකාරියේ",
                    value = onDutyList.size.toString(),
                    color = DutyActiveGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "අනුමැති ඉල්ලීම්",
                    value = pendingList.size.toString(),
                    color = if (pendingList.isNotEmpty()) DutyOffOrange else Color.Gray,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Navigation
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PoliceNavyPrimary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("නිලධාරීන් (${approvedList.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
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
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("සිතියම", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Approved Subordinates List
                    if (approvedList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "තවමත් සම්බන්ධ වූ නිලධාරීන් නොමැත.\nඔබගේ OIC කේතය ($oicCode) නිලධාරීන්ට ලබා දෙන්න.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(approvedList) { officer ->
                                SubordinateOfficerCard(
                                    officer = officer,
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
                    // Pending Requests List
                    if (pendingList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "අනුමැතිය සඳහා නව ඉල්ලීම් නොමැත.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(pendingList) { officer ->
                                PendingRequestCard(
                                    officer = officer,
                                    onApprove = { viewModel.approveOfficer(officer.uid) },
                                    onReject = { viewModel.rejectOfficer(officer.uid) }
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // Full Live GPS Map for OIC
                    Box(modifier = Modifier.fillMaxSize()) {
                        val cameraPositionState = rememberCameraPositionState {
                            // Default to center of Sri Lanka or first on-duty officer
                            val firstLoc = onDutyList.firstOrNull()?.lastLocation
                            val lat = (firstLoc?.get("lat") as? Double) ?: 7.8731
                            val lng = (firstLoc?.get("lng") as? Double) ?: 80.7718
                            position = CameraPosition.fromLatLngZoom(LatLng(lat, lng), if (firstLoc != null) 12f else 7.5f)
                        }

                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState
                        ) {
                            subordinates.forEach { officer ->
                                officer.lastLocation?.let { loc ->
                                    val lat = loc["lat"] as? Double ?: 0.0
                                    val lng = loc["lng"] as? Double ?: 0.0
                                    if (lat != 0.0 || lng != 0.0) {
                                        Marker(
                                            state = MarkerState(position = LatLng(lat, lng)),
                                            title = "${officer.displayName} (${if (officer.isOnDuty) "ON DUTY" else "OFF DUTY"})",
                                            snippet = "Badge: ${officer.badgeNumber.ifBlank { "N/A" }}"
                                        )
                                    }
                                }
                            }
                        }

                        // Map Overlay info pill
                        Surface(
                            modifier = Modifier
                                .padding(12.dp)
                                .align(Alignment.TopCenter),
                            shape = RoundedCornerShape(20.dp),
                            color = PoliceNavyDark.copy(alpha = 0.9f),
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                text = "රාජකාරියේ නියුතු නිලධාරීන්: ${onDutyList.size} ක් සිතියමේ දැක්වේ",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (isDetailDialogOpen) {
        OfficerDetailDialog(
            viewModel = viewModel,
            onDismiss = { isDetailDialogOpen = false }
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
    onClick: () -> Unit
) {
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
                        if (officer.isOnDuty) DutyActiveGreen.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (officer.isOnDuty) DutyActiveGreen else Color.Gray,
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
                    text = "${officer.rank.ifBlank { "Officer" }} • ${officer.badgeNumber.ifBlank { "No badge" }}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                officer.lastLocation?.let { loc ->
                    val lat = loc["lat"] as? Double ?: 0.0
                    val lng = loc["lng"] as? Double ?: 0.0
                    val ts = loc["timestamp"] as? Long ?: 0L
                    if (lat != 0.0) {
                        Text(
                            text = "GPS: $lat, $lng (${DateTimeUtils.formatTime(ts)})",
                            fontSize = 10.sp,
                            color = PoliceNavyPrimary
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (officer.isOnDuty) DutyActiveGreen else Color.Gray.copy(alpha = 0.8f)
            ) {
                Text(
                    text = if (officer.isOnDuty) "ON DUTY" else "OFF DUTY",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
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
                        tint = DutyOffOrange,
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
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DutyOffOrange.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "අනුමැතිය අපේක්ෂිත",
                        color = DutyOffOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
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
                    colors = ButtonDefaults.buttonColors(containerColor = DutyActiveGreen),
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
