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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.MessRecord
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import java.io.File

@Composable
fun DayMessSummaryDialog(
    summary: DailyMessSummary,
    mealCostRate: Int = 180,
    onDismiss: () -> Unit,
    onEditMeal: (mealType: String, existing: MessRecord?) -> Unit,
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
                    // Header
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
                                imageVector = Icons.Default.Restaurant,
                                contentDescription = null,
                                tint = BadgeGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "දිනපතා මෙස් ආහාර සාරාංශය",
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

                    // Total Meals & Cost Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "ලබාගත් මුළු ආහාර වේල්: ${summary.takenCount} / 3",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "දිනපතා මෙස් වියදම (රු. $mealCostRate x ${summary.takenCount})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }

                            Text(
                                text = "රු. ${summary.takenCount * mealCostRate}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // 1. Breakfast Detail
                    SingleMealSummaryCard(
                        title = "උදේ ආහාරය (Breakfast)",
                        mealType = "BREAKFAST",
                        icon = Icons.Default.WbSunny,
                        iconColor = BadgeGold,
                        record = summary.breakfast,
                        onEdit = { onEditMeal("BREAKFAST", summary.breakfast) },
                        onViewPhoto = onViewPhoto
                    )

                    // 2. Lunch Detail
                    SingleMealSummaryCard(
                        title = "දිවා ආහාරය (Lunch)",
                        mealType = "LUNCH",
                        icon = Icons.Default.LightMode,
                        iconColor = Color(0xFFF59E0B),
                        record = summary.lunch,
                        onEdit = { onEditMeal("LUNCH", summary.lunch) },
                        onViewPhoto = onViewPhoto
                    )

                    // 3. Dinner Detail
                    SingleMealSummaryCard(
                        title = "රාත්‍රී ආහාරය (Dinner)",
                        mealType = "DINNER",
                        icon = Icons.Default.Bedtime,
                        iconColor = Color(0xFF6366F1),
                        record = summary.dinner,
                        onEdit = { onEditMeal("DINNER", summary.dinner) },
                        onViewPhoto = onViewPhoto
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("close_day_mess_dialog"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("වසා දමන්න (Close)", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SingleMealSummaryCard(
    title: String,
    mealType: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    record: MessRecord?,
    onEdit: () -> Unit,
    onViewPhoto: (path: String, title: String, compKb: Int, origKb: Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (record?.isTaken == true) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(iconColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (record?.isTaken == true) DutyActiveGreen.copy(alpha = 0.15f) else DutyOffOrange.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (record?.isTaken == true) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (record?.isTaken == true) DutyActiveGreen else DutyOffOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (record?.isTaken == true) "ලබා ගත්තා" else "ලබා නොගත්තා",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (record?.isTaken == true) DutyActiveGreen else DutyOffOrange
                        )
                    }
                }
            }

            if (record != null && record.mealNotes.isNotBlank()) {
                Text(
                    text = "මෙනුව / සටහන්: ${record.mealNotes}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Photo thumbnail if present
            if (record != null && !record.photoPath.isNullOrBlank()) {
                val file = File(record.photoPath)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            onViewPhoto(record.photoPath, "$title Photo", record.compressedSizeKb, record.compressedSizeKb * 4)
                        }
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (file.exists()) {
                        Image(
                            painter = rememberAsyncImagePainter(file),
                            contentDescription = title,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "ආහාරයේ ඡායාරූපය", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "${record.compressedSizeKb} KB (Auto Compressed)", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Edit button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Meal", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
