group = "crimera"

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
    // Used by JsonGenerator.
    implementation(libs.gson)

    implementation(libs.morphe.patches.library)

    // Shared X-Lite types (categories + keys) from the API module.
    // Must be implementation — the compiled patches reference these at load time.
    implementation(project(":extensions:xlite:api"))

    testImplementation(kotlin("test"))
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
