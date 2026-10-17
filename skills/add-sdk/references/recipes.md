# add-sdk recipes

## Contents
- A. Multiplatform library
- B. KMP wrapper over native SDKs
- C. Native-only SDKs (Kotlin interface, Swift implementation)
- D. Platform-only capability
- Creating a `:core:<name>` module (app mode)

## A. Multiplatform library (example: Kermit logging)
```toml
# libs.versions.toml
kermit = "2.0.5"
kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }
```
```kotlin
// owning module build.gradle.kts
kotlin { sourceSets { commonMain.dependencies { implementation(libs.kermit) } } }
```
```kotlin
// common interface (keeps call sites vendor-neutral)
interface AppLogger { fun d(tag: String, message: String); fun e(tag: String, message: String, throwable: Throwable? = null) }

internal class KermitAppLogger : AppLogger {
    override fun d(tag: String, message: String) = Logger.withTag(tag).d { message }
    override fun e(tag: String, message: String, throwable: Throwable?) = Logger.withTag(tag).e(throwable) { message }
}
// Koin: single<AppLogger> { KermitAppLogger() }
```
For UI libraries such as Coil 3 (`io.coil-kt.coil3:coil-compose` + `coil-network-ktor3`), add them to the feature or `:core:ui` `commonMain` and use them directly in composables. Wrapping composables in an interface isn't useful.

## B. KMP wrapper over native SDKs (example: Firebase Crashlytics via GitLive)
1. Add the wrapper to the catalog and `commonMain`: `dev.gitlive:firebase-crashlytics`. Check its supported Kotlin version.
2. **Android:**
   - Add the `com.google.gms.google-services` and `com.google.firebase.crashlytics` plugins with `apply false` at the root, then apply them in `androidApp`.
   - Put `google-services.json` in `androidApp/`. With dev/prod flavors, use `androidApp/src/dev/google-services.json` and `androidApp/src/prod/…`, or register both application IDs in one Firebase project.
3. **iOS:**
   - The wrapper links against the native Firebase SDK but doesn't ship it. Add `https://github.com/firebase/firebase-ios-sdk` with the products `FirebaseCrashlytics` and `FirebaseCore` to the iosApp target with SPM, at the version the wrapper's docs name.
   - Add `GoogleService-Info.plist` to the target.
   - Call `FirebaseApp.configure()` in `iOSApp.init()` before `doInitKoin`.
   - Static frameworks may fail to link with "Undefined symbols _OBJC_CLASS_$_FIRCrashlytics". The app target must contain the native package, and the KMP framework only references it.
4. **Common interface:**
```kotlin
interface CrashReporter {
    fun log(message: String)
    fun recordNonFatal(throwable: Throwable)
    fun setUserId(id: String?)
}
internal class FirebaseCrashReporter : CrashReporter {
    private val crashlytics get() = Firebase.crashlytics
    override fun log(message: String) = crashlytics.log(message)
    override fun recordNonFatal(throwable: Throwable) = crashlytics.recordException(throwable)
    override fun setUserId(id: String?) = crashlytics.setUserId(id ?: "")
}
```
5. **Initialization:** on Android, the Firebase plugin auto-initializes through its ContentProvider.
6. **Crashlytics on iOS also needs dSYM upload:** add the Crashlytics run script build phase described in the docs and mention it in the checklist. The KMP framework has its own dSYM; `embedAndSignAppleFrameworkForXcode` puts it next to the app's.

## C. Native-only SDKs: Kotlin interface, Swift implementation (example: Mixpanel)
Common code needs analytics, but Mixpanel has no KMP artifact. Calling the iOS SDK through cinterop from Kotlin is possible only for Objective-C-compatible APIs and needs a CocoaPods/SPM-to-cinterop setup. Implementing the interface in Swift is simpler and more robust.

1. **Common interface** (in `:core:analytics` or the SDK's `core.analytics`):
```kotlin
interface AnalyticsTracker {
    fun track(event: String, properties: Map<String, String> = emptyMap())
    fun identify(userId: String)
    fun reset()
}
```
2. **Android implementation** in `androidMain` (the AAR goes in `androidMain.dependencies { implementation(libs.mixpanel.android) }`):
```kotlin
internal class MixpanelAnalyticsTracker(context: Context, token: String) : AnalyticsTracker {
    private val mixpanel = MixpanelAPI.getInstance(context, token, /* trackAutomaticEvents = */ false)
    override fun track(event: String, properties: Map<String, String>) = mixpanel.track(event, JSONObject(properties))
    override fun identify(userId: String) = mixpanel.identify(userId)
    override fun reset() = mixpanel.reset()
}
// androidMain Koin platform module: single<AnalyticsTracker> { MixpanelAnalyticsTracker(androidContext(), BuildConfig.MIXPANEL_TOKEN) }
```
   In app mode, `BuildConfig` lives in `androidApp`. Pass the token through `AppConfig`, or bind the tracker from `MainApplication` with an extra Koin module (`initKoin(config) { modules(module { single<AnalyticsTracker> { … } }) }`).
3. **iOS implementation** in Swift, in the iosApp target, with the SDK added through SPM:
```swift
import Shared
import Mixpanel

final class MixpanelAnalyticsTracker: AnalyticsTracker {
    init(token: String) { Mixpanel.initialize(token: token, trackAutomaticEvents: false) }
    func track(event: String, properties: [String: String]) { Mixpanel.mainInstance().track(event: event, properties: properties) }
    func identify(userId: String) { Mixpanel.mainInstance().identify(distinctId: userId) }
    func reset() { Mixpanel.mainInstance().reset() }
}
```
4. **Hand the Swift object to Koin.** Extend the iOS init helper in `shared/src/iosMain/.../di/KoinIos.kt`:
```kotlin
fun doInitKoin(baseUrl: String, isDebug: Boolean, analytics: AnalyticsTracker) {
    initKoin(AppConfig(baseUrl, isDebug)) {
        modules(module { single<AnalyticsTracker> { analytics } })
    }
}
```
```swift
KoinIosKt.doInitKoin(baseUrl: "https://\(host)", isDebug: isDebug, analytics: MixpanelAnalyticsTracker(token: token))
```
   - Kotlin interfaces become Swift protocols, so the Swift class conforms directly.
   - Default arguments aren't visible from Swift. Pass every parameter.
   - In **SDK mode**, the same pattern becomes a parameter of `<Sdk>.initialize(config:analytics:)`, so the host app supplies the native implementation. It's the cleanest way to keep a native-only vendor out of the SDK binary.
   - Update `KoinGraphTest` `extraTypes` if the interface is now provided from outside the module list.

## D. Platform-only capability (example: Google Play In-App Review)
```kotlin
// common
interface ReviewPrompter { suspend fun requestReview() }
// androidMain: Play Core implementation needs an Activity; take it from a small ActivityProvider
// set in MainActivity.onResume, or trigger the flow from the androidApp module.
// iosMain:
internal class IosReviewPrompter : ReviewPrompter {
    override suspend fun requestReview() {
        val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull() as? UIWindowScene ?: return
        SKStoreReviewController.requestReviewInScene(scene)
    }
}
```
Platform Koin modules (`expect val platformXModule: Module`) bind the right implementation, the same way `:core:network` binds its engine.

## Creating `:core:<name>` (app mode)
1. `core/<name>/build.gradle.kts`: `plugins { alias(libs.plugins.kmp.library) }`, plus the dependencies.
2. `settings.gradle.kts`: `include(":core:<name>")`.
3. Put the sources in `core/<name>/src/commonMain/kotlin/<basePath>/core/<name>/…`, and `di/<Name>Module.kt` exposing `val <name>Module`.
4. Add `<name>Module` to `appModules()` in `shared/.../di/KoinInit.kt`, and `implementation(projects.core.<name>)` to `:shared`. Features that use it get `implementation(projects.core.<name>)` in their own build file. Only add it to `KmpFeatureConventionPlugin` if most features use it.
