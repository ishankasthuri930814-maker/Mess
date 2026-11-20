package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppNotification
import com.example.data.AppUser
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceThemeColors
import com.example.util.DateTimeUtils

@Composable
fun NotificationBellButton(
    unreadCount: Int,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.testTag("notifications_bell_button")
    ) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text(
                            text = if (unreadCount > 99) "99+" else "$unreadCount",
                            fontSize = 10.sp,
                            color = Color.White
                        )
                    }
                }
            }
        ) {
            Icon(
                imageVector = if (unreadCount > 0) Icons.Default.Notifications else Icons.Default.NotificationsNone,
                contentDescription = "Notifications",
                tint = tint
            )
        }
    }
}

@Composable
fun NotificationsInboxDialog(
    viewModel: PoliceViewModel,
    isOic: Boolean,
    onOpenSendDialog: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val appUser by viewModel.appUser.collectAsStateWithLifecycle()
    val myUid = appUser?.uid ?: ""

    LaunchedEffect(Unit) {
        viewModel.markNotificationsAsRead()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = PoliceThemeColors.navyText
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "දැනුම්දීම් (Notifications)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isOic) {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenSendDialog()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_send_notification_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PoliceNavyPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("නව දැනුම්දීමක් යවන්න (Send Alert)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "තවමත් දැනුම්දීම් කිසිවක් නොමැත.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(notifications, key = { it.id.ifBlank { it.createdAtMs.toString() } }) { notif ->
                            NotificationItemCard(
                                notification = notif,
                                canDelete = notif.senderUid == myUid && notif.id.isNotBlank(),
                                onDelete = { viewModel.deleteNotification(notif.id) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("වසන්න (Close)")
            }
        }
    )
}

@Composable
private fun NotificationItemCard(
    notification: AppNotification,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    val isUrgent = notification.priority == "URGENT"
    val timeMs = notification.createdAt?.toDate()?.time ?: notification.createdAtMs
    val scopeLabel = when (notification.targetType) {
        "ALL" -> "සියලුම පරිශීලකයන්ට"
        "STATION" -> "ස්ථානය (${notification.targetStationCode})"
        else -> "පෞද්ගලික පණිවිඩය"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUrgent) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isUrgent) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PriorityHigh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "හදිසි",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Surface(
                        color = PoliceThemeColors.navyText.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = scopeLabel,
                            color = PoliceThemeColors.navyText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete notification",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = notification.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = notification.message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "යැවූයේ: ${notification.senderName.ifBlank { notification.senderRole }}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (timeMs > 0L) {
                    Text(
                        text = DateTimeUtils.formatFullDateTime(timeMs),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendNotificationDialog(
    viewModel: PoliceViewModel,
    subordinates: List<AppUser>,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var targetType by remember { mutableStateOf("STATION") } // "STATION", "USER", "ALL"
    var selectedOfficer by remember { mutableStateOf<AppUser?>(subordinates.firstOrNull()) }
    var officerDropdownExpanded by remember { mutableStateOf(false) }
    var isUrgent by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }

    val canSend = title.isNotBlank() &&
        message.isNotBlank() &&
        !isSending &&
        (targetType != "USER" || selectedOfficer != null)

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = PoliceThemeColors.navyText)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "දැනුම්දීමක් යවන්න",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
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
                Text(
                    text = "ලැබිය යුතු පිරිස තෝරන්න:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = targetType == "STATION",
                        onClick = { targetType = "STATION" },
                        label = { Text("ස්ථානයට", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = targetType == "USER",
                        onClick = { targetType = "USER" },
                        enabled = subordinates.isNotEmpty(),
                        label = { Text("නිලධාරියෙකුට", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = targetType == "ALL",
                        onClick = { targetType = "ALL" },
                        label = { Text("සියලු දෙනාට", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (targetType == "USER") {
                    ExposedDropdownMenuBox(
                        expanded = officerDropdownExpanded,
                        onExpandedChange = { officerDropdownExpanded = !officerDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedOfficer?.let { "${it.displayName} (${it.badgeNumber.ifBlank { it.email }})" }
                                ?: "නිලධාරියෙකු තෝරන්න",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("නිලධාරියා") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = officerDropdownExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        )
                        ExposedDropdownMenu(
                            expanded = officerDropdownExpanded,
                            onDismissRequest = { officerDropdownExpanded = false }
                        ) {
                            subordinates.forEach { officer ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${officer.displayName} (${officer.rank.ifBlank { "Officer" }})")
                                    },
                                    onClick = {
                                        selectedOfficer = officer
                                        officerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isUrgent,
                        onClick = { isUrgent = false },
                        label = { Text("සාමාන්‍ය (Normal)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isUrgent,
                        onClick = { isUrgent = true },
                        label = { Text("හදිසි (Urgent)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 120) title = it },
                    label = { Text("මාතෘකාව (Title)") },
                    placeholder = { Text("උදා: හදිසි රැස්වීමක් / රාජකාරි උපදෙස්") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notification_title_input")
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 1000) message = it },
                    label = { Text("පණිවිඩය (Message)") },
                    placeholder = { Text("නිලධාරීන් වෙත දැනුම් දීමට අවශ්‍ය විස්තර මෙහි ලියන්න...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notification_message_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSending = true
                    viewModel.sendNotification(
                        title = title,
                        message = message,
                        targetType = targetType,
                        targetUid = selectedOfficer?.uid ?: "",
                        priority = if (isUrgent) "URGENT" else "NORMAL",
                        onSuccess = {
                            isSending = false
                            onDismiss()
                        },
                        onError = {
                            isSending = false
                        }
                    )
                },
                enabled = canSend,
                modifier = Modifier.testTag("send_notification_confirm_btn")
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("යවන්න (Send)")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSending
            ) {
                Text("අවලංගු කරන්න")
            }
        }
    )
}
