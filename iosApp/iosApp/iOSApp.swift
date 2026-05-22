import SwiftUI
import Shared // Your shared KMM module

@main
struct iOSApp: App {
    
    // Do this inside init() so it happens before ANY UI is rendered
    init() {
        let streamer = IosSpeechStreamer()
        // Inject it into the Kotlin registry immediately
        SttFactory_iosKt.setIosSpeechStreamer(nativeStreamer: streamer)
    }

    var body: some Scene {
        WindowGroup {
            ContentView() // Your Compose UI wrapper
        }
    }
}
