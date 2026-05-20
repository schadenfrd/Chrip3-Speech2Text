import Foundation
import Shared
import GRPC
import NIO
import AVFoundation

/**
 * Swift implementation of the SpeechStreamer interface.
 * This class uses grpc-swift and SwiftNIO for bidirectional HTTP/2 streaming,
 * as it provides more robust support for streaming on iOS compared to KMP-native solutions.
 */
class IosSpeechStreamer: NSObject, SpeechStreamer {
    
    private var group: EventLoopGroup?
    private var channel: ClientConnection?
    private var call: BidirectionalStreamingCall<Google_Cloud_Speech_V2_StreamingRecognizeRequest, Google_Cloud_Speech_V2_StreamingRecognizeResponse>?
    
    private let audioEngine = AVAudioEngine()
    
    func startStreaming(config: STTConfig, token: String, onResult: @escaping (TranscriptionResult) -> Void) {
        // 1. Initialize NIO EventLoopGroup (typically 1 thread for mobile)
        group = MultiThreadedEventLoopGroup(numberOfThreads: 1)
        
        // Determine the correct host based on region (e.g., europe-west3-speech.googleapis.com)
        let regionPrefix = config.language.recognizerId.contains("europe-west3") ? "europe-west3-" : ""
        let host = "\(regionPrefix)speech.googleapis.com"
        
        // 2. Create the gRPC Channel with TLS
        channel = ClientConnection.usingPlatformAppropriateTLS(on: group!)
            .connect(host: host, port: 443)
        
        // 3. Instantiate the generated gRPC Client
        let client = Google_Cloud_Speech_V2_SpeechClient(channel: channel!)
        
        // 4. Setup Call Options (Google Cloud requires Bearer Token in Metadata)
        var options = CallOptions()
        options.customMetadata.add(name: "Authorization", value: "Bearer \(token)")
        
        // 5. Open the Bidirectional Stream
        call = client.streamingRecognize(callOptions: options) { response in
            // Handle results as they arrive from the server
            for result in response.results {
                guard let alternative = result.alternatives.first else { continue }
                let transcript = alternative.transcript
                
                // Dispatch back to Main thread for UI updates
                DispatchQueue.main.async {
                    if result.isFinal {
                        onResult(TranscriptionResult.Final(text: transcript))
                    } else {
                        onResult(TranscriptionResult.Interim(text: transcript))
                    }
                }
            }
        }
        
        // 6. Send initial StreamingConfig (Required first message)
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
            }
            $0.streamingFeatures = Google_Cloud_Speech_V2_StreamingRecognitionFeatures.with {
                $0.interimResults = true
            }
        }
        
        let firstRequest = Google_Cloud_Speech_V2_StreamingRecognizeRequest.with {
            $0.recognizer = config.language.recognizerId
            $0.streamingConfig = streamingConfig
        }
        
        _ = call?.sendMessage(firstRequest)
        
        // 7. Start capturing audio via AVAudioEngine
        startAudioCapture()
    }
    
    private func startAudioCapture() {
        let inputNode = audioEngine.inputNode
        let bus = 0
        let inputFormat = inputNode.inputFormat(forBus: bus)
        
        // Output format must be 16kHz Mono PCM for STT
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
                let audioData = Data(bytes: channelData[0], count: Int(pcmBuffer.frameLength) * 2)
                
                let audioRequest = Google_Cloud_Speech_V2_StreamingRecognizeRequest.with {
                    $0.audio = audioData
                }
                
                // Stream the audio bytes to Google
                _ = self.call?.sendMessage(audioRequest)
            }
        }
        
        audioEngine.prepare()
        do {
            try audioEngine.start()
        } catch {
            print("Could not start AVAudioEngine: \(error)")
        }
    }
    
    func stopStreaming() {
        // Stop audio engine
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        
        // Gracefully close the gRPC stream
        _ = call?.sendEnd()
        
        // Shutdown NIO group
        try? group?.syncShutdownGracefully()
        
        group = nil
        channel = nil
        call = nil
    }
}
