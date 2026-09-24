# NewX photo viewer action-bar insets

## Evidence

The full-screen photo/media controls are composed in `com/x/media/`, not in
`com/x/video/tab/`. The earlier standalone fix targeted the video-tab action-bar helper, so it
patched cleanly without changing the photo viewer shown by the user.

The actual media-controls renderer first emits the shared inline action bar and then chooses one
of two mutually exclusive bottom elements:

- when a reply composer is present, it renders the minimal composer;
- otherwise, it renders a spacer whose height comes from the navigation-bar inset.

`HidePostReplyBarPatch` suppresses the minimal composer at its renderer boundary. The media
controls still take the composer-present branch, however, so neither the composer nor the native
navigation-bar spacer consumes the bottom area. That is why the action bar drops into the gesture
navigation region only while the hide setting is enabled.

The integrated resolver identifies the unique `com/x/media/` method by its preserved parameter
contract, one inline-action-bar call, and one semantic navigation-inset call. It then verifies one
call to the already-resolved minimal-composer container and derives the shared navigation fallback
from the conditional branch targets between those calls. When `hidePostReplyBar` is enabled, an
injected setting guard selects that existing fallback; when disabled, the original gates and
layout are unchanged.

Cardinality is required to be one for the media-controls renderer, inline action-bar call,
minimal-composer call, navigation-inset call, and navigation fallback target. Any changed or
ambiguous shape fails with a `PatchException` and candidate details.

The mistaken `ActionBarUntilExpanded` exemptions remain removed. The photo-viewer correction now
lives in `HidePostReplyBarPatch.kt`; the ineffective standalone patch and script selection were
deleted.

## Validation

`./gradlew :patches:build --no-daemon` passes. Exclusive `NewX: Hide post reply bar` runs report
`Applied` and `Saved to` for every declared target:

- `12.20.5-prod.01`
- `12.21.1-prod.05`
- `12.22.0-beta.01`
- `12.22.0-prod.01`
- `12.23.0-prod.01`
- `12.23.1-prod.01`
- `12.25.0-alpha.01`

The exact 12.20.5 and 12.21.1 APKM inputs were merged before patching. Final DEX inspection on
12.20.5 and 12.25.0 proves the setting read branches directly to the existing navigation-inset
spacer while the original boolean/null gates remain as the disabled-setting control path. The
same semantic resolver tolerated owner churn from `com/x/media/h1;->a` in 12.20.5 to
`com/x/media/e1;->a` in 12.25.0.

## 12.29 correction

The composer→nav-spacer fallback above applies to the legacy media-controls path. On 12.29's new
immersive screen, `media/e0` renders the likes/repost/share row before choosing between the composer
and a separate navigation-bar spacer. Hiding the composer leaves the row's modifier unchanged.
Forcing the spacer (`b46d10dc`) created a gap elsewhere and still did not reliably move the row.

`HidePostReplyBarPatch` now resolves `e0` semantically and reuses the exact
`navigationBarsIgnoringVisibility` object from its native spacer on the action row's modifier. This
moves the row itself, does not modify the reply editor, and leaves the original spacer branch
untouched. Runtime validation on `12.29.0-alpha.04` confirms the row clears the gesture pill. The
exact evidence and incident history are in
`docs/newx-resolver-linter/incidents/2026-09-24-12-29-main-activity-root-inset-follow-up.md` and
`docs/newx-hide-post-reply-bar.md`.
