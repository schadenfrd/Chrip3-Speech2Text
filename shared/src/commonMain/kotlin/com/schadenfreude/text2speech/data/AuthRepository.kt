package com.schadenfreude.text2speech.data
import com.schadenfreude.text2speech.BuildKonfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TokenResponse(val token: String)

interface AuthRepository {
    suspend fun getAuthToken(): String
}

open class DefaultAuthRepository : AuthRepository {
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    override suspend fun getAuthToken(): String {
        return try {
//            val response: TokenResponse = httpClient.get(BuildKonfig.AUTH_BACKEND_URL).body()
//            response.token
            BuildKonfig.POC_BEARER_TOKEN
        } catch (e: Exception) {
            // In a real PoC, you might want to handle this more gracefully
            // For now, we return a placeholder or throw
            throw Exception("Failed to fetch auth token: ${e.message}")
        }
    }
}
