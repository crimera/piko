# NewX Profile Photos Thumbnail Cache Specification

## Status

Proposed implementation spec for the custom disk cache used by the NewX profile
Photos gallery.

## Current behavior

`ProfilePhotosGallery` delegates thumbnail loading to
`MediaThumbnailLoader.load(Context, cacheUrl, networkUrl, callback)`.

The current lookup order is:

1. Extension in-memory `LruCache` (`8 MiB` maximum).
2. Glide memory/active-resource cache when the target exposes the resolved Glide shape.
3. Coil memory cache fallback.
4. Custom disk cache under `Context.getCacheDir()/piko_media_cache`.
5. Direct HTTP download with `HttpURLConnection`.

The current custom disk cache has no total-size limit, entry limit, TTL, or
eviction. It stores the original response bytes under MD5-derived filenames and
can write both the network URL and cache URL as separate files for one image.
`MAX_DOWNLOAD_BYTES` (`8 MiB`) limits one network response only; it does not
limit total disk usage.

The production bridge currently reads Glide's memory cache and active resources
through the patch-time-resolved bridge. A Glide or Coil disk-cache hit is not
queried directly by the gallery loader.

## Library reference behavior

### Glide

Glide checks active resources, memory cache, resource disk cache, and data disk
cache before fetching from the source. Its default disk implementation is a
fixed-size `DiskLruCacheWrapper` with a default limit of `250 MiB` in the
application cache directory. Remote URLs normally use the automatic strategy,
which stores the original source data.

References:

- <https://bumptech.github.io/glide/doc/caching.html>
- <https://bumptech.github.io/glide/doc/configuration.html#disk-cache>

### Coil 3

Each `ImageLoader` owns a memory cache and a journaled LRU disk cache. The
library's default `DiskCache.Builder` uses `2%` of free space, clamped to a
`10 MiB` minimum and `250 MiB` maximum, unless the application overrides it.
Writes use editor/commit semantics so incomplete entries are not published.

References:

- <https://coil-kt.github.io/coil/image_loaders/#caching>
- <https://raw.githubusercontent.com/coil-kt/coil/main/coil-core/src/commonMain/kotlin/coil3/disk/DiskCache.kt>

These limits protect Glide and Coil's own caches. They do not limit the
extension-owned `piko_media_cache` directory.

## Goals

- Bound the extension's persistent disk usage predictably.
- Preserve cache reuse across gallery visits and process restarts.
- Keep cache I/O off the UI thread.
- Survive concurrent thumbnail requests without publishing partial files.
- Treat all cache failures as recoverable network fallbacks.
- Keep the runtime boundary independent of Glide and Coil internals.
- Preserve API 29 compatibility.

## Non-goals

- Do not reconfigure or clear the application's Glide or Coil caches.
- Do not move media into external storage.
- Do not retain full-resolution media as user-owned downloads.
- Do not add a new runtime dependency on Glide, Coil, or a disk-cache library.
- Do not change the gallery's image rendering or pagination behavior.

## Proposed policy

### Limits

Use fixed conservative limits for the extension-owned cache:

- `MAX_DISK_CACHE_BYTES = 64 MiB` for all committed entries.
- `MAX_DISK_ENTRY_BYTES = 4 MiB` for one downloaded response.
- Keep `MAX_DOWNLOAD_BYTES = 8 MiB` as the independent per-response safety cap.

A response larger than `MAX_DISK_ENTRY_BYTES` is still decoded and displayed,
but is not persisted to disk. The in-memory LRU may retain the decoded bitmap
normally.

The 64 MiB limit intentionally stays below the library defaults because this
cache currently stores original response bytes rather than a compact,
pre-sized thumbnail representation.

### Keying

- Use the network URL as the canonical key for new writes.
- Continue checking both the network URL and cache URL when reading, so existing
  entries remain usable after the change.
- Write only one file for a successful response; do not duplicate bytes under
  both URL keys.
- Keep MD5-derived filenames for compatibility with existing entries. The URL
  itself must never be used as a filename.

### LRU eviction

- On a successful disk-cache hit, update the file's last-modified time.
- After every successful commit, calculate the committed file total and delete
  the oldest entries until the total is at or below `MAX_DISK_CACHE_BYTES`.
- Count only regular cache files; exclude temporary files from the committed
  total.
- Use filename ordering as a deterministic tie-breaker when timestamps match.
- Delete stale `.tmp` files during startup cleanup and before size accounting.
- If a cached file exists but cannot be decoded, delete it and continue to the
  network path.

### Atomic writes and concurrency

1. Download and validate the response off the UI thread.
2. Reject disk persistence when the response exceeds the entry limit.
3. Write to `<key>.tmp` in the cache directory.
4. Flush and close the temporary file.
5. Under a process-local disk-cache lock, rename the temporary file to the final
   key and run LRU trimming.
6. Never expose a temporary or partially written file to readers.

Lookup timestamp updates and eviction must use the same lock as commits. The
lock must cover only filesystem bookkeeping, not network I/O or bitmap decode.

### Startup cleanup

The first disk-cache access in a process schedules one background cleanup pass:

- Create `piko_media_cache` if needed.
- Remove stale temporary files.
- Remove corrupt zero-byte or undecodable entries when encountered.
- Trim pre-existing unbounded entries until the new 64 MiB limit is met.

The cleanup must not run synchronously on the main thread.

## Runtime flow after implementation

```text
Gallery ImageView
    ↓
Extension LruCache
    ↓ miss
Resolved Glide memory/active-resource lookup
    ↓ miss
Resolved Coil memory lookup
    ↓ miss
Bounded extension disk LRU
    ↓ miss
HTTP download
    ├─ display decoded bitmap
    ├─ populate extension LruCache
    └─ persist only if response ≤ 4 MiB
```

A disk-cache miss or disk I/O failure must never prevent the HTTP fallback.
If every source fails, preserve the current behavior: log the failure and leave
the cell without a bitmap.

## Migration behavior

No migration scan or filename rewrite is required. Existing files remain
readable through the network/cache URL lookup, while the first background trim
brings their total under the new limit. New writes use the canonical network
URL key and no longer create duplicate entries.

## Verification requirements

The implementation is complete only when focused checks demonstrate:

1. A cache hit updates recency and avoids a network request.
2. A cache miss falls through to the network path.
3. Entries larger than `4 MiB` are displayed but not persisted.
4. Total committed files never exceed `64 MiB` after trimming.
5. Oldest files are evicted first.
6. Duplicate network/cache URL writes produce one new file.
7. Interrupted or stale temporary files are removed.
8. Corrupt entries are deleted and refetched.
9. Concurrent writes leave only complete readable files.
10. Cache failures do not crash or block the gallery.
11. All filesystem work remains off the UI thread.
12. Generated bytecode remains API 29 compatible.

The extension cache remains bounded by this specification; Glide and Coil retain
ownership of their separate application caches and are not modified here.
