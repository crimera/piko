group = "crimera"

// ExtensionPlugin exports DEX only. Package native payloads as patch resources.
val wireguardNative = configurations.create("wireguardNative") {
    isTransitive = false
}
dependencies {
    add(wireguardNative.name, libs.wireguard)
}
val wireguardResources = tasks.register<Sync>("wireguardResources") {
    from(provider { zipTree(wireguardNative.singleFile) }) {
        include("jni/*/libwg-go.so")
        eachFile { path = path.removePrefix("jni/") }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("generated/wireguard-resources/wireguard/native"))
}
sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/wireguard-resources"))
}
tasks.processResources { dependsOn(wireguardResources) }

patches {
    about {
        name = "Piko"
        description = "Morphe patches focused on Twitter/X"
        source = "git@github.com:crimera/piko.git"
        author = "crimera"
        contact = "na"
        website = "https://github.com/crimera/piko"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
    compileOnly("com.github.REAndroid:ARSCLib:a28c6fb2a7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Used by JsonGenerator.
    implementation(libs.gson)

    implementation(libs.morphe.patches.library)
}

tasks {
    register<JavaExec>("checkStringResources") {
        description = "Checks resource strings for invalid formatting"

        dependsOn(compileKotlin)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.resource.CheckStringKt")
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }
    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-parameters")
    }
}
