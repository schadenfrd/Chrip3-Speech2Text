import Foundation
import GRPCCore
import GRPCNIOTransportHTTP2
import GRPCProtobuf
import Shared

/// Modernized Swift implementation of the SpeechStreamer interface using gRPC Swift V2.
/// This version is refactored to decouple audio recording from network transport.
@available(iOS 15.0, *)
class IosSpeechStreamer: NSObject, SpeechStreamer {

    private var streamingTask: Task<Void, Never>?
    private var continuation: AsyncStream<Google_Cloud_Speech_V2_StreamingRecognizeRequest>.Continuation?
    private let recorder = IosAudioRecorder()

    func startStreaming(config: STTConfig, token: String, onResult: @escaping (TranscriptionResult) -> Void) {
        // Ensure any previous streaming is stopped
        stopStreaming()

        // 1. Create the AsyncStream to act as our request producer
        let (requestStream, continuation) = AsyncStream<Google_Cloud_Speech_V2_StreamingRecognizeRequest>.makeStream()
        self.continuation = continuation

        // 2. Start the gRPC streaming task
        streamingTask = Task {
            do {
                // Determine the correct host based on region
                let regionPrefix = config.language.recognizerId.contains("europe-west3") ? "europe-west3-" : ""
                let host = "\(regionPrefix)speech.googleapis.com"

                // Configure the new HTTP2 transport with Apple's native TransportServices TLS
                let transport = try HTTP2ClientTransport.TransportServices(
                    target: .dns(host: host, port: 443),
                    transportSecurity: .tls
                )

                let coreClient = GRPCClient(transport: transport)

                // Use a TaskGroup to manage the client lifecycle and the streaming call
                try await withThrowingTaskGroup(of: Void.self) { group in
                    group.addTask {
                        try await coreClient.runConnections()
                    }

                    // 2. Start capturing audio bytes from decoupled recorder and yield to continuation
                    group.addTask {
                        for await audioData in self.recorder.startRecording() {
                            let audioRequest = Google_Cloud_Speech_V2_StreamingRecognizeRequest.with {
                                $0.audio = audioData
                            }
                            self.continuation?.yield(audioRequest)
                        }
                    }

                    // 3. Execute the bidirectional streaming call
                    group.addTask {
                        let client = Google_Cloud_Speech_V2_Speech.Client(wrapping: coreClient)

                        // Setup authentication metadata (Headers)
                        var metadata = Metadata()
                        metadata.addString("Bearer \(token)", forKey: "authorization")

                        // Use the V2 Producer closure pattern with RPCWriter
                        let streamingRequest = GRPCCore.StreamingClientRequest(metadata: metadata) { writer in
                            for await requestMessage in requestStream {
                                try await writer.write(requestMessage)
                            }
                        }

                        // A. Send the initial configuration message (REQUIRED)
                        self.sendInitialConfig(config: config)

                        // B. Execute the bidirectional streaming call
                        try await client.streamingRecognize(request: streamingRequest) { response in
                            for try await message in response.messages {
                                self.handleResponse(message, onResult: onResult)
                            }
                        }
                    }

                    // Wait for the first task to finish or throw
                    try await group.next()
                    group.cancelAll()
                }
            } catch {
                if !(error is CancellationError) {
                    DispatchQueue.main.async {
                        onResult(TranscriptionResult.Error(message: "Stream Error: \(error.localizedDescription)"))
                    }
                }
            }

            // Cleanup when the task finishes
            self.cleanup()
        }
    }

    private func sendInitialConfig(config: STTConfig) {
        let streamingConfig = Google_Cloud_Speech_V2_StreamingRecognitionConfig.with {
            $0.config = Google_Cloud_Speech_V2_RecognitionConfig.with {
                $0.explicitDecodingConfig = Google_Cloud_Speech_V2_ExplicitDecodingConfig.with {
                    $0.encoding = .linear16
                    $0.sampleRateHertz = 16000
                    $0.audioChannelCount = 1
                }
                $0.model = "chirp_3"
                $0.languageCodes = [config.language.languageCode]
                $0.features = Google_Cloud_Speech_V2_RecognitionFeatures.with {
                    $0.enableAutomaticPunctuation = true
                }
                $0.adaptation = Google_Cloud_Speech_V2_SpeechAdaptation.with {
                    $0.phraseSets = [
                        Google_Cloud_Speech_V2_SpeechAdaptation.AdaptationPhraseSet.with {
                            $0.phraseSet = config.language.phraseSetId
                        }
                    ]
                }
            }
            $0.streamingFeatures = Google_Cloud_Speech_V2_StreamingRecognitionFeatures.with {
                $0.interimResults = true
            }
        }

        let firstRequest = Google_Cloud_Speech_V2_StreamingRecognizeRequest.with {
            $0.recognizer = config.language.recognizerId
            $0.streamingConfig = streamingConfig
        }

        continuation?.yield(firstRequest)
    }

    private func handleResponse(_ response: Google_Cloud_Speech_V2_StreamingRecognizeResponse, onResult: @escaping (TranscriptionResult) -> Void) {
        for result in response.results {
            guard let alternative = result.alternatives.first else { continue }
            let transcript = alternative.transcript

            // Update UI on the main thread
            DispatchQueue.main.async {
                if result.isFinal {
                    onResult(TranscriptionResult.Final(text: transcript))
                } else {
                    onResult(TranscriptionResult.Interim(text: transcript))
                }
            }
        }
    }

    func stopStreaming() {
        cleanup()
        streamingTask?.cancel()
        streamingTask = nil
    }

    private func cleanup() {
        // Finishing the continuation signals the request stream to close, 
        // which in turn allows the gRPC call and associated tasks to wind down.
        continuation?.finish()
        continuation = nil
    }
}
