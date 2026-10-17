# Troubleshooting setup builds

| Error | Cause | Fix |
|---|---|---|
| `SDK location not found` | no `local.properties` | `echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties` (or `$ANDROID_HOME`) |
| `Add kmp.basePackage=… to gradle.properties` | gradle.properties not appended | add `kmp.basePackage=<pkg>` |
| `Plugin [id: 'kmp.library'] was not found` | `includeBuild("build-logic")` missing from `pluginManagement` | use the template settings.gradle.kts |
| `plugin … is already on the classpath with an unknown version` | a plugin is declared with a version in a module while build-logic loads it | modules use only `alias(libs.plugins.kmp.*)` or version-less ids; versions live in the root `apply false` block |
| `Cannot use 'T' as reified type parameter` (Room builder) | calling `Room.databaseBuilder` generically | use `DatabaseFactory.create(name) { XConstructor.initialize() }` from core.database |
| KSP `[ksp] … @ConstructedBy` / missing `_Impl` | Room plugin not applied to the module with the `@Database` | add `alias(libs.plugins.kmp.room)` |
| `MissingKoinDefinitionException … type:'kotlin.Boolean'` in KoinGraphTest | `verify()` inspects constructor params of instances provided by lambdas | add the type to `extraTypes` |
| `Unresolved reference: projects` | typesafe accessors disabled | `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` in settings |
| Compose resources `Res` not found | wrong package | each module's `Res` is `<namespace>.generated.resources.Res` (e.g. `com.acme.gym.feature.profile.generated.resources.Res`); run the `generateComposeResClass` task or build once |
| `Explicit API mode` errors (SDK) | public declaration without a modifier | add `public` or `internal` |
| `Undefined symbols` linking the iOS framework | native dependency missing | see add-sdk/references/ios-dependencies.md |
| Kotlin/Native build killed / OOM | daemon memory | `kotlin.daemon.jvmargs=-Xmx4g` and `org.gradle.jvmargs=-Xmx6g` in gradle.properties |
