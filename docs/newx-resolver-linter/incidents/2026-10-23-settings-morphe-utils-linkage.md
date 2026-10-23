# Settings screen crash: Morphe compile/runtime utility mismatch

## Target and provenance

- Device report: POCO 24069PC21G, Android 16 / API 36.
- App: `com.twitter.android`, `12.36.1-prod.01`, version code `312361001`, production.
- Input APK: `apks/12.36.1-prod.01.apk`.
- Patch version: `3.11.0-dev.1`; source base
  `c3c98b3634ce552879fb4c54dc5baead24be1b9e`, plus the local inline
  timeline-resume factory adapter and its fixtures.
- Bundle: `patches/build/libs/patches-3.11.0-dev.1.mpp`, rebuilt from this checkout.
- Shared extension: Gradle-built current sources, including the composite
  `../piko-patches-library` checkout at `d74e678f728b22006b9d5aa0b56c977043de3068`.
- CLI: `../piko/morphe-desktop-1.19.0-all.jar`, via `./patch-twitter.sh`.
- Reproduction command: `./patch-twitter.sh apks/12.36.1-prod.01.apk`.
- Patch application succeeds; user-reported failure occurs when opening Piko settings.

## Reported error

```text
java.lang.NoSuchMethodError: No static method setPreferenceTitlesToMultiLineIfNeeded(Landroid/preference/PreferenceGroup;)V in class Lapp/morphe/extension/shared/Utils; or its super classes (declaration of 'app.morphe.extension.shared.Utils' appears in /data/app/~~xkuRtrwZvcDeD-R_pboemQ==/com.twitter.android-O3Ii8zlIViqmBr7CjN5q9w==/base.apk!classes17.dex)
    at app.morphe.extension.crimera.settings.SettingsRenderer.render(SettingsRenderer.java:53)
    at app.morphe.extension.crimera.settings.PikoSettingsFragment.renderRoot(PikoSettingsFragment.java:110)
    at app.morphe.extension.crimera.settings.PikoSettingsFragment.onCreate(PikoSettingsFragment.java:51)
    at android.app.Fragment.performCreate(Fragment.java:2491)
    at android.app.FragmentManagerImpl.moveToState(FragmentManager.java:1262)
    at android.app.FragmentManagerImpl.addAddedFragments(FragmentManager.java:2432)
    at android.app.FragmentManagerImpl.executeOpsTogether(FragmentManager.java:2211)
    at android.app.FragmentManagerImpl.removeRedundantOperationsAndExecute(FragmentManager.java:2167)
    at android.app.FragmentManagerImpl.execPendingActions(FragmentManager.java:2068)
    at android.app.FragmentManagerImpl.dispatchMoveToState(FragmentManager.java:3058)
    at android.app.FragmentManagerImpl.dispatchActivityCreated(FragmentManager.java:3005)
    at android.app.FragmentController.dispatchActivityCreated(FragmentController.java:185)
    at android.app.Activity.performCreate(Activity.java:9315)
    at android.app.Activity.performCreate(Activity.java:9284)
    at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1545)
    at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4481)
    at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4700)
    at android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:224)
    at android.app.servertransaction.TransactionExecutor.executeNonLifecycleItem(TransactionExecutor.java:133)
    at android.app.servertransaction.TransactionExecutor.executeTransactionItems(TransactionExecutor.java:103)
    at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:80)
    at android.app.ActivityThread$H.handleMessage(ActivityThread.java:2962)
    at android.os.Handler.dispatchMessage(Handler.java:132)
    at android.os.Looper.dispatchMessage(Looper.java:333)
    at android.os.Looper.loopOnce(Looper.java:263)
    at android.os.Looper.loop(Looper.java:367)
    at android.app.ActivityThread.main(ActivityThread.java:9298)
    at java.lang.reflect.Method.invoke(Native Method)
    at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:569)
    at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:929)
```

## Cause and isolation

Classification: runtime library API mismatch / build dependency contract. Not a
NewX fingerprint, linter, cardinality-helper or timeline-resume algorithm failure.

`piko-patches-library/extension` compiled against Morphe extensions **1.6.0**.
The consuming shared bundle packages **2.0.1-dev.1**. Its actual `Utils` DEX
defines `setPreferenceTitlesToMultiLine(PreferenceGroup)V`, not the old
`setPreferenceTitlesToMultiLineIfNeeded(PreferenceGroup)V`. The old dependency
allows Java compilation to succeed while leaving an unresolved static invoke.
The same call exists in root, group and search-results rendering.

The failing runtime definition was inspected in the generated 12.36 APK before
editing. No device-control reproduction or reverted-library runtime test was
performed. Confidence in the missing-symbol diagnosis is high from the exact
reported descriptor and packaged class declaration; runtime acceptance remains
pending. No linter/cardinality-helper changes are involved.

## Fix

In the owning library, update all three calls to the new API and align both
compileOnly and test-runtime dependencies with Morphe `2.0.1-dev.1`. Bump local
library version to `0.10.0-dev.2`; update this repository's catalog accordingly.
This is a local composite build: the library changes/version are not yet
committed or published and must be released before non-composite consumers use
that version.

Add a packaged-DEX linkage invariant to `SettingsAggregateValidationTest`. It
resolves settings static calls against the actual shared runtime bundled in
`extensions/shared.mpe`, rather than trusting independent compile classpaths.
It would reject the missing old utility method in the reported build. No
one-off literal-name assertion or new test file is needed.

## Validation

- `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon`: passed,
  including the packaged-settings linkage regression.
- `./gradlew :piko-patches-library:extension:testDebugUnitTest :piko-patches-library:extension:lintRelease --no-daemon`:
  passed. Release unit-test task does not exist for this library; Debug is its
  enabled host-test variant.
- `./patch-twitter.sh apks/12.35.0-prod.01.apk`: all 46 selected patches apply;
  APK saved, copied to `/tmp/piko-settings-api-12.35.apk`.
- `./patch-twitter.sh apks/12.36.1-prod.01.apk`: all 46 selected patches apply;
  final output `/Users/steven/Downloads/piko-twitter-patched.apk`.
- Final DEX cross-reference confirms root, group and search-results renderers
  all invoke the existing `Utils.setPreferenceTitlesToMultiLine` definition.
- No installation or device interaction by the agent. Ask the user to open
  root settings, a category and settings search before retrying timeline restore.
