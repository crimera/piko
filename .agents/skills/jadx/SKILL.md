---
name: jadx
description: Android APK decompiler that converts DEX bytecode to readable Java source code. Use when you need to decompile APK files, analyze app logic, search for vulnerabilities, find hardcoded credentials, or understand app behavior through readable source code.
---

# Jadx - Android APK Decompiler

You are helping the user decompile Android APK files using jadx to convert DEX bytecode into readable Java source code for security analysis, vulnerability discovery, and understanding app internals.

## Tool Overview

Jadx is a dex to Java decompiler that produces clean, readable Java source code from Android APK files. Unlike apktool (which produces smali), jadx generates actual Java code that's much easier to read and analyze. It's essential for:
- Converting DEX bytecode to readable Java source
- Understanding app logic and control flow
- Finding security vulnerabilities in code
- Discovering hardcoded credentials, API keys, URLs
- Analyzing encryption/authentication implementations
- Searching through code with familiar Java syntax

## Prerequisites

- **jadx** (and optionally **jadx-gui**) must be installed
- Java Runtime Environment (JRE) required
- Sufficient disk space (decompiled output is typically 3-10x APK size)
- Write permissions in output directory

## GUI vs CLI

Jadx provides two interfaces:

**CLI (jadx)**: Command-line interface
- Best for automation and scripting
- Batch processing multiple APKs
- Integration with other tools
- Headless server environments

**GUI (jadx-gui)**: Graphical interface
- Interactive code browsing
- Built-in search functionality
- Cross-references and navigation
- Easier for manual analysis
- Syntax highlighting

**When to use each:**
- Use **CLI** for automated analysis, scripting, CI/CD pipelines
- Use **GUI** for interactive exploration and deep-dive analysis

## Instructions

### 1. Basic APK Decompilation (Most Common)

**Standard decompile command:**
```bash
jadx <apk-file> -d <output-directory>
```

**Example:**
```bash
jadx app.apk -d app-decompiled
```

**With deobfuscation (recommended for obfuscated apps):**
```bash
jadx --deobf app.apk -d app-decompiled
```

### 2. Understanding Output Structure

After decompilation, the output directory contains:

```
app-decompiled/
├── sources/                           # Java source code
│   └── com/company/app/              # Package structure
│       ├── MainActivity.java
│       ├── utils/
│       ├── network/
│       └── ...
└── resources/                         # Decoded resources
    ├── AndroidManifest.xml           # Readable manifest
    ├── res/                          # Resources
    │   ├── layout/                   # XML layouts
    │   ├── values/                   # Strings, colors
    │   ├── drawable/                 # Images
    │   └── ...
    └── assets/                       # App assets
```

### 3. Decompilation Options

#### A. Performance Options

**Multi-threaded decompilation (faster):**
```bash
jadx -j 4 app.apk -d output
# -j specifies number of threads (default: CPU cores)
```

**Skip resources (code only, much faster):**
```bash
jadx --no-res app.apk -d output
```

**Skip source code (resources only):**
```bash
jadx --no-src app.apk -d output
```

#### B. Deobfuscation Options

**Enable deobfuscation:**
```bash
jadx --deobf app.apk -d output
```
- Renames obfuscated classes (a.b.c → meaningful names)
- Attempts to recover original names
- Makes code much more readable
- Essential for obfuscated/minified apps

**Deobfuscation map output:**
```bash
jadx --deobf --deobf-rewrite-cfg --deobf-use-sourcename app.apk -d output
```
- More aggressive deobfuscation
- Uses source file names as hints
- Rewrites control flow graphs

#### C. Output Control

**Show inconsistent/bad code:**
```bash
jadx --show-bad-code app.apk -d output
```
- Shows code that couldn't be decompiled cleanly
- Useful for finding obfuscation or anti-decompilation tricks
- May contain syntax errors but reveals structure

**Export as Gradle project:**
```bash
jadx --export-gradle app.apk -d output
```
- Creates buildable Gradle Android project
- Useful for rebuilding/modifying app
- Includes build.gradle files

**Fallback mode (when decompilation fails):**
```bash
jadx --fallback app.apk -d output
```
- Uses alternative decompilation strategy
- Produces less clean code but handles edge cases

### 4. Common Analysis Tasks

#### A. Searching for Sensitive Information

**After decompilation, search for common security issues:**

```bash
# Search for API keys
grep -r "api.*key\|apikey\|API_KEY" app-decompiled/sources/

# Search for passwords and credentials
grep -r "password\|credential\|secret" app-decompiled/sources/

# Search for hardcoded URLs
grep -rE "https?://[^\"]+" app-decompiled/sources/

# Search for encryption keys
grep -r "AES\|DES\|RSA\|encryption.*key" app-decompiled/sources/

# Search for tokens
grep -r "token\|auth.*token\|bearer" app-decompiled/sources/

# Search for database passwords
grep -r "jdbc\|database\|db.*password" app-decompiled/sources/
```

#### B. Finding Security Vulnerabilities

**SQL Injection:**
```bash
grep -r "SELECT.*FROM.*WHERE" app-decompiled/sources/ | grep -v "PreparedStatement"
grep -r "rawQuery\|execSQL" app-decompiled/sources/
```

**Insecure Crypto:**
```bash
grep -r "DES\|MD5\|SHA1" app-decompiled/sources/
grep -r "SecureRandom.*setSeed" app-decompiled/sources/
grep -r "Cipher.getInstance" app-decompiled/sources/ | grep -v "AES/GCM"
```

**Insecure Storage:**
```bash
grep -r "SharedPreferences" app-decompiled/sources/
grep -r "MODE_WORLD_READABLE\|MODE_WORLD_WRITABLE" app-decompiled/sources/
grep -r "openFileOutput" app-decompiled/sources/
```

**WebView vulnerabilities:**
```bash
grep -r "setJavaScriptEnabled.*true" app-decompiled/sources/
grep -r "addJavascriptInterface" app-decompiled/sources/
grep -r "WebView.*loadUrl" app-decompiled/sources/
```

**Certificate pinning bypass:**
```bash
grep -r "TrustManager\|HostnameVerifier" app-decompiled/sources/
grep -r "checkServerTrusted" app-decompiled/sources/
```

#### C. Understanding App Logic

**Find entry points:**
```bash
# Main activities
grep -r "extends Activity\|extends AppCompatActivity" app-decompiled/sources/

# Application class
grep -r "extends Application" app-decompiled/sources/

# Services
grep -r "extends Service" app-decompiled/sources/

# Broadcast receivers
grep -r "extends BroadcastReceiver" app-decompiled/sources/
```

**Trace network communication:**
```bash
# Find HTTP client usage
grep -r "HttpURLConnection\|OkHttpClient\|Retrofit" app-decompiled/sources/

# Find API endpoints
grep -r "@GET\|@POST\|@PUT\|@DELETE" app-decompiled/sources/

# Find base URLs
grep -r "baseUrl\|BASE_URL\|API_URL" app-decompiled/sources/
```

**Find authentication logic:**
```bash
grep -r "login\|Login\|authenticate\|Authorization" app-decompiled/sources/
grep -r "jwt\|JWT\|bearer\|Bearer" app-decompiled/sources/
```

#### D. Analyzing Specific Classes

**After identifying interesting classes, read them directly:**
```bash
# View specific class
cat app-decompiled/sources/com/example/app/LoginActivity.java

# Use less for pagination
less app-decompiled/sources/com/example/app/network/ApiClient.java

# Search within specific class
grep "password" app-decompiled/sources/com/example/app/LoginActivity.java
```

### 5. GUI Mode (Interactive Analysis)

**Launch GUI:**
```bash
jadx-gui app.apk
```

**GUI features:**
- **Full-text search**: Ctrl+Shift+F (search all code)
- **Find usage**: Right-click on class/method → "Find usage"
- **Go to declaration**: Ctrl+Click on any class/method
- **Decompilation**: Click any class to see Java code
- **Save decompiled code**: File → Save all
- **Export options**: File → Export as Gradle project

**GUI workflow:**
1. Open APK with jadx-gui
2. Browse package structure in left panel
3. Use search (Ctrl+Shift+F) to find keywords
4. Click results to view code in context
5. Follow cross-references with Ctrl+Click
6. Save interesting findings

### 6. Integration with Other Tools

#### Combine Jadx with Apktool

Both tools complement each other:

**Jadx strengths:**
- Readable Java source code
- Easy to understand logic
- Fast searching through code

**Apktool strengths:**
- Accurate resource extraction
- Smali code (closer to original)
- Can rebuild/repackage APKs

**Recommended workflow:**
```bash
# Use jadx for code analysis
jadx --deobf app.apk -d app-jadx

# Use apktool for resources and smali
apktool d app.apk -o app-apktool

# Analyze both outputs
grep -r "API_KEY" app-jadx/sources/
grep -r "api_key" app-apktool/res/
```

## Common Workflows

### Workflow 1: Security Assessment

```bash
# 1. Decompile with deobfuscation
jadx --deobf app.apk -d app-decompiled

# 2. Search for hardcoded secrets
echo "[+] Searching for API keys..."
grep -ri "api.*key\|apikey" app-decompiled/sources/ | tee findings-apikeys.txt

echo "[+] Searching for passwords..."
grep -ri "password\|passwd\|pwd" app-decompiled/sources/ | tee findings-passwords.txt

echo "[+] Searching for URLs..."
grep -rE "https?://[^\"]+" app-decompiled/sources/ | tee findings-urls.txt

# 3. Check crypto usage
echo "[+] Checking crypto implementations..."
grep -r "Cipher\|SecretKey\|KeyStore" app-decompiled/sources/ | tee findings-crypto.txt

# 4. Check for insecure storage
echo "[+] Checking storage mechanisms..."
grep -r "SharedPreferences\|SQLite\|openFileOutput" app-decompiled/sources/ | tee findings-storage.txt

# 5. Summary
echo "[+] Analysis complete. Check findings-*.txt files"
```

### Workflow 2: IoT App Analysis

For IoT companion apps, find device communication:

```bash
# 1. Decompile
jadx --deobf iot-app.apk -d iot-app-decompiled

# 2. Find device communication
echo "[+] Finding device endpoints..."
grep -rE "https?://[^\"]+" iot-app-decompiled/sources/ | \
  grep -v "google\|android\|facebook" | \
  tee device-endpoints.txt

# 3. Find API structure
echo "[+] Finding API definitions..."
grep -r "@GET\|@POST\|@PUT" iot-app-decompiled/sources/ | tee api-endpoints.txt

# 4. Find authentication
echo "[+] Finding auth mechanisms..."
grep -r "Authorization\|authentication\|apiKey" iot-app-decompiled/sources/ | tee auth-methods.txt

# 5. Find device discovery
echo "[+] Finding device discovery..."
grep -r "discover\|scan\|broadcast\|mdns" iot-app-decompiled/sources/ | tee device-discovery.txt

# 6. Check for certificate pinning
echo "[+] Checking certificate pinning..."
grep -r "CertificatePinner\|TrustManager" iot-app-decompiled/sources/ | tee cert-pinning.txt
```

### Workflow 3: Quick Credential Check

```bash
# Fast decompilation without resources
jadx --no-res --deobf app.apk -d app-code

# Search for common credential patterns
grep -r "username.*password\|user.*pass" app-code/sources/
grep -r "admin\|root\|default.*password" app-code/sources/
grep -r "hardcoded\|TODO.*password\|FIXME.*password" app-code/sources/
```

### Workflow 4: API Endpoint Discovery

```bash
# Decompile
jadx app.apk -d app-decompiled

# Find Retrofit/REST API definitions
find app-decompiled/sources -name "*Api*.java" -o -name "*Service*.java" -o -name "*Client*.java"

# Extract all endpoints
grep -r "@GET\|@POST\|@PUT\|@DELETE\|@PATCH" app-decompiled/sources/ | \
  sed 's/.*@\(GET\|POST\|PUT\|DELETE\|PATCH\)("\([^"]*\)".*/\1 \2/' | \
  sort -u

# Find base URLs
grep -r "baseUrl\|BASE_URL\|API_BASE" app-decompiled/sources/
```

### Workflow 5: Batch Processing Multiple APKs

```bash
# Decompile multiple APKs
for apk in *.apk; do
  name=$(basename "$apk" .apk)
  echo "[+] Processing $apk..."
  jadx --no-res --deobf "$apk" -d "decompiled-$name"

  # Quick search for secrets
  grep -r "api.*key\|password\|secret" "decompiled-$name/sources/" > "findings-$name.txt"
done

echo "[+] All APKs processed. Check findings-*.txt files"
```

## Best Practices

### 1. Always Use Deobfuscation for Production Apps

```bash
# Most production apps are obfuscated
jadx --deobf app.apk -d output
```

Without `--deobf`, you'll see code like:
```java
public class a {
    public void b(String c) { ... }
}
```

With `--deobf`, jadx attempts meaningful names:
```java
public class NetworkClient {
    public void sendRequest(String url) { ... }
}
```

### 2. Use Multi-threading for Large Apps

```bash
# Faster decompilation
jadx -j 8 large-app.apk -d output
```

### 3. Skip Resources for Code-Only Analysis

```bash
# 3-5x faster when you only need code
jadx --no-res app.apk -d output
```

### 4. Search Systematically

Create a search checklist:
- [ ] API keys and secrets
- [ ] Hardcoded credentials
- [ ] URLs and endpoints
- [ ] Crypto implementations
- [ ] Insecure storage
- [ ] WebView vulnerabilities
- [ ] Debug/logging code
- [ ] Commented-out sensitive code

### 5. Use GUI for Deep Analysis

For complex apps:
1. Use CLI for initial decompilation
2. Search for interesting patterns
3. Open in GUI for detailed exploration
4. Use cross-references to trace code flow

### 6. Combine with Runtime Analysis

Static analysis (jadx) + dynamic analysis:
- Use jadx to find API endpoints
- Test endpoints with curl/burp
- Use jadx to understand auth flow
- Test auth with runtime instrumentation (Frida)

## Troubleshooting

### Problem: Decompilation fails with errors

**Solution**: Use fallback mode or show bad code:
```bash
jadx --fallback --show-bad-code app.apk -d output
```

### Problem: Code is unreadable (obfuscated)

**Solution**: Enable deobfuscation:
```bash
jadx --deobf app.apk -d output
```

### Problem: Out of memory error

**Solution**: Increase Java heap size:
```bash
export JAVA_OPTS="-Xmx4096m"
jadx app.apk -d output
```

Or use the built-in option:
```bash
jadx -Xmx4096m app.apk -d output
```

### Problem: Decompilation is very slow

**Solution**: Skip resources or use more threads:
```bash
jadx --no-res -j 8 app.apk -d output
```

### Problem: Some methods show "Can't load method"

**Solution**: Use `--show-bad-code` to see partial decompilation:
```bash
jadx --show-bad-code app.apk -d output
```

### Problem: GUI won't open APK

**Solution**: Use CLI first to check for errors:
```bash
jadx app.apk -d test-output
# If successful, try GUI again
```

## Advanced Features

### Export as Gradle Project

```bash
jadx --export-gradle app.apk -d app-project
cd app-project
./gradlew build
```

Creates a buildable Android Studio project.

### Generate Deobfuscation Map

```bash
jadx --deobf --deobf-use-sourcename app.apk -d output
# Check output/mapping.txt for name mappings
```

### Custom Decompilation Options

```bash
# All options combined
jadx \
  --deobf \
  --deobf-use-sourcename \
  --show-bad-code \
  --no-imports \
  --no-inline-anonymous \
  --no-replace-consts \
  app.apk -d output
```

## Integration with IoTHackBot Tools

Jadx fits into the IoTHackBot workflow:

1. **APK → API Discovery**:
   - Decompile IoT app with jadx
   - Extract API endpoints
   - Test endpoints with network tools

2. **APK → Credential Extraction**:
   - Find hardcoded credentials
   - Test against IoT devices
   - Use with onvifscan, telnetshell

3. **APK → Protocol Analysis**:
   - Understand device communication protocol
   - Capture traffic with iotnet
   - Replay/modify with custom scripts

4. **APK → Device Enumeration**:
   - Find device discovery mechanisms
   - Use wsdiscovery for ONVIF devices
   - Use nmap for network scanning

## Quick Reference

```bash
# Basic decompilation
jadx <apk> -d <output-dir>

# With deobfuscation (recommended)
jadx --deobf <apk> -d <output-dir>

# Fast (no resources)
jadx --no-res <apk> -d <output-dir>

# Multi-threaded
jadx -j <threads> <apk> -d <output-dir>

# Show problematic code
jadx --show-bad-code <apk> -d <output-dir>

# Export as Gradle project
jadx --export-gradle <apk> -d <output-dir>

# GUI mode
jadx-gui <apk>

# Fallback mode
jadx --fallback <apk> -d <output-dir>
```

## Security Analysis Checklist

Use this checklist when analyzing APKs with jadx:

- [ ] Decompile with deobfuscation enabled
- [ ] Search for hardcoded API keys
- [ ] Search for hardcoded credentials
- [ ] Find all HTTP/HTTPS URLs
- [ ] Check crypto implementations (algorithms, key generation)
- [ ] Check certificate pinning implementation
- [ ] Find SharedPreferences usage (storage security)
- [ ] Check WebView security settings
- [ ] Find database operations (SQL injection)
- [ ] Check for debug/logging code
- [ ] Find exported components (from manifest)
- [ ] Check authentication/authorization logic
- [ ] Find file operations (path traversal)
- [ ] Check for native library loading
- [ ] Document all findings

## Important Notes

- Jadx produces Java source, which is approximate (not original)
- Some optimizations/obfuscations may produce uncompilable code
- Decompiled code may differ slightly from original source
- Always cross-check findings with runtime analysis
- Jadx works best with apps compiled with standard tools
- Heavily obfuscated/protected apps may have limited decompilation
- Some anti-tampering mechanisms detect decompilation

## Security and Ethics

**IMPORTANT**: Only decompile APKs you own or have permission to analyze.

- Respect intellectual property and licensing
- Follow responsible disclosure for vulnerabilities
- Don't distribute decompiled source code
- Be aware of terms of service and EULAs
- Use for authorized security testing and research only
- Some jurisdictions have laws against reverse engineering

## Success Criteria

A successful jadx analysis includes:

- APK successfully decompiled to readable Java code
- Deobfuscation applied (if app was obfuscated)
- All source code searchable and readable
- Security-relevant findings documented
- API endpoints and URLs extracted
- Crypto and authentication logic understood
- Integration points with other systems identified
- Findings verified with runtime testing when possible
