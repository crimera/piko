# Customize inline actions

## Status

**Ported and patch-tested on 12.17.3-alpha.01; preference behavior verification pending.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/inlineactions/CustomizeInlineActionsPatch.kt`

## Breakage

The alpha obfuscates `ContextualPost`, `CanonicalPost`, their accessors, the inline-action model, and the immutable-list implementation. The presenter constructor no longer stores the old preserved `ContextualPost`/`SubscriptionsFeatures` descriptors, so the class fingerprint failed. The renderer still builds actions from the canonical-post interface and converts an `ArrayList` into the canonical post's immutable inline-action list.

The alpha method also has no spare register at the final conversion result, so the previous three-register extension call could not be encoded safely.

## Port checklist

1. Run this patch alone against 12.17.3-alpha.01 and record the exact match/failure.
2. Use older decomps only to understand semantics; they are not compatibility targets.
3. Remove hardcoded obfuscated descriptors, method names, generated literals, or removed host resources.
4. Assert expected fingerprint cardinality.
5. Build the MPP and inspect the final DEX for a reachable mutation.
6. Install and test the feature on alpha.
7. Keep the implementation focused on the current declared alpha target.

## Findings and fix

- Resolve canonical post from stable `CanonicalPost(id=..., inlineActionEntry=...)` data-class text.
- Resolve its implemented interface and inline-action list descriptor structurally.
- Match the inline-action Compose state builder by:
  - one `Composer` parameter;
  - the canonical-post interface's zero-argument object getter;
  - `ArrayList.add(Object)`;
  - the preserved `com/x/inlineactionbar/` package boundary.
- Resolve the final immutable conversion by its return descriptor, then hook the conversion result consumed by the renderer.
- Expand the matched method by two registers while preserving parameter positions.
- Stage settings and presenter through extension-owned `ThreadLocal` values, allowing one-register invokes where the alpha method has severe register pressure.
- Parse the stable `InlineActionEntry(actionType=...)` representation in extension code, avoiding runtime references to obfuscated alpha model descriptors.

## Verification

- `:patches:build`: passes.
- Exclusive alpha patch reports `Applied: X-Lite: Customize inline actions`.
- Output: `/tmp/twitter-12.17.3-alpha.01-inline-customize.apk`.
- The shared filtered-list hook runs successfully at runtime as the dependency for the verified inline-download action.
- Manual verification of each hide-action preference remains pending.
- Older X-Lite versions are no longer declared targets.
