# NewX inline download button performance review

## Verdict

This document records the original performance review. The render-path findings have since been
addressed: logging is gated and lazy, media presence uses a cheap predicate only when needed,
action membership is an identity-based weak lookup, and the icon path no longer uses temporal
renderer state. See `docs/newx-inline-download-share-icon.md` for the current render contract.

## Findings

### Resolved: unconditional logging on the scroll/render path

The instrumentation in
`extensions/newx/src/main/java/app/morphe/extension/newx/misc/InlineDownloadButton.java` now
uses `NewXLogger.printInfo(() -> ...)`.

This gates output through `NewXLogger.isLoggingEnabled()` and keeps string concatenation lazy.
The calls remain on the render path, so the logger setting lookup and diagnostic lambda creation
are still avoidable overhead when profiling or shipping. Remove the instrumentation entirely for
release, or retain it only when explicitly diagnosing this feature.

The calls are made from:

- `addAction()` when an action list is skipped or extended;
- `markIconSize()` for every inline action render;
- `selectIcon()` for the share/download icon render.

The previous direct `Log.d` implementation produced allocations and log writes on the UI thread
during scrolling; it should not be reintroduced.

### P1: expensive media extraction during action-list construction

`addAction()` at `InlineDownloadButton.java:133-153` calls `hasMedia()` before adding the button.
The call chain is:

```text
hasMedia()
  -> mediaFor()
  -> selectMedia()
  -> downloadItems()
  -> media.toString(), ToStringParser, Uri parsing, URL rebuilding,
     DownloadItem/Variant allocation, and video-variant scanning
```

`hasMedia()` only needs a boolean, but `downloadItem()` also constructs thumbnail URLs and full
download metadata. For every media-bearing post this creates unnecessary strings, URI objects,
temporary model objects, and parser work on the Compose/UI path. Video `toString()` values can be
large because they contain variant lists.

The generated `DownloadItem` objects are discarded by `hasMedia()` and the media is parsed again
when the user actually clicks download.

Use a cheap downloadable-media predicate for the render path, or cache the result per stable post
identity. Do not build thumbnail URLs until the picker is opened.

### P1: media is parsed even when hiding media-less posts is disabled

The current code does this unconditionally:

```java
Object post = postFor(presenter);
boolean hasMedia = hasMedia(post);
if (hideWhenNoMedia() && !hasMedia) return actions;
```

When `newx.content.inline_download_hide_no_media` is false, the media scan has no functional use.
Only resolve the post and inspect media when that setting is enabled. Also consider checking for
an existing download action before doing media extraction.

### Resolved: linear action membership scans for every rendered action

`markIconSize()` calls `isDownloadAction()`. The reviewed implementation scanned a synchronized
bounded list linearly. It now uses an uncapped identity-based weak set backed by
`ConcurrentHashMap`; discarded actions are drained through a `ReferenceQueue`.

Lookup is now expected constant time and does not take a collection monitor. Identity semantics
remain required because NewX action models can be value-equal.

### Resolved: render overhead when the feature is disabled

The bytecode hooks in
`patches/src/main/kotlin/app/crimera/patches/newx/misc/inlineactions/InlineDownloadButtonPatch.kt`
injects `markIconSize()` at the beginning of the generic inline-action renderer. It returns the
original size immediately when disabled. The former exit cleanup and catch-all handler were
removed. The icon lambda's `selectIcon()` also returns the native icon immediately when disabled.

The patch-side hook remains installed because settings can change at runtime, but disabled calls
perform no membership lookup or renderer-state mutation.

### Resolved: extra Compose/marker work

The thread-local marker, remembered-renderer weak set, constructor hook, epsilon, and broad
catch-all cleanup handler were removed. Download classification is carried by the sign of the
icon lambda's captured size. The share branch restores the positive value before layout, so no
visual size adjustment remains.

## Things that are not on the scroll path

- `DownloadManager` enqueue/finalization and MediaStore queries occur after a click.
- Picker thumbnail network loads use background executors and are not initiated by scrolling.
- `DOWNLOAD_ACTIONS` contains only weak identity keys and drains cleared references during set
  operations; it has no cap that can evict a still-live composition.

## APK/bytecode validation

Source review was performed against commit `f94e38c52418e06d607fdd6382caf027ae0e6760` plus the
current uncommitted logging changes.

A clean build was run with:

```text
./gradlew clean :extensions:newx:assembleRelease :patches:build --no-build-cache
./gradlew :extensions:newx:testDebugUnitTest :patches:test :patches:lintNewxResolvers --no-build-cache
```

Both completed successfully.

The current MPP was applied successfully to:

- 12.24.0-prod.02;
- 12.25.0-alpha.01;
- 12.25.0-prod.01;
- 12.26.0-alpha.01;
- 12.26.0-alpha.02;
- 12.26.0-alpha.03;
- the 12.25.0-alpha.01 `.apks` bundle.

`dexscope` confirmed one action-list hook, one generic renderer hook, one icon hook, and one
event hook per target. In the 12.26.0-alpha.03 output, the injected renderer changes were small
in bytecode size (approximately +9 instructions in the generic renderer, +5 in the icon lambda,
and +12 in the action builder); the cost of the called extension methods is the bigger concern.

12.19.x APKs and the GlobeOne bundle are outside the declared NewX target set and were not treated
as supported targets.

## Original recommended fix order

1. Remove the diagnostic instrumentation for release profiling, or keep it routed through
   `NewXLogger` with lazy messages.
2. Stop building full `DownloadItem`/thumbnail data for `hasMedia()`.
3. Skip media inspection when `hide_no_media` is disabled; check duplicate actions earlier.
4. Replace the 512-entry linear action scan and reduce synchronization on render hooks. (Done.)
5. Reconsider whether hooks should run while the feature setting is disabled. (Reduced to early
   returns; hooks remain installed for runtime setting changes.)
6. Profile a no-debug release artifact on a real device with frame timing/Perfetto before release.

No device interaction or on-device frame trace was performed during this review.
