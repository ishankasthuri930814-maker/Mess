package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.BadgeGoldBright
import com.example.ui.theme.PoliceNavyDark

@Composable
fun OfficerMapDialog(
    viewModel: PoliceViewModel,
    onOpenEmergencySender: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val appUser by viewModel.appUser.collectAsStateWithLifecycle()
    val subordinates by viewModel.subordinates.collectAsStateWithLifecycle()
    val teamTeammates by viewModel.activeTeamTeammateUsers.collectAsStateWithLifecycle()
    val activeTeamSession by viewModel.activeTeamDutySession.collectAsStateWithLifecycle()
    val stationLocationHistory by viewModel.stationLocationHistory.collectAsStateWithLifecycle()
    val myLocationHistory by viewModel.myLocationHistory.collectAsStateWithLifecycle()
    val emergencyAlerts by viewModel.nearbyEmergencyAlerts.collectAsStateWithLifecycle()
    val myLocation by viewModel.myCurrentLocation.collectAsStateWithLifecycle()
    val myLocationAccuracy by viewModel.myLocationAccuracyMeters.collectAsStateWithLifecycle()
    val activeRouteState by viewModel.activeRouteState.collectAsStateWithLifecycle()
    val selectedMapDate by viewModel.selectedMapDate.collectAsStateWithLifecycle()
    val isMultiDayShiftMode by viewModel.mapMultiDayShiftMode.collectAsStateWithLifecycle()

    val isOic = appUser?.role == "OIC"
    val isInActiveTeam = !isOic && activeTeamSession?.status == "ACTIVE" && teamTeammates.isNotEmpty()
    val officersForMap = if (isOic) {
        subordinates.filter { !it.hiddenOnMap && !it.hiddenFromStationLists }
    } else if (isInActiveTeam) {
        (listOfNotNull(appUser) + teamTeammates).distinctBy { it.uid }
    } else {
        listOfNotNull(appUser)
    }
    val historyForMap = if (isOic) stationLocationHistory else myLocationHistory

    LaunchedEffect(Unit) {
        viewModel.refreshCurrentLocation(showFeedback = false)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(2f)
                        .background(PoliceNavyDark)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = BadgeGoldBright,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isOic) "සජීවී පොලිස් GPS සිතියම" else "සජීවී GPS හා හදිසි මාර්ග සිතියම",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = when {
                                    isOic -> "සම්බන්ධිත: ${officersForMap.size} • රාජකාරියේ: ${officersForMap.count { it.isOnDuty }} • හදිසි SOS: ${emergencyAlerts.size}"
                                    isInActiveTeam -> "කණ්ඩායම් සාමාජිකයින්: ${officersForMap.size} • ආසන්න හදිසි SOS: ${emergencyAlerts.size}"
                                    else -> "ඔබගේ GPS • ආසන්න හදිසි SOS: ${emergencyAlerts.size}"
                                },
                                color = BadgeGoldBright,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (onOpenEmergencySender != null) {
                        IconButton(onClick = onOpenEmergencySender) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Emergency SOS",
                                tint = Color(0xFFFCA5A5)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    PoliceLiveMapView(
                        officers = officersForMap,
                        locationHistory = historyForMap,
                        emergencyAlerts = emergencyAlerts,
                        myLocation = myLocation,
                        myLocationAccuracyMeters = myLocationAccuracy,
                        activeRouteState = activeRouteState,
                        selectedDateKey = selectedMapDate,
                        isOicView = isOic,
                        isMultiDayShiftMode = isMultiDayShiftMode,
                        onToggleMultiDayShiftMode = { viewModel.setMapMultiDayShiftMode(it) },
                        onSelectDate = { viewModel.selectMapDate(it) },
                        onNavigateToTarget = { target ->
                            viewModel.navigateToLocationOnMap(target, openDialog = false)
                        },
                        onClearRoute = { viewModel.clearMapRoute() },
                        onResolveEmergency = { viewModel.resolveEmergencyAlert(it) },
                        onRefreshGps = { viewModel.refreshCurrentLocation(showFeedback = true) },
                        onOpenEmergencySender = onOpenEmergencySender
                    )
                }
            }
        }
    }
}
