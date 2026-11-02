package org.harmonia.player.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.harmonia.player.data.local.ArtworkProvider
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens

/**
 * Reusable artwork presentation component.
 * Adheres strictly to Harmonia Core SHA-256 artwork hash identity.
 * 
 * Behavior:
 * 1. Checks in-memory cache synchronously (instant render on cache hit).
 * 2. On cache miss, loads bitmap asynchronously on Dispatchers.IO.
 * 3. Falls back gracefully to Harmonia geometric gradient placeholder on missing or corrupt artwork.
 * 4. Never exposes filesystem paths or raw files to Composables.
 */
@Composable
fun HarmoniaArtwork(
    artworkHash: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    cornerRadius: Dp = 8.dp,
    placeholderIcon: ImageVector = HarmoniaIcons.MusicNote,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val artworkProvider = remember(context) { ArtworkProvider(context) }

    // Instant check from LRU memory cache
    var bitmap by remember(artworkHash) {
        mutableStateOf<Bitmap?>(artworkProvider.getCachedBitmap(artworkHash))
    }

    LaunchedEffect(artworkHash) {
        if (bitmap == null && !artworkHash.isNullOrBlank()) {
            val loaded = withContext(Dispatchers.IO) {
                artworkProvider.loadBitmap(artworkHash)
            }
            bitmap = loaded
        }
    }

    val colors = HarmoniaThemeTokens.colors

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null && !currentBitmap.isRecycled) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Harmonia geometric acoustic gradient fallback (Obsidian & Warm Analog)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colors.surfaceElevated,
                                colors.surfaceContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = contentDescription,
                    tint = colors.secondarySignal.copy(alpha = 0.7f),
                    modifier = Modifier.size(size * 0.45f)
                )
            }
        }
    }
}
