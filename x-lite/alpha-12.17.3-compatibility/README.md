# X-Lite 12.17.3-alpha.01 Compatibility

This directory tracks the X-Lite patch port from Twitter/X 12.14.0 and 12.15.1 to `com.twitter.android` `12.17.3-alpha.01`.

The alpha removes or obfuscates several contracts previously treated as stable. Fixes must preserve compatibility with 12.14.0 and 12.15.1 and avoid embedding alpha-specific descriptors in extension code.

## Status

| Patch / component | Breakage | Current status |
|---|---|---|
| [Settings](patches/settings.md) | Compose invocation changed; legacy styles/layout and AppCompat toolbar assumptions disappeared | Runtime-tested: settings opens on alpha |
| [Timeline model adapter](patches/timeline-model-adapter.md) | Timeline item descriptors are obfuscated | Implemented; full runtime matrix pending |
| [Disable automatic timeline refresh](patches/disable-timeline-refresh.md) | `TimelineType` descriptor became obfuscated | Implemented; alpha patch/runtime verification pending |
| [Remove ads](patches/remove-ads.md) | Extension DEX referenced unobfuscated timeline models | Generic boundary implemented; runtime verification pending |
| [Hide who to follow](patches/hide-who-to-follow.md) | Extension DEX referenced unobfuscated timeline models | Generic boundary implemented; runtime verification pending |
| [Filter posts by keyword](patches/post-filter.md) | Extension DEX referenced `UrtTimelinePost` and related models | Main-text generic bridge implemented; broader matching temporarily reduced; runtime verification pending |
| [Hide AI-generated posts](patches/hide-ai-generated.md) | Timeline and content-disclosure models/getters are obfuscated | In progress; alpha patching still fails while resolving the post-to-disclosure accessor |

## Alpha mappings observed

| Semantic model | Alpha descriptor |
|---|---|
| `UrtTimelineItem` | `Lcom/x/models/timelines/items/d0;` |
| `UrtTimelinePost` | `Lcom/x/models/timelines/items/w0;` |
| `UrtTimelineModule` | `Lcom/x/models/timelines/items/n0;` |
| `UrtTimelineModuleItem` | `Lcom/x/models/timelines/items/q0;` |
| `UrtTimelineRtbImageAd` | `Lcom/x/models/timelines/items/c1;` |
| `ContentDisclosure` | `Lcom/x/models/a1;` |

These names are analysis evidence only. Production extension signatures must not reference them directly.

## Verification record

- `:extensions:xlite:assembleRelease`: passes after the settings activity changes.
- `:patches:build`: passed for the initial timeline boundary refactor.
- Settings runtime progression on alpha:
  1. Missing `Twitter` style fixed with optional style lookup and fallback.
  2. Missing `preference_fragment_activity` fixed with an extension-owned programmatic layout.
  3. Stripped AppCompat `Toolbar` crash fixed by framework `LinearLayout`, `ImageButton`, and `TextView` controls.
  4. Fragment restore failure fixed by reusing the host `fragment_container` ID.
  5. User confirmed the settings screen opens.
- Four-patch alpha test currently stops in **Hide AI-generated posts** because no direct post accessor returning the resolved disclosure model has yet been identified.

## Remaining matrix

1. Finish the AI disclosure bridge.
2. Build the MPP and patch 12.17.3-alpha.01 with each timeline patch independently.
3. Inspect final DEX for direct alpha model operations and absence of unobfuscated timeline descriptors.
4. Install and exercise timeline filtering without `NoClassDefFoundError`.
5. Repatch and test 12.15.1 and 12.14.0.
