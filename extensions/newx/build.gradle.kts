android {
    namespace = "app.morphe.extension.newx"

    defaultConfig {
        // Oldest supported X build (12.27.0-prod.01) declares minSdkVersion 29.
        minSdk = 29
    }

    lint {
        // Gate the NewX extension to APIs present on the supported app floor. NewApi models
        // R8 desugaring/backports, so it matches the final dex rather than the raw Android jar.
        checkOnly += setOf("NewApi")
        abortOnError = true
    }
}

// The extension bundle is dexed from the release artifact; check it before packing.
tasks.matching { it.name == "syncExtension" }.configureEach {
    dependsOn("lintRelease")
}

dependencies {
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:newx:stub"))
    compileOnly(libs.morphe.extensions.library)
    compileOnly(libs.annotation)
    compileOnly(libs.appcompat)
    // Runtime copy is packed once in the shared extension, relocated away from X's Media3.
    compileOnly(project(path = ":extensions:shared:media3", configuration = "shadowedMedia3"))

    testImplementation(project(":extensions:newx:stub"))
    testImplementation(libs.piko.extension.library)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation(project(path = ":extensions:shared:media3", configuration = "shadowedMedia3"))
    testImplementation("org.robolectric:robolectric:4.17")
}
