package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BadgeGold

@Composable
fun ConfigureMessRatesDialog(
    initialBreakfast: Double = 150.0,
    initialLunch: Double = 220.0,
    initialDinner: Double = 180.0,
    onDismiss: () -> Unit,
    onSaveRates: (bPrice: Double, lPrice: Double, dPrice: Double) -> Unit
) {
    var bText by remember { mutableStateOf("%.2f".format(initialBreakfast)) }
    var lText by remember { mutableStateOf("%.2f".format(initialLunch)) }
    var dText by remember { mutableStateOf("%.2f".format(initialDinner)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .widthIn(max = 480.dp)
                    .clip(RoundedCornerShape(20.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(BadgeGold.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = BadgeGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "මෙස් ආහාර පෙරනිමි මිල ගණන්",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ආහාර වේල් සඳහා සාමාන්‍ය මිල සකසන්න",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                        }
                    }

                    // 1. Breakfast Rate Input
                    OutlinedTextField(
                        value = bText,
                        onValueChange = { bText = it },
                        label = { Text("උදේ ආහාරයේ මිල (Breakfast Rate in LKR)") },
                        leadingIcon = {
                            Icon(Icons.Default.WbSunny, contentDescription = null, tint = BadgeGold)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rate_input_breakfast")
                    )

                    // 2. Lunch Rate Input
                    OutlinedTextField(
                        value = lText,
                        onValueChange = { lText = it },
                        label = { Text("දිවා ආහාරයේ මිල (Lunch Rate in LKR)") },
                        leadingIcon = {
                            Icon(Icons.Default.LightMode, contentDescription = null, tint = Color(0xFFF59E0B))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rate_input_lunch")
                    )

                    // 3. Dinner Rate Input
                    OutlinedTextField(
                        value = dText,
                        onValueChange = { dText = it },
                        label = { Text("රාත්‍රී ආහාරයේ මිල (Dinner Rate in LKR)") },
                        leadingIcon = {
                            Icon(Icons.Default.Bedtime, contentDescription = null, tint = Color(0xFF6366F1))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rate_input_dinner")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("අවලංගු කරන්න")
                        }

                        Button(
                            onClick = {
                                val b = bText.toDoubleOrNull() ?: 150.0
                                val l = lText.toDoubleOrNull() ?: 220.0
                                val d = dText.toDoubleOrNull() ?: 180.0
                                onSaveRates(b, l, d)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_rates_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("සුරකින්න")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
