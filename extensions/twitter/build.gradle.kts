
android {
    namespace = "app.revanced.extension"

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:twitter:stub"))
    compileOnly(libs.annotation)
    compileOnly(libs.appcompat)
}
