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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceThemeColors
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBranchRegistrationScreen(
    viewModel: PoliceViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var adminName by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var badgeNumber by remember { mutableStateOf("") }
    var stationName by remember { mutableStateOf("") }
    var stationCode by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var adminPinSeed by remember { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val isPhoneValid = phoneNumber.filter { it.isDigit() }.length >= 9
    val isPinValid = adminPinSeed.length == 6 && adminPinSeed.all { it.isDigit() }
    val isFormValid = adminName.isNotBlank() &&
        stationName.isNotBlank() &&
        stationCode.isNotBlank() &&
        isPhoneValid &&
        isPinValid

    LaunchedEffect(Unit) {
        viewModel.userMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("පාලන අංශය ලියාපදිංචිය", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(BadgeGold.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = PoliceThemeColors.goldText,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "පාලන අංශය (Admin Branch)",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "පොලිස් ස්ථාන තොරතුරු, OIC කේතය සහ ඉලක්කම් 6 ක මුරපදය ඇතුළත් කරන්න",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = adminName,
                        onValueChange = { adminName = it },
                        label = { Text("පාලන අංශ නිලධාරී නම * (Admin Officer Name)") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = badgeNumber,
                        onValueChange = { badgeNumber = it },
                        label = { Text("නිල අංකය (Badge / Reg No)") },
                        placeholder = { Text("උදා: 64218") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = stationName,
                        onValueChange = { stationName = it },
                        label = { Text("පොලිස් ස්ථානයේ නම * (Station Name)") },
                        placeholder = { Text("උදා: කොළඹ මධ්‍යම / කළුතර") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = stationCode,
                        onValueChange = { stationCode = it.uppercase() },
                        label = { Text("ස්ථානාධිපති කේතය * (OIC Station Code)") },
                        placeholder = { Text("උදා: OIC-COLOMBO-1234") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = PoliceThemeColors.goldText) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("දුරකථන අංකය * (Mandatory Contact Number)") },
                        placeholder = { Text("07X-XXXXXXX") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = phoneNumber.isNotBlank() && !isPhoneValid,
                        supportingText = {
                            if (phoneNumber.isNotBlank() && !isPhoneValid) {
                                Text("අවම වශයෙන් ඉලක්කම් 9 ක වලංගු දුරකථන අංකයක් ඇතුළත් කරන්න.")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_reg_phone_input")
                    )

                    OutlinedTextField(
                        value = adminPinSeed,
                        onValueChange = { input ->
                            val digitsOnly = input.filter { it.isDigit() }.take(6)
                            adminPinSeed = digitsOnly
                        },
                        label = { Text("පාලන අංශ රහස් මුරපදය (ඉලක්කම් 6)") },
                        placeholder = { Text("උදා: 482910") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = PoliceThemeColors.navyText) },
                        trailingIcon = {
                            IconButton(onClick = { isPinVisible = !isPinVisible }) {
                                Icon(
                                    imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPinVisible) "Hide PIN" else "Show PIN"
                                )
                            }
                        },
                        visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = adminPinSeed.isNotEmpty() && !isPinValid,
                        supportingText = {
                            Text(
                                text = "ඉලක්කම් 6 ක් ඇතුළත් කරන්න. මෙම අංකය පදනම් කරගෙන දිනපතා ස්වයංක්‍රීයව වෙනස් වන රහස් අංකය OIC ට පමණක් පෙනෙනු ඇත.",
                                fontSize = 11.sp,
                                color = if (adminPinSeed.isNotEmpty() && !isPinValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_registration_pin_input")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BadgeGold.copy(alpha = 0.16f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = PoliceThemeColors.goldText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ලියාපදිංචි වීමෙන් පසු ස්ථානාධිපති (OIC) විසින් ඔබගේ ඉල්ලීම අනුමත කළ යුතුය. ඔබ නිර්මාණය කළ ඉලක්කම් 6 මත පදනම්ව දිනපතා අලුත් වන රහස් අංකය OIC හට පමණක් දිස්වේ. ඔහු එම අංකය ලබා දුන් පසු පමණක් පාලන අංශයට ඇතුළු විය හැක.",
                        fontSize = 12.sp,
                        color = PoliceThemeColors.goldText,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (isFormValid) {
                        isSubmitting = true
                        viewModel.registerAdminBranch(
                            adminName = adminName,
                            stationName = stationName,
                            stationCode = stationCode,
                            phoneNumber = phoneNumber,
                            badgeNumber = badgeNumber,
                            adminPinSeed = adminPinSeed,
                            onError = { isSubmitting = false }
                        )
                    }
                },
                enabled = isFormValid && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("admin_register_submit_btn"),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF475569),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "ලියාපදිංචි වන්න (Register Admin Branch)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = { viewModel.logout() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PoliceThemeColors.navyText)
            ) {
                Text("අවලංගු කරන්න (Sign Out)", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
