# Piko row absent from redesigned settings despite successful patching

## Report / artifacts

- Package/version/build: `com.twitter.android`, `12.36.1-prod.01`, production.
- APK: `apks/12.36.1-prod.01.apk`; screenshot supplied as
  `/Users/steven/.cache/termux-ghostty/184601.png`.
- Symptom: root Compose settings screen still shows **Additional resources**;
  no Piko row. No exception. Patch-time success is not runtime coverage.
- Source: `c3c98b3634ce552879fb4c54dc5baead24be1b9e` plus current working-tree
  timeline-resume adapter, shared-settings API migration and this resolver repair.
- MPP: freshly built `patches/build/libs/patches-3.11.0-dev.1.mpp`.
- Extensions: current Gradle outputs; composite `../piko-patches-library` at
  `d74e678f728b22006b9d5aa0b56c977043de3068` plus local API migration / version
  `0.10.0-dev.2`. Morphe extensions `2.0.1-dev.1`. No stale extension substitution.
- Command: `./patch-twitter.sh apks/12.36.1-prod.01.apk`.
- Affected dependency: `newXSettingsPatch`, model-constructor branch in
  `SettingsRowModelHook.kt`. All 46 selected public patches report Applied.

## Classification / evidence

Resolver logic / APK compiler-contract drift, not a linter or cardinality-helper
failure. This is not missing new-settings support: commit `f834ae94` already added
that support for 12.33 alpha.02. Its discovery assumptions stopped covering the
new builder, while the independently matched legacy renderer still patched.

The old model resolver only searched zero-argument methods returning `Object`.
On 12.36 the real root builder is:

```text
Lcom/x/settings/i;->c()Ljava/util/List;
0x220 const v5, 0x7f131b44  # settings_additional_resources_item_title
0x223 invoke-direct v0,v5, ResourceText.<init>(I)V
0x23b move-object/from16 v26,v0
0x23f invoke-direct/range {v25..v34}, Row.<init>(Text,Text,Icon,Auxiliary,Z,Function0,Destination,Menu,I)V
```

Exact title model: `Lcom/x/models/fd;`, superclass `gd`, resource field `b:I`.
Exact row constructor: `Lcom/x/settings/common/s;`, icon slot 2, click slot 5,
default-mask slot 8. These identities/offsets are evidence, never resolver anchors.
The `Object`-returning `settings/common/d0.invoke()` still builds legacy rows,
whose constructors lack the redesigned model's boolean role. Discovering that
method does not cover the visible new list.

Baseline source inspection proves the live builder is excluded before semantic
resolution and the nullable optional path silently retains only the renderer
hook. No reverted-MPP device reproduction was performed; user screenshot is the
runtime evidence. Linter/helper revisions were unchanged for this repair.

## Fix / safeguards

- Use the indexed Additional-resources resource literal without return-type,
  parameter-count or owner-package restrictions.
- Trace the literal into the `(I)V` resource-text constructor, then trace its
  receiver through object moves into the row constructor's title argument.
- Do not select unrelated constructors merely because they occur after the title.
- Keep the existing model hook, descriptor derivation, title discriminator and
  default-mask handling. Nonmatching titles keep the original path.
- Reject wide constructor parameters: parameter-slot indexes must equal register
  offsets for the existing mutation contract.
- Retain the legacy renderer hook. Optional model absence remains valid for
  targets genuinely using only the old renderer.

An initial development narrowing to `com/x/settings/` was disproven by the alpha
regression: 12.33 alpha.02's builder is R8-merged into
`com/x/notifications/devicefollow/d.invoke()Object`. Patching succeeded but final
DEX had zero model-hook references. Removing that scope restores one model hook.
This was a resolver false negative, not linter behavior. The fixture uses a
repackaged owner and covers Object/List return forms, title aliases, constructor
distance greater than the old four-instruction window, an unrelated later row,
and an overwritten-title negative case through the real method-analysis entry.

## Validation

- `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon`: passed,
  including the added fixture in `NewXResolverLinterTest`.
- `./patch-twitter.sh apks/12.35.0-prod.01.apk`: 46 applied, saved. Copy:
  `/tmp/piko-compose-settings-12.35.apk`.
- `./patch-twitter.sh apks/12.33.0-alpha.02.apk -d 'NewX: Restore timeline position'`:
  45 applied, saved. Restore disabled because of its separately logged resume-model
  incompatibility, not to hide a settings failure. Copy:
  `/tmp/piko-compose-settings-alpha.apk`.
- `./patch-twitter.sh apks/12.36.1-prod.01.apk`: all 46 applied, saved to
  `/Users/steven/Downloads/piko-twitter-patched.apk`; this is the final artifact
  after regression runs, with no patches disabled and no continue-on-error.
- Final DEX has exactly one `getSettingsTitleResId()` model-hook call in each
  artifact: row constructors `s` (12.36), `t` (12.35), `p` (12.33 alpha).
- 12.36 hook checks the original resource ID, writes Piko title/icon/click, and
  clears mask bits `0x25` (`and-int/lit8 p9,p9,-0x26`). Original defaulting code
  cannot null out the overridden click/icon. Other model titles branch straight
  to original initialization. Source-method index-0 sound proof: `v0,v1` safe;
  the existing typed emitter uses those two locals without touching `this`.
- No device control or installation by the agent. Pending user acceptance:
  Piko row visible and clickable on the redesigned screen; unrelated root rows
  still navigate normally; legacy entry remains usable.

## User acceptance

After installing the repaired build, the user reported "seems to be working fine".
This confirms acceptance of the reported settings-entry repair; individual legacy,
unrelated-row and timeline-restore control paths were not separately reported.
