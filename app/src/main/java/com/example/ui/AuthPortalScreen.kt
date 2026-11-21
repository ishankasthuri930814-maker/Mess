package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.BadgeGoldBright
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary

@Composable
fun AuthPortalScreen(
    isDarkTheme: Boolean = false,
    onSelectDarkMode: (Boolean) -> Unit = {},
    onSelectOIC: () -> Unit,
    onSelectAdminBranch: () -> Unit,
    onSelectOfficer: () -> Unit
) {
    val overlayBrush = if (isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xE0020617),
                Color(0xCC0B132B),
                Color(0xF2020617)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xEDF8FAFC),
                Color(0xDDE0F2FE),
                Color(0xF2F1F5F9)
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Atmospheric Backdrop Image
        Image(
            painter = painterResource(id = R.drawable.img_portal_bg),
            contentDescription = "Sri Lanka Police Portal Backdrop",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Adaptive Scrim Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayBrush)
        )

        // Main Scrollable Portal Selection (placed BEFORE the top theme pill in Z-order)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(54.dp))

            // Police Shield Emblem
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(BadgeGoldBright, PoliceNavyDark)
                        )
                    )
                    .border(2.5.dp, BadgeGoldBright.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "All In One Police",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDarkTheme) Color.White else PoliceNavyDark,
                textAlign = TextAlign.Center
            )

            Text(
                text = "ශ්‍රී ලංකා පොලීසිය",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkTheme) BadgeGoldBright else Color(0xFFB45309),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDarkTheme) Color.Black.copy(alpha = 0.42f) else PoliceNavyPrimary.copy(alpha = 0.12f),
                border = if (isDarkTheme) null else BorderStroke(1.dp, PoliceNavyPrimary.copy(alpha = 0.25f))
            ) {
                Text(
                    text = "කරුණාකර ඔබගේ පිවිසුම් අංශය තෝරන්න",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.95f) else PoliceNavyDark,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 1. OIC Chief Portal (Royal Gold-Indigo)
            PortalRoleCard(
                title = "ස්ථානාධිපති පිවිසුම",
                subtitle = "OIC Chief Portal",
                description = "අනන්‍ය OIC අංකය, පාලන අංශ රහස් අංකය, නිලධාරීන් කළමනාකරණය, සංවාද කුටිය හා සජීවී සිතියම",
                icon = Icons.Default.Security,
                gradient = if (isDarkTheme) {
                    Brush.horizontalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E3A8A)))
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFF1E3A8A), Color(0xFF2563EB)))
                },
                borderColor = BadgeGoldBright,
                accentColor = BadgeGoldBright,
                onClick = onSelectOIC,
                modifier = Modifier.testTag("portal_oic_card")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Administration Branch Portal (Emerald-Teal)
            PortalRoleCard(
                title = "පාලන අංශය පිවිසුම",
                subtitle = "Administration Branch Portal",
                description = "රාජකාරි යෙදවීම (උපසේවා/මුරසංචාර), ඉදිරි දින රාජකාරි ලේඛනය, දැනුම්දීම් සහ මෙස් ආහාර වාර්තා",
                icon = Icons.Default.AdminPanelSettings,
                gradient = if (isDarkTheme) {
                    Brush.horizontalGradient(listOf(Color(0xFF064E3B), Color(0xFF047857)))
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFF047857), Color(0xFF0D9488)))
                },
                borderColor = Color(0xFF34D399),
                accentColor = Color(0xFFA7F3D0),
                onClick = onSelectAdminBranch,
                modifier = Modifier.testTag("portal_admin_card")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Police Officer Portal (Vibrant Cobalt-Azure)
            PortalRoleCard(
                title = "පොලිස් නිලධාරී පිවිසුම",
                subtitle = "Police Officer Portal",
                description = "පැවරුණු රාජකාරි, මෙස් ආහාර, ගමන් සටහන්, කණ්ඩායම් රාජකාරි, සංවාද කුටිය සහ සජීවී GPS",
                icon = Icons.Default.Person,
                gradient = if (isDarkTheme) {
                    Brush.horizontalGradient(listOf(Color(0xFF1E40AF), Color(0xFF0284C7)))
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF38BDF8)))
                },
                borderColor = Color(0xFF7DD3FC),
                accentColor = Color(0xFFE0F2FE),
                onClick = onSelectOfficer,
                modifier = Modifier.testTag("portal_officer_card")
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Corner Light / Dark Theme Switcher - declared LAST with zIndex(20f) so scroll Column never intercepts taps
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDarkTheme) Color(0xF01E293B) else Color(0xF8FFFFFF),
            border = BorderStroke(1.5.dp, BadgeGold.copy(alpha = 0.85f)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(20f)
                .statusBarsPadding()
                .padding(top = 10.dp, end = 14.dp)
                .testTag("portal_theme_toggle_pill")
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeOptionChip(
                    label = "දිවා",
                    icon = Icons.Default.LightMode,
                    selected = !isDarkTheme,
                    isDarkSurface = isDarkTheme,
                    onClick = { onSelectDarkMode(false) },
                    modifier = Modifier.testTag("portal_light_mode_btn")
                )
                ThemeOptionChip(
                    label = "රාත්‍රී",
                    icon = Icons.Default.DarkMode,
                    selected = isDarkTheme,
                    isDarkSurface = isDarkTheme,
                    onClick = { onSelectDarkMode(true) },
                    modifier = Modifier.testTag("portal_dark_mode_btn")
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    isDarkSurface: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = when {
        selected -> PoliceNavyDark
        isDarkSurface -> Color.White.copy(alpha = 0.9f)
        else -> PoliceNavyDark.copy(alpha = 0.8f)
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (selected) BadgeGoldBright else Color.Transparent,
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(22.dp))
            .semantics { this.selected = selected }
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun PortalRoleCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    gradient: Brush,
    borderColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.8.dp, borderColor.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.5.dp, accentColor.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.92f),
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
