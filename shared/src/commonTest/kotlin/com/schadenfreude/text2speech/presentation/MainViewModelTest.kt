package com.schadenfreude.text2speech.presentation

import app.cash.turbine.test
import com.schadenfreude.text2speech.data.AuthRepository
import com.schadenfreude.text2speech.data.SttRepository
import com.schadenfreude.text2speech.domain.STTConfig
import com.schadenfreude.text2speech.domain.TranscriptionResult
import com.schadenfreude.text2speech.platform.AudioRecorder
import com.schadenfreude.text2speech.platform.FilePicker
import com.schadenfreude.text2speech.platform.SpeechStreamer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeAudioRecorder : AudioRecorder {
        override fun startRecording(): Flow<ByteArray> = flow { }
        override fun stopRecording() {}
    }

    private class FakeSpeechStreamer : SpeechStreamer {
        override fun startStreaming(
            config: STTConfig,
            token: String,
            onResult: (TranscriptionResult) -> Unit
        ) {
        }

        override fun stopStreaming() {}
    }

    private class FakeFilePicker : FilePicker {
        override suspend fun pickFile(): ByteArray? = null
    }

    // Since we now use interfaces, mocking is cleaner
    private class FakeSttRepository : SttRepository {
        override suspend fun transcribeFile(
            fileData: ByteArray,
            config: STTConfig,
            token: String
        ): String = ""
    }

    private class FakeAuthRepository : AuthRepository {
        override suspend fun getAuthToken(): String = "fake-token"
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun toggleMode_correctly_switches_between_Live_Stream_and_File_Upload() = runTest {
        val viewModel = MainViewModel(
            speechStreamer = FakeSpeechStreamer(),
            filePicker = FakeFilePicker(),
            sttRepository = FakeSttRepository(),
            authRepository = FakeAuthRepository()
        )

        viewModel.uiState.test {
            // Initial state
            val initialState = awaitItem()
            assertTrue(initialState.isLiveStream)

            viewModel.toggleMode(false)
            val updatedState = awaitItem()
            assertFalse(updatedState.isLiveStream)

            viewModel.toggleMode(true)
            val backState = awaitItem()
            assertTrue(backState.isLiveStream)
        }
    }

    @Test
    fun startRecording_updates_UI_state_to_isRecording_true_and_shows_loading() = runTest {
        val viewModel = MainViewModel(
            speechStreamer = FakeSpeechStreamer(),
            filePicker = FakeFilePicker(),
            sttRepository = FakeSttRepository(),
            authRepository = FakeAuthRepository()
        )

        viewModel.uiState.test {
            awaitItem() // Consume initial state

            viewModel.toggleRecording() // Starts recording

            val recordingState = awaitItem()
            assertTrue(recordingState.isRecording)
            assertTrue(recordingState.isLoading)
            assertEquals("Starting stream...", recordingState.partialText)

            val fetchedTokenState = awaitItem()
            assertFalse(fetchedTokenState.isLoading)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
