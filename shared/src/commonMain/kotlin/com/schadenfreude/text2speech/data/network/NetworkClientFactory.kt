
package com.schadenfreude.text2speech.data.network

import io.ktor.client.HttpClient

expect object NetworkClientFactory {
    fun createKtorClient(): HttpClient
}
