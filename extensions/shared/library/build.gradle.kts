plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.morphe.extension.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Shared in-app logging and themeable settings UI. `api` so app extension modules compile
    // against them through this module, and so both are dexed into the shared bundle.
    api(libs.piko.extension.library)
    implementation(libs.morphe.extensions.library)
    compileOnly(libs.annotation)
    compileOnly(libs.appcompat)
    implementation(project(
        path = ":extensions:shared:media3",
        configuration = "shadowedMedia3"
    ))
}
