# iOS notes

## App mode
- The wizard's Run Script build phase (`./gradlew :shared:embedAndSignAppleFrameworkForXcode`) compiles Kotlin on every Xcode build. The phase needs a JDK: Xcode inherits it from `JAVA_HOME` or `/usr/libexec/java_home`. If Xcode can't find Java, add `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` at the top of the phase.
- `ENABLE_USER_SCRIPT_SANDBOXING` must stay `NO` for that phase (the wizard sets it).
- `BASE_URL` is a build setting (`Config.xcconfig`), exposed through Info.plist and read in `iOSApp.swift`. CI overrides it with `xcargs: BASE_URL=…`.
- Bundle id / team: `Config.xcconfig` (`PRODUCT_BUNDLE_IDENTIFIER`, `TEAM_ID`). fastlane's `Appfile` and `Matchfile` read `IOS_BUNDLE_ID` and default to the wizard pattern `<appId>.<AppName>`.
- Deployment target: set `IPHONEOS_DEPLOYMENT_TARGET` in both build configurations of `project.pbxproj` to the minimum agreed with the user (default 16.0).

## SDK mode: consuming from Swift (SKIE)
```swift
import FitCoreSdk

@main
struct DemoApp: App {
    init() {
        FitCore.shared.initialize(config: AppConfig(baseUrl: "https://api.acme.com/", isDebug: false))
    }
    var body: some Scene { WindowGroup { ContentView() } }
}

@MainActor @Observable
final class ProfileModel {
    var name = ""
    func load() async {
        do {
            let result = try await FitCore.shared.profile.getProfile.invoke(id: 42)   // suspend -> async
            switch onEnum(of: result) {                                               // sealed -> exhaustive enum
            case .success(let s): name = s.data.fullName
            case .failure(let f): handle(f.error)
            }
        } catch { /* only CancellationException or bugs reach here */ }
    }
    func observe() async {
        for await workouts in FitCore.shared.workouts.observeWorkouts.invoke() { … }   // Flow -> AsyncSequence
    }
}
```
- The module is `FitCoreSdk` (XCFramework name); the facade object is `FitCore`. Never give a public type the module's name: SKIE renames it to `FitCoreSdk_`.
- Header inspection: SKIE's async/AsyncSequence/enum bridges are generated Swift, so `FitCoreSdk.h` still shows `Kotlinx_coroutines_coreFlow`. Check Swift API with a tiny `swiftc -typecheck` consumer instead.
- `DataResultSuccess<T>.data` is optional in Swift (`T?`) because Kotlin's `T` has no `Any` bound: unwrap it with `guard let`.
- Kotlin `object` → `.shared`; `data class` gets `doCopy(...)`; Kotlin `enum` → a Swift enum, with SKIE.
- Parameters marked `Int?` become `KotlinInt?`. Prefer non-null API parameters.
- The XCFramework is static, with bundled SQLite. It needs no extra linker flags unless a dependency adds native code (see add-sdk).

## Common failures
| Symptom | Fix |
|---|---|
| `No such module 'Shared'` in Xcode | build once (the Run Script produces it); check the scheme builds the iosApp target; delete DerivedData |
| `Undefined symbols … _OBJC_CLASS_$_…` | a native SDK used by Kotlin isn't in the app target (add it through SPM) |
| `Kotlin source set … not found` / `iosArm64 not configured` | Kotlin targets in `kmp.library` are iosArm64 + iosSimulatorArm64; Intel simulators (iosX64) aren't built, so use an Apple Silicon Mac or add `iosX64()` |
| SKIE error `Unsupported Kotlin compiler version` | pin Kotlin to a version SKIE supports, or upgrade SKIE (versions.md) |
| Very slow first build | the Kotlin/Native toolchain is downloading to `~/.konan`; later builds are incremental |
