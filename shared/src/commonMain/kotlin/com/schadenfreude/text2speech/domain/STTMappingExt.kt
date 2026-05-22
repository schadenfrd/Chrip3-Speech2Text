package com.schadenfreude.text2speech.domain

// Shared constants for Google Cloud STT
val GCP_SPEECH_MODEL = "chirp_3"
val GCP_ENABLE_PUNCTUATION = true

// Unified Error Mapper
fun Throwable.toTranscriptionError(): TranscriptionResult.Error {
    val message = this.message ?: "Unknown streaming error"

    return when {
        message.contains("401") || message.contains("UNAUTHENTICATED") || message.contains("Authentication failed") ->
            TranscriptionResult.Error("Authentication failed. Reconnecting...")

        message.contains("RESOURCE_EXHAUSTED") || message.contains("TooManyRequests") ->
            TranscriptionResult.Error("API Quota Exceeded. Please try again later.")

        else -> TranscriptionResult.Error("Stream error: $message")
    }
}
