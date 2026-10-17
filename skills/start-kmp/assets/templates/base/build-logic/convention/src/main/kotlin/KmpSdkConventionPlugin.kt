import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * SDK module (SDK mode): explicit API mode so nothing becomes public by accident, Room, SKIE for
 * a Swift-friendly API (async/await, AsyncSequence, exhaustive enums) and maven-publish.
 * The XCFramework itself is declared in the module's build.gradle.kts so its name stays visible.
 */
class KmpSdkConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("kmp.library")
            apply("kmp.room")
            apply("co.touchlab.skie")
            apply("maven-publish")
        }

        extensions.configure<KotlinMultiplatformExtension> {
            explicitApi()
        }
    }
}
