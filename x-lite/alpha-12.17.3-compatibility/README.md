# X-Lite 12.17.3-alpha.01 Compatibility

This directory tracks the X-Lite patch port from Twitter/X 12.14.0 and 12.15.1 to `com.twitter.android` `12.17.3-alpha.01`.

The alpha removes or obfuscates several contracts previously treated as stable. X-Lite compatibility now targets `12.17.3-alpha.01` only; older targets were retired rather than carrying parallel legacy fingerprints. Release-specific descriptors must not be embedded in extension code.

## Status

| Patch / component | Breakage | Current status |
|---|---|---|
| [Settings](patches/settings.md) | Compose invocation changed; legacy styles/layout and AppCompat toolbar assumptions disappeared | Runtime-tested: settings opens on alpha |
| [Timeline model adapter](patches/timeline-model-adapter.md) | Timeline item descriptors are obfuscated | Runtime-tested on alpha through timeline filters; no `NoClassDefFoundError` |
| [Disable automatic timeline refresh](patches/disable-timeline-refresh.md) | `TimelineType` descriptor became obfuscated | Runtime-tested on alpha: working |
| [Remove ads](patches/remove-ads.md) | Extension DEX referenced unobfuscated timeline models | Runtime-tested on alpha: working |
| [Hide who to follow](patches/hide-who-to-follow.md) | Extension DEX referenced unobfuscated timeline models | Runtime-tested on alpha: working |
| [Filter posts by keyword](patches/post-filter.md) | Extension DEX referenced `UrtTimelinePost` and related models | Main-text generic bridge runtime-tested on alpha: working |
| [Hide AI-generated posts](patches/hide-ai-generated.md) | Timeline, contextual-post, canonical-post, and disclosure models are obfuscated | Runtime-tested on alpha: working |
| [Customize inline actions](patches/customize-inline-actions.md) | Presenter/model descriptors changed and the final Compose list conversion had severe register pressure | Ported and exercised through inline-download dependency; hide-option matrix pending |
| [Inline download button](patches/inline-download-button.md) | Inline models/getters and icon renderer changed; old extension types failed verification | Runtime-tested on alpha: action, icon, and download working |

## Unported queue

These patches have not yet been independently run and verified on the alpha. Their documents are placeholders for the exact failure, fix, and evidence discovered during each port.

### Priority 1 — Timeline and user-facing controls

- [Hide compose button](patches/hide-compose-button.md)
- [Hide new posts pill](patches/hide-new-posts-pill.md)
- [Hide Spaces bar](patches/hide-spaces-bar.md)
- [Restore timeline position](patches/restore-timeline-position.md)
- [Show sensitive media](patches/show-sensitive-media.md)

### Priority 2 — Premium and navigation

- [Hide premium upsell](patches/hide-premium-upsell.md)
- [Unlock downloads](patches/unlock-downloads.md)
- [Customize drawer items](patches/customize-drawer.md)
- [Customize navigation bar items](patches/customize-navigation-bar.md)
- [Customize default media tab](patches/default-media-tab.md)
- [Customize default reply sorting](patches/default-reply-sorting.md)

### Priority 3 — Miscellaneous features

- [Open canonical URLs](patches/open-canonical-urls.md)
- [Custom font](patches/custom-font.md)
- [Dynamic color](patches/dynamic-color.md)
- [Feature switch overrides](patches/feature-switch-overrides.md)
- [Share post as image](patches/share-post-as-image.md)

### Priority 4 — Diagnostics and development tools

- [Log network diagnostics](patches/log-network-diagnostics.md)
- [Collect AI-filter training posts](patches/collect-ai-training-posts.md)
- [Browse tweet object](patches/browse-tweet-object.md)

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
- User runtime-verified the timeline model adapter, automatic-refresh control, ad filtering, who-to-follow filtering, and keyword filtering on the alpha. The original timeline-model `NoClassDefFoundError` is gone.
- User runtime-verified AI-generated post filtering on the alpha after resolving the full timeline post → contextual post → canonical post → content disclosure field chain.
- User and agent runtime-verified the alpha inline-download action: normal-size download icon appears on media posts, native actions remain usable, and tapping it saves media successfully.

## Remaining matrix

1. Work through the unported queue, one patch at a time.
2. Build the MPP and patch 12.17.3-alpha.01 with each patch independently.
3. Inspect final DEX for reachable mutations and unresolved model references.
4. Install and exercise each feature on alpha.
5. Exercise the production patch set on 12.17.3-alpha.01.
6. Keep the centralized compatibility list aligned with the current alpha-only target.
