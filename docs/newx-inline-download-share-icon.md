# NewX inline download button showing the share icon

## Issue

The NewX inline download button could render Twitter's native share glyph instead of the
download tray glyph after scrolling profile timelines. The affected slot was still the injected
download action: tapping it started a download, but its visual identity had become a share icon.

The report was reproduced by the user on profile media timelines. Static analysis and patch
validation used the exact supplied NewX APKs under `apks/`, especially
`twitter_12.25.0-alpha.01.apk`.

## Bytecode truth

The injected action is constructed with NewX's `TwitterShare` action type. In
12.25.0-alpha.01, the exact renderer chain is:

```text
InlineActionEntry.actionType = TwitterShare
  -> Lcom/x/ui/common/r0; maps TwitterShare to switch value 11
  -> Lcom/x/ui/common/s0; switch value 11 constructs icon lambda h(size, 2)
  -> Lcom/x/cards/impl/unified/components/appstore/h;.invoke, case 2
  -> Lcom/x/icons/a;->P9 = ic_vector_share
```

The neighboring native `Share` action is a different contract:

```text
Share -> switch value 12 -> h(size, 3) -> Q9 = ic_vector_share_android
```

The obfuscated fields shift in 12.26 (`Q9` and `R9`), but the semantic mapping is unchanged.

This disproves the earlier theory that profile timelines route the injected `TwitterShare`
entry through `ic_vector_share_android`. Hooking both icon fields was an unsafe broadening:
it modified the unrelated native `Share` branch without addressing the classification failure.

## Root causes

### 1. Capped action identity tracking evicted live compositions

NewX creates a distinct injected action object per composition. `DOWNLOAD_ACTIONS` used a
bounded identity set, most recently capped at 512 entries. A long scroll can retain more than 512
remembered composition objects. FIFO eviction therefore removed actions that Compose could still
render later, even though those action objects were live.

Once evicted, `markIconSize()` classified the injected entry as native and the renderer selected
`ic_vector_share`.

The correct lifetime rule is reachability, not insertion count. `DOWNLOAD_ACTIONS` is now an
uncapped weak identity set. Live composition objects remain classified; discarded objects are
removed through a `ReferenceQueue`.

### 2. Icon classification depended on Compose execution timing

The previous renderer contract reconstructed download identity across asynchronous/reused Compose
work using:

- a thread-local marker set at the parent entry renderer;
- a weak set of remembered icon-lambda instances;
- a constructor hook intended to copy the marker before deferred invocation;
- an epsilon added to icon size to force a changed Compose input;
- exit and exception cleanup injected into the parent renderer.

This had several timing-sensitive states: the icon lambda could be skipped, deferred until after
cleanup, or reused for another slot. A missing or stale marker made the native share icon sticky.
Each prior repair covered another scheduling shape rather than making the lambda self-describing.

## Fix: a self-contained render discriminator

The rebuilt patch carries classification in the icon lambda's existing captured float:

1. `markIconSize(action, size)` returns `-abs(size)` for a tracked download action and the
   unchanged positive size for native actions.
2. Compose observes a real input change on native/download slot flips.
3. The resolved `TwitterShare/ic_vector_share` branch calls `displayIconSize()` immediately
   after loading the captured field, restoring `abs(size)` before the value reaches layout.
4. At the final icon load, `selectIcon()` reads the original captured field and uses its raw sign
   bit to choose `ic_vector_incoming_stroke` or the native icon.

The sign is metadata only; layout receives the original positive size. The raw sign-bit check also
handles negative zero. Native Compose icon sizes cannot validly be negative, so the discriminator
does not overlap a supported native value.

This removes the thread-local marker, renderer weak set, icon-lambda constructor hook, epsilon,
renderer-exit cleanup, catch-all handler, and the alternate-share hook. The only remaining identity
state is the weak action set at the point where the injected object is created.

## Resolver and mutation contract

- Resolve `ic_vector_share` and `ic_vector_incoming_stroke` from drawable resource IDs.
- Require exactly one shared icon-lambda method containing the captured float and
  `ic_vector_share`.
- Within that method, require exactly one captured-size load in the same packed-switch block as
  `ic_vector_share`, bounded by the preceding `return-object`.
- Normalize immediately after that load.
- Select the icon immediately after the one `ic_vector_share` access.
- Do not modify `ic_vector_share_android` or other branches.
- Continue resolving action and model owners from the target APK; no version routing is used.

## Regression coverage

`InlineDownloadButtonTest` now guards the real failure modes:

- the sign-tagged value survives deferred rendering and still selects the download icon;
- native and download slots have distinct captured values but identical displayed sizes;
- action membership uses identity rather than `equals()`;
- a live action remains classified after 2,000 later registrations;
- disabling the feature preserves the native render contract.

Obsolete tests for thread-local cleanup and renderer remembrance were removed with that mechanism.

## Validation

Source baseline: commit `3da9efdbe81d02dabf5c367726069878ab476742` plus the working-tree
redesign.

Build artifact:

- MPP: `patches/build/libs/patches-3.9.0-dev.4.mpp`
- SHA-256: `d68d8aa4a0c9218fa14220bbb3c7e0d6be20e064dad7d8ca6704018dc2f9b500`
- extension: `extensions/newx/build/morphe/extensions/newx.mpe`
- SHA-256: `b7014093238c4d04cb6507b40e682b31bccff7796ed632034751f3a6f9b1e36d`

Commands passed:

```text
./gradlew :extensions:newx:test :patches:test :patches:lintNewxResolvers
./gradlew :patches:build
```

Exclusive patching reported `Applied: NewX: Inline download button` and `Saved to` for:

| Target | Output | SHA-256 |
| --- | --- | --- |
| 12.24.0-prod.02 | `/tmp/piko-inline-download-12.24.0-prod.02.apk` | `cffd036dc08680e07f1267f31d6fd37981d2e5b94f6bb05e4bf358204530e2e2` |
| 12.25.0-alpha.01 | `/tmp/piko-inline-download-12.25.0-alpha.01.apk` | `a794a03d86d3a9c9909c7b1e677dae1b50a7166c31e4179ddb428b39cb23e0d7` |
| 12.25.0-alpha.01 `.apks` bundle | `/tmp/piko-inline-download-12.25.0-alpha.01-from-apks.apk` | `12fbf4102c2d64e787f9bbb73078a023bf38c26c9ccfef250a2d0082ce0e1e25` |
| 12.25.0-prod.01 | `/tmp/piko-inline-download-12.25.0-prod.01.apk` | `a13eec5adace096e70881da514aa876ca3c163f27073395c1a4e3a910d080b93` |
| 12.26.0-alpha.03 | `/tmp/piko-inline-download-12.26.0-alpha.03.apk` | `50e055ab579254c0a3e628fafdfe0d13190645bb3a88663b494e5c4ead098574` |

`dexscope` confirmed exactly one caller of each render helper in every output. In 12.25
production and 12.26 alpha.03, final smali shows:

```text
iget <size>, <lambda>, <captured-size>
invoke-static {<size>}, InlineDownloadButton->displayIconSize(F)F
move-result <size>
... layout consumes the normalized size ...
sget-object <icon>, <ic_vector_share>
sget-object <download>, <ic_vector_incoming_stroke>
iget <raw-size>, <lambda>, <captured-size>
invoke-static {<icon>, <raw-size>, <download>}, InlineDownloadButton->selectIcon(...)
move-result-object <icon>
```

The adjacent `ic_vector_share_android` branch has no injected call.

No device was launched or controlled during this work. Final runtime confirmation still requires
normal user-driven scrolling on a patched profile timeline.
