# Share post as image

## Status

**Ported and verified on 12.17.3-alpha.01.**

Source: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/shareimage/ShareImagePatch.kt`
Framework: `patches/src/main/kotlin/app/crimera/patches/xlite/misc/postoptions/PostOptionsPatch.kt`

## Breakage on Alpha

1. **Obfuscated Action Carrier**: `PostActionType` is obfuscated to `Lcom/x/models/w5;` on alpha (`ViewDebugDialog` and `None` enum items).
2. **Obfuscated Presenter & State**: `PostOptionsPresenter` is `Lcom/x/urt/items/post/options/s;` holding `b: Lcom/x/models/timelines/items/w0;`. The `PostOptionsState` constructor parameter 2 is obfuscated to `Lcom/x/models/kh;`.
3. **Action Dispatcher**: Click handling in `com.x.urt.items.post.options.m` uses `invoke-virtual {v...}, Ljava/lang/Enum;->ordinal()I`.
4. **Post Identifier & Timeline Post State**: Timeline post state is `com.x.urt.items.post.d5` and its `PostIdentifier` field `b` is `Lcom/x/models/b6;` (`a: Long` post ID).
5. **Compose Icon Injection**: In `androidx.compose.material.i0`, `toIcon()` branches to a `default:` case setting `x7` (`ic_vector_overflow` = 3 dots) for custom action enums. Icon injection must run on the common path alongside `labelFor` rather than inside an individual switch branch case.

## Fixes

1. Dynamically match `PostActionType` via `PostActionTypeFingerprint` looking for `ViewDebugDialog` and `AddToBookmarks`.
2. Relax parameter matching for `PostOptionsState` constructor and broaden presenter type matching to `Lcom/x/models/timelines/items/`.
3. Locate `Enum.ordinal()` inside the post options event handler (`m.smali`) and inject action carrier handler before ordinal resolution using safe 4-bit registers.
4. Refactor `injectLabelsAndIcons` to inject the `usesIcon` check and icon register assignment directly on the common execution path alongside `labelFor`.
5. Support `Lcom/x/models/b6;`, String, and Number in `XLiteShareImageHandler.identifierValue()`, and support Float Rect representations in `XLiteShareImageHandler.readIntRect()`.

## Verification Evidence

- `ShareImagePatch` applies cleanly to `12.17.3-alpha.01`.
- Post options sheet displays "Share Tweet as Image" with the real share icon (`ic_vector_share`).
- Tapping the option captures rendered tweet bounds and opens the system share sheet.
