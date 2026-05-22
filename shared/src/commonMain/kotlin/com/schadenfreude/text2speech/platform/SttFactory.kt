package com.schadenfreude.text2speech.platform

expect object SttFactory {
    fun getSpeechStreamer(): SpeechStreamer
}
