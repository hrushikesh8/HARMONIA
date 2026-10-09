package org.harmonia.player.data.local.storage.lifecycle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import org.harmonia.player.data.local.entity.StorageSourceEntity

/**
 * Listener for hardware volume mount and unmount events.
 */
interface VolumeEventListener {
    fun onVolumeMounted(volumeId: String)
    fun onVolumeUnmounted(volumeId: String)
}

/**
 * Platform bridge decoupling OS-specific volume and permission lookups
 * from coordinator business logic, enabling fast deterministic JVM unit testing.
 */
interface StoragePlatformBridge {
    fun isPlatformVolumeMounted(volumeUuid: String): Boolean
    fun isSafPermissionGranted(treeUri: String): Boolean
    suspend fun checkVolumeReadiness(source: StorageSourceEntity): Boolean
    fun registerVolumeEventListener(listener: VolumeEventListener): AutoCloseable
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

    override fun registerVolumeEventListener(listener: VolumeEventListener): AutoCloseable {
        val cleanups = mutableListOf<() -> Unit>()

        // 1. API 30+ (Android 11+) StorageManager.StorageVolumeCallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                if (sm != null) {
                    val callback = object : StorageManager.StorageVolumeCallback() {
                        override fun onStateChanged(volume: StorageVolume) {
                            val id = volume.uuid ?: volume.directory?.name ?: return
                            val state = volume.state
                            if (state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY) {
                                listener.onVolumeMounted(id)
                            } else if (state == Environment.MEDIA_UNMOUNTED ||
                                state == Environment.MEDIA_EJECTING ||
                                state == Environment.MEDIA_REMOVED ||
                                state == Environment.MEDIA_BAD_REMOVAL) {
                                listener.onVolumeUnmounted(id)
                            }
                        }
                    }
                    sm.registerStorageVolumeCallback(context.mainExecutor, callback)
                    cleanups.add {
                        try { sm.unregisterStorageVolumeCallback(callback) } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Dynamic BroadcastReceiver for media mount/unmount broadcasts (universal fallback)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val uri = intent.data
                val path = uri?.path ?: ""
                val volumeId = uri?.schemeSpecificPart?.substringAfterLast('/')
                    ?: path.substringAfterLast('/')
                if (volumeId.isBlank()) return

                when (action) {
                    Intent.ACTION_MEDIA_MOUNTED -> {
                        listener.onVolumeMounted(volumeId)
                    }
                    Intent.ACTION_MEDIA_UNMOUNTED,
                    Intent.ACTION_MEDIA_EJECT,
                    Intent.ACTION_MEDIA_REMOVED,
                    Intent.ACTION_MEDIA_BAD_REMOVAL -> {
                        listener.onVolumeUnmounted(volumeId)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        try {
            context.registerReceiver(receiver, filter)
            cleanups.add {
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        return AutoCloseable {
            for (cleanup in cleanups) cleanup()
        }
    }
}

