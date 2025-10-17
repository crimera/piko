android {
    defaultConfig {
        minSdk = 28
    }
}

dependencies {
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:twitter:stub"))
    compileOnly(libs.annotation)
    compileOnly(libs.appcompat)
}
