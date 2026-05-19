package com.schadenfreude.text2speech.platform

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class AndroidAudioRecorder : AudioRecorder {
    private var isRecording = false
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    @SuppressLint("MissingPermission")
    override fun startRecording(): Flow<ByteArray> = callbackFlow {
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize * 2
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            close(IllegalStateException("AudioRecord initialization failed. Ensure RECORD_AUDIO permission is granted."))
            return@callbackFlow
        }

        audioRecord.startRecording()
        isRecording = true

        val recordingJob = launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    send(buffer.copyOf(read))
                }
            }
        }

        awaitClose {
            isRecording = false
            recordingJob.cancel()
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                audioRecord.release()
            }
        }
    }

    override fun stopRecording() {
        isRecording = false
    }
}

actual fun getAudioRecorder(): AudioRecorder = AndroidAudioRecorder()
