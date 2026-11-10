package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppUser
import com.example.util.DateTimeUtils

@Composable
fun OfficerDetailDialog(
    viewModel: PoliceViewModel,
    onDismiss: () -> Unit
) {
    val officer by viewModel.selectedOfficer.collectAsStateWithLifecycle()
    val activity by viewModel.selectedOfficerActivity.collectAsStateWithLifecycle()

    if (officer == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(officer!!.displayName) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("තත්ත්වය: ${officer!!.status}", fontWeight = FontWeight.Bold)
                Text("Duty: ${if (officer!!.isOnDuty) "ON" else "OFF"}")
                
                officer!!.lastLocation?.let { loc ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("අවසාන ස්ථානය:", fontWeight = FontWeight.Bold)
                    Text("${loc["lat"]}, ${loc["lng"]}", fontSize = 14.sp)
                    val ts = loc["timestamp"] as? Long ?: 0L
                    if (ts > 0) {
                        Text("යාවත්කාලීන වූයේ: ${DateTimeUtils.formatTime(ts)}", fontSize = 12.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("මෑත ක්‍රියාකාරකම්:", fontWeight = FontWeight.Bold)
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(activity.sortedByDescending { it.timestamp }.take(10)) { record ->
                        ListItem(
                            headlineContent = { Text(record.type) },
                            supportingContent = { Text(DateTimeUtils.formatSinhalaDisplayDate(DateTimeUtils.formatDate(record.timestamp))) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("වසන්න") }
        }
    )
}
