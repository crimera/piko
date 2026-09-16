# NewX navbar customization review

## Consolidation

The old NewX hide-only `CustomizeNavBarPatch.kt` was removed. The separate replacement patch from
the preceding implementation was folded into `NavBarCustomizationPatch.kt`.

There is now one user-facing NewX patch:

- Name: `NewX: Customize navigation bar`
- Source: `patches/src/main/kotlin/app/crimera/patches/newx/misc/navbar/NavBarCustomizationPatch.kt`
- Script entry: `patch-twitter.sh`
- Patch-list entry: `patches-list.json`

The patch includes item reordering, hiding, and drawer-destination replacement. Replacement icons
and labels are always taken from the selected destination.
`NavBarFilter.filter(Map)` applies the persisted order and hidden-item configuration before the
navigation state is consumed. The settings and extension bytecode patches remain internal
dependencies; they are not separate user-facing navbar patches.

## Findings

The previously reported divider-ordering and destination-icon-preview findings were resolved by
the two-section editor redesign. The replacement scratch register now uses the 4-bit allocator
required by the non-range invoke form.

## Validation performed

- `./gradlew clean :patches:build` passed.
- `./gradlew :patches:lintNewxResolvers` passed.
- The unified patch applied successfully to the available declared targets:
  - 12.25.0-alpha.01
  - 12.25.2-prod.01 base APK
  - 12.26.0-alpha.01/02/03
  - 12.27.0-alpha.01
- Final-Dex verification found no new invalid methods. The one invalid method reported on the
  12.25 targets (`Lads_mobile_sdk/se;->f`) is also invalid in the corresponding unpatched APK.
- `12.25.0-prod.01` was not validated because the local APK artifact is corrupt.
- No device/runtime test was performed during this review.
