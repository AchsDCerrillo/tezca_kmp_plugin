# tezca-kmp-development

Agent skills for **Kotlin Multiplatform** on Android and iOS, in two modes:

- **SDK**: one KMP library that Android and iOS apps consume through Maven and a Swift Package.
- **Full app**: Compose Multiplatform UI with MVVM and one Gradle module per feature.

The skills work as a Claude Code plugin. The same folders also install into Codex, Cursor, Antigravity, Gemini CLI, OpenCode and Copilot.

**Version:** 0.1.0

## Skills
| Skill | What it does |
|---|---|
| `start-kmp` | Asks **SDK or full app**, then sets up the stack: Koin, Room 3, Ktor, DataStore, kotlinx datetime/serialization/coroutines, lifecycle ViewModel, Navigation Compose and SKIE. It adds convention plugins (`build-logic`), core modules, Koin bootstrap on both platforms, and a ready `HttpClient` and Room database. It also wires iOS (Info.plist / xcconfig) and CI/CD: pushes to `dev` go to Firebase App Distribution, a Release goes to the Play internal track and TestFlight, and SDKs publish to Maven and SPM. Finally it verifies the build. |
| `new-feature <name>` | App: creates the `:feature:<name>` module (data / domain / presentation, Koin module, navigation graph, resources) and registers it everywhere. SDK: creates a feature package and a public accessor on the SDK facade. |
| `make-api` | Ktor calls: DTOs, type-safe `@Resource` routes, an `Api` class built on `safeCall → DataResult`, and a `RemoteDataSource` that maps to domain models. |
| `make-database` | Room 3: entity, DAO (Flow reads, upserts, transactions), a per-feature `@Database`, a `LocalDataSource`, and migrations. |
| `make-preferences` | Typed DataStore preferences, plus an optional DataStore-backed `TokenProvider` for Ktor auth. |
| `make-repository` | Repository that combines remote and local data. It's **offline-first** by default (Room as source of truth, TTL refresh), or remote-only ("don't do this offline") or local-only. |
| `make-usecase` | One responsibility per use case: a single `operator fun invoke`, registered as a Koin `factory`. In SDK mode it's exposed to Swift as async / AsyncSequence. |
| `make-viewmodel` | `UiState` as a `StateFlow` (`stateIn WhileSubscribed`), `onAction(Action)`, and one-off `Event`s through a `Channel`. Data comes only from use cases. |
| `make-screen` | Builds a screen end to end. It creates whatever is missing (api → database → repository → use cases → ViewModel), then the Route and Screen composables, strings, previews, the navigation destination and Koin wiring. |
| `add-tests` | Tests for every layer in `commonTest`: MockEngine, in-memory Room, a DataStore temp file, fakes, Turbine and a Koin graph check. They run on the JVM and the iOS simulator. |
| `add-sdk` | Integrates a third-party SDK from its docs URL. It works out whether the library is KMP, a wrapper, or native-only. It adds the catalog entries, platform setup (Gradle plugins, manifest, SPM) and a vendor-neutral interface with Koin wiring, then gives a checklist for the console and Xcode steps. |
| `kmp-architecture` | Shared conventions that every skill reads: layers, naming, `DataResult` / `AppError`, Koin rules, offline sync, SDK vs app, and Swift-friendly APIs. |

## Typical flow
```
start-kmp                      → "full app, package com.acme.gym, api.acme.com/v1/"
new-feature onboarding         → :feature:onboarding
make-screen login              → api + session prefs + repository + LoginUseCase + LoginViewModel + LoginScreen
make-screen workout-history    → offline-first list with pull to refresh
add-tests                      → tests for what was just built
add-sdk https://firebase.google.com/docs/crashlytics/get-started
```
In SDK mode the flow is `start-kmp`, then `new-feature`, then `make-api` / `make-database` / `make-repository` / `make-usecase`. Consumers call `FitCoreSdk.shared.workouts.observeWorkouts.invoke()` from Swift.

## Architecture in one picture
```
Route/Screen ─▶ ViewModel ─▶ UseCase ─▶ Repository ─┬─▶ RemoteDataSource ─▶ Api (Ktor, safeCall → DataResult)
 (Compose)     (StateFlow)    (1 job)   (offline-1st) └─▶ LocalDataSource  ─▶ Dao (Room 3) / DataStore
```
App modules: `:androidApp`, `iosApp`, `:shared` (App, NavHost, initKoin, iOS framework), `:core:{common,network,database,datastore,ui,testing}`, and one `:feature:<name>` per feature.

## Install

### Claude Code (plugin, from the git marketplace)
Repository: **https://github.com/AchsDCerrillo/tezca_kmp_plugin** (public, so no credentials are needed).

From a terminal:
```bash
claude plugin marketplace add https://github.com/AchsDCerrillo/tezca_kmp_plugin.git
claude plugin install tezca-kmp-development@tezca-kmp-marketplace                  # user scope (default)
claude plugin install tezca-kmp-development@tezca-kmp-marketplace --scope project  # only this project, shared via .claude/settings.json
```
Or inside Claude Code:
```
/plugin marketplace add AchsDCerrillo/tezca_kmp_plugin
/plugin install tezca-kmp-development@tezca-kmp-marketplace
```
The skills then show up as `/tezca-kmp-development:start-kmp`, `/tezca-kmp-development:make-screen` and so on, and Claude also uses them automatically when a request matches.

Updating: `claude plugin marketplace update tezca-kmp-marketplace`, then restart Claude Code.

### Codex, Cursor, Antigravity, Gemini CLI, OpenCode, Copilot (and Claude without the plugin)
```bash
git clone https://github.com/AchsDCerrillo/tezca_kmp_plugin.git && cd tezca_kmp_plugin
./install-skills.sh                                                          # interactive
./install-skills.sh --scope project --agents all --project-dir ~/code/my-kmp-app
./install-skills.sh --scope user --agents codex,gemini,antigravity
./install-skills.sh --list-agents
git pull && ./install-skills.sh --scope user --agents codex --yes                        # update later
./install-skills.sh --uninstall --scope project --agents all --project-dir .
```
| Agent | Project scope | User scope |
|---|---|---|
| Codex, OpenCode, Copilot, Cursor, Gemini, Antigravity | `<project>/.agents/skills/` | Codex / OpenCode / Copilot: `~/.agents/skills`; Gemini: `~/.gemini/skills`; Antigravity: `~/.gemini/config/skills`; Cursor: project only |
| Claude Code (plain folders) | `<project>/.claude/skills/` | `~/.claude/skills/` |

How the installer behaves:
- It only ever replaces folders it installed itself; each one carries a `.tezca-kmp-install` marker.
- It never edits agent config files.
- `claude-plugin` goes through the official `claude plugin` CLI.
- All skills are always installed together, because they reference each other as `../<skill>/SKILL.md`.

## Making the CI/CD pipelines pass

`start-kmp` generates three workflows. Only `ci.yml` works out of the box. `dev.yml` and `release.yml` need accounts, signing material and GitHub secrets that only you can create. Do the steps below once per project, in order. Each step names the secret or variable it produces. Add them under **GitHub repo → Settings → Secrets and variables → Actions**, as *Secrets* unless marked *Variable*.

| Workflow | Trigger | Needs |
|---|---|---|
| `ci.yml` | every PR and push to `main`/`dev` | nothing beyond step 0 |
| `dev.yml` | push to `dev` | steps 0–4 |
| `release.yml` | GitHub Release published (`v1.2.0`) | steps 0–2 and 5–6 |

### App mode

**0. Repository basics (all workflows)**
1. Commit the generated files, including `gradle/wrapper/gradle-wrapper.jar`, `.tezca-kmp.json`, `Gemfile`, `fastlane/` and `.github/`.
2. Run `bundle install` once locally (Ruby 3.x) and commit the resulting **`Gemfile.lock`**. The CI Ruby setup caches gems from it.
3. Create a `dev` branch (`git switch -c dev && git push -u origin dev`).
4. Push and confirm **CI** is green. It needs no secrets. The Android job runs on Ubuntu; the iOS job runs on a macOS runner, and its first Kotlin/Native build takes about 10–15 minutes.

**1. Android signing (dev + release)**
1. Create an upload keystore once and keep it safe; losing it means you can't update the app.
   `keytool -genkeypair -v -keystore release.keystore -alias upload -keyalg RSA -keysize 2048 -validity 10000`
2. Secrets:
   - `ANDROID_KEYSTORE_BASE64` = output of `base64 -i release.keystore`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS` (e.g. `upload`)
   - `ANDROID_KEY_PASSWORD`

**2. Apple signing with fastlane match (dev + release)**
1. In developer.apple.com, register the bundle ID. The default is `<appId>.<AppName>`, e.g. `com.neutronsolutions.kratosgym.KratosGym`. Fix `PRODUCT_BUNDLE_IDENTIFIER` in `iosApp/Configuration/Config.xcconfig` to that exact value and delete the wizard's `$(TEAM_ID)` suffix, so local builds and CI agree. If you choose a different bundle ID, also add it as the *Variable* `IOS_BUNDLE_ID`.
2. Secret `APPLE_TEAM_ID`: your 10-character Team ID (Membership page). Also put it in `TEAM_ID=` in `Config.xcconfig` for local builds.
3. App Store Connect → Users and Access → Integrations → **App Store Connect API** → generate a key with the *App Manager* role. Secrets:
   - `ASC_KEY_ID`
   - `ASC_ISSUER_ID`
   - `ASC_KEY_P8_BASE64` = `base64 -i AuthKey_XXXX.p8`
4. Create an **empty private git repository** for certificates, e.g. `<you>/certificates`, and set up match once from your Mac:
   ```bash
   bundle exec fastlane match adhoc     # dev builds (Firebase)
   bundle exec fastlane match appstore  # TestFlight
   ```
   Then add these secrets:
   - `MATCH_GIT_URL` (the https URL of that repo)
   - `MATCH_PASSWORD` (the passphrase you chose)
   - `MATCH_GIT_BASIC_AUTHORIZATION` = `echo -n "<github-user>:<token with repo read>" | base64`
5. Ad-hoc builds only install on registered devices. Add the testers' UDIDs in the Apple portal (Devices), then refresh the profile with `bundle exec fastlane match adhoc --force_for_new_devices`.

**3. Firebase App Distribution (dev)**
1. Create a Firebase project. Add an **Android app** with package `<appId>.dev` (the dev flavor adds `.dev`) and an **iOS app** with your bundle ID.
2. Secrets:
   - `FIREBASE_ANDROID_APP_ID` and `FIREBASE_IOS_APP_ID` (Project settings → Your apps → App ID, `1:…:android:…` / `1:…:ios:…`).
3. Project settings → Service accounts → create a key, and give that service account the **Firebase App Distribution Admin** role in Google Cloud IAM. Secret `FIREBASE_SERVICE_ACCOUNT_JSON` = the whole JSON file content.
4. App Distribution → Testers & Groups → create a group, e.g. `qa`. *Variable* `FIREBASE_TESTER_GROUPS` = `qa` (comma separated for several).

**4. Run dev.** Push to `dev`. Both jobs should upload a build, and testers get an email. `google-services.json` is **not** needed for distribution; add it only when you integrate Firebase SDKs (use `add-sdk`).

**5. Google Play (release)**
1. In Play Console, create the app with package `<appId>` (no `.dev`). Upload the **first** AAB by hand: `./gradlew :androidApp:bundleProdRelease` with the signing env vars from step 1 set. Play's API can't create an app's first release.
2. Google Cloud → create a service account and a JSON key. In Play Console → Users and permissions, invite its email with *Release to testing tracks*. Secret `PLAY_SERVICE_ACCOUNT_JSON` = the JSON content.
3. Version codes come from the workflow run number, so they must keep growing. If the manual first upload used a higher code, bump it with `-PversionCode` once.

**6. TestFlight (release)**
1. In App Store Connect → Apps, create the app with the bundle ID from step 2.
2. Publish a GitHub Release with a tag like `v1.0.0`. The tag becomes `versionName` / `CFBundleShortVersionString`, and the run number becomes the build number. Android goes to the Play **internal** track and iOS to TestFlight. Promote to production from the store consoles.

**Quick checklist of what each workflow reads**

| Name | Kind | dev.yml | release.yml |
|---|---|---|---|
| `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | secret | ✔ | ✔ |
| `APPLE_TEAM_ID`, `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8_BASE64` | secret | ✔ | ✔ |
| `MATCH_GIT_URL`, `MATCH_PASSWORD`, `MATCH_GIT_BASIC_AUTHORIZATION` | secret | ✔ | ✔ |
| `FIREBASE_SERVICE_ACCOUNT_JSON`, `FIREBASE_ANDROID_APP_ID`, `FIREBASE_IOS_APP_ID` | secret | ✔ | |
| `PLAY_SERVICE_ACCOUNT_JSON` | secret | | ✔ |
| `FIREBASE_TESTER_GROUPS`, `IOS_BUNDLE_ID` | variable (optional) | ✔ | ✔ (bundle id) |

**Common failures**

| Error in the log | Fix |
|---|---|
| `Could not find gem 'fastlane'` / bundler error | commit `Gemfile.lock` (step 0) |
| `key not found: "APPLE_TEAM_ID"` (or another `KeyError`) | that secret is missing; secrets aren't passed to workflows triggered from forks |
| `No matching provisioning profiles found` / `sigh_…_profile-name` missing | run `fastlane match adhoc`/`appstore` locally first; the bundle ID in the portal, `Config.xcconfig` and `IOS_BUNDLE_ID` must be identical |
| `Code signing is required…` / automatic signing errors | `APPLE_TEAM_ID` is wrong, or the profile doesn't match the bundle ID |
| Firebase `403 / permission denied` | the service account lacks *Firebase App Distribution Admin*, or the app ID is from another project |
| Firebase `app not found` for Android | the Firebase Android app must be `<appId>.dev` |
| Play `Package not found` / `APK specifies a version code that has already been used` | upload the first AAB by hand (step 5.1) / raise the version code |
| Xcode phase `Unable to locate a Java Runtime` | keep the `actions/setup-java` step; locally, export `JAVA_HOME` in the build phase |

### SDK mode
1. Do step 0 above (commit, `dev` branch, CI green). There's no fastlane or Gemfile in SDK mode.
2. Create an **empty public or private repo for the Swift package**, e.g. `<you>/FitCoreSdk-spm`. *Variable* `SPM_REPO` = `<you>/FitCoreSdk-spm`.
3. Create a fine-grained token with **Contents: read and write** on that repo. Secret `SPM_REPO_TOKEN`.
4. Maven publishing uses the built-in `GITHUB_TOKEN`. The workflows already request `packages: write` and `contents: write`, and nothing else is needed. Set the base version in `gradle.properties` (`sdkVersion=0.1.0`).
5. Push to `dev`: CI publishes `0.1.0-dev.<sha>` to GitHub Packages and tags the SPM repo. Publish a GitHub Release `v0.1.0` for a real version, and bump `sdkVersion` afterwards.
6. Consumers:
   - **Android:** add the GitHub Packages Maven repo with a token that has `read:packages`.
   - **iOS:** add `https://github.com/<SPM_REPO>` in Xcode. If the SDK repo is private, iOS consumers also need access to its release assets (git credentials plus a `~/.netrc` entry for `github.com`).

## Requirements
- JDK 17+ and the Android SDK (with `local.properties` or `ANDROID_HOME`).
- macOS with Xcode for anything iOS.
- `python3` for the scaffold scripts (standard library only, run as `python3 -I`).
- App mode starts from the [KMP wizard](https://kmp.jetbrains.com) project (Android + iOS, Share UI). SDK mode can start from the wizard or any folder with a Gradle wrapper.

## Pinned versions (verified together)
Kotlin 2.4.20 · AGP 9.1.1 · Gradle 9.5 · Compose Multiplatform 1.12.1 · Koin 4.2.2 · Ktor 3.6.0 · Room 3.0.3 · SQLite bundled 2.7.1 · DataStore 1.2.1 · lifecycle 2.11.0 · navigation 2.9.2 · coroutines 1.11.0 · serialization 1.11.0 · datetime 0.8.0 · KSP 2.3.12 · SKIE 0.10.15.

`start-kmp` looks up newer stable releases and checks they're compatible. See `skills/start-kmp/references/versions.md`.

## Repository layout
```
.claude-plugin/{plugin.json,marketplace.json}
skills/<skill>/SKILL.md (+ references/, scripts/, assets/templates/)
install-skills.sh
evals/evals.json            end-to-end test prompts used to validate the skills
```

## Developing the skills
- Templates are real, compilable files. `skills/start-kmp/assets/templates/{base,app,sdk}` are rendered by `scripts/scaffold.py` with placeholders such as `__PKG__`, `__PKG_PATH__` and `__APP_NAME__`. In SDK mode, `/* sdk:internal */ public` becomes `internal`.
- After changing a template, render it into a scratch copy of a wizard project in both modes and run the verification commands from `start-kmp` step 8.
- `evals/evals.json` lists the end-to-end prompts and assertions. Run them with skill-creator.
