package com.synq.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.synq.ui.theme.PinkDeep
import com.synq.ui.theme.PinkPrimary
import com.synq.ui.theme.Spacing

/**
 * Unified SectionHeader composable (§5).
 * Replaces the 3 private copies across Home, Library, and Search.
 *
 * - Title: titleLarge (18sp Bold)
 * - Subtitle: bodySmall (13sp Normal, onSurfaceVariant)
 * - Action text: labelMedium (12sp Medium), PinkPrimary on dark / PinkDeep on light
 * - Section rhythm: 24dp top gap, 12dp bottom gap to content, 20dp horizontal padding
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(
        start = Spacing.screenHorizontal,
        end = Spacing.screenHorizontal,
        top = Spacing.sectionGap,
        bottom = Spacing.headerToContent
    )
) {
    val actionColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) PinkPrimary else PinkDeep

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!actionText.isNullOrBlank() && onActionClick != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelMedium,
                color = actionColor,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}
