package org.harmonia.player.domain

import org.harmonia.player.domain.model.storage.ScanCheckpoint
import org.harmonia.player.domain.model.storage.ScanStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanCheckpointTest {

    @Test
    fun `scan checkpoint initializes with clean defaults`() {
        val checkpoint = ScanCheckpoint(
            sourceId = "mediastore_primary",
            volumeName = "external_primary"
        )

        assertEquals("mediastore_primary", checkpoint.sourceId)
        assertEquals("external_primary", checkpoint.volumeName)
        assertEquals(0L, checkpoint.lastGeneration)
        assertEquals(0L, checkpoint.lastScanTimestamp)
        assertEquals(0, checkpoint.scannedFileCount)
        assertEquals(ScanStatus.IDLE, checkpoint.status)
    }

    @Test
    fun `scan checkpoint reflects state progression`() {
        val checkpoint = ScanCheckpoint(
            sourceId = "vol-1",
            volumeName = "sdcard",
            lastGeneration = 450L,
            lastScanTimestamp = 1700000000L,
            scannedFileCount = 350,
            status = ScanStatus.COMPLETED
        )

        assertEquals(450L, checkpoint.lastGeneration)
        assertEquals(1700000000L, checkpoint.lastScanTimestamp)
        assertEquals(350, checkpoint.scannedFileCount)
        assertEquals(ScanStatus.COMPLETED, checkpoint.status)
    }
}
