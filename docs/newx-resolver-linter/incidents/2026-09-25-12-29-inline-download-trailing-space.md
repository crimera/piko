# 12.29 inline-download trailing space

- Date: 2026-09-25
- APK: `com.twitter.android` `12.29.0-alpha.04`, `./apks/12.29.0-alpha.04.apk`
- Report: enabling `NewX: Inline download button` leaves excess space at the right edge of the
  timeline inline-action row.
- Source: `5bfd0250` plus the working-tree inline-download fix.
- MPP: `patches/build/libs/patches-3.9.0-dev.4.mpp`, rebuilt from the same checkout.
- Command: `./patch-twitter.sh ./apks/12.29.0-alpha.04.apk`
- Output: `/Users/steven/Downloads/piko-twitter-patched.apk`.

## Root cause

The injected download entry intentionally uses the `TwitterShare` action carrier to reuse stable
icon and click wiring. In 12.29, the inline-action layout switch maps native `Share` and bookmark
actions to `f1.IconOnly`, but has no `TwitterShare` mapping. The injected entry therefore falls
through to `f1.Countless`. `Lcom/x/inlineactionbar/l;->a(...)` reserves different widths for
`Countless` and `IconOnly`, and the last-item correction in `Lcom/x/inlineactionbar/j;->invoke(...)`
is applied only to `IconOnly`. This produces the visible trailing-space mismatch.

## Fix

- Keep the `TwitterShare` carrier and existing icon/click behavior.
- Resolve the shared inline-action layout lambda from the stable entry action-type field read.
- Resolve its unique `(Z, Z, enum)` kind-model constructor and the enum's `IconOnly` constant.
- Immediately before constructing the kind model, call the extension's identity-based
  `isDownloadAction(Object)` predicate. Only the registered injected object has its kind register
  replaced with `IconOnly`; native actions keep their original classification.
- Registers, constructor, enum type, and field are derived from exact bytecode. Ambiguous or missing
  shapes fail with `PatchException`.

## Validation

- `./gradlew :patches:build --no-daemon`: PASS.
- `./gradlew :patches:lintNewxResolvers --no-daemon`: PASS.
- NewX extension tests: 43 tests, zero failures.
- Exact 12.29 patch run: all 43 selected patches applied; output saved successfully.
- Final `Lcom/x/inlineactionbar/c;->invoke(...)` contains the identity check followed by
  `sget-object ... f1->IconOnly` before the original kind-model constructor.
- `dexscope verify-method` is INCONCLUSIVE because reference/field assignability is not modeled; it
  reports warnings rather than INVALID bytecode.
- Output manifest remains `12.29.0-alpha.04` / version code `312290204`; ZIP integrity passes.
- Runtime spacing validation is pending user testing. No device was controlled by the agent.
