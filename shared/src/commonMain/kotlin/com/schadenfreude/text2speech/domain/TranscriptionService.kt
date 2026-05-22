package com.schadenfreude.text2speech.domain

import com.schadenfreude.text2speech.data.AuthRepository
import com.schadenfreude.text2speech.data.DefaultAuthRepository
import com.schadenfreude.text2speech.data.DefaultSttRepository
import com.schadenfreude.text2speech.data.SttRepository
import com.schadenfreude.text2speech.platform.FilePicker
import com.schadenfreude.text2speech.platform.SpeechStreamer
import com.schadenfreude.text2speech.platform.SttFactory
import com.schadenfreude.text2speech.platform.getFilePicker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class TranscriptionService(
    private val speechStreamer: SpeechStreamer = SttFactory.getSpeechStreamer(),
    private val filePicker: FilePicker = getFilePicker(),
    private val sttRepository: SttRepository = DefaultSttRepository(),
    private val authRepository: AuthRepository = DefaultAuthRepository(),
) {

    fun streamAudio(config: STTConfig): Flow<TranscriptionResult> = callbackFlow {
        try {
            val token = authRepository.getAuthToken()
            speechStreamer.startStreaming(config, token) { result ->
                trySend(result)
            }
        } catch (e: Exception) {
            trySend(TranscriptionResult.Error(e.message ?: "Unknown authorization error"))
            close(e)
        }

        awaitClose {
            speechStreamer.stopStreaming()
        }
    }

    fun stopStreaming() {
        speechStreamer.stopStreaming()
    }

    suspend fun pickFile(): ByteArray? {
        return filePicker.pickFile()
    }

    suspend fun transcribeFile(file: ByteArray, config: STTConfig): String {
        val token = authRepository.getAuthToken()

        return sttRepository.transcribeFile(file, config, token)
    }
}
