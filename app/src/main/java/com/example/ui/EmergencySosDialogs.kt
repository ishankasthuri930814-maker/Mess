package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.EmergencyAlert
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.PoliceThemeColors
import com.example.util.DateTimeUtils
import com.example.util.GeoRouteUtils

@Composable
fun EmergencySosBanner(
    alerts: List<EmergencyAlert>,
    myLocation: Pair<Double, Double>?,
    myUid: String,
    isOic: Boolean,
    onClickAlertLocation: (EmergencyAlert) -> Unit,
    onResolveAlert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (alerts.isEmpty()) return
    val context = LocalContext.current
    val topAlert = alerts.first()
    val distanceText = remember(topAlert, myLocation) {
        if (myLocation != null && (topAlert.lat != 0.0 || topAlert.lng != 0.0)) {
            GeoRouteUtils.formatDistance(
                GeoRouteUtils.distanceMeters(myLocation.first, myLocation.second, topAlert.lat, topAlert.lng)
            ) + " දුරින්"
        } else {
            "GPS: ${String.format("%.4f", topAlert.lat)}, ${String.format("%.4f", topAlert.lng)}"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClickAlertLocation(topAlert) }
            .testTag("emergency_sos_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF991B1B)),
        border = BorderStroke(1.5.dp, Color(0xFFFCA5A5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFEF08A),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🚨 හදිසි GPS ඇමතුමක් (${alerts.size}) • $distanceText",
                        color = Color(0xFFFEF08A),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${topAlert.senderName.ifBlank { topAlert.senderRole }} (${topAlert.senderRank.ifBlank { topAlert.senderRole }})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = DateTimeUtils.formatTime(topAlert.createdAt?.toDate()?.time ?: topAlert.createdAtMs),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = topAlert.message,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onClickAlertLocation(topAlert) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF991B1B)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ස්ථානයට යන පාර බලන්න", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (topAlert.senderPhone.isNotBlank()) {
                    Button(
                        onClick = {
                            val clean = topAlert.senderPhone.replace(" ", "").replace("-", "")
                            try {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
                            } catch (_: Exception) {
                                Toast.makeText(context, "ඇමතුම් ලබාගත නොහැක", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DutyActiveGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(16.dp))
                    }
                }

                if (topAlert.senderUid == myUid || isOic) {
                    OutlinedButton(
                        onClick = { onResolveAlert(topAlert.id) },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("අවසන්", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun IncomingEmergencyPopupDialog(
    alert: EmergencyAlert,
    myLocation: Pair<Double, Double>?,
    onShowRouteOnMap: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val distStr = if (myLocation != null && (alert.lat != 0.0 || alert.lng != 0.0)) {
        GeoRouteUtils.formatDistance(
            GeoRouteUtils.distanceMeters(myLocation.first, myLocation.second, alert.lat, alert.lng)
        ) + " දුරින්"
    } else {
        "${String.format("%.5f", alert.lat)}, ${String.format("%.5f", alert.lng)}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF7F1D1D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFEF08A),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🚨 හදිසි ආධාර ඇමතුමක්!",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = Color(0xFFFEF08A)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "යැවූ නිලධාරියා: ${alert.senderName} (${alert.senderRank.ifBlank { alert.senderRole }})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "දුර: $distStr",
                    fontSize = 12.sp,
                    color = Color(0xFFFCA5A5),
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.28f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = alert.message,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                if (alert.senderPhone.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            val clean = alert.senderPhone.replace(" ", "").replace("-", "")
                            try {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
                            } catch (_: Exception) {
                                Toast.makeText(context, "ඇමතුම් ලබාගත නොහැක", Toast.LENGTH_SHORT).show()
                            }
                        },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("දුරකථන ඇමතුමක් ගන්න (${alert.senderPhone})", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onShowRouteOnMap,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF991B1B)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("මාර්ගය සිතියමේ පෙන්වන්න", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("වසන්න", color = Color.White.copy(alpha = 0.85f))
            }
        }
    )
}

@Composable
fun SendEmergencySosDialog(
    viewModel: PoliceViewModel,
    onDismiss: () -> Unit
) {
    val myLocation by viewModel.myCurrentLocation.collectAsStateWithLifecycle()
    val appUser by viewModel.appUser.collectAsStateWithLifecycle()
    val savedFallback = GeoRouteUtils.extractLatLng(appUser?.lastLocation)
    val displayLat = myLocation?.first ?: savedFallback?.first
    val displayLng = myLocation?.second ?: savedFallback?.second

    val presets = remember {
        listOf(
            "හදිසි ආධාර අවශ්‍යයි - වහාම මෙම ස්ථානයට පැමිණෙන්න!",
            "රිය අනතුරක් / මාර්ග අවහිරතාවයක් - සහය අවශ්‍යයි",
            "සැකකටයුතු පුද්ගලයෙක් / සිදුවීමක් - අවට නිලධාරීන් එන්න",
            "හදිසි වෛද්‍ය ආධාර අවශ්‍යයි!"
        )
    }
    var message by remember { mutableStateOf(presets.first()) }
    var radiusKm by remember { mutableDoubleStateOf(10.0) }
    var isSending by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshCurrentLocation(showFeedback = false)
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = PoliceThemeColors.errorText,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "හදිසි GPS පණිවිඩය (SOS)",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PoliceThemeColors.errorText
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PoliceThemeColors.navyText.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = PoliceThemeColors.navyText, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ඔබගේ වත්මන් GPS ස්ථානය:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (displayLat != null && displayLng != null) {
                                    "${String.format("%.5f", displayLat)}, ${String.format("%.5f", displayLng)}"
                                } else {
                                    "GPS ලබාගනිමින්..."
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PoliceThemeColors.navyText
                            )
                        }
                        IconButton(onClick = { viewModel.refreshCurrentLocation(showFeedback = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh GPS", tint = PoliceThemeColors.navyText)
                        }
                    }
                }

                Text(
                    text = "ක්ෂණික පණිවිඩ (Quick Presets):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = message == preset,
                            onClick = { message = preset },
                            label = {
                                Text(
                                    text = preset.take(26) + if (preset.length > 26) "…" else "",
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 600) message = it },
                    label = { Text("හදිසි පණිවිඩය (Message)") },
                    minLines = 3,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sos_message_input")
                )

                Text(
                    text = "ආසන්න දුර ප්‍රමාණය (Nearby Radius):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(2.0, 5.0, 10.0, 25.0, 50.0).forEach { r ->
                        FilterChip(
                            selected = radiusKm == r,
                            onClick = { radiusKm = r },
                            label = { Text("${r.toInt()} km", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Text(
                    text = "මෙය යැවූ විගස ඔබ අවට (${radiusKm.toInt()} km ඇතුළත) සිටින OIC හා සියලුම නිලධාරීන්ට Notification එකක් සමඟ ඔබේ GPS ස්ථානය ලැබෙනු ඇත.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSending = true
                    viewModel.sendEmergencySos(
                        message = message,
                        radiusKm = radiusKm,
                        onSuccess = {
                            isSending = false
                            onDismiss()
                        },
                        onError = {
                            isSending = false
                        }
                    )
                },
                enabled = message.isNotBlank() && !isSending,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626),
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("send_sos_confirm_btn")
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("හදිසි SOS යවන්න", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) {
                Text("අවලංගු කරන්න")
            }
        }
    )
}
