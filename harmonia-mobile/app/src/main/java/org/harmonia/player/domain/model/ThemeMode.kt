package org.harmonia.player.domain.model

/** How the app picks between the dark and light theme. */
enum class ThemeMode {
    /** Follow the phone's dark mode setting. */
    SYSTEM,
    DARK,
    LIGHT;

    fun resolveDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        DARK -> true
        LIGHT -> false
    }
}
