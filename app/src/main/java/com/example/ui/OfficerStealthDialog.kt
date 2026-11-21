package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppUser
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceThemeColors

@Composable
fun OfficerStealthPasswordDialog(
    viewModel: PoliceViewModel,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = PoliceThemeColors.navyText)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "රහස් පාලන මුරපදය",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "උසස් පෞද්ගලික හා සිතියම් පාලන සැකසුම් වෙත පිවිසීමට රහස් මුරපදය ඇතුළත් කරන්න.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("රහස් මුරපදය (Password)") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Password"
                            )
                        }
                    },
                    isError = errorMessage != null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stealth_password_input")
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (viewModel.isStealthPasswordValid(password)) {
                        onUnlocked()
                    } else {
                        errorMessage = "වැරදි මුරපදයකි! කරුණාකර නිවැරදි මුරපදය ඇතුළත් කරන්න."
                    }
                },
                enabled = password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PoliceNavyPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("stealth_password_submit_btn")
            ) {
                Text("විවෘත කරන්න")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("අවලංගු කරන්න")
            }
        }
    )
}

@Composable
fun OfficerStealthOptionsDialog(
    viewModel: PoliceViewModel,
    currentUser: AppUser?,
    onDismiss: () -> Unit
) {
    var hiddenFromStationLists by remember(currentUser) {
        mutableStateOf(currentUser?.hiddenFromStationLists == true)
    }
    var hiddenOnMap by remember(currentUser) {
        mutableStateOf(currentUser?.hiddenOnMap == true)
    }
    var pauseLocationLogging by remember(currentUser) {
        mutableStateOf(currentUser?.pauseLocationLogging == true)
    }
    var hideDutyStatus by remember(currentUser) {
        mutableStateOf(currentUser?.hideDutyStatus == true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = PoliceThemeColors.navyText)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "රහස් පෞද්ගලික පාලනය",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StealthSwitchRow(
                    title = "OIC / පාලන අංශ ලැයිස්තු වලින් සඟවන්න",
                    subtitle = "නිලධාරී ලැයිස්තුවේ සහ පාලන අංශ ලැයිස්තුවේ නම නොපෙන්වයි.",
                    checked = hiddenFromStationLists,
                    onCheckedChange = { hiddenFromStationLists = it }
                )
                StealthSwitchRow(
                    title = "සජීවී සිතියමෙන් සහ ගමන් මගෙන් සඟවන්න",
                    subtitle = "OIC සිතියමේ ඔබගේ Pin එක සහ ගමන් මග නොපෙන්වයි.",
                    checked = hiddenOnMap,
                    onCheckedChange = { hiddenOnMap = it }
                )
                StealthSwitchRow(
                    title = "GPS ගමන් මග සටහන් කිරීම නවත්වන්න",
                    subtitle = "පසුබිම් හා සජීවී GPS ස්ථාන යැවීම තාවකාලිකව නවත්වයි.",
                    checked = pauseLocationLogging,
                    onCheckedChange = { pauseLocationLogging = it }
                )
                StealthSwitchRow(
                    title = "ON/OFF Duty තත්ත්වය සඟවන්න",
                    subtitle = "ඔබගේ රාජකාරි තත්ත්වය අන් අයට නොපෙන්වන ලෙස සඟවයි.",
                    checked = hideDutyStatus,
                    onCheckedChange = { hideDutyStatus = it }
                )

                HorizontalDivider()

                OutlinedButton(
                    onClick = { viewModel.clearLocalTrailCache() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("දේශීය ගමන් මග මතකය මකන්න (Clear Cache)", fontSize = 12.sp)
                }

                TextButton(
                    onClick = {
                        hiddenFromStationLists = false
                        hiddenOnMap = false
                        pauseLocationLogging = false
                        hideDutyStatus = false
                        viewModel.updateOfficerStealthSettings(
                            hiddenFromStationLists = false,
                            hiddenOnMap = false,
                            pauseLocationLogging = false,
                            hideDutyStatus = false,
                            onSuccess = onDismiss
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("සියල්ල සාමාන්‍ය තත්ත්වයට පත් කරන්න (Reset All)", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.updateOfficerStealthSettings(
                        hiddenFromStationLists = hiddenFromStationLists,
                        hiddenOnMap = hiddenOnMap,
                        pauseLocationLogging = pauseLocationLogging,
                        hideDutyStatus = hideDutyStatus,
                        onSuccess = onDismiss
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PoliceNavyPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("stealth_save_btn")
            ) {
                Text("සුරකින්න (Save)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("වසන්න")
            }
        }
    )
}

@Composable
private fun StealthSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun OicPasscodeRemoveMemberDialog(
    member: AppUser,
    onDismiss: () -> Unit,
    onConfirm: (enteredPasscode: String, onError: (String) -> Unit) -> Unit
) {
    var passcode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ස්ථාන ලැයිස්තුවෙන් ඉවත් කිරීම",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${member.displayName} (${member.rank.ifBlank { member.role }}) ඔබගේ පොලිස් ස්ථාන ලැයිස්තුවෙන් ඉවත් කිරීමට අද දින පාලන අංශ රහස් අංකය (Daily PIN) ඇතුළත් කරන්න.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { input ->
                        passcode = input.filter { it.isDigit() }.take(6)
                        errorMessage = null
                    },
                    label = { Text("අද දින රහස් අංකය (6-Digit PIN)") },
                    leadingIcon = {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = PoliceThemeColors.goldText)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = errorMessage != null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("oic_remove_member_pin_input")
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSubmitting = true
                    onConfirm(passcode) { err ->
                        isSubmitting = false
                        errorMessage = err
                    }
                },
                enabled = passcode.length == 6 && !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("oic_remove_member_confirm_btn")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("ඉවත් කරන්න (Remove)")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("අවලංගු කරන්න")
            }
        }
    )
}
