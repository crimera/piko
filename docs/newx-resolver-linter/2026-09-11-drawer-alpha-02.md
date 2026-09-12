# NewX drawer resolver drift: 12.26.0-alpha.02

- **Detected:** 2026-09-11
- **APK:** `com.twitter.android` 12.26.0-alpha.02, version code `312260202`
- **APK path:** `../twitter-analysis/apks/twitter_12.26.0-alpha.02.apk`
- **APK SHA-256:** `7bf6b883f43e8b8cb108f64de83f3aded280edc534c5bd6433c3735d687329ca`
- **Source commit:** `d748bf98692a78c748d0aabb273c33dfa50b00cc` plus the working-tree drawer fix
- **MPP:** `patches/build/libs/patches-3.9.0-dev.4.mpp`
- **MPP SHA-256:** `0ad865d198fa42ce7189f245e8b2dfe1774f6e26159c3d831e7670bae52e02be`
- **Command:** `./patch-twitter.sh ../twitter-analysis/apks/twitter_12.26.0-alpha.02.apk`
- **Failing patch:** `NewX: Customize drawer items`
- **Classification:** APK contract drift; not resolver-linter or cardinality-helper behavior

## Failure

The drawer menu renderer changed from:

```text
String, icon, Function0, Modifier, boolean, Function2, Composer, int, int
```

to:

```text
String, icon, Function0, Modifier, boolean, Function2, Function3, Composer, int, int
```

The exact `NewXDrawerMenuItemFingerprint` therefore found zero methods in
`Lcom/x/main/drawer/o;`.

## Fix and validation

Added a second semantic parameter-shape resolver for the auxiliary `Function3`
slot and retained the original shape. Both candidates share one cardinality
check. Added 12.26.0-alpha.02 as an experimental NewX target.

- `./gradlew :patches:build` — passed
- `./gradlew :patches:lintNewxResolvers` — passed
- 12.26.0-alpha.01 patch — applied successfully
- 12.26.0-alpha.02 patch — applied successfully
- Output: `/Users/steven/Downloads/piko-twitter-patched.apk`
- Output SHA-256: `e1efc9827e55e993fe7d4ff06fd4e05d76d9919a5aa0a7fb35490ce932cd4a10`
- APK v3 signature verification and zip alignment — passed
- Patched `Lcom/x/main/drawer/o;->q(...)` contains the `DrawerItemFilter` guard
