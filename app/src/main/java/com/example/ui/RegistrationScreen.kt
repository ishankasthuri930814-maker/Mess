package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DutyActiveGreen

@Composable
fun RegistrationScreen(
    viewModel: PoliceViewModel
) {
    var officerName by remember { mutableStateOf("") }
    var oicEmail by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isOICMode by remember { mutableStateOf(false) }

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
            text = if (isOICMode) "OICවරයෙකු ලෙස ලියාපදිංචි වීමට විස්තර ඇතුළත් කරන්න." else "ඔබේ OICවරයා තහවුරු කිරීමට විස්තර ඇතුළත් කරන්න.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = officerName,
            onValueChange = { officerName = it },
            label = { Text("සම්පූර්ණ නම") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        
        if (!isOICMode) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = oicEmail,
                onValueChange = { oicEmail = it },
                label = { Text("OICවරයාගේ විද්‍යුත් තැපෑල (Email)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                isSubmitting = true
                if (isOICMode) {
                    viewModel.registerOIC(officerName)
                } else {
                    viewModel.registerOfficer(officerName, oicEmail)
                }
            },
            enabled = officerName.isNotBlank() && (isOICMode || oicEmail.isNotBlank()) && !isSubmitting,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DutyActiveGreen)
        ) {
            Text(if (isOICMode) "OIC ලෙස ලියාපදිංචි වන්න" else "ඉල්ලීම යොමු කරන්න", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { isOICMode = !isOICMode }) {
            Text(if (isOICMode) "නිලධාරියෙකු ලෙස ලියාපදිංචි වන්න" else "මම OICවරයෙකි (I am an OIC)")
        }
    }
}
