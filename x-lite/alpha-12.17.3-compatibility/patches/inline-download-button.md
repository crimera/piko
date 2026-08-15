# Inline download button

## Status

**Runtime-tested on 12.17.3-alpha.01: working.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/inlineactions/InlineDownloadButtonPatch.kt`

## Breakage

The alpha obfuscates `InlineActionEntry` and `PostActionType`, removes their old public getter calls from the renderer/event path, and changes the renderer parameter shape. The previous fingerprint therefore found zero renderers. The old TwitterShare icon renderer fingerprint also disappeared because icon selection moved into a different shared Compose renderer.

## Port checklist

1. Run this patch alone against 12.17.3-alpha.01 and record the exact match/failure.
2. Use older decomps only to understand semantics; they are not compatibility targets.
3. Remove hardcoded obfuscated descriptors, method names, generated literals, or removed host resources.
4. Assert expected fingerprint cardinality.
5. Build the MPP and inspect the final DEX for a reachable mutation.
6. Install and test the feature on alpha.
7. Keep the implementation focused on the current declared alpha target.

## Findings and fix

- Resolve `InlineActionEntry` from stable `InlineActionEntry(actionType=..., isEnabled=...)` data-class text.
- Resolve `PostActionType` from the first object field read by that `toString()` implementation.
- Match the alpha entry renderer by its semantic parameter shape and direct action-type/enabled field reads.
- Change `markIconSize` to an `Object` boundary so the extension does not name the alpha entry descriptor.
- Match the event handler in the already-resolved inline presenter through direct action-type field access followed by `Enum.ordinal()`.
- Preserve identity-based interception of the synthetic carrier action before native event handling.
- Resolve the relocated TwitterShare Compose icon lambda through the share resource field and captured icon-size field.
- Mark the currently rendered synthetic action with an extension-owned `ThreadLocal<Boolean>` and substitute `ic_vector_incoming_stroke` without changing layout size.

## Verification

- `:patches:build`: passes.
- Exclusive alpha patch reports `Applied: X-Lite: Inline download button`.
- Output: `/tmp/twitter-12.17.3-alpha.01-inline-download.apk`.
- First runtime test crashed on every native inline action with `NoClassDefFoundError: com.x.models.InlineActionEntry`; it also added no download action.
- Removed all extension references to `InlineActionEntry`, `PostActionType`, and the old `ContextualPost` class name.
- Synthetic entries are constructed through a patch-time-resolved enum field and exact constructor, tracked as `Object`, and discovered in events by identity rather than type.
- The presenter post class name is resolved from the matched presenter constructor and injected into an `Object`-boundary helper at patch time.
- Rebuilt exclusive alpha APK after the runtime-boundary correction. The first build used `p0` in a zero-parameter static helper, causing a startup `VerifyError`; corrected the injected bridge to use its sole local register `v0`.
- Runtime logs then showed `NoSuchMethodException: com.x.models.p1.getMedia()` during every recomposition. Resolved contextual-post → canonical-post and canonical-post → media fields at patch time and injected direct `Object` bridge reads.
- Media item extraction now parses stable `MediaContentImage`, `MediaContentGif`, `MediaContentVideo`, and `MediaVariant` data-class text instead of reflecting over obfuscated getters.
- Post ID and author fallback extraction also use canonical-post semantic text, avoiding alpha accessor names.
- Runtime then failed to construct the carrier action because reflection selected the static `Companion` field type instead of `PostActionType`. Replaced reflective construction with a patch-time-resolved direct `TwitterShare` enum field read and exact inline-entry constructor invocation.
- The first direct-construction bridge exceeded the placeholder helper's one local register and failed verification. The patch now clones that helper with four local registers before injecting `new-instance`, enum read, and constructor arguments.
- Corrected output: `/tmp/twitter-12.17.3-alpha.01-inline-download.apk`.
- User and agent runtime-tested the corrected alpha build:
  - synthetic action is visible at normal size with the download/incoming icon;
  - native inline actions remain usable;
  - tapping the synthetic action downloads media successfully;
  - no repeated media-check toast or runtime crash remains.
- Older X-Lite versions are no longer declared targets.

## Repost follow-up

- Structured reposts can keep the downloadable media in `ContextualPost.rePostedPost.canonicalPost` while the wrapper canonical post has no usable media. The previous media bridge inspected only the wrapper and therefore omitted the inline action.
- The patch now resolves `ContextualPost` → `RePostedPost` → canonical post at patch time and selects the first media list containing downloadable image/video items.
- Exact alpha smali confirms `p1.d:Lcom/x/models/h8;`, `h8.a:Lcom/x/models/s0;`, and `s0.d:Lkotlinx/collections/immutable/b;`.
- Unit coverage exercises empty and unusable wrapper media with downloadable repost media. Exclusive patch output: `/tmp/twitter-12.17.3-alpha.01-inline-download-repost.apk`.
- Device verification for the two reported posts remains pending because no ADB device is connected.
