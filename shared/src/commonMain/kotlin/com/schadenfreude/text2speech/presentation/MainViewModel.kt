package com.schadenfreude.text2speech.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schadenfreude.text2speech.domain.Language
import com.schadenfreude.text2speech.domain.STTConfig
import com.schadenfreude.text2speech.domain.TranscriptionResult
import com.schadenfreude.text2speech.domain.TranscriptionService
import com.schadenfreude.text2speech.util.logError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isLiveStream: Boolean = true,
    val selectedLanguage: Language = Language.ENGLISH,
    val finalText: String = "",
    val partialText: String = "",
    val isRecording: Boolean = false,
    val pickedFile: ByteArray? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MainUiState

        if (isLiveStream != other.isLiveStream) return false
        if (isRecording != other.isRecording) return false
        if (isLoading != other.isLoading) return false
        if (selectedLanguage != other.selectedLanguage) return false
        if (finalText != other.finalText) return false
        if (partialText != other.partialText) return false
        if (!pickedFile.contentEquals(other.pickedFile)) return false
        if (errorMessage != other.errorMessage) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isLiveStream.hashCode()
        result = 31 * result + isRecording.hashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + selectedLanguage.hashCode()
        result = 31 * result + finalText.hashCode()
        result = 31 * result + partialText.hashCode()
        result = 31 * result + (pickedFile?.contentHashCode() ?: 0)
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        return result
    }
}

class MainViewModel(
    private val transcriptionService: TranscriptionService = TranscriptionService()
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var inactivityJob: Job? = null
    private var recordingJob: Job? = null

    fun toggleMode(isLiveStream: Boolean) {
        _uiState.update {
            it.copy(
                isLiveStream = isLiveStream,
                finalText = "",
                partialText = "",
                errorMessage = null
            )
        }
    }

    fun selectLanguage(language: Language) {
        _uiState.update { it.copy(selectedLanguage = language) }
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        _uiState.update {
            it.copy(
                isRecording = true,
                isLoading = true,
                finalText = "",
                partialText = "Starting stream...",
                errorMessage = null
            )
        }

        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            val config = STTConfig(_uiState.value.selectedLanguage, true)

            transcriptionService.streamAudio(config).collect { result ->
                _uiState.update { it.copy(isLoading = false) }
                resetInactivityTimer()

                when (result) {
                    is TranscriptionResult.Interim -> {
                        _uiState.update { it.copy(partialText = result.text) }
                    }

                    is TranscriptionResult.Final -> {
                        _uiState.update {
                            it.copy(
                                finalText = (it.finalText + " " + result.text).trim(),
                                partialText = ""
                            )
                        }
                    }

                    is TranscriptionResult.Error -> {
                        _uiState.update { it.copy(errorMessage = result.message) }
                        stopRecording()
                    }
                }
            }
        }
    }

    private fun stopRecording() {
        inactivityJob?.cancel()
        recordingJob?.cancel()
        transcriptionService.stopStreaming()
        _uiState.update { it.copy(isRecording = false, partialText = "") }
    }

    private fun startInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = viewModelScope.launch {
            delay(10000)
            if (_uiState.value.isRecording) {
                stopRecording()
                recordingJob?.cancel()
                _uiState.update { it.copy(errorMessage = "Recording stopped due to inactivity") }
            }
        }
    }

    private fun resetInactivityTimer() {
        startInactivityTimer()
    }

    fun pickFile() {
        viewModelScope.launch {
            val file = transcriptionService.pickFile()
            _uiState.update {
                it.copy(
                    pickedFile = file,
                    finalText = if (file != null) "File picked: ${file.size} bytes" else "",
                    partialText = "",
                    errorMessage = null
                )
            }
        }
    }

    fun uploadAndTranscribe() {
        val file = _uiState.value.pickedFile ?: return
        val config = STTConfig(_uiState.value.selectedLanguage, false)

        _uiState.update {
            it.copy(
                isLoading = true,
                finalText = "Uploading and transcribing...",
                partialText = "",
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val result = transcriptionService.transcribeFile(file, config)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        finalText = result
                    )
                }
            } catch (e: Exception) {
                logError("MainViewModel", "Upload and transcribe failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Upload Error [${e::class.simpleName}]: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }
}
