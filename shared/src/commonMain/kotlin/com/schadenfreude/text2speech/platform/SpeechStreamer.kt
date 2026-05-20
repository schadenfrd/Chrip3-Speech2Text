package com.schadenfreude.text2speech.platform

import com.schadenfreude.text2speech.domain.STTConfig
import com.schadenfreude.text2speech.domain.TranscriptionResult

/**
 * Interface for bidirectional gRPC speech streaming.
 * On iOS, this will be implemented in Swift via the Swift Bridge pattern
 * to leverage native gRPC-Swift and NIO libraries.
 */
interface SpeechStreamer {
    /**
     * Starts the streaming process.
     * @param config The STT configuration (language, etc.)
     * @param token The OAuth2 bearer token for authentication.
     * @param onResult Callback for streaming results (interim, final, or error).
     */
    fun startStreaming(
        config: STTConfig,
        token: String,
        onResult: (TranscriptionResult) -> Unit
    )

    /**
     * Stops the streaming process and releases resources.
     */
    fun stopStreaming()
}

expect fun getSpeechStreamer(): SpeechStreamer
