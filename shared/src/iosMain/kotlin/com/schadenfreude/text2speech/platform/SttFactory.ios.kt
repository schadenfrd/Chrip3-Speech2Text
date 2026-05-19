package com.schadenfreude.text2speech.platform

import io.ktor.client.HttpClient

actual fun createSpeechClient(httpClient: HttpClient, baseUrl: String, token: String): Any? {
    // Note: Bidirectional gRPC streaming with Wire 5.1.0 on iOS is supported.
    // However, for the PoC we return null if the native client cannot be instantiated in this environment.
    return null
}
