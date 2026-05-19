package com.schadenfreude.text2speech.platform

interface PermissionManager {
    fun requestMicrophonePermission(onGranted: () -> Unit, onDenied: () -> Unit)
}

expect fun getPermissionManager(): PermissionManager
