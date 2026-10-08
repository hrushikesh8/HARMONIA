package org.harmonia.player.domain

import org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker
import org.harmonia.player.domain.model.storage.permission.StoragePermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePermissionStateTest {

    private class MockStoragePermissionChecker(
        var state: StoragePermissionState
    ) : StoragePermissionChecker {
        override fun getPermissionState(): StoragePermissionState = state
        override fun canScanAudioMedia(): Boolean = state == StoragePermissionState.GRANTED
    }

    @Test
    fun `permission checker allows scanning only when state is GRANTED`() {
        val checker = MockStoragePermissionChecker(StoragePermissionState.GRANTED)
        assertTrue(checker.canScanAudioMedia())
        assertEquals(StoragePermissionState.GRANTED, checker.getPermissionState())

        checker.state = StoragePermissionState.DENIED
        assertFalse(checker.canScanAudioMedia())
        assertEquals(StoragePermissionState.DENIED, checker.getPermissionState())

        checker.state = StoragePermissionState.SETTINGS_REQUIRED
        assertFalse(checker.canScanAudioMedia())
        assertEquals(StoragePermissionState.SETTINGS_REQUIRED, checker.getPermissionState())
    }
}
