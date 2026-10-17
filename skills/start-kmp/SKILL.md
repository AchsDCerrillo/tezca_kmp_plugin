---
name: start-kmp
description: Set up a Kotlin Multiplatform project for Android + iOS, either as an SDK/library only or as a full Compose Multiplatform app. Adds and configures Koin, Room 3 (KMP), Ktor, DataStore, kotlinx-datetime, kotlinx-serialization, kotlinx-coroutines, the multiplatform lifecycle ViewModel, Navigation Compose, and SKIE. It also generates convention plugins, core modules, Koin bootstrap on both platforms, a ready HttpClient and Room database, iOS integration, and GitHub Actions + fastlane CI/CD: dev pushes go to Firebase App Distribution, releases go to Play Store/TestFlight, and SDKs publish to Maven + SPM. Use it whenever the user wants to start, bootstrap, initialize, scaffold or "add the dependencies/config" to a KMP or Compose Multiplatform project, convert the JetBrains wizard template into a real architecture, choose between SDK and app, or set up KMP CI/CD, even if they only say "prepare the project" or "configure Koin/Room/Ktor".
---

# start-kmp: set up a KMP SDK or Compose Multiplatform app

The result is a project that builds and tests on both platforms, with the networking, database, preferences and DI stack already wired. Every later skill (`new-feature`, `make-*`, `add-tests`, `add-sdk`) builds on what this one generates, so follow the conventions in `../kmp-architecture/SKILL.md`.

Most of the work is deterministic, so it's done by `scripts/scaffold.py`, which renders `assets/templates/`. Your job is to ask the right questions, run the script, merge the files that already existed, refresh versions, verify the build, and explain what's left for the user (Firebase, signing, secrets).

## 1. Inspect the project
- Look for `settings.gradle.kts`, `gradle/libs.versions.toml`, `gradlew`, `shared/`, `androidApp/`, `iosApp/` and `.tezca-kmp.json`.
- **`.tezca-kmp.json` exists:** the project is already set up. Offer to refresh versions, add CI, or switch nothing. Don't re-scaffold over it.
- **Empty folder, or no `gradlew`:** ask the user to create the base with the [KMP wizard](https://kmp.jetbrains.com) or Android Studio's *New Project → Kotlin Multiplatform*:
  - app: Android + iOS with **Share UI**
  - SDK: Android + iOS with **Do not share UI**

  The wizard provides the Gradle wrapper and a working `iosApp.xcodeproj`, which can't be generated reliably by hand. If they can't, and `gradle` is installed, run `gradle wrapper --gradle-version <latest>` and continue in SDK mode only.
- **Detect the template generation.**
  - **Modern** (AGP 9): `com.android.kotlin.multiplatform.library` with `kotlin { android { … } }`.
  - **Legacy:** `com.android.library` + `androidTarget()`. Say that the templates move it to AGP 9's KMP library plugin. Upgrading AGP is part of this setup; the `agp-9-upgrade` skill can help if the project has custom Android config.
- Note the existing package (the `namespace` in `shared/build.gradle.kts` or the Kotlin sources) and the app name (`rootProject.name`). Offer them as defaults.

## 2. Ask (one AskUserQuestion round, defaults pre-filled)
1. **Project type**:
   - **SDK only:** a library consumed by Android and iOS apps; no UI.
   - **Full app:** Compose Multiplatform UI for Android + iOS, MVVM, one module per feature.

   This is the key question. Explain the difference in one line each.
2. **Base package** (e.g. `com.acme.gym`) and **app/SDK name** (e.g. `KratosGym`). In SDK mode the XCFramework is `<Name>Sdk` and the public facade object `<Name>`.
3. **API base URL** for prod and dev (host + path, e.g. `api.acme.com/v1/`). Placeholders are fine.
4. **Minimum iOS version** (default `16.0`). Mention that Compose Multiplatform needs iOS 15+.
5. **CI/CD**: yes or no, plus the GitHub owner/repo for SDK Maven publishing. Default yes.
6. App mode only: start with an empty welcome screen, or create the first feature right away (runs `new-feature`).

## 3. Resolve versions
Read `references/versions.md`. If its matrix is less than about a month old and the project's Kotlin/AGP/Compose versions match it, keep the pins and skip the lookups. Otherwise look up the newest releases. It has the pinned, known-good matrix and the commands to look up the latest stable releases.
- Use the newest **stable** versions that are compatible with each other.
- Keep the project's own Kotlin, AGP, Compose Multiplatform and Gradle versions if they're newer than the pins.
- SKIE must support the chosen Kotlin version (check its release notes). If it doesn't, keep the Kotlin version SKIE supports, or fall back to KMP-NativeCoroutines and tell the user.

Edit the rendered `gradle/libs.versions.toml` after step 4 if the versions changed.

## 4. Render the templates
```bash
python3 -I <this-skill>/scripts/scaffold.py --mode app|sdk --dest <project> \
  --base-package com.acme.gym --app-name KratosGym [--app-id com.acme.gym] \
  [--sdk-name KratosGymSdk --sdk-facade KratosGym --sdk-module shared --github-owner acme] \
  --base-url-prod api.acme.com/ --base-url-dev dev.api.acme.com/ --ios-min 16.0 \
  --staging <scratch>/tezca-kmp-staging
```
Run it with `--dry-run` first and show the user the plan: created files, conflicts and appended lines.

The script never overwrites. **Conflicts** are files that already exist, usually the wizard's `settings.gradle.kts`, `build.gradle.kts`, `libs.versions.toml`, `shared/build.gradle.kts`, `androidApp/build.gradle.kts`, `MainActivity.kt`, `App.kt` and the Swift files. For each conflict:
- Diff the existing file against the staged version.
- If the existing file is still the wizard default, take the staged version (re-run with `--force`, which writes `.bak` copies, or copy the file).
- If the user customized it, merge by hand. Keep their additions (extra dependencies, plugins, manifest entries, catalog entries) and adopt the template's structure.

Then remove the wizard's sample code that is now unused: `Greeting.kt`, `GreetingUtil.kt`, `Platform.kt`, `Platform.android.kt`, `Platform.ios.kt`, the sample tests (`SharedCommonTest`, `SharedLogicAndroidHostTest`, `SharedLogicIOSTest`) and the `compose-multiplatform.xml` drawable. Delete the `.bak` files once merged. Files that differ only in trailing whitespace are reported as `identical`, not as conflicts.

What the templates contain is listed in `references/generated-layout.md`. Read it if you need to explain or adjust a file.

## 5. Write `.tezca-kmp.json`
```json
{ "mode": "app", "basePackage": "com.acme.gym", "rootModule": ":shared", "sdkName": null,
  "appId": "com.acme.gym", "interop": "none", "features": [], "pluginVersion": "0.1.0" }
```
SDK mode uses `"mode": "sdk"`, `"sdkName": "KratosGymSdk"` (XCFramework / Swift module), `"sdkFacade": "KratosGym"` (public Kotlin object; must differ from the module name) and `"interop": "skie"`; set `"rootModule"` to the SDK module. App mode uses `"interop": "none"` because the iOS app only hosts Compose. Commit this file; the other skills read it.

## 6. iOS specifics
- **App mode:**
  - The wizard's Xcode build phase (`./gradlew :shared:embedAndSignAppleFrameworkForXcode`) stays as it is.
  - The script adds `BASE_URL` to `Configuration/Config.xcconfig` and `Info.plist`.
  - `iOSApp.swift` calls `KoinIosKt.doInitKoin(...)` in `init()`.
  - The script sets `IPHONEOS_DEPLOYMENT_TARGET` in `project.pbxproj` to `--ios-min` (all configurations), so the iOS app supports exactly the minimum the user chose.
  - `Config.xcconfig` gets `BASE_URL_Debug` (dev host) and `BASE_URL_Release` (prod host). A local Xcode Debug run talks to the dev API like Android's `devDebug`. CI still overrides `BASE_URL` per lane.
- **SDK mode:**
  - The XCFramework is static and uses bundled SQLite, so no linker flags are needed.
  - SKIE is applied by `kmp.sdk`.
  - Consumers add the Swift package published by CI.

`references/ios.md` covers Swift usage of the SDK, the consumer setup and common iOS build failures.

## 7. CI/CD (if chosen)
The templates add `.github/workflows/{ci,dev,release}.yml`, plus `fastlane/` and `Gemfile` (app mode) or `scripts/publish-spm.sh` and `swiftpackage/Package.swift.template` (SDK mode), and `docs/ci-secrets.md`.
- **App:** a push to `dev` builds the dev flavor (Android) and an ad-hoc build against the dev URL (iOS), and sends both to Firebase App Distribution. A published GitHub Release builds the prod AAB for the Play internal track and an App Store build for TestFlight.
- **SDK:** a push to `dev` publishes `x.y.z-dev.<sha>` to GitHub Packages, plus an XCFramework prerelease tagged in the SPM repo. A Release publishes the same at the release version.

Walk the user through `docs/ci-secrets.md`. They must create Firebase apps, keystores, an App Store Connect API key, a `match` repo and the SPM repo. Those steps can't be automated from here. `references/ci-cd.md` explains the design and how to adapt it (other branches, Play tracks, Crashlytics).

## 8. Verify (don't skip)
Run these, in this order:
```bash
./gradlew :androidApp:assembleDevDebug                          # app mode
./gradlew testAndroidHostTest                                   # JVM tests incl. KoinGraphTest
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64           # app mode iOS framework
./gradlew :<sdk>:assemble<SdkName>DebugXCFramework              # SDK mode
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build   # app mode, macOS only
```
`local.properties` needs `sdk.dir`. If it's missing, create it from `$ANDROID_HOME` or `~/Library/Android/sdk`.

The first Kotlin/Native build downloads the toolchain and can take 10+ minutes. Run it in the background. Fix failures before reporting success; `references/troubleshooting.md` lists the known ones.

## 9. Update the project's agent docs
If the project has a `CLAUDE.md` / `AGENTS.md` describing the wizard template, update it with a short architecture summary: mode, modules, the `.tezca-kmp.json` file, the skills to use (`new-feature`, `make-screen`, …) and the verification commands. Future sessions shouldn't see a stale "wizard template" description.

## 10. Report
Tell the user, briefly:
- the mode and module or package layout
- the versions chosen
- what was verified, and how
- the manual follow-ups (Firebase `google-services.json`, CI secrets, bundle ID/team in `Config.xcconfig`)
- the next step: `new-feature <name>` (app) or `new-feature` then `make-api`/`make-repository`/`make-usecase` (SDK)
