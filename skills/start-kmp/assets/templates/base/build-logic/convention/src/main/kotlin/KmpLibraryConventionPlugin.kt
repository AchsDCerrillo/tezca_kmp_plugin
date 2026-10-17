import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Base for every KMP module: Android (AGP 9 KMP library plugin) + iosArm64 + iosSimulatorArm64,
 * kotlinx serialization, coroutines, datetime and Koin, plus the common test stack.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.android.kotlin.multiplatform.library")
            apply("org.jetbrains.kotlin.plugin.serialization")
        }

        extensions.configure<KotlinMultiplatformExtension> {
            iosArm64()
            iosSimulatorArm64()

            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                namespace = kmpNamespace
                compileSdk = libs.versionOf("android-compileSdk").toInt()
                minSdk = libs.versionOf("android-minSdk").toInt()
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
                withHostTest {
                    isIncludeAndroidResources = true
                }
            }

            compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
                optIn.add("kotlin.time.ExperimentalTime")
            }

            sourceSets.commonMain.dependencies {
                implementation(libs.lib("kotlinx-coroutines-core"))
                implementation(libs.lib("kotlinx-serialization-json"))
                implementation(libs.lib("kotlinx-datetime"))
                implementation(libs.lib("koin-core"))
            }
            sourceSets.androidMain.dependencies {
                implementation(libs.lib("kotlinx-coroutines-android"))
                implementation(libs.lib("koin-android"))
            }
            sourceSets.commonTest.dependencies {
                implementation(libs.lib("kotlin-test"))
                implementation(libs.lib("kotlinx-coroutines-test"))
                implementation(libs.lib("turbine"))
                implementation(libs.lib("koin-test"))
            }
        }
    }
}
