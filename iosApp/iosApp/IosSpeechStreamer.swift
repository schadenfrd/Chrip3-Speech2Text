import AVFoundation
import Foundation
import GRPCCore
import GRPCNIOTransportHTTP2
import GRPCProtobuf
import Shared

/// Modernized Swift implementation of the SpeechStreamer interface using gRPC Swift V2.
/// This class leverages Swift Concurrency (async/await, Tasks, AsyncStream) for a
/// performant and clean streaming implementation.
@available(iOS 15.0, *)
class IosSpeechStreamer: NSObject, SpeechStreamer {

    private var streamingTask: Task<Void, Never>?
    private var continuation: AsyncStream<Google_Cloud_Speech_V2_StreamingRecognizeRequest>.Continuation?
    private let audioEngine = AVAudioEngine()

    func startStreaming(config: STTConfig, token: String, onResult: @escaping (TranscriptionResult) -> Void) {
        // Ensure any previous streaming is stopped
        stopStreaming()

        // 1. Setup Audio Session for recording
        setupAudioSession()

        // 2. Create the AsyncStream to act as our request producer
        let (requestStream, continuation) = AsyncStream<Google_Cloud_Speech_V2_StreamingRecognizeRequest>.makeStream()
        self.continuation = continuation

        // 3. Start the gRPC streaming task
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

                    group.addTask {
                        let client = Google_Cloud_Speech_V2_Speech.Client(wrapping: coreClient)

                        // Setup authentication metadata (Headers)
                        var metadata = Metadata()
                        metadata.addString("Bearer \(token)", forKey: "authorization")

                        // FIXED: Use the V2 Producer closure pattern with RPCWriter
                        let streamingRequest = GRPCCore.StreamingClientRequest(metadata: metadata) { writer in
                            for await requestMessage in requestStream {
                                try await writer.write(requestMessage)
                            }
                        }

                        // A. Send the initial configuration message (REQUIRED)
                        self.sendInitialConfig(config: config)

                        // B. Start capturing audio bytes and yielding them to the continuation
                        self.startAudioCapture()

                        // C. Execute the bidirectional streaming call
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

    private func setupAudioSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(
                .playAndRecord,
                mode: .measurement,
                options: [.defaultToSpeaker, .allowBluetoothHFP]
            )
            try session.setActive(true)
        } catch {
            print("Failed to setup audio session: \(error)")
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

    private func startAudioCapture() {
        let inputNode = audioEngine.inputNode
        let bus = 0
        let inputFormat = inputNode.inputFormat(forBus: bus)

        // Google STT V2 requires 16kHz, Mono, 16-bit PCM
        guard let outputFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: false) else {
            return
        }

        let converter = AVAudioConverter(from: inputFormat, to: outputFormat)!

        inputNode.installTap(onBus: bus, bufferSize: 1024, format: inputFormat) { [weak self] (buffer, time) in
            guard let self = self else { return }

            let pcmBuffer = AVAudioPCMBuffer(pcmFormat: outputFormat, frameCapacity: buffer.frameLength)!
            var error: NSError?

            let inputBlock: AVAudioConverterInputBlock = { inNumPackets, outStatus in
                outStatus.pointee = .haveData
                return buffer
            }

            converter.convert(to: pcmBuffer, error: &error, withInputFrom: inputBlock)

            if let channelData = pcmBuffer.int16ChannelData {
                // 16-bit = 2 bytes per frame
                let audioData = Data(bytes: channelData[0], count: Int(pcmBuffer.frameLength) * 2)

                let audioRequest = Google_Cloud_Speech_V2_StreamingRecognizeRequest.with {
                    $0.audio = audioData
                }

                // Yield the audio chunk to the gRPC request stream
                self.continuation?.yield(audioRequest)
            }
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
        } catch {
            print("Could not start AVAudioEngine: \(error)")
        }
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
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }

        continuation?.finish()
        continuation = nil
    }
}
