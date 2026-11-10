package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.util.DateTimeUtils

@Composable
fun OfficerDetailDialog(
    viewModel: PoliceViewModel,
    onDismiss: () -> Unit
) {
    val officer by viewModel.selectedOfficer.collectAsStateWithLifecycle()
    val activity by viewModel.selectedOfficerActivity.collectAsStateWithLifecycle()

    val currentOfficer = officer ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (currentOfficer.isOnDuty) DutyActiveGreen.copy(alpha = 0.2f) else PoliceNavyPrimary.copy(alpha = 0.15f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = if (currentOfficer.isOnDuty) DutyActiveGreen else PoliceNavyPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
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
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                            text = if (currentOfficer.isOnDuty) "● රාජකාරියේ නියුතුයි (ON DUTY)" else "○ නිවාඩු / බැහැරයි (OFF DUTY)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PoliceNavyPrimary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "කේතය: ${currentOfficer.oicStationCode?.ifBlank { "N/A" } ?: "N/A"}",
                            color = PoliceNavyPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // GPS Location Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "සජීවී GPS පිහිටීම (Live Location)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        currentOfficer.lastLocation?.let { loc ->
                            val lat = loc["lat"] as? Double ?: 0.0
                            val lng = loc["lng"] as? Double ?: 0.0
                            val ts = loc["timestamp"] as? Long ?: 0L
                            if (lat != 0.0 || lng != 0.0) {
                                Text(
                                    text = "අක්ෂාංශ, දේශාංශ: $lat, $lng",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (ts > 0) {
                                    Text(
                                        text = "යාවත්කාලීන වූයේ: ${DateTimeUtils.formatTime(ts)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    text = "තවමත් GPS ස්ථානය වාර්තා වී නොමැත.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } ?: Text(
                            text = "තවමත් GPS ස්ථානය වාර්තා වී නොමැත.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "මෑත රාජකාරි ක්‍රියාකාරකම් (Recent Logs)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (activity.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "වාර්තා වූ රාජකාරි සටහන් නොමැත.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.height(180.dp)) {
                        items(activity.sortedByDescending { it.timestamp }.take(10)) { record ->
                            val dutyTitle = when (record.type) {
                                "ON_DUTY" -> "රාජකාරිය වාර්තා කිරීම (ON DUTY)"
                                "EXTRA_DUTY" -> "අමතර රාජකාරිය (EXTRA DUTY)"
                                else -> "බැහැරවීම (OFF DUTY)"
                            }
                            ListItem(
                                headlineContent = { Text(dutyTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                                supportingContent = {
                                    Column {
                                        Text(
                                            text = "${DateTimeUtils.formatDate(record.timestamp)} • ${DateTimeUtils.formatTime(record.timestamp)}",
                                            fontSize = 11.sp
                                        )
                                        if (record.dutyNature.isNotBlank()) {
                                            Text(
                                                text = "ස්වභාවය: ${record.dutyNature}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("වසන්න (Close)")
            }
        }
    )
}
