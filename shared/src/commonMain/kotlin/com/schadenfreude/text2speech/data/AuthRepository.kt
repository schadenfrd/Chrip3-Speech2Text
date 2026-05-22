package com.schadenfreude.text2speech.data
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TokenResponse(val token: String)

interface AuthRepository {
    suspend fun getAuthToken(): String
    fun setManualToken(token: String?)
    fun getManualToken(): String?
}

open class DefaultAuthRepository : AuthRepository {
    private var manualToken: String? = null

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    override fun setManualToken(token: String?) {
        manualToken = token
    }

    override fun getManualToken(): String? = manualToken

    override suspend fun getAuthToken(): String {
        return manualToken ?: ""
    }
}
