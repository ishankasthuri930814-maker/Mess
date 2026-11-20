package com.example.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import androidx.compose.material3.Checkbox
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.data.DutyRecord
import com.example.ui.theme.DutyOffOrange
import com.example.ui.theme.PoliceThemeColors
import com.example.util.DutySlotRules
import com.example.util.ReserveHolderInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DutyEntryDialog(
    initialType: String = "ON_DUTY",
    editingRecord: DutyRecord? = null,
    assignedDuty: com.example.data.DutyAssignment? = null,
    currentUserUid: String = "",
    activeSingleSlotHolders: Map<String, ReserveHolderInfo> = emptyMap(),
    onVerifyAdminPasscode: ((String) -> Boolean)? = null,
    onDismiss: () -> Unit,
    onSubmit: (
        type: String,
        timestamp: Long,
        page: String,
        paragraph: String,
        nature: String,
        remarks: String,
        photoUri: Uri?,
        scenePhotoUri: Uri?,
        dutyDurationMinutes: Long,
        isHandoverConfirmed: Boolean,
        onError: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val effectiveType = editingRecord?.type ?: initialType
    var dutyType by remember { mutableStateOf(effectiveType) }

    // Calendar & Date Time State
    val calendar = remember {
        Calendar.getInstance().apply {
            if (editingRecord != null) {
                timeInMillis = editingRecord.timestamp
            }
        }
    }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var selectedDateStr by remember { mutableStateOf(dateFormat.format(calendar.time)) }
    var selectedTimeStr by remember { mutableStateOf(timeFormat.format(calendar.time)) }

    var ibPage by remember { mutableStateOf(editingRecord?.ibPage ?: "") }
    var ibParagraph by remember { mutableStateOf(editingRecord?.ibParagraph ?: "") }

    val defaultNature = editingRecord?.dutyNature ?: if (assignedDuty != null && effectiveType == "ON_DUTY") {
        assignedDuty.dutyNature
    } else if (effectiveType == "ON_DUTY") "Station Reserve (උපසේවා)" else "General Off Duty"

    var dutyNature by remember { mutableStateOf(defaultNature) }
    var remarks by remember { mutableStateOf(editingRecord?.remarks ?: "") }
    var isHandoverConfirmed by remember { mutableStateOf(false) }

    var adminPasscodeInput by remember { mutableStateOf("") }
    var adminPasscodeError by remember { mutableStateOf<String?>(null) }

    // Shift duration in hours & minutes
    val initHours = editingRecord?.dutyDurationMinutes?.let { (it / 60).toInt() } ?: if (effectiveType == "OFF_DUTY") 8 else 0
    val initMins = editingRecord?.dutyDurationMinutes?.let { (it % 60).toInt() } ?: 0
    var durationHours by remember { mutableIntStateOf(initHours) }
    var durationMinutes by remember { mutableIntStateOf(initMins) }

    // 1. IB Page Photo
    val initIbUri = editingRecord?.photoPath?.takeIf { it.isNotBlank() }?.let { Uri.fromFile(File(it)) }
    var ibPhotoUri by remember { mutableStateOf<Uri?>(initIbUri) }
    var tempIbCameraUri by remember { mutableStateOf<Uri?>(null) }

    // 2. Incident / Scene Photo
    val initSceneUri = editingRecord?.scenePhotoPath?.takeIf { it.isNotBlank() }?.let { Uri.fromFile(File(it)) }
    var scenePhotoUri by remember { mutableStateOf<Uri?>(initSceneUri) }
    var tempSceneCameraUri by remember { mutableStateOf<Uri?>(null) }

    var isSubmitting by remember { mutableStateOf(false) }

    val activeReserveHolder = activeSingleSlotHolders[DutySlotRules.SLOT_RESERVE]
    val isCurrentUserUnrelievedReserve = editingRecord == null &&
        currentUserUid.isNotBlank() &&
        activeReserveHolder != null &&
        activeReserveHolder.officerUid == currentUserUid
    val selectedSlotCategory = DutySlotRules.getSingleSlotCategory(dutyNature)
    val conflictingSlotHolder = if (editingRecord == null && selectedSlotCategory != null) {
        activeSingleSlotHolders[selectedSlotCategory]?.takeIf { it.officerUid != currentUserUid }
    } else {
        null
    }
    val needsReserveHandover = editingRecord == null &&
        dutyType == "ON_DUTY" &&
        selectedSlotCategory == DutySlotRules.SLOT_RESERVE &&
        conflictingSlotHolder != null
    val isBlockedByReserveOffDuty = isCurrentUserUnrelievedReserve && dutyType == "OFF_DUTY"
    val isBlockedBySingleSlotConflict = conflictingSlotHolder != null &&
        (dutyType == "EXTRA_DUTY" || (dutyType == "ON_DUTY" && selectedSlotCategory != DutySlotRules.SLOT_RESERVE))
    val isBlockedByLeavingReserve = isCurrentUserUnrelievedReserve && dutyType == "ON_DUTY" && selectedSlotCategory != DutySlotRules.SLOT_RESERVE

    // DatePicker Dialog
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            selectedDateStr = dateFormat.format(calendar.time)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // TimePicker Dialog
    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            selectedTimeStr = timeFormat.format(calendar.time)
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        true
    )

    // IB Photo Pickers
    val ibGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> if (uri != null) ibPhotoUri = uri }

    val ibCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean -> if (success && tempIbCameraUri != null) ibPhotoUri = tempIbCameraUri }

    // Scene Photo Pickers
    val sceneGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> if (uri != null) scenePhotoUri = uri }

    val sceneCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean -> if (success && tempSceneCameraUri != null) scenePhotoUri = tempSceneCameraUri }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(context, "කැමරා අවසරය ලබාදෙන්න (Camera permission needed)", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCameraFor(isScene: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = createTempImageUri(context, if (isScene) "scene" else "ib")
            if (isScene) {
                tempSceneCameraUri = uri
                sceneCameraLauncher.launch(uri)
            } else {
                tempIbCameraUri = uri
                ibCameraLauncher.launch(uri)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Common duty quick selections based on type
    val dutySuggestions = when (dutyType) {
        "ON_DUTY" -> listOf(
            "Station Reserve (උපසේවා)",
            "Duty Officer (රාජකාරි භාර නිලධාරී)",
            "Charge Room Officer (චෝදනාගාර භාර)",
            "Day Shift (දිවා මුරය)",
            "Night Shift (රාත්‍රී මුරය)",
            "General Duty (සාමාන්‍ය රාජකාරිය)"
        )
        "EXTRA_DUTY" -> listOf(
            "Day Mobile (දිවා මුරසංචාරය)",
            "Night Mobile (රාත්‍රී මුරසංචාරය)",
            "Traffic Duty (රථවාහන)",
            "Court Duty (උසාවි රාජකාරි)",
            "VIP Security (විශේෂ ආරක්ෂක)",
            "Check Point (මාර්ග බාධක)",
            "Raid / Investigation (වැටලීම්)",
            "Special Incident (විශේෂ සිද්ධිය)",
            "Crime Scene (ස්ථාන පරීක්ෂාව)"
        )
        else -> listOf(
            "General Off Duty (සාමාන්‍ය බැහැරවීම)",
            "Rest Day (විවේක දිනය)",
            "Leave (නිවාඩු)",
            "Night Rest (රාත්‍රී විවේකය)"
        )
    }

    fun performSubmit() {
        if (ibPage.isBlank()) {
            Toast.makeText(context, "කරුණාකර තොරතුරු පොතේ පිටුව ඇතුළත් කරන්න (Enter IB Page)", Toast.LENGTH_SHORT).show()
            return
        }
        if (isBlockedByReserveOffDuty) {
            Toast.makeText(context, "වෙනත් නිලධාරියෙකු උපසේවා (Station Reserve) භාර ගන්නා තෙක් Off Duty විය නොහැක!", Toast.LENGTH_LONG).show()
            return
        }
        if (isBlockedByLeavingReserve) {
            Toast.makeText(context, "වෙනත් නිලධාරියෙකු උපසේවා භාර ගන්නා තෙක් වෙනත් රාජකාරියකට මාරු විය නොහැක!", Toast.LENGTH_LONG).show()
            return
        }
        if (isBlockedBySingleSlotConflict && conflictingSlotHolder != null) {
            Toast.makeText(context, "මෙම රාජකාරියේ දැනටමත් ${conflictingSlotHolder.officerName} සිටී!", Toast.LENGTH_LONG).show()
            return
        }
        if (needsReserveHandover && !isHandoverConfirmed) {
            Toast.makeText(context, "කරුණාකර හිටපු නිලධාරියාගෙන් උපසේවා රාජකාරිය භාරගැනීම ස්ථිර කරන්න!", Toast.LENGTH_LONG).show()
            return
        }

        // If assigned by Admin Branch and reporting on duty with a different duty nature, require 6-digit passcode
        if (assignedDuty != null && dutyType == "ON_DUTY") {
            val isDifferent = !dutyNature.trim().equals(assignedDuty.dutyNature.trim(), ignoreCase = true)
            if (isDifferent) {
                val isValidPin = onVerifyAdminPasscode?.invoke(adminPasscodeInput) == true
                if (!isValidPin) {
                    adminPasscodeError = "වලංගු නොවන පාලන අංශ රහස් අංකයකි! පාලන අංශයෙන් ලබාගත් අංක 6 ඇතුළත් කරන්න."
                    Toast.makeText(context, "පාලන අංශයේ අද දින රහස් අංකය නිවැරදිව ඇතුළත් කරන්න", Toast.LENGTH_LONG).show()
                    return
                }
            }
        }

        isSubmitting = true
        val finalTimestamp = calendar.timeInMillis
        val totalDurationMin = (durationHours * 60L) + durationMinutes
        onSubmit(
            dutyType,
            finalTimestamp,
            ibPage,
            ibParagraph,
            dutyNature,
            remarks,
            ibPhotoUri,
            scenePhotoUri,
            totalDurationMin,
            isHandoverConfirmed
        ) {
            isSubmitting = false
        }
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(18.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header with TOP SAVE BUTTON
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    color = when (dutyType) {
                                        "ON_DUTY" -> DutyActiveGreen.copy(alpha = 0.15f)
                                        "EXTRA_DUTY" -> Color(0xFF2563EB).copy(alpha = 0.15f)
                                        else -> DutyOffOrange.copy(alpha = 0.15f)
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (dutyType) {
                                    "ON_DUTY" -> Icons.Default.Security
                                    "EXTRA_DUTY" -> Icons.Default.Shield
                                    else -> Icons.AutoMirrored.Filled.MenuBook
                                },
                                contentDescription = null,
                                tint = when (dutyType) {
                                    "ON_DUTY" -> PoliceThemeColors.greenText
                                    "EXTRA_DUTY" -> PoliceThemeColors.blueText
                                    else -> PoliceThemeColors.orangeText
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (dutyType) {
                                    "ON_DUTY" -> "රාජකාරි වාර්තාව"
                                    "EXTRA_DUTY" -> "අමතර රාජකාරිය"
                                    else -> "බැහැරවීම"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (dutyType) {
                                    "ON_DUTY" -> "Main On Duty"
                                    "EXTRA_DUTY" -> "Extra Duty / Task"
                                    else -> "Off Duty"
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // PROMINENT TOP SAVE BUTTON (NEVER HIDDEN BY NAVIGATION BAR)
                        Button(
                            onClick = { performSubmit() },
                            enabled = !isSubmitting && ibPage.isNotBlank() && !isBlockedByReserveOffDuty && !isBlockedBySingleSlotConflict && !isBlockedByLeavingReserve && (!needsReserveHandover || isHandoverConfirmed),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (dutyType) {
                                    "ON_DUTY" -> DutyActiveGreen
                                    "EXTRA_DUTY" -> Color(0xFF2563EB)
                                    else -> DutyOffOrange
                                },
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF475569),
                                disabledContentColor = Color.White.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("top_save_duty_btn")
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("සුරකින්න", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onDismiss, enabled = !isSubmitting, modifier = Modifier.size(34.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                        }
                    }

                    // 3-Segment Duty Type Selector (On Duty / Extra Duty / Off Duty)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val items = listOf(
                            Triple("ON_DUTY", "වාර්තා කිරීම", DutyActiveGreen),
                            Triple("EXTRA_DUTY", "අමතර රාජකාරිය", Color(0xFF2563EB)),
                            Triple("OFF_DUTY", "බැහැරවීම", DutyOffOrange)
                        )
                        items.forEach { (type, label, color) ->
                            val isSelected = dutyType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) color else Color.Transparent)
                                    .clickable {
                                        dutyType = type
                                        isHandoverConfirmed = false
                                        dutyNature = when (type) {
                                            "ON_DUTY" -> "Station Reserve (උපසේවා)"
                                            "EXTRA_DUTY" -> "Day Mobile (දිවා මුරසංචාරය)"
                                            else -> "General Off Duty (සාමාන්‍ය බැහැරවීම)"
                                        }
                                        if (type == "OFF_DUTY" && durationHours == 0) durationHours = 8
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Date & Time Selectors (Compact)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { datePickerDialog.show() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PoliceThemeColors.navyText),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(selectedDateStr, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { timePickerDialog.show() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PoliceThemeColors.navyText),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(selectedTimeStr, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Shift Duration if Off Duty
                    if (dutyType == "OFF_DUTY") {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DutyOffOrange.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = PoliceThemeColors.orangeText, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "රාජකාරි කළ කාලය: $durationHours පැය $durationMinutes මිනි",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PoliceThemeColors.orangeText,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(6, 8, 12).forEach { h ->
                                        FilterChip(
                                            selected = durationHours == h && durationMinutes == 0,
                                            onClick = {
                                                durationHours = h
                                                durationMinutes = 0
                                            },
                                            label = { Text("${h}h", fontSize = 10.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Station Reserve Off-Duty Lock Warning Banner
                    if (isBlockedByReserveOffDuty || isBlockedByLeavingReserve) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "උපසේවා (Station Reserve) අවහිරතාවය: වෙනත් නිලධාරියෙකු පැමිණ Station Reserve රාජකාරිය භාර ගන්නා තෙක් ඔබට Off Duty වීමට හෝ වෙනත් රාජකාරියකට මාරු වීමට නොහැක.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    // IB Page and Paragraph Fields
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ibPage,
                            onValueChange = { ibPage = it },
                            label = { Text("පොතේ පිටුව (Page)", fontSize = 11.sp) },
                            placeholder = { Text("e.g. 142", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ib_page_input")
                        )

                        OutlinedTextField(
                            value = ibParagraph,
                            onValueChange = { ibParagraph = it },
                            label = { Text("ඡේදය (Para)", fontSize = 11.sp) },
                            placeholder = { Text("e.g. 05", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ib_paragraph_input")
                        )
                    }

                    // Duty Nature
                    OutlinedTextField(
                        value = dutyNature,
                        onValueChange = { dutyNature = it },
                        label = { Text("රාජකාරියේ ස්වභාවය (Duty Nature)", fontSize = 11.sp) },
                        placeholder = { Text("e.g. Station Reserve / Day Mobile", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("duty_nature_input")
                    )

                    // Suggestion Chips (Compact)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        dutySuggestions.forEach { suggestion ->
                            val label = suggestion.substringBefore(" (")
                            FilterChip(
                                selected = dutyNature.startsWith(label),
                                onClick = {
                                    dutyNature = suggestion
                                    isHandoverConfirmed = false
                                },
                                label = { Text(suggestion, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    // Single-slot Duty Conflict / Station Reserve Handover Confirmation Banner
                    if (needsReserveHandover && conflictingSlotHolder != null) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BadgeGold.copy(alpha = 0.16f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BadgeGold),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "උපසේවා (Station Reserve) රාජකාරි භාරගැනීම",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = PoliceThemeColors.goldText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "දැනට උපසේවා රාජකාරියේ සිටින්නේ: ${conflictingSlotHolder.rank.ifBlank { "Officer" }} ${conflictingSlotHolder.officerName}. ඔබ මෙම රාජකාරිය භාරගත් පසු ඔහුට Off Duty වීමට අවසර ලැබේ.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 15.sp
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isHandoverConfirmed = !isHandoverConfirmed }
                                        .padding(top = 6.dp)
                                ) {
                                    Checkbox(
                                        checked = isHandoverConfirmed,
                                        onCheckedChange = { isHandoverConfirmed = it }
                                    )
                                    Text(
                                        text = "හිටපු නිලධාරියාගෙන් උපසේවා රාජකාරිය භාරගනිමි (Confirm Handover)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else if (isBlockedBySingleSlotConflict && conflictingSlotHolder != null) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "එක් පොලිසියකට මෙම රාජකාරියට සිටිය හැක්කේ එක් නිලධාරියෙකු පමණි! දැනට [${conflictingSlotHolder.rank.ifBlank { "Officer" }} ${conflictingSlotHolder.officerName}] මෙම රාජකාරියේ නිරතව සිටී.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    // Assigned Duty Link & Passcode Protection
                    if (assignedDuty != null && dutyType == "ON_DUTY") {
                        val isDifferent = !dutyNature.trim().equals(assignedDuty.dutyNature.trim(), ignoreCase = true)
                        if (!isDifferent) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DutyActiveGreen.copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Done, contentDescription = null, tint = PoliceThemeColors.greenText, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "පාලන අංශය මගින් පවරන ලද රාජකාරිය (Assigned by Admin Branch)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = PoliceThemeColors.greenText
                                        )
                                        Text(
                                            text = "${assignedDuty.dutyNature} • මුරය: ${assignedDuty.shift}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        } else {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DutyOffOrange.copy(alpha = 0.14f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DutyOffOrange.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = PoliceThemeColors.orangeText, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "වෙනත් රාජකාරියක් වාර්තා කිරීම (Passcode Required)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = PoliceThemeColors.orangeText
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "පාලන අංශය විසින් ඔබට පවරා ඇත්තේ '${assignedDuty.dutyNature}' රාජකාරියයි. එය වෙනුවට වෙනත් රාජකාරියක් වාර්තා කරන්නේ නම් පාලන අංශයේ අද දින අංක 6 ක Passcode එක ඇතුළත් කළ යුතුය.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = adminPasscodeInput,
                                        onValueChange = {
                                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                                adminPasscodeInput = it
                                                adminPasscodeError = null
                                            }
                                        },
                                        label = { Text("පාලන අංශ රහස් අංකය (6-Digit PIN)", fontSize = 11.sp) },
                                        placeholder = { Text("123456", fontSize = 11.sp) },
                                        isError = adminPasscodeError != null,
                                        supportingText = if (adminPasscodeError != null) {
                                            { Text(adminPasscodeError ?: "", color = MaterialTheme.colorScheme.error, fontSize = 10.sp) }
                                        } else null,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("admin_passcode_duty_change_input")
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TextButton(
                                        onClick = {
                                            dutyNature = assignedDuty.dutyNature
                                            adminPasscodeInput = ""
                                            adminPasscodeError = null
                                        },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = "නැවත පවරා ඇති රාජකාරියට (${assignedDuty.dutyNature}) මාරු වන්න",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Remarks
                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { remarks = it },
                        label = { Text("අමතර සටහන් (Remarks / Details)", fontSize = 11.sp) },
                        placeholder = { Text("සිද්ධිය, ස්ථානය හෝ උපකරණ පිළිබඳ සටහන්...", fontSize = 11.sp) },
                        maxLines = 2,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("duty_remarks_input")
                    )

                    // --- PHOTOS SECTION: 1. IB BOOK PHOTO & 2. SCENE PHOTO ---
                    Text(
                        text = "ඡායාරූප සුරැකීම (Auto-Compressed Photos)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // 1. IB Book Photo Box
                    PhotoPickerRow(
                        label = "1. තොරතුරු පොතේ ඡායාරූපය (IB Photo)",
                        photoUri = ibPhotoUri,
                        onCameraClick = { launchCameraFor(isScene = false) },
                        onGalleryClick = {
                            ibGalleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemove = { ibPhotoUri = null }
                    )

                    // 2. Scene / Incident Photo Box (Requested by user)
                    PhotoPickerRow(
                        label = "2. සිද්ධියේ / ස්ථානයේ ඡායාරූපය (Scene Photo)",
                        photoUri = scenePhotoUri,
                        onCameraClick = { launchCameraFor(isScene = true) },
                        onGalleryClick = {
                            sceneGalleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemove = { scenePhotoUri = null }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action Buttons (BOTH TOP & BOTTOM FOR MAXIMUM CONVENIENCE)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text("අවලංගු කරන්න", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { performSubmit() },
                            enabled = !isSubmitting && ibPage.isNotBlank() && !isBlockedByReserveOffDuty && !isBlockedBySingleSlotConflict && !isBlockedByLeavingReserve && (!needsReserveHandover || isHandoverConfirmed),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (dutyType) {
                                    "ON_DUTY" -> DutyActiveGreen
                                    "EXTRA_DUTY" -> Color(0xFF2563EB)
                                    else -> DutyOffOrange
                                },
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF475569),
                                disabledContentColor = Color.White.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(44.dp)
                                .testTag("submit_duty_button")
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (dutyType) {
                                        "ON_DUTY" -> "වාර්තා කරන්න"
                                        "EXTRA_DUTY" -> "අමතර රාජකාරිය සුරකින්න"
                                        else -> "බැහැරවන්න"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // GENEROUS BOTTOM SPACER SO BUTTONS ARE NEVER CUT OFF BY OS NAVIGATION BAR
                    Spacer(modifier = Modifier.height(36.dp))
                }
            }
        }
    }
}

@Composable
fun PhotoPickerRow(
    label: String,
    photoUri: Uri?,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))

            if (photoUri != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = photoUri),
                        contentDescription = label,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "ඡායාරූපය තෝරා ඇත", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Compress, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = "Auto compress & save", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = PoliceThemeColors.errorText, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onCameraClick,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("කැමරාව", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onGalleryClick,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ගැලරිය", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun createTempImageUri(context: Context, prefix: String): Uri {
    val tempFile = File.createTempFile("duty_${prefix}_", ".jpg", context.cacheDir).apply {
        createNewFile()
        deleteOnExit()
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        tempFile
    )
}
