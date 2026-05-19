package com.schadenfreude.text2speech.util

import android.util.Log

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    Log.e(tag, message, throwable)
}

actual fun logInfo(tag: String, message: String) {
    Log.i(tag, message)
}
