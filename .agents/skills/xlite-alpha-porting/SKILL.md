---
name: xlite-alpha-porting
description: Port and debug X-Lite Morphe patches against obfuscated alpha builds. Use when adapting X-Lite fingerprints, model boundaries, Compose hooks, inline actions, runtime crashes, or patch-time descriptor resolution for a new X/Twitter alpha APK.
---

# X-Lite Alpha Porting

Use the exact target APK as bytecode truth. Current declared target: `com.twitter.android` `12.17.3-alpha.01`.

Also load these skills when relevant:

- `morphe-patch-development-workflow`
- `fingerprinting-guide`
- `morphe-runtime-boundaries`
- `patcher-advanced-apis`
- `build-deploy-troubleshoot`

## Search discipline

Broad searches can hang the agent process.

1. Read a known source file directly.
2. Use `rg` against one exact file for follow-up symbols.
3. If the file is unknown, search only the narrowest likely package directory.
4. Inspect the first useful result before another search.
5. Never run repository-wide `ffgrep` during APK work.
6. Never search a whole JADX tree when a class/package is already known.

Good:

```bash
rg -n 'TwitterShare|ic_vector_share' /tmp/jadx-inline-u0/u0.java
rg -l 'InlineActionEntry(' /tmp/jadx-inline-package/sources/com/x/inlineactionbar
```

Bad:

```bash
ffgrep 'InlineActionEntry' .
rg 'getMedia' x-lite/
```

## Porting loop

1. Record exact APK path/version/hash and user-visible symptom.
2. Run the patch exclusively and capture the earliest failure.
3. Resolve semantic model classes through stable `toString()` labels or preserved package contracts.
4. Assert exact match cardinality. Zero or ambiguity must fail loudly.
5. Keep extension-facing boundaries as `Object`, primitives, collections, and strings.
6. Inject release-specific casts, field reads, constructors, and invokes at patch time.
7. Build the MPP and patch the exact APK.
8. Install with `adb install -r`; clear logcat before reproducing.
9. Read logs from the current process only when possible:

```bash
PID=$(adb shell pidof com.twitter.android | tr -d '\r')
adb logcat -d -v threadtime --pid="$PID"
```

10. Capture screenshots and drive the connected phone when the user grants access.
11. Update `x-lite/alpha-12.17.3-compatibility/` after every disproved assumption and final verification.
12. Stage only relevant X-Lite files; the worktree commonly contains unrelated changes.

## Runtime boundary pattern

Extension placeholder:

```java
private static Object getCanonicalPost(Object post) {
    return null;
}
```

Patch-time replacement:

```smali
check-cast p0, <resolved contextual-post type>
iget-object p0, p0, <resolved canonical-post field>
return-object p0
```

Never put an obfuscated target descriptor in Java signatures, fields, generics, `instanceof`, class literals, or reflection constants. `compileOnly` prevents packaging stubs; it does not make a wrong runtime descriptor safe.

## Stable model discovery

Useful data-class labels from the alpha port:

- `ContextualPost(canonicalPost=`
- `CanonicalPost(id=`
- `, media=`
- `, inlineActionEntry=`
- `InlineActionEntry(actionType=`
- `MediaContentImage(mediaId=`
- `MediaContentGif(mediaId=`
- `MediaContentVideo(mediaId=`
- `MediaVariant(url=`

Use labels to resolve fields structurally. Obfuscated descriptors observed during analysis are evidence only, never production constants.

## Compose collection hooks

Inline actions are built in a mutable `ArrayList`, converted to an immutable list, then consumed by Compose.

- Hook the final conversion result, not an earlier builder.
- Reconvert the filtered Java list to the exact immutable representation.
- Existing loop exits may target the conversion instruction and skip insertions placed before it.
- Resolve the conversion by its return descriptor and assert one match.
- If the method has no free registers, clone it with additional locals while preserving parameter positions.
- For high register pressure, stage values with narrow one-register calls. `ThreadLocal` is acceptable only for synchronous same-thread render staging; clear it immediately after consumption.

## Register foot-guns

- A zero-parameter static helper has no `p0`. Use `v0`.
- Injected code cannot use `v1..v3` if the placeholder has only one register. Clone with enough additional registers first.
- `invoke-static { ... }` operands must fit four-bit registers. Use `/range` or move values into a valid contiguous range.
- `v0` and `p0` may alias in a one-register static method with one parameter. Writing a boolean into `v0` can destroy the object in `p0` and cause `VerifyError`.
- Keep `invoke-*` immediately followed by the correct `move-result*`.
- When multiple edits affect one method, account for shifted indices or mutate highest index first.
- Check the concrete instruction interface before casting: `35c` invokes are `FiveRegisterInstruction`, not `TwoRegisterInstruction`.

## Inline-action foot-guns

### Model descriptors

`InlineActionEntry`, `PostActionType`, `ContextualPost`, and `CanonicalPost` may all be obfuscated.

- Track synthetic actions as `Object` identity using weak references.
- Find an action inside an event by identity, not a target class literal.
- Resolve the exact enum field and constructor at patch time.
- Do not infer constructor parameters from `getDeclaredFields()[0]`; static `Companion` fields can appear first.

### Synthetic carrier

A synthetic download action can reuse `TwitterShare`, then intercept its event before native handling.

- Resolve the exact `TwitterShare` enum field by semantic name and type.
- Resolve the exact `(PostActionType, Long, boolean)` constructor.
- Register the created object by identity.
- Ensure native actions return `false` from the extension handler and continue untouched.

### Icon substitution

The alpha moved share icon rendering into a synthetic Compose lambda.

- Resolve `ic_vector_share` and `ic_vector_incoming_stroke` through resource IDs and `<clinit>` field writes.
- Fingerprint the relocated lambda by the resolved share icon field and captured float field.
- Do not encode state by negating icon size unless every upstream and downstream consumer is normalized. The negative marker made the action almost invisible.
- Prefer a same-thread `ThreadLocal<Boolean>` render marker: set it when rendering the identity-tracked synthetic action, consume and clear it in the icon lambda, and leave layout size unchanged.

### Media models

Runtime reflection such as `post.getMedia()` failed after accessors were obfuscated.

- Resolve contextual post → canonical post and canonical post → media at patch time.
- Keep bridges `Object`-typed.
- If media accessors are unstable, parse verified stable data-class text for image URLs and MP4 variants.
- Validate parsing against exact target models before relying on it.

## Failure map

| Symptom | Likely cause | First check |
|---|---|---|
| Fingerprint applies nowhere | Old stable descriptor/getter disappeared | Exact alpha class and `toString()` labels |
| `NoClassDefFoundError` | Old model type leaked into extension bytecode | Java signatures, fields, generics, `instanceof`, class literals |
| Startup `VerifyError` register out of range | Helper lacks locals or static helper used nonexistent `p0` | Placeholder register count and parameter count |
| `check-cast on non-reference` | Local and parameter aliases were clobbered | Register layout of injected helper |
| Repeated toast during scrolling | Exception occurs during every recomposition | Current PID logcat, first exception line |
| Action works but is invisible/tiny | Marker changed layout size | Renderer parameter mutation and icon lambda |
| Action displays Share icon | Icon renderer moved or marker not reaching lambda | Resolved share field and same-thread marker |
| Any native action crashes | Event hook references an absent model class | Extension `findActionEntry` boundary |
| Download action not added | Constructor/media bridge failed | `Failed to add...` or `Failed to check...` exception |
| Patch logs `Applied` but UI unchanged | Hook is unreachable or mutates intermediate value | Final DEX and incoming branch targets |

## Runtime verification matrix

For inline customization:

- Hide Like, Repost, Bookmark, Share independently.
- Verify inverse states (`Unfavorite`, `UndoRetweet`, remove bookmark).
- Restart and confirm settings persist.

For inline download:

- Text-only post: no synthetic action.
- Image post: normal-size download icon and successful file.
- Video/GIF post: highest MP4 variant selected.
- Multi-media post: picker and download-all.
- Native Reply/Repost/Like/Bookmark/Share remain functional.
- Fullscreen and timeline surfaces do not show a tiny/blank action.
- No repeated Morphe toast or `AndroidRuntime` exception.

## Build and test

```bash
bash .gradle-gh.sh ./gradlew :patches:build --no-daemon

java -jar morphe-desktop-1.11.0-all.jar patch \
  -p patches/build/libs/patches-3.9.0-dev.4.mpp \
  --keystore Morphe.keystore \
  --exclusive \
  -e 'X-Lite: Inline download button' \
  --force \
  -o /tmp/twitter-12.17.3-alpha.01-inline-download.apk \
  "$HOME/Downloads/twitter_12.17.3-alpha.01.apk"

adb install -r /tmp/twitter-12.17.3-alpha.01-inline-download.apk
adb shell am force-stop com.twitter.android
adb logcat -c
adb shell monkey -p com.twitter.android -c android.intent.category.LAUNCHER 1
```

Do not treat build, `Applied`, or absence of a crash as completion. Confirm the exact visual behavior and operation on the connected device.
