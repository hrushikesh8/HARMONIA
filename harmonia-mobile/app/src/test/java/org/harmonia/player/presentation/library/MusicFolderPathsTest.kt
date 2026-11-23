package org.harmonia.player.presentation.library

import org.harmonia.player.data.local.MediaFolderResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicFolderPathsTest {

    @Test
    fun `media store uris use the resolved folder`() {
        val uri = "content://media/external/audio/media/42"
        assertEquals("Music/Rock", folderPathFor(uri, mapOf(uri to "Music/Rock")))
    }

    @Test
    fun `unresolved media store uris fall into the unknown folder instead of a shared media bucket`() {
        assertEquals(UNKNOWN_FOLDER, folderPathFor("content://media/external/audio/media/42", emptyMap()))
    }

    @Test
    fun `saf document uris decode their document id`() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic" +
            "/document/primary%3AMusic%2FJazz%2FSo%20What.flac"
        assertEquals("Music/Jazz", folderPathFor(uri, emptyMap()))
    }

    @Test
    fun `saf files at the volume root use the storage root folder`() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3A/document/primary%3Asong.mp3"
        assertEquals(STORAGE_ROOT_FOLDER, folderPathFor(uri, emptyMap()))
    }

    @Test
    fun `file paths use their parent directory and keep plus signs`() {
        assertEquals("/storage/emulated/0/Music/A+B", folderPathFor("file:///storage/emulated/0/Music/A+B/x.mp3", emptyMap()))
    }

    @Test
    fun `display name is the last path segment`() {
        assertEquals("Jazz", folderDisplayName("Music/Jazz"))
        assertEquals("Music", folderDisplayName("Music/"))
        assertEquals(UNKNOWN_FOLDER, folderDisplayName(UNKNOWN_FOLDER))
    }

    @Test
    fun `media store audio uri parsing extracts volume and id`() {
        assertEquals("external" to 42L, MediaFolderResolver.parseMediaStoreAudioUri("content://media/external/audio/media/42"))
        assertEquals("1a2b-3c4d" to 7L, MediaFolderResolver.parseMediaStoreAudioUri("content://media/1a2b-3c4d/audio/media/7"))
        assertNull(MediaFolderResolver.parseMediaStoreAudioUri("content://com.android.externalstorage.documents/document/x"))
    }
}
