# Restore Twitter branding: launch reveal parameter order

Reported failure: `NewX: Restore Twitter branding` depends on `NewX: Restore Twitter splash transition`, which throws
`app.morphe.patcher.patch.PatchException: Expected exactly one NewX launch reveal renderer after marker, found 0: []`
at `resolveLaunchRevealRenderer(RestoreTwitterBrandingPatch.kt:46)`, called from the launch-reveal patch execute block (line 190 in the reported source).

Source baseline: `2add2633e6f99859483493c2c20972e5735d0155`, with local unrelated timeline changes preserved.
The user's MPP/extension provenance was not supplied. Local verification uses the current checkout's
`patches/build/libs/patches-3.11.0-dev.1.mpp` and bundled extension rebuilt with
`./gradlew :patches:build :patches:lintNewxResolvers --no-daemon`.

## Bytecode evidence and cause

Package `com.twitter.android`; exact local targets:

| APK | Version code | Renderer |
| --- | --- | --- |
| `apks/12.38.0-prod.01.apk` | 312380001 | `Landroidx/camera/camera2/internal/u0;->w(ILai/x/commerce/webview/f4;)V` |
| `apks/12.38.1-prod.01.apk` | 312381001 | `Landroidx/camera/camera2/internal/u0;->w(ILcom/airbnb/lottie/network/c;)V` |
| `apks/12.39.0-alpha.03.apk` | 312390203 | `Lai/x/grok/conversation/ui/m;->A(Landroidx/camera/video/q0;I)V` |

Each APK has exactly one caller of framework `AttachedSurfaceControl.buildReparentTransaction`.
12.38 production has the same SurfaceControl reveal work with its two parameters reversed.
The old renderer and callback predicates required `(object, int)`; fallback lookup also assumed
the object was the first parameter. Classification: resolver logic exposed by parameter-order
drift. No linter or cardinality-helper changes are involved; the existing shape predicate rejects
the exact production signature directly, so this is not an attribution based on timing.

In 12.38.0, callback `Lcom/x/android/main/launchreveal/a;->d(Lai/x/commerce/webview/f4;)V`
calls renderer `w(int, object)` at instruction 25, and the same owner's fallback `k(object)` at
instruction 27. The fallback performs `View.animate`, `alpha(0)`, `setDuration(150)`, and
`withEndAction`. These instructions establish the intended normal dismiss path.

The repair accepts exactly one object and one int in either order, retains both SurfaceControl
anchors and exact renderer/callback/fallback cardinalities, and derives the fallback object's
descriptor from the validated shape. No obfuscated owner or version routing is introduced.
The emitted hook remains an entry `invoke-static fallback(p1); return-void`, with branch targets
relocated and structured labels preserved. Marker absence retains the pre-existing optional path.

The existing launch-reveal fixture in `NewXResolverLinterTest` now covers both argument orders,
invalid parameter types, missing framework operations, and mismatched callback object types.
The first repair's `objectTypes.size == 1 && objectTypes.single()` triggered a conservative
`raw-single` false positive despite its short-circuit guard. The fixture corpus records the current
expected finding; production instead counts matching descriptors directly. The library is unchanged.

## Validation

No agent device interaction was performed. The user subsequently confirmed the patched build works.
An initial overlapping pair of Gradle runs collided in Kotlin incremental caches. A sequential
`:patches:clean :patches:build` completed successfully (including all 68 resolver fixtures), before
the linter identified the guarded-single issue above. The final predicate is rebuilt below.

Final `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon` passed,
including the full patch test suite and resolver linter (zero findings).
All three commands exited 0 using the freshly built MPP and its bundled extensions:

| Command | Result and output |
| --- | --- |
| `PATCH_OUTPUT_APK=/tmp/piko-branding-12.38.0-prod.01.apk ./patch-twitter.sh apks/12.38.0-prod.01.apk` | Full selected patch set applied, saved to `/tmp/piko-branding-12.38.0-prod.01.apk` |
| `PATCH_OUTPUT_APK=/tmp/piko-branding-12.38.1-prod.01.apk ./patch-twitter.sh apks/12.38.1-prod.01.apk` | Full selected patch set applied, saved to `/tmp/piko-branding-12.38.1-prod.01.apk` |
| `PATCH_OUTPUT_APK=/tmp/piko-branding-12.39.0-alpha.03.apk ./patch-twitter.sh apks/12.39.0-alpha.03.apk` | Full selected patch set applied, saved to `/tmp/piko-branding-12.39.0-alpha.03.apk` |

Reports include `Applied: NewX: Restore Twitter branding` (with its splash dependency completed)
and `Applied: NewX: Remove share tracking parameters`.
Logs: `/tmp/piko-branding-12.38.0.log`, `/tmp/piko-branding-12.38.1.log`,
`/tmp/piko-branding-12.39.0.log`; final build log `/tmp/piko-branding-build-final.log`.
