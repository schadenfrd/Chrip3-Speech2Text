package com.schadenfreude.text2speech.platform

import com.google.cloud.speech.v2.ExplicitDecodingConfig
import com.google.cloud.speech.v2.RecognitionConfig
import com.google.cloud.speech.v2.RecognitionFeatures
import com.google.cloud.speech.v2.SpeechAdaptation
import com.google.cloud.speech.v2.SpeechClient
import com.google.cloud.speech.v2.StreamingRecognitionConfig
import com.google.cloud.speech.v2.StreamingRecognitionFeatures
import com.google.cloud.speech.v2.StreamingRecognizeRequest
import com.schadenfreude.text2speech.BuildKonfig
import com.schadenfreude.text2speech.domain.STTConfig
import com.schadenfreude.text2speech.domain.TranscriptionResult
import com.squareup.wire.GrpcClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.ByteString.Companion.toByteString

class AndroidSpeechStreamer(
    private val audioRecorder: AudioRecorder = AndroidAudioRecorder()
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
                val okHttpClient = OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .addHeader("Authorization", "Bearer $token")
                            .build()
                        chain.proceed(request)
                    }
                    .build()

                val baseUrl = "https://${BuildKonfig.GCP_REGION}-speech.googleapis.com"
                val grpcClient = GrpcClient.Builder()
                    .client(okHttpClient)
                    .baseUrl(baseUrl)
                    .minMessageToCompress(Long.MAX_VALUE)
                    .build()

                val client = grpcClient.create(SpeechClient::class)

                coroutineScope {
                    val (requestChannel, responseChannel) = client.StreamingRecognize()
                        .executeIn(this)

                    // 1. Send configuration and then audio
                    launch {
                        try {
                            requestChannel.send(
                                StreamingRecognizeRequest(
                                    recognizer = config.language.recognizerId,
                                    streaming_config = StreamingRecognitionConfig(
                                        config = RecognitionConfig(
                                            explicit_decoding_config = ExplicitDecodingConfig(
                                                encoding = ExplicitDecodingConfig.AudioEncoding.LINEAR16,
                                                sample_rate_hertz = 16000,
                                                audio_channel_count = 1
                                            ),
                                            model = "chirp_3",
                                            language_codes = listOf(config.language.languageCode),
                                            features = RecognitionFeatures(
                                                enable_automatic_punctuation = true
                                            ),
                                            adaptation = SpeechAdaptation(
                                                phrase_sets = listOf(
                                                    SpeechAdaptation.AdaptationPhraseSet(phrase_set = config.language.phraseSetId)
                                                )
                                            )
                                        ),
                                        streaming_features = StreamingRecognitionFeatures(
                                            interim_results = true
                                        )
                                    )
                                )
                            )

                            audioRecorder.startRecording().collect { chunk ->
                                requestChannel.send(
                                    StreamingRecognizeRequest(
                                        audio = chunk.toByteString()
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            // Handle channel closed or other send errors
                        } finally {
                            requestChannel.close()
                        }
                    }

                    // 2. Receive results and call onResult
                    try {
                        for (response in responseChannel) {
                            response.results.forEach { result ->
                                val transcript = result.alternatives.firstOrNull()?.transcript
                                if (!transcript.isNullOrBlank()) {
                                    if (result.is_final) {
                                        onResult(TranscriptionResult.Final(transcript))
                                    } else {
                                        onResult(TranscriptionResult.Interim(transcript))
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        val msg = e.message ?: "Unknown streaming error"
                        if (msg.contains("UNAUTHENTICATED") || msg.contains("401")) {
                            onResult(TranscriptionResult.Error("Authentication failed. Reconnecting..."))
                        } else if (msg.contains("RESOURCE_EXHAUSTED")) {
                            onResult(TranscriptionResult.Error("API Quota Exceeded. Please try again later."))
                        } else {
                            onResult(TranscriptionResult.Error("Stream error: $msg"))
                        }
                    }
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

