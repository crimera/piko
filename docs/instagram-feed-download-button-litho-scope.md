# Scope: Litho-level hook for the feed download button (profile post viewer)

Target: `com.instagram.android` 439.0.0.37.89. Branch: `backport/feed-download-button-439`.

The inline download button works on the main feed because that surface binds the view UFI row
holder (`LX/01Tb`) through `LX/08OC;->A07(LX/00R5;LX/0Aeo;LX/01Tx;LX/01Tb;)V`, where the extension
hook already lives. The profile contextual post viewer does not use that holder, so no variant
pinning reaches it:

- pinning `LX/00SX` for `feed_contextual_*` only flips the row-type registration branch and drops
  the whole UFI row;
- pinning the profile module chooser (`LX/00w0`) keeps the bar but never creates the holder;
- swapping the contextual registrar's hardcoded row type (`LX/00S9;->A07`, `litho_media_ufi` →
  `media_ufi`) keeps the bar but still does not create or bind the holder.

Runtime file diagnostics (`piko-feed-download.log` in the app cache) show the hook firing and
attaching on the main feed only; the contextual surface never calls `LX/08OC;->A07`.

## Verified rendering path (439)

1. `ContextualFeedFragment` creates `LX/06bP` (FeedItemBinderGroup).
2. `LX/06bP;->A0W` routes the contextual (non-`feed_timeline`) module to
   `LX/00S9;->A07(LX/09mm;Lcom/instagram/feed/media/Media;LX/00R5;LX/00u4;)V`, which registers
   Litho rows on the delegate:
   - `litho_coalesced_media`, `litho_media_header`, `litho_media_content`,
     `litho_media_ufi`, `litho_coalesced_footer`.
3. `LX/06bP;->A0T(Landroid/view/View;LX/00GP;LX/01Pb;Ljava/lang/Object;)V` binds those rows.
   `LX/01Pb` carries `A00: Media`, `A01: LX/00R5`, `A02: LX/01Pa`; the binder has
   `A08: UserSession`.
4. `A0T` hands the Litho row to `LX/00S8;->A03(Landroid/view/View;LX/00GP;Ljava/lang/Object;Ljava/lang/Object;)V`
   (an instance method; `LX/00S8` has `A02: UserSession`), which delegates to
   `LX/0Ael;->GKt(Landroid/view/View;LX/0Awl;Ljava/lang/Object;I)V`.
5. The Litho UFI component builder `LX/01m8;->A0o(LX/01iy;)LX/03Wk;` (presenter, super `LX/03iE`)
   adds a node with:
   - `viewClass = "android.widget.Button"` (`LX/07iO;->A07`), and
   - `id = 0x7f0b3603` (`row_feed_button_save`) (`LX/01kJ;->A0G`).

Because that node is a real Android view with the save-button id, the existing view-based
extension can attach to the mounted tree once it is available.

## Option A (preferred POC): post-mount view insertion

Hook `LX/00S8;->A03` at index 0 and call a new extension entry point with the row view, media and
session. The extension schedules the existing insertion logic with `View.post(...)` because Litho
mounts children asynchronously, then finds `row_feed_button_save` and inserts the tagged
`ImageView` beside it exactly like the view-holder path.

Patch-side resolution:

- Reuse the existing binder/holder resolution; `LX/08OC;->A07` is already resolved.
- Resolve `LX/06bP` as the class that both invokes `LX/08OC;->A07` and owns the
  `(View, LX/00GP, LX/01Pb, Object)V` method, then take that method as `A0T`. `LX/00GP` is
  resolved through the row-type enum `<clinit>` semantic strings (`media_ufi`, `litho_media_ufi`,
  ...), the same technique already prototyped with `enumFieldFor(name)`.
- Resolve `LX/00S8;->A03` from `A0T`'s call site: `invoke-virtual` on the provider field, with
  parameters `(View, LX/00GP, Object, Object)V`.
- Resolve the UFI row types from the enum: `litho_media_ufi`, `litho_coalesced_media`, and
  `media_ufi` (main-feed Litho fallback). Call the extension only for those types.
- Emit: `move` view/media from the args, `iget UserSession` from `LX/00S8;->A02`, then
  `invoke-static` the new extension entry. `insertHook(index = 0, relocateBranchTargets = false)`
  is safe: `A03` is a 14-instruction leaf with no labels at index 0.

Extension changes:

- New entry `addFeedDownloadButtonWhenReady(View rootView, Object mediaObject, UserSession session)`
  that:
  - calls the existing logic immediately, and
  - if the save button is not found yet, posts one retry per root view (guard with a tag or
    `rootView.removeCallbacks`), and re-arms on `OnGlobalLayout`/next bind.
- Keep the idempotent tag check; rebinds must not stack buttons.
- Media may be `Media` or an `LX/0Awl` wrapper (`CWx()` returns the media); handle both before
  calling `downloadPost`.

## Option B (if Option A is unstable): native Litho component

Define a Litho component in the extension (compileOnly Litho core) whose mount content is the
download `ImageView` and whose click calls `DownloadUtils.downloadPost`, then append it to the UFI
component tree in `LX/01m8;->A0o` next to the save node (`LX/03iH` builder calls). This makes the
button part of the component tree so it survives recycling. Cost: extension gains a Litho
dependency, and the injection point is builder-call-shaped (`LX/01kJ`/`LX/07iO` sequences) and more
fragile across versions.

## Risks and mitigations

- **Litho unmount removes foreign children.** Mitigate by re-adding on every `A03` bind and
  verifying scroll in/out of the viewport.
- **ComponentHost bookkeeping.** Insert into the save button's parent only when it is a plain
  `ViewGroup`; clone layout params from the save button (existing `cloneLayoutParams`). Fall back
  to the nearest host ancestor otherwise.
- **Async mount.** Never insert synchronously in the hook; use `post` plus a one-pending-runnable
  guard.
- **Main feed double attach.** The tag check makes the extra call a no-op; main feed behaviour must
  be unchanged.
- **Performance.** Gate the hook on the three UFI row types so non-UFI Litho rows pay nothing.

## Test plan

1. Build the POC on this branch, patch 439, install with plain `adb install -r` (not
   `--fastdeploy`, which left a stale `oat_primary` cache during this investigation).
2. Main feed: button still present, click opens the download dialog, scrolling keeps it.
3. Profile -> post viewer: button beside save, click downloads the post, bar intact.
4. Scroll the viewer away and back: button still present (recycled rows).
5. `dexscope verify-diff`: no new invalid methods.

## Effort

- Option A POC: resolver + hook + extension retry path, roughly one focused session.
- Option B: substantially larger; only if Option A proves unstable on recycled Litho rows.
