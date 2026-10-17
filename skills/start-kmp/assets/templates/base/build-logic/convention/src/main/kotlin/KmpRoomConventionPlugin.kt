import androidx.room3.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Room 3 (KMP) with KSP for every target, the bundled SQLite driver and exported schemas. */
class KmpRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("kmp.library")
            apply("com.google.devtools.ksp")
            apply("androidx.room3")
        }

        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.commonMain.dependencies {
                implementation(libs.lib("androidx-room3-runtime"))
                implementation(libs.lib("androidx-sqlite-bundled"))
            }
            // JVM host tests can't load the Android build of the bundled SQLite natives; use the desktop one.
            sourceSets.getByName("androidHostTest").dependencies {
                implementation(libs.lib("androidx-sqlite-bundled-jvm"))
            }
        }

        dependencies {
            listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { configuration ->
                add(configuration, libs.lib("androidx-room3-compiler"))
            }
        }
    }
}
