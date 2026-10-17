import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kmp.sdk)
}

group = "__PKG__"
// CI passes -PsdkVersion=<x.y.z | x.y.z-dev.<sha>>; local builds use gradle.properties.
version = providers.gradleProperty("sdkVersion").get()

kotlin {
    // Static XCFramework for SPM (`./gradlew :__SDK_MODULE__:assemble__SDK_NAME__ReleaseXCFramework`).
    // SQLite is bundled (androidx.sqlite:sqlite-bundled), so consumers need no -lsqlite3.
    val xcFramework = XCFramework("__SDK_NAME__")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "__SDK_NAME__"
            isStatic = true
            binaryOption("bundleId", "__PKG__")
            xcFramework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.resources)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.androidx.datastore.preferences.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
        androidHostTest.dependencies {
            implementation(libs.kotlin.testJunit)
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        // Maven coordinates: __PKG__:__SDK_ARTIFACT__(-android|-iosarm64|...):<version>
        artifactId = artifactId.replace(project.name, "__SDK_ARTIFACT__")
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY") ?: "__GITHUB_OWNER__/__PROJECT_NAME__"}")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
