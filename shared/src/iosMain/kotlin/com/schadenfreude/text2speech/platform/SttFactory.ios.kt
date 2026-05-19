package com.schadenfreude.text2speech.platform

import io.ktor.client.HttpClient

actual fun createSpeechClient(httpClient: HttpClient, baseUrl: String, token: String): Any? {
    // iOS gRPC implementation requires additional C-Interop setup for Wire.
    // For the PoC, we return null to allow common code to compile.
    return null
}
