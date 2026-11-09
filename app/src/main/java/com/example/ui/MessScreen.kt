package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Today
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.data.MessRecord
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import com.example.util.DateTimeUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun MessScreen(
    viewModel: PoliceViewModel,
    onOpenMealDialog: (mealType: String, existing: MessRecord?) -> Unit,
    onViewPhoto: (path: String, title: String, compKb: Int, origKb: Int) -> Unit
) {
    val selectedDate by viewModel.selectedMessDate.collectAsStateWithLifecycle()
    val dayMeals by viewModel.selectedDayMeals.collectAsStateWithLifecycle()
    val monthlyMeals by viewModel.monthlyMessRecords.collectAsStateWithLifecycle()
    val monthlySummary by viewModel.monthlySummary.collectAsStateWithLifecycle()

    var mealCostRate by remember { mutableIntStateOf(180) } // Default approx mess cut per meal in LKR

    val breakfastRecord = dayMeals.firstOrNull { it.mealType == "BREAKFAST" }
    val lunchRecord = dayMeals.firstOrNull { it.mealType == "LUNCH" }
    val dinnerRecord = dayMeals.firstOrNull { it.mealType == "DINNER" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // 1. Date Selector Bar
        item {
            DateNavigationCard(
                selectedDate = selectedDate,
                onDateChange = { viewModel.selectMessDate(it) }
            )
        }

        // 2. Breakfast Card (උදේ ආහාරය)
        item {
            MealCard(
                title = "උදේ ආහාරය (Breakfast)",
                mealType = "BREAKFAST",
                icon = Icons.Default.WbSunny,
                iconColor = BadgeGold,
                record = breakfastRecord,
                onToggle = { isTaken -> viewModel.toggleMealTaken("BREAKFAST", isTaken) },
                onEdit = { onOpenMealDialog("BREAKFAST", breakfastRecord) },
                onViewPhoto = onViewPhoto,
                testTagPrefix = "breakfast"
            )
        }

        // 3. Lunch Card (දිවා ආහාරය)
        item {
            MealCard(
                title = "දිවා ආහාරය (Lunch)",
                mealType = "LUNCH",
                icon = Icons.Default.LightMode,
                iconColor = Color(0xFFF59E0B),
                record = lunchRecord,
                onToggle = { isTaken -> viewModel.toggleMealTaken("LUNCH", isTaken) },
                onEdit = { onOpenMealDialog("LUNCH", lunchRecord) },
                onViewPhoto = onViewPhoto,
                testTagPrefix = "lunch"
            )
        }

        // 4. Dinner Card (රාත්‍රී ආහාරය)
        item {
            MealCard(
                title = "රාත්‍රී ආහාරය (Dinner)",
                mealType = "DINNER",
                icon = Icons.Default.Bedtime,
                iconColor = Color(0xFF6366F1),
                record = dinnerRecord,
                onToggle = { isTaken -> viewModel.toggleMealTaken("DINNER", isTaken) },
                onEdit = { onOpenMealDialog("DINNER", dinnerRecord) },
                onViewPhoto = onViewPhoto,
                testTagPrefix = "dinner"
            )
        }

        // 5. Monthly Mess Summary & Bill Estimator
        item {
            MonthlyMessSummaryCard(
                summary = monthlySummary,
                mealCostRate = mealCostRate,
                onRateChange = { mealCostRate = it }
            )
        }

        // 6. Monthly Log Entries
        if (monthlyMeals.isNotEmpty()) {
            item {
                Text(
                    text = "මෙම මස මෙස් සටහන් (Month's Log)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(monthlyMeals, key = { it.id }) { record ->
                MonthlyMealListItem(
                    record = record,
                    onViewPhoto = onViewPhoto,
                    onDelete = { viewModel.deleteMealRecord(record) }
                )
            }
        }
    }
}

@Composable
fun DateNavigationCard(
    selectedDate: String,
    onDateChange: (String) -> Unit
) {
    val displayDate = DateTimeUtils.formatDisplayDateFromKey(selectedDate)
    val isToday = selectedDate == DateTimeUtils.getTodayKey()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("date_navigation_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { onDateChange(addDays(selectedDate, -1)) },
                modifier = Modifier.testTag("prev_date_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day")
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = displayDate,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isToday) {
                    Text(
                        text = "අද දිනය (Today)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isToday) {
                    IconButton(
                        onClick = { onDateChange(DateTimeUtils.getTodayKey()) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Today, contentDescription = "Jump to Today", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(
                    onClick = { onDateChange(addDays(selectedDate, 1)) },
                    modifier = Modifier.testTag("next_date_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day")
                }
            }
        }
    }
}

@Composable
fun MealCard(
    title: String,
    mealType: String,
    icon: ImageVector,
    iconColor: Color,
    record: MessRecord?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onViewPhoto: (path: String, title: String, compKb: Int, origKb: Int) -> Unit,
    testTagPrefix: String
) {
    val isTaken = record?.isTaken == true
    val hasPhoto = !record?.photoPath.isNullOrBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_meal_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTaken) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isTaken) 2.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Icon, Title, and Taken Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(iconColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isTaken) "ආහාරය ලබා ගත්තා (Taken)" else "ලබා නොගත්තා (Skipped)",
                        fontSize = 12.sp,
                        color = if (isTaken) DutyActiveGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isTaken) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                Switch(
                    checked = isTaken,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = DutyActiveGreen
                    ),
                    modifier = Modifier.testTag("${testTagPrefix}_toggle_switch")
                )
            }

            // Menu / Notes
            if (record?.mealNotes?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = record.mealNotes,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Photo Preview if attached
            if (hasPhoto && record?.photoPath != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            onViewPhoto(
                                record.photoPath,
                                title,
                                record.compressedSizeKb,
                                record.compressedSizeKb * 4
                            )
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = File(record.photoPath)),
                        contentDescription = "Meal Photo",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ආහාරයේ ඡායාරූපය (Meal Photo)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Compress,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ප්‍රමාණය: ${record.compressedSizeKb} KB (Auto Compressed)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = "බලන්න >",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Button: Add / Edit Note & Photo
            OutlinedButton(
                onClick = onEdit,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_edit_btn")
            ) {
                Icon(
                    imageVector = if (hasPhoto) Icons.Default.Edit else Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (hasPhoto || record?.mealNotes?.isNotBlank() == true) "සටහන / ඡායාරූපය සංස්කරණය"
                    else "ඡායාරූපයක් / සටහනක් එක් කරන්න",
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun MonthlyMessSummaryCard(
    summary: MonthlyMessSummary,
    mealCostRate: Int,
    onRateChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monthly_mess_summary_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        tint = BadgeGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "මාසික ආහාර සාරාංශය (Monthly Summary)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = DateTimeUtils.getCurrentMonthYear(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Counts Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MealCountBox(
                    label = "උදේ",
                    count = summary.breakfastCount,
                    icon = Icons.Default.WbSunny,
                    color = BadgeGold,
                    modifier = Modifier.weight(1f)
                )
                MealCountBox(
                    label = "දිවා",
                    count = summary.lunchCount,
                    icon = Icons.Default.LightMode,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
                MealCountBox(
                    label = "රාත්‍රී",
                    count = summary.dinnerCount,
                    icon = Icons.Default.Bedtime,
                    color = Color(0xFF6366F1),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Total & Estimated Mess Cost
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "මුළු ආහාර වේල් ගණන: ${summary.totalMeals}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "ආසන්න මෙස් ගාස්තුව (වේලකට රු. $mealCostRate බැගින්)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    val totalEst = summary.totalMeals * mealCostRate
                    Text(
                        text = "රු. $totalEst",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun MealCountBox(
    label: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$count",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MonthlyMealListItem(
    record: MessRecord,
    onViewPhoto: (path: String, title: String, compKb: Int, origKb: Int) -> Unit,
    onDelete: () -> Unit
) {
    val mealName = when (record.mealType) {
        "BREAKFAST" -> "උදේ ආහාරය"
        "LUNCH" -> "දිවා ආහාරය"
        "DINNER" -> "රාත්‍රී ආහාරය"
        else -> "මෙස් ආහාරය"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        if (record.isTaken) DutyActiveGreen.copy(alpha = 0.15f)
                        else DutyOffOrange.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (record.isTaken) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (record.isTaken) DutyActiveGreen else DutyOffOrange,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$mealName • ${record.date}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (record.mealNotes.isNotBlank()) {
                    Text(
                        text = record.mealNotes,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!record.photoPath.isNullOrBlank()) {
                IconButton(
                    onClick = {
                        onViewPhoto(
                            record.photoPath,
                            "$mealName (${record.date})",
                            record.compressedSizeKb,
                            record.compressedSizeKb * 4
                        )
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = "View Photo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun addDays(dateStr: String, days: Int): String {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = format.parse(dateStr) ?: Date()
        val calendar = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_YEAR, days)
        }
        format.format(calendar.time)
    } catch (e: Exception) {
        dateStr
    }
}
