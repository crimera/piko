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

Newer targets also apply the navigation-bar inset to the shared `MainActivity` content root. The
patch resolves that call from the stable state-singleton -> Composer getter -> inset-field ->
`Modifier` data flow, not from obfuscated Compose owner or method names. Older targets without that
root call are accepted only after the stable root method is found and verified to have no `insets`
marker. Post-detail sheets have a second, local navigation-insets call; its host is resolved from
the renderer call chain and the same data flow, so Compose owner/method renames do not silently
skip it.

Every required match is cardinality-checked before mutation: one tagged renderer, one caller at
each call-chain edge, one mutable container per resolved owner, and one post-detail inset call.
The root inset hook allows only the explicitly validated legacy zero-call shape or one call. A
navigation call must be followed by `move-result-object`, and the setting branch targets the
instruction after that result, preventing invalid bytecode when the inset is skipped.

Validated root/post-detail inset cardinalities are `0/1` for `12.20.5-prod.01` and
`12.21.1-prod.05`, and `1/1` for `12.22.0-beta.01`, `12.22.0-prod.01`, `12.23.0-prod.01`,
`12.23.1-prod.01`, and `12.25.0-alpha.01`.

## Validation

The MPP built successfully with `./gradlew :patches:build --no-daemon`. Exclusive patch runs
rebuilt and signed all seven target artifacts with `NewX: Hide post reply bar` applied and no
failed patches. The optional SDK verifier reported only pre-existing missing Stripe/Amazon
optional classes, so the successful validation runs used the normal non-verifying rebuild path.

The same patch also applied to the declared `12.25.0-alpha.01` target. Runtime installation was
not performed during this change.
