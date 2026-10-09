package org.harmonia.player.data.local.storage.lifecycle

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import org.harmonia.player.data.local.entity.StorageSourceEntity

/**
 * Platform bridge decoupling OS-specific volume and permission lookups
 * from coordinator business logic, enabling fast deterministic JVM unit testing.
 */
interface StoragePlatformBridge {
    fun isPlatformVolumeMounted(volumeUuid: String): Boolean
    fun isSafPermissionGranted(treeUri: String): Boolean
    suspend fun checkVolumeReadiness(source: StorageSourceEntity): Boolean
}

class AndroidStoragePlatformBridge(
    private val context: Context
) : StoragePlatformBridge {

    override fun isPlatformVolumeMounted(volumeUuid: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                val volumes = sm?.storageVolumes ?: return true
                for (vol in volumes) {
                    val uuid = vol.uuid
                    if (uuid != null && uuid.equals(volumeUuid, ignoreCase = true)) {
                        return vol.state == Environment.MEDIA_MOUNTED ||
                               vol.state == Environment.MEDIA_MOUNTED_READ_ONLY
                    }
                }
            } catch (_: Exception) {
                return true
            }
        }
        return true
    }

    override fun isSafPermissionGranted(treeUri: String): Boolean {
        return try {
            val permissions = context.contentResolver.persistedUriPermissions
            permissions.any { it.uri.toString() == treeUri && it.isReadPermission }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun checkVolumeReadiness(source: StorageSourceEntity): Boolean {
        for (attempt in 1..3) {
            try {
                if (source.type == "MEDIA_STORE_PRIMARY" || source.type == "MEDIA_STORE_VOLUME") {
                    val cursor = context.contentResolver.query(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(android.provider.MediaStore.Audio.Media._ID),
                        null,
                        null,
                        null
                    )
                    cursor?.close()
                    return true
                }
                return true
            } catch (_: Exception) {
                if (attempt < 3) kotlinx.coroutines.delay(100L)
            }
        }
        return true
    }
}
