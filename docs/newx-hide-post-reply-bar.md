# NewX hide post reply bar

Issue: [piko-newx#37](https://github.com/crimera/piko-newx/issues/37).

## Evidence

The exact stored smali for every declared NewX target contains the Compose test tag
`post-detail-reply-text-field` in one `void` renderer under `com/x/composer/minimal/`:

- `12.20.5-prod.01`
- `12.21.1-prod.05`
- `12.22.0-beta.01`
- `12.22.0-prod.01`
- `12.23.0-prod.01`
- `12.23.1-prod.01`
- `12.25.0-alpha.01`

That renderer builds the persistent inline post-detail reply bar. The floating new-post compose
action is rendered by the surrounding post-detail UI, so the renderer is the narrowest shared
hook for hiding the bar. The unrelated `conversations_alternative_reply_hint` path was discarded.

The patch matches the package scope, `void` return type, and stable test tag, then requires exactly
one match. It injects the toggle guard at method entry; the setting key is
`newx.post_actions_media.hide_post_reply_bar` and defaults to `false`.

The post-detail sheet's wrapper and post-detail Scaffold host the minimal-composer container
(`com/x/composer/minimal/b;->h`), which explicitly draws a vertical gradient scrim
(`Brush.verticalGradient(listOf(Color.Transparent, Color.Black, Color.Black))`) and Haze blur node.
Hiding only the text field renderer left this container running, creating a persistent fading black
gradient over bottom content. The patch resolves the renderer's call chain:
`minimal/e -> minimal/c -> minimal/h -> postdetailsheet/f` from exact bytecode references, and
guards both the minimal-composer container (`minimal/h`) and the post-detail sheet wrapper
(`postdetailsheet/f`) with `returnVoidIfEnabled`. This completely removes the fading black gradient
scrim, blur box, and empty inset space.

The shared `MainActivity` content root applies a system-bar safe area and remains untouched by this
patch. Runtime testing showed that keeping this root inset alone does not protect the likes/repost/
share row in the 12.29 immersive screen. The visible row is rendered in the immersive-media
renderer, before its composer/no-composer branch. With the hide setting enabled, the patch applies
that renderer's existing `navigationBarsIgnoringVisibility` inset to the row's own modifier before
rendering it. It does not force the separate no-composer spacer, which previously created a gap.

Every required match is cardinality-checked before mutation: one tagged renderer, one caller at
each call-chain edge, one mutable container per resolved owner, one post-detail inset call, and at
most one semantic immersive-media action-row renderer. The latter derives the exact action-row
modifier, Composer, WindowInsets holder, navigation-bars field, and modifier call from bytecode.

## Validation

The MPP built successfully with `./gradlew :patches:build --no-daemon`. Exclusive patch runs
rebuilt and signed all seven target artifacts with `NewX: Hide post reply bar` applied and no
failed patches. The optional SDK verifier reported only pre-existing missing Stripe/Amazon
optional classes, so the successful validation runs used the normal non-verifying rebuild path.

The same patch also applied to the declared `12.25.0-alpha.01` target. Runtime installation was
not performed during this change.

## Inset policy (12.29 immersive photo screen correction)

The post-detail reply-bar container applies its local navigation-bar inset in one of two ways:

- **Gated** (verified 12.27/12.28/12.29): a composition-local flag selects the inset, so only
  bottom-anchored immersive compositions (the fullscreen photo screen) reserve the gesture area.
  Preserve the container's gate and hide the composer at `composer/minimal/b;->h`. On 12.29,
  separately apply the immersive-media renderer's native navigation-bar inset to the likes/repost/
  share row itself; the container and `MainActivity` insets do not move that row.
- **Unconditional** (legacy containers): the inset is part of the reply bar's "empty inset space"
  and is removed together with the bar, as in `98ee98c8`.

`HidePostReplyBarPatch.classifyInsetApplication` distinguishes the container shapes at patch time
and fails closed on anything else. The 12.29 action-row regression and its follow-up are recorded in
`docs/newx-resolver-linter/incidents/2026-09-24-12-29-main-activity-root-inset-follow-up.md`.
