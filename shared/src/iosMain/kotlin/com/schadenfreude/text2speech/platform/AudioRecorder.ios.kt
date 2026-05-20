package com.schadenfreude.text2speech.platform

import kotlinx.cinterop.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.AVFAudio.*
import platform.Foundation.*
import platform.posix.memcpy

class IosAudioRecorder : AudioRecorder {
    private val audioEngine = AVAudioEngine()
    private var isRecording = false

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun startRecording(): Flow<ByteArray> = callbackFlow {
        if (isRecording) {
            close(IllegalStateException("Already recording"))
            return@callbackFlow
        }

        val inputNode = audioEngine.inputNode
        val bus = 0uL
        val inputFormat = inputNode.inputFormatForBus(bus)

        // Standard STT format: 16kHz, Mono, PCM 16-bit
        val outputFormat = AVAudioFormat(
            commonFormat = AVAudioPCMFormatInt16,
            sampleRate = 16000.0,
            channels = 1u,
            interleaved = false
        )

        val converter = AVAudioConverter(fromFormat = inputFormat, toFormat = outputFormat)

        val session = AVAudioSession.sharedInstance()
        memScoped {
            val errorVar = alloc<ObjCObjectVar<NSError?>>()
            session.setCategory(AVAudioSessionCategoryPlayAndRecord, 
                withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker or AVAudioSessionCategoryOptionAllowBluetooth, 
                error = errorVar.ptr)
            session.setMode(AVAudioSessionModeMeasurement, error = errorVar.ptr)
            session.setActive(true, error = errorVar.ptr)
        }

        inputNode.installTapOnBus(bus = bus, bufferSize = 1024u, format = inputFormat) { buffer, _ ->
            if (buffer == null) return@installTapOnBus

            val pcmBuffer = AVAudioPCMBuffer(
                pCMFormat = outputFormat,
                frameCapacity = buffer.frameLength
            )

            val inputBlock: AVAudioConverterInputBlock = { _, outStatus ->
                if (outStatus != null) {
                    // 0L maps to AVAudioConverterInputStatus_HaveData in Kotlin/Native for iOS
                    outStatus.pointed.value = 0L 
                }
                buffer
            }

            memScoped {
                val errorVar = alloc<ObjCObjectVar<NSError?>>()
                converter.convertToBuffer(pcmBuffer, errorVar.ptr, inputBlock)
            }

            val channelData = pcmBuffer.int16ChannelData?.get(0)
            val length = pcmBuffer.frameLength.toInt()

            if (channelData != null && length > 0) {
                // 16-bit PCM = 2 bytes per frame
                val byteArray = ByteArray(length * 2) 
                byteArray.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), channelData, (length * 2).toULong())
                }
                trySend(byteArray)
            }
        }

        audioEngine.prepare()
        try {
            memScoped {
                val errorVar = alloc<ObjCObjectVar<NSError?>>()
                if (!audioEngine.startAndReturnError(errorVar.ptr)) {
                    val error = errorVar.value
                    close(Exception("AudioEngine failed to start: ${error?.localizedDescription}"))
                } else {
                    isRecording = true
                }
            }
        } catch (e: Exception) {
            close(e)
        }

        awaitClose {
            stopRecording()
        }
    }

    override fun stopRecording() {
        isRecording = false
        audioEngine.inputNode.removeTapOnBus(0uL)
        audioEngine.stop()
    }
}

actual fun getAudioRecorder(): AudioRecorder = IosAudioRecorder()
