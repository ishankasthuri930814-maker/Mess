package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppUser
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceThemeColors

internal enum class StationContactRoleFilter(val label: String) {
    ALL("සියල්ල"),
    LEADERSHIP("OIC / පාලන"),
    OFFICERS("නිලධාරීන්")
}

internal fun dialStationContactPhone(context: Context, phone: String) {
    val clean = phone.replace(" ", "").replace("-", "")
    if (clean.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
    } catch (_: Exception) {
        Toast.makeText(context, "ඇමතුම් ලබාගත නොහැක", Toast.LENGTH_SHORT).show()
    }
}

internal fun openStationContactWhatsApp(context: Context, phone: String) {
    val digits = phone.filter { it.isDigit() }
    if (digits.isBlank()) return
    val waNumber = when {
        digits.startsWith("94") -> digits
        digits.startsWith("0") && digits.length >= 10 -> "94${digits.drop(1)}"
        else -> digits
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$waNumber")))
    } catch (_: Exception) {
        Toast.makeText(context, "WhatsApp විවෘත කළ නොහැක", Toast.LENGTH_SHORT).show()
    }
}

internal fun copyStationContactPhone(context: Context, phone: String, ownerName: String) {
    if (phone.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Station Contact Phone", phone))
    Toast.makeText(context, "$ownerName ගේ අංකය ($phone) පිටපත් විය", Toast.LENGTH_SHORT).show()
}

internal fun filterStationContacts(
    stationContacts: List<AppUser>,
    searchQuery: String,
    roleFilter: StationContactRoleFilter
): List<AppUser> {
    val q = searchQuery.trim().lowercase()
    return stationContacts.filter { contact ->
        val roleMatch = when (roleFilter) {
            StationContactRoleFilter.ALL -> true
            StationContactRoleFilter.LEADERSHIP -> contact.role == "OIC" || contact.role == "ADMIN_BRANCH"
            StationContactRoleFilter.OFFICERS -> contact.role == "OFFICER"
        }
        val queryMatch = q.isBlank() ||
            contact.displayName.lowercase().contains(q) ||
            contact.rank.lowercase().contains(q) ||
            contact.badgeNumber.lowercase().contains(q) ||
            contact.phoneNumber.lowercase().contains(q)
        roleMatch && queryMatch
    }
}

@Composable
fun StationContactsSection(
    viewModel: PoliceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appUser by viewModel.appUser.collectAsStateWithLifecycle()
    val stationContacts by viewModel.stationContacts.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf(StationContactRoleFilter.ALL) }
    var isEditOwnContactOpen by remember { mutableStateOf(false) }

    val filteredContacts = remember(stationContacts, searchQuery, roleFilter) {
        filterStationContacts(stationContacts, searchQuery, roleFilter)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            stationContactsItems(
                appUser = appUser,
                stationContacts = stationContacts,
                filteredContacts = filteredContacts,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                roleFilter = roleFilter,
                onRoleFilterChange = { roleFilter = it },
                onCall = { dialStationContactPhone(context, it) },
                onWhatsApp = { openStationContactWhatsApp(context, it) },
                onCopy = { phone, ownerName -> copyStationContactPhone(context, phone, ownerName) },
                onEditOwn = { isEditOwnContactOpen = true }
            )
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (isEditOwnContactOpen && appUser != null) {
        EditOwnStationContactDialog(
            initialPhone = appUser?.phoneNumber.orEmpty(),
            initialBadge = appUser?.badgeNumber.orEmpty(),
            initialRank = appUser?.rank.orEmpty(),
            onDismiss = { isEditOwnContactOpen = false },
            onSave = { phone, badge, rank ->
                viewModel.updateOwnContactInfo(
                    phoneNumber = phone,
                    badgeNumber = badge,
                    rank = rank,
                    onSuccess = { isEditOwnContactOpen = false }
                )
            }
        )
    }
}

internal fun LazyListScope.stationContactsItems(
    appUser: AppUser?,
    stationContacts: List<AppUser>,
    filteredContacts: List<AppUser>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    roleFilter: StationContactRoleFilter,
    onRoleFilterChange: (StationContactRoleFilter) -> Unit,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onCopy: (String, String) -> Unit,
    onEditOwn: () -> Unit,
    itemModifier: Modifier = Modifier
) {
    val stationCode = appUser?.oicStationCode?.trim()?.uppercase().orEmpty()
    val stationName = appUser?.stationName?.takeIf { it.isNotBlank() } ?: stationCode.ifBlank { "පොලිස් ස්ථානය" }

    // 1. Station Scoped Banner Card
    item(key = "station_contacts_banner") {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PoliceNavyDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = itemModifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(BadgeGold),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contacts,
                                contentDescription = null,
                                tint = PoliceNavyDark,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ස්ථානීය නිලධාරී සබඳතා ලැයිස්තුව",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "$stationName • $stationCode",
                                fontSize = 11.sp,
                                color = BadgeGold,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "${stationContacts.size} දෙනෙක්",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "ඔබගේ පොලිස් ස්ථානයට ($stationCode) අනුමත OIC, පාලන අංශය සහ නිලධාරීන්ට පමණක් දිස්වන පෞද්ගලික සබඳතා ලැයිස්තුව.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 15.sp
                )
            }
        }
    }

    // 2. Prompt if current user has no phone number saved yet (legacy accounts)
    val currentUserHasValidPhone = (appUser?.phoneNumber?.filter { it.isDigit() }?.length ?: 0) >= 9
    if (!currentUserHasValidPhone && appUser != null) {
        item(key = "station_contacts_missing_phone") {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = itemModifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ඔබගේ දුරකථන අංකය සටහන් කර නැත",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "ස්ථානයේ අනෙකුත් නිලධාරීන්ට ඔබව සම්බන්ධ කර ගැනීමට දුරකථන අංකය එක් කරන්න.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onEditOwn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("add_missing_phone_btn")
                    ) {
                        Text("එක් කරන්න", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 3. Search Bar & Filter Chips
    item(key = "station_contacts_search_filter") {
        Column(
            modifier = itemModifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        "නම, නිලය, නිල අංකය හෝ දුරකථන අංකය සොයන්න...",
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search Station Contacts")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("station_contacts_search_input")
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(StationContactRoleFilter.entries) { filter ->
                    val count = when (filter) {
                        StationContactRoleFilter.ALL -> stationContacts.size
                        StationContactRoleFilter.LEADERSHIP -> stationContacts.count { it.role == "OIC" || it.role == "ADMIN_BRANCH" }
                        StationContactRoleFilter.OFFICERS -> stationContacts.count { it.role == "OFFICER" }
                    }
                    FilterChip(
                        selected = roleFilter == filter,
                        onClick = { onRoleFilterChange(filter) },
                        label = {
                            Text(
                                text = "${filter.label} ($count)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                }
            }
        }
    }

    // 4. Empty State or Station Contact Cards
    if (filteredContacts.isEmpty()) {
        item(key = "station_contacts_empty") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = itemModifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ContactPhone,
                        contentDescription = null,
                        tint = PoliceThemeColors.mutedText,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (stationContacts.isEmpty()) {
                            "මෙම පොලිස් ස්ථානයට අනුමත නිලධාරී සබඳතා තවම නොමැත."
                        } else {
                            "සෙවුමට ගැළපෙන නිලධාරියෙකු හමු නොවීය."
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        items(filteredContacts, key = { "contact_${it.uid}" }) { contact ->
            Box(modifier = itemModifier.fillMaxWidth()) {
                StationContactCard(
                    contact = contact,
                    isMe = contact.uid == appUser?.uid,
                    onCall = { onCall(contact.phoneNumber) },
                    onWhatsApp = { onWhatsApp(contact.phoneNumber) },
                    onCopy = { onCopy(contact.phoneNumber, contact.displayName) },
                    onEditOwn = onEditOwn
                )
            }
        }
    }
}

@Composable
private fun StationContactCard(
    contact: AppUser,
    isMe: Boolean,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onCopy: () -> Unit,
    onEditOwn: () -> Unit
) {
    val roleLabel = when (contact.role) {
        "OIC" -> "ස්ථානාධිපති (OIC)"
        "ADMIN_BRANCH" -> "පාලන අංශය (Admin)"
        else -> "නිලධාරී"
    }
    val roleColor = when (contact.role) {
        "OIC" -> PoliceThemeColors.goldText
        "ADMIN_BRANCH" -> PoliceThemeColors.blueText
        else -> PoliceThemeColors.navyText
    }
    val roleBgColor = when (contact.role) {
        "OIC" -> BadgeGold.copy(alpha = 0.2f)
        "ADMIN_BRANCH" -> PoliceThemeColors.blueText.copy(alpha = 0.14f)
        else -> PoliceThemeColors.navyText.copy(alpha = 0.12f)
    }

    val displayName = contact.displayName.ifBlank { contact.email.ifBlank { "නිලධාරියා" } }
    val displayRank = contact.rank.ifBlank {
        when (contact.role) {
            "OIC" -> "OIC (ස්ථානාධිපති)"
            "ADMIN_BRANCH" -> "Admin Branch (පාලන අංශය)"
            else -> "Police Officer"
        }
    }
    val hasPhone = contact.phoneNumber.isNotBlank()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (contact.role == "OIC") BorderStroke(1.dp, BadgeGold.copy(alpha = 0.7f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("station_contact_card_${contact.uid}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(roleBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (contact.role) {
                            "OIC" -> Icons.Default.Security
                            "ADMIN_BRANCH" -> Icons.Default.AdminPanelSettings
                            else -> Icons.Default.Person
                        },
                        contentDescription = roleLabel,
                        tint = roleColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = displayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isMe) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = DutyActiveGreen.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "ඔබ",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PoliceThemeColors.greenText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = displayRank,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = roleColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "නිල අංකය: ${contact.badgeNumber.ifBlank { "සටහන් කර නැත" }}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = roleBgColor
                        ) {
                            Text(
                                text = roleLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = roleColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (isMe) {
                    IconButton(
                        onClick = onEditOwn,
                        modifier = Modifier.testTag("edit_own_contact_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit My Contact Number",
                            tint = PoliceThemeColors.navyText
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = hasPhone) { onCall() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = if (hasPhone) PoliceThemeColors.greenText else PoliceThemeColors.mutedText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasPhone) contact.phoneNumber else "දුරකථන අංකය සටහන් කර නැත",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (hasPhone) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (hasPhone) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onCall,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DutyActiveGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .testTag("call_contact_${contact.uid}")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ඇමතුම්", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalIconButton(
                            onClick = onWhatsApp,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color(0xFF059669),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("whatsapp_contact_${contact.uid}")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp", modifier = Modifier.size(17.dp))
                        }

                        OutlinedIconButton(
                            onClick = onCopy,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("copy_contact_${contact.uid}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Phone",
                                tint = PoliceThemeColors.navyText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditOwnStationContactDialog(
    initialPhone: String,
    initialBadge: String,
    initialRank: String,
    onDismiss: () -> Unit,
    onSave: (phone: String, badge: String, rank: String) -> Unit
) {
    var phone by remember { mutableStateOf(initialPhone) }
    var badge by remember { mutableStateOf(initialBadge) }
    var rank by remember { mutableStateOf(initialRank) }
    val isPhoneValid = phone.filter { it.isDigit() }.length >= 9

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("මගේ සබඳතා තොරතුරු යාවත්කාලීන කිරීම", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("දුරකථන අංකය *") },
                    placeholder = { Text("0771234567") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = phone.isNotBlank() && !isPhoneValid,
                    supportingText = {
                        if (phone.isNotBlank() && !isPhoneValid) {
                            Text("අවම ඉලක්කම් 9 ක වලංගු අංකයක් ඇතුළත් කරන්න.")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = badge,
                    onValueChange = { badge = it },
                    label = { Text("නිල අංකය (Badge / Reg No)") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rank,
                    onValueChange = { rank = it },
                    label = { Text("තනතුර (Rank)") },
                    leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(phone, badge, rank) },
                enabled = isPhoneValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PoliceNavyPrimary,
                    contentColor = Color.White
                )
            ) {
                Text("සුරකින්න", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("අවලංගු කරන්න")
            }
        }
    )
}
