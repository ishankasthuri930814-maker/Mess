package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.example.data.DutyRecord
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import com.example.util.DateTimeUtils
import java.io.File

@Composable
fun DayDutySummaryDialog(
    summary: DailyDutySummary,
    onDismiss: () -> Unit,
    onEditRecord: ((DutyRecord) -> Unit)? = null,
    onViewPhoto: (path: String, title: String, compKb: Int, origKb: Int) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(20.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Top Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(BadgeGold.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = BadgeGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "දිනපතා රාජකාරි සාරාංශය",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = summary.formattedDate,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                        }
                    }

                    // Total Duty Hours Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DutyActiveGreen.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = DutyActiveGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "එදින රාජකාරි කාලය",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = summary.totalHoursFormatted,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DutyActiveGreen
                                    )
                                }
                            }

                            if (summary.totalPhotosCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Photo,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "ඡායාරූප ${summary.totalPhotosCount}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 1. Primary On Duty Record
                    SectionTitle(title = "1. රාජකාරිය වාර්තා කිරීම (On Duty)", icon = Icons.Default.Security, color = DutyActiveGreen)
                    if (summary.onDutyRecord != null) {
                        SingleDutyDetailCard(
                            record = summary.onDutyRecord,
                            badgeColor = DutyActiveGreen,
                            onEditRecord = onEditRecord,
                            onViewPhoto = onViewPhoto
                        )
                    } else {
                        EmptyDutyCard(text = "මෙම දිනයේ On Duty සටහනක් තවම ඇතුළත් කර නැත")
                    }

                    // 2. Primary Off Duty Record
                    SectionTitle(title = "2. බැහැරවීම (Off Duty)", icon = Icons.Default.MenuBook, color = DutyOffOrange)
                    if (summary.offDutyRecord != null) {
                        SingleDutyDetailCard(
                            record = summary.offDutyRecord,
                            badgeColor = DutyOffOrange,
                            onEditRecord = onEditRecord,
                            onViewPhoto = onViewPhoto
                        )
                    } else {
                        EmptyDutyCard(text = "මෙම දිනයේ Off Duty සටහනක් ඇතුළත් කර නැත")
                    }

                    // 3. Extra Duties & Incidents
                    SectionTitle(
                        title = "3. අමතර රාජකාරි සහ සිද්ධි (${summary.extraDutyRecords.size})",
                        icon = Icons.Default.Shield,
                        color = Color(0xFF2563EB)
                    )
                    if (summary.extraDutyRecords.isNotEmpty()) {
                        summary.extraDutyRecords.forEachIndexed { index, extraRec ->
                            SingleDutyDetailCard(
                                record = extraRec,
                                badgeColor = Color(0xFF2563EB),
                                labelPrefix = "අමතර රාජකාරිය #${index + 1}: ",
                                onEditRecord = onEditRecord,
                                onViewPhoto = onViewPhoto
                            )
                        }
                    } else {
                        EmptyDutyCard(text = "මෙම දිනයේ අමතර රාජකාරි හෝ සිද්ධි සටහන් නැත")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("close_day_summary_dialog"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("වසා දමන්න (Close)", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyDutyCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
fun SingleDutyDetailCard(
    record: DutyRecord,
    badgeColor: Color,
    labelPrefix: String = "",
    onEditRecord: ((DutyRecord) -> Unit)? = null,
    onViewPhoto: (path: String, title: String, compKb: Int, origKb: Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Row: Nature & Time & Edit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$labelPrefix${record.dutyNature.ifBlank { "රාජකාරි සටහන" }}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = badgeColor,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DateTimeUtils.formatTime(record.timestamp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (onEditRecord != null) {
                        IconButton(
                            onClick = { onEditRecord(record) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit record",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // IB Page & Para
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "පිටුව (Page): ${record.ibPage}  •  ඡේදය (Para): ${record.ibParagraph}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (record.remarks.isNotBlank()) {
                Text(
                    text = "සටහන්: ${record.remarks}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Dual Photo Thumbnails if present
            if (!record.photoPath.isNullOrBlank() || !record.scenePhotoPath.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!record.photoPath.isNullOrBlank()) {
                        DutyPhotoThumbnail(
                            label = "IB පොතේ Photo",
                            photoPath = record.photoPath,
                            compKb = record.compressedSizeKb,
                            origKb = if (record.originalSizeKb > 0) record.originalSizeKb else record.compressedSizeKb * 4,
                            onViewPhoto = onViewPhoto,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!record.scenePhotoPath.isNullOrBlank()) {
                        DutyPhotoThumbnail(
                            label = "සිද්ධියේ/ස්ථානයේ Photo",
                            photoPath = record.scenePhotoPath,
                            compKb = record.compressedSizeKb,
                            origKb = if (record.originalSizeKb > 0) record.originalSizeKb else record.compressedSizeKb * 4,
                            onViewPhoto = onViewPhoto,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DutyPhotoThumbnail(
    label: String,
    photoPath: String,
    compKb: Int,
    origKb: Int,
    onViewPhoto: (path: String, title: String, compKb: Int, origKb: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable {
            onViewPhoto(photoPath, label, compKb, origKb)
        },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val file = File(photoPath)
            if (file.exists()) {
                Image(
                    painter = rememberAsyncImagePainter(file),
                    contentDescription = label,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(text = "${compKb} KB (Auto Compressed)", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
