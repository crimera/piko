group = "crimera"

patches {
    about {
        name = "Piko"
        description = "ReVanced patches focused on Twitter/X"
        source = "git@github.com:crimera/piko.git"
        author = "crimera"
        contact = "contact@your.homepage"
        website = "https://github.com/crimera/piko"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
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
