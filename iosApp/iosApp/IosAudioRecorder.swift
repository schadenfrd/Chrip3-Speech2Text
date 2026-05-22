import AVFoundation

@available(iOS 15.0, *)
class IosAudioRecorder {
    private let audioEngine = AVAudioEngine()
    private var audioDataBuffer = Data()
    
    func startRecording() -> AsyncStream<Data> {
        let (stream, continuation) = AsyncStream<Data>.makeStream()
        
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker, .allowBluetoothHFP])
            try session.setActive(true)
        } catch {
            print("Failed to setup audio session: \(error)")
            continuation.finish()
            return stream
        }
        
        let inputNode = audioEngine.inputNode
        let bus = 0
        let inputFormat = inputNode.inputFormat(forBus: bus)
        
        guard let outputFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: false) else {
            continuation.finish()
            return stream
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
            print("Could not start AVAudioEngine: \(error)")
            continuation.finish()
        }
        
        // Handle stream cancellation/cleanup dynamically
        continuation.onTermination = { [weak self] _ in
            self?.stopRecording()
        }
        
        return stream
    }
    
    private func stopRecording() {
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }
        audioDataBuffer.removeAll()
    }
}
