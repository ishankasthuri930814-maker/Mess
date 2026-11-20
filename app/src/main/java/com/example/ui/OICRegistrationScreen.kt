package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.BadgeGoldBright
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceThemeColors
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OICRegistrationScreen(
    viewModel: PoliceViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var oicName by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var badgeNumber by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf("OIC (CI - ප්‍රධාන පොලිස් පරීක්ෂක)") }
    var rankExpanded by remember { mutableStateOf(false) }
    var phoneNumber by remember { mutableStateOf("") }
    var stationName by remember { mutableStateOf("") }
    var stationCode by remember { mutableStateOf("OIC-${(1000..9999).random()}") }
    var isSubmitting by remember { mutableStateOf(false) }

    val oicRankOptions = listOf(
        "OIC (CI - ප්‍රධාන පොලිස් පරීක්ෂක)",
        "OIC (IP - පොලිස් පරීක්ෂක)",
        "OIC (SI - උප පොලිස් පරීක්ෂක)",
        "OIC (ASP - සහකාර පොලිස් අධිකාරී)",
        "OIC (ස්ථානාධිපති)"
    )

    val isPhoneValid = phoneNumber.filter { it.isDigit() }.length >= 9
    val isFormValid = oicName.isNotBlank() &&
        stationName.isNotBlank() &&
        stationCode.isNotBlank() &&
        isPhoneValid

    LaunchedEffect(Unit) {
        viewModel.userMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("ස්ථානාධිපති ලියාපදිංචිය (OIC Registration)") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PoliceNavyDark,
                    titleContentColor = BadgeGold
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(BadgeGold.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = PoliceThemeColors.goldText,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "ස්ථානාධිපති (OIC) තොරතුරු",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "ගිණුම: ${currentUser?.email ?: ""} • දුරකථන අංකය අනිවාර්ය වේ",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // OIC Name
            OutlinedTextField(
                value = oicName,
                onValueChange = { oicName = it },
                label = { Text("ස්ථානාධිපතිගේ සම්පූර්ණ නම *") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("oic_reg_name_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // OIC Rank Dropdown
            ExposedDropdownMenuBox(
                expanded = rankExpanded,
                onExpandedChange = { rankExpanded = !rankExpanded }
            ) {
                OutlinedTextField(
                    value = rank,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("තනතුර (Rank)") },
                    leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rankExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = rankExpanded,
                    onDismissRequest = { rankExpanded = false }
                ) {
                    oicRankOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                rank = option
                                rankExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Badge / Service Number
            OutlinedTextField(
                value = badgeNumber,
                onValueChange = { badgeNumber = it },
                label = { Text("නිල අංකය (Service / Badge No)") },
                placeholder = { Text("උදා: 45120") },
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("oic_reg_badge_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Mandatory Phone Number
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("දුරකථන අංකය * (Mandatory Phone Number)") },
                placeholder = { Text("උදා: 0771234567") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = phoneNumber.isNotBlank() && !isPhoneValid,
                supportingText = {
                    if (phoneNumber.isNotBlank() && !isPhoneValid) {
                        Text("අවම වශයෙන් ඉලක්කම් 9 ක වලංගු දුරකථන අංකයක් ඇතුළත් කරන්න.")
                    } else {
                        Text("ඔබගේ ස්ථානයේ නිලධාරීන්ගේ සබඳතා ලැයිස්තුව සඳහා අනිවාර්ය වේ.")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("oic_reg_phone_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Police Station
            OutlinedTextField(
                value = stationName,
                onValueChange = {
                    stationName = it
                    if (stationCode.startsWith("OIC-") && stationCode.length <= 8 && it.isNotBlank()) {
                        stationCode = viewModel.generateSuggestedOicCode(it)
                    }
                },
                label = { Text("පොලිස් ස්ථානය / කොට්ඨාසය * (Station / Division)") },
                placeholder = { Text("උදා: කොළඹ මධ්‍යම පොලිස් ස්ථානය") },
                leadingIcon = { Icon(Icons.Default.LocalPolice, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("oic_reg_station_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Dedicated OIC Station Code Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = PoliceNavyDark
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = BadgeGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ඔබට වෙන් වූ OIC Station Code එක",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                stationCode = viewModel.generateSuggestedOicCode(stationName)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "අලුත් අංකයක් ජනනය කරන්න",
                                tint = BadgeGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "මෙම අංකය ප්‍රධාන OIC වන ඔබට පමණක් වෙන් වූ අංකයකි. ඔබ යටතේ සේවය කරන නිලධාරීන්ට මෙම අංකය ලබා දිය යුතු අතර, ඔවුන් මෙම අංකය භාවිතයෙන් ඔබ වෙත වාර්තා කරනු ඇත.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = stationCode,
                        onValueChange = { stationCode = it.uppercase() },
                        label = { Text("ඔබගේ OIC අංකය (Station Code)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = BadgeGold,
                            unfocusedBorderColor = BadgeGold.copy(alpha = 0.6f),
                            focusedLabelColor = BadgeGold,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                        )
                    )
                }
            }

            if (!isFormValid) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "* නම, පොලිස් ස්ථානය, OIC අංකය සහ වලංගු දුරකථන අංකය (ඉලක්කම් 9+) ඇතුළත් කිරීම අනිවාර්ය වේ.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = {
                    isSubmitting = true
                    viewModel.registerOIC(
                        oicName = oicName,
                        stationName = stationName,
                        customCode = stationCode,
                        phoneNumber = phoneNumber,
                        badgeNumber = badgeNumber,
                        rank = rank,
                        onError = { isSubmitting = false }
                    )
                },
                enabled = isFormValid && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("oic_reg_submit_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BadgeGoldBright,
                    contentColor = PoliceNavyDark
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = PoliceNavyDark,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "OIC ලෙස ලියාපදිංචි වී Dashboard එකට පිවිසෙන්න",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = PoliceNavyDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { viewModel.logout() }) {
                Text("ලොග් අවුට් වන්න (Logout)")
            }
        }
    }
}

