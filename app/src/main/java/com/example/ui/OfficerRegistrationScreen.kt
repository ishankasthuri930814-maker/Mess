package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceThemeColors
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerRegistrationScreen(
    viewModel: PoliceViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var officerName by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var badgeNumber by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf("Police Constable (PC)") }
    var rankExpanded by remember { mutableStateOf(false) }
    var stationName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var stationCode by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val rankOptions = listOf(
        "Police Constable (PC)",
        "Woman Police Constable (WPC)",
        "Police Sergeant (PS)",
        "Woman Police Sergeant (WPS)",
        "Sub Inspector of Police (SI)",
        "Inspector of Police (IP)",
        "Chief Inspector (CI)",
        "Assistant Superintendent of Police (ASP)",
        "Superintendent of Police (SP)"
    )

    val isFormValid = officerName.isNotBlank() &&
        badgeNumber.isNotBlank() &&
        rank.isNotBlank() &&
        stationName.isNotBlank() &&
        phoneNumber.filter { it.isDigit() }.length >= 9 &&
        address.isNotBlank() &&
        stationCode.isNotBlank()

    LaunchedEffect(Unit) {
        viewModel.userMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("නිලධාරී ලියාපදිංචිය (Officer Profile)") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PoliceNavyPrimary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PoliceThemeColors.navyText.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Badge,
                    contentDescription = null,
                    tint = PoliceThemeColors.navyText,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "පොලිස් නිලධාරී සම්පූර්ණ තොරතුරු",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "ගිණුම: ${currentUser?.email ?: ""} • සියලු විස්තර අනිවාර්ය වේ",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Officer Name
            OutlinedTextField(
                value = officerName,
                onValueChange = { officerName = it },
                label = { Text("සම්පූර්ණ නම * (Full Name)") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Badge Number
            OutlinedTextField(
                value = badgeNumber,
                onValueChange = { badgeNumber = it },
                label = { Text("නිල අංකය * (Badge / Police Reg No)") },
                placeholder = { Text("උදා: 82914") },
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Rank Dropdown
            ExposedDropdownMenuBox(
                expanded = rankExpanded,
                onExpandedChange = { rankExpanded = !rankExpanded }
            ) {
                OutlinedTextField(
                    value = rank,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("තනතුර * (Rank)") },
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
                    rankOptions.forEach { option ->
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

            Spacer(modifier = Modifier.height(10.dp))

            // Police Station Name
            OutlinedTextField(
                value = stationName,
                onValueChange = { stationName = it },
                label = { Text("සේවා ස්ථානය * (Police Station)") },
                placeholder = { Text("උදා: Colombo Central Police Station") },
                leadingIcon = { Icon(Icons.Default.LocalPolice, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Mandatory Personal Phone Number
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("පුද්ගලික දුරකථන අංකය * (Personal Phone)") },
                placeholder = { Text("උදා: 0771234567") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = phoneNumber.isNotBlank() && phoneNumber.filter { it.isDigit() }.length < 9,
                supportingText = {
                    if (phoneNumber.isNotBlank() && phoneNumber.filter { it.isDigit() }.length < 9) {
                        Text("අවම වශයෙන් ඉලක්කම් 9 ක වලංගු දුරකථන අංකයක් ඇතුළත් කරන්න.")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Mandatory Personal Address
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("පුද්ගලික ලිපිනය * (Personal Address)") },
                placeholder = { Text("ඔබගේ ස්ථිර පදිංචි ලිපිනය ඇතුළත් කරන්න") },
                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                minLines = 2,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // OIC Station Code Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = BadgeGold.copy(alpha = 0.15f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BadgeGold)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = PoliceThemeColors.navyText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OIC ලබාදුන් Station Code අංකය *",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = PoliceThemeColors.navyText
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "ඔබගේ ස්ථානාධිපතිවරයාගෙන් (OIC) ලබාගත් OIC කේතය ඇතුළත් කරන්න (උදා: OIC-COL-1042).",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = stationCode,
                        onValueChange = { stationCode = it.uppercase() },
                        label = { Text("OIC Station Code *") },
                        placeholder = { Text("OIC-XXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PoliceThemeColors.navyText,
                            unfocusedBorderColor = PoliceThemeColors.navyText.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            if (!isFormValid) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "* නම, නිල අංකය, සේවා ස්ථානය, පුද්ගලික දුරකථන අංකය (ඉලක්කම් 9+), පදිංචි ලිපිනය සහ OIC කේතය සියල්ල අනිවාර්ය වේ.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    isSubmitting = true
                    viewModel.registerOfficer(
                        officerName = officerName,
                        badgeNumber = badgeNumber,
                        rank = rank,
                        stationName = stationName,
                        phoneNumber = phoneNumber,
                        address = address,
                        stationCode = stationCode,
                        onError = { isSubmitting = false }
                    )
                },
                enabled = isFormValid && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DutyActiveGreen,
                    contentColor = Color.White
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "ලියාපදිංචිය සම්පූර්ණ කර OIC වෙත යවන්න",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = { viewModel.logout() }) {
                Text("ලොග් අවුට් වන්න (Logout)")
            }
        }
    }
}
