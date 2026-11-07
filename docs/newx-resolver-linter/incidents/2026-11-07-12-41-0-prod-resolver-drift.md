# 12.41.0 prod: five resolver failures from contract drift

- Date: 2026-11-07
- APK: `X_12.41.0-prod.01.apks` (base plus arm64-v8a, en and xxxhdpi splits), `com.twitter.android`
  `12.41.0-prod.01`
- Control APK: `com.twitter.android` `12.40.1-prod.01` (base APK)
- Fix: `86fb221b feat(newx): support 12.41.0-prod.01`. Regression fixtures were added afterwards.
- MPP: `patches/build/libs/patches-<gradle.properties version>.mpp`, rebuilt from that checkout
- Morphe CLI: `morphe-desktop-1.19.0-all.jar`
- Command: `./patch-twitter.sh <APK> --continue-on-error`. The Morphe manager stops at the first failure,
  so on a device it reports only `NewX: Gallery profile Photos tab`:
  `Expected exactly one NewX full viewer destination, found 0`.

Baseline on 12.41.0 before the fix: 45 Applied, 5 failed. All five failures are APK contract drift.
None came from the linter or the cardinality helpers.

## 1. NewX: Gallery profile Photos tab

The full-viewer route constructor gained a boolean before the default-flags mask:
`(List, I, Route, String, String, String, I)V` became `(List, I, Route, String, String, String, Z, I)V`.
The host builds the viewer at two sites:

| Site | `Z` argument | Flags on 12.41.0 | Flags on 12.40.1 |
|---|---|---|---|
| Plain media tap | `const 0` | `0x1a0` (bit `0x100` defaults the `Z`) | `0xa0` |
| Card | moved in | `0xa0` | `0xa0` |

Fix:
- Match the List/Int/route/three-String prefix plus the trailing mask.
- Default auxiliary int-like parameters to 0.
- Size the emitted register run from the descriptor.
- Read the flags only from the call site whose auxiliary arguments are constant 0.

## 2. NewX: Hide compose button

The FAB host's Compose `$changed` int moved from the first parameter to the last. Before:
`(I, Composer, Modifier, List, …)`. After: `(…, List, Model, Modifier, Composer, I)`.

Fix: the positional anchor is dropped. Selection stays exact-one over the Compose shape and the menu
contract.

Fixture: `post FAB host resolves its renderer with the compose changed int first or last`.

## 3. NewX: Redirect downloads to chosen folder

At the video-tab null-check merge, two things changed:
- The null arm became `move-object post, vNull`. Before it was `const/4 post, 0`. `vNull` is a
  hoisted `const/4 vNull, 0x0`.
- An `iget-object` of the downloader field now sits between the goto-targeted merge and the
  metadata call.

Fix:
- Derive the merge from the goto target, walking back over a straight-line prelude.
- The prelude must not write the post register.
- Accept a moved null only when it is proven literal 0 on every path.

Fixtures:
- `video-tab post tuple follows a merge prelude and a hoisted null register`
- `video-tab post tuple rejects a prelude that writes the post or a null source that may be non-zero`

## 4. NewX: Restore Twitter branding (via Restore Twitter splash transition)

| Method | 12.40.1 | 12.41.0 |
|---|---|---|
| Launch reveal renderer | `(holder, I)V` | `(holder, I, completion)V` |
| Normal-dismiss fallback | `(holder)V` | `(holder, Function0)V` |

The 12.38.x renderer is `(I, holder)`; see `2026-10-30-launch-reveal-parameter-order.md`.

Fix:
- Accept auxiliary object parameters on the renderer and on the fallback.
- Force the app's own normal-dismiss arm by zeroing the `Build.VERSION.SDK_INT` value at its
  `if-lt` gate. The old approach re-created the fallback's arguments.

Fixtures:
- `launch reveal renderer accepts the 12_41 trailing callback and rejects other shapes`
- `launch reveal dismiss fallback leads with the callback holder and takes no Int`

## 5. NewX: Unlock downloads

R8 folded the download selector (29) into the packed-switch default arm. The payload keys are 0..28.
An absent key now selects the fall-through arm.

Fixture: `defaultArm`, in `download capabilities require…`.

## Validation

- Gate `:patches:lintNewxResolvers :patches:test :patches:build :extensions:newx:testDebugUnitTest`:
  passed.
- 12.41.0-prod.01 split bundle, `--continue-on-error`: 50/50 Applied.
- 12.40.1-prod.01, `--continue-on-error`: 50/50 Applied.
- Device (Xiaomi 2304FPN6DC, arm64): an equivalent 12.41.0 build was installed. It was patched
  from an independent implementation of the same five fixes. In that build:
  - the splash transition, Gallery photo tap, compose-button toggle and video download worked;
  - the crash buffer stayed empty, with no `VerifyError`.
- The `86fb221b` output itself was not run on a device.

## Known limits

- No 12.38.1 or 12.39.0 APK was available locally. The `(I, holder)` renderer order is still
  accepted. An SDK gate of a different shape fails closed at patch time.
