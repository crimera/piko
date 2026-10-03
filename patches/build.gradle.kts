plugins {
    id("ru.vyarus.animalsniffer")
}

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
    // Patch code runs inside Morphe Manager on the devices that can run the supported X builds,
    // whose oldest supported minSdkVersion is 29. The Gummy Bears signature describes that
    // runtime: Android 29 plus D8 backports.
    add("signature", "com.toasttab.android:gummy-bears-api-29:0.15.0@signature")

    compileOnly("com.github.REAndroid:ARSCLib:a28c6fb2a7")

    // Used by JsonGenerator.
    implementation(libs.gson)

    implementation(libs.morphe.patches.library)

    // Typed Dalvik emission (https://github.com/crimera/morphe-bytecode).
    implementation("crimera:morphe-bytecode:0.1.3")

    // Settings DSL and registry injection shared with the other piko patch sets.
    implementation(libs.piko.patches.library)

    testImplementation(kotlin("test"))
}

tasks {
    matching { it.name == "check" || it.name == "buildAndroid" || it.name == "jar" }.configureEach {
        dependsOn("animalsnifferMain")
    }

    register<JavaExec>("checkStringResources") {
        description = "Checks resource strings for invalid formatting"

        dependsOn(compileKotlin)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.resource.CheckStringKt")
    }

    register<JavaExec>("lintNewxResolvers") {
        description = "Checks NewX resolvers for unsafe candidate selection and nullable fallthrough"
        group = "verification"

        dependsOn(classes)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.crimera.tools.lint.ResolverLinterKt")
        args(
            providers.gradleProperty("newxResolverSourceRoot").orElse(
                rootProject.projectDir.resolve("patches/src/main/kotlin/app/crimera/patches/newx").absolutePath,
            ).get(),
        )
        if (providers.gradleProperty("newxResolverLintReportOnly").isPresent) {
            args("--report-only")
        }
    }

    register<JavaExec>("checkExtensionDescriptors") {
        description = "Checks patch-side extension descriptors against the built extension dex files"
        group = "verification"

        dependsOn(classes)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.crimera.tools.lint.ExtensionDescriptorLinterKt")
        args(
            "--extensions=${layout.buildDirectory.dir("resources/main/extensions").get().asFile.absolutePath}",
            "--sources=${projectDir.resolve("src/main/kotlin").absolutePath}",
        )
        if (providers.gradleProperty("extensionDescriptorReportOnly").isPresent) {
            args("--report-only")
        }
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
