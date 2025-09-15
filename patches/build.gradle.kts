group = "crimera"

patches {
    about {
        name = "Piko"
        description = "Patches for ReVanced"
        source = "git@github.com:crimera/revanced-patches.git"
        author = "ReVanced"
        contact = "contact@revanced.app"
        website = "https://example.com"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    implementation(libs.apksig)

    // Android API stubs defined here.
    compileOnly(project(":patches:stub"))
}

tasks {
    register<JavaExec>("preprocessCrowdinStrings") {
        description = "Preprocess strings for Crowdin push"

        dependsOn(compileKotlin)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.revanced.util.CrowdinPreprocessorKt")

        args =
            listOf(
                "src/main/resources/addresources/values/strings.xml",
                // Ideally this would use build/tmp/crowdin/strings.xml
                // But using that does not work with Crowdin pull because
                // it does not recognize the strings.xml file belongs to this project.
                "src/main/resources/addresources/values/strings.xml",
            )
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/revanced/revanced-patches")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
