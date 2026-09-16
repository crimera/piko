# NewX thumbnail cache routing

Internal note: the NewX media renderer contains both Coil and Glide paths. The
library names alone are misleading because `Lcom/bumptech/glide/e;` is also used
as an app-side Compose integration shim for Coil requests.

## Current routing

`newXThumbnailCachePatch` resolves the target APK's Glide cache shape instead of
routing by version. When that shape is available, the primary
`MediaThumbnailLoader.getCachedThumbnail(Object, String)` helper checks Glide
and falls through to a separately patched Coil helper. This keeps the cache
lookup correct if the server-side renderer switch changes after patching.

Targets without the recognized Glide cache shape keep the original Coil-only
helper. The two bridges are never inserted into the same method.

## Why the old Coil hook worked

The media renderer checks `Lcom/x/media/imageloader/b;->isEnabled()Z`. In the
production-shaped path, the false branch builds a `Lcoil3/request/f` request
and sends it through the misleading `Lcom/bumptech/glide/e;` wrapper. The
Coil-memory-cache bridge therefore sees the entries used by the visible
thumbnail renderer.

The feature implementation reads the remote/configurable switch
`x_lite_infra_glide_enabled`, whose bytecode default is false. The alpha can
run the true branch, which calls `Lcom/x/ui/common/glide/c;` and populates
Glide's engine caches. Coil remains packaged for fallback and other surfaces,
but it is not the cache owner while that branch is active.

## Glide bridge behavior

The Glide bridge resolves the provider, engine, cache fields, key model field,
resource interface, active-resource map, and memory-entry type from the target
APK. It then scans a snapshot of the memory cache and active-resource map for a
key containing the requested cache URL, unwraps the resource, converts a
`BitmapDrawable`/`Drawable` to a `Bitmap`, and returns it. A null result leaves
the existing HTTP network fallback unchanged.

The memory-entry class is anchored to the engine's
`Map.remove(Object) -> Object -> CHECK_CAST` sequence and then shape-validated;
it is not selected by an obfuscated class name.

## Files

- `patches/src/main/kotlin/app/crimera/patches/newx/misc/inlineactions/ThumbnailCachePatch.kt`
  owns the temporary route selection.
- `GlideThumbnailCachePatch.kt` and `CoilThumbnailCachePatch.kt` contain the
  two bridge implementations.
- `extensions/newx/.../MediaThumbnailLoader.java` contains the shared helper,
  conversion, and diagnostics.

## Known limitation

The Glide resolver is still tied to the currently recognized engine/cache
relationships. A future contract change that invalidates those relationships
must fail or add a validated capability shape; it must not reintroduce a
version-based route.
