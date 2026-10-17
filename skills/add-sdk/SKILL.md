---
name: add-sdk
description: Integrate a third-party SDK or library into a Kotlin Multiplatform project from its documentation URL, for example Firebase (Analytics, Crashlytics, Messaging, Auth), RevenueCat, Sentry, Coil, Google Maps, Stripe or Mixpanel, or any Maven/SPM/CocoaPods dependency. It asks for the docs URL, reads it, decides whether the library is multiplatform, has a KMP wrapper, or is native-only on each platform, then adds the version catalog entries, Gradle and source-set dependencies, Android manifest and Gradle plugin setup, iOS Swift Package/CocoaPods setup, a common interface with expect/actual or platform implementations, Koin wiring and initialization. It also guides the user through the console and Xcode steps that can't be automated. Use it whenever the user wants to add, install, integrate or set up an SDK, library or dependency in a KMP or Compose Multiplatform app or SDK, even if they just paste a library link or name.
---

# add-sdk: integrate a library the KMP way

The goal is that common code depends on **one interface** that works on both platforms, whatever shape the library comes in. Platform details stay in `androidMain` / `iosMain` or in the native apps, and the user gets a checklist for everything that needs a console, an account or Xcode.

## 1. Gather inputs
Ask with AskUserQuestion, or take what the user gave:
1. **Documentation URL(s).** Ask for the official docs. If the library has separate Android and iOS guides, ask for both, plus the KMP guide if one exists. If the user only gives a name, find the official docs yourself (web search) and confirm the URL with them.
2. **What they need from it.** For example "crash reporting + non-fatal logging", or "paywall + entitlement check". This decides which APIs to wrap. Wrap only those, not the whole SDK.
3. **Where it's used:** shared code (most cases), Android only, or iOS only.
4. Any keys or IDs they already have (API key, `google-services.json`, `GoogleService-Info.plist`). Never ask them to paste secrets into the chat. Explain where the value goes instead (`local.properties`, CI secret, xcconfig).

Read `.tezca-kmp.json` and `../kmp-architecture/SKILL.md`. In SDK mode, also decide whether this library becomes a **transitive dependency of the SDK** for consumers, and say so. That matters for licensing and binary size, and for iOS, where consumers must add the native SDK themselves if you only link against it.

## 2. Read the docs, then classify
Fetch the URL(s) and extract:
- artifact coordinates and the latest stable version
- Gradle plugins
- manifest entries and permissions
- the iOS install method (SPM URL / pod), minimum OS versions
- initialization code and its location (Application, AppDelegate)
- the API calls the user needs

Verify the version against Maven Central, Google Maven or the GitHub releases (`../start-kmp/references/versions.md` shows how), and confirm it's compatible with the project's Kotlin version.

Classify the library. This decides the integration recipe, detailed in `references/recipes.md`:

| Shape | Example | Recipe |
|---|---|---|
| **A. Multiplatform library** (has iOS/native artifacts on Maven) | Coil 3, Kermit, Ktor plugins, Supabase-kt, multiplatform-settings | add to `commonMain`, wire in Koin |
| **B. Community KMP wrapper over native SDKs** | `dev.gitlive:firebase-*`, RevenueCat KMP (`purchases-kmp`), `sentry-kotlin-multiplatform` | wrapper in `commonMain`, **plus** the native SDK on iOS via SPM/CocoaPods (wrappers usually don't bundle it) |
| **C. Native-only SDKs** (Android AAR + iOS SPM/Pod) | Stripe, Google Maps, Mixpanel, most analytics / payments | common interface + Android `actual` using the AAR + iOS implementation in **Swift**, injected into Kotlin (see recipe C) |
| **D. Platform-only** (only one platform needs it) | Play In-App Review, Apple Sign In | interface in common, implementation on that platform, no-op or a different provider on the other |

Prefer A, then B, then C. Tell the user which shape you found and why. When a wrapper is unmaintained (last release more than a year ago, or no support for the project's Kotlin version), say so and use C instead.

## 3. Integrate
Follow the recipe. Every recipe ends with these:
- **Version catalog:** add entries in `gradle/libs.versions.toml`, grouped and commented with the library name. Never hardcode versions in build files.
- **Interface in common code:**
  - App mode: in a `:core:*` module if several features use it (e.g. `:core:analytics`, created with the `kmp.library` convention), or in the one feature that needs it.
  - SDK mode: in a `core.<name>` package, `internal` unless consumers must see it.
  - Name it by capability (`AnalyticsTracker`, `CrashReporter`, `PurchaseManager`), not by vendor, so the vendor can be swapped later.
- **Koin binding:** in the owning module, plus a fake for tests in `:core:testing` / `commonTest`.
- **Initialization:**
  - Android: `MainApplication.onCreate()` before or after `initKoin` as the SDK requires.
  - iOS: `iOSApp.init()`, or an `AppDelegate` via `@UIApplicationDelegateAdaptor` when the SDK needs `application(_:didFinishLaunchingWithOptions:)` or push callbacks.
  - SDK mode: inside `<Sdk>.initialize(...)` only if the SDK owns that dependency. Otherwise document that the host app must initialize it.
- **Android specifics:** Gradle plugins (in the root `build.gradle.kts` with `apply false`, then applied in `androidApp`), manifest entries and permissions, ProGuard/R8 rules if the docs list any, and `minSdk` if the library requires a higher one. Ask before raising it.
- **iOS specifics:** see `references/ios-dependencies.md`.
  - **App mode:** add the Swift package to `iosApp.xcodeproj`. Explain the Xcode steps (File → Add Package Dependencies → URL → version rule → target), because editing `project.pbxproj` by hand is fragile. Or, if the user agrees, edit `project.pbxproj` carefully and confirm with `xcodebuild`.
  - **Kotlin code calling a native iOS SDK directly** (cinterop): only when the docs provide a Kotlin/Native route (e.g. through the KMP wrapper's instructions). Otherwise prefer recipe C's Swift-implemented interface.
- **Secrets and config files:** say where each key goes (`local.properties` + `buildConfigField`, `Config.xcconfig` + Info.plist, CI secret) and update `docs/ci-secrets.md` and the workflows if CI needs them. Add `google-services.json` and `GoogleService-Info.plist` to `.gitignore` only if the user wants them out of git, and then provide them in CI from secrets.

## 4. Verify
```bash
./gradlew :androidApp:assembleDevDebug            # or the SDK module: :<sdk>:testAndroidHostTest
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64   # SDK: assemble<Sdk>DebugXCFramework
./gradlew testAndroidHostTest                     # KoinGraphTest picks up the new bindings
```
For iOS changes in app mode, build with `xcodebuild … CODE_SIGNING_ALLOWED=NO build` if the Xcode project was edited. If you can't verify something here (a console setting, a device-only feature such as push), say so.

## 5. Hand over a checklist
End with a short, numbered list of what the user must do outside the code, with links from the docs you read. For example:
1. Create the Firebase project and register the Android app `com.acme.gym` and `com.acme.gym.dev`.
2. Download `google-services.json` into `androidApp/`.
3. In Xcode, add `GoogleService-Info.plist` to the iosApp target.
4. Add the `FIREBASE_*` CI secrets.

Then show the one-line usage from common code (`get<AnalyticsTracker>().track("workout_started")`).
