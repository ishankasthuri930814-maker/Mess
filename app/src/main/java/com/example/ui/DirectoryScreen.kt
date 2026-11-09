package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PoliceContact
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceSlate

@Composable
fun DirectoryScreen(
    viewModel: PoliceViewModel
) {
    val context = LocalContext.current
    val contacts by viewModel.filteredContacts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.contactSearchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.contactFilter.collectAsStateWithLifecycle()
    val isLoading by viewModel.isContactsLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadContacts(context)
    }

    fun makeCall(phone: String) {
        val clean = phone.replace(" ", "").replace("-", "")
        if (clean.isBlank()) return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "ඇමතුම් ලබාගත නොහැක", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWhatsApp(phone: String) {
        var clean = phone.replace(" ", "").replace("-", "")
        if (clean.startsWith("0")) {
            clean = "94" + clean.substring(1)
        }
        val url = "https://api.whatsapp.com/send?phone=$clean"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp විවෘත කළ නොහැක", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$text කොපි කරන ලදී", Toast.LENGTH_SHORT).show()
    }

    fun openNavigation(geo: String, address: String) {
        val uri = if (geo.isNotBlank()) {
            Uri.parse("geo:0,0?q=$geo($address)")
        } else {
            Uri.parse("geo:0,0?q=$address Sri Lanka")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "සිතියම විවෘත කළ නොහැක", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Directory Header Hero Banner (Matching Screenshot 5)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = PoliceNavyPrimary)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PoliceNavyDark, PoliceNavyPrimary, PoliceSlate)
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(30.dp),
                                color = BadgeGold.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = BadgeGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "OFFICIAL DIRECTORY",
                                        color = BadgeGold,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 119 quick button
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFDC2626),
                                modifier = Modifier.clickable { makeCall("119") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("119 EMERGENCY", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Sri Lanka Police Directory",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ශ්‍රී ලංකා පොලිස් නිල ඇමතුම් සහ විද්‍යුත් තැපැල් නාමාවලිය",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 2. Quick Emergency Hotlines (Matching Screenshot 5)
        item {
            Column {
                Text(
                    text = "⚡ Quick Emergency Hotlines",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EmergencyButton(
                        code = "119",
                        label = "Police",
                        color = Color(0xFFEF4444),
                        onClick = { makeCall("119") },
                        modifier = Modifier.weight(1f)
                    )
                    EmergencyButton(
                        code = "118",
                        label = "National",
                        color = Color(0xFF1E3A8A),
                        onClick = { makeCall("118") },
                        modifier = Modifier.weight(1f)
                    )
                    EmergencyButton(
                        code = "1990",
                        label = "Suwa Seriya",
                        color = Color(0xFF059669),
                        onClick = { makeCall("1990") },
                        modifier = Modifier.weight(1f)
                    )
                    EmergencyButton(
                        code = "HQ",
                        label = "0112421111",
                        color = BadgeGold,
                        onClick = { makeCall("0112421111") },
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }

        // 3. Search Bar & Refresh
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setContactSearchQuery(it) },
                    placeholder = { Text("Search station, officer, phone, OIC...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setContactSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("directory_search_input")
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { viewModel.refreshContactsOnline() },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .size(48.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync from Online Sheet", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // 4. Filter Chips (All, Police, Emergency, HQ)
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "ALL" to "All",
                    "POLICE" to "Police Stations",
                    "EMERGENCY" to "Emergency / OIC",
                    "HQ" to "Headquarters & Divisions"
                ).forEach { (key, label) ->
                    item {
                        FilterChip(
                            selected = selectedFilter == key,
                            onClick = { viewModel.setContactFilter(key) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // Results count
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Directory Results (${contacts.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (searchQuery.isNotBlank()) {
                    Text(
                        text = "Query: \"$searchQuery\"",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 5. Contacts List Cards (Matching Screenshot 3 & 6)
        if (contacts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLoading) "නාමාවලිය ලබාගනිමින් පවතී..." else "සෙවුමට ගැළපෙන සබඳතා හමු නොවීය",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(contacts.take(50)) { contact ->
                PoliceContactCard(
                    contact = contact,
                    onCall = { makeCall(it) },
                    onWhatsApp = { openWhatsApp(it) },
                    onCopy = { label, num -> copyToClipboard(label, num) },
                    onNavigate = { geo, addr -> openNavigation(geo, addr) }
                )
            }
        }
    }
}

@Composable
fun EmergencyButton(
    code: String,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = code, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PoliceContactCard(
    contact: PoliceContact,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onCopy: (String, String) -> Unit,
    onNavigate: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Station / Designation Name and Rank badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (contact.name.isNotBlank() && contact.name != contact.designation) {
                        Text(
                            text = contact.name,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (contact.rank.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BadgeGold.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = contact.rank,
                            color = BadgeGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Primary Telephone
            if (contact.primaryPhone.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(DutyActiveGreen.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = DutyActiveGreen, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "ප්‍රධාන දුරකථන / Primary Telephone", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = contact.primaryPhone, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        // Call button
                        Button(
                            onClick = { onCall(contact.primaryPhone) },
                            colors = ButtonDefaults.buttonColors(containerColor = DutyActiveGreen),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call", fontSize = 12.sp)
                        }

                        IconButton(
                            onClick = { onCopy(contact.displayName, contact.primaryPhone) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Mobile 2
            if (contact.secondaryPhone.isNotBlank() && contact.secondaryPhone != contact.primaryPhone) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF0284C7).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "ජංගම දුරකථන / Mobile", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = contact.secondaryPhone, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onCall(contact.secondaryPhone) },
                            colors = ButtonDefaults.buttonColors(containerColor = DutyActiveGreen),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call", fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Button(
                            onClick = { onWhatsApp(contact.secondaryPhone) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("WhatsApp", fontSize = 10.sp)
                        }

                        IconButton(
                            onClick = { onCopy(contact.displayName, contact.secondaryPhone) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // OIC Direct Lines (Traffic, Crime, Vice, Community) - Matching Screenshot 3 & 6
            val oicLines = listOf(
                "Traffic OIC" to contact.oicTraffic,
                "Crime OIC" to contact.oicCrime,
                "Vice OIC" to contact.oicVice,
                "Community OIC" to contact.oicCommunity
            ).filter { it.second.isNotBlank() }

            if (oicLines.isNotEmpty()) {
                Text(
                    text = "OIC ස්ථානාධිපති සෘජු අංශ / OIC Direct Lines:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                oicLines.forEach { (label, number) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$label: $number",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = { onCall(number) },
                            colors = ButtonDefaults.buttonColors(containerColor = DutyActiveGreen),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Call", fontSize = 10.sp)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Button(
                            onClick = { onWhatsApp(number) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("WhatsApp", fontSize = 10.sp)
                        }
                    }
                }
            }

            // Location Navigation & Address
            if (contact.locationAddress.isNotBlank() || contact.locationGeo.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DutyActiveGreen.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(contact.locationGeo, contact.locationAddress) }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, tint = DutyActiveGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Google Maps Navigation (මාර්ගය පෙන්වන්න)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DutyActiveGreen
                            )
                            if (contact.locationAddress.isNotBlank()) {
                                Text(
                                    text = contact.locationAddress,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
