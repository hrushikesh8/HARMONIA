package org.harmonia.player.presentation.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.harmonia.player.data.local.ArtworkProvider
import org.harmonia.player.design.theme.AcousticCharcoal
import org.harmonia.player.design.theme.AcousticTeal
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.MutedSlate
import org.harmonia.player.design.theme.StudioSlate

/**
 * Reusable artwork presentation component.
 * Adheres strictly to Harmonia Core SHA-256 artwork hash identity.
 * Clearly separates artwork identity from on-disk byte availability.
 * When local artwork file is present, renders the bitmap.
 * Otherwise, renders the bespoke Harmonia geometric gradient placeholder.
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
    val artworkFile = remember(artworkHash) { artworkProvider.getArtworkFile(artworkHash) }

    val bitmap = remember(artworkFile) {
        artworkFile?.let { file ->
            try {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Elegant geometric acoustic gradient fallback
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AcousticCharcoal,
                                StudioSlate
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = contentDescription,
                    tint = AcousticTeal.copy(alpha = 0.7f),
                    modifier = Modifier.size(size * 0.45f)
                )
            }
        }
    }
}
