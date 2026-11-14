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
    id("app.morphe.patches") version "1.3.4"
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "PikoGitHubPackages"
            url = uri("https://maven.pkg.github.com/crimera/piko-patches-library")
            credentials {
                username = providers.gradleProperty("gpr.user").getOrElse(System.getenv("GITHUB_ACTOR") ?: "")
                password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("GITHUB_TOKEN") ?: "")
            }
        }
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

// Shared in-app code lives in piko-patches-library and is consumed from GitHub Packages as
// app.crimera:piko-patches-library (patch-side infrastructure and the settings DSL) and
// app.crimera:piko-extension-library (in-app logging and themeable settings UI).
// A sibling checkout substitutes the published artifacts so library changes can be tested without
// publishing first.
val pikoLibraryBuild = listOf("../piko-patches-library", "piko-patches-library-lib")
    .map { rootDir.resolve(it) }
    .firstOrNull { it.resolve("settings.gradle.kts").exists() }
if (pikoLibraryBuild != null) {
    includeBuild(pikoLibraryBuild) {
        dependencySubstitution {
            substitute(module("app.crimera:piko-patches-library")).using(project(":"))
            substitute(module("app.crimera:piko-extension-library")).using(project(":extension"))
        }
    }
}
