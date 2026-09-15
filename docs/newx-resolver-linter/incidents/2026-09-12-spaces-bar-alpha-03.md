# NewX Spaces bar resolver drift: 12.26.0-alpha.03

- Date: 2026-09-12
- Reporter/session: user report; pi repair session
- APK package/version/build type: `com.twitter.android` 12.26.0-alpha.03 alpha, version code `312260203`
- APK path and checksum: `../twitter-analysis/apks/twitter_12.26.0-alpha.03.apk` / `4a5bc95c8b6a9cd1ceb6e159333943b11e094ecf168153f14decb14d697ccf5e`
- Source commit: `baf3189040abdfddb40cb76ecffd233846d8e995` plus working-tree Spaces resolver fix
- MPP path/checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` / `8c4c975537100be595bc11a0f01d56fc536ea09f584dc77037abd0f6a357716a`
- Extension artifact paths/checksums: `/Volumes/realme/Dev/piko/morphe-data/extensions/newx.mpe` / `285745207b89d20048f3b4548f986df3dd35e3d04a0c0b991189ef665f2c8163`
- Output artifact path/checksum: `/tmp/piko-spaces-alpha03-fixed.apk` / `650c25f467d02aa3892092ee1ed56554dc3401c3ce88fdad66dbea159193f470`
- Command: `java -jar ../piko/morphe-desktop-1.11.0-all.jar patch -p patches/build/libs/patches-3.9.0-dev.4.mpp --keystore Morphe.keystore --exclusive -e 'NewX: Hide Spaces bar' --force -o /tmp/piko-spaces-alpha03-fixed.apk -- ../twitter-analysis/apks/twitter_12.26.0-alpha.03.apk`
- Failing patch: `NewX: Hide Spaces bar`
- Severity: high
- Confidence: confirmed

## Symptom

The Spaces-bar patch aborted before producing an APK because its fingerprint
expected a ten-parameter Compose renderer, while 12.26.0-alpha.03 contains an
eleven-parameter renderer.

## Complete error

```text
SEVERE: FAILED: NewX: Hide Spaces bar
app.morphe.patcher.patch.PatchException: Failed to match the fingerprint: app.crimera.patches.newx.timeline.NewXSpacesBarFingerprint
```

## Reproduction

`dexscope` found the semantic anchors in the target at:

```text
Lcom/x/spaces/ui/home/i;->i(
  Lkotlinx/coroutines/flow/v2;
  Lkotlin/jvm/functions/Function1;
  Landroidx/compose/ui/Modifier;
  Z;
  Lkotlin/jvm/functions/Function1;
  Lkotlin/jvm/functions/Function1;
  Lkotlin/jvm/functions/Function1;
  Lkotlin/jvm/functions/Function0;
  Landroidx/compose/runtime/Composer;
  II
)V
```

The old fingerprint omitted the third `Function1` callback before
`Function0`, so it found zero candidates. The same old fingerprint applied to
12.26.0-alpha.02, whose renderer still has the ten-parameter shape.

## Cause classification

- [ ] linter behavior
- [ ] cardinality-helper behavior
- [x] resolver logic
- [x] APK contract drift
- [ ] tooling or artifact setup
- [ ] runtime behavior

## Isolation comparison

The failure reproduced with the pre-fix MPP against the exact alpha.03 APK.
The linter and cardinality helpers were not involved in candidate discovery;
the failure occurred in the fingerprint's exact parameter-shape match. After
the resolver change, a clean MPP rebuilt from the working tree applied the
patch to alpha.03, alpha.02, alpha.01, and 12.25.0-prod.01.

## Fix and validation

The fingerprint now keeps the Spaces package and semantic string anchors, then
accepts the two observed parameter shapes through one shape resolver. The
shared `requireExactlyOne` helper still fails closed for zero or ambiguous
matches. 12.26.0-alpha.03 was added as an experimental NewX target.

Validation:

- `./gradlew :patches:test :patches:build` — passed
- `./gradlew :patches:lintNewxResolvers` — passed
- alpha.03 patch — applied and saved
- alpha.02 patch — applied and saved
- alpha.01 patch — applied and saved
- 12.25.0-prod.01 patch — applied and saved
- final alpha.03 method contains the settings guard and an early `return-void`

## Fixture or test added

No linter fixture was needed; the linter and cardinality helpers were
unchanged. The real APK matrix is recorded above.

## Lessons

Compose renderer parameter lists can gain callback slots between releases.
Keep exact tested shapes in a shared capability resolver instead of routing by
version or relying on obfuscated owner/method names.
