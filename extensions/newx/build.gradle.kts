android {
    namespace = "app.morphe.extension.newx"

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:newx:stub"))
    compileOnly(libs.morphe.extensions.library)
    compileOnly(libs.annotation)
    compileOnly(libs.appcompat)

    testImplementation(project(":extensions:newx:stub"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
