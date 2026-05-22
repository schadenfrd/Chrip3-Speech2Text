package com.schadenfreude.text2speech.platform

import com.schadenfreude.text2speech.domain.STTConfig
import com.schadenfreude.text2speech.domain.TranscriptionResult

interface NativeIosStreamer {
    fun start(config: STTConfig, token: String, onResult: (TranscriptionResult) -> Unit)
    fun stop()
}
