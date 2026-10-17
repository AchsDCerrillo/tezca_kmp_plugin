# CI/CD design

## App mode: GitHub Actions + fastlane
- `ci.yml` (PRs, pushes to main/dev): an ubuntu job runs `testAndroidHostTest` (all modules) and `assembleDevDebug`; a macOS job runs `iosSimulatorArm64Test` and links the framework.
- `dev.yml` (push to `dev`):
  - Android: `fastlane android dev` builds the **dev flavor** signed release APK and uploads it with `firebase_app_distribution` to `FIREBASE_TESTER_GROUPS`.
  - iOS: `fastlane ios dev` gets **ad-hoc** certificates with match, builds Release with `BASE_URL` = dev host, and uploads to Firebase App Distribution.
- `release.yml` (GitHub Release published, tag `vX.Y.Z`):
  - Android: `fastlane android release` builds the **prod flavor** AAB and uploads it with `upload_to_play_store` to the **internal** track.
  - iOS: `fastlane ios release` uses App Store match, builds, and runs `upload_to_testflight`.
  - Version name comes from the tag; version code / build number from `github.run_number`.

### Adapting it
- **Other branches:** edit `on.push.branches`, e.g. `develop`, `staging`. To add a staging environment, add a `staging` flavor and a `BASE_URL_STAGING_HOST` lane variable.
- **Promote straight to production:** change `track: "internal"` to `production`, or add a `promote` lane with `upload_to_play_store(track: "internal", track_promote_to: "production", skip_upload_aab: true)`. For iOS, add `deliver(submit_for_review: true, …)` after TestFlight.
- **Crashlytics dSYMs:** add `upload_symbols_to_crashlytics(dsym_path: lane_context[SharedValues::DSYM_OUTPUT_PATH])` after `build_app`.
- **Firebase Android without fastlane:** apply `com.google.firebase.appdistribution` in `androidApp` and run `./gradlew assembleDevRelease appDistributionUploadDevRelease`. The fastlane route is used because it shares one lane file with iOS.
- **Caching:** `gradle/actions/setup-gradle` caches Gradle and `~/.konan`. The first iOS run is slow (toolchain download).
- **Self-hosted runners:** replace `runs-on`. The macOS runner needs Xcode, JDK 17 and Ruby 3.

## SDK mode
- `ci.yml`: JVM + iOS simulator tests and a debug XCFramework on macOS. iOS klibs need a Mac.
- `dev.yml`: `publishAllPublicationsToGitHubPackagesRepository -PsdkVersion=<x.y.z>-dev.<sha>`, then the release XCFramework, then `scripts/publish-spm.sh` (zip, checksum, prerelease asset, Package.swift commit and tag in `SPM_REPO`).
- `release.yml`: the same with the release tag's version; the zip is attached to that release.

Why a separate SPM repository: SPM resolves versions from git tags, and `Package.swift` must contain the zip's checksum. In a single repository, the release tag would have to exist before the artifact is built. With a separate repo, the tag is created after the checksum is known, which is the pattern the DeepDecibels SDK already used.

**Maven Central instead of GitHub Packages:** apply `com.vanniktech.maven.publish`, add signing keys as secrets, and replace the publish task with `publishToMavenCentral`. Consumers then don't need a GitHub token.
