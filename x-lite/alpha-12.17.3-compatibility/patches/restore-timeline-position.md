# Restore timeline position

## Status

**Ported and runtime-tested on `12.17.3-alpha.01`.**

## Artifacts

- Package: `com.twitter.android`
- Target: `/Users/steven/Downloads/twitter_12.17.3-alpha.01.apk`
- Target SHA-256: `b7dd95a6b7ea222ecf946766dc8e971f3e892a2de6b6fdd8bf4bd660c491867e`
- MPP: `patches/build/libs/patches-3.9.0-dev.4.mpp`
- MPP SHA-256: `6becfa9271378d552ae9043c60ea9674b406abac920b8dd5f02df923cbc37965`
- Exclusive output: `/tmp/xlite-restore-position-alpha-final.apk`
- Output SHA-256: `e942eb7a1d181096f173f67489403ebc1a02daa759043ebbe0bbe8a2144c57af`

## Original failure

The old fingerprint expected a `TimelineType`-keyed store class with a `ConcurrentHashMap` instance field, a timeline getter, and a timeline/holder save method. On the alpha it matched zero methods because the state was moved into the obfuscated URT implementation.

The exact alpha path is:

- Scroll holder: `ScrollPositionHolder(firstVisibleItemIndex=..., firstVisibleItemScrollOffset=...)`.
- Timeline enum: preserved semantic enum metadata, but an obfuscated descriptor.
- Component: obfuscated `DefaultUrtTimelineComponent` implementation.
- Position map: a `ConcurrentHashMap` owned by the component's obfuscated feature/state object.
- Restore getter: the component's zero-argument method returning the holder.
- Save event: the component's one-argument event method, where the holder is inserted into the map.

## Fix

- Resolve the holder class from its verified `toString()` labels and resolve its `(int, int)` constructor at patch time.
- Fingerprint the alpha getter using the map read, holder cast, and `Restoring scrolling position for ` semantic string. Derive the timeline register from the enum-typed repository result that feeds the map key; no alpha enum descriptor is embedded in patch source.
- Expand the getter with additional locals while preserving the instance parameter register layout. If the map has no position, call the extension bridge returning `[I`, construct the exact alpha holder through the resolved constructor, and store it in the app map before the original getter continues.
- Scope the save fingerprint to the resolved component class and holder field. Inject `save(Enum, Object)` immediately before the native map write.
- Replace the extension's reflection-based store discovery with an `Object`-free stable bridge: `restore(Enum): int[]`. The extension only persists preferences and parses the verified holder `toString()` during saving; target model descriptors do not appear in its signatures.

## Final DEX evidence

The final decoded output contains the reachable mutations in `Lcom/x/urt/v;`:

- `d()Lcom/x/urt/f0;` calls `TimelineScrollPositionStore->restore(Ljava/lang/Enum;)[I`, constructs `Lcom/x/urt/f0;`, and writes it to the exact `ConcurrentHashMap` before the original map read.
- `i(Lcom/x/urt/b1;)V` calls `TimelineScrollPositionStore->save(Ljava/lang/Enum;Ljava/lang/Object;)V` immediately before the native map write.
- The extension helper itself contains no `com/x/urt/*` or alpha model descriptor.

## Verification

- Existing patch against the exact alpha: failed at the old store fingerprint with zero matches.
- `:patches:build`: PASS.
- Exclusive patch of the exact target: PASS; `Applied` and `Saved to` confirmed.
- Final DEX disassembly: PASS; both direct bridge calls and the holder/map value flow are present.
- Install: PASS with `adb install -r`.
- Runtime: PASS. On the connected device, the For You timeline was scrolled down and the app was force-stopped and relaunched. The same timeline content/position returned instead of starting at the top. The user confirmed the feature works. A transient post-loading failure was observed while clicking a timeline post, but no corresponding patch/runtime exception was reported.

The normal production patch set and retired pre-alpha targets were not part of this isolated verification run.
