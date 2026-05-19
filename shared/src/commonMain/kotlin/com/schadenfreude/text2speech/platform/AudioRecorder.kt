package com.schadenfreude.text2speech.platform

import kotlinx.coroutines.flow.Flow

interface AudioRecorder {
    fun startRecording(): Flow<ByteArray>
    fun stopRecording()
}

expect fun getAudioRecorder(): AudioRecorder
