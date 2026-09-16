# NewX inline download renders a share icon on profile timelines

- Date: 2026-09-13
- Reporter/session: user report with profile-timeline screenshots; Codex repair session
- APK package/version/build type: `com.twitter.android` 12.25.0-alpha.01 alpha, version code `312250201`; compatibility checked through 12.26.0-alpha.03
- APK path and checksum: `apks/twitter_12.25.0-alpha.01.apks` / `c0cb3ca14b44ccddad44620125692490efc963335a83e115c8d3ff8d862778a8`; extracted base `apks/twitter_12.25.0-alpha.01.apk` / `18bd4bdf92e6c32cf4c03d688a705d62045d5fe2c4752cd3f8b8e984566781c4`
- Source commit: `3da9efdbe81d02dabf5c367726069878ab476742` plus working-tree inline icon redesign
- MPP path/checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` / `d68d8aa4a0c9218fa14220bbb3c7e0d6be20e064dad7d8ca6704018dc2f9b500`
- Extension artifact path/checksum: `extensions/newx/build/morphe/extensions/newx.mpe` / `b7014093238c4d04cb6507b40e682b31bccff7796ed632034751f3a6f9b1e36d`
- Output artifact path/checksum: `/tmp/piko-inline-download-12.25.0-alpha.01-from-apks.apk` / `12fbf4102c2d64e787f9bbb73078a023bf38c26c9ccfef250a2d0082ce0e1e25`
- Command: `java -jar ../piko/morphe-desktop-1.11.0-all.jar patch -p patches/build/libs/patches-3.9.0-dev.4.mpp --keystore Morphe.keystore --exclusive -e "NewX: Inline download button" --striplibs=arm64-v8a --force -o /tmp/piko-inline-download-12.25.0-alpha.01-from-apks.apk -- apks/twitter_12.25.0-alpha.01.apks`
- Failing patch: `NewX: Inline download button`
- Severity: high
- Confidence: confirmed statically; repaired runtime path awaits user-driven device confirmation

## Symptom

After scrolling a profile media timeline, the injected download slot displayed a native curved
share arrow instead of the download tray icon. The action still occupied the appended slot and a
prior screenshot showed the expected tray icon in the same position.

## Complete error

No patch-time exception. This was a runtime rendering failure reported with screenshots.

## Reproduction

The report occurs while browsing profile posts after continued scrolling. The supplied
12.25.0-alpha.01 APK was inspected with `dexscope`; no device was launched or controlled during
this session.

Exact DEX tracing showed that injected entries carry `TwitterShare`, which maps through switch
value 11 to synthetic lambda discriminator 2 and `ic_vector_share`. The separate native `Share`
action maps through switch value 12 to discriminator 3 and `ic_vector_share_android`.

## Cause classification

- [ ] linter behavior
- [ ] cardinality-helper behavior
- [x] resolver logic
- [ ] APK contract drift
- [ ] tooling or artifact setup
- [x] runtime behavior
- [ ] unrelated

## Isolation comparison

The linter and cardinality helpers were unchanged and passed before and after the repair. The old
patch applied successfully, so the failure was not candidate discovery. Its runtime contract used
a capped weak action set plus thread-local and remembered-renderer state to reconstruct identity
across Compose calls. Static DEX analysis also disproved the working-tree theory that profiles send
the injected action through `ic_vector_share_android`; that field belongs to another action type.

The rebuilt patch applies only to `ic_vector_share` and carries classification in the icon lambda's
captured size sign. This removes all dependency on parent/lambda call timing. A failed parallel
12.25 production validation was separately reproduced as a Morphe temporary-directory collision;
the same command succeeded when rerun serially and is not attributed to this patch.

## Fix and validation

- Removed the renderer thread-local, remembered-renderer weak set, constructor hook, epsilon,
  renderer cleanup/catch handler, and `ic_vector_share_android` hook.
- Removed the action-set cap; cleared weak keys are drained through a `ReferenceQueue`.
- Download actions capture `-abs(size)`; native actions capture the original positive size.
- The one `TwitterShare` branch normalizes with `abs(size)` immediately before layout and selects
  the download icon from the raw captured sign at the final icon load.
- `./gradlew :extensions:newx:test :patches:test :patches:lintNewxResolvers` passed.
- `./gradlew :patches:build` passed.
- Exclusive patching applied and saved for 12.24.0-prod.02, 12.25.0-alpha.01,
  12.25.0-prod.01, and 12.26.0-alpha.03.
- `dexscope` found exactly one `markIconSize`, one `displayIconSize`, and one `selectIcon` caller
  in each output; `ic_vector_share_android` remained unmodified.

Runtime confirmation is intentionally deferred to normal user operation under the repository's
device-safety rule.

## Fixture or test added

No linter fixture was needed because the linter and cardinality helpers were not involved. The
existing `InlineDownloadButtonTest` suite now covers sign-tag persistence, native/download slot
flips, identity semantics, survival after 2,000 registrations, and the disabled path.

## Lessons

Do not reconstruct a Compose child's identity from temporal parent state when the child can carry
an explicit discriminator. Trace enum-to-renderer mappings before broadening icon hooks: adjacent
share glyphs can belong to distinct native actions even when they share a synthetic lambda class.
