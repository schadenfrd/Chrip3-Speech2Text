package com.schadenfreude.text2speech.platform

private var _iosSpeechStreamer: SpeechStreamer? = null

// Swift will call this and pass its NativeIosStreamer implementation
fun setIosSpeechStreamer(nativeStreamer: NativeIosStreamer) {
    _iosSpeechStreamer = IosSpeechStreamerWrapper(nativeStreamer)
}

actual object SttFactory {
    actual fun getSpeechStreamer(): SpeechStreamer {
        return _iosSpeechStreamer ?: error("IosSpeechStreamer has not been initialized via Swift yet!")
    }
}
