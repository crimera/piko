android {
    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(libs.wireguard)
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:twitter:stub"))
    compileOnly(libs.morphe.extensions.library)
    compileOnly(libs.annotation)
    compileOnly(libs.appcompat)
}

// The Morphe plugin renames DEX files to one .mpe; fail instead of silently
// overwriting multidex output if this extension ever exceeds one DEX.
tasks.named("syncExtension") {
    doFirst {
        val dexFiles = tasks.getByName("minifyReleaseWithR8").outputs.files.asFileTree
            .matching { include("**/*.dex") }.files
        check(dexFiles.size == 1) { "Twitter extension must contain exactly one DEX; found ${dexFiles.size}" }
    }
}
