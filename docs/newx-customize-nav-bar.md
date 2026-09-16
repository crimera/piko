# NewX customize navigation bar

Feature: `NewX: Customize navigation bar` (`customizeNewXNavBarPatch`).

The patch replaces the old hide/replace single-choice settings with one editor custom screen. The
screen has a `Shown in navigation bar` section and an `Available destinations` section. Dragging
reorders shown native slots, hides a shown slot when it is dropped into the available section, or
assigns an available destination to a shown slot. The shown section is capped at five items.
Replacement destinations include Bookmarks, Profile, Lists, Communities, Spaces, and Creator
Studio. History is included on releases that expose its drawer entry. Destination labels and icons
always come from the resolved drawer row.

## Why replacement and not an eighth item

The NewX bar is a fixed Kotlin enum (`Lcom/x/navigation/ha;` on 12.25, renamed per release) with
five native slots. The bar renders one item per entry of the tab map
(`Lcom/x/main/api/e0;->i`, `Map<ha, f0>`) and selection is the head of the Decompose child stack.
Every enum consumer is an exhaustive `when` that throws `NoWhenBranchMatchedException`. Adding a
new constant would require enum injection plus patches to the item icon/label switch, the child
renderer switch, and the bar map, and the Bookmarks screen is a `screenNavigator` route rather than
a tab component, so it could never be selected. Available destinations reuse the app's own drawer
navigation instead of adding new enum values.

Order and visibility are free: the bar iterates the `LinkedHashMap` the extension filter returns,
so the editor just persists an ordered list and the filter rebuilds the map.

## Resolvers

- Navigation enum and component: shared `NewXTabDataFingerprint` (`tabData` builder with
  `getEntries`, `COMMUNITIES`, `SPACES`, `Map.put`). `validateNewXNavBarTabData` also resolves the
  map value type (`f0`) from the `Map.put` value register's `new-instance`.
- Tab map filter: `resolveNewXNavBarFilterTarget` finds the generated state constructor that
  receives the tab map (constructor parameter index 9) and injects
  `NavBarFilter.filter(Map) -> Map` before it.
- Tab change method: unique `void` method in the component class whose only parameter is the
  navigation enum and that reads the component's stack navigator field before invoking its
  two-lambda navigation operation.
  The guard calls `openReplacementFor(tab)` and returns before the original body when the captured
  drawer click handled the request.
- Item content lambda: unique constructor `(Z, <enum>, <f0>)`; the enum field is resolved from the
  constructor's `iput-object` of the tab parameter, and the icon/label renderer is the unique
  `(<icons/*>, String, <f0>, Composer, I)V` method it calls. The method is cloned with one extra
  local (`cloneMutable(numberOfParameterRegisters + 1)`) because newer releases reuse the receiver
  parameter register for the label resource. The override hook is inserted at the renderer call
  itself: the label switch's internal jump target is the label conversion instruction, so an
  earlier insertion is skipped by every case but the fall-through one.
- Tab icons: the item content icon switches are parsed. The `when` mapping array is parsed for the
  enum-case mapping, the icon switch payloads are parsed for case branches, and the unselected
  switch's icon field is used for both states.
- Icon drawables: each resolved `com/x/icons/*` static field is traced in its `<clinit>` back to
  the `new-instance`/`const`/`<init>(I)V` allocation so the editor can render the app icons with
  `ImageView.setImageResource` without referencing app classes.
- Drawer row: title-based drawer row renderers are discovered by shape
  (`String, <icons/*>, Function0, Modifier, ...`, 8-10 params) and matched by the row's resolved
  title resource id. Rows may live outside `com/x/main/drawer/` in a lazy row lambda, so the scan is
  not package-scoped.
- Every selection is cardinality-checked with `requireExactlyOne`; a missing row, icon, field,
  renderer, conversion, or drawable throws a `PatchException`.

## Editor screen

`NavBarEditorFragment` (`newx.navigation.editor` custom screen) lists the five native slots from
`NavBarCatalog.tabIds()` in the stored order, followed by unassigned drawer destinations. Each row
has a drag handle, the effective icon, and the localized title.

- Order is persisted with `NavBarConfig.saveOrder` on every drop.
- Dropping any shown slot into the available section hides it; a replacement also clears its
  destination, so both the native slot and the destination become available again.
- Dropping an available destination onto a shown slot assigns it directly; duplicate destinations
  are omitted from the available section.
- Tab and destination icons are tinted with `Theme.primaryText` because the app icon drawables carry
  their own fill color.
- The scrollable content ends with a rounded filled restart button. It stays disabled until a
  navbar change is made, because the tab map is built once when the component is constructed.

## Injected hooks

- `NavBarCatalog.registerDestination/registerTab` and the internal `NavBarConfig` settings run at
  settings-registry load.
- `NavBarFilter.filter(Map)`: reorders and hides entries using `NavBarConfig`.
- `NavBarReplacement.overrideIcon` / `overrideLabel`: substituted at the icon/label renderer call.
- `NavBarReplacement.openReplacementFor(tab)`: at the tab change method entry; returns `false` and
  falls through to the original behavior when no destination is selected or no click was captured.
- One generated `captureDestinationClick<N>(Function0)` bridge per destination: the drawer row call
  has no spare low register for the destination id string, so the id is baked into a static bridge
  that the row calls with the click alone. The captured click is re-captured on every composition.

## Known limits

- Changes require an app restart before the bar reflects them.
- The replaced item is a launcher: it never renders as selected and the underneath tab stays
  selected, matching the drawer shortcut behavior.
- The tab badge (`f0`) is not cleared, so a replaced Messages item can still show the DM badge.
- The drag list auto-scrolls when a dragged item is held near the top or bottom edge.
- If the drawer click has not been captured yet (the drawer row was never composed), the item falls
  back to the original tab until the next drawer composition.

## Validation

Patch applied (`INFO: Applied: NewX: Customize navigation bar`) and every method referencing
`NavBarReplacement` verified with `dexscope verify-method` (all `INCONCLUSIVE`, no verifier error):

| Target | APK sha256 (12) | Resolved content class | Tab method |
|---|---|---|---|
| 12.25.0-alpha.01 | `18bd4bdf92e6` | `Lcom/x/android/main/rn;` | `Lcom/x/main/u;->f` |
| 12.25.2-prod.01 (APKM base) | — | `Lcom/x/android/main/sn;` | `Lcom/x/main/v;->f` |
| 12.26.0-alpha.01 | `b3d0e3e7bc6a` | `Lcom/x/android/main/sn;` | `Lcom/x/main/v;->f` |
| 12.26.0-alpha.02 | `7bf6b883f43e` | `Lcom/x/android/main/un;` | `Lcom/x/main/v;->f` |
| 12.26.0-alpha.03 | `4a5bc95c8b6a` | `Lcom/x/android/main/vn;` | `Lcom/x/main/w;->f` |
| 12.27.0-alpha.01 | `82d0fe729854` | `Lcom/x/android/main/vn;` | `Lcom/x/main/w;->f` |

`apks/twitter_12.25.0-prod.01.apk` is corrupt on disk (`unzip -t` fails, patching aborts with
`EOFException` while reading the container), so it was not used for validation. Re-fetch the
artifact before retesting that target.
