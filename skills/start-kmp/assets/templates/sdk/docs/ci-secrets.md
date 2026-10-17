# CI/CD for the SDK

| Workflow | Trigger | Publishes |
|---|---|---|
| `ci.yml` | PR / push to main, dev | tests (JVM + iOS simulator), debug XCFramework |
| `dev.yml` | push to `dev` | `<sdkVersion>-dev.<sha>`: Maven on GitHub Packages + XCFramework prerelease + SPM tag |
| `release.yml` | GitHub Release published (`v1.4.0`) | `1.4.0`: Maven + XCFramework on that release + SPM tag `1.4.0` |

Setup (Settings → Secrets and variables → Actions):
- Variable `SPM_REPO`: `owner/name` of the Swift package repository (create it empty; CI writes `Package.swift`).
  A separate repo keeps SPM tags clean and lets the checksum be known before the tag exists.
- Secret `SPM_REPO_TOKEN`: fine-grained token with *Contents: read and write* on `SPM_REPO`.
- `GITHUB_TOKEN` is used for GitHub Packages and releases (the workflows request `packages: write`).
- Bump `sdkVersion` in `gradle.properties` after each release.

Consumers:
- **Android** (`settings.gradle.kts`):
  ```kotlin
  maven {
      url = uri("https://maven.pkg.github.com/<owner>/<repo>")
      credentials { username = System.getenv("GPR_USER"); password = System.getenv("GPR_TOKEN") } // token with read:packages
  }
  ```
  and `implementation("__PKG__:__SDK_ARTIFACT__:<version>")`.
- **iOS**: Xcode → Add Package → `https://github.com/<SPM_REPO>` → exact version. Private repos need
  the consumer's git credentials and a `~/.netrc` entry for `github.com` to download the release asset.
