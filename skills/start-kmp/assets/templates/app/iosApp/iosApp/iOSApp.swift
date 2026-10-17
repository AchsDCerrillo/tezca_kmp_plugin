import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // BASE_URL comes from Configuration/Config.xcconfig through Info.plist (host + path, no scheme,
        // because "//" starts a comment in xcconfig files). CI overrides it per environment.
        let host = Bundle.main.object(forInfoDictionaryKey: "BASE_URL") as? String ?? "__BASE_URL_PROD_HOST__"
        #if DEBUG
        let isDebug = true
        #else
        let isDebug = false
        #endif
        KoinIosKt.doInitKoin(baseUrl: "https://\(host)", isDebug: isDebug)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
