package com.synq.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.synq.ui.GlassAccent
import com.synq.ui.glassContentColors
import com.synq.ui.glassSurface

/**
 * Floating glass bottom navigation bar (prototype style).
 *
 * Shows all items from [Screen.bottomNavItems]: icon stacked over a small label.
 * The active tab gets a primary-colored label and an indigo icon that lifts 2dp.
 * Navigation behaviour is unchanged.
 */
@Composable
fun AppBottomNavBar(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var lastMusicClickTime by remember { mutableLongStateOf(0L) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .glassSurface(RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Screen.bottomNavItems.forEach { screen ->
            val isSelected = currentRoute == screen.route

            NavBarItem(
                label = screen.label,
                icon = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                selected = isSelected,
                onClick = {
                    if (screen == Screen.Music) {
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastMusicClickTime < 400L) {
                            lastMusicClickTime = 0L
                            navController.navigate(Screen.Search.route) {
                                launchSingleTop = true
                            }
                            return@NavBarItem
                        }
                        lastMusicClickTime = now
                    }

                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NavBarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = glassContentColors()
    val lift by animateDpAsState(
        targetValue = if (selected) (-2).dp else 0.dp,
        label = "navIconLift"
    )

    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) GlassAccent else colors.muted,
            modifier = Modifier
                .size(22.dp)
                .offset(y = lift)
        )
        Text(
            text = label,
            color = if (selected) colors.primary else colors.muted,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}