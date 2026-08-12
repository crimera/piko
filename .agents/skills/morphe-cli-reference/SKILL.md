---
name: morphe-cli-reference
description: Morphe CLI v1.11.0 (morphe-desktop) complete command reference — patch, list-patches, list-versions, options-create, install, uninstall, clear-cache. Use when running CLI commands, testing patches, or troubleshooting CLI usage.
---

> **When to use:** User wants to run CLI commands — patching, listing, installing, testing. Always resolve MPP path from `gradle.properties` version first. Always use `morphe-cli.jar` (not full path) and `--keystore Morphe.keystore`. Output patched APKs to `analysis/<app>/builds/`. Use `-f` for split APK base.apk.

# Morphe CLI v1.11.0 Reference

- CLI jar: `morphe-cli.jar` (symlink at project root, run `./setup-cli.sh` to download)
- Upstream repo: `MorpheApp/morphe-desktop` (formerly morphe-cli)
- Keystore: `Morphe.keystore` (project root — use for all patches)
- MPP path: always resolve from `gradle.properties` version (see below)

## Getting the MPP path

Always use this pattern to get the exact MPP file:

```bash
VER=$(grep "^version" paresh-patches/gradle.properties | cut -d= -f2 | tr -d ' ')
MPP="paresh-patches/patches/build/libs/patches-${VER}.mpp"
```

## patch — Patch an APK

```bash
java -jar morphe-cli.jar patch -p "$MPP" [options] <apk>
```

| Flag | Short | Description | Default |
|------|-------|-------------|---------|
| `--patches` | `-p` | Path to MPP file OR GitHub/GitLab repo URL (required, repeatable) | |
| `--out` | `-o` | Output APK path. If omitted, saved next to input in app-named subfolder | |
| `--install` | `-i` | Install via ADB after patching (optional device serial) | |
| `--enable` | `-e` | Enable patch by name | |
| `--disable` | `-d` | Disable patch by name | |
| `--ei` | | Enable patch by index | |
| `--di` | | Disable patch by index | |
| `--exclusive` | | Disable all except enabled patches | false |
| `--options` | `-O` | Set patch option: `-Okey=value` | |
| `--options-file` | | Path to options JSON file | |
| `--options-update` | | Auto-update options JSON after patching | false |
| `--force` | `-f` | Skip version compatibility check | false |
| `--mount` | | Install by mounting (root required) | false |
| `--unsigned` | | Don't sign the output APK | |
| `--keystore` | | Custom keystore file path | |
| `--keystore-password` | | Keystore password (empty by default) | |
| `--keystore-entry-alias` | | Key alias | Morphe |
| `--keystore-entry-password` | | Key entry password | |
| `--signer` | | Signer name | Morphe |
| `--striplibs` | | Keep only specified architectures (comma-separated, e.g. `arm64-v8a,x86`) | |
| `--temporary-files-path` | `-t` | Custom temp directory | |
| `--result-file` | `-r` | Save JSON patching report to file | |
| `--disable-purge` | | Keep scratch files after patching (for debugging). Default is to purge | false |
| `--continue-on-error` | | Don't stop on first patch failure | false |
| `--bytecode-mode` | | DEX processing: FULL, STRIP_SAFE, or STRIP_FAST | STRIP_FAST |
| `--prerelease` | | Fetch dev pre-release from repo URL (use with `--patches <repo-url>`) | false |
| `--verify-with-sdk` | | Verify patched DEX/APK with Android SDK tools (optional path) | |

### Standard patch

```bash
java -jar morphe-cli.jar patch \
  -p "$MPP" \
  --keystore Morphe.keystore \
  -o analysis/<app>/builds/<app>_patched.apk \
  -f analysis/<app>/apk/<app>_<version>.<ext>
```

### Patch + install

```bash
java -jar morphe-cli.jar patch \
  -p "$MPP" \
  --keystore Morphe.keystore \
  -o analysis/<app>/builds/<app>_patched.apk \
  -f -i analysis/<app>/apk/<app>_<version>.<ext>
```

### Exclusive mode (only specific patches)

```bash
java -jar morphe-cli.jar patch \
  -p "$MPP" \
  --keystore Morphe.keystore \
  --exclusive -e "Premium Unlock" -e "Remove Ads" \
  -o analysis/<app>/builds/<app>_patched.apk \
  -f analysis/<app>/apk/<app>_<version>.<ext>
```

### With patch options

```bash
java -jar morphe-cli.jar patch \
  -p "$MPP" \
  --keystore Morphe.keystore \
  -e "Patch Name" -Okey=value \
  -f analysis/<app>/apk/<app>_<version>.<ext>
```

### Use GitHub/GitLab repo URL directly

```bash
# Stable release
java -jar morphe-cli.jar patch -p https://github.com/user/patches input.apk

# Pre-release (dev branch)
java -jar morphe-cli.jar patch -p https://github.com/user/patches --prerelease input.apk

# GitLab repo
java -jar morphe-cli.jar patch -p https://gitlab.com/user/patches input.apk
```

### Keep temp files for debugging

```bash
java -jar morphe-cli.jar patch -p "$MPP" --disable-purge -f input.apk
```

## list-patches — List available patches

```bash
java -jar morphe-cli.jar list-patches --patches "$MPP" [options]
```

| Flag | Short | Description | Default |
|------|-------|-------------|---------|
| `--patches` | | Path to MPP file (required, repeatable) | |
| `--with-packages` | `-p` | Show compatible packages | false |
| `--with-versions` | `-v` | Show compatible versions | false |
| `--with-options` | `-o` | Show patch options | false |
| `--with-descriptions` | `-d` | Show descriptions | true |
| `--with-universal-patches` | `-u` | Show universal patches | true |
| `--index` | `-i` | Show patch index | true |
| `--include-experimental` | `-x` | Include experimental app versions | false |
| `--filter-package-name` | `-f` | Filter by package name | |
| `--prerelease` | | Fetch dev pre-release from repo URL | false |
| `--out` | | Write to file instead of stdout | |

### Examples

```bash
# Full listing
java -jar morphe-cli.jar list-patches --patches "$MPP" -pvo

# Filter by app
java -jar morphe-cli.jar list-patches --patches "$MPP" -f com.truecaller -pvo

# Include experimental versions
java -jar morphe-cli.jar list-patches --patches "$MPP" -pvox

# Save to file
java -jar morphe-cli.jar list-patches --patches "$MPP" -pvo --out patches.txt
```

## list-versions — Show recommended versions

```bash
java -jar morphe-cli.jar list-versions --patches "$MPP" [options]
```

| Flag | Short | Description | Default |
|------|-------|-------------|---------|
| `--patches` | | Path to MPP file (required, repeatable) | |
| `--filter-package-names` | `-f` | Filter by package name (repeatable) | |
| `--count-unused-patches` | `-u` | Include non-default patches in count | false |
| `--include-experimental` | `-x` | Include experimental versions | false |
| `--prerelease` | | Fetch dev pre-release from repo URL | false |

### Examples

```bash
java -jar morphe-cli.jar list-versions --patches "$MPP"
java -jar morphe-cli.jar list-versions --patches "$MPP" -f com.truecaller
java -jar morphe-cli.jar list-versions --patches "$MPP" -ux
```

## options-create — Generate options JSON

```bash
java -jar morphe-cli.jar options-create -p "$MPP" -o options.json [options]
```

| Flag | Short | Description |
|------|-------|-------------|
| `--patches` | `-p` | Path to MPP file (required) |
| `--out` | `-o` | Output JSON file path (required) |
| `--filter-package-name` | `-f` | Filter by package name |
| `--prerelease` | | Fetch dev pre-release from repo URL |

## utility install — Install APK via ADB

```bash
java -jar morphe-cli.jar utility install -a <apk> [options] [deviceSerials...]
```

| Flag | Short | Description |
|------|-------|-------------|
| `--apk` | `-a` | APK file to install (required) |
| `--mount` | `-m` | Mount over existing app (provide package name) |
| `--route-links` | | Route this app's supported web links to it ("open with") |
| `--disable-stock` | | With --route-links: stop stock package from handling links |

### Examples

```bash
# Standard install
java -jar morphe-cli.jar utility install -a analysis/<app>/builds/<app>_patched.apk

# Install on specific device
java -jar morphe-cli.jar utility install -a app_patched.apk ABC123DEF

# Mount install (root)
java -jar morphe-cli.jar utility install -a app_patched.apk -m com.example.app

# Install + route links
java -jar morphe-cli.jar utility install -a app_patched.apk --route-links --disable-stock com.example.app
```

## utility uninstall — Uninstall app

```bash
java -jar morphe-cli.jar utility uninstall -p <packageName> [options] [deviceSerials...]
```

| Flag | Short | Description | Default |
|------|-------|-------------|---------|
| `--package-name` | `-p` | Package name (required) | |
| `--unmount` | `-u` | Unmount instead of uninstall | false |

### Examples

```bash
java -jar morphe-cli.jar utility uninstall -p com.example.app
java -jar morphe-cli.jar utility uninstall -p com.example.app -u
```

## utility clear-cache — Delete cached files

```bash
java -jar morphe-cli.jar utility clear-cache [options]
```

| Flag | Description |
|------|-------------|
| `--info` | Show per-category breakdown of what was cleared and space freed |

### Examples

```bash
java -jar morphe-cli.jar utility clear-cache
java -jar morphe-cli.jar utility clear-cache --info
```

## Full Workflow

```bash
# 1. Build patches
cd paresh-patches && ./gradlew buildAndroid && cd ..

# 2. Get MPP path
VER=$(grep "^version" paresh-patches/gradle.properties | cut -d= -f2 | tr -d ' ')
MPP="paresh-patches/patches/build/libs/patches-${VER}.mpp"

# 3. List patches
java -jar morphe-cli.jar list-patches --patches "$MPP" -pvo

# 4. Patch + install
java -jar morphe-cli.jar patch \
  -p "$MPP" \
  --keystore Morphe.keystore \
  -o analysis/<app>/builds/<app>_patched.apk \
  -f -i analysis/<app>/apk/<app>_<version>.<ext>

# 5. Clear cache when done
java -jar morphe-cli.jar utility clear-cache --info
```
