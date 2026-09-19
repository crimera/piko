# NewX profile Photos gallery

## What it does

The profile **Photos** tab normally renders the same vertical URT post cards as
Posts/Replies (avatar, text, action bar). This feature replaces only the Photos
body with a 3-column square thumbnail grid, and tapping a thumbnail opens that
photo in X's own viewer instead of a custom dialog.

- Photos tab defaults to `Photos` via `NewX: Set default media tab`.
- `NewX: Gallery profile Photos tab` replaces the Photos timeline body with the grid.
- Controlled by the `Gallery layout for Photos tab` toggle in Piko Settings -> Posts and media (`newx.post_actions_media.gallery_profile_photos`).
- Posts, Replies, Reposts, Videos, and every non-Photos timeline render untouched.
- Infinite scroll: reaching the bottom dynamically triggers URT bottom pagination to load the next page of photos.

## Call path

```text
profile pager (Lcom/x/profile/n1)
  -> URT timeline body e(bVar, timelineType, ..., callback, ..., modifier, ..., composer)
       bVar         = timeline items (immutable list of o0)
       timelineType = Com/x/models/timelines/u; (USER_PROFILE_PHOTOS for Photos)
       callback     = Function1 (k0 holding URT component)
  -> [patch guard] timelineType == USER_PROFILE_PHOTOS && isEnabled()?
       yes -> AndroidView(ProfilePhotosGallery factory/updater with callback) + return
       no  -> original LazyColumn post-card path
```

The patch inserts the guard at index 0 of the timeline body, so no Compose
restart-group state is disturbed. Anything that is not `USER_PROFILE_PHOTOS`
or when the setting toggle is disabled falls through to the first original instruction.

## Files

- `patches/.../misc/mediatab/ProfilePhotosGalleryPatch.kt` — fingerprint +
  guard injection, callback register derivation, and settings toggle registration.
- `extensions/newx/.../misc/ProfilePhotosGallery.java` — AndroidView bridge,
  grid layout, incremental cell updates, thumbnail loading, tap routing, and bottom pagination triggering.
- `extensions/newx/.../ui/LoadingIndicatorView.java` — theme-aware, rounded-caps indeterminate
  spinner used by the gallery pagination footer.
- `patch-twitter.sh` — enables both `NewX: Set default media tab` and
  `NewX: Gallery profile Photos tab`.

## Fingerprint

`NewXTimelineBodyFingerprint` matches the static URT timeline body `e(...)`:

- Owner under `Lcom/x/urt/ui/` (`a0` on 12.27.0-prod.01; the owner shifts on
  alpha, e.g. `Lcom/x/urt/ui/b` on 12.28.0-alpha.01, so the name is not hardcoded).
- Exactly one `Lkotlinx/collections/immutable/b;` (items), one
  `Lcom/x/models/timelines/u;` (timeline type), one `PaddingValues` (`y2`), one
  `Modifier`, and one `Composer`.
- Body constructs the content lambda holder `Lcom/x/urt/ui/y;`
  (`NEW_INSTANCE .../y;`). The earlier `media/controller/ui/i;C` anchor only
  exists on production, so `y` is the cross-version anchor.

Cardinality is asserted with `requireExactlyOne`; zero or many matches fail
closed with a `PatchException`.

## Injected guard (smali shape)

```smali
sget-object v0, Lcom/x/models/timelines/u;->USER_PROFILE_PHOTOS:...;
move-object v1, <timelineTypeRegister>
if-ne v1, v0, :piko_newx_photos_original
invoke-static {}, Lapp/morphe/extension/newx/misc/ProfilePhotosGallery;->isEnabled()Z
move-result v0
if-eqz v0, :piko_newx_photos_original
move-object/from16 v0, <listRegister>
move-object/from16 v1, <callbackRegister>
move-object/from16 v6, <paddingValuesRegister>
invoke-interface {v6}, Landroidx/compose/foundation/layout/y2;->d()F
move-result v2
invoke-interface {v6}, Landroidx/compose/foundation/layout/y2;->a()F
move-result v3
invoke-static/range {v0 .. v3}, ...->createFactory(Ljava/util/List;Ljava/lang/Object;FF)...
move-result-object v0
move-object v7, v0
move-object/from16 v0, <listRegister>
move-object/from16 v1, <callbackRegister>
move-object/from16 v6, <paddingValuesRegister>
invoke-interface {v6}, Landroidx/compose/foundation/layout/y2;->d()F
move-result v2
invoke-interface {v6}, Landroidx/compose/foundation/layout/y2;->a()F
move-result v3
invoke-static/range {v0 .. v3}, ...->createUpdater(Ljava/util/List;Ljava/lang/Object;FF)...
move-result-object v2
move-object v0, v7
move-object v1, <modifierRegister>
move-object v3, <composerRegister>
const/4 v4, 0
const/4 v5, 0
invoke-static/range {v0 .. v5}, Landroidx/compose/ui/viewinterop/j;->a(...)V
return-void
:piko_newx_photos_original
```

Register indices are derived from the matched descriptor
(`registerCount - parameterTypes.size + index`), never hardcoded.

## AndroidView bridge & pagination

`ProfilePhotosGallery.createFactory/createUpdater` return **explicit**
`Function1` implementation classes (`GalleryFactory`, `GalleryUpdater`), not
Java lambdas.

- `GalleryView` invokes `requestLoadMore(callback)` only after a downward scroll reaches the bottom
  and shows the themed `LoadingIndicatorView` footer while the request is in flight.
- `requestLoadMore` extracts the URT component (`r0` on prod, `q0` on alpha) from the callback,
  retrieves the bottom paginator (`bottom/h`), and invokes `a(...)` to fire the next cursor request.
- When new items arrive, `GalleryUpdater` calls `galleryView.update(...)`, incrementally appends
  the new views to `GalleryGrid`, and hides the loading footer without resetting scroll position.

## Grid and thumbnails

- `GalleryView` is a `NestedScrollView` wrapping `GalleryGrid`, a plain `ViewGroup`.
- `GalleryView` uses AndroidX `NestedScrollView` with nested scrolling enabled. AndroidView's
  nested-scroll bridge can therefore coordinate the grid with the profile container: the parent
  consumes header collapse/expansion deltas first, and the gallery consumes the remaining grid
  scroll without taking the profile tab row off screen.
- The original LazyColumn receives the same `PaddingValues`; the patch resolves its top
  (`y2.d()`) and bottom (`y2.a()`) values and applies them as native view padding in dp.
  This keeps the native grid aligned with the profile container instead of drawing over the
  sticky tabs.
- The view remains transparent outside the grid so the profile tabs are not covered by the native
  AndroidView layer.
- `GalleryGrid` measures/lays out 3 columns with a 2dp gap; every cell is square
  (`CENTER_CROP`).
- Each cell keeps its source timeline item plus its 1-based photo index within
  that post (`GalleryCell`), capped at 2000 cells.
- Media URLs are parsed from the timeline item `toString`
  (`MediaContentImage/Video/Gif`, `mediaContent` fallback) in post order, and
  thumbnails load through the existing `MediaThumbnailLoader` (twimg `small`
  variant for cache hits, network fallback otherwise).

## Tap routing (native viewer)

Tapping a cell does **not** open a custom dialog. It builds the post's photo
deep link from data already in hand:

```text
https://x.com/<username>/status/<postId>/photo/<photoIndex>
```

- `username` / `postId` come from `NewXUtils.sourceUsername/sourcePostId`
  applied to the cell's own timeline item (same toString fields the download
  path uses, including repost/canonical handling).
- The link is fired as `ACTION_VIEW` with package `com.twitter.android` first,
  so X claims its own link and opens its native photo viewer with auth,
  pager, and gestures intact.
- If X does not claim it (`ActivityNotFoundException`), the same URL is fired
  without a package (browser/chooser fallback).
- If identity parsing fails (`post`/`twitter` fallbacks), the cell's image URL
  is opened directly instead of guessing.

## Lessons (crash log)

1. `AbstractMethodError ... Function1.invoke` on
   `ProfilePhotosGallery$$ExternalSyntheticLambda0` → use explicit `Function1`
   classes for Compose `AndroidView` factory/update callbacks.
2. `NoSuchFieldError: ... kotlin/Unit;` at `GalleryUpdater.invoke` →
   return `null` from the updater; never reference `Unit.INSTANCE` from the
   extension DEX against the obfuscated host Kotlin runtime.
3. Pagination in standalone `AndroidView` → since Compose `LazyListState` is bypassed,
   extract the URT bottom paginator (`p()`) from the passed callback and invoke `a(...)`
   on scroll threshold.

## Validation

```bash
./gradlew :patches:build --no-daemon
./patch-twitter.sh ./apks/12.27.0-prod.01.apk
./patch-twitter.sh ./apks/12.28.0-alpha.01.apk
```

Expect `Applied: NewX: Gallery profile Photos tab` on both. Test the matching
output APK: open a profile → Photos shows the grid → scroll down → more photos load incrementally → tap a thumbnail → X's viewer opens that photo.
