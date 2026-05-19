package com.schadenfreude.text2speech.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schadenfreude.text2speech.data.AuthRepository
import com.schadenfreude.text2speech.data.DefaultAuthRepository
import com.schadenfreude.text2speech.data.DefaultSttRepository
import com.schadenfreude.text2speech.data.SttRepository
import com.schadenfreude.text2speech.domain.Language
import com.schadenfreude.text2speech.domain.STTConfig
import com.schadenfreude.text2speech.domain.TranscriptionResult
import com.schadenfreude.text2speech.platform.AudioRecorder
import com.schadenfreude.text2speech.platform.FilePicker
import com.schadenfreude.text2speech.platform.getAudioRecorder
import com.schadenfreude.text2speech.platform.getFilePicker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
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
    private val audioRecorder: AudioRecorder = getAudioRecorder(),
    private val filePicker: FilePicker = getFilePicker(),
    private val sttRepository: SttRepository = DefaultSttRepository(),
    private val authRepository: AuthRepository = DefaultAuthRepository()
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
            var retryCount = 0
            val maxRetries = 3
            
            while (_uiState.value.isRecording && retryCount < maxRetries) {
                try {
                    // Fetch a fresh dynamic token immediately before starting/restarting
                    val token = authRepository.getAuthToken()
                    _uiState.update { it.copy(isLoading = false) }
                    val config = STTConfig(_uiState.value.selectedLanguage, true)
                    
                    _uiState.update { it.copy(partialText = if (retryCount > 0) "Reconnecting..." else "") }

                    // Start timer after connection is established
                    startInactivityTimer()

                    sttRepository.streamAudio(
                        speechClient = sttRepository.speechClient,
                        audioFlow = audioRecorder.startRecording().onEach { resetInactivityTimer() },
                        config = config,
                        token = token
                    ).collect { result ->
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
                                // Check if it's an auth error to trigger reconnection
                                if (result.message.contains("UNAUTHENTICATED", ignoreCase = true) || 
                                    result.message.contains("401", ignoreCase = true) ||
                                    result.message.contains("Authentication failed", ignoreCase = true)) {
                                    throw Exception("Auth expired: ${result.message}")
                                }
                                if (_uiState.value.isRecording) { _uiState.update { it.copy(errorMessage = result.message) } }
                            }
                        }
                    }
                    // If stream completes normally without error, break the retry loop
                    break
                } catch (e: Exception) {
                    if ((e.message?.contains("Auth expired") == true || e.message?.contains("401") == true || e.message?.contains("Authentication failed") == true) && retryCount < maxRetries) {
                        retryCount++
                        // Continue loop to retry with fresh token
                        continue
                    } else {
                        if (_uiState.value.isRecording) { _uiState.update { it.copy(errorMessage = "Stream Error: ${e.message}", isLoading = false) } } else { _uiState.update { it.copy(isLoading = false) } }
                        break
                    }
                }
            }
            
            if (_uiState.value.isRecording) {
                stopRecording()
            }
        }
    }

    private fun stopRecording() {
        inactivityJob?.cancel()
        recordingJob?.cancel()
        audioRecorder.stopRecording()
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
            val file = filePicker.pickFile()
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
                val token = authRepository.getAuthToken()
                val result = sttRepository.transcribeFile(file, config, token)
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        finalText = result
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Upload Error: ${e.message}"
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
