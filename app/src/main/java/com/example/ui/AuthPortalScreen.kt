package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.BadgeGoldBright
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.ui.theme.PoliceThemeColors

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.AdminPanelSettings

@Composable
fun AuthPortalScreen(
    onSelectOIC: () -> Unit,
    onSelectAdminBranch: () -> Unit,
    onSelectOfficer: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Police Shield Emblem
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(BadgeGold, PoliceNavyDark)
                        )
                    ),
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
                fontWeight = FontWeight.Bold,
                color = PoliceThemeColors.navyText
            )

            Text(
                text = "ශ්‍රී ලංකා පොලීසිය",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = PoliceThemeColors.goldText
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "කරුණාකර ඔබගේ පිවිසුම් අංශය තෝරන්න\n(Please select your portal to continue)",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Option 1: OIC Portal
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectOIC() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = PoliceNavyPrimary
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(BadgeGold.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = BadgeGoldBright,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ස්ථානාධිපති පිවිසුම",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "OIC Chief Portal",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BadgeGoldBright
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "අනන්‍ය OIC අංකය, පාලන අංශ රහස් අංකය, නිලධාරීන් කළමනාකරණය හා සජීවී සිතියම",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.95f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Option 2: Administration Branch Portal (NEW)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectAdminBranch() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B) // Slate Dark
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BadgeGold.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(BadgeGold.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = BadgeGoldBright,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "පාලන අංශය පිවිසුම",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Administration Branch Portal",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BadgeGoldBright
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "රාජකාරි යෙදවීම (උපසේවා/මුරසංචාර), ඉදිරි දින රාජකාරි ලේඛනය සහ මෙස් ආහාර වාර්තා",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.95f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Option 3: Police Officer Portal
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectOfficer() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, PoliceThemeColors.navyText.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(PoliceThemeColors.navyText.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = PoliceThemeColors.navyText,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "පොලිස් නිලධාරී පිවිසුම",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Police Officer Portal",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PoliceThemeColors.navyText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "පැවරුණු රාජකාරි, මෙස් ආහාර, ගමන් සටහන්, කණ්ඩායම් රාජකාරි සහ සජීවී GPS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
