import SwiftUI
import MobileVLCKit


func foo() {
}

@main
struct iOSApp: App {
    @Environment(\.scenePhase) var scenePhase
    @State private var shouldRender: Bool = true // Boolean state


    var body: some Scene {
        WindowGroup {
            ZStack {
                Color.black.ignoresSafeArea(.all) // status bar color

                ContentView()
                    .opacity(shouldRender ? 1.0 : 0.0)
            }
            .preferredColorScheme(.light)
            .onChange(of: scenePhase) { newPhase in
                if newPhase != .active {
                    shouldRender = false
                } else {
                    shouldRender = true
                }
            }
        }
    }
}
