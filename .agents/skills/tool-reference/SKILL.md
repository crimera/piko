---
name: tool-reference
description: Optimized command-line arguments for all Android RE tools — jadx, baksmali, aapt, apkid, rg. Use when running any analysis tool to get the best flags and arguments.
---

> **When to use:** Running any RE tool. Use these optimized flags for best results. Always use `rg` instead of `grep`. For jadx, prefer Kaggle remote (`.kiro/jadx-decompile`) for large APKs, local only for small/quick tasks.

# Tool Reference — Optimized Arguments

## ripgrep (rg) — Fast Code Search

Use `rg` instead of `grep` everywhere. It's faster, respects .gitignore, and has better output.

```bash
# Search Java files only
rg "pattern" path/ -g "*.java"

# List matching files only (fast overview)
rg "pattern" path/ -g "*.java" -l

# With context lines
rg "pattern" path/ -g "*.java" -C 3    # 3 lines before+after
rg "pattern" path/ -g "*.java" -A 10   # 10 lines after

# Case insensitive
rg -i "pattern" path/

# Count matches per file
rg "pattern" path/ -g "*.java" -c

# Only show matching text (extract values)
rg -o "pattern" path/

# Max matches per file (avoid flooding)
rg "pattern" path/ --max-count 3

# Search smali files
rg "pattern" path/ -g "*.smali"

# Fixed string (no regex interpretation)
rg -F "exact.string.match" path/

# Multiple patterns
rg "pattern1|pattern2|pattern3" path/ -g "*.java" -l

# Exclude directories
rg "pattern" path/ --glob '!**/test/**'
```

## jadx — APK Decompiler

### Remote (Kaggle — primary method)
```bash
.kiro/jadx-decompile "<url>" analysis/<app>/
```

### Local (small APKs only)
```bash
# Full decompile with all optimizations
jadx -d output/ input.apk \
  --deobf \
  --show-bad-code \
  --decompilation-mode restructure \
  -j $(nproc) \
  -Pdex-input.verify-checksum=no \
  -Pkotlin-metadata.class-alias=yes \
  -Pkotlin-metadata.method-args=yes \
  -Pkotlin-metadata.fields=yes \
  -Pkotlin-metadata.data-class=yes \
  -Pkotlin-metadata.to-string=yes \
  -Pkotlin-metadata.getters=yes \
  --use-source-name-as-class-name-alias always \
  --use-kotlin-methods-for-var-names apply-and-hide \
  --rename-flags all

# Decompile single class (quick check)
jadx --single-class "com.example.ClassName" -d output/ input.apk

# Sources only (no resources — faster)
jadx -d output/ --no-res input.apk
```

### Key flags
| Flag | Purpose |
|------|---------|
| `--deobf` | Rename obfuscated a/b/c to readable names |
| `--show-bad-code` | Show broken code instead of hiding |
| `--decompilation-mode restructure` | Cleanest Java output |
| `-j N` | Thread count (use all cores) |
| `--no-res` | Skip resources (faster) |
| `--single-class` | Decompile one class only |

## baksmali — DEX Disassembler

```bash
# Disassemble single DEX
baksmali d classes.dex -o smali/

# Disassemble specific DEX from APK
baksmali d "app.apk/classes2.dex" -o smali/classes2/

# Disassemble with code offsets (useful for debugging)
baksmali d --code-offsets classes.dex -o smali/

# Disassemble specific classes only
baksmali d --classes "Lcom/example/Target;" classes.dex -o smali/

# Use all cores
baksmali d -j $(nproc) classes.dex -o smali/

# Disassemble ALL DEX files from APK
for dex in $(unzip -l app.apk | rg "\.dex" | awk '{print $4}'); do
    name=$(basename $dex .dex)
    unzip -o app.apk $dex -d /tmp/dex_extract
    baksmali d /tmp/dex_extract/$dex -o smali/$name
done
```

## aapt — APK Info

```bash
# Package name, version, SDK (most common)
aapt dump badging app.apk | head -5

# Full manifest as XML tree
aapt dump xmltree app.apk AndroidManifest.xml

# Check for split APK requirements
aapt dump xmltree app.apk AndroidManifest.xml | rg -i "split|requiredSplit"

# List permissions
aapt dump permissions app.apk

# List all resources
aapt dump resources app.apk

# List strings
aapt dump strings app.apk
```

## apkid — Protection Detection

```bash
# Basic scan
uvx apkid app.apk

# Verbose (more detail)
uvx apkid -v app.apk

# Recursive (scan inside split APKs)
uvx apkid -r app.apk

# JSON output (for parsing)
uvx apkid -j app.apk
```

### What apkid tells you
| Output | Meaning |
|--------|---------|
| `compiler: r8` | R8 optimizer used (standard) |
| `compiler: d8` | D8 compiler (no optimization) |
| `obfuscator: proguard` | ProGuard obfuscation |
| `packer: *` | App is packed (harder to patch) |
| `anti_vm` | Emulator detection present |
| `anti_debug` | Debugger detection present |

## unzip — APK Contents

```bash
# List all files in APK
unzip -l app.apk

# List DEX files
unzip -l app.apk | rg "\.dex"

# List native libraries
unzip -l app.apk | rg "\.so|lib/"

# Check framework (React Native, Flutter)
unzip -l app.apk | rg "index.android.bundle|libflutter|libapp"

# Extract specific file
unzip -o app.apk classes5.dex -d /tmp/

# Extract decompiled zip
unzip -qo decompiled.zip -d decompiled/
```

## strings — Binary String Extraction

```bash
# Extract all printable strings from APK
strings app.apk | rg -i "premium|entitlement|license"

# Find base64 encoded URLs
strings app.apk | rg "aHR0c" | while read b; do echo "$b" | base64 -d 2>/dev/null; echo; done

# Find API endpoints
strings app.apk | rg "https?://" | sort -u
```
