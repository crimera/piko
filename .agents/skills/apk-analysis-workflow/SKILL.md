---
name: apk-analysis-workflow
description: Step-by-step APK analysis workflow — decompile, identify protections, find targets, extract smali. Use when starting analysis of a new app for patching.
---

> **When to use:** Starting analysis of a new app. Follow steps in order: setup folder → identify APK → decompile (Kaggle) → extract smali → search targets → document findings. Always use `rg` not `grep`. Always verify targets in smali before writing fingerprints.

# APK Analysis Workflow

## Step 1: Setup Analysis Folder

```bash
APP="appname"
mkdir -p analysis/$APP/notes
# Move + rename APK: <app>_<version>.<ext>
mv /path/to/downloaded.apk analysis/$APP/${APP}_<version>.<ext>
cd analysis/$APP
```

## Step 2: Identify the APK

```bash
aapt dump badging ${APP}_*.apk* | head -5       # Package, version, SDK
uvx apkid ${APP}_*.apk*                          # Protections/obfuscators
```

APKiD tells you: compiler (dx/d8/r8), obfuscator (ProGuard/R8/DexGuard), packer.

## Step 3: Check APK Type

```bash
aapt dump xmltree ${APP}_*.apk* AndroidManifest.xml | rg -i "split|requiredSplit"
unzip -l ${APP}_*.apk* | rg "\.dex"
unzip -l ${APP}_*.apk* | rg "index.android.bundle|libflutter|libapp"
```

| What you see | ApkFileType |
|-------------|-------------|
| No requiredSplitTypes | `APK` |
| requiredSplitTypes in manifest | `XAPK` |
| APKM format | `APKM` |
| index.android.bundle | React Native (JS, not Java) |
| libflutter.so | Flutter (logic in libapp.so) |

## Step 4: Decompile (Kaggle — primary method)

```bash
# Remote decompile on Kaggle (4 cores, 28GB RAM)
.kiro/jadx-decompile "<apk-download-url>" analysis/$APP/

# Unzip the output
cd analysis/$APP && unzip *_decompiled.zip -d decompiled/
```

For quick local decompile of small APKs only:
```bash
jadx -d decompiled ${APP}_*.apk --deobf --show-bad-code -m restructure --no-res
```

## Step 5: Extract Smali (CRITICAL)

The patcher works on smali, not Java. Always extract:

```bash
for dex in $(unzip -l ${APP}_*.apk* | rg "\.dex" | awk '{print $4}'); do
    name=$(basename $dex .dex)
    unzip -o ${APP}_*.apk* $dex -d /tmp/dex_extract
    baksmali d /tmp/dex_extract/$dex -o smali/$name
done
```

## Step 6: Search for Targets

```bash
# Step 6a: Identify billing SDK
rg "revenuecat|adapty|qonversion|superwall|BillingClient|LicenseChecker" decompiled/ -g "*.java" -l

# Step 6b: Find subscription checks (by SDK found above)
rg "CustomerInfo|EntitlementInfos|getActive|getEntitlements" decompiled/ -g "*.java" -l
rg "isPro|isPremium|isSubscribed|hasPremium" decompiled/ -g "*.java" -l

# Step 6c: Find ads
rg "showAd|loadAd|interstitial|AdMob|adView|MobileAds" decompiled/ -g "*.java" -l

# Step 6d: Read target method with context
rg "public static boolean.*CustomerInfo" decompiled/ -g "*.java" -A 10
```

## Step 7: Verify in Smali

```bash
# Find target class in smali
find smali/ -name "TargetClass.smali" | head

# Read target method
rg -A 30 "\.method public static" smali/classes5/yz/u.smali
```

From smali, note: method signature, access flags, register count, invoke-virtual calls and their order.

## Step 8: Document Findings

Save in separate files per target type in `notes/`:

`notes/recon.md` — APK identification:
```markdown
# <App> Recon
- Package: com.example.app
- Version: 1.0.0
- APK Type: XAPK
- Obfuscator: R8
- DEX count: 5
- Framework: Native Java/Kotlin
```

`notes/premium-bypass.md` — subscription targets:
```markdown
# <App> — Premium Bypass

## Target 1: Entitlement check
- Class: yz/u (obfuscated)
- Method: public static boolean e(CustomerInfo)
- DEX: classes5.dex
- Purpose: Returns true if user has active entitlements
- Fingerprint strategy:
  - returnType: Z
  - accessFlags: PUBLIC, STATIC
  - parameters: CustomerInfo
  - filters: getEntitlements() → getActive()
```

Other files: `ad-removal.md`, `signature-bypass.md`, `feature-gates.md`

## Final Folder Structure

```
analysis/<app>/
├── <app>_<version>.<ext>              # Original APK (renamed)
├── <app>_<version>_decompiled.zip     # JADX output zip
├── decompiled/                        # Extracted Java sources
├── smali/                             # baksmali output (all DEX files)
│   ├── classes/
│   ├── classes2/
│   └── ...
└── notes/
    ├── recon.md                       # APK identification
    ├── premium-bypass.md              # Subscription/entitlement targets
    ├── ad-removal.md                  # Ad-related targets
    ├── signature-bypass.md            # Signature check targets
    └── feature-gates.md               # Feature flag targets
```
