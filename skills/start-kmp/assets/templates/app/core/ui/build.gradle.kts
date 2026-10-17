plugins {
    alias(libs.plugins.kmp.compose)
}

// Features reuse core strings (Retry, error messages), so this module's Res is public.
compose.resources {
    publicResClass = true
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
        }
    }
}
