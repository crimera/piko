plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.morphe.extension.xlite.stub"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
