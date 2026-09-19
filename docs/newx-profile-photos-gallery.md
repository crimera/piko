# NewX profile Photos gallery

## What it does

The profile **Photos** tab normally renders the same vertical URT post cards as
Posts/Replies. This feature replaces only the Photos body with a three-column
square thumbnail grid. Tapping a thumbnail uses the existing NewX item-click
path and opens the selected photo in X's native viewer.

- `NewX: Set default media tab` selects Photos by default.
- `NewX: Gallery profile Photos tab` supplies the native grid.
- The feature is controlled by
  `newx.post_actions_media.gallery_profile_photos`.
- Non-Photos timelines fall through to the original Compose implementation.
- Reaching the bottom requests the next URT page through the resolved paginator.

## Files

- `patches/.../misc/mediatab/ProfilePhotosGalleryPatch.kt` — semantic
  fingerprint, patch-time contract resolution, guard injection, and settings
  registration.
- `extensions/newx/.../misc/ProfilePhotosGallery.java` — AndroidView bridge,
  grid layout, thumbnail loading, item-click routing, and pagination.
- `extensions/newx/.../ui/LoadingIndicatorView.java` — themed pagination
  spinner.

## Patch-time resolver contract

The patch does not route on release versions or obfuscated member names. It
resolves the contracts from the target APK and fails closed on zero or
ambiguous matches:

- **Timeline selector:** the direct enum under the timeline-model scope that
  declares the static self-typed `USER_PROFILE_PHOTOS` field.
- **Timeline list:** the immutable-list interface extending `java.util.List`
  with exactly one `subList(Int, Int)` method returning itself.
- **PaddingValues:** the Compose interface with two no-argument `Float`
  accessors and two layout-direction `Float` accessors. The implementation is
  identified by its four-side `Float` constructor and parameter-to-field
  writes; top and bottom accessors are derived from those fields.
- **AndroidView:** the unique static `void` method in the view-interop scope
  whose ordered parameters contain factory `Function1`, `Modifier`, update
  `Function1`, `Composer`, and trailing integer flags.
- **Timeline body:** a static `void` URT UI method with one immutable timeline
  list, one timeline enum, one `Modifier`, one `Composer`, and the expected
  content-lambda constructor shape. The owner, method, lambda, and descriptor
  names are not anchors.
- **Paging event:** resolves the unique bottom-paginator dispatch method and
  its event interface, then identifies the request-event class from the
  dispatch method's `instance-of` branch and its reference-plus-two-integer
  constructor. The patched `createPagingEvent()` constructs that request
  event with null analytics metadata and zero counters; the singleton event
  branch is telemetry-only and is not used for loading pages.
  The patch execute block resolves this contract and invokes
  `patchPagingEventBridge` before emitting the gallery and native-viewer changes.
- **Native viewer bridge:** post action, media, identifier, route, and
  navigation contracts are resolved from method/field shapes before smali is
  emitted.

All required matches use `requireExactlyOne`; optional alternatives use
`requireAtMostOne` only where the contract permits absence.

## Injected guard shape

The emitted instructions are equivalent to:

```text
if timelineType != <resolved USER_PROFILE_PHOTOS field> -> original
if !ProfilePhotosGallery.isEnabled() -> original

factory = ProfilePhotosGallery.createFactory(
    items,
    itemRendererCallback,
    itemClickCallback,
    <resolved top padding>,
    <resolved bottom padding>,
)
updater = ProfilePhotosGallery.createUpdater(
    items,
    itemRendererCallback,
    itemClickCallback,
    <resolved top padding>,
    <resolved bottom padding>,
)
<resolved AndroidView>(factory, modifier, updater, composer, zero flags)
return
```

Parameter registers are derived from the matched method's descriptor and
register count. Wide parameter moves use the appropriate `/from16` form, and
the AndroidView flag count comes from the resolved method signature.

## Runtime bridge and pagination

`createFactory` and `createUpdater` return explicit `Function1`
implementations (`GalleryFactory`, `GalleryUpdater`) rather than Java lambda
classes. The updater returns `null` and incrementally updates the existing
grid.

The extension does not call obfuscated method names or enumerate minified
event classes:

- The patched `createPagingEvent()` bridge constructs the resolved request
  event with null analytics metadata and zero counters; it does not use the
  telemetry-only singleton event.
- The callback's fields and methods are searched for a component exposing a
  no-argument getter or field whose type has a non-static `void` dispatch
  method accepting that event instance.
- Declared interface return types are valid; the resolved runtime value is checked
  again by its concrete class and implemented interfaces before dispatch.
- Multiple concrete paginator implementations can share the same interface; the patch-time
  resolver injects the exact bottom-paginator class name, and runtime selection rejects the
  top-paginator instance before dispatch.
- The paginator's zero-argument boolean termination method and event dispatch
  method are selected by signature and runtime assignability.
- Failure to resolve any contract returns `false` and preserves the original
  path.

### Diagnostics

When `newx.advanced.debug_tools.logging` is enabled, pagination diagnostics use
the `[PikoNewX][PhotosGallery][Pagination]` prefix. The useful Android filter is:

```sh
adb logcat -d -v threadtime -e 'PhotosGallery|NewXLogger' -s morphe:I '*:S'
```

The messages identify the callback, request-event class, resolved URT component,
paginator getter or field, termination state, dispatch invocation, and item-count
updates without logging URLs, cursors, or response payloads.

The grid is a `NestedScrollView` around a three-column `ViewGroup`. It keeps
source timeline items and one-based photo indices, appends new cells without
resetting scroll, and shows the themed loading indicator while a page request
is in flight.

## Native viewer routing

Each cell stores its source item and zero-based media index. The existing
timeline item-click callback receives the tap event as a best-effort native
viewer request; once that callback returns, `openPhoto` continues to the
existing URL/image fallback so a no-op or failed native bridge cannot swallow
the click. The patched post-media handler consumes a short-lived post-ID/index
request, builds the existing single-media destination, and navigates through
the app's controller. Invalid or stale requests clear the pending state and
preserve the original event path.

## Resilience lessons

1. Compose `AndroidView` callbacks use explicit `Function1` classes to avoid
   synthetic-lambda `invoke` incompatibilities.
2. The updater returns `null` instead of referencing `kotlin.Unit` from the
   host runtime.
3. Standalone native scrolling bypasses Compose `LazyListState`, so pagination
   is dispatched through the resolved URT bottom-paginator event contract.

## Validation

```bash
./gradlew :patches:lintNewxResolvers --no-daemon
./gradlew :extensions:newx:compileReleaseJavaWithJavac --no-daemon --rerun-tasks
./gradlew :patches:build --no-daemon
./patch-twitter.sh ./apks/12.27.0-prod.01.apk
./patch-twitter.sh ./apks/12.28.0-alpha.01.apk
dexscope verify-diff apks/12.27.0-prod.01.apk \
  /Users/steven/Downloads/piko-twitter-patched-prod.apk
dexscope verify-diff apks/12.28.0-alpha.01.apk \
  /Users/steven/Downloads/piko-twitter-patched.apk
```

Both APKs applied `NewX: Gallery profile Photos tab`. DEX verification
reported `0 invalid` methods for both outputs (the verifier marked unrelated
methods inconclusive where its model is intentionally incomplete). Device
execution was not performed; repository rules prohibit adb/device control
without explicit permission.
