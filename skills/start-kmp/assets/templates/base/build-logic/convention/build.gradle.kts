import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "__PKG__.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// compileOnly: the plugin versions are put on the classpath by the root build.gradle.kts
// (`alias(...) apply false`), so build-logic never pins a second copy of them.
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room3.gradlePlugin)
    compileOnly(libs.skie.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("kmpRoom") {
            id = "kmp.room"
            implementationClass = "KmpRoomConventionPlugin"
        }
        register("kmpFeature") {
            id = "kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
        register("kmpSdk") {
            id = "kmp.sdk"
            implementationClass = "KmpSdkConventionPlugin"
        }
    }
}
