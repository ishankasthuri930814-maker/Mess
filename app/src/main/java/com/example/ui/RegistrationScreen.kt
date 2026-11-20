package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DutyActiveGreen

@Composable
fun RegistrationScreen(
    viewModel: PoliceViewModel
) {
    var officerName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var stationCode by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isOICMode by remember { mutableStateOf(false) }

    val isPhoneValid = phoneNumber.filter { it.isDigit() }.length >= 9

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isOICMode) "OIC ලියාපදිංචිය" else "නිලධාරි ලියාපදිංචිය",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isOICMode) "OICවරයෙකු ලෙස ලියාපදිංචි වීමට ඔබේ ස්ථාන කේතය (Station Code) සහ දුරකථන අංකය ඇතුළත් කරන්න." else "ඔබේ OICවරයාගේ ස්ථාන කේතය (Station Code) සහ දුරකථන අංකය ඇතුළත් කරන්න.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = officerName,
            onValueChange = { officerName = it },
            label = { Text("සම්පූර්ණ නම *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("දුරකථන අංකය * (Mandatory Phone)") },
            placeholder = { Text("0771234567") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = phoneNumber.isNotBlank() && !isPhoneValid,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = stationCode,
            onValueChange = { stationCode = it.uppercase() },
            label = { Text(if (isOICMode) "අලුත් ස්ථාන කේතයක් * (Station Code)" else "OICගේ ස්ථාන කේතය * (Station Code)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                isSubmitting = true
                if (isOICMode) {
                    viewModel.registerOIC(
                        oicName = officerName,
                        customCode = stationCode,
                        phoneNumber = phoneNumber,
                        onError = { isSubmitting = false }
                    )
                } else {
                    viewModel.registerOfficer(
                        officerName = officerName,
                        phoneNumber = phoneNumber,
                        stationCode = stationCode,
                        onError = { isSubmitting = false }
                    )
                }
            },
            enabled = officerName.isNotBlank() && stationCode.isNotBlank() && isPhoneValid && !isSubmitting,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DutyActiveGreen,
                contentColor = Color.White
            )
        ) {
            Text(if (isOICMode) "OIC ලෙස ලියාපදිංචි වන්න" else "ඉල්ලීම යොමු කරන්න", fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { isOICMode = !isOICMode }) {
            Text(if (isOICMode) "නිලධාරියෙකු ලෙස ලියාපදිංචි වන්න" else "මම OICවරයෙකි (I am an OIC)")
        }
    }
}

