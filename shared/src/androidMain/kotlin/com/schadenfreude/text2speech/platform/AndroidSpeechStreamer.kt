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
import com.schadenfreude.text2speech.data.network.NetworkClientFactory
import com.schadenfreude.text2speech.domain.GCP_ENABLE_PUNCTUATION
import com.schadenfreude.text2speech.domain.GCP_SPEECH_MODEL
import com.schadenfreude.text2speech.domain.STTConfig
import com.schadenfreude.text2speech.domain.TranscriptionResult
import com.schadenfreude.text2speech.domain.toTranscriptionError
import com.squareup.wire.GrpcClient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.ByteString.Companion.toByteString

class AndroidSpeechStreamer(
    private val audioRecorder: AudioRecorder = AndroidAudioRecorder(),
    private val sharedOkHttpClient: OkHttpClient = NetworkClientFactory.sharedOkHttpClient
) : SpeechStreamer {

    override fun startStreaming(
        config: STTConfig,
        token: String
    ): Flow<TranscriptionResult> = flow {
        try {
            val okHttpClient = sharedOkHttpClient.newBuilder()
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
                                        model = GCP_SPEECH_MODEL,
                                        language_codes = listOf(config.language.languageCode),
                                        features = RecognitionFeatures(
                                            enable_automatic_punctuation = GCP_ENABLE_PUNCTUATION
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

                // 2. Receive results and emit
                try {
                    for (response in responseChannel) {
                        response.results.forEach { result ->
                            val transcript = result.alternatives.firstOrNull()?.transcript
                            if (!transcript.isNullOrBlank()) {
                                if (result.is_final) {
                                    emit(TranscriptionResult.Final(transcript))
                                } else {
                                    emit(TranscriptionResult.Interim(transcript))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    emit(e.toTranscriptionError())
                }
            }
        } catch (e: Exception) {
            emit(e.toTranscriptionError())
        } finally {
            audioRecorder.stopRecording()
        }
    }

    override fun stopStreaming() {
        audioRecorder.stopRecording()
    }
}

