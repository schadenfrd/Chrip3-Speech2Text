# Google Cloud STT V2 Proto Setup for Wire

To enable gRPC streaming with Square Wire, you need to place the following Google Cloud `.proto` files in the `shared/src/commonMain/proto` directory. These files are part of the [google-apis](https://github.com/googleapis/googleapis) repository.

## Required Directory Structure

```text
shared/src/commonMain/proto/
├── google/
│   ├── api/
│   │   ├── annotations.proto
│   │   ├── client.proto
│   │   ├── field_behavior.proto
│   │   ├── http.proto
│   │   └── resource.proto
│   ├── cloud/
│   │   └── speech/
│   │       └── v2/
│   │           └── cloud_speech.proto
│   ├── longrunning/
│   │   └── operations.proto
│   ├── protobuf/
│   │   ├── any.proto
│   │   ├── duration.proto
│   │   ├── empty.proto
│   │   ├── field_mask.proto
│   │   ├── struct.proto
│   │   ├── timestamp.proto
│   │   └── wrappers.proto
│   └── rpc/
│       └── status.proto
```

## How to use in `SttRepository.kt`

Once the `.proto` files are in place and the project is built, Wire will generate the `SpeechClient` and request/response models. You can then uncomment the `streamAudio` function in `SttRepository.kt`.

## Gradle Configuration (already in build.gradle.kts)

```kotlin
wire {
    kotlin {
        rpcRole = "client"
    }
}
```
