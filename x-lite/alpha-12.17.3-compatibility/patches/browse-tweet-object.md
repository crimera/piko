# Browse tweet object

## Status

**Ported and verified on 12.17.3-alpha.01.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/browseobject/BrowseObjectPatch.kt`
Framework: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/postoptions/PostOptionsPatch.kt`

## Breakage on Alpha

1. **Obfuscated Action Carrier**: `PostActionType.None` is obfuscated to `Lcom/x/models/w5;->None`.
2. **Obfuscated Presenter & Post Item**: `DefaultPostOptionsPresenter` is `com.x.urt.items.post.options.s` holding field `b` (`Lcom/x/models/timelines/items/w0;`).
3. **Multi-option Icon Interception**: In `PostOptionsPatch`, icon interception previously skipped secondary contributions when the first returned `false`.

## Fixes

1. `PostOptionsPatch` dynamically maps `PostActionType` and matches timeline item packages.
2. `XLiteUtils.findPresenterData` matches timeline post fields with package prefix `com.x.models.timelines.items.`.
3. Chained multi-contribution icon continuation in `PostOptionsPatch.injectLabelsAndIcons`.

## Verification Evidence

- `BrowseObjectPatch` applies cleanly to `12.17.3-alpha.01`.
- Post options sheet displays "Browse Tweet Object" with the flask icon (`ic_vector_flask_stroke`).
- Tapping the action successfully resolves the timeline post and opens the Object Browser activity.
