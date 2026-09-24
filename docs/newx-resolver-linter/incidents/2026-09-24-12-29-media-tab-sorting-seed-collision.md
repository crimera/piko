# NewX 12.29 media-tab seed collision with profile post sorting

- Date: 2026-09-24
- Reporter/session: 12.29 port session (surfaced after the server error logging repair)
- APK package/version/build type: `com.twitter.android` 12.29.0-alpha.04 alpha
- APK path and checksum: `apks/12.29.0-alpha.04.apk` / `767a55b4897b3d88391d4c53102f502f3f109ba7324b7158e53b2b1ce79fc7b2`
- Additional validated targets:
  - `apks/12.28.0-prod.01.apk` / `a938b4373d2be7c8ed94d6595bf2d1299df2b2cef96b0677b4dfa22e11b32804`
  - `apks/12.27.0-prod.01.apk` / `7f6226675d48549dfbe50549205dd71e296ba432a279ab43928a326a926aa3fc`
- Source commit: `3f95e2415086094160ec05b97d85955f736beafe` plus this working-tree repair
- MPP path and checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` (working-tree build)
- Command: `./patch-twitter.sh --continue-on-error`, plus focused media+sorting probes
- Failing patch: `NewX: Set default profile post sorting`; root cause in `NewX: Set default media tab`
- Severity: high (both a patch abort and a latent runtime `ClassCastException` in the media-tab patch)
- Cause: resolver logic — `DefaultMediaTabPatch` selected the `MutableStateFlow` seed by register
  identity, and 12.29 reuses the parameter register for the post-sorting seed
- Confidence: high; both injection points verified in the patched bytecode of all three versions

## Symptom

`NewX: Set default profile post sorting` aborted with:

```text
app.morphe.patcher.patch.PatchException: Expected exactly one profile post sorting state initializer, found 0: []
    at ...ResolverCardinalityKt.requireExactlyOne(ResolverCardinality.kt:15)
```

The sorting patch applies on its own. It only fails when `NewX: Set default media tab` runs first.

## Root cause

Both patches target the combined profile timeline constructor `Lcom/x/profile/timeline/d;-><init>`.
The constructor declares two `MutableStateFlow` states: the selected sub-tab and the latest/popular
post sorting state.

Through 12.28 the seeds are distinct registers:

| Release | Sub-tab seed | Sort seed |
| --- | --- | --- |
| 12.27.0-prod.01 | index 33, register `p2` | index 37, `p4 = Boolean.FALSE` |
| 12.28.0-prod.01 | index 33, register `p2` | index 37, `p4 = Boolean.FALSE` |
| 12.29.0-alpha.04 | index 61, register `p1` (computed) | index 74, `p2 = Boolean.FALSE` |

`DefaultMediaTabPatch` computed `initialSubTabRegister = registerCount - parameterTypes.size + 1`
(second declared parameter) and matched any `MutableStateFlow` seed using that register. On 12.29,
the compiler computes the sub-tab seed into `p1` and reuses `p2` for `Boolean.FALSE` immediately
before the sorting seed. The register heuristic therefore matched the **sorting** seed and injected:

```smali
sget-object p2, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
invoke-static {p2}, Lapp/.../MediaTabResolver;->getEnumDefault(Ljava/lang/Object;)Ljava/lang/Object;
move-result-object p2
check-cast p2, Lcom/x/profile/s;      # Boolean.FALSE cast to the profile tab type
invoke-static {p2}, Lkotlinx/coroutines/flow/z;->c(Ljava/lang/Object;)Lkotlinx/coroutines/flow/w2;
```

This both corrupted the sorting state (latent `ClassCastException`) and split the sorting patch's
required `Boolean.FALSE` / flow-seed adjacency, so its fingerprint matched 0 classes.

## Fix

`DefaultMediaTabPatch` now identifies the sub-tab seed semantically instead of by register:

- `isInitialSubTabSeed` selects a `MutableStateFlow` (`flow/**` one-Object-argument factory) seed
  that is **not** immediately preceded by `sget-object Boolean.FALSE`.
- The fingerprint and the `execute` seed selection both use it, and `requireExactlyOne` keeps the
  fail-closed boundary.
- The obsolete `parameterRegisterFloor` guard was removed; it was always false under the old
  register-match invariant and produced a false positive once the seed register changed.

The sorting patch is unchanged. Its `Boolean.FALSE`/flow-seed adjacency is preserved on 12.29.

## Evidence

Patched 12.29 `Lcom/x/profile/timeline/d;-><init>` (full run):

```smali
[007a] (61) invoke-static p1, Lapp/.../MediaTabResolver;->getEnumDefault(...)   # sub-tab seed
[007d] (62) move-result-object p1
[007e] (63) check-cast p1, Lcom/x/profile/s;
...
[0096] (76) sget-object p2, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
[0098] (77) invoke-static , Lapp/.../ProfilePostSortingResolver;->getDefault()Ljava/lang/Boolean;
[009b] (78) move-result-object p2
[009c] (79) invoke-static p2, Lkotlinx/coroutines/flow/z;->c(...)              # sorting seed
```

`dexscope verify-method` on the patched constructor exits 0 with no errors. 12.27 and 12.28 still
inject the media-tab resolver at the sub-tab seed and the sorting resolver at the sort seed.

## Regression fixture

`media tab sub-tab seed ignores the Boolean FALSE post-sorting seed` in the existing
`NewXResolverLinterTest` suite asserts that `isInitialSubTabSeed` accepts the non-Boolean flow seed
and rejects the `Boolean.FALSE` seed.

## Validation

- `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon` — passed.
- 12.29.0-alpha.04 — `./patch-twitter.sh --continue-on-error` completes with no failures and
  `Saved to`; media-tab and sorting hooks verified in `d-><init>`.
- 12.28.0-prod.01 and 12.27.0-prod.01 — full runs complete with no failures and `Saved to`.
