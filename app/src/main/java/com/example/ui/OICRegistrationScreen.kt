package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Security
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
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OICRegistrationScreen(
    viewModel: PoliceViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var oicName by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var stationName by remember { mutableStateOf("") }
    var stationCode by remember { mutableStateOf("OIC-${(1000..9999).random()}") }
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold(
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
                    tint = BadgeGold,
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
                text = "Google ගිණුම: ${currentUser?.email ?: ""}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // OIC Name
            OutlinedTextField(
                value = oicName,
                onValueChange = { oicName = it },
                label = { Text("ස්ථානාධිපතිගේ සම්පූර්ණ නම") },
                modifier = Modifier.fillMaxWidth(),
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
                label = { Text("පොලිස් ස්ථානය / කොට්ඨාසය (Station / Division)") },
                placeholder = { Text("උදා: කොළඹ මධ්‍යම පොලිස් ස්ථානය") },
                modifier = Modifier.fillMaxWidth(),
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

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    isSubmitting = true
                    viewModel.registerOIC(
                        oicName = oicName,
                        stationName = stationName,
                        customCode = stationCode
                    )
                },
                enabled = oicName.isNotBlank() && stationCode.isNotBlank() && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BadgeGold)
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
