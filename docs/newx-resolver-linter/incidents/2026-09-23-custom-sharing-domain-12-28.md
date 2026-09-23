# NewX 12.28 custom sharing domain no-op (branch-target bypass)

- Date: 2026-09-23
- Reporter/session: user issue crimera/piko-newx#69; pi investigation session
- APK package/version/build type: `com.twitter.android` 12.28.0-prod.01 production
- APK path and checksum: `apks/12.28.0-prod.01.apk` / `a938b4373d2be7c8ed94d6595bf2d1299df2b2cef96b0677b4dfa22e11b32804`
- Additional validated targets:
  - `apks/12.27.0-prod.01.apk` / `7f6226675d48549dfbe50549205dd71e296ba432a279ab43928a326a926aa3fc`
  - `apks/12.28.0-alpha.04.apk` / `2faeabfc103f2104d7bfb698078680f06dadbc0afdaefefbf934dfa191aa978b`
- Source commit: `618c2af3c2c98f036c783c6a591b1700dbd05d8a` plus working-tree mutation fix
- MPP path and checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` / `6bc759616ea4f08e3b0ea8e37cbe5eca8f2384a2afb866936f9bfb6c181ab205`
- Command: `./patch-twitter.sh apks/12.28.0-prod.01.apk`
- Failing patch: none (patch applied); the failure is runtime behavior
- Severity: high (feature no-op)
- Cause: mutation insertion point — injected rewrite sat before a control-flow
  label, so every real URL branch jumped past it in the final DEX
- Confidence: high; reachability verified in final-DEX smali on 12.27 and 12.28

## Symptom

With `NewX: Custom sharing domain` set to `fxtwitter.com`, share-sheet copy link
and share-via-app still produced `https://x.com/...` on 12.28.0-prod.01. The
patch reported `Applied` and the setting persisted, so the failure was a
runtime no-op, not a patch-time failure.

## Root cause

`hookShareSheetUrlConstructor` rewrote the share-sheet URL by inserting
`ShareUrlResolver.changeDomain` immediately `before` the final
`iput-object v7, <sharesheet host>->v:Ljava/lang/String;` with
`MutableMethod.addInstructions(fieldStoreIndex, ...)`.

The field store is the merge point for every URL branch:

```text
:pswitch_c0                     # post share
  iget-object v7, d0->d
  ...
  if-nez v7, :cond_1            # non-null post URL -> :cond_1
:cond_3
  const-string v7, "https://x.com/i/status/"
  goto :goto_0
...
:cond_1
  iput-object v7, h->v
```

`addInstructions(index)` inserts before the instruction at `index`, but the
branch labels stay attached to the original `iput-object`. In the final DEX the
rewrite executed only on the fall-through `:cond_2` null path, while the post
URL, status builder, trending and lists paths all jumped to the label on the
store and skipped it:

```text
if-nez v7, :cond_d0            # branch target is now the store
...
:cond_d0
:goto_d0
invoke-static {v7}, changeDomain   # only reached by fall-through
move-result-object v7
iput-object v7, h->v
```

This is the documented trap "injecting before a branch target can be skipped
even when the final DEX contains the hook". The same structure and the same
bug exist in 12.28.0-alpha.04, which the earlier `d109ef76` "fix" had assumed
worked. The copy callback in that release is `Landroidx/camera/core/impl/n->b`
(not the DM stub `Lcom/x/dm/sharesheet/d->b`), so the earlier assumption that
the copy path had moved into the URL field was wrong; the pre-existing
`share/impl` copy hook simply stopped matching.

## Fix

Insert the rewrite at the control-flow label with
`app.morphe.util.addInstructionsAtControlFlowLabel`. That helper duplicates the
labeled instruction, inserts the rewrite before the copy, and removes the
original location, which retargets the label to the first injected instruction.
`addDomainRewriteAtControlFlowLabel` now handles the field store; the
straight-line call sites keep the plain `addDomainRewrite`.

Patched smali after the fix:

```text
:pswitch_c0
  ...
  if-nez v7, :cond_d0
:cond_cc
  const-string v7, "https://x.com/i/status/"
  goto :goto_b8
:cond_cf
:pswitch_cf
  move-object v7, v6
:cond_d0
:goto_d0
  invoke-static {v7}, changeDomain
  move-result-object v7
  iput-object v7, h->v
```

Every path (post URL, status builder via `:goto_b8 -> :goto_d0`, trending,
lists, null) now flows through `changeDomain`.

## Evidence

Final-DEX `changeDomain` call sites after the fix:

- `12.28.0-prod.01`: `sharesheet/h-><init>` field store (branch target) plus
  `navigation/bh->l`, `navigation/wg->l`, and five `share/impl/k->c` sites.
- `12.27.0-prod.01`: `sharesheet/h-><init>` field store (branch target) plus
  the unchanged `share/impl/m->b` copy callback and two navigation getters.
- `12.28.0-alpha.04`: patch applied; the copy callback owner is the merged
  `Landroidx/camera/core/impl/n`, discovery of which is left as a follow-up
  (see Known limits).

## Known limits

The helper-owner fallback in `hookShareIntentBuilder` still selects only
`Lcom/x/share/impl/k` when the package-scoped `ShareSheetCopyCallbackFingerprint`
misses. On 12.28 the semantic copy callback and three `z8->a` share-Intent call
sites (`c`, `d`, `e`) live in the merged `Landroidx/camera/core/impl/n`.
Post-share copy and share-via receive the already-rewritten share-sheet URL, so
the reported issue is fixed, but share paths that build a fresh URL do not.
The clearest example is profile sharing, where `sharesheet/h->a` builds
`"https://x.com/" + handle` and calls `sharesheet/l->d`, which is not rewritten.
A follow-up should broaden the copy-callback discovery and hook every class that
invokes the resolved helper so those fresh-URL paths are covered.

## Validation

- `./gradlew :patches:lintNewxResolvers` — passed.
- `./gradlew :patches:test` — passed.
- `./gradlew :patches:build` — passed.
- Patched `12.27.0-prod.01`, `12.28.0-alpha.04`, and `12.28.0-prod.01`; each
  reported `Applied: NewX: Custom sharing domain` and `Saved to`.
- `dexscope verify-method` on the patched share-sheet constructor, share Intent
  owner, and navigation getters: no `INVALID` results.
- Runtime/device behavior not exercised by the agent; user asked to verify
  share-sheet copy link and share-via-app with the custom domain set.
