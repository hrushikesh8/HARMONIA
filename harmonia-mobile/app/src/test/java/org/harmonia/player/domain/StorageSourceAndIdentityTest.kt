package org.harmonia.player.domain

import org.harmonia.player.domain.model.storage.ContentIdentity
import org.harmonia.player.domain.model.storage.SourceIdentity
import org.harmonia.player.domain.model.storage.SourceType
import org.harmonia.player.domain.model.storage.StorageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageSourceAndIdentityTest {

    @Test
    fun `storage source types instantiate correctly with clean domain defaults`() {
        val primary = StorageSource.MediaStorePrimary()
        assertEquals("mediastore_primary", primary.id)
        assertFalse(primary.isRemovable)
        assertTrue(primary.isAvailable)

        val volume = StorageSource.MediaStoreVolume(
            id = "vol-1",
            volumeName = "1234-5678",
            volumeUuid = "1234-5678",
            displayName = "SanDisk SD Card"
        )
        assertEquals("vol-1", volume.id)
        assertTrue(volume.isRemovable)
        assertTrue(volume.isAvailable)

        val saf = StorageSource.SafDocumentTree(
            id = "saf-music",
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
            displayName = "Music Folder",
            isPersisted = true
        )
        assertEquals("saf-music", saf.id)
        assertFalse(saf.isRemovable)
        assertTrue(saf.isPersisted)

        val sync = StorageSource.InternalSyncStorage(
            rootPath = "/data/user/0/org.harmonia.player/files/music"
        )
        assertEquals("internal_sync_store", sync.id)
        assertFalse(sync.isRemovable)
    }

    @Test
    fun `test case 1 - same source and same location produce identical identity`() {
        val id1 = SourceIdentity.mediaStorePrimary("Music/Rock/track1.mp3")
        val id2 = SourceIdentity.mediaStorePrimary("Music/Rock/track1.mp3")

        assertEquals(id1, id2)
        assertEquals(id1.toSerializedKey(), id2.toSerializedKey())
    }

    @Test
    fun `test case 2 - different source produces different identity`() {
        val mediaStoreId = SourceIdentity.mediaStorePrimary("Music/track1.mp3")
        val safId = SourceIdentity.safDocument("tree-uri", "Music/track1.mp3")

        assertNotEquals(mediaStoreId, safId)
        assertNotEquals(mediaStoreId.toSerializedKey(), safId.toSerializedKey())
    }

    @Test
    fun `test case 3 - different volume produces different identity`() {
        val volumeA = SourceIdentity.mediaStoreVolume("sdcard-1", "Music/track1.mp3")
        val volumeB = SourceIdentity.mediaStoreVolume("sdcard-2", "Music/track1.mp3")

        assertNotEquals(volumeA, volumeB)
        assertNotEquals(volumeA.toSerializedKey(), volumeB.toSerializedKey())
    }

    @Test
    fun `test case 4 - different path produces different identity`() {
        val path1 = SourceIdentity.mediaStorePrimary("Music/track1.mp3")
        val path2 = SourceIdentity.mediaStorePrimary("Music/track2.mp3")

        assertNotEquals(path1, path2)
        assertNotEquals(path1.toSerializedKey(), path2.toSerializedKey())
    }

    @Test
    fun `test case 5 - same file content at different locations has different location identities`() {
        val loc1 = SourceIdentity.mediaStorePrimary("Music/track.flac")
        val loc2 = SourceIdentity.mediaStoreVolume("ext-sd", "Music/track.flac")

        assertNotEquals(loc1, loc2)
        assertNotEquals(loc1.toSerializedKey(), loc2.toSerializedKey())
    }

    @Test
    fun `test case 6 - same content hash at different locations maintains identical content identity`() {
        val content1 = ContentIdentity(fileHash = "sha256-abc-123", fileSize = 45_000_000L)
        val content2 = ContentIdentity(fileHash = "sha256-abc-123", fileSize = 45_000_000L)

        assertEquals(content1, content2)
        assertEquals(content1.fileHash, content2.fileHash)
    }

    @Test
    fun `source identity serialization roundtrip works injectively across all source types`() {
        val testIdentities = listOf(
            SourceIdentity(SourceType.MEDIA_STORE_PRIMARY, "primary", "FLAC/Album/01.flac"),
            SourceIdentity(SourceType.MEDIA_STORE_VOLUME, "1234-5678", "Music/Song with spaces.mp3"),
            SourceIdentity(SourceType.SAF_DOCUMENT_TREE, "content://tree/uri", "doc:id:with:colons"),
            SourceIdentity(SourceType.INTERNAL_SYNC, "internal", "sync/track_99.wav")
        )

        for (original in testIdentities) {
            val serialized = original.toSerializedKey()
            val restored = SourceIdentity.fromSerializedKey(serialized)
            assertEquals(original, restored)
        }
    }
}
