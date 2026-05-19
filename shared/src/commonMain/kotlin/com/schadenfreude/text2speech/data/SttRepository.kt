package com.schadenfreude.text2speech.data

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
import com.schadenfreude.text2speech.platform.createSpeechClient
import com.schadenfreude.text2speech.util.logError
import com.schadenfreude.text2speech.util.logInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import kotlin.io.encoding.Base64

// REST Models
@Serializable
data class RestRecognizeRequest(
    val config: RestRecognitionConfig,
    val content: String // Base64 encoded
)

@Serializable
class RestAutoDecodingConfig

@Serializable
data class RestRecognitionConfig(
    val model: String = "chirp_3",
    val languageCodes: List<String>,
    val features: RestRecognitionFeatures,
    val autoDecodingConfig: RestAutoDecodingConfig = RestAutoDecodingConfig()
)

@Serializable
data class RestRecognitionFeatures(
    val enableAutomaticPunctuation: Boolean = true,
    val adaptation: RestAdaptation? = null
)

@Serializable
data class RestAdaptation(
    val phraseSets: List<RestPhraseSetContext>
)

@Serializable
data class RestPhraseSetContext(
    val phraseSet: String
)

@Serializable
data class RestRecognizeResponse(
    val results: List<RestSpeechRecognitionResult>? = null
)

@Serializable
data class RestSpeechRecognitionResult(
    val alternatives: List<RestSpeechRecognitionAlternative>? = null
)

@Serializable
data class RestSpeechRecognitionAlternative(
    val transcript: String? = null
)

interface SttRepository {
    suspend fun transcribeFile(fileData: ByteArray, config: STTConfig, token: String): String
    suspend fun streamAudio(
        speechClient: Any?,
        audioFlow: Flow<ByteArray>,
        config: STTConfig,
        token: String
    ): Flow<TranscriptionResult>

    val speechClient: Any?
}

private val defaultHttpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    install(Logging) {
        level = LogLevel.INFO
        logger = object : Logger {
            override fun log(message: String) {
                logInfo("Ktor", message)
            }
        }
    }
}

open class DefaultSttRepository(
    private val httpClient: HttpClient = defaultHttpClient
) : SttRepository {

    override val speechClient: Any? = null

    override suspend fun transcribeFile(
        fileData: ByteArray,
        config: STTConfig,
        token: String
    ): String {
        try {
            logInfo(
                "SttRepository",
                "Transcribing file: ${fileData.size} bytes, Language: ${config.language.languageCode}"
            )
            val base64Audio = Base64.encode(fileData)

            val requestBody = RestRecognizeRequest(
                config = RestRecognitionConfig(
                    languageCodes = listOf(config.language.languageCode),
                    features = RestRecognitionFeatures(
                        enableAutomaticPunctuation = true,
                        adaptation = RestAdaptation(
                            phraseSets = listOf(RestPhraseSetContext(phraseSet = config.language.phraseSetId))
                        )
                    )
                ),
                content = base64Audio
            )

            val url =
                "https://${BuildKonfig.GCP_REGION}-speech.googleapis.com/v2/${config.language.recognizerId}:recognize"
            logInfo("SttRepository", "Request URL: $url")

            val response: RestRecognizeResponse = httpClient.post(url) {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            val transcript =
                response.results?.firstOrNull()?.alternatives?.firstOrNull()?.transcript
            return transcript ?: "No speech recognized."

        } catch (e: ClientRequestException) {
            val errorBody = e.response.bodyAsText()
            logError("SttRepository", "REST API Error: $errorBody", e)
            when (e.response.status) {
                HttpStatusCode.TooManyRequests -> throw Exception("API Quota Exceeded. Please try again later.")
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> throw Exception("Authentication failed. Reconnecting...")
                else -> throw Exception("API Error (${e.response.status.value}): $errorBody")
            }
        } catch (e: Exception) {
            logError("SttRepository", "Network error", e)
            throw Exception("Network error: ${e.message}")
        }
    }

    override suspend fun streamAudio(
        speechClient: Any?,
        audioFlow: Flow<ByteArray>,
        config: STTConfig,
        token: String
    ): Flow<TranscriptionResult> = flow {

        val baseUrl = "https://${BuildKonfig.GCP_REGION}-speech.googleapis.com"
        val client = createSpeechClient(httpClient, baseUrl, token) as? SpeechClient
            ?: throw IllegalStateException("SpeechClient not initialized.")

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
                                streaming_features = StreamingRecognitionFeatures(interim_results = true)
                            )
                        )
                    )

                    audioFlow.collect { chunk ->
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

            // 2. Receive results and emit to the outer flow
            try {
                for (response in responseChannel) {
                    response.results.forEach { result ->
                        val transcript = result.alternatives.firstOrNull()?.transcript
                        if (transcript != null && transcript.isNotBlank()) {
                            if (result.is_final == true) {
                                emit(TranscriptionResult.Final(transcript))
                            } else {
                                emit(TranscriptionResult.Interim(transcript))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown streaming error"
                if (msg.contains("UNAUTHENTICATED") || msg.contains("401")) {
                    emit(TranscriptionResult.Error("Authentication failed. Reconnecting..."))
                } else if (msg.contains("RESOURCE_EXHAUSTED")) {
                    emit(TranscriptionResult.Error("API Quota Exceeded. Please try again later."))
                } else {
                    emit(TranscriptionResult.Error("Stream error: $msg"))
                }
            }
        }
    }
}
