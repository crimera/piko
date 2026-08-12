---
name: build-deploy-troubleshoot
description: Build, test, deploy, and troubleshoot Morphe patches — gradle commands, CLI usage, git workflow, common errors and fixes. Use when building patches, testing with CLI, deploying releases, or debugging issues.
---

> **When to use:** User wants to build patches, deploy releases, or troubleshoot build/git issues. All development happens on `dev` branch — never commit to main directly. Always `git pull` after push.

# Build, Deploy & Troubleshoot

## Build

```bash
# Build patches
cd paresh-patches && ./gradlew buildAndroid && cd ..

# Get MPP path (always use this, never glob)
VER=$(grep "^version" paresh-patches/gradle.properties | cut -d= -f2 | tr -d ' ')
MPP="paresh-patches/patches/build/libs/patches-${VER}.mpp"
```

CLI: `morphe-cli.jar` (symlink at project root, run `./setup-cli.sh` to build)
Keystore: `Morphe.keystore` (project root)

## CLI Usage

```bash
# List patches
java -jar morphe-cli.jar list-patches --patches "$MPP" -pvo

# Patch APK (output to builds/, use Morphe.keystore)
java -jar morphe-cli.jar patch \
  -p "$MPP" \
  --keystore Morphe.keystore \
  -o analysis/<app>/builds/<app>_patched.apk \
  -f analysis/<app>/<app>_<version>.<ext>

# Patch + install
java -jar morphe-cli.jar patch \
  -p "$MPP" \
  --keystore Morphe.keystore \
  -o analysis/<app>/builds/<app>_patched.apk \
  -f -i analysis/<app>/<app>_<version>.<ext>

# Install existing patched APK
java -jar morphe-cli.jar utility install -a analysis/<app>/builds/<app>_patched.apk

# Exclusive mode (only specific patches)
java -jar morphe-cli.jar patch \
  -p "$MPP" \
  --keystore Morphe.keystore \
  --exclusive -e "Patch Name" \
  -o analysis/<app>/builds/<app>_patched.apk \
  -f analysis/<app>/<app>_<version>.<ext>
```

## Git/Release Workflow

| Branch | Purpose | Release Type |
|--------|---------|-------------|
| `dev` | Development & testing | Pre-release (v1.x.0-dev.N) |
| `main` | Stable (after verified) | Stable (v1.x.0) |

- **All development on `dev`** — never commit directly to main
- Conventional commits: `feat:` (minor), `fix:` (patch), `docs:`/`chore:` (no release)
- ALWAYS `git pull` after push — GitHub Actions auto-updates CHANGELOG.md, gradle.properties, patches-bundle.json, patches-list.json

## Troubleshooting

| Error | Fix |
|-------|-----|
| Build auth failure | Set `gpr.user`/`gpr.key` in `~/.gradle/gradle.properties` |
| Fingerprint match failure | Verify smali, update filters for new app version |
| `instructionMatches` without `filters` | Add `filters` to fingerprint or don't use `instructionMatches` |
| Patched app crashes | Check `adb logcat`, verify register numbers in smali |
| "Not compatible with device" | Use XAPK/APKM with correct `ApkFileType` |
| Push rejected | `git pull` first (auto-updated files) |
| Duplicate .mpp in release | `gh release delete-asset v1.2.0 old.mpp` |
| Google login fails | Expected — signature mismatch after re-signing |
| Multiple .mpp files in build | Use version from gradle.properties, not glob |

## Debugging

```bash
adb logcat | grep 'morphe\|AndroidRuntime'
```
