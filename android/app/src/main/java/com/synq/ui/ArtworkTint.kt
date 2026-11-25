package com.synq.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// In-memory cache for extracted artwork dominant colors across recompositions
private val artworkTintCache = ConcurrentHashMap<String, Color>()

/**
 * Returns an animated dominant tint [Color] for [imageUrl], falling back to
 * [fallbackColor] while loading or if unavailable.
 *
 * Runs bitmap extraction off the main thread (Dispatchers.IO) using Coil's imageLoader,
 * downsampling to a 32x32 bitmap for minimal CPU and memory overhead.
 */
@Composable
fun rememberArtworkTint(
    imageUrl: String?,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
): State<Color> {
    val context = LocalContext.current
    var extractedColor by remember(imageUrl) {
        mutableStateOf(if (imageUrl != null) artworkTintCache[imageUrl] ?: fallbackColor else fallbackColor)
    }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) {
            extractedColor = fallbackColor
            return@LaunchedEffect
        }

        val cached = artworkTintCache[imageUrl]
        if (cached != null) {
            extractedColor = cached
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(32, 32)
                    .allowHardware(false)
                    .build()

                val result = (context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable
                val bitmap: Bitmap? = result?.bitmap

                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                    var rSum = 0L
                    var gSum = 0L
                    var bSum = 0L
                    var count = 0
                    val stepX = (bitmap.width / 8).coerceAtLeast(1)
                    val stepY = (bitmap.height / 8).coerceAtLeast(1)

                    for (x in 0 until bitmap.width step stepX) {
                        for (y in 0 until bitmap.height step stepY) {
                            val pixel = bitmap.getPixel(x, y)
                            rSum += android.graphics.Color.red(pixel)
                            gSum += android.graphics.Color.green(pixel)
                            bSum += android.graphics.Color.blue(pixel)
                            count++
                        }
                    }

                    if (count > 0) {
                        val dominant = Color(
                            (rSum / count).toInt(),
                            (gSum / count).toInt(),
                            (bSum / count).toInt()
                        )
                        artworkTintCache[imageUrl] = dominant
                        withContext(Dispatchers.Main) {
                            extractedColor = dominant
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall back to fallbackColor on error or network failure
            }
        }
    }

    return animateColorAsState(
        targetValue = extractedColor,
        animationSpec = tween(durationMillis = 200),
        label = "artworkTint"
    )
}
