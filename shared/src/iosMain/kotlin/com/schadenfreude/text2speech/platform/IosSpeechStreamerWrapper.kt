package com.schadenfreude.text2speech.platform

import com.schadenfreude.text2speech.domain.STTConfig
import com.schadenfreude.text2speech.domain.TranscriptionResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class IosSpeechStreamerWrapper(
    private val nativeStreamer: NativeIosStreamer
) : SpeechStreamer {

    override fun startStreaming(config: STTConfig, token: String): Flow<TranscriptionResult> = callbackFlow {
        nativeStreamer.start(config, token) { result ->
            trySend(result)
        }
        
        awaitClose {
            nativeStreamer.stop()
        }
    }

    override fun stopStreaming() {
        nativeStreamer.stop()
    }
}
