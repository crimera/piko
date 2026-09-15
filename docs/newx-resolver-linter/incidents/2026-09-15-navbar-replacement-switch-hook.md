# NewX navigation bar replacement hook skipped by the label switch

- Date: 2026-09-15
- Reporter/session: user device test; pi repair session
- APK package/version/build type: `com.twitter.android` NewX (reproduced statically on
  12.25.0-alpha.01, 12.25.2-prod.01, 12.26.0-alpha.01/02/03, 12.27.0-alpha.01)
- Source commit: working tree after the settings-group and icon-picker changes
- Failing patch: `NewX: Replace navigation bar item` (`replaceNewXNavBarItemPatch`)
- Severity: medium (patch applies and does not crash, but the replacement does nothing for most
  tabs)
- Confidence: high; root cause proven from the emitted bytecode

## Symptom

Replacing the Grok navigation bar item with Bookmarks left both the Grok icon and the Grok label in
place. Changing the replacement icon between "Destination icon" and "Bookmarks" had no effect.

## Cause

The override block was inserted before the label resource conversion instruction:

```text
:goto_3
[00a3] invoke-static v7, v6, errorhandler/a;->P(Composer;I)String
[00a7] iget-object v8, v9, vn;->c          # injected override starts here
```

The label packed switch's cases `goto :goto_3`, and `:goto_3` is attached to the conversion
instruction. Inserting instructions before an instruction keeps existing branches pointing at the
original instruction, so every case except the fall-through case jumped over the whole override
block. Only the first label case (Home) executed it.

## Fix

The override hook now sits at the icon/label renderer call, after the conversion, and replaces the
localized label string instead of the label resource id:

```text
:goto_3
[00a3] invoke-static v7, v6, errorhandler/a;->P(Composer;I)String
[00a6] move-result-object v6
[00a7] iget-object v8, v9, vn;->c
[00a9] invoke-static v8, v0, NavBarReplacement->overrideIcon(...)
[00ad] check-cast v8, Lcom/x/icons/b;
[00af] move-object/from16 v0, v8
[00b1] iget-object v8, v9, vn;->c
[00b3] invoke-static v8, v6, NavBarReplacement->overrideLabel(...)
[00b6] move-result-object v6
[00b7] invoke-static v0, v6, v1, v7, v5, n0;->e(...)
```

`overrideLabel` resolves the destination's localized title from its registered resource id with
`Utils.getContext().getString(id)`, so no resource-id override and no switch-relative insertion are
needed.

## Evidence

`dexscope verify-method` on every method that references `NavBarReplacement` reports
`INCONCLUSIVE` (no verifier error) for 12.25.0-alpha.01, 12.25.2-prod.01, 12.26.0-alpha.01/02/03,
and 12.27.0-alpha.01.

## Cause classification

- [ ] linter behavior
- [ ] cardinality-helper behavior
- [x] resolver logic (insertion edge relative to an existing switch jump target)
- [ ] APK contract drift
- [ ] tooling or artifact setup
- [x] runtime behavior (replacement inert for most tabs)
