import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A feature module (app mode): data + domain + presentation for one feature, depending only on
 * :core:* modules. Features never depend on each other; navigation between them is wired in :shared.
 * Apply `kmp.room` as well once the feature owns a Room database (make-database does that).
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("kmp.library")
            apply("kmp.compose")
        }

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.commonMain.dependencies {
                implementation(project(":core:common"))
                implementation(project(":core:network"))
                implementation(project(":core:database"))
                implementation(project(":core:datastore"))
                implementation(project(":core:ui"))
                implementation(libs.lib("androidx-navigation-compose"))
                implementation(libs.lib("ktor-client-core"))
                implementation(libs.lib("ktor-client-resources"))
            }
            sourceSets.commonTest.dependencies {
                implementation(project(":core:testing"))
                implementation(libs.lib("ktor-client-mock"))
            }
        }
    }
}
