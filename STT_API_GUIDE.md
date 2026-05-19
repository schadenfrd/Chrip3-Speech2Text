# Google Cloud Speech-to-Text V2 API Integration Guide

This guide outlines the integration details for the safety inspection dictation tool using Google Cloud STT V2.

## 1. Synchronous File Upload (REST via Ktor)

When "File Upload" mode is active, the app sends a POST request to the Google Cloud STT V2 API.

### Endpoint
`POST https://speech.googleapis.com/v2/projects/{project}/locations/{location}/recognizers/{recognizer}:recognize`

### JSON Payload Structure
```json
{
  "config": {
    "autoDecodingConfig": {},
    "model": "long",
    "languageCodes": ["bg-BG"],
    "features": {
      "enableAutomaticPunctuation": true,
      "adaptation": {
        "phraseSets": [
          {
            "phraseSet": "projects/[ID]/locations/europe-west3/phraseSets/bulgarian-safety-terms-ps"
          }
        ]
      }
    }
  },
  "content": "BASE64_ENCODED_AUDIO_BYTES"
}
```

### Ktor Implementation Outline
```kotlin
suspend fun transcribeFile(audioBytes: ByteArray, config: Language) {
    val response = client.post("https://speech.googleapis.com/v2/projects/...:recognize") {
        header("Authorization", "Bearer $SHORT_LIVED_TOKEN")
        setBody(RecognizeRequest(
            config = RecognitionConfig(
                languageCodes = listOf(config.languageCode),
                features = RecognitionFeatures(
                    adaptation = Adaptation(
                        phraseSets = listOf(PhraseSetContext(config.phraseSetId))
                    )
                )
            ),
            content = audioBytes.toBase64()
        ))
    }
}
```

## 2. Live Stream Transcription (gRPC via Wire)

When "Live Stream" mode is active, the app establishes a bidirectional gRPC stream.

### gRPC Service
`google.cloud.speech.v2.Speech/StreamingRecognize`

### Basic Setup Outline
1. **Define Proto**: Include Google's `cloud/speech/v2/cloud_speech.proto`.
2. **Initialize Client**:
```kotlin
val grpcClient = GrpcClient.Builder()
    .client(okHttpClient) // or native engine for KMM
    .baseUrl("https://speech.googleapis.com")
    .build()

val speechClient = grpcClient.create(SpeechClient::class)
```
3. **Stream Logic**:
```kotlin
val (requestChannel, responseFlow) = speechClient.StreamingRecognize().execute()

// First request must contain the StreamingConfig
requestChannel.send(StreamingRecognizeRequest(
    recognizer = config.recognizerId,
    streamingConfig = StreamingRecognitionConfig(
        config = RecognitionConfig(
            languageCodes = listOf(config.languageCode)
        )
    )
))

// Subsequent requests contain audio chunks
audioRecorder.startRecording().collect { chunk ->
    requestChannel.send(StreamingRecognizeRequest(audio = chunk))
}
```

## 3. Authentication (PoC)
For the PoC, we use a hardcoded Bearer token or Service Account JSON.
In production, this will be handled by the C# backend which will provide a short-lived access token.
