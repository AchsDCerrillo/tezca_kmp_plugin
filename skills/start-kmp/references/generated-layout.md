# What start-kmp generates

Paths use the placeholders `<base>` (base package as a path) and `<pkg>` (base package). Files tagged *(conflict)* usually already exist in a wizard project and are merged rather than written fresh.

## Both modes
| Path | Purpose |
|---|---|
| `gradle/libs.versions.toml` *(conflict)* | full catalog: KotlinX, Koin, Ktor, Room 3, SQLite bundled, DataStore, lifecycle, navigation, Compose, SKIE, Turbine, Gradle plugin artifacts for build-logic, convention plugin aliases `libs.plugins.kmp.*` |
| `gradle.properties` (appended) | `kmp.basePackage=<pkg>` (read by every convention plugin to derive namespaces); SDK: `sdkVersion` |
| `build-logic/` | included build with convention plugins: `kmp.library` (KMP + AGP 9 KMP library + iosArm64/iosSimulatorArm64 + JVM 11 + host tests + coroutines/serialization/datetime/Koin + test stack), `kmp.compose` (Compose MP + resources with a per-module `Res` package + lifecycle ViewModel + Koin Compose), `kmp.room` (KSP for all targets + Room 3 + schemas + sqlite-bundled-jvm for host tests), `kmp.feature` (library + compose + all `:core:*` + navigation + ktor + test deps), `kmp.sdk` (library + room + SKIE + explicitApi + maven-publish) |
| core sources | `core.common` (`DataResult`, `AppError`, `DispatcherProvider`, `AppConfig`, `commonModule`), `core.network` (`HttpClientFactory`, `safeCall`, `TokenProvider`, OkHttp/Darwin engines, `networkModule`), `core.database` (`DatabaseFactory` + `create()` + `buildDefault()`, `CoreDatabase` with `SyncMetadataStore` for cache freshness, `databaseModule`), `core.datastore` (`createDataStore`, single `DataStore<Preferences>`, `dataStoreModule`), `core.testing` (`mockHttpClient`, `respondJson`, `TestDispatcherProvider`, `setMainDispatcher`) |

## App mode
| Path | Purpose |
|---|---|
| `settings.gradle.kts`, `build.gradle.kts` *(conflict)* | `includeBuild("build-logic")`, typesafe project accessors, core modules, `// tezca-kmp:features` marker; root `apply false` for every plugin |
| `core/{common,network,database,datastore,ui,testing}/build.gradle.kts` | one module each; `:core:ui` holds `AppTheme`, `Dimens`, `UiText`, `AppError.toUiText()`, `ObserveAsEvents`, `LoadingContent`/`ErrorContent` and error strings |
| `shared/build.gradle.kts` *(conflict)* | umbrella: static iOS framework `Shared`, depends on core + features (`// tezca-kmp:feature-dependencies`) |
| `shared/.../App.kt`, `navigation/AppNavHost.kt`, `di/KoinInit.kt` | root composable, the only NavHost (`// tezca-kmp:feature-graphs`, `start-destination`), `initKoin(config)` + `featureModules` (`// tezca-kmp:feature-modules`) |
| `shared/src/iosMain/.../MainViewController.kt`, `di/KoinIos.kt` | `ComposeUIViewController { App() }`, `doInitKoin(baseUrl, isDebug)` for Swift |
| `shared/src/androidHostTest/.../di/KoinGraphTest.kt` | `verify()` over the whole graph |
| `androidApp/build.gradle.kts` *(conflict)* | `dev`/`prod` flavors with `BASE_URL`, release signing from env, versionCode/Name from `-P`, BuildConfig |
| `androidApp/.../MainApplication.kt`, `MainActivity.kt`, `AndroidManifest.xml` *(conflict)* | `initKoin` with `androidContext`, INTERNET permission |
| `iosApp/iosApp/iOSApp.swift`, `ContentView.swift` *(conflict)* | `doInitKoin` in `init()` with `BASE_URL` from Info.plist |
| `iosApp/Configuration/Config.xcconfig` (appended), `Info.plist` (key added) | `BASE_URL` build setting → `$(BASE_URL)` in Info.plist |
| `.github/workflows/{ci,dev,release}.yml`, `fastlane/*`, `Gemfile`, `docs/ci-secrets.md` | CI/CD (see ci-cd.md) |

## SDK mode
| Path | Purpose |
|---|---|
| `settings.gradle.kts`, `build.gradle.kts` | one SDK module (`--sdk-module`, default `shared`) |
| `<sdk>/build.gradle.kts` | `kmp.sdk`, static `XCFramework("<SdkName>")`, Ktor/DataStore deps, Maven publishing to GitHub Packages with artifactId `<sdk-artifact>` |
| `<sdk>/src/commonMain/.../<SdkName>.kt`, `di/SdkKoin.kt` | public facade object (`// tezca-kmp:sdk-features`), isolated thread-safe `koinApplication` (`// tezca-kmp:feature-modules`) |
| `<sdk>/src/{androidMain,iosMain}/.../<SdkName>.{android,ios}.kt` | `initialize(context, config)` / `initialize(config)` |
| core packages | the same core sources as app mode, with infrastructure made `internal` (`/* sdk:internal */` markers); `core.testing` goes to `commonTest` |
| `swiftpackage/Package.swift.template`, `scripts/publish-spm.sh` | SPM binary target published by CI to a separate SPM repo |
| `.github/workflows/{ci,dev,release}.yml`, `docs/ci-secrets.md` | test + publish Maven/XCFramework/SPM |
