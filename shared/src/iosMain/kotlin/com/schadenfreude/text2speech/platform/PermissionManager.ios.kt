package com.schadenfreude.text2speech.platform

import platform.AVFAudio.*

class IosPermissionManager : PermissionManager {
    override fun requestMicrophonePermission(onGranted: () -> Unit, onDenied: () -> Unit) {
        AVAudioSession.sharedInstance().requestRecordPermission { granted: Boolean ->
            if (granted) {
                onGranted()
            } else {
                onDenied()
            }
        }
    }
}

actual fun getPermissionManager(): PermissionManager = IosPermissionManager()
