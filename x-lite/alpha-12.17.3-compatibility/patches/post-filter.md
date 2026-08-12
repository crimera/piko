# Filter Posts by Keyword

## Why it broke

`PostFilterMatcher` accepted `UrtTimelinePost` and traversed many compile-only model types (`PostResult`, users, notes, articles, cards). Alpha obfuscation made those descriptors unsafe in the extension DEX.

## Fix

- Changed the matcher entry point to accept `Object`.
- Added the timeline model adapter dependency.
- Resolve and inject the target post text getter at patch time.
- Keep normalization and phrase matching entirely on safe Java strings/collections.

## Current limitation

Only main post text matching is currently retained. Username, quoted-post, note, article, and card matching were removed from this first safe-boundary port and need separate stable bridges before restoration.

## Verification

The alpha patcher logged this patch as applied before the combined run stopped in the AI patch. Final-DEX and runtime filtering tests remain pending.
