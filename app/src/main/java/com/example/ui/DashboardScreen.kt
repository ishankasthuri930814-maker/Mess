package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DutyRecord
import com.example.data.MessRecord
import com.example.data.OfficerProfile
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.BadgeGoldLight
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import com.example.ui.theme.PoliceNavyPrimary
import com.example.util.DateTimeUtils

@Composable
fun DashboardScreen(
    viewModel: PoliceViewModel,
    onNavigateToDuty: () -> Unit,
    onNavigateToRunningChart: () -> Unit,
    onNavigateToDirectory: () -> Unit,
    onNavigateToMess: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onOpenDutyDialog: (type: String) -> Unit,
    onOpenMealDialog: (mealType: String) -> Unit,
    onViewPhoto: (path: String, title: String, compKb: Int, origKb: Int) -> Unit
) {
    val profile by viewModel.officerProfile.collectAsStateWithLifecycle()
    val latestDuty by viewModel.latestDutyRecord.collectAsStateWithLifecycle()
    val latestShift by viewModel.latestShiftRecord.collectAsStateWithLifecycle()
    val todayMeals by viewModel.selectedDayMeals.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val recentDuties by viewModel.filteredDutyRecords.collectAsStateWithLifecycle()

    val todayDutyHours by viewModel.todayDutyHoursFormatted.collectAsStateWithLifecycle()
    val monthlyDutyHours by viewModel.monthlyDutyHoursFormatted.collectAsStateWithLifecycle()

    val isOnDuty = latestShift?.type == "ON_DUTY"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // 1. Officer Header Banner / Card
        item {
            OfficerHeaderCard(
                profile = profile,
                onEditProfile = onNavigateToProfile
            )
        }

        // Quick Police Services Grid
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickActionShortcut(
                        title = "රාජකාරි",
                        subtitle = "Duty Logs",
                        icon = Icons.Default.MenuBook,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = onNavigateToDuty,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionShortcut(
                        title = "ගමන් සටහන්",
                        subtitle = "Running Chart",
                        icon = Icons.Default.DirectionsCar,
                        color = DutyActiveGreen,
                        onClick = onNavigateToRunningChart,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionShortcut(
                        title = "මෙස් ආහාර",
                        subtitle = "Mess Meals",
                        icon = Icons.Default.Restaurant,
                        color = BadgeGold,
                        onClick = onNavigateToMess,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionShortcut(
                        title = "නාමාවලිය",
                        subtitle = "Directory",
                        icon = Icons.Default.Phone,
                        color = Color(0xFF0284C7),
                        onClick = onNavigateToDirectory,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Active Duty Status Card (Real-time Duty state)
        item {
            DutyStatusHeroCard(
                latestDuty = latestDuty,
                isOnDuty = isOnDuty,
                todayDutyHours = todayDutyHours,
                monthlyDutyHours = monthlyDutyHours,
                onReportOnDuty = { onOpenDutyDialog("ON_DUTY") },
                onReportExtraDuty = { onOpenDutyDialog("EXTRA_DUTY") },
                onReportOffDuty = { onOpenDutyDialog("OFF_DUTY") },
                onViewPhoto = onViewPhoto
            )
        }

        // 3. Today's Mess Meal Tracker (Breakfast, Lunch, Dinner quick toggle)
        item {
            TodayMessQuickCard(
                todayMeals = todayMeals,
                onToggleMeal = { type, taken -> viewModel.toggleMealTaken(type, taken) },
                onOpenMealDialog = onOpenMealDialog,
                onNavigateToMess = onNavigateToMess
            )
        }

        // 4. Storage & Compression Efficiency Card
        item {
            StorageEfficiencyCard(storageStats = storageStats)
        }

        // 5. Recent Duty Records Snippet
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "මෑත රාජකාරි සටහන් (Recent Logs)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "සියල්ල බලන්න",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateToDuty() }
                )
            }
        }

        if (recentDuties.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "තවම රාජකාරි සටහන් නොමැත",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onOpenDutyDialog("ON_DUTY") },
                            modifier = Modifier.testTag("dashboard_first_duty_button")
                        ) {
                            Text("රාජකාරියක් වාර්තා කරන්න")
                        }
                    }
                }
            }
        } else {
            val previewList = recentDuties.take(3)
            items(previewList.size) { index ->
                val duty = previewList[index]
                DutyItemCard(
                    record = duty,
                    onViewPhoto = onViewPhoto,
                    onDelete = { viewModel.deleteDutyRecord(duty) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun OfficerHeaderCard(
    profile: OfficerProfile?,
    onEditProfile: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("officer_header_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            PoliceNavyPrimary.copy(alpha = 0.08f),
                            BadgeGold.copy(alpha = 0.06f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge Icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            Brush.linearGradient(listOf(PoliceNavyPrimary, BadgeGold)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Police Shield",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val name = profile?.name?.ifBlank { null } ?: "නිලධාරී නම ඇතුළත් කරන්න"
                    val rank = profile?.rank?.ifBlank { null } ?: "Police Officer"
                    val badge = profile?.badgeNumber?.ifBlank { null } ?: "ලියාපදිංචි අංකය"
                    val station = profile?.station?.ifBlank { null } ?: "පොලිස් ස්ථානය"

                    Text(
                        text = name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$rank • No: $badge",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = station,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onEditProfile,
                    modifier = Modifier.testTag("edit_profile_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun DutyStatusHeroCard(
    latestDuty: DutyRecord?,
    isOnDuty: Boolean,
    todayDutyHours: String = "",
    monthlyDutyHours: String = "",
    onReportOnDuty: () -> Unit,
    onReportExtraDuty: () -> Unit,
    onReportOffDuty: () -> Unit,
    onViewPhoto: (path: String, title: String, compKb: Int, origKb: Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("duty_status_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOnDuty) DutyActiveGreen.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                colors = if (isOnDuty) listOf(DutyActiveGreen, DutyActiveGreen.copy(alpha = 0.4f))
                else listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = if (isOnDuty) DutyActiveGreen else DutyOffOrange,
                            shape = RoundedCornerShape(30.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isOnDuty) "රාජකාරියේ නිරතව සිටී (ON DUTY)" else "රාජකාරියෙන් බැහැරයි (OFF DUTY)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (latestDuty != null) {
                    Text(
                        text = DateTimeUtils.formatTime(latestDuty.timestamp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Duty Hours Summary Indicator
            if (todayDutyHours.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⏱️ අද කාලය: $todayDutyHours",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DutyActiveGreen
                        )
                        if (monthlyDutyHours.isNotBlank()) {
                            Text(
                                text = "🗓️ මස: $monthlyDutyHours",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isOnDuty && latestDuty != null) {
                // On duty details
                Text(
                    text = latestDuty.dutyNature.ifBlank { "රාජකාරි වාර්තාව" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "IB පිටුව: ${latestDuty.ibPage}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (latestDuty.ibParagraph.isNotBlank()) {
                        Text(
                            text = "ඡේදය: ${latestDuty.ibParagraph}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    val elapsed = System.currentTimeMillis() - latestDuty.timestamp
                    Text(
                        text = "ගතවූ කාලය: ${DateTimeUtils.formatDuration(elapsed)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!latestDuty.photoPath.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                onViewPhoto(
                                    latestDuty.photoPath,
                                    "තොරතුරු පොතේ පිටුව: ${latestDuty.ibPage}",
                                    latestDuty.compressedSizeKb,
                                    latestDuty.originalSizeKb
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Photo,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "පොතේ පිටුවේ ඡායාරූපය (${latestDuty.compressedSizeKb} KB - Auto Compressed)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (!latestDuty.scenePhotoPath.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BadgeGold.copy(alpha = 0.12f))
                            .clickable {
                                onViewPhoto(
                                    latestDuty.scenePhotoPath,
                                    "සිද්ධිය / ස්ථානය: ${latestDuty.dutyNature}",
                                    latestDuty.compressedSizeKb,
                                    latestDuty.originalSizeKb
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = BadgeGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "සිද්ධියේ / ස්ථානයේ ඡායාරූපය (Scene Photo)",
                            fontSize = 12.sp,
                            color = BadgeGold,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action 1: Add Extra Duty / Incident during shift (Unlimited!)
                Button(
                    onClick = onReportExtraDuty,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hero_add_extra_duty_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "+ අමතර රාජකාරියක් / සිද්ධියක් එක් කරන්න (Extra Duty)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action 2: Go Off Duty button
                Button(
                    onClick = onReportOffDuty,
                    colors = ButtonDefaults.buttonColors(containerColor = DutyOffOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hero_go_off_duty_btn")
                ) {
                    Text(
                        text = "රාජකාරියෙන් බැහැරවීම සටහන් කරන්න (Go Off Duty)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            } else {
                // Off duty details
                Text(
                    text = "නව රාජකාරියක් ආරම්භ කිරීමට සූදානම්ද?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "තොරතුරු පොතේ පිටුව සහ ඡේදය සටහන් කර ඡායාරූපය ලබාගන්න.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onReportOnDuty,
                    colors = ButtonDefaults.buttonColors(containerColor = DutyActiveGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hero_report_duty_btn")
                ) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "රාජකාරිය වාර්තා කරන්න (Report On Duty)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onReportExtraDuty,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hero_report_extra_duty_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "+ අමතර රාජකාරියක් / සිද්ධියක් සටහන් කරන්න",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun TodayMessQuickCard(
    todayMeals: List<MessRecord>,
    onToggleMeal: (mealType: String, isTaken: Boolean) -> Unit,
    onOpenMealDialog: (mealType: String) -> Unit,
    onNavigateToMess: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("today_mess_quick_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(BadgeGold.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = BadgeGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "අද මෙස් ආහාර (Today's Mess)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = DateTimeUtils.formatDisplayDate(System.currentTimeMillis()),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "විස්තර",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateToMess() }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Meal Options: Breakfast, Lunch, Dinner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickMealPill(
                    label = "උදේ",
                    english = "Breakfast",
                    mealType = "BREAKFAST",
                    records = todayMeals,
                    onToggle = { onToggleMeal("BREAKFAST", it) },
                    onOpenDialog = { onOpenMealDialog("BREAKFAST") },
                    modifier = Modifier.weight(1f)
                )

                QuickMealPill(
                    label = "දිවා",
                    english = "Lunch",
                    mealType = "LUNCH",
                    records = todayMeals,
                    onToggle = { onToggleMeal("LUNCH", it) },
                    onOpenDialog = { onOpenMealDialog("LUNCH") },
                    modifier = Modifier.weight(1f)
                )

                QuickMealPill(
                    label = "රාත්‍රී",
                    english = "Dinner",
                    mealType = "DINNER",
                    records = todayMeals,
                    onToggle = { onToggleMeal("DINNER", it) },
                    onOpenDialog = { onOpenMealDialog("DINNER") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun QuickMealPill(
    label: String,
    english: String,
    mealType: String,
    records: List<MessRecord>,
    onToggle: (Boolean) -> Unit,
    onOpenDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val record = records.firstOrNull { it.mealType == mealType }
    val isTaken = record?.isTaken == true
    val hasPhoto = !record?.photoPath.isNullOrBlank()

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle(!isTaken) },
        color = if (isTaken) DutyActiveGreen.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isTaken) CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(DutyActiveGreen, DutyActiveGreen)))
        else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            color = if (isTaken) DutyActiveGreen else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isTaken) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    } else {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }

                if (hasPhoto) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = "Photo attached",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isTaken) DutyActiveGreen else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isTaken) "ලබා ගත්තා" else "නොගත්තා",
                fontSize = 11.sp,
                color = if (isTaken) DutyActiveGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "සටහන / ඡායාරූපය",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onOpenDialog() }
            )
        }
    }
}

@Composable
fun StorageEfficiencyCard(storageStats: StorageStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("storage_efficiency_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Compress,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ස්වයංක්‍රීය ඡායාරූප සම්පීඩනය (Auto Compress)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                val savedText = if (storageStats.totalSavedKb > 1024) {
                    "%.1f MB ඉතිරි කර ඇත".format(storageStats.totalSavedKb / 1024f)
                } else {
                    "${storageStats.totalSavedKb} KB ඉතිරි කර ඇත"
                }
                Text(
                    text = "ඡායාරූප ${storageStats.totalPhotos} ක් සටහන්ව ඇත • $savedText (Device Storage Saved)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun QuickActionShortcut(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

