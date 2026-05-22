package com.schadenfreude.text2speech.data

import com.schadenfreude.text2speech.BuildKonfig
import com.schadenfreude.text2speech.data.network.NetworkClientFactory
import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(val token: String)

interface AuthRepository {
    suspend fun getAuthToken(): String
}

open class DefaultAuthRepository(
    private val httpClient: HttpClient = NetworkClientFactory.createKtorClient()
) : AuthRepository {

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
