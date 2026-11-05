# 12.40.1 prod: automatic-refresh event handler and reply-bar drift

- Date: 2026-11-05
- APK: `../reseam/patches-x/apks/12.40.1-prod.01.apk`, `com.twitter.android` `12.40.1-prod.01`
- Source checkout: `b71db7a71ad8c819a6248ffc2407fa0e60b42c6f` plus uncommitted timeline work
  (`DisableTimelineRefreshPatch.kt`, `RestoreTimelinePositionPatch.kt`, `TimelineRefreshGate.java`, …)
- MPP: `patches/build/libs/patches-<gradle.properties version>.mpp`, rebuilt from the working tree
- Command: `./gradlew publish && ./patch-twitter.sh ../reseam/patches-x/apks/12.40.1-prod.01.apk`

## 1. NewX: Disable automatic timeline refresh

Error: `Failed to match the fingerprint: app.crimera.patches.newx.timeline.NewXUrtAutoRefreshEventFingerprint`

Cause category: APK contract drift (resolver logic assumed one dispatch).

The URT event handler (`Lcom/x/urt/a0;->k(Lcom/x/urt/t1;)V`) now has two
`AUTO_REFRESH` request dispatches:

- index 57, on the `RefreshTimeline` branch (`areEqual(p1, g1.a)` → `if-eqz` fall-through); the
  same shape exists unchanged from 12.34 through 12.40.0-alpha.01;
- index 124, on a new `ReloadTimelineInPlace` (`i1`) branch whose only producer is
  `Lcom/x/explore/q;->invokeSuspend`.

The fingerprint required exactly one `AUTO_REFRESH` read and one repository request call.

Fix: the fingerprint accepts any equal number of reads and request calls. The handler then selects
the read by control flow:

1. The `RefreshTimeline` event is the singleton read whose class implements the handler's event
   interface and whose `toString` returns `"RefreshTimeline"`. Exactly one is required.
2. The `Intrinsics.areEqual` call must compare the event parameter, proved with
   `provesParameterValue`, against that singleton, proved with `provesValueFromInstruction`. Its
   result must feed an `if-eqz`. Exactly one such call is required.
3. Over all CFG edges, including exceptional ones, the selected `AUTO_REFRESH` read must be
   reachable from the match fall-through and unreachable from the mismatch target. Exactly one read
   is required.
4. The repository request must be the instruction right after the read and must consume the read's
   register.
5. The guard hook's index is `settingRead.nextIndex`, not the first matching field read.

Explore's in-place reload is intentionally left unguarded.

Validation (`:patches:build`, `:patches:lintNewxResolvers` passed):

- 12.40.1-prod.01: refresh patch `Applied`; the run then aborts on the reply-bar failure below.
- 12.40.0-alpha.01 (`--continue-on-error`): every patch `Applied`, saved to `/tmp/piko-refresh-12.40.0-alpha.01.apk`.
- 12.39.0-prod.01 (`--continue-on-error`): every patch `Applied`, saved to `/tmp/piko-refresh-12.39.0-prod.01.apk`.
- No runtime test on a device yet.

## 2. NewX: Hide post reply bar (blocks the patch run after fix 1)

Error: `NewX media reply-bar capability is not proven for Lcom/x/composer/minimal/b;->i(...)`.
`Direct media callers: []. Selected immersive safe-area renderer: absent.`

Cause category: APK contract drift (layout refactor).

`Lcom/x/media/y0;->a(...)` still has the immersive parameter types and the single inline-action-bar
render. The reply composer and bottom inset moved into a new helper,
`Lcom/x/media/y0;->b(haze, composer/minimal/r, Function0, Function1, immersive/chrome/u, media/e0, Z, Composer, I)V`.
That helper reads `WindowInsetsHolder.navigationBars` as a raw bottom `int` (`Math.max`), not as a
`padding(WindowInsets)` Modifier call. It shows the composer through `AnimatedVisibility`, using
the merged lambda `Lcom/x/dm/grokbots/q1`. `isImmersiveMediaControlsShape` therefore finds no
navigation-insets call, the immersive hook is absent, and the capability proof fails closed.

`y0.b` is a bottom Box. It computes its height before branching, from fixed dp values plus
`WindowInsetsHolder.n` (`navigationBarsIgnoringVisibility`, per the `z3.<init>` naming string). It
then shows either the playback bar (`getHasPlaybackControls`) or the AnimatedVisibility composer.
When the minimal container returns early, the Box keeps its height, so the `GalleryActionBar` row
above it stays clear of the gesture area. 12.40.1 therefore needs no action-row padding hook.

Fix: `mediaBottomSlotReservesNavigationInset` is a second accepted shape in
`requireImmersiveOnlyPhotoViewerReply`. It is used only when there are no direct media callers and
no immersive hook. It requires all of the following:

1. Exactly one media-scope inline-action row renderer, with exactly one row call.
2. At most one media-scope static callee after the row call that holds a reply-composer anchor
   (`replyComposerAnchorIndices`, including the AnimatedVisibility-content proof). Exactly one
   anchor in that callee.
3. At most one `iget-object` in the slot whose receiver is proved, via `ReplyControlFlow.origin`,
   to be the result of a Composer-scoped foundation-layout holder accessor. The field must be named
   `navigationBars` or `navigationBarsIgnoringVisibility`. The name is proved from the holder
   constructor: the field's single write comes from a call or `<init>` whose argument originates
   at that `const-string`.
4. Dominance: the inset read is reachable, and the anchor is unreachable from entry once the read
   is removed (`ReplyControlFlow.reachable(skip = …)`, which rejects try blocks).

Known limit: the proof shows the inset is read on every path to the composer. It does not follow
the arithmetic into the Box height modifier.

Validation (MPP rebuilt; `:patches:build` and `:patches:lintNewxResolvers` passed):

- 12.40.1-prod.01: no failures. Both patches `Applied`; saved to `~/Downloads/piko-twitter-patched.apk`.
- 12.40.0-alpha.01, 12.39.0-prod.01 (`--continue-on-error`): no failures; both patches `Applied`.
- 12.34.0-prod.01 is not a declared target. It already fails seven unrelated patches, reply bar
  included. The new shape only adds an accepting path ahead of the existing failure.
- Runtime (hidden reply bar; action row above the gesture pill on photo and video media) not yet checked.
