# 12.28 inline-download kind-override regression

- Date: 2026-09-25
- APK: `com.twitter.android` `12.28.0-prod.01`, `./apks/12.28.0-prod.01.apk`
- Report: `NewX: Inline download button` fails with
  `Expected exactly one NewX inline-action kind model constructor, found 0: []`
  at `PostModels.kt:762` (`resolveInlineActionKindOverride`).
- Source: `5e657679` plus working-tree fix.
- MPP: `patches/build/libs/patches-3.9.0-dev.4.mpp`, rebuilt from the same checkout.
- Command: `./patch-twitter.sh apks/12.28.0-prod.01.apk`
- Output: `/Users/steven/Downloads/piko-twitter-patched.apk`.

## Root cause

`5e657679` (12.29 trailing-space fix) assumed the inline-action kind model is always
`(ZZ, enum)` with an `IconOnly` constant (`Lcom/x/inlineactionbar/d1` /
`Lcom/x/inlineactionbar/f1` on 12.29). Older targets keep a boolean-only kind model
with no enum to rewrite:

- 12.27.0-prod.01, 12.28.0-alpha.01: `(Z)` (`Lcom/x/inlineactionbar/v0`).
- 12.28.0-alpha.04, 12.28.0-prod.01: `(ZZZ)` (`Lcom/x/inlineactionbar/y0`).

The resolver required exactly one enum-shaped constructor, so every pre-12.29 target
failed closed with 0 matches. Those releases worked without the override before `5e657679`.

## Fix

- Keep the 12.29 enum path unchanged (require the layout lambda, the unique
  `(ZZ, enum)` constructor, and the `IconOnly` constant).
- Detect the validated legacy shapes with `isLegacyInlineActionKindModelConstructor`
  (all-`Z` params, arity 1 or 3; the `FFFF` padding constructor never matches).
- `resolveInlineActionKindOverride` returns null on a validated legacy shape, and the
  patch skips `patchInlineActionKindOverride` only in that case. Zero recognized
  shapes, or an ambiguous enum match, still throws `PatchException`.
- The nullable resolution state uses an explicit resolved flag so a cached null is not
  re-resolved.

## Validation

- `./gradlew :patches:lintNewxResolvers --no-daemon`: PASS.
- `./gradlew :patches:test --rerun-tasks --no-daemon`: PASS (includes the new
  `inline-action legacy boolean kind model does not match the enum model` fixture).
- `./gradlew :patches:build --no-daemon`: PASS.
- Patch runs (same MPP, rebuilt from this checkout):
  - `12.27.0-prod.01`: `NewX: Inline download button` Applied, Saved successfully.
  - `12.28.0-alpha.01`: Applied, Saved successfully.
  - `12.28.0-alpha.04`: Applied, Saved successfully.
  - `12.28.0-prod.01`: Applied, Saved successfully; layout lambda contains no
    `isDownloadAction`/`IconOnly` rewrite, `createDownloadAction` bridge present.
  - `12.29.0-alpha.04`: Applied, Saved successfully; `Lcom/x/inlineactionbar/c;->invoke(...)`
    contains `isDownloadAction` followed by `sget-object ... f1->IconOnly` before the
    original kind-model constructor (fix preserved).
- Runtime spacing validation is pending user testing. No device was controlled by the agent.
