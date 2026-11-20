package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.data.TeamDutyMember
import com.example.data.TeamDutySession
import com.example.ui.theme.*
import com.example.util.DateTimeUtils
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamDutyScreen(
    viewModel: PoliceViewModel,
    onViewPhoto: (path: String, title: String, compKb: Int, origKb: Int) -> Unit
) {
    val context = LocalContext.current
    val appUser by viewModel.appUser.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeTeamDutySession.collectAsStateWithLifecycle()
    val activeMembers by viewModel.activeTeamDutyMembers.collectAsStateWithLifecycle()
    val allDuties by viewModel.allDutyRecords.collectAsStateWithLifecycle()

    val myUid = appUser?.uid ?: ""
    val isLeader = activeSession?.leaderUid == myUid

    // Start new Team Duty form state
    var dutyNature by remember { mutableStateOf("Mobile Patrol (මුරසංචාරය)") }
    var ibPage by remember { mutableStateOf("") }
    var ibParagraph by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var isStartingSession by remember { mutableStateOf(false) }

    // Manual join code + scanner state
    var manualJoinCode by remember { mutableStateOf("") }
    var isJoiningSession by remember { mutableStateOf(false) }
    var isScannerOpen by remember { mutableStateOf(false) }
    var isEnlargedQrOpen by remember { mutableStateOf(false) }
    var isEndConfirmOpen by remember { mutableStateOf(false) }

    // Incident / Special Note + Local Photo capture state
    var noteTitle by remember { mutableStateOf("") }
    var noteDetails by remember { mutableStateOf("") }
    var incidentPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isSavingIncident by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) incidentPhotoUri = uri }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success && tempCameraUri != null) incidentPhotoUri = tempCameraUri }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createTeamTempImageUri(context)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "ඡායාරූප ගැනීමට කැමරා අවසරය අවශ්‍ය වේ.", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchIncidentCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = createTeamTempImageUri(context)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun dialPhone(phone: String) {
        val clean = phone.replace(" ", "").replace("-", "")
        if (clean.isBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
        } catch (_: Exception) {
            Toast.makeText(context, "ඇමතුම් ලබාගත නොහැක", Toast.LENGTH_SHORT).show()
        }
    }

    val teamSpecialNotes = remember(allDuties) {
        allDuties.filter {
            it.dutyNature.startsWith("[කණ්ඩායම්") ||
                it.dutyNature.startsWith("[විශේෂ") ||
                !it.scenePhotoPath.isNullOrBlank()
        }
    }

    val dutySuggestions = listOf(
        "Mobile Patrol (මුරසංචාරය)",
        "Check Point (මාර්ග බාධක)",
        "Raid / Investigation (වැටලීම්)",
        "VIP Security (විශේෂ ආරක්ෂක)",
        "Traffic Duty (රථවාහන)",
        "Special Escort (විශේෂ පෙරගමන්)"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        // 1. Hero Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = PoliceNavyPrimary)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PoliceNavyDark, PoliceNavyPrimary, PoliceSlate)
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = BadgeGold.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = BadgeGoldBright, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("QR GROUP DUTY SYNC", color = BadgeGoldBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "කණ්ඩායම් රාජකාරි සහ QR සබඳතාව",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "නිලධාරීන් කිහිප දෙනෙකු එකම රාජකාරියේ යෙදෙන විට එක් අයෙකුගේ QR කේතය අනෙක් අය Scan කිරීමෙන් සියල්ල එකම රාජකාරියට එක්වේ. ප්‍රධාන නිලධාරියා ඉවත් වූ විට සැමදෙනාම ස්වයංක්‍රීයව ඉවත් වේ.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        val currentSession = activeSession
        if (currentSession != null && currentSession.status == "ACTIVE") {
            // 2A. Active Team Duty Dashboard + QR Code + Members List
            item {
                ActiveTeamDutyCard(
                    session = currentSession,
                    members = activeMembers,
                    isLeader = isLeader,
                    onEnlargeQr = { isEnlargedQrOpen = true },
                    onCopyCode = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Team Code", currentSession.joinCode))
                        Toast.makeText(context, "කේතය (${currentSession.joinCode}) කොපි විය!", Toast.LENGTH_SHORT).show()
                    },
                    onDialMember = { dialPhone(it) },
                    onEndAsLeader = { isEndConfirmOpen = true },
                    onLeaveAsMember = { viewModel.leaveTeamDutyAsMember() }
                )
            }
        } else {
            // 2B. Join via QR Scanner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.5.dp, DutyActiveGreen.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(DutyActiveGreen.copy(alpha = 0.14f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = PoliceThemeColors.greenText)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "කණ්ඩායම් රාජකාරියකට එක්වන්න",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ප්‍රධාන නිලධාරියාගේ QR කේතය Scan කරන්න",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { isScannerOpen = true },
                            enabled = !isJoiningSession,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("scan_team_qr_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DutyActiveGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("QR කේතය ස්කෑන් කරන්න (Scan QR)", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualJoinCode,
                                onValueChange = { manualJoinCode = it.uppercase() },
                                label = { Text("කේතය (Join Code)") },
                                placeholder = { Text("උදා: A7B9K2") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    isJoiningSession = true
                                    viewModel.joinTeamDuty(
                                        rawQrOrCode = manualJoinCode,
                                        onSuccess = {
                                            isJoiningSession = false
                                            manualJoinCode = ""
                                        },
                                        onError = { isJoiningSession = false }
                                    )
                                },
                                enabled = manualJoinCode.isNotBlank() && !isJoiningSession,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Text("එක්වන්න", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2C. Start New Group Duty (Leader creates QR)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(PoliceThemeColors.navyText.copy(alpha = 0.14f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Groups, contentDescription = null, tint = PoliceThemeColors.navyText)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "නව කණ්ඩායම් රාජකාරියක් අරඹන්න",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "අනෙක් නිලධාරීන්ට Scan කිරීමට QR කේතයක් සෑදේ",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            dutySuggestions.forEach { label ->
                                FilterChip(
                                    selected = dutyNature == label,
                                    onClick = { dutyNature = label },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = dutyNature,
                            onValueChange = { dutyNature = it },
                            label = { Text("රාජකාරියේ ස්වභාවය (Duty Nature)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = ibPage,
                                onValueChange = { ibPage = it },
                                label = { Text("IB පිටුව") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = ibParagraph,
                                onValueChange = { ibParagraph = it },
                                label = { Text("ඡේදය") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = remarks,
                            onValueChange = { remarks = it },
                            label = { Text("අමතර සටහන් (Remarks)") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                isStartingSession = true
                                viewModel.startTeamDuty(
                                    dutyNature = dutyNature,
                                    ibPage = ibPage,
                                    ibParagraph = ibParagraph,
                                    remarks = remarks,
                                    onSuccess = { isStartingSession = false },
                                    onError = { isStartingSession = false }
                                )
                            },
                            enabled = dutyNature.isNotBlank() && !isStartingSession,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("start_team_duty_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PoliceNavyPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isStartingSession) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("කණ්ඩායම් රාජකාරිය අරඹා QR සාදන්න", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 3. Special Notes & Incident Local Photo Storage Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(BadgeGold.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = PoliceThemeColors.navyText)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "විශේෂ සටහන් සහ සිද්ධි ඡායාරූප",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "රාජකාරියේ විශේෂ සිද්ධි ඡායාරූපගත කර Local Storage හි සුරකින්න",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("සිද්ධිය / විශේෂ සටහන (Incident / Title)") },
                        placeholder = { Text("උදා: රථවාහන අනතුර / සැක කටයුතු පරීක්ෂාව") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = noteDetails,
                        onValueChange = { noteDetails = it },
                        label = { Text("විස්තරය (Details & Observations)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (incidentPhotoUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(incidentPhotoUri),
                                contentDescription = "Incident preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { incidentPhotoUri = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { launchIncidentCamera() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("කැමරාවෙන් ගන්න", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ගැලරියෙන් තෝරන්න", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            isSavingIncident = true
                            viewModel.saveTeamIncidentPhotoNote(
                                context = context,
                                noteTitle = noteTitle,
                                noteDetails = noteDetails,
                                photoUri = incidentPhotoUri,
                                onSuccess = {
                                    isSavingIncident = false
                                    noteTitle = ""
                                    noteDetails = ""
                                    incidentPhotoUri = null
                                },
                                onError = { isSavingIncident = false }
                            )
                        },
                        enabled = (noteTitle.isNotBlank() || noteDetails.isNotBlank() || incidentPhotoUri != null) && !isSavingIncident,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_team_incident_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DutyActiveGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSavingIncident) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Local Storage හි සුරකින්න (Save Note & Photo)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4. Saved Special Notes & Local Photos List
        item {
            Text(
                text = "සුරැකි විශේෂ සටහන් සහ ඡායාරූප (${teamSpecialNotes.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (teamSpecialNotes.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "තවමත් කණ්ඩායම් හෝ විශේෂ සිද්ධි සටහන් සුරැකී නොමැත.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(teamSpecialNotes.take(20)) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = record.dutyNature,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${DateTimeUtils.formatDate(record.timestamp)} • ${DateTimeUtils.formatTime(record.timestamp)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (record.remarks.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = record.remarks,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val path = record.scenePhotoPath
                        if (!path.isNullOrBlank() && File(path).exists()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Image(
                                painter = rememberAsyncImagePainter(File(path)),
                                contentDescription = "Incident local photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onViewPhoto(path, record.dutyNature, record.compressedSizeKb, record.originalSizeKb)
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    if (isScannerOpen) {
        QrScannerDialog(
            onScanned = { qr ->
                isScannerOpen = false
                isJoiningSession = true
                viewModel.joinTeamDuty(
                    rawQrOrCode = qr,
                    onSuccess = { isJoiningSession = false },
                    onError = { isJoiningSession = false }
                )
            },
            onDismiss = { isScannerOpen = false }
        )
    }

    if (isEnlargedQrOpen && activeSession != null) {
        val sess = activeSession!!
        val payload = "POLICE_TEAM_DUTY:${sess.sessionId}:${sess.joinCode}"
        val qrBitmap = remember(payload) { QrCodeGenerator.generateQrBitmap(payload, 640) }
        Dialog(onDismissRequest = { isEnlargedQrOpen = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("කණ්ඩායම් QR කේතය", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = PoliceNavyDark)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Join Code: ${sess.joinCode}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = DutyActiveGreen)
                    Spacer(modifier = Modifier.height(12.dp))
                    qrBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Team Duty QR Code",
                            modifier = Modifier.size(250.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { isEnlargedQrOpen = false }) {
                        Text("වසන්න")
                    }
                }
            }
        }
    }

    if (isEndConfirmOpen) {
        AlertDialog(
            onDismissRequest = { isEndConfirmOpen = false },
            title = { Text("කණ්ඩායම් රාජකාරිය අවසන් කරන්නද?", fontWeight = FontWeight.Bold) },
            text = {
                Text("ඔබ මෙම කණ්ඩායම් රාජකාරිය අවසන් කළ විට, QR කේතය මගින් සම්බන්ධ වී සිටින අනෙක් සියලුම නිලධාරීන්ද (සාමාජිකයන් ${activeMembers.size} දෙනෙකු) ස්වයංක්‍රීයව රාජකාරියෙන් ඉවත් (OFF DUTY) වනු ඇත.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        isEndConfirmOpen = false
                        viewModel.endTeamDutyAsLeader()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("ඔව්, අවසන් කරන්න")
                }
            },
            dismissButton = {
                TextButton(onClick = { isEndConfirmOpen = false }) {
                    Text("රැඳී සිටින්න")
                }
            }
        )
    }
}

@Composable
private fun ActiveTeamDutyCard(
    session: TeamDutySession,
    members: List<TeamDutyMember>,
    isLeader: Boolean,
    onEnlargeQr: () -> Unit,
    onCopyCode: () -> Unit,
    onDialMember: (String) -> Unit,
    onEndAsLeader: () -> Unit,
    onLeaveAsMember: () -> Unit
) {
    val qrPayload = "POLICE_TEAM_DUTY:${session.sessionId}:${session.joinCode}"
    val qrBitmap = remember(qrPayload) { QrCodeGenerator.generateQrBitmap(qrPayload, 420) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.5.dp, DutyActiveGreen)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(10.dp), color = DutyActiveGreen) {
                    Text(
                        text = "● සක්‍රීය කණ්ඩායම් රාජකාරිය",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BadgeGold.copy(alpha = 0.25f),
                    modifier = Modifier.clickable { onCopyCode() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = session.joinCode,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = PoliceThemeColors.goldText
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", modifier = Modifier.size(14.dp), tint = PoliceThemeColors.goldText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(text = session.dutyNature, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "ප්‍රධාන නිලධාරී: ${session.leaderName} • ආරම්භය: ${DateTimeUtils.formatTime(session.startedAtMs)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // QR Preview Box
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEnlargeQr() }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    qrBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Team Duty QR Code",
                            modifier = Modifier.size(96.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "සහෝදර නිලධාරීන් එක්කර ගන්න",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PoliceNavyDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "මෙම QR කේතය Scan කිරීමට දෙන්න (විශාල කිරීමට ඔබන්න) හෝ Join Code (${session.joinCode}) ලබා දෙන්න.",
                            fontSize = 11.sp,
                            color = Color.DarkGray,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val activeList = members.filter { it.status == "ACTIVE" }
            Text(
                text = "එකම රාජකාරියේ සිටින නිලධාරීන් (${activeList.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            activeList.forEach { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (member.roleInTeam == "LEADER") Icons.Default.Stars else Icons.Default.Person,
                        contentDescription = null,
                        tint = if (member.roleInTeam == "LEADER") PoliceThemeColors.goldText else PoliceThemeColors.navyText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = member.displayName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${member.rank.ifBlank { "Officer" }} • ${member.badgeNumber.ifBlank { "N/A" }} • ${if (member.roleInTeam == "LEADER") "ප්‍රධාන" else "සාමාජික"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (member.phoneNumber.isNotBlank()) {
                        IconButton(
                            onClick = { onDialMember(member.phoneNumber) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Call Member", tint = PoliceThemeColors.greenText)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLeader) {
                Button(
                    onClick = onEndAsLeader,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("end_team_duty_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("කණ්ඩායම් රාජකාරිය අවසන් කරන්න (සැම ඉවත් වේ)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PoliceThemeColors.navyText.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ප්‍රධාන නිලධාරී ${session.leaderName} රාජකාරිය අවසන් කළ විට ඔබද ස්වයංක්‍රීයව ඉවත් වනු ඇත.",
                        fontSize = 11.sp,
                        color = PoliceThemeColors.navyText,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onLeaveAsMember,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("මම පමණක් ඉවත් වන්න (Leave Team Duty)")
                }
            }
        }
    }
}

private fun createTeamTempImageUri(context: Context): Uri {
    val tempFile = File.createTempFile("team_inc_", ".jpg", context.cacheDir).apply {
        createNewFile()
        deleteOnExit()
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        tempFile
    )
}
