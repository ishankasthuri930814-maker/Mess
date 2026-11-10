package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.PoliceNavyPrimary
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerRegistrationScreen(
    viewModel: PoliceViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var officerName by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var badgeNumber by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf("Police Constable (PC)") }
    var stationCode by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("නිලධාරී ලියාපදිංචිය (Officer Registration)") },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PoliceNavyPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Badge,
                    contentDescription = null,
                    tint = PoliceNavyPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "පොලිස් නිලධාරී තොරතුරු",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Google ගිණුම: ${currentUser?.email ?: ""}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Officer Name
            OutlinedTextField(
                value = officerName,
                onValueChange = { officerName = it },
                label = { Text("සම්පූර්ණ නම (Officer Name)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Badge Number
            OutlinedTextField(
                value = badgeNumber,
                onValueChange = { badgeNumber = it },
                label = { Text("නිල අංකය (Badge / Police No)") },
                placeholder = { Text("උදා: PC 82914") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Rank
            OutlinedTextField(
                value = rank,
                onValueChange = { rank = it },
                label = { Text("තනතුර (Rank / Designation)") },
                placeholder = { Text("PC / WPC / PS / SI / IP") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // OIC Station Code Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = BadgeGold.copy(alpha = 0.15f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BadgeGold)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = PoliceNavyPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OIC ලබාදුන් Station Code අංකය",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = PoliceNavyPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "ඔබගේ ස්ථානාධිපතිවරයාගෙන් (OIC) ලබාගත් OIC කේතය මෙහි ඇතුළත් කරන්න (උදා: OIC-COL-1042). මෙම අංකය මගින් ඔබ අදාළ OICවරයා යටතට ස්වයංක්‍රීයව ලියාපදිංචි වේ.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = stationCode,
                        onValueChange = { stationCode = it },
                        label = { Text("OIC Station Code") },
                        placeholder = { Text("OIC-XXXX") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PoliceNavyPrimary,
                            unfocusedBorderColor = PoliceNavyPrimary.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    isSubmitting = true
                    viewModel.registerOfficer(
                        officerName = officerName,
                        badgeNumber = badgeNumber,
                        rank = rank,
                        stationCode = stationCode
                    )
                },
                enabled = officerName.isNotBlank() && stationCode.isNotBlank() && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DutyActiveGreen)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "OIC වෙත ඉල්ලීම යොමු කරන්න (Send Request)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
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
