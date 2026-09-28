rootProject.name = "piko-twitter-patches"

buildCache {
    local {
        isEnabled = !System.getenv().containsKey("CI")
    }
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                username = providers.gradleProperty("gpr.user").getOrElse(System.getenv("GITHUB_ACTOR"))
                password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("GITHUB_TOKEN"))
            }
        }
    }
}

plugins {
    id("app.morphe.patches") version "1.3.3"
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "BytecodeGitHubPackages"
            url = uri("https://maven.pkg.github.com/crimera/morphe-bytecode")
            credentials {
                username = providers.gradleProperty("gpr.user").getOrElse(System.getenv("GITHUB_ACTOR") ?: "")
                password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("GITHUB_TOKEN") ?: "")
            }
        }
    }
}

settings {
    extensions {
        defaultNamespace = "app.morphe.extension"

        // Must resolve to an absolute path (not relative),
        // otherwise the extensions in subfolders will fail to find the proguard config.
        proguardFiles(rootProject.projectDir.resolve("extensions/proguard-rules.pro").toString())
    }
}

// Typed bytecode emission lives in its own repository and is consumed as crimera:morphe-bytecode
// from GitHub Packages (packages: read on the workflow token). A sibling checkout, or a
// morphe-bytecode-lib checkout in this workspace, substitutes that artifact so layer changes can
// be tested without publishing first.
val bytecodeBuild = listOf("../morphe-bytecode", "morphe-bytecode-lib")
    .map { rootDir.resolve(it) }
    .firstOrNull { it.resolve("settings.gradle.kts").exists() }
if (bytecodeBuild != null) {
    includeBuild(bytecodeBuild)
}
