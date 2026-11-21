package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.luminance
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.BadgeGoldBright
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceSlate
import com.example.ui.theme.PoliceThemeColors
import com.example.util.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: PoliceViewModel,
    isMandatoryCompletionMode: Boolean = false
) {
    val context = LocalContext.current
    val currentProfile by viewModel.officerProfile.collectAsStateWithLifecycle()
    val appUser by viewModel.appUser.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val dutyRecords by viewModel.filteredDutyRecords.collectAsStateWithLifecycle()
    val monthlySummary by viewModel.monthlySummary.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf("Police Constable (PC)") }
    var badgeNumber by remember { mutableStateOf("") }
    var station by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var rankDropdownExpanded by remember { mutableStateOf(false) }

    var isTransferDialogOpen by remember { mutableStateOf(false) }
    var newStationCodeInput by remember { mutableStateOf("") }
    var isLogoutConfirmOpen by remember { mutableStateOf(false) }
    var isStealthPasswordOpen by remember { mutableStateOf(false) }
    var isStealthOptionsOpen by remember { mutableStateOf(false) }
    var stealthTapCount by remember { mutableIntStateOf(0) }
    var lastStealthTapMs by remember { mutableLongStateOf(0L) }

    fun registerSecretTap() {
        val now = System.currentTimeMillis()
        stealthTapCount = if (now - lastStealthTapMs <= 4000L) stealthTapCount + 1 else 1
        lastStealthTapMs = now
        if (stealthTapCount >= 7) {
            stealthTapCount = 0
            isStealthPasswordOpen = true
        }
    }

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

    LaunchedEffect(currentProfile, appUser) {
        val u = appUser
        val p = currentProfile
        name = (u?.displayName?.takeIf { it.isNotBlank() } ?: p?.name).orEmpty()
        rank = (u?.rank?.takeIf { it.isNotBlank() } ?: p?.rank?.takeIf { it.isNotBlank() }) ?: "Police Constable (PC)"
        badgeNumber = (u?.badgeNumber?.takeIf { it.isNotBlank() } ?: p?.badgeNumber).orEmpty()
        station = (u?.stationName?.takeIf { it.isNotBlank() } ?: p?.station?.takeIf { it.isNotBlank() } ?: u?.oicStationCode).orEmpty()
        phoneNumber = (u?.phoneNumber?.takeIf { it.isNotBlank() } ?: p?.phoneNumber).orEmpty()
        address = (u?.address?.takeIf { it.isNotBlank() } ?: p?.address).orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        if (isMandatoryCompletionMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ප්‍රධාන පුවරුවට පිවිසීමට පෙර ඔබගේ පුද්ගලික දුරකථන අංකය, පදිංචි ලිපිනය සහ සම්පූර්ණ නිලධාරී තොරතුරු අනිවාර්යයෙන් සුරැකිය යුතුය.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // 1. Virtual Police Identification Card
        PoliceIdBadgeCard(
            name = name,
            rank = rank,
            badgeNumber = badgeNumber,
            station = station,
            phoneNumber = phoneNumber,
            onSecretTitleTap = { registerSecretTap() }
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
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
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
                    label = { Text("පොලිස් ස්ථානය * (Police Station / Division)") },
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

                Spacer(modifier = Modifier.height(12.dp))

                // Mandatory Personal Phone Number
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("පුද්ගලික දුරකථන අංකය * (Personal Phone)") },
                    placeholder = { Text("0771234567") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = phoneNumber.isNotBlank() && phoneNumber.filter { it.isDigit() }.length < 9,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("officer_phone_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Mandatory Personal Address
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("පුද්ගලික ලිපිනය * (Personal Address)") },
                    placeholder = { Text("ඔබගේ පදිංචි ලිපිනය ඇතුළත් කරන්න") },
                    leadingIcon = {
                        Icon(Icons.Default.Home, contentDescription = null)
                    },
                    minLines = 2,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("officer_address_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (name.isBlank() || badgeNumber.isBlank() || station.isBlank() || phoneNumber.filter { it.isDigit() }.length < 9 || address.isBlank()) {
                            Toast.makeText(
                                context,
                                "කරුණාකර නම, නිල අංකය, ස්ථානය, දුරකථන අංකය සහ ලිපිනය සියල්ල නිවැරදිව ඇතුළත් කරන්න",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        viewModel.saveOfficerProfile(
                            name = name,
                            rank = rank,
                            badgeNumber = badgeNumber,
                            station = station,
                            phoneNumber = phoneNumber,
                            address = address
                        )
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

        // 2.5 OIC & Station Transfer Card
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(PoliceThemeColors.navyText.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = PoliceThemeColors.navyText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ස්ථාන මාරුවීම් (Station Transfer)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "නව OIC කෙනෙකු යටතට මාරුවීම",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "වත්මන් OIC කේතය:",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = appUser?.oicStationCode?.ifBlank { "නොමැත" } ?: "නොමැත",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PoliceThemeColors.navyText
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "අනුමැති තත්ත්වය:",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val isApproved = appUser?.status == "APPROVED"
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isApproved) DutyActiveGreen.copy(alpha = 0.15f) else BadgeGold.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (isApproved) "අනුමතයි (Approved)" else "අනුමැතිය අපේක්ෂිතයි",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isApproved) PoliceThemeColors.greenText else PoliceThemeColors.goldText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { isTransferDialogOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("නව OIC වෙත මාරු වන්න (Transfer to New OIC)")
                }
            }
        }

        if (isTransferDialogOpen) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { isTransferDialogOpen = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ස්ථාන මාරුවීමක් සටහන් කිරීම", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "ඔබ වෙනත් පොලිස් ස්ථානයකට හෝ වෙනත් OICවරයෙකු යටතට මාරුවී ඇත්නම්, ඔබගේ නව ස්ථානාධිපති ලබාදුන් OIC කේතය (Station Code) මෙහි ඇතුළත් කරන්න.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = newStationCodeInput,
                            onValueChange = { newStationCodeInput = it.uppercase() },
                            label = { Text("නව OIC කේතය (New OIC Code)") },
                            placeholder = { Text("e.g. OIC-KND-4821") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "මාරුවීමෙන් පසු ඔබගේ නව OIC වෙත අනුමැතිය සඳහා ඉල්ලීම යොමු කෙරේ.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newStationCodeInput.isNotBlank()) {
                                viewModel.transferOfficerToNewOic(newStationCodeInput)
                                isTransferDialogOpen = false
                                newStationCodeInput = ""
                            }
                        },
                        enabled = newStationCodeInput.isNotBlank()
                    ) {
                        Text("මාරු වන්න (Confirm)")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { isTransferDialogOpen = false }) {
                        Text("අවලංගු කරන්න")
                    }
                }
            )
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
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = "මෙස් ආහාර",
                        value = "${monthlySummary.totalMeals}",
                        icon = Icons.Default.Restaurant,
                        color = PoliceThemeColors.goldText,
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
                            tint = PoliceThemeColors.greenText,
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
                                color = PoliceThemeColors.greenText
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

        // 5. Explicit Sign Out Button (session stays saved until tapped)
        OutlinedButton(
            onClick = { isLogoutConfirmOpen = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sign_out_btn"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("ගිණුමෙන් ඉවත් වන්න (Sign Out)")
        }

        if (isLogoutConfirmOpen) {
            AlertDialog(
                onDismissRequest = { isLogoutConfirmOpen = false },
                title = { Text("ගිණුමෙන් ඉවත් වන්නද?", fontWeight = FontWeight.Bold) },
                text = {
                    Text("ඔබ Sign Out වන තුරු ඔබගේ ගිණුම ස්වයංක්‍රීයව සුරැකී පවතී. නැවත වෙනත් ගිණුමකින් පිවිසීමට අවශ්‍ය නම් පමණක් ඉවත් වන්න.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isLogoutConfirmOpen = false
                            viewModel.logout()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("ඉවත් වන්න (Sign Out)")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isLogoutConfirmOpen = false }) {
                        Text("රැඳී සිටින්න")
                    }
                }
            )
        }

        if (isStealthPasswordOpen) {
            OfficerStealthPasswordDialog(
                viewModel = viewModel,
                onUnlocked = {
                    isStealthPasswordOpen = false
                    isStealthOptionsOpen = true
                },
                onDismiss = { isStealthPasswordOpen = false }
            )
        }

        if (isStealthOptionsOpen) {
            OfficerStealthOptionsDialog(
                viewModel = viewModel,
                currentUser = appUser,
                onDismiss = { isStealthOptionsOpen = false }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        val footerInteraction = remember { MutableInteractionSource() }
        Text(
            text = "All In One Police • Sri Lanka Police v1.0",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(
                    interactionSource = footerInteraction,
                    indication = null
                ) { registerSecretTap() }
                .padding(vertical = 8.dp)
                .testTag("profile_secret_footer")
        )
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
fun PoliceIdBadgeCard(
    name: String,
    rank: String,
    badgeNumber: String,
    station: String,
    phoneNumber: String = "",
    onSecretTitleTap: () -> Unit = {}
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
                        val titleInteraction = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = titleInteraction,
                                    indication = null
                                ) { onSecretTitleTap() }
                                .testTag("id_badge_secret_title")
                        ) {
                            Text(
                                text = "SRI LANKA POLICE",
                                color = BadgeGoldBright,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "ශ්‍රී ලංකා පොලීසිය",
                                color = Color.White.copy(alpha = 0.85f),
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
                    color = BadgeGoldBright,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Station & Phone
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

                if (phoneNumber.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = phoneNumber,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
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
            val iconTint = if (color.luminance() > 0.45f) PoliceNavyDark else Color.White
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
                Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
