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
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.synq.RootViewModel
import com.synq.navigation.Screen
import com.synq.ui.theme.PinkGradientHero
import com.synq.ui.theme.PinkPrimary
import com.synq.ui.theme.SemanticLive
import com.synq.ui.theme.Spacing

/**
 * Unified Header for Home, Music, Sports, and Library screens (§4.6, UI-B).
 *
 * - Screen title: headlineMedium (24sp Bold)
 * - Subtitle: bodySmall (13sp Normal, onSurfaceVariant)
 * - Header padding: 20dp horizontal, 12dp top, 8dp bottom
 * - Logo size: 44dp unchanged (§3.2)
 */
@Composable
fun AppHeader(
    title: String,
    subtitle: String? = null,
    rootViewModel: RootViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
    showBrandLogo: Boolean = true
) {
    val user by rootViewModel.user.collectAsState()
    val isDarkTheme by rootViewModel.isDarkTheme.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                top = Spacing.headerTop,
                bottom = Spacing.headerBottom
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBrandLogo) {
            Box {
                SynqLogo(
                    size = 44.dp,
                    showGlow = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(44.dp * 0.22f))
                        .clickable { menuExpanded = true }
                )

                AppLogoMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    userName = user?.username ?: "User",
                    planLabel = user?.email ?: "Account & Profile",
                    onProfileClick = {
                        navController.navigate(Screen.Profile.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { rootViewModel.toggleTheme() },
                    onSettingsClick = {
                        navController.navigate(Screen.Profile.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onHistoryClick = {
                        navController.navigate(Screen.ListeningHistory.route) { launchSingleTop = true }
                    },
                    onLogoutClick = {
                        rootViewModel.promptSignOut()
                    }
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            if (title.equals("synq", ignoreCase = true)) {
                SynqBrandedText(
                    capitalized = title.firstOrNull()?.isUpperCase() ?: true,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    style = MaterialTheme.typography.headlineMedium
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AppLogoMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    userName: String,
    planLabel: String,
    onProfileClick: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initial = userName.firstOrNull()?.uppercase() ?: "U"

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 8.dp),
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.width(230.dp)
    ) {
        // Header row: tapping it opens the Profile screen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDismiss(); onProfileClick() }
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientAvatar(initial = initial)
            Spacer(Modifier.width(Spacing.s))
            Column {
                Text(
                    text = userName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = planLabel,
                    color = PinkPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            Modifier
                .padding(horizontal = Spacing.m)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )
        Spacer(Modifier.height(Spacing.xxs))

        ProfileMenuItem(
            label = if (isDarkTheme) "Light Mode" else "Dark Mode",
            icon = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode
        ) { onDismiss(); onToggleTheme() }

        ProfileMenuItem("Settings", Icons.Default.Settings) { onDismiss(); onSettingsClick() }
        ProfileMenuItem("Listening History", Icons.Default.Refresh) { onDismiss(); onHistoryClick() }
        ProfileMenuItem("Log Out", Icons.Default.ExitToApp, tint = SemanticLive) {
            onDismiss()
            onLogoutClick()
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
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
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
            .background(PinkGradientHero)
            .border(1.5.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge
        )
    }
}
