package com.synq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.synq.RootViewModel
import com.synq.navigation.Screen

private val AvatarGradient = Brush.linearGradient(
    listOf(Color(0xFF6366F1), Color(0xFFEC4899))
)

/**
 * Unified Header for Home, Music, Sports, and Library screens.
 * Ensures identical padding, spacing, font sizes, and in-header profile avatar across all tabs.
 */
@Composable
fun AppHeader(
    title: String,
    subtitle: String? = null,
    rootViewModel: RootViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
    showBrandLogo: Boolean = false
) {
    val isLoggedIn by rootViewModel.isLoggedIn.collectAsState(initial = false)
    val user by rootViewModel.user.collectAsState()
    val isDarkTheme by rootViewModel.isDarkTheme.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBrandLogo) {
                SynqLogo(
                    size = 38.dp,
                    showGlow = false
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                if (title.equals("synq", ignoreCase = true)) {
                    SynqBrandedText(
                        capitalized = title.firstOrNull()?.isUpperCase() ?: true,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                } else {
                    Text(
                        text = title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        ProfileAvatarMenu(
            isLoggedIn = isLoggedIn,
            userName = if (isLoggedIn) (user?.username ?: "User") else "Guest",
            planLabel = if (isLoggedIn) (user?.email ?: "Account & Profile") else "Tap to sign in",
            onProfileClick = {
                if (isLoggedIn) {
                    navController.navigate(Screen.Profile.route) { launchSingleTop = true }
                } else {
                    navController.navigate(Screen.Auth.route) { launchSingleTop = true }
                }
            },
            onSignInClick = {
                navController.navigate(Screen.Auth.route) { launchSingleTop = true }
            },
            isDarkTheme = isDarkTheme,
            onToggleTheme = { rootViewModel.toggleTheme() },
            onSettingsClick = {
                navController.navigate(Screen.Profile.route) { launchSingleTop = true }
            },
            onHistoryClick = {
                navController.navigate(Screen.ListeningHistory.route) { launchSingleTop = true }
            },
            onLogoutClick = {
                rootViewModel.promptSignOut()
            }
        )
    }
}

@Composable
private fun ProfileAvatarMenu(
    isLoggedIn: Boolean,
    userName: String,
    planLabel: String,
    onProfileClick: () -> Unit,
    onSignInClick: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val initial = if (isLoggedIn) userName.firstOrNull()?.uppercase() ?: "U" else "?"

    Box(modifier = modifier) {
        GradientAvatar(initial = initial, onClick = { expanded = true })

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 8.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(220.dp)
        ) {
            // Header row: tapping it opens the Profile or Auth screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = false; onProfileClick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GradientAvatar(initial = initial)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = userName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = planLabel,
                        color = Color(0xFFEC4899),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            )
            Spacer(Modifier.height(4.dp))

            ProfileMenuItem(
                label = if (isDarkTheme) "Light Mode" else "Dark Mode",
                icon = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode
            ) { expanded = false; onToggleTheme() }
            if (isLoggedIn) {
                ProfileMenuItem("Settings", Icons.Default.Settings) { expanded = false; onSettingsClick() }
                ProfileMenuItem("Listening History", Icons.Default.Refresh) { expanded = false; onHistoryClick() }
                ProfileMenuItem("Log Out", Icons.Default.ExitToApp, tint = Color(0xFFEF4444)) {
                    expanded = false
                    onLogoutClick()
                }
            } else {
                ProfileMenuItem("Sign In", Icons.Default.AccountCircle, tint = MaterialTheme.colorScheme.primary) {
                    expanded = false
                    onSignInClick()
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    label: String,
    icon: ImageVector,
    tint: Color? = null,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = tint ?: MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        },
        onClick = onClick
    )
}

@Composable
private fun GradientAvatar(initial: String, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(AvatarGradient)
            .border(2.dp, Color.White.copy(alpha = 0.1f), CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
