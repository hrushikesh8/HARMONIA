package org.harmonia.player.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal const val TARGET_PACKAGE = "org.harmonia.player"

/**
 * Records which code runs on launch and on the main screens, so the app ships with it
 * pre-compiled. Run with: ./gradlew :app:generateBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        visitMainScreens()
    }
}

/** Home, then Search, then Library and its Songs list: the journey most launches take. */
internal fun MacrobenchmarkScope.visitMainScreens() {
    device.wait(Until.hasObject(By.text("Library")), 10_000)
    tapText("Search")
    tapText("Library")
    tapText("Songs")
    device.pressBack()
    device.waitForIdle()
    tapText("Home")
}

private fun MacrobenchmarkScope.tapText(text: String) {
    device.wait(Until.hasObject(By.text(text)), 5_000)
    device.findObject(By.text(text))?.click()
    device.waitForIdle()
}
