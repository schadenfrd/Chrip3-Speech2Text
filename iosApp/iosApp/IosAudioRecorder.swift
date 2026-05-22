import AVFoundation

@available(iOS 15.0, *)
class IosAudioRecorder {
    private let audioEngine = AVAudioEngine()
    private var audioDataBuffer = Data()
    
    func startRecording() -> AsyncThrowingStream<Data, Error> {
        return AsyncThrowingStream { continuation in
            let session = AVAudioSession.sharedInstance()
            
            // --- CRITICAL: Permission Guard ---
            if session.recordPermission == .denied || session.recordPermission == .undetermined {
                continuation.finish(throwing: NSError(domain: "IosAudioRecorder", code: 1, userInfo: [NSLocalizedDescriptionKey: "Microphone permission denied or undetermined"]))
                return
            }
            
            // --- CRITICAL: Interruption Handling ---
            let observer = NotificationCenter.default.addObserver(
                forName: AVAudioSession.interruptionNotification,
                object: nil,
                queue: .main
            ) { [weak self] notification in
                guard let userInfo = notification.userInfo,
                      let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
                      let type = AVAudioSession.InterruptionType(rawValue: typeValue) else { return }
                
                if type == .began {
                    print("Audio interruption began")
                    self?.stopRecording()
                    continuation.finish(throwing: NSError(domain: "IosAudioRecorder", code: 2, userInfo: [NSLocalizedDescriptionKey: "Audio interruption began (e.g. phone call)"]))
                }
            }

            do {
                try session.setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker, .allowBluetoothHFP])
                try session.setActive(true)
            } catch {
                continuation.finish(throwing: error)
                return
            }
            
            let inputNode = audioEngine.inputNode
            let bus = 0
            let inputFormat = inputNode.inputFormat(forBus: bus)
            
            guard let outputFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: false) else {
                continuation.finish(throwing: NSError(domain: "IosAudioRecorder", code: 3, userInfo: [NSLocalizedDescriptionKey: "Failed to create output format"]))
                return
            }
            
            let converter = AVAudioConverter(from: inputFormat, to: outputFormat)!
            
            inputNode.installTap(onBus: bus, bufferSize: 4096, format: inputFormat) { [weak self] (buffer, time) in
                guard let self = self else { return }
                
                let pcmBuffer = AVAudioPCMBuffer(pcmFormat: outputFormat, frameCapacity: buffer.frameLength)!
                var error: NSError?
                
                var hasConsumedInput = false
                let inputBlock: AVAudioConverterInputBlock = { inNumPackets, outStatus in
                    if hasConsumedInput {
                        outStatus.pointee = .noDataNow
                        return nil
                    }
                    hasConsumedInput = true
                    outStatus.pointee = .haveData
                    return buffer
                }
                
                converter.convert(to: pcmBuffer, error: &error, withInputFrom: inputBlock)
                
                if let channelData = pcmBuffer.int16ChannelData {
                    let length = Int(pcmBuffer.frameLength) * 2
                    let convertedData = Data(bytes: channelData[0], count: length)
                    
                    self.audioDataBuffer.append(convertedData)
                    
                    while self.audioDataBuffer.count >= 3200 {
                        let chunk = self.audioDataBuffer.prefix(3200)
                        self.audioDataBuffer.removeFirst(3200)
                        continuation.yield(chunk)
                    }
                }
            }
            
            audioEngine.prepare()
            do {
                try audioEngine.start()
            } catch {
                continuation.finish(throwing: error)
            }
            
            // Handle stream cancellation/cleanup dynamically
            continuation.onTermination = { [weak self] _ in
                NotificationCenter.default.removeObserver(observer)
                self?.stopRecording()
            }
        }
    }
    
    private func stopRecording() {
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }
        audioDataBuffer.removeAll()
    }
}
