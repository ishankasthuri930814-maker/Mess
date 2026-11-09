package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.BadgeGold
import com.example.util.DateTimeUtils
import com.example.util.ReportGenerator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportReportDialog(
    viewModel: PoliceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val officerProfile by viewModel.officerProfile.collectAsStateWithLifecycle()
    val allDutyRecords by viewModel.allDutyRecords.collectAsStateWithLifecycle()
    val allMessRecords by viewModel.allMessRecords.collectAsStateWithLifecycle()
    val allRunningChartRecords by viewModel.allRunningChartRecords.collectAsStateWithLifecycle()

    val bDefaultPrice by viewModel.breakfastDefaultPrice.collectAsStateWithLifecycle()
    val lDefaultPrice by viewModel.lunchDefaultPrice.collectAsStateWithLifecycle()
    val dDefaultPrice by viewModel.dinnerDefaultPrice.collectAsStateWithLifecycle()

    val todayStr = DateTimeUtils.getTodayKey()
    val monthStartStr = DateTimeUtils.getCurrentMonthKey() + "-01"

    var startDate by remember { mutableStateOf(monthStartStr) }
    var endDate by remember { mutableStateOf(todayStr) }

    // Quick presets
    val applyPreset = { preset: String ->
        val cal = Calendar.getInstance()
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        when (preset) {
            "THIS_MONTH" -> {
                startDate = monthStartStr
                endDate = todayStr
            }
            "LAST_7" -> {
                cal.add(Calendar.DAY_OF_YEAR, -6)
                startDate = format.format(cal.time)
                endDate = todayStr
            }
            "LAST_30" -> {
                cal.add(Calendar.DAY_OF_YEAR, -29)
                startDate = format.format(cal.time)
                endDate = todayStr
            }
            "ALL_TIME" -> {
                val minDutyDate = allDutyRecords.minOfOrNull { DateTimeUtils.formatDate(it.timestamp) } ?: "2026-01-01"
                val minMessDate = allMessRecords.minOfOrNull { it.date } ?: "2026-01-01"
                val minRunningDate = allRunningChartRecords.minOfOrNull { it.date } ?: "2026-01-01"
                val minDate = listOf(minDutyDate, minMessDate, minRunningDate).minOrNull() ?: "2026-01-01"
                startDate = minDate
                endDate = todayStr
            }
        }
    }

    // Filter data according to date range
    val filteredDuty = remember(startDate, endDate, allDutyRecords) {
        allDutyRecords.filter {
            val dateKey = DateTimeUtils.formatDate(it.timestamp)
            dateKey in startDate..endDate
        }
    }

    val filteredMess = remember(startDate, endDate, allMessRecords) {
        allMessRecords.filter { it.date in startDate..endDate }
    }

    val filteredRunning = remember(startDate, endDate, allRunningChartRecords) {
        allRunningChartRecords.filter { it.date in startDate..endDate }
    }

    val reportData = remember(startDate, endDate, officerProfile, filteredDuty, filteredMess, filteredRunning, bDefaultPrice, lDefaultPrice, dDefaultPrice) {
        ReportGenerator.ReportData(
            startDate = startDate,
            endDate = endDate,
            profile = officerProfile,
            dutyRecords = filteredDuty,
            messRecords = filteredMess,
            runningChartRecords = filteredRunning,
            bDefaultPrice = bDefaultPrice,
            lDefaultPrice = lDefaultPrice,
            dDefaultPrice = dDefaultPrice
        )
    }

    // Preview Stats
    val totalDutyMins = filteredDuty.sumOf { it.dutyDurationMinutes }
    val dutyHours = totalDutyMins / 60
    val dutyMins = totalDutyMins % 60

    val takenMealsCount = filteredMess.count { it.isTaken }
    val totalMessCost = filteredMess.filter { it.isTaken }.sumOf { r ->
        if (r.price > 0.0) r.price else when (r.mealType) {
            "BREAKFAST" -> bDefaultPrice
            "LUNCH" -> lDefaultPrice
            "DINNER" -> dDefaultPrice
            else -> 180.0
        }
    }
    val totalDistance = filteredRunning.sumOf { it.distanceKm }

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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = BadgeGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "වාර්තා සාරාංශය ලබා ගැනීම",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "PDF හෝ WhatsApp සාරාංශ වාර්තාව ලබා ගන්න",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                        }
                    }

                    // Date Inputs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = startDate,
                            onValueChange = { startDate = it },
                            label = { Text("ආරම්භක දිනය (From)") },
                            placeholder = { Text("YYYY-MM-DD") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("report_start_date_input")
                        )

                        OutlinedTextField(
                            value = endDate,
                            onValueChange = { endDate = it },
                            label = { Text("අවසාන දිනය (To)") },
                            placeholder = { Text("YYYY-MM-DD") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("report_end_date_input")
                        )
                    }

                    // Preset Chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = { applyPreset("THIS_MONTH") },
                            label = { Text("මෙම මාසය", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = { applyPreset("LAST_7") },
                            label = { Text("පසුගිය දින 7", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = { applyPreset("LAST_30") },
                            label = { Text("පසුගිය දින 30", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = { applyPreset("ALL_TIME") },
                            label = { Text("සමස්තය (All Time)", fontSize = 11.sp) }
                        )
                    }

                    // Data Preview Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "තෝරාගත් කාලසීමාවේ සාරාංශය ($startDate -> $endDate)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("රාජකාරි කාලය:", fontSize = 12.sp)
                                }
                                Text("${dutyHours}h ${dutyMins}m (${filteredDuty.size} සටහන්)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = BadgeGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("මෙස් වේල් / වියදම:", fontSize = 12.sp)
                                }
                                Text("$takenMealsCount වේල් (රු. ${"%.2f".format(totalMessCost)})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("වාහන ධාවනය:", fontSize = 12.sp)
                                }
                                Text("${"%.1f".format(totalDistance)} km", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Action 1: Create & View PDF
                    Button(
                        onClick = {
                            try {
                                val pdfFile = ReportGenerator.createPdfReport(context, reportData)
                                ReportGenerator.openPdfViewer(context, pdfFile)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "PDF සාදා ගැනීමට නොහැකි විය: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_pdf_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PDF වාර්තාව සාදා බලන්න (View PDF)", fontWeight = FontWeight.Bold)
                    }

                    // Action 2: Share PDF File
                    OutlinedButton(
                        onClick = {
                            try {
                                val pdfFile = ReportGenerator.createPdfReport(context, reportData)
                                ReportGenerator.sharePdfReport(context, pdfFile, "Police Report ($startDate to $endDate)")
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "PDF බෙදාගැනීමට නොහැකි විය", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_pdf_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PDF ගොනුව බෙදාගන්න (Share PDF File)")
                    }

                    // Action 3: Share WhatsApp Text Summary
                    Button(
                        onClick = {
                            val textSummary = ReportGenerator.generateTextSummary(reportData)
                            ReportGenerator.shareWhatsAppText(context, textSummary)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("whatsapp_share_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)) // WhatsApp Green
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("WhatsApp මගින් යවන්න (Share on WhatsApp)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
