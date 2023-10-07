import SwiftUI
import MobileVLCKit


func foo() {
}

@main
struct iOSApp: App {
	var body: some Scene {
		WindowGroup {
		    ZStack {
		        Color.black.ignoresSafeArea(.all) // status bar color
			    ContentView()
                
			}.preferredColorScheme(.light)
		}
	}
}
