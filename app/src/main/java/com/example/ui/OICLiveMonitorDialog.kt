package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppUser
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange

@Composable
fun OICLiveMonitorDialog(
    viewModel: PoliceViewModel
) {
    val session by viewModel.oicLiveMonitorSession.collectAsStateWithLifecycle()
    val targetOfficer by viewModel.oicLiveMonitorTarget.collectAsStateWithLifecycle()
    val currentFrame by viewModel.oicLiveMonitorFrame.collectAsStateWithLifecycle()
    val isMuted by viewModel.isOicMonitorMuted.collectAsStateWithLifecycle()
    val monitorMode by viewModel.oicMonitorMode.collectAsStateWithLifecycle()
    val liveError by viewModel.oicLiveError.collectAsStateWithLifecycle()
    val audioLevel by viewModel.oicLiveAudioLevel.collectAsStateWithLifecycle()
    val subordinates by viewModel.subordinates.collectAsStateWithLifecycle()
    val appUser by viewModel.appUser.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val stationMembers = remember(subordinates, targetOfficer) {
        subordinates
            .filter { it.status == "APPROVED" && !it.hiddenFromStationLists }
            .sortedWith(
                compareByDescending<AppUser> { it.uid == targetOfficer?.uid }
                    .thenByDescending { it.isOnDuty && !it.hideDutyStatus && (it.hasCameraPermission || it.hasMicPermission) }
                    .thenByDescending { it.isOnDuty && !it.hideDutyStatus }
                    .thenBy { it.displayName.lowercase() }
            )
    }

    val readyOnDutyMembers = remember(stationMembers) {
        stationMembers.filter {
            it.isOnDuty && !it.hideDutyStatus && (it.hasCameraPermission || it.hasMicPermission)
        }
    }

    val filteredMembers = remember(stationMembers, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) {
            stationMembers
        } else {
            stationMembers.filter {
                it.displayName.lowercase().contains(q) ||
                    it.badgeNumber.lowercase().contains(q) ||
                    it.rank.lowercase().contains(q)
            }
        }
    }

    val activeSession = session

    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val liveDotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    BackHandler {
        viewModel.stopOicLiveMonitor()
    }

    Dialog(
        onDismissRequest = { viewModel.stopOicLiveMonitor() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .testTag("oic_live_monitor_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A101D)),
            border = BorderStroke(1.dp, BadgeGold.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // 1. Player Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (activeSession?.status == "ACTIVE") Color(0xFFDC2626).copy(alpha = 0.24f)
                                else BadgeGold.copy(alpha = 0.18f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = if (activeSession?.status == "ACTIVE") Color(0xFFF87171) else BadgeGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "OIC සජීවී කැමරා / ශ්‍රව්‍ය Player",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val stationLabel = appUser?.stationName?.takeIf { it.isNotBlank() }
                            ?: appUser?.oicStationCode
                            ?: "ස්ථානය"
                        Text(
                            text = "$stationLabel • සූදානම්: ${readyOnDutyMembers.size}/${stationMembers.size}",
                            color = BadgeGold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { isSearchExpanded = !isSearchExpanded },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("oic_live_search_toggle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "නිලධාරියෙකු සොයන්න",
                            tint = if (isSearchExpanded) BadgeGold else Color.White
                        )
                    }
                    IconButton(
                        onClick = { viewModel.stopOicLiveMonitor() },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("oic_live_close_dialog_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "වසන්න",
                            tint = Color.White
                        )
                    }
                }

                AnimatedVisibility(visible = isSearchExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "නම, නිලය හෝ අංකය අනුව සොයන්න...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = BadgeGold, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BadgeGold,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF11192B),
                                unfocusedContainerColor = Color(0xFF11192B)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("oic_live_search_input")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Horizontal Individual Officer Switcher Strip (Switch between officers anytime)
                if (filteredMembers.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("oic_live_officer_strip"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(filteredMembers, key = { it.uid }) { officer ->
                            val isSelected = targetOfficer?.uid == officer.uid
                            val isStreamingThis = isSelected && activeSession != null && activeSession.status != "ENDED"
                            val isOnDutyReady = officer.isOnDuty && !officer.hideDutyStatus &&
                                (officer.hasCameraPermission || officer.hasMicPermission)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isStreamingThis -> Color(0xFFDC2626).copy(alpha = 0.24f)
                                    isSelected -> BadgeGold.copy(alpha = 0.16f)
                                    isOnDutyReady -> Color(0xFF132038)
                                    else -> Color(0xFF111827).copy(alpha = 0.72f)
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = when {
                                        isStreamingThis -> Color(0xFFEF4444)
                                        isSelected -> BadgeGold
                                        isOnDutyReady -> DutyActiveGreen.copy(alpha = 0.45f)
                                        else -> Color(0xFF1E293B)
                                    }
                                ),
                                modifier = Modifier
                                    .widthIn(min = 122.dp, max = 180.dp)
                                    .clickable {
                                        viewModel.startOicLiveMonitor(officer, monitorMode)
                                    }
                                    .testTag("oic_live_officer_chip_${officer.uid}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .alpha(if (isStreamingThis) liveDotAlpha else 1f)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isStreamingThis -> Color(0xFFEF4444)
                                                    isOnDutyReady -> Color(0xFF22C55E)
                                                    officer.isOnDuty && !officer.hideDutyStatus -> DutyOffOrange
                                                    else -> Color(0xFF64748B)
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = officer.displayName.ifBlank { officer.badgeNumber },
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (officer.hasCameraPermission) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                                contentDescription = null,
                                                tint = if (officer.hasCameraPermission) Color(0xFF4ADE80) else Color(0xFF64748B),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Icon(
                                                imageVector = if (officer.hasMicPermission) Icons.Default.Mic else Icons.Default.MicOff,
                                                contentDescription = null,
                                                tint = if (officer.hasMicPermission) Color(0xFF4ADE80) else Color(0xFF64748B),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = if (officer.isOnDuty && !officer.hideDutyStatus) "ON" else "OFF",
                                                color = if (officer.isOnDuty && !officer.hideDutyStatus) Color(0xFF86EFAC) else Color(0xFF94A3B8),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 3. Individual Stream Mode Bar (Cam + Mic / Cam Only / Mic Only) + Selected Officer Header
                if (targetOfficer != null || activeSession != null) {
                    val officerDisplayName = targetOfficer?.displayName?.ifBlank { activeSession?.officerName.orEmpty() }
                        ?: activeSession?.officerName.orEmpty()
                    val officerSub = listOfNotNull(
                        targetOfficer?.rank?.takeIf { it.isNotBlank() },
                        targetOfficer?.badgeNumber?.takeIf { it.isNotBlank() }?.let { "No: $it" }
                    ).joinToString(" • ").ifBlank { "ON DUTY නිලධාරී" }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = officerDisplayName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = officerSub,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (readyOnDutyMembers.size > 1) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val currentIdx = readyOnDutyMembers.indexOfFirst { it.uid == targetOfficer?.uid }
                                FilledTonalButton(
                                    onClick = {
                                        val prevIdx = if (currentIdx <= 0) readyOnDutyMembers.lastIndex else currentIdx - 1
                                        viewModel.startOicLiveMonitor(readyOnDutyMembers[prevIdx], monitorMode)
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color(0xFF1E293B),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("oic_live_prev_officer_btn")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "පෙර නිලධාරියා", modifier = Modifier.size(18.dp))
                                    Text("පෙර", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        val nextIdx = if (currentIdx < 0 || currentIdx >= readyOnDutyMembers.lastIndex) 0 else currentIdx + 1
                                        viewModel.startOicLiveMonitor(readyOnDutyMembers[nextIdx], monitorMode)
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color(0xFF1E293B),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("oic_live_next_officer_btn")
                                ) {
                                    Text("ඊළඟ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "ඊළඟ නිලධාරියා", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val camGranted = targetOfficer?.hasCameraPermission ?: activeSession?.requestedCamera ?: false
                    val micGranted = targetOfficer?.hasMicPermission ?: activeSession?.requestedAudio ?: false

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val modeItems = listOf(
                            Triple(OicMonitorMode.BOTH, "Cam + Mic", camGranted && micGranted),
                            Triple(OicMonitorMode.CAMERA_ONLY, "Cam පමණි", camGranted),
                            Triple(OicMonitorMode.AUDIO_ONLY, "Mic පමණි", micGranted)
                        )
                        modeItems.forEach { (mode, label, allowed) ->
                            val selected = monitorMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (targetOfficer != null) {
                                        viewModel.startOicLiveMonitor(targetOfficer!!, mode)
                                    } else {
                                        viewModel.setOicMonitorMode(mode)
                                    }
                                },
                                enabled = allowed,
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DutyActiveGreen.copy(alpha = 0.25f),
                                    selectedLabelColor = Color(0xFF86EFAC),
                                    containerColor = Color(0xFF141E30),
                                    labelColor = Color(0xFFCBD5E1),
                                    disabledContainerColor = Color(0xFF0F172A),
                                    disabledLabelColor = Color(0xFF475569)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = allowed,
                                    selected = selected,
                                    borderColor = Color(0xFF334155),
                                    selectedBorderColor = DutyActiveGreen
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (!liveError.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF7F1D1D).copy(alpha = 0.72f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = liveError!!,
                            color = Color(0xFFFECACA),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 4. Main Live Video / Audio Stage (or Stand-by Individual Officer Launcher)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF131C2E), Color(0xFF090E19))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = currentFrame
                    when {
                        activeSession == null -> {
                            // Dedicated Stand-by Officer Launcher Stage
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "නිලධාරියෙකු තෝරා සජීවී කැමරා / මයික් Player ආරම්භ කරන්න",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ඕනෑම ON DUTY නිලධාරියෙකුගේ කැමරාව හෝ ශ්‍රව්‍ය වෙන වෙනම නැරඹිය/සවන්දිය හැක.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                if (filteredMembers.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (stationMembers.isEmpty()) {
                                                "මෙම පොලිස් ස්ථානයේ අනුමත නිලධාරීන් නොමැත."
                                            } else {
                                                "සෙවුමට ගැළපෙන නිලධාරියෙකු හමු නොවීය."
                                            },
                                            color = Color(0xFF94A3B8),
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(filteredMembers, key = { "stage_${it.uid}" }) { officer ->
                                            val isDutyVisible = officer.isOnDuty && !officer.hideDutyStatus
                                            val hasAnyPerm = officer.hasCameraPermission || officer.hasMicPermission
                                            val canLaunch = isDutyVisible && hasAnyPerm

                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (canLaunch) Color(0xFF18243B) else Color(0xFF101829),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (canLaunch) DutyActiveGreen.copy(alpha = 0.45f) else Color(0xFF1E293B)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(enabled = canLaunch) {
                                                        viewModel.startOicLiveMonitor(officer, OicMonitorMode.BOTH)
                                                    }
                                            ) {
                                                Column(modifier = Modifier.padding(11.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(38.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (canLaunch) DutyActiveGreen.copy(alpha = 0.2f)
                                                                    else Color(0xFF1E293B)
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Person,
                                                                contentDescription = null,
                                                                tint = if (canLaunch) Color(0xFF4ADE80) else Color(0xFF64748B),
                                                                modifier = Modifier.size(21.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = officer.displayName.ifBlank { officer.badgeNumber },
                                                                color = Color.White,
                                                                fontSize = 13.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = listOfNotNull(
                                                                    officer.rank.takeIf { it.isNotBlank() },
                                                                    officer.badgeNumber.takeIf { it.isNotBlank() }?.let { "No: $it" },
                                                                    if (isDutyVisible) "ON DUTY" else "OFF DUTY"
                                                                ).joinToString(" • "),
                                                                color = if (isDutyVisible) Color(0xFF86EFAC) else Color(0xFF94A3B8),
                                                                fontSize = 11.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }

                                                        if (canLaunch) {
                                                            Button(
                                                                onClick = {
                                                                    viewModel.startOicLiveMonitor(officer, OicMonitorMode.BOTH)
                                                                },
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = Color(0xFFDC2626),
                                                                    contentColor = Color.White
                                                                ),
                                                                shape = RoundedCornerShape(10.dp),
                                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                                modifier = Modifier
                                                                    .height(36.dp)
                                                                    .testTag("oic_stage_play_${officer.uid}")
                                                            ) {
                                                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                Spacer(modifier = Modifier.width(3.dp))
                                                                Text("නරඹන්න", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        } else {
                                                            Surface(
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = Color(0xFF1E293B)
                                                            ) {
                                                                Text(
                                                                    text = if (!isDutyVisible) "OFF DUTY" else "අවසර නැත",
                                                                    color = Color(0xFF94A3B8),
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    if (canLaunch) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            if (officer.hasCameraPermission) {
                                                                FilledTonalButton(
                                                                    onClick = {
                                                                        viewModel.startOicLiveMonitor(officer, OicMonitorMode.CAMERA_ONLY)
                                                                    },
                                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                                        containerColor = Color(0xFF1E293B),
                                                                        contentColor = Color(0xFF93C5FD)
                                                                    ),
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                                    modifier = Modifier.weight(1f).height(32.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(14.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("Cam පමණි", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                                                }
                                                            }
                                                            if (officer.hasMicPermission) {
                                                                FilledTonalButton(
                                                                    onClick = {
                                                                        viewModel.startOicLiveMonitor(officer, OicMonitorMode.AUDIO_ONLY)
                                                                    },
                                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                                        containerColor = Color(0xFF1E293B),
                                                                        contentColor = Color(0xFF86EFAC)
                                                                    ),
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                                    modifier = Modifier.weight(1f).height(32.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("Mic පමණි", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        activeSession.status == "ENDED" -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    tint = Color(0xFFFCA5A5),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "සජීවී විකාශනය අවසන් කරන ලදී",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "නිලධාරියා නිරීක්ෂණය නවත්වා ඇත හෝ OFF DUTY වී ඇත.\nඉහත ලැයිස්තුවෙන් වෙනත් නිලධාරියෙකු තෝරාගත හැක.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                val officerCanRestart = targetOfficer?.let {
                                    it.isOnDuty && !it.hideDutyStatus && (it.hasCameraPermission || it.hasMicPermission)
                                } ?: false
                                if (officerCanRestart && targetOfficer != null) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    FilledTonalButton(
                                        onClick = { viewModel.startOicLiveMonitor(targetOfficer!!, monitorMode) },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color(0xFF1E293B),
                                            contentColor = BadgeGold
                                        )
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("නැවත සම්බන්ධ කරන්න", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        activeSession.requestedCamera && bmp != null -> {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "සජීවී කැමරා දසුන",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        !activeSession.requestedCamera && activeSession.requestedAudio -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(88.dp)
                                        .alpha(liveDotAlpha)
                                        .clip(CircleShape)
                                        .background(DutyActiveGreen.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier.size(46.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "සජීවී මයික්‍රොෆෝන ශ්‍රව්‍ය Player",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                LiveAudioEqualizerBars(
                                    level = if (isMuted) 0f else audioLevel,
                                    isMuted = isMuted
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (isMuted) {
                                        "ශබ්දය නිහඬ කර ඇත (Muted)"
                                    } else {
                                        "නිලධාරියාගේ මයික්‍රොෆෝනයෙන් සජීවී ශ්‍රව්‍ය ලැබෙමින් පවතී"
                                    },
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        activeSession.status == "NEEDS_FOREGROUND" -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                CircularProgressIndicator(color = BadgeGold, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "නිලධාරියාගේ App එක පසුබිමේ පවතී",
                                    color = BadgeGold,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "කැමරාව ආරම්භ කිරීමට නිලධාරියාගේ දුරකථනයට දැනුම්දීමක් යවා ඇත. ඔහු App එක විවෘත කළ වහාම සජීවී දසුන ලැබෙනු ඇත.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "සජීවී දසුනට සම්බන්ධ වෙමින් පවතී...",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "නිලධාරියාගේ දුරකථනයෙන් කැමරා / ශ්‍රව්‍ය විකාශනය ආරම්භ වන තුරු රැඳී සිටින්න.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Overlay LIVE badge + Audio Equalizer Pill on Video when ACTIVE
                    if (activeSession != null && activeSession.status == "ACTIVE") {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFDC2626).copy(alpha = 0.90f)
                            ) {
                                Text(
                                    text = "● LIVE",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (activeSession.requestedCamera) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.65f)
                                ) {
                                    Text(
                                        text = if (activeSession.lensFacing == "FRONT") "ඉදිරිපස Cam" else "පසුපස Cam",
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        if (activeSession.requestedAudio && activeSession.requestedCamera) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.68f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = if (isMuted) Color(0xFFFDBA74) else Color(0xFF4ADE80),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    LiveAudioEqualizerBars(
                                        level = if (isMuted) 0f else audioLevel,
                                        isMuted = isMuted,
                                        barCount = 5,
                                        maxHeightDp = 14
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. Bottom Player Controls Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeSession != null && activeSession.requestedCamera && activeSession.status != "ENDED") {
                        FilledTonalButton(
                            onClick = { viewModel.switchOicMonitorCamera() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("oic_live_switch_cam_btn")
                        ) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (activeSession.lensFacing == "FRONT") "පසුපස" else "ඉදිරිපස",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    if (activeSession != null && activeSession.requestedAudio && activeSession.status != "ENDED") {
                        FilledTonalButton(
                            onClick = { viewModel.toggleOicMonitorMute() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isMuted) DutyOffOrange.copy(alpha = 0.25f) else Color(0xFF1E293B),
                                contentColor = if (isMuted) Color(0xFFFDBA74) else Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("oic_live_mute_btn")
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isMuted) "ශබ්දය" else "නිහඬ",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    if (activeSession != null) {
                        Button(
                            onClick = { viewModel.disconnectCurrentOicMonitor() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("oic_live_end_btn")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (activeSession.status == "ENDED") "ලැයිස්තුවට" else "නවත්වන්න",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    } else {
                        Button(
                            onClick = { viewModel.stopOicLiveMonitor() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("oic_live_end_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Player වසන්න",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveAudioEqualizerBars(
    level: Float,
    isMuted: Boolean,
    barCount: Int = 9,
    maxHeightDp: Int = 26
) {
    val activeColor = if (isMuted) Color(0xFF64748B) else Color(0xFF4ADE80)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val weights = listOf(0.45f, 0.72f, 0.95f, 0.65f, 1.0f, 0.78f, 0.9f, 0.58f, 0.4f)
        for (i in 0 until barCount) {
            val weight = weights[i % weights.size]
            val barH = (4f + (maxHeightDp - 4f) * level * weight).coerceIn(4f, maxHeightDp.toFloat())
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barH.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(activeColor)
            )
        }
    }
}
