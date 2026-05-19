package com.schadenfreude.text2speech.util

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    println("ERROR: [$tag] $message")
    throwable?.printStackTrace()
}

actual fun logInfo(tag: String, message: String) {
    println("INFO: [$tag] $message")
}
