# Versions

## Pinned matrix (verified together on 2026-10-16)
The templates use these versions. An app-mode project and an SDK-mode project were built and tested with them: Android assemble, iOS framework/XCFramework, JVM and iOS simulator tests, and an Xcode build.

| Catalog key | Version | Artifact / plugin | Notes |
|---|---|---|---|
| kotlin | 2.4.20 | org.jetbrains.kotlin.* | |
| agp | 9.1.1 | com.android.application, com.android.kotlin.multiplatform.library | Gradle 9.x wrapper |
| composeMultiplatform | 1.12.1 | org.jetbrains.compose | material3 is versioned separately (`material3`) |
| ksp | 2.3.12 | com.google.devtools.ksp | KSP 2.3+ is no longer tied to one Kotlin release |
| androidx-room3 | 3.0.3 | androidx.room3:room3-runtime / -compiler, plugin `androidx.room3` | **Room 3**: package `androidx.room3.*` |
| androidx-sqlite | 2.7.1 | androidx.sqlite:sqlite-bundled | BundledSQLiteDriver |
| androidx-datastore | 1.2.1 | androidx.datastore:datastore-preferences-core | KMP: Preferences DataStore only |
| androidx-lifecycle | 2.11.0 | org.jetbrains.androidx.lifecycle:* | JetBrains multiplatform artifacts |
| androidx-navigation | 2.9.2 | org.jetbrains.androidx.navigation:navigation-compose | |
| koin | 4.2.2 | io.insert-koin:* | |
| ktor | 3.6.0 | io.ktor:* | |
| kotlinx-coroutines | 1.11.0 | | |
| kotlinx-serialization | 1.11.0 | | |
| kotlinx-datetime | 0.8.0 | | `Instant`/`Clock` now come from `kotlin.time` |
| skie | 0.10.15 | co.touchlab.skie | first release supporting Kotlin 2.4.20 |
| turbine | 1.2.1 | app.cash.turbine | |
| googleServices / firebaseAppDistribution | 4.4.4 / 5.2.1 | | only applied once Firebase is configured |

## Looking up newer releases
Prefer stable releases; skip `-alpha`, `-beta`, `-rc`, `-dev`, `-eap` and `-SNAPSHOT`.
```bash
# Maven Central
mc() { curl -s "https://repo1.maven.org/maven2/$1/maven-metadata.xml" | grep -o '<version>[^<]*' | sed 's/<version>//' | grep -viE 'alpha|beta|rc|dev|eap|snapshot|-M[0-9]' | tail -1; }
mc io/insert-koin/koin-core; mc io/ktor/ktor-client-core; mc org/jetbrains/kotlinx/kotlinx-coroutines-core
mc org/jetbrains/kotlinx/kotlinx-serialization-json; mc org/jetbrains/kotlinx/kotlinx-datetime
mc com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin; mc co/touchlab/skie/gradle-plugin
mc org/jetbrains/compose/compose-gradle-plugin; mc org/jetbrains/androidx/lifecycle/lifecycle-viewmodel-compose
mc org/jetbrains/androidx/navigation/navigation-compose; mc app/cash/turbine/turbine
# Google Maven
gm() { curl -s "https://dl.google.com/android/maven2/$1/maven-metadata.xml" | grep -o '<version>[^<]*' | sed 's/<version>//' | grep -viE 'alpha|beta|rc' | tail -1; }
gm androidx/room3/room3-runtime; gm androidx/sqlite/sqlite-bundled; gm androidx/datastore/datastore-preferences-core
gm com/android/tools/build/gradle
```

## Compatibility rules to check before bumping
- **SKIE ↔ Kotlin.** Each SKIE release supports a specific range of Kotlin versions, listed in its changelog at https://skie.touchlab.co/changelog. If the newest Kotlin isn't supported yet, stay on the newest Kotlin that is. SKIE fails the build on an unsupported compiler.
- **Compose Multiplatform ↔ Kotlin.** Every CMP release names its minimum Kotlin version. The Compose compiler plugin always uses the project's Kotlin version (`org.jetbrains.kotlin.plugin.compose`).
- **AGP ↔ Gradle.** AGP 9.x needs Gradle 9.x. Update `gradle/wrapper/gradle-wrapper.properties` together with AGP.
- **Room ↔ KSP.** Room 3 needs KSP2, which is the default in KSP 2.x. Changing Room's major version also changes its package; Room 2 uses `androidx.room`. Don't mix the two.
- **Lifecycle / Navigation ↔ CMP.** The JetBrains multiplatform lifecycle and navigation versions in the CMP release notes are the safest pairing.
- **Ktor 3.x** uses kotlinx-io: `kotlinx.io.IOException`, used in `safeCall`.

After changing versions, re-run the full verification from SKILL.md step 8.
