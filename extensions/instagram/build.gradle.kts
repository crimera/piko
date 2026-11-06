android {
    namespace = "app.morphe.extension.instagram"

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:instagram:stub"))
    compileOnly(libs.morphe.extensions.library)
    compileOnly(libs.annotation)
    compileOnly(libs.appcompat)

    // Pure-JVM tests for the notification.state package (no Android types there).
    testImplementation("junit:junit:4.13.2")
}
