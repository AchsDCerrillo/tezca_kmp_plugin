plugins {
    alias(libs.plugins.kmp.library)
    alias(libs.plugins.kmp.room)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            // Features declare their own @Database with these APIs.
            api(libs.androidx.room3.runtime)
            api(libs.androidx.sqlite.bundled)
        }
    }
}
