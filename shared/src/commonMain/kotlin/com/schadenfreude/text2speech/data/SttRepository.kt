package com.schadenfreude.text2speech.data

import com.schadenfreude.text2speech.BuildKonfig
import com.schadenfreude.text2speech.data.network.NetworkClientFactory
import com.schadenfreude.text2speech.domain.GCP_ENABLE_PUNCTUATION
import com.schadenfreude.text2speech.domain.GCP_SPEECH_MODEL
import com.schadenfreude.text2speech.domain.STTConfig
import com.schadenfreude.text2speech.util.logError
import com.schadenfreude.text2speech.util.logInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64

// REST Models
@Serializable
data class RestRecognizeRequest(
    val config: RestRecognitionConfig,
    val content: String // Base64 encoded
)

@Serializable
data class RestRecognitionConfig(
    val model: String = GCP_SPEECH_MODEL,
    @SerialName("language_codes")
    val languageCodes: List<String>,
    val features: RestRecognitionFeatures,
    val adaptation: RestAdaptation? = null,
    @SerialName("auto_decoding_config")
    val autoDecodingConfig: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class RestRecognitionFeatures(
    @SerialName("enable_automatic_punctuation")
    val enableAutomaticPunctuation: Boolean = GCP_ENABLE_PUNCTUATION,
)

@Serializable
data class RestAdaptation(
    @SerialName("phrase_sets")
    val phraseSets: List<RestPhraseSetContext>
)

@Serializable
data class RestPhraseSetContext(
    @SerialName("phrase_set")
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
}

open class DefaultSttRepository(
    private val httpClient: HttpClient = NetworkClientFactory.createKtorClient()
) : SttRepository {

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
                    model = GCP_SPEECH_MODEL,
                    languageCodes = listOf(config.language.languageCode),
                    adaptation = RestAdaptation(
                        phraseSets = listOf(RestPhraseSetContext(phraseSet = config.language.phraseSetId))
                    ),
                    features = RestRecognitionFeatures(enableAutomaticPunctuation = GCP_ENABLE_PUNCTUATION)
                ),
                content = base64Audio
            )

            val url =
                "https://${BuildKonfig.GCP_REGION}-speech.googleapis.com/v2/${config.language.recognizerId}:recognize"
            logInfo("SttRepository", "Request URL: $url")

            // 1. Get the raw HttpResponse FIRST without parsing the body
            val response: HttpResponse = httpClient.post(url) {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            // 2. Check if it's a success (200-299)
            if (response.status.isSuccess()) {
                // ONLY parse if we know it's a success
                val successResponse: RestRecognizeResponse = response.body()
                val transcript = successResponse
                    .results?.firstOrNull()
                    ?.alternatives?.firstOrNull()
                    ?.transcript
                return transcript ?: "No speech recognized."
            } else {
                // 3. It failed! Read the raw text so Kotlinx Serialization doesn't crash
                val errorBody = response.bodyAsText()
                logError("SttRepository", "REST API Error (${response.status.value}): $errorBody")

                when (response.status) {
                    HttpStatusCode.BadRequest -> throw Exception("Bad Request: $errorBody")
                    HttpStatusCode.TooManyRequests -> throw Exception("API Quota Exceeded. Please try again later.")
                    HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> throw Exception("Authentication failed. Reconnecting...")
                    else -> throw Exception("API Error (${response.status.value}): $errorBody")
                }
            }
        } catch (e: Exception) {
            // If it's already an Exception we threw above, just rethrow it
            if (e.message?.contains("Bad Request") == true
                || e.message?.contains("API Error") == true
                || e.message?.contains("Auth") == true
            ) {
                throw e
            }
            logError("SttRepository", "Network or Serialization error", e)
            throw Exception("Error: ${e.message}")
        }
    }

}
