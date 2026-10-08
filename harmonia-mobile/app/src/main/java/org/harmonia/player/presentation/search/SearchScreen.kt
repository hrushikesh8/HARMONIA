package org.harmonia.player.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.AcousticCharcoal
import org.harmonia.player.design.theme.AcousticTeal
import org.harmonia.player.design.theme.BorderSlate
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.MutedSlate
import org.harmonia.player.design.theme.ObsidianSpace
import org.harmonia.player.design.theme.PureSignalWhite
import org.harmonia.player.domain.model.Track

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(),
    onTrackClick: (Track) -> Unit = {}
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianSpace)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "SEARCH",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                fontSize = 26.sp
            ),
            color = PureSignalWhite
        )
        Text(
            text = "LOCAL CATALOG INDEX",
            style = MaterialTheme.typography.labelSmall,
            color = AcousticTeal
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.onQueryChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Search input field" },
            placeholder = { Text("Search tracks, albums, artists, playlists...", color = MutedSlate) },
            leadingIcon = {
                Icon(imageVector = HarmoniaIcons.Search, contentDescription = null, tint = AcousticTeal)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AcousticTeal,
                unfocusedBorderColor = BorderSlate,
                focusedContainerColor = AcousticCharcoal,
                unfocusedContainerColor = AcousticCharcoal,
                cursorColor = AcousticTeal,
                focusedTextColor = PureSignalWhite,
                unfocusedTextColor = PureSignalWhite
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (query.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = HarmoniaIcons.Search,
                        contentDescription = null,
                        tint = MutedSlate,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Type to search local catalog", style = MaterialTheme.typography.bodyMedium, color = MutedSlate)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                if (uiState.matchingTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "TRACKS (${uiState.matchingTracks.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = AcousticTeal,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(uiState.matchingTracks, key = { "trk_${it.id}" }) { track ->
                        Text(
                            text = "${track.title} • ${track.artistName ?: "Unknown"}",
                            color = PureSignalWhite,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }

                if (uiState.matchingAlbums.isNotEmpty()) {
                    item {
                        Text(
                            text = "ALBUMS (${uiState.matchingAlbums.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = AcousticTeal,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(uiState.matchingAlbums, key = { "alb_${it.id}" }) { album ->
                        Text(
                            text = "${album.title} • ${album.artistName ?: "Unknown"}",
                            color = PureSignalWhite,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
