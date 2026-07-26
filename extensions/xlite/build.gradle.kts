android {
    namespace = "app.morphe.extension.xlite"

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    compileOnly(project(":extensions:shared:library"))
    implementation(project(":extensions:xlite:api"))
    compileOnly(project(":extensions:xlite:stub"))
    compileOnly(libs.morphe.extensions.library)
    compileOnly(libs.annotation)
    compileOnly(libs.appcompat)
}
