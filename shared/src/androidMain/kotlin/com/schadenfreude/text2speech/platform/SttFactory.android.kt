package com.schadenfreude.text2speech.platform

import com.google.cloud.speech.v2.SpeechClient
import com.squareup.wire.GrpcClient
import io.ktor.client.HttpClient
import okhttp3.OkHttpClient

actual fun createSpeechClient(httpClient: HttpClient, baseUrl: String, token: String): Any? {
    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
            chain.proceed(request)
        }
        .build()

    val grpcClient = GrpcClient.Builder()
        .client(okHttpClient)
        .baseUrl(baseUrl)
        .minMessageToCompress(Long.MAX_VALUE)
        .build()

    return grpcClient.create(SpeechClient::class)
}