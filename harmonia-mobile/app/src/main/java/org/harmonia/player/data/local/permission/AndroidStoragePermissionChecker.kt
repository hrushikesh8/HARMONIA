package org.harmonia.player.data.local.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker
import org.harmonia.player.domain.model.storage.permission.StoragePermissionState

/**
 * Android platform implementation of StoragePermissionChecker.
 * Evaluates runtime permissions dynamically based on Android OS version.
 *
 * Scoped Storage Invariants:
 * - On Android 13+ (API 33+): checks READ_MEDIA_AUDIO.
 * - On Android 8–12 (API 26–32): checks READ_EXTERNAL_STORAGE.
 * - Never requests MANAGE_EXTERNAL_STORAGE.
 * - Never requests INTERNET.
 */
class AndroidStoragePermissionChecker(
    private val context: Context
) : StoragePermissionChecker {

    fun getRequiredAudioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    override fun getPermissionState(): StoragePermissionState {
        val permission = getRequiredAudioPermission()
        val result = ContextCompat.checkSelfPermission(context, permission)
        return if (result == PackageManager.PERMISSION_GRANTED) {
            StoragePermissionState.GRANTED
        } else {
            StoragePermissionState.DENIED
        }
    }

    override fun canScanAudioMedia(): Boolean {
        return getPermissionState() == StoragePermissionState.GRANTED
    }
}
