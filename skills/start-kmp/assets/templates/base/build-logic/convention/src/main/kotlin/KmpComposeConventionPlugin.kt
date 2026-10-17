import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Compose Multiplatform UI module: Compose, resources, lifecycle ViewModel and Koin Compose. */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("kmp.library")
            apply("org.jetbrains.compose")
            apply("org.jetbrains.kotlin.plugin.compose")
        }

        extensions.configure<KotlinMultiplatformExtension> {
            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                androidResources.enable = true
            }

            sourceSets.commonMain.dependencies {
                implementation(libs.lib("compose-runtime"))
                implementation(libs.lib("compose-foundation"))
                implementation(libs.lib("compose-material3"))
                implementation(libs.lib("compose-ui"))
                implementation(libs.lib("compose-components-resources"))
                implementation(libs.lib("compose-uiToolingPreview"))
                implementation(libs.lib("androidx-lifecycle-viewmodel"))
                implementation(libs.lib("androidx-lifecycle-viewmodelCompose"))
                implementation(libs.lib("androidx-lifecycle-runtimeCompose"))
                implementation(libs.lib("koin-compose"))
                implementation(libs.lib("koin-compose-viewmodel"))
            }
            sourceSets.androidMain.dependencies {
                implementation(libs.lib("compose-uiToolingPreview"))
            }
        }

        // Each module gets its own Res class: <namespace>.generated.resources.Res
        extensions.configure<ComposeExtension> {
            (this as ExtensionAware).extensions.configure<ResourcesExtension> {
                packageOfResClass = "$kmpNamespace.generated.resources"
            }
        }

        dependencies {
            add("androidRuntimeClasspath", libs.lib("compose-uiTooling"))
        }
    }
}
