---
name: morphe-runtime-boundaries
description: Design Morphe extension boundaries for obfuscated Android apps using compileOnly model stubs, patch-time descriptor resolution, direct smali bridges, and final DEX verification. Use when avoiding runtime reflection, fixing NoClassDefFoundError or VerifyError issues, or optimizing patch-time resolution.
---

# Morphe Runtime Boundaries

Use this skill when extension code needs app model types but some app descriptors or method signatures change between releases.

## Default architecture

```text
compileOnly stubs for verified stable app models
        ↓ compile-time types only
extension runtime code ── direct calls to stable app ABI
        ↑
patch-time adapter resolves unstable descriptors and injects direct smali calls
```

- Stubs are compile-time contracts, not runtime implementations.
- `compileOnly` keeps stub classes out of the extension DEX.
- Resolve release-specific descriptors once while patching.
- Keep runtime bridges typed as `Object`, primitives, or verified stable models.
- Prefer direct invokes in final DEX; do not use runtime reflection for app internals.

## Classify every reference

A model is stable only when its descriptor, hierarchy, and every used method signature have been verified across the declared targets. A readable class name alone is not proof.

| Reference | Strategy |
|---|---|
| Stable class and stable used signatures | Keep a minimal `compileOnly` stub and use direct typed calls |
| Stable class but unstable return/parameter descriptor | Use a safe bridge signature and resolve the exact method reference at patch time |
| Obfuscated or unknown owner class | Use a fingerprint or a resolved owner from another match |
| Optional/release-only type | Do not put it in an extension signature; cross the boundary through a safe type |

A stable owner with an unstable method return is still unstable at the method boundary. Do not add a stub whose method signature names a release-specific type.

## Patch-time resolution and cost

Morphe's class store is descriptor-indexed:

- `mutableClassDefBy(descriptor)` performs an average `O(1)` class-map lookup.
- Searching `classDef.methods` is linear in that one class only; assert the expected cardinality.
- A fingerprint with `definingClass` or a class fingerprint follows the same indexed class lookup before matching methods.
- String/opcode indexes help locate candidate classes when no stable descriptor exists, but an unscoped fingerprint may still inspect many classes.
- There are no useful numeric method indexes. Do not hardcode “method 6”; resolve by verified descriptor/signature or fingerprint.

Use direct indexed lookup when the owner descriptor is stable and the method name/signature has been verified. Use fingerprints when the owner is unknown, obfuscated, or must be identified by bytecode semantics.

```kotlin
val owner = context.mutableClassDefBy(STABLE_OWNER_DESCRIPTOR)
val getters = owner.methods.filter { method ->
    method.name == VERIFIED_NAME && method.parameterTypes.isEmpty()
}
if (getters.size != 1) {
    throw PatchException("Expected one getter, found ${getters.size}: ${getters.joinToString()}")
}
val getter = getters.single()
```

## Safe bridge pattern

Keep the extension-facing method stable:

```java
private static Object readReleaseValue(StablePost post) {
    return null; // replaced by patch-time smali
}
```

Resolve the real method reference from the target APK, then inject the direct call:

```smali
invoke-virtual {p0}, Lcom/x/models/timelines/items/UrtTimelinePost;->getReleaseValue()Lcom/x/models/StableModel;
move-result-object p0
return-object p0
```

The extension can inspect the result through stable Java contracts or `Object` checks. If a release-specific enum is returned, use `Enum.name()` and stable semantic values rather than naming the enum class in Java.

This prevents the extension constant pool from directly requiring a missing/renamed release type while retaining direct runtime dispatch after patching.

## X-Lite case study

The hide-AI-posts patch demonstrates the boundary:

- General URT/timeline models remain `compileOnly` stubs because their descriptors and used ABI are stable.
- Disclosure-specific stubs were removed because the disclosure-source enum descriptor changes between releases.
- The patch resolves the stable disclosure/post owners and exact getter return descriptor locally, then injects direct calls into `Object`-returning bridge helpers.
- Runtime checks the returned value as `Enum<?>` and uses `name()`; it never references the changing enum class.
- The obfuscated timeline-success constructor still uses one global fingerprint because it has no safe stable descriptor; everything else uses indexed owner lookup.

This is the intended split: fingerprint genuinely unknown owners, index known owners, and keep unstable types on the patch-time side of the boundary.

## Runtime rules

- Do not package model stubs; verify the extension artifact contains extension classes but not `com/x/models/*` stubs.
- Do not reference unstable types in public, private, field, generic, or method signatures merely because a path is rarely executed. Android verification can resolve them before that path runs.
- Do not use reflection to find release methods at runtime. Reflection is slower, harder to verify, and fails silently or differently across obfuscation changes.
- Keep bridge methods small and patch them before their placeholder return executes.
- Keep runtime parsing/normalization outside the patcher; patch-time code should only resolve and inject the release-specific reference.

## Verification checklist

```text
[ ] Stable stubs are compileOnly dependencies
[ ] Stub inventory contains only verified stable model contracts
[ ] No release-specific type appears in extension signatures
[ ] Patch-time owner lookup uses a descriptor index or justified fingerprint
[ ] Method cardinality and return/parameter descriptors are asserted
[ ] Injected invoke is paired with move-result*
[ ] Extension artifact contains no model stub classes
[ ] Final DEX contains the resolved direct invoke
[ ] Final DEX contains no unresolved release-specific extension reference
[ ] Runtime path uses direct calls, not reflection
[ ] Exact target and at least one additional declared target were patched
[ ] Original behavior and control paths were manually tested
```

## Failure diagnosis

| Symptom | Likely boundary error |
|---|---|
| `NoClassDefFoundError` during extension load | An unstable type leaked into an extension signature or stub |
| `NoSuchMethodError`/`VerifyError` | A compiled stub signature does not match the target method descriptor |
| Patch is slow and scans most classes | A stable owner was searched globally instead of using indexed lookup |
| Patch applies but runtime does nothing | Placeholder bridge was not injected, or the injected call is unreachable |
| Runtime works on one release only | A release-specific owner/signature or enum type was hardcoded |
| Stub classes appear in final APK | The stub dependency was packaged instead of `compileOnly` |

When a boundary fails, inspect the compiled extension constant pool and final DEX before adding another fingerprint or reflection fallback.
