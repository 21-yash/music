package com.synq.features.profile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.synq.ui.SynqBrandedText
import com.synq.ui.SynqLogo
import com.synq.ui.components.SectionHeader
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import com.synq.ui.theme.PinkGradient
import com.synq.ui.theme.PinkPrimary
import com.synq.ui.theme.Spacing

private enum class AvatarColorPalette(
    val label: String,
    val gradient: Brush,
    val primaryColor: Color
) {
    OCEAN_BLUE(
        label = "Ocean Blue",
        gradient = Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF1D4ED8))),
        primaryColor = Color(0xFF0EA5E9)
    ),
    ROYAL_INDIGO(
        label = "Royal Indigo",
        gradient = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF3730A3))),
        primaryColor = Color(0xFF6366F1)
    ),
    EMERALD(
        label = "Emerald",
        gradient = Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF047857))),
        primaryColor = Color(0xFF10B981)
    ),
    SUNSET_ORANGE(
        label = "Sunset Orange",
        gradient = Brush.linearGradient(listOf(Color(0xFFF97316), Color(0xFFC2410C))),
        primaryColor = Color(0xFFF97316)
    ),
    CYBER_PURPLE(
        label = "Cyber Purple",
        gradient = Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFF6B21A8))),
        primaryColor = Color(0xFFA855F7)
    ),
    NEON_ROSE(
        label = "Neon Rose",
        gradient = Brush.linearGradient(listOf(Color(0xFFFF2D7A), Color(0xFFBE185D))),
        primaryColor = Color(0xFFFF2D7A)
    )
}

private enum class AvatarIconType(
    val label: String,
    val icon: ImageVector?
) {
    INITIAL("Initial", null),
    HEADPHONES("Headphones", Icons.Default.Headphones),
    MUSIC("Music", Icons.Default.MusicNote),
    SPARKLE("Sparkles", Icons.Default.AutoAwesome),
    STAR("Star", Icons.Default.Star)
}

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {},
) {
    val user by viewModel.user.collectAsState()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val context = LocalContext.current

    val profilePrefs = remember(context) {
        context.getSharedPreferences("synq_profile_prefs", Context.MODE_PRIVATE)
    }

    var selectedPaletteIndex by remember {
        mutableIntStateOf(profilePrefs.getInt("avatar_palette_index", 0)) // 0 = Ocean Blue (non-pink default)
    }
    var selectedIconTypeIndex by remember {
        mutableIntStateOf(profilePrefs.getInt("avatar_icon_index", 0))
    }

    val currentPalette = AvatarColorPalette.entries.getOrElse(selectedPaletteIndex) { AvatarColorPalette.OCEAN_BLUE }
    val currentIconType = AvatarIconType.entries.getOrElse(selectedIconTypeIndex) { AvatarIconType.INITIAL }

    var showSignOutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAvatarCustomizer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadCacheSize()
    }

    // ─── Avatar Customizer Dialog ───
    if (showAvatarCustomizer) {
        AlertDialog(
            onDismissRequest = { showAvatarCustomizer = false },
            title = {
                Text(
                    text = "Customize Avatar",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Live Avatar Preview
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(currentPalette.gradient),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentIconType.icon != null) {
                            Icon(
                                imageVector = currentIconType.icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        } else {
                            Text(
                                text = (user?.username ?: "U").take(1).uppercase(),
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // Palette Selection
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Color Theme",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarColorPalette.entries.forEachIndexed { index, palette ->
                                val isSelected = index == selectedPaletteIndex
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .pressable(
                                            onClick = {
                                                selectedPaletteIndex = index
                                                profilePrefs.edit().putInt("avatar_palette_index", index).apply()
                                            },
                                            pressedScale = MotionTokens.ChipPressScale
                                        )
                                        .clip(CircleShape)
                                        .background(palette.gradient)
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Icon / Initial Style Selection
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Display Style",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarIconType.entries.forEachIndexed { index, type ->
                                val isSelected = index == selectedIconTypeIndex
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .pressable(
                                            onClick = {
                                                selectedIconTypeIndex = index
                                                profilePrefs.edit().putInt("avatar_icon_index", index).apply()
                                            },
                                            pressedScale = MotionTokens.ChipPressScale
                                        )
                                        .clip(MaterialTheme.shapes.small)
                                        .background(
                                            if (isSelected) currentPalette.primaryColor.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) currentPalette.primaryColor else Color.Transparent,
                                            MaterialTheme.shapes.small
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (type.icon != null) {
                                        Icon(
                                            imageVector = type.icon,
                                            contentDescription = type.label,
                                            tint = if (isSelected) currentPalette.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = (user?.username ?: "U").take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (isSelected) currentPalette.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarCustomizer = false }) {
                    Text("Done", color = PinkPrimary)
                }
            }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = {
                Text(
                    text = "Sign Out",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to sign out of your account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = {
                Text(
                    text = "Clear Music Cache?",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "This will remove all locally cached audio (${formatCacheBytes(cacheSizeBytes)}). Repeated playback of songs will stream from the network again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearCache { success ->
                            com.synq.ui.toast.SynqToastManager.show(
                                if (success) "Music cache cleared" else "Failed to clear cache",
                                if (success) com.synq.ui.toast.SynqToastType.SUCCESS else com.synq.ui.toast.SynqToastType.ERROR
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Cache")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Appearance (Theme Selection) Dialog
    if (showAppearanceDialog) {
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                        contentDescription = null,
                        tint = PinkPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Select your preferred app theme",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))

                    // Dark Theme Option
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable(
                                onClick = {
                                    viewModel.setDarkTheme(true)
                                    showAppearanceDialog = false
                                },
                                pressedScale = MotionTokens.RowPressScale
                            )
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (isDarkTheme) MaterialTheme.colorScheme.surfaceContainerHigh
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            .border(
                                1.dp,
                                if (isDarkTheme) PinkPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                MaterialTheme.shapes.medium
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.DarkMode,
                                contentDescription = null,
                                tint = if (isDarkTheme) PinkPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Dark Theme",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Sleek dark theme optimized for OLED",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = isDarkTheme,
                                onClick = {
                                    viewModel.setDarkTheme(true)
                                    showAppearanceDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = PinkPrimary)
                            )
                        }
                    }

                    // Light Theme Option
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable(
                                onClick = {
                                    viewModel.setDarkTheme(false)
                                    showAppearanceDialog = false
                                },
                                pressedScale = MotionTokens.RowPressScale
                            )
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (!isDarkTheme) MaterialTheme.colorScheme.surfaceContainerHigh
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            .border(
                                1.dp,
                                if (!isDarkTheme) PinkPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                MaterialTheme.shapes.medium
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.LightMode,
                                contentDescription = null,
                                tint = if (!isDarkTheme) PinkPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Light Theme",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Crisp and clear high-contrast look",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = !isDarkTheme,
                                onClick = {
                                    viewModel.setDarkTheme(false)
                                    showAppearanceDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = PinkPrimary)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppearanceDialog = false }) {
                    Text("Close", color = PinkPrimary)
                }
            }
        )
    }

    // Cache Option Dialog
    if (showCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCacheDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(currentPalette.gradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Offline Stream Cache",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Cached songs are stored locally on your device for instant offline playback with zero data consumption.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Current Usage",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${formatCacheBytes(cacheSizeBytes)} / 2.0 GB",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            val progress = (cacheSizeBytes.toFloat() / (2L * 1024 * 1024 * 1024)).coerceIn(0.01f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = PinkPrimary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showClearCacheDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Audio Cache", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCacheDialog = false }) {
                    Text("Done", color = PinkPrimary)
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SynqLogo(size = 32.dp, showGlow = false)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "About Synq",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SynqBrandedText(
                            capitalized = true,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "v0.1.0 Beta",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "Your unified companion for high-fidelity music streaming, real-time Jam listening sessions, and live cricket match coverage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Text(
                        text = "Key Features",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Lossless audio streaming & offline cache\n• Collaborative live Jam listening rooms\n• Ball-by-ball cricket match tracker & commentary\n• Synced lyrics & automated sleep timer",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Text(
                        text = "Designed by Yash",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = PinkPrimary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Got It", color = PinkPrimary)
                }
            }
        )
    }

    val settingsItems = remember(isDarkTheme, cacheSizeBytes, selectedPaletteIndex, selectedIconTypeIndex) {
        listOf(
            SettingsItem(
                title = "Appearance",
                subtitle = if (isDarkTheme) "Dark Theme" else "Light Theme",
                icon = if (isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                onClick = { showAppearanceDialog = true }
            ),
            SettingsItem(
                title = "Customize Avatar",
                subtitle = "${currentPalette.label} • ${currentIconType.label}",
                icon = Icons.Filled.Palette,
                onClick = { showAvatarCustomizer = true }
            ),
            SettingsItem(
                title = "Cache",
                subtitle = "${formatCacheBytes(cacheSizeBytes)} cached",
                icon = Icons.Filled.Storage,
                onClick = { showCacheDialog = true }
            ),
            SettingsItem(
                title = "Notifications",
                subtitle = "Score alerts & release updates",
                icon = Icons.Filled.Notifications,
                onClick = {
                    com.synq.ui.toast.SynqToastManager.show(
                        "Notifications are active for match & music updates",
                        com.synq.ui.toast.SynqToastType.INFO
                    )
                }
            ),
            SettingsItem(
                title = "Privacy & Security",
                subtitle = "Local storage & app permissions",
                icon = Icons.Filled.Security,
                onClick = {
                    com.synq.ui.toast.SynqToastManager.show(
                        "All streaming data is stored securely on device",
                        com.synq.ui.toast.SynqToastType.INFO
                    )
                }
            ),
            SettingsItem(
                title = "About",
                subtitle = "v0.1.0 Beta",
                icon = Icons.Filled.Info,
                onClick = { showAboutDialog = true }
            ),
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 240.dp),
        ) {
            // ─── User Info Card with Customizable Non-Pink Avatar ───
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            MaterialTheme.shapes.large
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Avatar (Customizable gradient + icon/initial with edit badge)
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .pressable(
                                    onClick = { showAvatarCustomizer = true },
                                    pressedScale = MotionTokens.CardPressScale
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(currentPalette.gradient),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentIconType.icon != null) {
                                    Icon(
                                        imageVector = currentIconType.icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                } else {
                                    Text(
                                        text = (user?.username ?: "U").take(1).uppercase(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            // Subtle edit indicator badge
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(currentPalette.primaryColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit avatar",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user?.username ?: "User",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = user?.email ?: "Account",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ─── Settings Section Header ───
            item {
                SectionHeader(
                    title = "Settings",
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = Spacing.sectionGap,
                        bottom = Spacing.headerToContent
                    )
                )
            }

            // ─── Settings Rows ───
            items(settingsItems.size) { index ->
                SettingsRow(item = settingsItems[index])
                if (index < settingsItems.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    )
                }
            }

            // ─── Logout Row ───
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .pressable(
                            onClick = { showSignOutDialog = true },
                            pressedScale = MotionTokens.CardPressScale
                        )
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.16f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                            MaterialTheme.shapes.large
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Sign Out",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // ─── Branding Footer ───
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SynqBrandedText(
                        capitalized = true,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        suffix = " v0.1.0",
                        synColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private data class SettingsItem(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun SettingsRow(item: SettingsItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(
                onClick = item.onClick,
                pressedScale = MotionTokens.RowPressScale
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = PinkPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!item.subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun formatCacheBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        String.format(java.util.Locale.US, "%.2f GB", mb / 1024)
    } else {
        String.format(java.util.Locale.US, "%.1f MB", mb)
    }
}
