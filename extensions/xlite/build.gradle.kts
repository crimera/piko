android {
    namespace = "app.morphe.extension.xlite"

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:xlite:stub"))
    compileOnly(libs.morphe.extensions.library)
    compileOnly(libs.annotation)
    compileOnly(libs.appcompat)
}
