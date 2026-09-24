# NewX 12.29 alpha tab component split (drawer + navigation bar)

- Date: 2026-09-24
- Reporter/session: user report (`SEVERE: FAILED: NewX: Customize drawer items`); pi repair session
- APK package/version/build type: `com.twitter.android` 12.29.0-alpha.04 alpha
- APK path and checksum: `/Volumes/realme/Dev/piko-x-lite/apks/12.29.0-alpha.04.apk` / `767a55b4897b3d88391d4c53102f502f3f109ba7324b7158e53b2b1ce79fc7b2`
- Source commit: `40560ca79b091a1c4a23b41c1b61983a26671a90` plus this working tree
- MPP path/checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` / `8f5586ed9ef5815891cbf50a22bd869dbbf61f09ad59116bef2e220aaa19f756f`
- Extension artifact path/checksum: `extensions/newx/build/morphe/extensions/newx.mpe` / `7d0304cc9c2a77d833a0b35bc0138129c9d12b498074962eee5e8107d02e39a3`
- Output artifact path/checksum: `/Users/steven/Downloads/piko-twitter-patched.apk` / `e9891118626fbcc9dac9fa66545087c0c71239d8339912dd57ba7c4a8440c854`
- Command: `./patch-twitter.sh --continue-on-error apks/12.29.0-alpha.04.apk`
- Failing patch: `NewX: Customize drawer items` (first), then `NewX: Customize navigation bar` (`resolveNewXNavBarFilterTarget`)
- Severity: high
- Confidence: likely; patch-time behavior and emitted bytecode confirmed, not device-tested

## Symptom

Patching aborted before producing an artifact:

```
SEVERE: FAILED: NewX: Customize drawer items
app.morphe.patcher.patch.PatchException: Expected exactly one NewX tab change method, found 0: []
        at ...ResolverCardinalityKt.requireExactlyOne(ResolverCardinality.kt:15)
        at ...NavBarTabDataKt.resolveTabChangeMethod(NavBarTabData.kt:253)
        at ...CustomizeDrawerPatchKt.customizeNewXDrawerPatch$lambda$0$0(CustomizeDrawerPatch.kt:1412)
```

## Complete error

```
SEVERE: FAILED: NewX: Customize drawer items
app.morphe.patcher.patch.PatchException: Expected exactly one NewX tab change method, found 0: []
```

After the first fix, the run advanced to:

```
SEVERE: FAILED: NewX: Customize navigation bar
app.morphe.patcher.patch.PatchException: Expected exactly one stable NewX tabData State constructor, found 0: []
        at ...NavBarTabDataKt.findStateInitIndex(NavBarTabData.kt:242)
        at ...NavBarTabDataKt.resolveNewXNavBarFilterTarget(NavBarTabData.kt:197)
        at ...NavBarCustomizationPatchKt.injectNavBarFilter(NavBarCustomizationPatch.kt:180)
```

## Reproduction

12.27/12.28 keep the tab-map builder, tab change handler, and drawer close field on one
component (`Lcom/x/main/w;`, `Lcom/x/main/z;`). 12.29 splits them:

- Tab-map builder (what `NewXTabDataFingerprint` still matches): `Lcom/x/main/landing/b;`.
  Its constructor iterates `Lcom/x/navigation/hb;`, drops COMMUNITIES/SPACES, filters GROK by
  feature switch, and `Map.put`s `Lcom/x/main/api/landing/v;` values into a `LinkedHashMap`
  that is stored in a `StateFlow`.
- Tab change: `Lcom/x/main/tabs/j;->j(Lcom/x/navigation/hb;Lkotlin/jvm/functions/Function0;)V`
  (nav bar / tabs UI) and `Lcom/x/main/h;->j(Lcom/x/navigation/hb;Lkotlin/jvm/functions/Function0;)V`
  (root / deeplink). Both satisfy `hasStackNavigationCall()`.
- Drawer tab open: the Communities case of `Lcom/x/home/tabbed/j;->invoke()` loads
  `Lcom/x/main/landing/p;->r:Lkotlin/jvm/functions/Function1;`, invokes it with the tab, then
  closes via `Lcom/x/main/landing/p;->i:Lcom/x/utils/n;`. It no longer pushes the stack frame
  inline and no longer calls a component method.
- Tab-map consumption: the map is passed as the `LinkedHashMap` argument of
  `Lcom/x/main/api/landing/j;-><init>(Ljava/util/Map;ZLjava/util/LinkedHashMap;)V` instead of a
  16-18 parameter state constructor.

The old resolver searched only `tabData.componentClass` (the tab-map builder) for a single
navigation-typed method and required the closer field to live on the same class.

## Cause classification

- [ ] linter behavior
- [ ] cardinality-helper behavior
- [x] resolver logic
- [x] APK contract drift
- [ ] tooling or artifact setup
- [ ] runtime behavior

## Isolation comparison

The pre-change MPP was rebuilt and patched before the repair: both patches failed exactly as
reported. `dexscope` on the frozen APK confirmed the three split owners and the Function1-based
drawer click before any code was changed. The repair was then built and patched on
12.27.0-prod.01, 12.28.0-prod.01, 12.28.0-alpha.01, and 12.29.0-alpha.04.

## Fix and validation

- `resolveTabChangeMethod` became `resolveTabChangeMethods`, an APK-wide scan for methods whose
  first parameter is the navigation enum, whose signature is `[nav]` or `[nav, Function0]`, and
  that contain `hasStackNavigationCall()`. The parameter shape excludes the MainActivity
  deep-link handlers (`Lcom/x/android/main/n6;->p(nav,String,navArgs)V` and its 12.28 twin),
  which share the stack-navigation call: without it the replacement guard was injected into
  those handlers on 12.27/12.28 (4227 modified methods on 12.27 vs 4226 after the constraint).
  The nav bar replacement guard is injected into every remaining match, so every tab-change
  entry point is covered. `hasStackNavigationCall` now accepts the immutable `Method` interface.
- The drawer resolver derives the capture class from the drawer dispatcher's close field owner,
  bounded to the Communities case, and resolves the tab change shape from that case: an inline
  stack push (12.27-12.28) or a captured `Function1` field invoke (12.29). `DrawerTabNavigation`
  carries either a method descriptor or a `Function1` field, and `replaceDrawerTabOpenerBody`
  emits the matching smali.
- `resolveNewXNavBarFilterTarget` falls back to the landing wrapper constructor when no
  state constructor exists, filtering the `LinkedHashMap` argument (with a `check-cast`) at the
  same insertion point. The wrapper is identified purely by shape
  `(Map, Z, LinkedHashMap)V` plus the `LinkedHashMap` argument being the exact register the
  fingerprint's `Map.put` built, so no obfuscated wrapper owner is hardcoded.

Validation:

- `./gradlew :patches:build :patches:lintNewxResolvers --console=plain` (pass)
- `./patch-twitter.sh --continue-on-error apks/12.29.0-alpha.04.apk` — `Applied: NewX: Customize
  drawer items`, `Applied: NewX: Customize navigation bar`, `Saved to`
- `./patch-twitter.sh --continue-on-error` on 12.27.0-prod.01, 12.28.0-prod.01, 12.28.0-alpha.01 —
  both patches `Applied`, no patch-time failures
- `dexscope verify-diff` original vs patched: 0 invalid on all four APKs (135 valid / inconclusive
  remainder per the pass's unmodeled-feature limits)

## Known limits

- The nav bar replacement guard now applies to every APK-wide method that matches; on 12.29 that
  is both `main/tabs/j.j` and `main/h.j`. This is intentional (all entry points must be blocked)
  but means the resolution intentionally has cardinality > 1 on 12.29.
- 12.29 is not added to `Constants.COMPATIBILITY_NEW_X`; other NewX patches
  (`Disable video player scrolling`, `Filter For You by topic`, `Hide compose button`,
  `Hide post dividers`, `Open canonical URLs`, `Server error logging`,
  `Set default profile post sorting`) still fail on the same build. The CLI run used `--force` /
  `--continue-on-error`.
- Runtime behavior (drawer shortcuts, tab replacement, tab filtering) was not device-tested.
