package com.musicsportsapp.features.music.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicsportsapp.features.music.domain.model.Song
import com.musicsportsapp.ui.isAppDark

@Composable
fun SongItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onLike: (() -> Unit)? = null
) {
    val isDark = isAppDark()
    val titleColor = if (isDark) Color.White else Color(0xFF0F172A)
    val artistColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.imageUrl,
            contentDescription = "Album art for ${song.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.primaryArtistName,
                style = MaterialTheme.typography.bodyMedium,
                color = artistColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        var showOptions by remember { mutableStateOf(false) }

        IconButton(onClick = { showOptions = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF64748B)
            )
        }

        if (showOptions) {
            SongOptionsSheet(
                song = song,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onLike = onLike,
                onDismiss = { showOptions = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    song: Song,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onLike: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isDark = isAppDark()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1E1624) else Color(0xFFFFFFFF),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.15f)
                    )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.primaryArtistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(8.dp))
            
            SongOptionItem(
                icon = Icons.Default.PlayArrow,
                text = "Play Next",
                isDark = isDark,
                onClick = {
                    onPlayNext?.invoke()
                    Toast.makeText(context, "Added to play next", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )
            SongOptionItem(
                icon = Icons.Default.List,
                text = "Add to Queue",
                isDark = isDark,
                onClick = {
                    onAddToQueue?.invoke()
                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )
            SongOptionItem(
                icon = Icons.Default.FavoriteBorder,
                text = "Like Song",
                isDark = isDark,
                onClick = {
                    onLike?.invoke()
                    onDismiss()
                }
            )
            SongOptionItem(
                icon = Icons.Default.Share,
                text = "Share Song",
                isDark = isDark,
                onClick = {
                    clipboardManager.setText(AnnotatedString("${song.title} - ${song.primaryArtistName}"))
                    Toast.makeText(context, "Copied song info to clipboard", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color(0xFF2E2436) else Color(0xFFF1F5F9)
                ),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = "Close",
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SongOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isDark: Boolean,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isDark) Color(0xFFFF2A5F) else Color(0xFFFF2A5F),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = text,
            color = if (isDark) Color.White else Color(0xFF0F172A),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
