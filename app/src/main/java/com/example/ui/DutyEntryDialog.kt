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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
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
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DutyEntryDialog(
    initialType: String = "ON_DUTY",
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
        dutyDurationMinutes: Long
    ) -> Unit
) {
    val context = LocalContext.current
    var dutyType by remember { mutableStateOf(initialType) }

    // Calendar & Date Time State
    val calendar = remember { Calendar.getInstance() }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var selectedDateStr by remember { mutableStateOf(dateFormat.format(calendar.time)) }
    var selectedTimeStr by remember { mutableStateOf(timeFormat.format(calendar.time)) }

    var ibPage by remember { mutableStateOf("") }
    var ibParagraph by remember { mutableStateOf("") }
    var dutyNature by remember { mutableStateOf(if (initialType == "ON_DUTY") "Station Reserve" else "Off Duty") }
    var remarks by remember { mutableStateOf("") }

    // Shift duration in hours & minutes (e.g. 8h 30m)
    var durationHours by remember { mutableIntStateOf(if (initialType == "OFF_DUTY") 8 else 0) }
    var durationMinutes by remember { mutableIntStateOf(0) }

    // 1. IB Page Photo
    var ibPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var tempIbCameraUri by remember { mutableStateOf<Uri?>(null) }

    // 2. Incident / Scene Photo
    var scenePhotoUri by remember { mutableStateOf<Uri?>(null) }
    var tempSceneCameraUri by remember { mutableStateOf<Uri?>(null) }

    var isSubmitting by remember { mutableStateOf(false) }

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
            "Station Reserve (ස්ථාන රක්ෂිතය)",
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

    val performSubmit: () -> Unit = {
        if (ibPage.isBlank()) {
            Toast.makeText(context, "කරුණාකර තොරතුරු පොතේ පිටුව ඇතුළත් කරන්න (Enter IB Page)", Toast.LENGTH_SHORT).show()
        } else {
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
                totalDurationMin
            )
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
                                    else -> Icons.Default.MenuBook
                                },
                                contentDescription = null,
                                tint = when (dutyType) {
                                    "ON_DUTY" -> DutyActiveGreen
                                    "EXTRA_DUTY" -> Color(0xFF2563EB)
                                    else -> DutyOffOrange
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
                            enabled = !isSubmitting && ibPage.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (dutyType) {
                                    "ON_DUTY" -> DutyActiveGreen
                                    "EXTRA_DUTY" -> Color(0xFF2563EB)
                                    else -> DutyOffOrange
                                }
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
                                        dutyNature = when (type) {
                                            "ON_DUTY" -> "Station Reserve"
                                            "EXTRA_DUTY" -> "Day Mobile"
                                            else -> "General Off Duty"
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
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(selectedDateStr, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { timePickerDialog.show() },
                            shape = RoundedCornerShape(10.dp),
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
                            color = DutyOffOrange.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = DutyOffOrange, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "රාජකාරි කළ කාලය: $durationHours පැය $durationMinutes මිනි",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DutyOffOrange,
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
                                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
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
                                onClick = { dutyNature = label },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
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
                            enabled = !isSubmitting && ibPage.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (dutyType) {
                                    "ON_DUTY" -> DutyActiveGreen
                                    "EXTRA_DUTY" -> Color(0xFF2563EB)
                                    else -> DutyOffOrange
                                }
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
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(16.dp))
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
