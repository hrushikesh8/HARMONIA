package org.harmonia.player.presentation.personalization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.touchTarget48

/**
 * Reusable, accessible Favorite toggle button.
 * Uses semantic theme colors and exposes Role.Checkbox with stateDescription semantics.
 */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 24.dp,
    contentDescription: String = if (isFavorite) "Favorite track, currently favorited" else "Favorite track, currently not favorited"
) {
    val colors = HarmoniaThemeTokens.colors
    val iconColor = if (isFavorite) colors.primarySignal else colors.textSecondary
    val icon = if (isFavorite) HarmoniaIcons.Favorite else HarmoniaIcons.FavoriteBorder

    IconButton(
        onClick = onToggleFavorite,
        enabled = enabled,
        modifier = modifier
            .touchTarget48()
            .semantics {
                role = Role.Checkbox
                this.contentDescription = contentDescription
                stateDescription = if (isFavorite) "Favorited" else "Not favorited"
                toggleableState = ToggleableState(isFavorite)
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // Announced via IconButton semantics container
            tint = iconColor,
            modifier = Modifier.size(size)
        )
    }
}

/**
 * Reusable, accessible 1-5 Star Rating bar.
 * Each interactive star strictly satisfies the minimum 48dp touch target constraint.
 */
@Composable
fun RatingBar(
    rating: Int?,
    onRatingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    starSize: Dp = 28.dp
) {
    val colors = HarmoniaThemeTokens.colors

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (star in 1..5) {
            val isSelected = rating != null && star <= rating
            val starColor = if (isSelected) colors.statusWarning else colors.textSecondary
            val starIcon = if (isSelected) HarmoniaIcons.Star else HarmoniaIcons.StarBorder
            val starLabel = "Rate $star star${if (star > 1) "s" else ""}"
            val starState = if (isSelected) "Selected" else "Not selected"

            val interactionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .touchTarget48()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = false, radius = 24.dp),
                        enabled = enabled,
                        onClick = { onRatingSelected(star) }
                    )
                    .semantics {
                        role = Role.RadioButton
                        this.contentDescription = starLabel
                        stateDescription = starState
                        selected = isSelected
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = starIcon,
                    contentDescription = null, // Handled by outer box semantics
                    tint = starColor,
                    modifier = Modifier.size(starSize)
                )
            }
        }
    }
}

/**
 * Accessible Modal Dialog for displaying and setting track star ratings (1 to 5).
 * Connects with PersonalizationViewModel rating actions (setRating / clearRating).
 */
@Composable
fun RatingDialog(
    currentRating: Int?,
    onRatingSelected: (Int) -> Unit,
    onClearRating: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    trackTitle: String? = null,
    artistName: String? = null
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = colors.surfaceElevated,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        modifier = modifier,
        title = {
            Text(
                text = "TRACK RATING",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = colors.primarySignal
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                if (!trackTitle.isNullOrBlank()) {
                    Text(
                        text = trackTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                if (!artistName.isNullOrBlank()) {
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(spacing.xSmall))

                Text(
                    text = if (currentRating != null) "$currentRating of 5 stars" else "Not rated",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (currentRating != null) colors.statusWarning else colors.textSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(spacing.small))

                RatingBar(
                    rating = currentRating,
                    onRatingSelected = { selectedStar ->
                        onRatingSelected(selectedStar)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .touchTarget48()
                    .semantics {
                        contentDescription = "Close rating dialog"
                    }
            ) {
                Text(
                    text = "DONE",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.primarySignal
                )
            }
        },
        dismissButton = {
            if (currentRating != null) {
                TextButton(
                    onClick = onClearRating,
                    modifier = Modifier
                        .touchTarget48()
                        .semantics {
                            contentDescription = "Clear track rating"
                        }
                ) {
                    Text(
                        text = "CLEAR RATING",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.statusError
                    )
                }
            }
        }
    )
}
