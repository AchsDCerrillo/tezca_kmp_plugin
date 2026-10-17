plugins {
    alias(libs.plugins.kmp.compose)
}

kotlin {
    // The iOS app links this umbrella framework; it contains App(), the root NavHost and Koin start-up.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.network)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.ui)
            implementation(libs.androidx.navigation.compose)
            // tezca-kmp:feature-dependencies (new-feature adds implementation(projects.feature.<name>) below)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
        androidHostTest.dependencies {
            implementation(libs.kotlin.testJunit)
        }
    }
}
