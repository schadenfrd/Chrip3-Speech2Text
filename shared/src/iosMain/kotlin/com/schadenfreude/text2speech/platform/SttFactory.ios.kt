package com.schadenfreude.text2speech.platform


private var _iosSpeechStreamer: SpeechStreamer? = null

fun setIosSpeechStreamer(streamer: SpeechStreamer) {
    _iosSpeechStreamer = streamer
}

actual object SttFactory {
    actual fun getSpeechStreamer(): SpeechStreamer {
        return _iosSpeechStreamer ?: error("IosSpeechStreamer has not been initialized via Swift yet!")
    }
}
