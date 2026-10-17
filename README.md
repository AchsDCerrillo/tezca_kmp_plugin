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

### Claude Code (plugin, from a git repository)
```bash
claude plugin marketplace add https://github.com/<owner>/<repo>.git     # or a local path
claude plugin install tezca-kmp-development@tezca-kmp-marketplace       # --scope user|project|local
```
Inside Claude Code you can also run `/plugin marketplace add <git-url>` and then `/plugin install tezca-kmp-development@tezca-kmp-marketplace`. The skills show up as `/tezca-kmp-development:start-kmp`, `/tezca-kmp-development:make-screen` and so on.

For a private repository, everyone who installs it needs read access and git credentials (`gh auth login` or SSH). For background updates, also set `GITHUB_TOKEN`.

### Codex, Cursor, Antigravity, Gemini CLI, OpenCode, Copilot (and Claude without the plugin)
```bash
git clone https://github.com/<owner>/<repo>.git tezca-kmp && cd tezca-kmp
./install-skills.sh                                                          # interactive
./install-skills.sh --scope project --agents all --project-dir ~/code/my-kmp-app
./install-skills.sh --scope user --agents codex,gemini,antigravity
./install-skills.sh --list-agents
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
