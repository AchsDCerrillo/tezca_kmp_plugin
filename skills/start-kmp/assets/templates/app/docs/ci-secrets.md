# CI/CD secrets

Workflows: `.github/workflows/ci.yml` (every PR / push), `dev.yml` (push to `dev` → Firebase App
Distribution), `release.yml` (GitHub Release published → Google Play internal track + TestFlight).
Lanes live in `fastlane/Fastfile`. Add these under **Settings → Secrets and variables → Actions**.

| Secret | Used by | How to get it |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | dev, release | `base64 -i release.keystore` of your upload keystore |
| `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | dev, release | from the keystore |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | dev | Firebase console → Project settings → Service accounts → generate key (role *Firebase App Distribution Admin*) |
| `FIREBASE_ANDROID_APP_ID` | dev | Firebase console → Android app (`__APP_ID__.dev`) → App ID `1:…:android:…` |
| `FIREBASE_IOS_APP_ID` | dev | Firebase console → iOS app → App ID `1:…:ios:…` |
| `PLAY_SERVICE_ACCOUNT_JSON` | release | Play Console → Setup → API access → service account JSON (release manager). The first AAB must be uploaded by hand once. |
| `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8_BASE64` | dev, release (iOS) | App Store Connect → Users and Access → Integrations → API key; `base64 -i AuthKey_XXX.p8` |
| `MATCH_GIT_URL`, `MATCH_PASSWORD`, `MATCH_GIT_BASIC_AUTHORIZATION` | dev, release (iOS) | private certificates repo for `fastlane match`; run `bundle exec fastlane match adhoc` and `match appstore` once locally; basic auth = `base64 "user:token"` |

Variables (not secrets): `FIREBASE_TESTER_GROUPS` (comma separated, default `qa`).

Also:
- Register the ad-hoc devices in the Apple portal and re-run `match adhoc --force_for_new_devices` when testers change.
- Set the iOS bundle id once in `iosApp/Configuration/Config.xcconfig` and, if it differs from
  `__APP_ID__.__APP_NAME__`, add `IOS_BUNDLE_ID` to the workflow env.
- Versioning: `versionName` / `CFBundleShortVersionString` come from the release tag (`v1.4.0` → `1.4.0`),
  `versionCode` / build number from the workflow run number.
