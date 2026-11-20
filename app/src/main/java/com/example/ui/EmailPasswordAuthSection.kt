package com.example.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.BadgeGoldBright
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceThemeColors
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

fun resolveLoginEmail(input: String): String {
    val trimmed = input.trim().lowercase()
    return when {
        trimmed.isBlank() -> ""
        trimmed.contains("@") -> trimmed
        else -> "$trimmed@police.lk"
    }
}

fun authenticateWithEmailPassword(
    emailOrUsername: String,
    password: String,
    isCreateAccount: Boolean,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    val email = resolveLoginEmail(emailOrUsername)
    if (email.isBlank()) {
        onAuthError("කරුණාකර Username හෝ Email ලිපිනය ඇතුළත් කරන්න.")
        return
    }
    if (password.length < 6) {
        onAuthError("මුරපදය (Password) අවම වශයෙන් අක්ෂර 6ක් විය යුතුය.")
        return
    }

    scope.launch {
        try {
            if (isCreateAccount) {
                Firebase.auth.createUserWithEmailAndPassword(email, password).await()
            } else {
                Firebase.auth.signInWithEmailAndPassword(email, password).await()
            }
            onAuthSuccess()
        } catch (e: FirebaseAuthWeakPasswordException) {
            onAuthError("මුරපදය දුර්වලයි. අවම වශයෙන් අක්ෂර 6ක් ඇතුළත් කරන්න.")
        } catch (e: FirebaseAuthUserCollisionException) {
            onAuthError("මෙම ගිණුම දැනටමත් ලියාපදිංචි වී ඇත. 'පිවිසෙන්න (Sign In)' තෝරා ලොග් වන්න.")
        } catch (e: FirebaseAuthInvalidUserException) {
            onAuthError("මෙම Username / Email ගිණුම හමු නොවීය. පළමුව 'නව ගිණුමක්' සාදන්න.")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            onAuthError("ඇතුළත් කළ Username / Email හෝ Password වැරදිය.")
        } catch (e: FirebaseNetworkException) {
            onAuthError("අන්තර්ජාල සබඳතාවය පරීක්ෂා කර නැවත උත්සාහ කරන්න.")
        } catch (e: FirebaseAuthException) {
            Log.e("Auth", "Email/Password auth error: ${e.errorCode}", e)
            val msg = if (e.errorCode == "ERROR_OPERATION_NOT_ALLOWED") {
                "Firebase Console හි Email/Password පිවිසුම සක්‍රීය කර නොමැත."
            } else {
                e.localizedMessage ?: "පිවිසීම අසාර්ථක විය."
            }
            onAuthError(msg)
        } catch (e: Exception) {
            Log.e("Auth", "Email/Password auth exception", e)
            onAuthError(e.localizedMessage ?: "පිවිසීම අසාර්ථක විය.")
        }
    }
}

@Composable
fun EmailPasswordAuthSection(
    isDarkCard: Boolean,
    isLoadingExternal: Boolean,
    onLoadingChange: (Boolean) -> Unit,
    onErrorChange: (String?) -> Unit,
    onAuthSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var emailOrUsername by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isCreateAccount by remember { mutableStateOf(false) }

    val primaryTextColor = if (isDarkCard) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (isDarkCard) Color.White.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = if (isDarkCard) BadgeGoldBright else PoliceThemeColors.navyText
    val chipSelectedBgColor = if (isDarkCard) BadgeGoldBright else PoliceNavyPrimary
    val dividerColor = if (isDarkCard) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant

    val textFieldColors = if (isDarkCard) {
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = BadgeGoldBright,
            unfocusedBorderColor = Color.White.copy(alpha = 0.45f),
            focusedLabelColor = BadgeGoldBright,
            unfocusedLabelColor = Color.White.copy(alpha = 0.8f),
            focusedLeadingIconColor = BadgeGoldBright,
            unfocusedLeadingIconColor = Color.White.copy(alpha = 0.8f),
            focusedTrailingIconColor = BadgeGoldBright,
            unfocusedTrailingIconColor = Color.White.copy(alpha = 0.8f),
            cursorColor = BadgeGoldBright
        )
    } else {
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PoliceThemeColors.navyText,
            focusedLabelColor = PoliceThemeColors.navyText
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = dividerColor)
            Text(
                text = "  හෝ Username / Email මගින්  ",
                fontSize = 12.sp,
                color = secondaryTextColor,
                fontWeight = FontWeight.Medium
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = dividerColor)
        }

        // Toggle between Sign In and Create Account
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !isCreateAccount,
                onClick = {
                    isCreateAccount = false
                    onErrorChange(null)
                },
                label = {
                    Text(
                        text = "පිවිසෙන්න (Sign In)",
                        fontSize = 12.sp,
                        fontWeight = if (!isCreateAccount) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("auth_mode_signin_chip"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipSelectedBgColor,
                    selectedLabelColor = if (isDarkCard) PoliceNavyDark else Color.White,
                    labelColor = primaryTextColor
                )
            )
            FilterChip(
                selected = isCreateAccount,
                onClick = {
                    isCreateAccount = true
                    onErrorChange(null)
                },
                label = {
                    Text(
                        text = "නව ගිණුමක් (Register)",
                        fontSize = 12.sp,
                        fontWeight = if (isCreateAccount) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("auth_mode_register_chip"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipSelectedBgColor,
                    selectedLabelColor = if (isDarkCard) PoliceNavyDark else Color.White,
                    labelColor = primaryTextColor
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = emailOrUsername,
            onValueChange = {
                emailOrUsername = it
                onErrorChange(null)
            },
            label = { Text("Username හෝ Email") },
            placeholder = { Text("උදා: oic1 හෝ name@gmail.com") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Person, contentDescription = null)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = textFieldColors,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("email_username_input")
        )

        if (emailOrUsername.isNotBlank() && !emailOrUsername.contains("@")) {
            Text(
                text = "භාවිතා වන Email: ${resolveLoginEmail(emailOrUsername)}",
                fontSize = 11.sp,
                color = accentColor,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                onErrorChange(null)
            },
            label = { Text("මුරපදය (Password)") },
            placeholder = { Text("අවම අක්ෂර 6ක්") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "මුරපදය සඟවන්න" else "මුරපදය පෙන්වන්න"
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = textFieldColors,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                onLoadingChange(true)
                onErrorChange(null)
                authenticateWithEmailPassword(
                    emailOrUsername = emailOrUsername,
                    password = password,
                    isCreateAccount = isCreateAccount,
                    scope = coroutineScope,
                    onAuthSuccess = {
                        onLoadingChange(false)
                        onAuthSuccess()
                    },
                    onAuthError = { msg ->
                        onLoadingChange(false)
                        onErrorChange(msg)
                    }
                )
            },
            enabled = !isLoadingExternal && emailOrUsername.isNotBlank() && password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("email_auth_submit_button"),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDarkCard) Color.White else PoliceNavyPrimary,
                contentColor = if (isDarkCard) PoliceNavyDark else Color.White
            )
        ) {
            if (isLoadingExternal) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = if (isDarkCard) PoliceNavyDark else Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (isCreateAccount) {
                        "ගිණුම සාදා පිවිසෙන්න (Create Account)"
                    } else {
                        "Username / Password මගින් පිවිසෙන්න"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
