# iOS dependencies in a KMP project

## App mode (Compose Multiplatform, `iosApp.xcodeproj` + static `Shared` framework)
- **Swift Package Manager (preferred).** In Xcode: *File → Add Package Dependencies…*, paste the package URL, pick a version rule (*Up to Next Major* from the docs' version), and add the products to the **iosApp** target. SPM resolves on the next build and CI picks it up from `project.pbxproj` + `Package.resolved`. Commit both.
- **Editing `project.pbxproj` without Xcode** works, but you have to add five object kinds by hand: `XCRemoteSwiftPackageReference`, `XCSwiftPackageProductDependency`, a `PBXBuildFile` entry, the framework build phase entry, and `packageReferences` / `packageProductDependencies`. Only do this if the user can't use Xcode. Copy the structure from an existing package entry and generate new 24-hex-character IDs. Always confirm with `xcodebuild -resolvePackageDependencies` and a build.
- **CocoaPods.** Only use it if the vendor has no SPM package. That means a `Podfile` in `iosApp/` and opening the `.xcworkspace` from then on. Don't mix it with the Kotlin CocoaPods Gradle plugin unless the whole project switches to it.
- **Kotlin calling the native SDK.** The `Shared` framework is static, so its references to native symbols are resolved when the **app** links. The app target must therefore contain the native SDK. KMP wrapper libraries (GitLive Firebase, purchases-kmp) document exactly which SPM products to add. Missing products show up as `Undefined symbols for architecture arm64: _OBJC_CLASS_$_…` at link time.

## SDK mode (XCFramework distributed through SPM)
- **Native SDKs used by the SDK through a wrapper** (shape B): the XCFramework can't bundle them. Consumers must add the native package next to yours. Either document it in the SDK README, or publish a **wrapper package** whose target depends on both the binary target and the native package:
```swift
// Package.swift in the SPM repo
.target(
    name: "FitCoreSdkWrapper",
    dependencies: [
        "FitCoreSdk",                                                     // binaryTarget
        .product(name: "FirebaseCrashlytics", package: "firebase-ios-sdk"),
    ],
    path: "Sources/FitCoreSdkWrapper"                                    // one file: @_exported import FitCoreSdk
),
```
  Then point the library product at the wrapper target. Update `swiftpackage/Package.swift.template` so CI keeps generating it.
- **Prefer recipe C** (the host app injects a Swift implementation) for analytics, crash reporting and similar. It keeps vendor binaries out of the SDK, and each consumer chooses its vendor.
- **System frameworks and libraries** a dependency needs (e.g. `-lc++`, `-framework StoreKit`): add `linkerOpts` to the framework in the SDK module's build file only if consumers would otherwise fail to link, and mirror them in the wrapper target's `linkerSettings`.

## Info.plist / capabilities checklist
Native SDKs often need:
- usage descriptions (`NSCameraUsageDescription`, `NSLocationWhenInUseUsageDescription`, `NSUserTrackingUsageDescription`)
- URL schemes (`CFBundleURLTypes` for OAuth redirects)
- `LSApplicationQueriesSchemes`
- capabilities (Push Notifications, Sign in with Apple, Associated Domains), which also need the matching App ID setting in the Apple Developer portal and a regenerated provisioning profile (`fastlane match … --force`).

List every one you add in the hand-over checklist.
