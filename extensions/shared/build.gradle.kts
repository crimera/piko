android {
    namespace = "app.morphe.extension"

    defaultConfig {
        minSdk = 28
    }
}

dependencies {
    implementation(project(":extensions:shared:library"))
}
