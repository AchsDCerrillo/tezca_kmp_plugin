import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalArgumentException("Library '$alias' missing from libs.versions.toml") }

internal fun VersionCatalog.versionOf(alias: String): String =
    findVersion(alias).orElseThrow { IllegalArgumentException("Version '$alias' missing from libs.versions.toml") }.requiredVersion

/**
 * Namespace / package of a module, derived from `kmp.basePackage` in gradle.properties and the
 * Gradle path: `com.example.app` + `:feature:user-profile` -> `com.example.app.feature.userprofile`.
 */
internal val Project.kmpNamespace: String
    get() {
        val base = providers.gradleProperty("kmp.basePackage").orNull
            ?: error("Add kmp.basePackage=<your.base.package> to gradle.properties")
        val suffix = path.split(':')
            .filter { it.isNotBlank() }
            .joinToString(".") { it.replace("-", "").replace("_", "").lowercase() }
        return "$base.$suffix"
    }
