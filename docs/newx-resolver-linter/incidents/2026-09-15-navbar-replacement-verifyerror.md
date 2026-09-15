# NewX navigation bar item content lambda reuses the receiver register for the label

- Date: 2026-09-15
- Reporter/session: user device test; pi repair session
- APK package/version/build type: `com.twitter.android` NewX 12.26/12.27 alpha family
  (`com.x.android.main.vn` on 12.26.0-alpha.03 and 12.27.0-alpha.01; `sn` on 12.25.x)
- Source commit: working tree after `NewX: Replace navigation bar item` was first implemented
- Failing patch: `NewX: Replace navigation bar item` (`replaceNewXNavBarItemPatch`)
- Severity: critical on device (activity fails to compose and the app crashes)
- Confidence: high; reproduced and fixed in local builds, verifier-clean on the declared targets

## Symptom

The user installed a patched NewX build and the app crashed while `MainActivity` composed the
bottom navigation bar:

```text
java.lang.VerifyError: Verifier rejected class com.x.android.main.vn:
java.lang.Object com.x.android.main.vn.invoke(java.lang.Object, java.lang.Object) failed to verify:
[0x9B] instance field access on object that has non-reference type IntegerConstant
    at com.x.android.main.MainActivity.G(Unknown Source:250)
```

## Cause

The patch substituted the navigation item icon/label at the convergence point before the label
resource is converted to a string. The original 12.25.2 body keeps `this` in `p0` up to that point,
but the newer body reuses the `this` parameter register as the label resource register:

```text
:pswitch_13
const p0, 0x7f140622
iget-object p2, p0, Lcom/x/android/main/vn;->c:Ljava/lang/Object;   # p0 is now an int
```

The injected `iget-object` therefore read the item tab from an integer register, and the verifier
rejected the method. This is not an R8 rename: the class, field, and renderer all resolve, but the
register lifetime at the injection edge changed.

## Fix

`resolveNavBarItemContent` now clones the content method with
`cloneMutable(additionalRegisters = numberOfParameterRegisters + 1)` and preserves the receiver in
the added local at method entry. The convergence code reads the tab from the preserved register
instead of `p0`, so it no longer depends on parameter-register reuse at that edge. The
`cloneMutable` copies keep the original absolute parameter references valid, and the new local is
excluded from the scratch-register allocator.

## Evidence

Emitted patched 12.27.0-alpha.01 before the fix (invalid):

```text
[009b] iget-object p2, p0, Lcom/x/android/main/vn;->c:Ljava/lang/Object;
[009d] invoke-static p2, v0, NavBarReplacement->overrideIcon(...)
```

Emitted patched 12.27.0-alpha.01 after the fix (verifier-clean):

```text
[0000] move-object/from16 v9, p0          # preserved receiver, added local
[00a3] iget-object v8, v9, Lcom/x/android/main/vn;->c:Ljava/lang/Object;
[00a5] invoke-static v8, v0, NavBarReplacement->overrideIcon(...)
```

`dexscope verify-method` on every method that references `NavBarReplacement` reports
`INCONCLUSIVE` (no verifier error, reference-assignability pass unmodeled) for
12.25.0-alpha.01, 12.26.0-alpha.01/02/03, 12.27.0-alpha.01, and 12.25.2-prod.01 (`12.25.2` base
from the APKM). The pre-existing `Lads_mobile_sdk/se;->f` invalid method reported by
`verify-diff` is also invalid in the unpatched APK.

## Cause classification

- [ ] linter behavior
- [ ] cardinality-helper behavior
- [x] resolver logic (receiver register lifetime at the injection edge)
- [x] APK contract drift (rebuild changed which register holds `this` at the convergence point)
- [ ] tooling or artifact setup
- [x] runtime behavior (device `VerifyError`)
