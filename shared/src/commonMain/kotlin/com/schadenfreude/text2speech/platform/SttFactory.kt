package com.schadenfreude.text2speech.platform

import io.ktor.client.HttpClient

expect fun createSpeechClient(httpClient: HttpClient, baseUrl: String, token: String): Any?
