---
name: apktool
description: Android APK unpacking and resource extraction tool for reverse engineering. Use when you need to decode APK files, extract resources, examine AndroidManifest.xml, analyze smali code, or repackage modified APKs.
---

# Apktool - Android APK Unpacking and Resource Extraction

You are helping the user reverse engineer Android APK files using apktool for security analysis, vulnerability discovery, and understanding app internals.

## Tool Overview

Apktool is a tool for reverse engineering Android APK files. It can decode resources to nearly original form and rebuild them after modifications. It's essential for:
- Extracting readable AndroidManifest.xml
- Decoding resources (XML layouts, strings, images)
- Disassembling DEX to smali code
- Analyzing app structure and permissions
- Repackaging modified APKs

## Prerequisites

- **apktool** must be installed on the system
- Java Runtime Environment (JRE) required
- Sufficient disk space (unpacked APK is typically 2-5x original size)
- Write permissions in output directory

## Instructions

### 1. Basic APK Unpacking (Most Common)

When the user asks to unpack, decode, or analyze an APK:

**Standard decode command:**
```bash
apktool d <apk-file> -o <output-directory>
```

**Example:**
```bash
apktool d app.apk -o app-unpacked
```

**With force overwrite (if directory exists):**
```bash
apktool d app.apk -o app-unpacked -f
```

### 2. Understanding Output Structure

After unpacking, the output directory contains:

```
app-unpacked/
├── AndroidManifest.xml          # Readable manifest (permissions, components)
├── apktool.yml                  # Apktool metadata (version info, SDK levels)
├── original/                    # Original META-INF certificates
│   └── META-INF/
├── res/                         # Decoded resources
│   ├── layout/                  # XML layouts
│   ├── values/                  # Strings, colors, dimensions
│   ├── drawable/                # Images and drawables
│   └── ...
├── smali/                       # Disassembled DEX code (smali format)
│   └── com/company/app/        # Package structure
├── assets/                      # App assets (if present)
├── lib/                         # Native libraries (if present)
│   ├── arm64-v8a/
│   ├── armeabi-v7a/
│   └── ...
└── unknown/                     # Files apktool couldn't classify
```

### 3. Selective Decoding (Performance Optimization)

**Skip resources (code analysis only):**
```bash
apktool d app.apk -o app-code-only -r
# or
apktool d app.apk -o app-code-only --no-res
```
- Faster processing
- Only extracts smali code and manifest
- Use when you only need to analyze code logic

**Skip source code (resource analysis only):**
```bash
apktool d app.apk -o app-resources-only -s
# or
apktool d app.apk -o app-resources-only --no-src
```
- Faster processing
- Only extracts resources and manifest
- Use when you only need resources, strings, layouts

### 4. Common Analysis Tasks

#### A. Examining AndroidManifest.xml

The manifest reveals critical security information:

```bash
# After unpacking
cat app-unpacked/AndroidManifest.xml
```

**Look for:**
- **Permissions**: What device features/data the app accesses
- **Exported components**: Activities, services, receivers accessible from other apps
- **Intent filters**: How the app responds to system/app intents
- **Backup settings**: `android:allowBackup="true"` (security risk)
- **Debuggable flag**: `android:debuggable="true"` (major security issue)
- **Network security config**: Custom certificate pinning, cleartext traffic
- **Min/Target SDK versions**: Outdated versions may have vulnerabilities

**Example analysis commands:**
```bash
# Find all permissions
grep "uses-permission" app-unpacked/AndroidManifest.xml

# Find exported components
grep "exported=\"true\"" app-unpacked/AndroidManifest.xml

# Check if debuggable
grep "debuggable" app-unpacked/AndroidManifest.xml

# Find all activities
grep "android:name.*Activity" app-unpacked/AndroidManifest.xml
```

#### B. Extracting Strings and Resources

```bash
# View all string resources
cat app-unpacked/res/values/strings.xml

# Search for API keys, URLs, credentials
grep -r "api" app-unpacked/res/values/
grep -r "http" app-unpacked/res/values/
grep -r "password\|secret\|key\|token" app-unpacked/res/values/

# Find hardcoded URLs in resources
grep -rE "https?://" app-unpacked/res/
```

#### C. Analyzing Smali Code

Smali is the disassembled Dalvik bytecode format:

```bash
# Find specific class
find app-unpacked/smali -name "*Login*.smali"
find app-unpacked/smali -name "*Auth*.smali"

# Search for security-relevant code
grep -r "crypto\|encrypt\|decrypt" app-unpacked/smali/
grep -r "http\|https\|url" app-unpacked/smali/
grep -r "password\|credential\|token" app-unpacked/smali/

# Find native library usage
grep -r "System.loadLibrary" app-unpacked/smali/

# Find file operations
grep -r "openFileOutput\|openFileInput" app-unpacked/smali/
```

**Note**: Smali is harder to read than Java source. Consider using jadx for Java decompilation for easier analysis.

#### D. Examining Native Libraries

```bash
# List native libraries
ls -lah app-unpacked/lib/

# Check architectures supported
ls app-unpacked/lib/

# Identify library types
file app-unpacked/lib/arm64-v8a/*.so

# Search for interesting strings in libraries
strings app-unpacked/lib/arm64-v8a/libnative.so | grep -i "http\|key\|password"
```

### 5. Repackaging APK (Build)

After modifying resources or smali code:

```bash
apktool b app-unpacked -o app-modified.apk
```

**Important**: Rebuilt APKs must be signed before installation:
```bash
# Generate keystore (one-time setup)
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias

# Sign APK
jarsigner -verbose -keystore my-release-key.jks app-modified.apk my-key-alias

# Verify signature
jarsigner -verify app-modified.apk

# Zipalign (optimization)
zipalign -v 4 app-modified.apk app-modified-aligned.apk
```

### 6. Framework Management

For system apps or apps dependent on device manufacturer frameworks:

```bash
# Install framework
apktool if framework-res.apk

# List installed frameworks
apktool list-frameworks

# Decode with specific framework
apktool d -t <tag> app.apk
```

## Common Workflows

### Workflow 1: Security Analysis

```bash
# 1. Unpack APK
apktool d target.apk -o target-unpacked

# 2. Examine manifest for security issues
cat target-unpacked/AndroidManifest.xml

# 3. Search for hardcoded credentials
grep -r "password\|api_key\|secret\|token" target-unpacked/res/

# 4. Check for debuggable flag
grep "debuggable" target-unpacked/AndroidManifest.xml

# 5. Find exported components
grep "exported=\"true\"" target-unpacked/AndroidManifest.xml

# 6. Examine network security config
cat target-unpacked/res/xml/network_security_config.xml 2>/dev/null
```

### Workflow 2: IoT App Analysis

For IoT companion apps, find device communication details:

```bash
# 1. Unpack APK
apktool d iot-app.apk -o iot-app-unpacked

# 2. Search for device endpoints
grep -rE "https?://[^\"']+" iot-app-unpacked/res/ | grep -v "google\|android"

# 3. Find API keys
grep -r "api\|key" iot-app-unpacked/res/values/strings.xml

# 4. Locate device communication code
find iot-app-unpacked/smali -name "*Device*.smali"
find iot-app-unpacked/smali -name "*Network*.smali"
find iot-app-unpacked/smali -name "*Api*.smali"

# 5. Check for certificate pinning
grep -r "certificatePinner\|TrustManager" iot-app-unpacked/smali/
```

### Workflow 3: Resource Extraction Only

```bash
# Fast resource-only extraction
apktool d app.apk -o app-resources -s

# Extract app icon
cp app-resources/res/mipmap-xxxhdpi/ic_launcher.png ./

# Extract strings for localization
cat app-resources/res/values*/strings.xml

# Extract layouts for UI analysis
ls app-resources/res/layout/
```

### Workflow 4: Quick Code Check (No Resources)

```bash
# Fast code-only extraction
apktool d app.apk -o app-code -r

# Analyze smali quickly
grep -r "http" app-code/smali/ | head -20
grep -r "password" app-code/smali/
```

## Output Formats

Apktool doesn't have built-in output format options, but you can structure your analysis:

**For human-readable reports:**
```bash
# Generate analysis report
{
  echo "=== APK Analysis Report ==="
  echo "APK: app.apk"
  echo "Date: $(date)"
  echo ""
  echo "=== Permissions ==="
  grep "uses-permission" app-unpacked/AndroidManifest.xml
  echo ""
  echo "=== Exported Components ==="
  grep "exported=\"true\"" app-unpacked/AndroidManifest.xml
  echo ""
  echo "=== Package Info ==="
  grep "package=" app-unpacked/AndroidManifest.xml
} > apk-analysis-report.txt
```

## Integration with IoTHackBot Tools

Apktool works well with other analysis workflows:

1. **APK → Network Analysis**:
   - Extract API endpoints from resources
   - Use extracted URLs with curl/wget for testing
   - Feed endpoints to network testing tools

2. **APK → Credential Discovery**:
   - Find hardcoded credentials in resources
   - Test credentials against IoT devices
   - Use with onvifscan or other device testing tools

3. **APK → Code Analysis**:
   - Extract smali code with apktool
   - Decompile to Java with jadx for easier reading
   - Cross-reference findings between both tools

## Best Practices

### 1. Always Examine the Manifest First

```bash
apktool d app.apk -o app-unpacked
cat app-unpacked/AndroidManifest.xml | less
```

The manifest provides the roadmap for further analysis.

### 2. Use Selective Decoding for Speed

- Code only: `-r` flag
- Resources only: `-s` flag
- Full decode: No flags (default)

### 3. Search Systematically

```bash
# Create analysis script
cat > analyze.sh << 'EOF'
#!/bin/bash
APK_DIR="$1"
echo "[+] Searching for URLs..."
grep -rE "https?://" "$APK_DIR/res/" | grep -v "schema\|google\|android"
echo "[+] Searching for API keys..."
grep -ri "api.*key\|apikey" "$APK_DIR/res/"
echo "[+] Searching for secrets..."
grep -ri "secret\|password\|credential" "$APK_DIR/res/"
EOF
chmod +x analyze.sh
./analyze.sh app-unpacked
```

### 4. Document Your Findings

Keep notes on:
- APK package name and version
- Interesting permissions
- Hardcoded credentials/URLs
- Exported components
- Security misconfigurations

### 5. Combine with Jadx

Use both tools together:
- **Apktool**: For resources, manifest, and detailed smali
- **Jadx**: For readable Java source code

## Troubleshooting

### Problem: "brut.directory.DirectoryException: Framework"

**Solution**: Install framework resources:
```bash
apktool if <framework-res.apk>
```

### Problem: Decoding fails with resource errors

**Solution**: Use `--keep-broken-res` flag:
```bash
apktool d app.apk -o output --keep-broken-res
```

### Problem: "Input file was not found or was not readable"

**Solution**: Check file path and permissions:
```bash
ls -l app.apk
file app.apk  # Should show "Zip archive data"
```

### Problem: Out of memory error

**Solution**: Increase Java heap size:
```bash
export _JAVA_OPTIONS="-Xmx2048m"
apktool d large-app.apk
```

### Problem: Build fails after modifications

**Solution**: Validate your smali/XML syntax:
```bash
# Check for syntax errors
apktool b app-unpacked -o test.apk --use-aapt2
```

### Problem: APK won't install after repackaging

**Solution**: Sign the APK:
```bash
jarsigner -verbose -keystore debug.keystore rebuilt.apk androiddebugkey
```

## Important Notes

- Apktool requires Java Runtime Environment (JRE)
- Decoded APKs are typically 2-5x larger than original
- Smali code is more verbose than Java source (use jadx for Java)
- Always work on copies of APK files, never originals
- Repackaging requires signing before installation
- Some obfuscated apps may have unreadable class/method names
- System apps may require framework installation

## Security and Ethics

**IMPORTANT**: Only analyze APKs you own or have permission to analyze.

- Respect intellectual property and licensing
- Follow responsible disclosure for vulnerabilities
- Don't distribute modified APKs without authorization
- Be aware of terms of service and EULAs
- Use for authorized security testing and research only

## Example Analysis Session

```bash
# Complete analysis workflow
TARGET="myapp.apk"
OUTPUT="myapp-analysis"

# 1. Unpack
echo "[+] Unpacking APK..."
apktool d "$TARGET" -o "$OUTPUT"

# 2. Basic info
echo "[+] Package info:"
grep "package=" "$OUTPUT/AndroidManifest.xml"

# 3. Permissions
echo "[+] Permissions:"
grep "uses-permission" "$OUTPUT/AndroidManifest.xml"

# 4. Exported components
echo "[+] Exported components:"
grep "exported=\"true\"" "$OUTPUT/AndroidManifest.xml"

# 5. Search for secrets
echo "[+] Searching for hardcoded secrets..."
grep -r "api.*key\|password\|secret" "$OUTPUT/res/" | grep -v "^Binary"

# 6. Find URLs
echo "[+] Finding URLs..."
grep -rE "https?://[^\"']+" "$OUTPUT/res/" | grep -v "schema\|xmlns"

# 7. Check debuggable
echo "[+] Debug status:"
grep "debuggable" "$OUTPUT/AndroidManifest.xml" || echo "Not debuggable (good)"

# 8. Summary
echo "[+] Analysis complete. Output in: $OUTPUT/"
```

## Success Criteria

A successful apktool analysis includes:

- APK successfully decoded without errors
- AndroidManifest.xml is readable and analyzed
- Resources extracted and searchable
- Smali code available for inspection
- Security-relevant findings documented
- Output organized in clear directory structure
- Any modifications can be repackaged if needed

## Quick Reference

```bash
# Decode (unpack)
apktool d <apk> -o <output-dir>

# Decode with force overwrite
apktool d <apk> -o <output-dir> -f

# Decode without resources (faster)
apktool d <apk> -o <output-dir> -r

# Decode without source (faster)
apktool d <apk> -o <output-dir> -s

# Build (repack)
apktool b <unpacked-dir> -o <output-apk>

# Install framework
apktool if <framework.apk>

# Empty framework cache
apktool empty-framework-dir
```
