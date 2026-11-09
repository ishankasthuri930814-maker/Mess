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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Security
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
    onSubmit: (type: String, timestamp: Long, page: String, paragraph: String, nature: String, remarks: String, photoUri: Uri?) -> Unit
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
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
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

    // Common duty quick selections
    val dutySuggestions = if (dutyType == "ON_DUTY") {
        listOf(
            "Station Reserve (ස්ථාන රක්ෂිතය)",
            "Day Mobile (දිවා මුරසංචාරය)",
            "Night Mobile (රාත්‍රී මුරසංචාරය)",
            "Traffic Duty (රථවාහන)",
            "Court Duty (උසාවි රාජකාරි)",
            "VIP Security (විශේෂ ආරක්ෂක)",
            "Check Point (මාර්ග බාධක)",
            "Crime Duty (අපරාධ විමර්ශන)"
        )
    } else {
        listOf(
            "General Off Duty (සාමාන්‍ය බැහැරවීම)",
            "Rest Day (විවේක දිනය)",
            "Leave (නිවාඩු)",
            "Night Rest (රාත්‍රී විවේකය)"
        )
    }

    // Photo pickers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && cameraPhotoUri != null) {
            selectedPhotoUri = cameraPhotoUri
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val uri = createTempImageUri(context)
            cameraPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "කැමරා අවසරය අවශ්‍ය වේ (Camera permission required)", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = createTempImageUri(context)
            cameraPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (dutyType == "ON_DUTY") DutyActiveGreen.copy(alpha = 0.15f)
                                else DutyOffOrange.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (dutyType == "ON_DUTY") Icons.Default.Security else Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = if (dutyType == "ON_DUTY") DutyActiveGreen else DutyOffOrange
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (dutyType == "ON_DUTY") "රාජකාරි වාර්තා කිරීම" else "බැහැරවීම සටහන් කිරීම",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (dutyType == "ON_DUTY") "On Duty Report" else "Off Duty Report",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Type Toggle (On Duty / Off Duty)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isOn = dutyType == "ON_DUTY"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isOn) DutyActiveGreen else Color.Transparent)
                            .clickable {
                                dutyType = "ON_DUTY"
                                dutyNature = "Station Reserve"
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "වාර්තා කිරීම (On Duty)",
                            fontSize = 13.sp,
                            fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium,
                            color = if (isOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isOn) DutyOffOrange else Color.Transparent)
                            .clickable {
                                dutyType = "OFF_DUTY"
                                dutyNature = "Off Duty"
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "බැහැරවීම (Off Duty)",
                            fontSize = 13.sp,
                            fontWeight = if (!isOn) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date and Time selection fields (Explicitly requested by user)
                Text(
                    text = "දිනය සහ වේලාව (Date & Time)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Date Selector
                    OutlinedButton(
                        onClick = { datePickerDialog.show() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(selectedDateStr, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Time Selector
                    OutlinedButton(
                        onClick = { timePickerDialog.show() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(selectedTimeStr, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Information Book (IB) Page and Paragraph Fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = ibPage,
                        onValueChange = { ibPage = it },
                        label = { Text("පොතේ පිටුව (Page)") },
                        placeholder = { Text("e.g. 142") },
                        leadingIcon = {
                            Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ib_page_input")
                    )

                    OutlinedTextField(
                        value = ibParagraph,
                        onValueChange = { ibParagraph = it },
                        label = { Text("ඡේදය (Para)") },
                        placeholder = { Text("e.g. 05") },
                        leadingIcon = {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ib_paragraph_input")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Duty Nature
                OutlinedTextField(
                    value = dutyNature,
                    onValueChange = { dutyNature = it },
                    label = { Text("රාජකාරියේ ස්වභාවය (Duty Nature)") },
                    placeholder = { Text("e.g. Station Reserve / Day Mobile") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("duty_nature_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Suggestion chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dutySuggestions.forEach { suggestion ->
                        val label = suggestion.substringBefore(" (")
                        FilterChip(
                            selected = dutyNature.startsWith(label),
                            onClick = { dutyNature = label },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Remarks / Notes
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("අමතර සටහන් (Remarks / Handover)") },
                    placeholder = { Text("රාජකාරි උපකරණ, අවි ආයුධ හෝ සිදුවීම් සටහන්...") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("duty_remarks_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Photo Capture Section (Information Book Photo)
                Text(
                    text = "පොතේ පිටුවේ ඡායාරූපය (IB Page Photo)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (selectedPhotoUri != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(model = selectedPhotoUri),
                                contentDescription = "Captured IB Photo",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ඡායාරූපය තෝරාගෙන ඇත",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Compress,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "ස්වයංක්‍රීයව compress කර සුරැකේ",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = { selectedPhotoUri = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = Color.Red)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { launchCamera() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("camera_button")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("කැමරාව (Camera)", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gallery_button")
                        ) {
                            Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ගැලරිය (Gallery)", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions: Cancel & Submit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("අවලංගු කරන්න")
                    }

                    Button(
                        onClick = {
                            if (ibPage.isBlank()) {
                                Toast.makeText(context, "කරුණාකර තොරතුරු පොතේ පිටුව ඇතුළත් කරන්න", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSubmitting = true
                            val finalTimestamp = calendar.timeInMillis
                            onSubmit(dutyType, finalTimestamp, ibPage, ibParagraph, dutyNature, remarks, selectedPhotoUri)
                        },
                        enabled = !isSubmitting && ibPage.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (dutyType == "ON_DUTY") DutyActiveGreen else DutyOffOrange
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("submit_duty_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (dutyType == "ON_DUTY") "වාර්තා කරන්න" else "බැහැරවන්න")
                        }
                    }
                }
            }
        }
    }
}

private fun createTempImageUri(context: Context): Uri {
    val tempFile = File.createTempFile("duty_capture_", ".jpg", context.cacheDir).apply {
        createNewFile()
        deleteOnExit()
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        tempFile
    )
}
