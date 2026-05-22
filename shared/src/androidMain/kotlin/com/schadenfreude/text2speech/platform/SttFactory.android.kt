package com.schadenfreude.text2speech.platform

actual object SttFactory {
    actual fun getSpeechStreamer(): SpeechStreamer = AndroidSpeechStreamer()
}