package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppUser
import com.example.ui.theme.BadgeGoldBright
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceThemeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OICStationTransferDialog(
    viewModel: PoliceViewModel,
    onDismiss: () -> Unit
) {
    val appUser by viewModel.appUser.collectAsStateWithLifecycle()
    val subordinates by viewModel.subordinates.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Transfer Subordinates, 1: OIC Move Station

    // Tab 0 State: Transfer Subordinates
    var newOicCodeForSubordinates by remember { mutableStateOf("") }
    val selectedOfficerUids = remember(subordinates) {
        mutableStateMapOf<String, Boolean>().apply {
            subordinates.forEach { this[it.uid] = true }
        }
    }

    // Tab 1 State: OIC Move Station
    var newStationName by remember { mutableStateOf("") }
    var newStationCode by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PoliceNavyDark)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BadgeGoldBright.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = BadgeGoldBright,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ස්ථාන මාරුවීම් හා භාරදීම්",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Station Handover & Transfers",
                                color = BadgeGoldBright,
                                fontSize = 11.sp
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

                // Primary Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = PoliceThemeColors.navyText.copy(alpha = 0.08f),
                    contentColor = PoliceThemeColors.navyText
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "නිලධාරීන් පැවරීම\n(Transfer Officers)",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "OIC නව ස්ථානයකට\n(OIC Station Move)",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                // Tab Content
                when (selectedTab) {
                    0 -> {
                        // Transfer Subordinates to New OIC
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "නව ස්ථානාධිපති වෙත නිලධාරීන් පැවරීම",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ඔබ ස්ථානයෙන් බැහැර වන විට, ඔබ යටතේ සිටින නිලධාරීන් නව OICවරයාගේ කේතය වෙත මාරු කිරීමට නව OIC කේතය ඇතුළත් කරන්න.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = newOicCodeForSubordinates,
                                onValueChange = { newOicCodeForSubordinates = it.uppercase() },
                                label = { Text("නව OICවරයාගේ ස්ථාන කේතය (New OIC Code)") },
                                placeholder = { Text("e.g. OIC-COL-5821") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Subordinates Selection Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "මාරු කරන නිලධාරීන් තෝරන්න (${selectedOfficerUids.count { it.value }}/${subordinates.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row {
                                    TextButton(onClick = {
                                        subordinates.forEach { selectedOfficerUids[it.uid] = true }
                                    }) {
                                        Text("සියල්ල (All)", fontSize = 11.sp)
                                    }
                                    TextButton(onClick = {
                                        subordinates.forEach { selectedOfficerUids[it.uid] = false }
                                    }) {
                                        Text("ඉවත් (None)", fontSize = 11.sp)
                                    }
                                }
                            }

                            if (subordinates.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("මාරු කිරීමට නිලධාරීන් නොමැත.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(subordinates) { officer ->
                                        val isChecked = selectedOfficerUids[officer.uid] ?: false
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedOfficerUids[officer.uid] = !isChecked },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isChecked) PoliceThemeColors.navyText.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = { selectedOfficerUids[officer.uid] = it }
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = officer.displayName,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${officer.rank.ifBlank { "Officer" }} • ${officer.badgeNumber.ifBlank { "No badge" }}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val selectedCount = selectedOfficerUids.count { it.value }
                            Button(
                                onClick = {
                                    val uids = selectedOfficerUids.filter { it.value }.keys.toList()
                                    if (newOicCodeForSubordinates.isNotBlank() && uids.isNotEmpty()) {
                                        viewModel.transferSubordinatesToNewOic(uids, newOicCodeForSubordinates)
                                        onDismiss()
                                    }
                                },
                                enabled = newOicCodeForSubordinates.isNotBlank() && selectedCount > 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DutyActiveGreen,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = "තෝරාගත් නිලධාරීන් ($selectedCount) නව OIC වෙත පවරන්න",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    1 -> {
                        // OIC Station Move / Change Station Code
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "ස්ථානාධිපතිවරයා වෙනත් ස්ථානයකට මාරුවීම",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ඔබ නව පොලිස් ස්ථානයකට පත්වී ඇත්නම්, එම ස්ථානයේ නම සහ නව OIC කේතයක් ජනනය කරගන්න. (හෝ එම ස්ථානයේ දැනටමත් පවතින කේතය ඇතුළත් කරන්න).",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PoliceThemeColors.navyText.copy(alpha = 0.08f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "වත්මන් ස්ථානය: ${appUser?.stationName?.ifBlank { "සටහන් කර නොමැත" } ?: "සටහන් කර නොමැත"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "වත්මන් OIC කේතය: ${appUser?.oicStationCode ?: ""}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PoliceThemeColors.navyText
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = newStationName,
                                onValueChange = { newStationName = it },
                                label = { Text("නව පොලිස් ස්ථානයේ නම (New Police Station)") },
                                placeholder = { Text("e.g. Kandy Central Police Station") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = newStationCode,
                                onValueChange = { newStationCode = it.uppercase() },
                                label = { Text("නව OIC කේතය (New OIC Code)") },
                                placeholder = { Text("e.g. OIC-KND-7482") },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        newStationCode = viewModel.generateSuggestedOicCode(newStationName)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Generate",
                                            tint = PoliceThemeColors.goldText
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "💡 ස්වයංක්‍රීයව නව කේතයක් ජනනය කිරීමට දකුණු පස ඇති තරු ලකුණ මත ක්ලික් කරන්න.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = {
                                    if (newStationName.isNotBlank()) {
                                        viewModel.transferOicToNewStation(newStationName, newStationCode)
                                        onDismiss()
                                    }
                                },
                                enabled = newStationName.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PoliceNavyPrimary,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = "නව ස්ථානය භාරගන්න (Update Station & Code)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
