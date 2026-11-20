package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import com.example.ui.theme.PoliceThemeColors
import com.example.util.DateTimeUtils

@Composable
fun OfficerDetailDialog(
    viewModel: PoliceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val officer by viewModel.selectedOfficer.collectAsStateWithLifecycle()
    val dutyLogs by viewModel.selectedOfficerActivity.collectAsStateWithLifecycle()
    val messLogs by viewModel.selectedOfficerMessRecords.collectAsStateWithLifecycle()
    val runningCharts by viewModel.selectedOfficerRunningCharts.collectAsStateWithLifecycle()

    val currentOfficer = officer ?: return
    var selectedSection by remember { mutableIntStateOf(0) }

    fun dialOfficer(phone: String) {
        val clean = phone.replace(" ", "").replace("-", "")
        if (clean.isBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
        } catch (_: Exception) {
            Toast.makeText(context, "ඇමතුම් ලබාගත නොහැක", Toast.LENGTH_SHORT).show()
        }
    }

    fun openMap(lat: Double, lng: Double) {
        viewModel.navigateToLocationOnMap(
            target = MapNavigationTarget(
                title = "${currentOfficer.displayName} (${if (currentOfficer.isOnDuty) "ON DUTY" else "OFF DUTY"})",
                subtitle = "${currentOfficer.rank.ifBlank { "Officer" }} • ${currentOfficer.badgeNumber.ifBlank { "N/A" }}",
                lat = lat,
                lng = lng,
                phone = currentOfficer.phoneNumber
            ),
            openDialog = true
        )
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (currentOfficer.isOnDuty) PoliceThemeColors.greenText.copy(alpha = 0.2f)
                                else PoliceThemeColors.navyText.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (currentOfficer.isOnDuty) PoliceThemeColors.greenText else PoliceThemeColors.navyText,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentOfficer.displayName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${currentOfficer.rank.ifBlank { "Officer" }} • ${currentOfficer.badgeNumber.ifBlank { "No Badge" }}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status & Station Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (currentOfficer.isOnDuty) DutyActiveGreen else DutyOffOrange
                    ) {
                        Text(
                            text = if (currentOfficer.isOnDuty) "● රාජකාරියේ නියුතුයි (ON DUTY)" else "○ බැහැරයි (OFF DUTY)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PoliceThemeColors.navyText.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${currentOfficer.stationName.ifBlank { currentOfficer.oicStationCode ?: "N/A" }}",
                            color = PoliceThemeColors.navyText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Personal Contact, Address & GPS Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (currentOfficer.phoneNumber.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = PoliceThemeColors.greenText, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("පුද්ගලික දුරකථන අංකය", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(currentOfficer.phoneNumber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { dialOfficer(currentOfficer.phoneNumber) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DutyActiveGreen,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("call_officer_detail_btn")
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Call", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Text(
                                text = "දුරකථන අංකය ඇතුළත් කර නොමැත",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (currentOfficer.address.isNotBlank()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Home, contentDescription = null, tint = PoliceThemeColors.navyText, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ලිපිනය: ${currentOfficer.address}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (currentOfficer.email.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Email, contentDescription = null, tint = PoliceThemeColors.navyText, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentOfficer.email,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        currentOfficer.lastLocation?.let { loc ->
                            val lat = (loc["lat"] as? Number)?.toDouble() ?: 0.0
                            val lng = (loc["lng"] as? Number)?.toDouble() ?: 0.0
                            val ts = (loc["timestamp"] as? Number)?.toLong() ?: 0L
                            if (lat != 0.0 || lng != 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "GPS: ${String.format("%.5f", lat)}, ${String.format("%.5f", lng)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (ts > 0) {
                                            Text(
                                                text = "යාවත්කාලීන: ${DateTimeUtils.formatTime(ts)}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    TextButton(onClick = { openMap(lat, lng) }) {
                                        Text("Map", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Data Section Selector Tabs (Duty / Mess / Running Chart)
                TabRow(selectedTabIndex = selectedSection) {
                    Tab(
                        selected = selectedSection == 0,
                        onClick = { selectedSection = 0 },
                        text = { Text("රාජකාරි (${dutyLogs.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    )
                    Tab(
                        selected = selectedSection == 1,
                        onClick = { selectedSection = 1 },
                        text = { Text("මෙස් (${messLogs.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    )
                    Tab(
                        selected = selectedSection == 2,
                        onClick = { selectedSection = 2 },
                        text = { Text("ගමන් (${runningCharts.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tab Content List
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedSection) {
                        0 -> {
                            if (dutyLogs.isEmpty()) {
                                EmptyDetailState("වාර්තා වූ රාජකාරි සටහන් නොමැත.")
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(dutyLogs.sortedByDescending { it.timestamp }) { record ->
                                        val dutyTitle = when (record.type) {
                                            "ON_DUTY" -> "රාජකාරියට පැමිණීම (ON DUTY)"
                                            "EXTRA_DUTY" -> "අමතර / විශේෂ රාජකාරිය"
                                            else -> "රාජකාරියෙන් බැහැරවීම (OFF DUTY)"
                                        }
                                        val pillColor = when (record.type) {
                                            "ON_DUTY" -> PoliceThemeColors.greenText
                                            "EXTRA_DUTY" -> PoliceThemeColors.blueText
                                            else -> PoliceThemeColors.orangeText
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(shape = RoundedCornerShape(6.dp), color = pillColor.copy(alpha = 0.16f)) {
                                                        Text(
                                                            text = dutyTitle,
                                                            color = pillColor,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "${DateTimeUtils.formatDate(record.timestamp)} ${DateTimeUtils.formatTime(record.timestamp)}",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (record.dutyNature.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("ස්වභාවය: ${record.dutyNature}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                                if (record.ibPage.isNotBlank() || record.ibParagraph.isNotBlank()) {
                                                    Text(
                                                        text = "IB පිටුව: ${record.ibPage.ifBlank { "-" }} • ඡේදය: ${record.ibParagraph.ifBlank { "-" }}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (record.dutyDurationMinutes > 0) {
                                                    Text(
                                                        text = "කාලය: ${record.dutyDurationMinutes / 60} පැය ${record.dutyDurationMinutes % 60} මිනි",
                                                        fontSize = 11.sp,
                                                        color = PoliceThemeColors.navyText
                                                    )
                                                }
                                                if (record.remarks.isNotBlank()) {
                                                    Text("සටහන්: ${record.remarks}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            if (messLogs.isEmpty()) {
                                EmptyDetailState("වාර්තා වූ මෙස් ආහාර සටහන් නොමැත.")
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(messLogs.sortedByDescending { it.timestamp }) { mess ->
                                        val mealSinhala = when (mess.mealType) {
                                            "BREAKFAST" -> "උදෑසන ආහාරය"
                                            "LUNCH" -> "දිවා ආහාරය"
                                            "DINNER" -> "රාත්‍රී ආහාරය"
                                            else -> mess.mealType
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("${mess.date} • $mealSinhala", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = if (mess.isTaken) PoliceThemeColors.greenText.copy(alpha = 0.16f)
                                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
                                                    ) {
                                                        Text(
                                                            text = if (mess.isTaken) "ලබාගත්තා (රු. %.0f)".format(mess.price) else "නොමැත",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (mess.isTaken) PoliceThemeColors.greenText else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (mess.mealNotes.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("සටහන: ${mess.mealNotes}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            if (runningCharts.isEmpty()) {
                                EmptyDetailState("වාර්තා වූ වාහන ගමන් සටහන් (Running Chart) නොමැත.")
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(runningCharts.sortedByDescending { it.timestamp }) { rc ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("වාහනය: ${rc.vehicleNumber}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PoliceThemeColors.navyText)
                                                    Text(rc.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "මීටරය: ${rc.startMeter} → ${rc.endMeter} (දුර: ${"%.1f".format(rc.distanceKm)} km)",
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = "ඉන්ධන: ආරම්භය ${"%.1f".format(rc.startFuel)}L • එකතු ${"%.1f".format(rc.addedFuel)}L • ඉතිරි ${"%.1f".format(rc.remainingFuel)}L",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (rc.notes.isNotBlank()) {
                                                    Text("සටහන්: ${rc.notes}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) {
                        Text("වසන්න (Close)")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDetailState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
