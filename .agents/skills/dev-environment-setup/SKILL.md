---
name: dev-environment-setup
description: Complete Morphe development environment setup — prerequisites, cloning repos, gradle auth, IDE config, build commands. Use when setting up a new dev environment or troubleshooting build/auth issues.
---

# Morphe Development Environment Setup

## Prerequisites

- JDK 17 (development), JRE 11+ (runtime)
- Android reverse engineering tools: jadx, baksmali, smali, apktool, aapt
- ripgrep (rg) for fast code search
- GitHub CLI (gh) for auth and releases
- Python tools via uvx: apkid, androguard

## Quick Install

```bash
sudo apt install -y jadx baksmali smali apktool aapt ripgrep dex2jar gh openjdk-17-jdk
```

## GitHub Auth

```bash
gh auth login
TOKEN=$(gh auth token)
mkdir -p ~/.gradle
cat > ~/.gradle/gradle.properties << EOF
gpr.user = <GitHub username>
gpr.key = $TOKEN
EOF
```

## Clone Repos

```bash
mkdir morphe && cd morphe
git clone https://github.com/MorpheApp/morphe-desktop
git clone https://github.com/MorpheApp/morphe-patches-template
# Optional: clone for reference
git clone https://github.com/MorpheApp/morphe-patches
git clone https://github.com/MorpheApp/morphe-patcher
```

## Gradle Plugin Config

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
        maven { url = uri("https://jitpack.io") }
    }
}
plugins { id("app.morphe.patches") version "1.3.0" }
```

`patches/build.gradle.kts`:
```kotlin
group = "app.mypatches"
patches {
    about {
        name = "My Patches"
        description = "Custom patches"
        source = "git@github.com:user/repo.git"
        author = "Author"
        contact = "na"
        website = "na"
        license = "GPLv3"
    }
}
```

Extension `build.gradle.kts`:
```kotlin
extension { name = "extensions/extension.mpe" }
android { namespace = "app.morphe.extension" }
```

## Build Commands

```bash
# Build patches → patches/build/libs/patches-<version>.mpp
cd morphe-patches-template && ./gradlew buildAndroid

# Build CLI → build/libs/morphe-desktop-<version>-all.jar
cd morphe-desktop && ./gradlew build
```

## Quick Dev Script

```bash
#!/bin/sh
cd morphe-desktop && ./gradlew build && cd ..
cd morphe-patches-template && ./gradlew buildAndroid && cd ..
java -Xms152m -jar morphe-desktop/build/libs/morphe-desktop-*-all.jar \
  patch --patches morphe-patches-template/build/libs/patches-*.mpp \
  --out morphe.apk $1 --install
```

## Troubleshooting

- Auth failure: Check `~/.gradle/gradle.properties` has `gpr.user` and `gpr.key`
- Wrong JDK: Ensure `JAVA_HOME` points to JDK 17
- MPP file names change after releases: Update paths after `git pull`
- Composite builds: If `morphe-patcher` exists as sibling dir, it's auto-included
