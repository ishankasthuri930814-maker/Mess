package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.data.DutyRecord
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import com.example.util.DateTimeUtils
import java.io.File

import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.ArrowForward

@Composable
fun DutyScreen(
    viewModel: PoliceViewModel,
    onOpenDutyDialog: (type: String) -> Unit,
    onViewPhoto: (path: String, title: String, compKb: Int, origKb: Int) -> Unit
) {
    val dutyList by viewModel.filteredDutyRecords.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val dutyFilter by viewModel.dutyFilter.collectAsStateWithLifecycle()
    val todayDutyHours by viewModel.todayDutyHoursFormatted.collectAsStateWithLifecycle()
    val monthlyDutyHours by viewModel.monthlyDutyHoursFormatted.collectAsStateWithLifecycle()
    val dailyDutySummaries by viewModel.dailyDutySummaries.collectAsStateWithLifecycle()

    var recordToDelete by remember { mutableStateOf<DutyRecord?>(null) }
    var selectedDaySummary by remember { mutableStateOf<DailyDutySummary?>(null) }
    var showDailySummariesOnly by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onOpenDutyDialog("ON_DUTY") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_duty_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Duty Record")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("වාර්තා කරන්න", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Duty Hours Counter Card (Clickable to view today's daily summary)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val todaySummary = dailyDutySummaries.firstOrNull { it.dateKey == DateTimeUtils.getTodayKey() }
                        if (todaySummary != null) {
                            selectedDaySummary = todaySummary
                        } else if (dailyDutySummaries.isNotEmpty()) {
                            selectedDaySummary = dailyDutySummaries.first()
                        }
                    },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(DutyActiveGreen.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = DutyActiveGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "අද රාජකාරි කාලය (සාරාංශය ->)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = todayDutyHours,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DutyActiveGreen
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "මෙම මස එකතුව",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = monthlyDutyHours,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                label = { Text("සොයන්න (පිටුව, ඡේදය, රාජකාරිය)...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("duty_search_bar")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips (Scrollable including Daily Summary view toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !showDailySummariesOnly && dutyFilter == DutyFilter.ALL,
                    onClick = {
                        showDailySummariesOnly = false
                        viewModel.setDutyFilter(DutyFilter.ALL)
                    },
                    label = { Text("සියල්ල (${dutyList.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = showDailySummariesOnly,
                    onClick = { showDailySummariesOnly = !showDailySummariesOnly },
                    label = { Text("දිනපතා සාරාංශ (${dailyDutySummaries.size})") },
                    leadingIcon = {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BadgeGold,
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = !showDailySummariesOnly && dutyFilter == DutyFilter.ON_DUTY,
                    onClick = {
                        showDailySummariesOnly = false
                        viewModel.setDutyFilter(DutyFilter.ON_DUTY)
                    },
                    label = { Text("වාර්තා කිරීම් (On Duty)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DutyActiveGreen,
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = !showDailySummariesOnly && dutyFilter == DutyFilter.EXTRA_DUTY,
                    onClick = {
                        showDailySummariesOnly = false
                        viewModel.setDutyFilter(DutyFilter.EXTRA_DUTY)
                    },
                    label = { Text("අමතර රාජකාරි (Extra Duty)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2563EB),
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = !showDailySummariesOnly && dutyFilter == DutyFilter.OFF_DUTY,
                    onClick = {
                        showDailySummariesOnly = false
                        viewModel.setDutyFilter(DutyFilter.OFF_DUTY)
                    },
                    label = { Text("බැහැරවීම් (Off Duty)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DutyOffOrange,
                        selectedLabelColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Content Area (Daily Summaries View vs Individual Duty Records View)
            if (showDailySummariesOnly) {
                if (dailyDutySummaries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("දිනපතා සාරාංශ සටහන් කිසිවක් නැත", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        items(dailyDutySummaries, key = { it.dateKey }) { daySummary ->
                            DailyDutySummaryItemCard(
                                summary = daySummary,
                                onClick = { selectedDaySummary = daySummary }
                            )
                        }
                    }
                }
            } else {
                if (dutyList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "සෙවුමට ගැළපෙන සටහන් නැත" else "තවම රාජකාරි සටහන් කිසිවක් නැත",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        items(dutyList, key = { it.id }) { record ->
                            val recordDateKey = DateTimeUtils.formatDate(record.timestamp)
                            DutyItemCard(
                                record = record,
                                onViewPhoto = onViewPhoto,
                                onDelete = { recordToDelete = record },
                                onViewDaySummary = {
                                    val summary = dailyDutySummaries.firstOrNull { it.dateKey == recordDateKey }
                                    if (summary != null) selectedDaySummary = summary
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Day Duty Summary Dialog
    selectedDaySummary?.let { summary ->
        DayDutySummaryDialog(
            summary = summary,
            onDismiss = { selectedDaySummary = null },
            onViewPhoto = onViewPhoto
        )
    }

    // Delete confirmation dialog
    if (recordToDelete != null) {
        val record = recordToDelete!!
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("සටහන මකා දැමීම") },
            text = { Text("මෙම රාජකාරි සටහන (පිටුව: ${record.ibPage}) සදහටම ඉවත් කිරීමට ඔබට විශ්වාසද?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDutyRecord(record)
                        recordToDelete = null
                    }
                ) {
                    Text("මකන්න", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("අවලංගු කරන්න")
                }
            }
        )
    }
}

@Composable
fun DutyItemCard(
    record: DutyRecord,
    onViewPhoto: (path: String, title: String, compKb: Int, origKb: Int) -> Unit,
    onDelete: () -> Unit,
    onViewDaySummary: (() -> Unit)? = null
) {
    val isExtraDuty = record.type == "EXTRA_DUTY"
    val isOnDuty = record.type == "ON_DUTY"
    val badgeColor = when {
        isExtraDuty -> Color(0xFF2563EB)
        isOnDuty -> DutyActiveGreen
        else -> DutyOffOrange
    }
    val badgeIcon = when {
        isExtraDuty -> Icons.Default.Shield
        isOnDuty -> Icons.Default.Security
        else -> Icons.Default.MenuBook
    }
    val badgeText = when {
        isExtraDuty -> "අමතර රාජකාරිය (Extra Duty)"
        isOnDuty -> "වාර්තා කිරීම (On Duty)"
        else -> "බැහැරවීම (Off Duty)"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("duty_card_${record.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Type badge & Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = badgeColor.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = badgeText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DateTimeUtils.formatFullDateTime(record.timestamp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete record",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Duty Nature
            Text(
                text = record.dutyNature.ifBlank { "රාජකාරි සටහන" },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Information Book (IB) Page and Paragraph Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "තොරතුරු පොතේ පිටුව: ${record.ibPage}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (record.ibParagraph.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BadgeGold.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = BadgeGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ඡේදය: ${record.ibParagraph}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BadgeGold
                            )
                        }
                    }
                }

                if (record.dutyDurationMinutes > 0) {
                    val hrs = record.dutyDurationMinutes / 60
                    val mins = record.dutyDurationMinutes % 60
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DutyActiveGreen.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = DutyActiveGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "කාලය: ${hrs}h ${mins}m",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DutyActiveGreen
                            )
                        }
                    }
                }
            }

            if (record.remarks.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = record.remarks,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1. IB Photo preview section
            if (!record.photoPath.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            onViewPhoto(
                                record.photoPath,
                                "තොරතුරු පොතේ පිටුව: ${record.ibPage} (ඡේදය: ${record.ibParagraph})",
                                record.compressedSizeKb,
                                record.originalSizeKb
                            )
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = File(record.photoPath)),
                        contentDescription = "Duty Book Page Photo",
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "පොතේ පිටුවේ ඡායාරූපය (IB Photo)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Compress,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
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

            // 2. Incident / Scene Photo preview section
            if (!record.scenePhotoPath.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BadgeGold.copy(alpha = 0.12f))
                        .clickable {
                            onViewPhoto(
                                record.scenePhotoPath,
                                "සිද්ධිය / ස්ථානය: ${record.dutyNature}",
                                record.compressedSizeKb,
                                record.originalSizeKb
                            )
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = File(record.scenePhotoPath)),
                        contentDescription = "Incident / Scene Photo",
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "සිද්ධියේ / ස්ථානයේ ඡායාරූපය (Scene Photo)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = BadgeGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ස්ථානීය සාක්ෂි ඡායාරූපය",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "බලන්න >",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BadgeGold
                    )
                }
            }
        }
    }
}

@Composable
fun DailyDutySummaryItemCard(
    summary: DailyDutySummary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("daily_summary_card_${summary.dateKey}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            .background(BadgeGold.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = BadgeGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = summary.formattedDate,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "සටහන් ${summary.allRecords.size} ක් • ඡායාරූප ${summary.totalPhotosCount}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DutyActiveGreen.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = DutyActiveGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = summary.totalHoursFormatted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DutyActiveGreen
                        )
                    }
                }
            }

            // Badges for ON, OFF, and EXTRA duty counts
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (summary.onDutyRecord != null) {
                    Surface(shape = RoundedCornerShape(6.dp), color = DutyActiveGreen.copy(alpha = 0.15f)) {
                        Text("On Duty: ${DateTimeUtils.formatTime(summary.onDutyRecord.timestamp)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DutyActiveGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                if (summary.offDutyRecord != null) {
                    Surface(shape = RoundedCornerShape(6.dp), color = DutyOffOrange.copy(alpha = 0.15f)) {
                        Text("Off Duty: ${DateTimeUtils.formatTime(summary.offDutyRecord.timestamp)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DutyOffOrange, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                if (summary.extraDutyRecords.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF2563EB).copy(alpha = 0.15f)) {
                        Text("අමතර රාජකාරි ${summary.extraDutyRecords.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Text("විස්තර බලන්න >", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
