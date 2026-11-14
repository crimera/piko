plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.morphe.extension.newx.stub"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
