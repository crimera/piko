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

That renderer builds the persistent inline post-detail reply bar. The floating new-post compose
action is rendered by the surrounding post-detail UI, so the renderer is the narrowest shared
hook for hiding the bar. The unrelated `conversations_alternative_reply_hint` path was discarded.

The patch matches the package scope, `void` return type, and stable test tag, then requires exactly
one match. It injects the toggle guard at method entry; the setting key is
`newx.post_actions_media.hide_post_reply_bar` and defaults to `false`.

## Validation

The MPP built successfully with `./gradlew :patches:build --no-daemon`. Exclusive patch runs
rebuilt and signed all six target artifacts with `NewX: Hide post reply bar` applied and no failed
patches. The optional SDK verifier reported only pre-existing missing Stripe/Amazon optional
classes, so the successful validation runs used the normal non-verifying rebuild path.
