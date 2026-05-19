package com.schadenfreude.text2speech.platform

import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import java.util.UUID

class AndroidPermissionManager : PermissionManager {
    override fun requestMicrophonePermission(onGranted: () -> Unit, onDenied: () -> Unit) {
        val activity = AndroidContext.getActivity() ?: run {
            onDenied()
            return
        }

        val key = UUID.randomUUID().toString()
        val launcher = activity.activityResultRegistry.register(
            key,
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                onGranted()
            } else {
                onDenied()
            }
        }

        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

actual fun getPermissionManager(): PermissionManager = AndroidPermissionManager()
