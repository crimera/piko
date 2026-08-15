# X-Lite `makeFieldsPublic` audit

## Scope

- APK: `com.twitter.android` `12.17.3-alpha.01`
- Input: `/Volumes/realme/Dev/piko/analysis/twitter-12.17.3-alpha.01/twitter_12.17.3-alpha.01.apk`
- MPP: `patches/build/libs/patches-3.9.0-dev.4.mpp`
- Patcher: Morphe Desktop `1.11.0`
- Final APK: `/tmp/piko-xlite-plan-f-full.apk`

## Finding

The exact target already declares every model field used by the generated extension bridges as
`public final`. The old `makeFieldsPublic`/local `makePublic` calls therefore rewrote no effective
access boundary on this APK, but still gave the patch a broad mutation capability.

The mutating helper was replaced with `requirePublicFields`. It resolves each exact field reference,
asserts that the target declaration is public, and fails the patch if a future target requires a
visibility mutation. No access flags are changed.

`postResultField` was removed from the shared timeline-adapter visibility group. It is consumed only
by the AI-disclosure bridge and is now checked by `HideAiGeneratedPostsPatch` at its own feature
boundary.

## Call-site inventory

| Former mutation site | Former references | Current check | Scope |
|---|---:|---:|---|
| `models/TimelineModels.kt` | 15 | 14 | Timeline filter bridges; `postResultField` moved to the AI feature |
| `timeline/HideAiGeneratedPostsPatch.kt` | 4 | 5 | AI disclosure bridge, including the moved `postResultField` |
| `misc/inlineactions/CustomizeInlineActionsPatch.kt` | 1 | 1 | Inline action enum-name bridge |
| `misc/inlineactions/InlineDownloadButtonPatch.kt` | 5 | 5 | Presenter, canonical, repost, and media bridges |

The old code attempted 25 field writes (24 unique field references; contextual canonical-post was
shared by two features). The new code performs zero field writes and checks 25 bridge inputs at the
feature boundary.

## Access-flag comparison

The following exact target declarations were compared with the corresponding final-APK smali:

- Timeline adapter: `w0->{c,e,g}`, `v0->a`, `timelinemodule/k->a`,
  `timelines/items/n0->{a..g}`, `timelines/items/q0->{a,b}`.
- Inline actions: `l4->a` and `inlineactionbar/n0->b`.
- Inline download: `p1->{b,d}`, `h8->a`, and `s0->d`.
- AI disclosure: `a1->{b,c}`, `s0->G`, plus the shared `w0->a` and `p1->b` reads.

Every declaration remained `public final` before and after patching. **Fields widened: 0.**

## Final-Dex bridge checks

The final APK contains the expected direct field bridges:

- `XLiteTimelineFilter.smali`: timeline module/post/vertical bridges and the AI disclosure chain.
- `InlineActionFilter.smali`: direct inline-action enum field read.
- `InlineDownloadButton.smali`: direct presenter, canonical-post, repost, and media field reads.

The direct reads return before the original placeholder returns, so no bridge relies on the
placeholder implementation.

## Validation

- `./gradlew :extensions:xlite:test --no-daemon`: PASS.
- `./gradlew :patches:test :patches:build --no-daemon`: PASS.
- Exact APK exclusive production set: 24/24 patches applied; output saved to
  `/tmp/piko-xlite-plan-f-full.apk`.
- Final DEX decoded with Apktool and the bridge bodies/access flags inspected.
- Installed with the exact APK's `arm64_v8a`, `en`, and `xxhdpi` split APKs using
  `adb install-multiple -r`: PASS.
- App launched on the connected device. Current-process logcat contained no
  `VerifyError`, `IllegalAccessError`, `NoClassDefFoundError`, `NoSuchMethodError`, or fatal
  exception.

## Decision

Keep the non-mutating `requirePublicFields` guard. Do not reintroduce a visibility rewrite for this
alpha. If a future supported APK makes one of these fields non-public, the patch must stop and receive
a new exact-bytecode audit rather than silently widening unrelated fields.
