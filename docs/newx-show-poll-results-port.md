# NewX show-poll-results port

## Evidence

- Target package: `com.twitter.android`.
- The declared NewX compatibility list is the source of supported versions; the formatter's
  wrapper evidence was checked in stored smali for 12.20.5-prod.01, 12.21.1-prod.05,
  12.22.0-prod.01, 12.23.0-prod.01, and 12.23.1-prod.01.
- Morphe MCP dry runs found exactly one static helper with shape
  `(int, String, Map) -> String` under `Lcom/x/cards/impl/poll/` in 12.22.0 beta/prod,
  12.23.0, and 12.23.1. Its body builds `choiceN_kind` keys and reads the binding map.
- The same helper shape patched successfully in the locally available 12.20.5 and 12.21.1
  split bundles.
- The renderer separately reads `counts_are_final` as `com.x.models.cards.f` and the helper
  returns `com.x.models.cards.m.b`; their verified stable `toString()` labels are
  `BooleanValue(value=...)` and `StringValue(value=...)`.

## Hook

Before the helper's original first instruction, the patch calls the primitive-only
`PollResultsFormatter.formatLabel(int, String, Map)` bridge. A non-null result returns immediately;
null branches to the original helper. Only `label` lookups are changed, and final polls or
malformed bindings fall back unchanged.

The discarded anchor was the larger Compose poll renderer containing `counts_are_final`. The leaf
helper is preferable because it controls the exact consumed label and avoids constructing or
mutating obfuscated card model instances.

## Limits

The resolver intentionally fails closed if the helper shape or owner scope becomes ambiguous or
disappears. Runtime formatting relies on the verified `StringValue`/`BooleanValue` labels while
also accepting direct Java primitives for testability. The setting defaults to disabled; visual
device testing with the setting enabled remains a follow-up to the successful patch/build checks.
