package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppUser
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun OfficerMapDialog(
    viewModel: PoliceViewModel,
    onDismiss: () -> Unit
) {
    val subordinates by viewModel.subordinates.collectAsStateWithLifecycle()
    val onDutyOfficers = subordinates.filter { it.isOnDuty }

    // Focus on first on-duty officer or Sri Lanka center
    val firstLoc = onDutyOfficers.firstOrNull()?.lastLocation ?: subordinates.firstOrNull { it.lastLocation != null }?.lastLocation
    val initialLat = (firstLoc?.get("lat") as? Double) ?: 7.8731
    val initialLng = (firstLoc?.get("lng") as? Double) ?: 80.7718

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(initialLat, initialLng), if (firstLoc != null) 12f else 7.5f)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PoliceNavyDark)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = BadgeGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "නිලධාරීන්ගේ සජීවී සිතියම (Live Map)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                // Subtitle / Officer count pill
                Surface(
                    color = PoliceNavyPrimary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "සම්බන්ධිත නිලධාරීන්: ${subordinates.size} | රාජකාරියේ නියුතු: ${onDutyOfficers.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = PoliceNavyPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Google Map
                Box(modifier = Modifier.weight(1f)) {
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
                                        snippet = "තනතුර: ${officer.rank.ifBlank { "Officer" }} | Badge: ${officer.badgeNumber.ifBlank { "N/A" }}"
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Close button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = PoliceNavyPrimary)
                    ) {
                        Text("වසන්න (Close)")
                    }
                }
            }
        }
    }
}
