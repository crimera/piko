---
name: morphe-faq-and-app-notes
description: Morphe FAQ, supported apps, version info, and app-specific notes. Use when answering questions about Morphe capabilities, supported versions, or app-specific issues.
---

# Morphe FAQ & App Notes

## Supported Apps

| App | Package | Versions | APK Type |
|-----|---------|----------|----------|
| YouTube | com.google.android.youtube | 20.21.37 – 21.13.163 | APK_REQUIRED |
| YouTube Music | com.google.android.apps.youtube.music | 7.29.52 – 9.12.51 | APK |
| Reddit | com.reddit.frontpage | 2025.48.0 – 2026.12.0 | APKM |

## App-Specific Notes

- YouTube/Music: Requires MicroG-RE for Google account login on non-root
- Reddit: Uses APKM format (split APKs), needs signature spoofing
- Google login/Drive fails on all patched apps due to signature mismatch
- Server-validated features can't be bypassed (AI credits, cloud publishing)

## Key URLs

- Website: https://morphe.software
- MicroG: https://morphe.software/microg
- Patches issues: https://github.com/MorpheApp/morphe-patches/issues

## Known Limitations

- Google login fails after re-signing (expected behavior)
- Split APK apps must use XAPK/APKM format
- Pre-patched APKs from third parties are unsafe
- Android Auto requires enabling unknown sources in developer settings
