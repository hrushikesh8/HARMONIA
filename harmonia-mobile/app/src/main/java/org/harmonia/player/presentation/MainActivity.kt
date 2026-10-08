package org.harmonia.player.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.harmonia.player.design.theme.AcousticCharcoal
import org.harmonia.player.design.theme.AcousticTeal
import org.harmonia.player.design.theme.BorderSlate
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaTheme
import org.harmonia.player.design.theme.MutedSlate
import org.harmonia.player.design.theme.ObsidianSpace
import org.harmonia.player.design.theme.PureSignalWhite
import org.harmonia.player.design.theme.StudioSlate
import org.harmonia.player.presentation.home.HomeScreen
import org.harmonia.player.presentation.library.LibraryScreen
import org.harmonia.player.presentation.navigation.HarmoniaNavDestination
import org.harmonia.player.presentation.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HarmoniaTheme {
                MainAppContainer()
            }
        }
    }
}

enum class NavigationTab(
    val title: String,
    val icon: ImageVector,
    val destination: HarmoniaNavDestination
) {
    HOME("Home", HarmoniaIcons.Home, HarmoniaNavDestination.Home),
    LIBRARY("Library", HarmoniaIcons.Library, HarmoniaNavDestination.Library),
    SETTINGS("Settings", HarmoniaIcons.Settings, HarmoniaNavDestination.Settings)
}

@Composable
fun MainAppContainer() {
    var selectedTab by rememberSaveable { mutableStateOf(NavigationTab.HOME) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = AcousticCharcoal,
                contentColor = PureSignalWhite
            ) {
                NavigationTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(text = tab.title)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AcousticTeal,
                            selectedTextColor = AcousticTeal,
                            indicatorColor = StudioSlate,
                            unselectedIconColor = MutedSlate,
                            unselectedTextColor = MutedSlate
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianSpace)
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavigationTab.HOME -> HomeScreen(
                    onNavigateToLibrary = { selectedTab = NavigationTab.LIBRARY }
                )
                NavigationTab.LIBRARY -> LibraryScreen()
                NavigationTab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
