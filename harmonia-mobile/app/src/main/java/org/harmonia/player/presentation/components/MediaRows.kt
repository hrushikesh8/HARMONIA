package org.harmonia.player.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.personalization.FavoriteButton
import org.harmonia.player.presentation.util.TrackDisplayUtils

/*
 * Shared list rows in the Apple Music style: no card or border, artwork on the left, a regular
 * weight title and a grey second line. Used by Library categories and Search results so every
 * list in the app looks the same.
 */

private val RowShape = RoundedCornerShape(8.dp)

/** Album grids fit as many columns as allow at least this width: 2 on a phone, 4-5 sideways or on a tablet. */
val AlbumGridMinCellWidth = 150.dp

/** "Artist · Album", or a fallback when the file has no tags. */
internal fun songSubtitle(track: Track): String =
    listOfNotNull(track.artistName, track.albumTitle).joinToString(" · ").ifEmpty { "Unknown artist" }

/** "Album · Artist · 2019" for album rows in search results. */
internal fun albumSubtitle(album: Album): String =
    listOfNotNull("Album", album.artistName, album.year?.takeIf { it > 0 }?.toString()).joinToString(" · ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = songSubtitle(track),
    statusText: String? = null,
    isStatusError: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val title = TrackDisplayUtils.formatDisplayTitle(track)
    val dimmed = statusText != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .combinedClickable(onClick = onClick, onLongClick = onMenuClick)
            .semantics {
                contentDescription = buildString {
                    append("Play $title by ${track.artistName ?: "Unknown"}")
                    if (statusText != null) append(" ($statusText)")
                }
            }
            .heightIn(min = 60.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HarmoniaArtwork(
            artworkHash = track.artworkHash ?: track.localFile?.fileHash,
            size = 48.dp,
            cornerRadius = 6.dp,
            contentDescription = null,
            modifier = if (dimmed) Modifier.graphicsLayer(alpha = 0.45f) else Modifier
        )
        Spacer(modifier = Modifier.width(spacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (dimmed) colors.textPrimary.copy(alpha = 0.5f) else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (statusText != null) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isStatusError) colors.statusError else colors.statusWarning,
                    maxLines = 1
                )
            } else {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onToggleFavorite != null) {
            FavoriteButton(isFavorite = isFavorite, onToggleFavorite = onToggleFavorite)
        }
        if (onMenuClick != null) {
            TrackMenuButton(trackTitle = title, onClick = onMenuClick)
        }
    }
}

/** A row with square artwork, a title and a subtitle, opening a page (album, playlist, folder). */
@Composable
fun CollectionListRow(
    title: String,
    subtitle: String,
    artworkHash: String?,
    placeholderIcon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    showChevron: Boolean = true
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            }
            .heightIn(min = 68.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HarmoniaArtwork(
            artworkHash = artworkHash,
            size = 56.dp,
            cornerRadius = if (circular) 28.dp else 6.dp,
            placeholderIcon = placeholderIcon,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(spacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showChevron) {
            Icon(
                imageVector = HarmoniaIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textDisabled,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ArtistListRow(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String = "") {
    CollectionListRow(
        title = artist.name,
        subtitle = subtitle,
        artworkHash = artist.artworkHash,
        placeholderIcon = HarmoniaIcons.Person,
        description = "Artist ${artist.name}",
        onClick = onClick,
        modifier = modifier,
        circular = true
    )
}

@Composable
fun AlbumListRow(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CollectionListRow(
        title = album.title,
        subtitle = albumSubtitle(album),
        artworkHash = album.artworkHash,
        placeholderIcon = HarmoniaIcons.Album,
        description = "Album ${album.title} by ${album.artistName ?: "Unknown"}",
        onClick = onClick,
        modifier = modifier
    )
}

/** Album tile for grids: cover, title and artist, no card behind it. */
@Composable
fun AlbumGridItem(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HarmoniaThemeTokens.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Album ${album.title} by ${album.artistName ?: "Unknown"}"
            },
        verticalArrangement = Arrangement.Top
    ) {
        HarmoniaArtwork(
            artworkHash = album.artworkHash,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            size = 180.dp,
            cornerRadius = 10.dp,
            placeholderIcon = HarmoniaIcons.Album,
            contentDescription = null
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artistName ?: "Unknown artist",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}
