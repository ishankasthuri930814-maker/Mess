package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.OfficerProfile
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceSlate
import com.example.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: PoliceViewModel
) {
    val context = LocalContext.current
    val currentProfile by viewModel.officerProfile.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val dutyRecords by viewModel.filteredDutyRecords.collectAsStateWithLifecycle()
    val monthlySummary by viewModel.monthlySummary.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf("Police Constable (PC)") }
    var badgeNumber by remember { mutableStateOf("") }
    var station by remember { mutableStateOf("") }
    var rankDropdownExpanded by remember { mutableStateOf(false) }

    val rankOptions = listOf(
        "Police Constable (PC)",
        "Woman Police Constable (WPC)",
        "Police Sergeant (PS)",
        "Woman Police Sergeant (WPS)",
        "Sub Inspector of Police (SI)",
        "Inspector of Police (IP)",
        "Chief Inspector (CI)",
        "Assistant Superintendent of Police (ASP)",
        "Superintendent of Police (SP)"
    )

    LaunchedEffect(currentProfile) {
        currentProfile?.let {
            name = it.name
            rank = it.rank.ifBlank { "Police Constable (PC)" }
            badgeNumber = it.badgeNumber
            station = it.station
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 1. Virtual Police Identification Card
        PoliceIdBadgeCard(
            name = name,
            rank = rank,
            badgeNumber = badgeNumber,
            station = station
        )

        // 2. Registration & Edit Form Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("officer_profile_form_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "නිලධාරි ලියාපදිංචිය (Officer Registration)",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "ඔබගේ නම, තනතුර සහ නිල අංකය ලියාපදිංචි කර තබන්න.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("නිලධාරියාගේ නම (Officer Name)") },
                    placeholder = { Text("e.g. Ishan Kasthuri") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("officer_name_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Rank Selection Dropdown
                ExposedDropdownMenuBox(
                    expanded = rankDropdownExpanded,
                    onExpandedChange = { rankDropdownExpanded = !rankDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = rank,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("තනතුර (Rank / Designation)") },
                        leadingIcon = {
                            Icon(Icons.Default.Security, contentDescription = null)
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rankDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("officer_rank_input")
                    )

                    ExposedDropdownMenu(
                        expanded = rankDropdownExpanded,
                        onDismissRequest = { rankDropdownExpanded = false }
                    ) {
                        rankOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    rank = option
                                    rankDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Badge / Police Reg Number
                OutlinedTextField(
                    value = badgeNumber,
                    onValueChange = { badgeNumber = it },
                    label = { Text("නිල අංකය (Police / Reg No)") },
                    placeholder = { Text("e.g. 84725") },
                    leadingIcon = {
                        Icon(Icons.Default.Badge, contentDescription = null)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("officer_badge_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Police Station / Division
                OutlinedTextField(
                    value = station,
                    onValueChange = { station = it },
                    label = { Text("පොලිස් ස්ථානය (Police Station / Division)") },
                    placeholder = { Text("e.g. Colombo Central / Kandy") },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("officer_station_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (name.isBlank() || badgeNumber.isBlank()) {
                            Toast.makeText(context, "කරුණාකර නම සහ නිල අංකය ඇතුළත් කරන්න", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.saveOfficerProfile(name, rank, badgeNumber, station)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_profile_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("තොරතුරු සුරකින්න (Save Profile)", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Overall Statistics & Compression Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "යෙදුමේ සංඛ්‍යාලේඛන (App Statistics)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(
                        title = "රාජකාරි සටහන්",
                        value = "${dutyRecords.size}",
                        icon = Icons.Default.MenuBook,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = "මෙස් ආහාර",
                        value = "${monthlySummary.totalMeals}",
                        icon = Icons.Default.Restaurant,
                        color = BadgeGold,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Storage Savings Banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DutyActiveGreen.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compress,
                            contentDescription = null,
                            tint = DutyActiveGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            val savedStr = if (storageStats.totalSavedKb > 1024) {
                                "%.2f MB".format(storageStats.totalSavedKb / 1024f)
                            } else {
                                "${storageStats.totalSavedKb} KB"
                            }
                            Text(
                                text = "ස්වයංක්‍රීය සම්පීඩනයෙන් ඉතිරි වූ ඉඩ: $savedStr",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DutyActiveGreen
                            )
                            Text(
                                text = "ඡායාරූප ${storageStats.totalPhotos} ක ප්‍රමාණය ස්වයංක්‍රීයව අඩු කර සුරැකිණි",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 4. Copy Report to Clipboard
        OutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = buildString {
                    appendLine("=== පොලිස් රාජකාරි සහ මෙස් වාර්තාව ===")
                    appendLine("නිලධාරියා: $name ($rank)")
                    appendLine("නිල අංකය: $badgeNumber")
                    appendLine("පොලිස් ස්ථානය: $station")
                    appendLine("දිනය: ${DateTimeUtils.formatDisplayDate(System.currentTimeMillis())}")
                    appendLine("මෙම මස මෙස් ආහාර වේල්: ${monthlySummary.totalMeals} (උදේ: ${monthlySummary.breakfastCount}, දිවා: ${monthlySummary.lunchCount}, රාත්‍රී: ${monthlySummary.dinnerCount})")
                }
                clipboard.setPrimaryClip(ClipData.newPlainText("Police Report", text))
                Toast.makeText(context, "වාර්තාව කොපි කරගන්නා ලදී (Copied to clipboard)", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("copy_report_btn"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("සාරාංශ වාර්තාව කොපි කරන්න (Copy Summary)")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PoliceIdBadgeCard(
    name: String,
    rank: String,
    badgeNumber: String,
    station: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("police_id_badge_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PoliceNavyPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            PoliceNavyDark,
                            PoliceNavyPrimary,
                            PoliceSlate
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header of ID Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Column {
                            Text(
                                text = "SRI LANKA POLICE",
                                color = BadgeGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "ශ්‍රී ලංකා පොලීසිය",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (badgeNumber.isNotBlank()) "NO: $badgeNumber" else "REG NO",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Officer Name
                Text(
                    text = name.ifBlank { "නිලධාරියාගේ නම (Officer Name)" },
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Rank
                Text(
                    text = rank,
                    color = BadgeGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Station
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = station.ifBlank { "පොලිස් ස්ථානය (Police Station)" },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StatBox(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
                Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
