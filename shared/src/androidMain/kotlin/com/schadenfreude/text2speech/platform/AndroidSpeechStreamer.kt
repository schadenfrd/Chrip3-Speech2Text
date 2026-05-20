package com.schadenfreude.text2speech.platform

import com.schadenfreude.text2speech.data.DefaultSttRepository
import com.schadenfreude.text2speech.data.SttRepository
import com.schadenfreude.text2speech.domain.STTConfig
import com.schadenfreude.text2speech.domain.TranscriptionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AndroidSpeechStreamer(
    private val audioRecorder: AudioRecorder = AndroidAudioRecorder(),
    private val sttRepository: SttRepository = DefaultSttRepository()
) : SpeechStreamer {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var streamingJob: Job? = null

    override fun startStreaming(
        config: STTConfig,
        token: String,
        onResult: (TranscriptionResult) -> Unit
    ) {
        streamingJob?.cancel()
        streamingJob = scope.launch {
            try {
                sttRepository.streamAudio(
                    speechClient = sttRepository.speechClient,
                    audioFlow = audioRecorder.startRecording(),
                    config = config,
                    token = token
                ).collect { result ->
                    onResult(result)
                }
            } catch (e: Exception) {
                onResult(TranscriptionResult.Error(e.message ?: "Unknown error"))
            }
        }
    }

    override fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        audioRecorder.stopRecording()
    }
}

actual fun getSpeechStreamer(): SpeechStreamer = AndroidSpeechStreamer()
