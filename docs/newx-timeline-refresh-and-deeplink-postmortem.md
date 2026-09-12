# NewX timeline refresh and post-deeplink postmortem

## Scope

- App: `com.twitter.android`
- Target: X `12.25.0-alpha.01`
- Area: NewX URT timelines, For You topic filtering, post deep links, and saved scroll position
- Primary patch: `NewX: Disable automatic timeline refresh`

This document records the failure modes, bytecode evidence, final design, and validation so future agents do not have to rediscover the interaction between these patches.

## Original goal

The original goal was **not** to disable every `AUTO_REFRESH` request. It was to stop X from refreshing a populated Home timeline when the app starts, returns to the foreground, or the Home tab is reselected. Those refreshes move the user to the top and destroy the saved reading position.

The desired behavior is:

1. Keep an actually empty timeline's first load alive.
2. Suppress lifecycle refreshes when the timeline already has content.
3. Restore a saved position without converting a normal startup into a top-of-feed jump.
4. Allow explicit user actions, such as opening a post or changing For You topics, to load normally.

The patch also works with `Restore timeline position`, which persists positions through `TimelineScrollPositionStore`.

## Relevant X behavior

### Notification and post deep links

X intentionally routes many notification post links through Home/For You. The post is injected into the current Home timeline; opening For You is therefore expected and is not itself a routing bug.

The relevant path is approximately:

```text
external/notification link
  -> XUrlInterpreterActivity
  -> MainActivity
  -> Home/For You post injection
  -> URT timeline refresh/load
```

`XUrlInterpreterActivity` copies the incoming action, data, and extras into X's launch intent. Ordinary external `https://x.com/<user>/status/<id>` links do not contain X's internal or notification extras, even though they still arrive at `MainActivity` with a `/status/` URI.

### For You topic filtering

`ForYouTopicFilterPatch` installs `pikoRefreshForYouTopicFilter()`. The generated bridge dispatches:

1. `ClearAndRefreshTimeline`
2. `RequestScrollToTop`

That sequence intentionally clears the current For You data and then requests a fresh load. It must not be treated as a lifecycle refresh.

### URT data shape

The target's repository is `Lcom/x/repositories/urt/w;`.

The request method is:

```text
Lcom/x/repositories/urt/w;->i(
    Lcom/x/models/timelines/d;
    Lcom/x/models/timelines/items/j0;
)V
```

The timeline data getter returns `h2`, whose `d()` result is page-shaped data: an outer `List` containing page `List`s. The target's network coroutine also obtains the first outer element and casts it to `List`, confirming this shape.

The old check used `outerList.isEmpty()`. An initial state can contain an empty page, so the outer list is non-empty even though no timeline content has loaded.

## Failure history

### 1. Initial-load suppression

The first implementation treated a null cursor as evidence that a request was merely a foreground refresh. That was wrong: X also uses a null cursor for the first request on a fresh install. The result was a permanent loading skeleton on an empty timeline.

The first correction allowed null-cursor requests, but saved scroll state introduced another ambiguity.

### 2. `0,0` saved position is not proof of loaded data

`RestoreTimelinePositionPatch` can observe and save the initial scroll holder before the first network response. That creates a valid-looking persisted position such as:

```text
FOR_YOU.index = 0
FOR_YOU.offset = 0
```

That position only means “the list is at its initial layout position”; it does not prove that the timeline contains posts. Using the existence of a saved position alone to suppress the first request caused fresh/deep-link loads to be skipped.

The final logic uses timeline data to decide whether content exists, and uses the saved position only to choose `VIEWPORT_AWARE_AUTO_REFRESH` after the empty-data check.

### 3. Empty outer page mistaken for populated data

The next implementation switched from saved-position detection to `List.isEmpty()`. This fixed some startup cases but still suppressed the request when URT exposed an outer list containing an empty page. The UI showed the exact loading skeleton from the report while the required request had already been dropped.

The final extension helper is:

```java
TimelineRefreshGate.isTimelineDataEmpty(List<?> pages)
```

It considers the data empty when all pages are empty. A non-list page is treated as populated, which fails closed if the target contract changes.

The same semantic check is injected in both places that can suppress the request:

- the URT repository request method;
- the URT automatic-refresh event handler.

Patching only one of those locations is insufficient because the event handler can return before the repository method is reached.

### 4. For You reset/snooze infinite skeleton

Reset and snooze clear the timeline first. The disable-refresh patch then saw the refresh as an automatic request and suppressed it. The UI remained on an empty/skeleton state because the clear happened but the reload did not.

The fix added a separate, single-use `For You filter refresh` gate. The bridge marks it immediately before dispatching the clear event. The event and repository hooks allow that request through, preserving the intentional `AUTO_REFRESH` behavior and scroll-to-top operation.

This gate is independent from the post deep-link gate, so a topic change cannot accidentally consume a deep-link allowance and vice versa.

### 5. External post deep links still loaded forever

The first deep-link gate only recognized a `/status/` URI when either of these extras was present:

```text
com.x.deeplink.EXTRA_INTERNAL_DEEPLINK
com.x.deeplink.EXTRA_NOTIFICATION_DEEPLINK
```

That covered notification/internal flows but missed ordinary external links. Those links still route through `MainActivity`, but have no special extra. The gate was never marked, so the populated-timeline guard suppressed the load and left Home/For You on a skeleton.

The final gate recognizes a post deep link from the semantic `/status/` path itself. A normal launcher intent has no data path, so this does not affect ordinary startup. The existing Home/For You injection route remains untouched.

## Final design

### `TimelineRefreshGate`

`extensions/newx/src/main/java/app/morphe/extension/newx/timeline/TimelineRefreshGate.java` contains two independent, process-local, single-use gates:

- `postDeepLink`
- `forYouFilterRefresh`

Each gate uses an `AtomicLong` deadline with a 15-second expiry:

- `mark...()` arms the gate;
- `is...Pending()` is a non-consuming check for the event hook;
- `consume...()` consumes the allowance exactly once at the repository hook.

The post gate is marked from:

- `MainActivity.onCreate(Intent)`;
- `MainActivity.onNewIntent(Intent)`.

The filter gate is marked from the generated `pikoRefreshForYouTopicFilter()` bridge before it dispatches the clear/scroll events.

### Refresh decision order

When `newx.timeline.disable_refresh` is enabled and the request is a null-cursor `AUTO_REFRESH` for For You, Following, or Ranked Following:

1. Allow and consume a pending post deep-link request.
2. Allow and consume a pending For You filter request.
3. Inspect actual page content using `isTimelineDataEmpty()`.
4. Allow an empty initial load.
5. If content is present, suppress the lifecycle refresh.
6. If the data is empty but a saved position exists, change the request to `VIEWPORT_AWARE_AUTO_REFRESH` so restoration does not jump to the top.

The automatic-refresh event hook applies the same gate and page-content logic before it calls the repository. Requests with a non-null cursor, a different request type, a different timeline, or a disabled setting retain X's original path.

## Files involved

- `extensions/newx/src/main/java/app/morphe/extension/newx/timeline/TimelineRefreshGate.java`
  - Deep-link/filter gates.
  - Nested URT page-content predicate.
- `patches/src/main/kotlin/app/crimera/patches/newx/timeline/DisableTimelineRefreshPatch.kt`
  - MainActivity gate hooks.
  - Repository and event-handler refresh guards.
  - Viewport-aware startup restoration.
- `patches/src/main/kotlin/app/crimera/patches/newx/timeline/ForYouTopicFilterPatch.kt`
  - Marks explicit topic refreshes before clearing and reloading For You.
- `extensions/newx/src/main/java/app/morphe/extension/newx/timeline/TimelineScrollPositionStore.java`
  - Persists For You and Following positions, but must not be used as proof that data has loaded.
  - Profile restoration is opt-in and uses the profile timeline type plus the profile ID as its key.
- `patches/src/main/kotlin/app/crimera/patches/newx/timeline/RestoreTimelinePositionPatch.kt`
  - Hooks X's scroll-position holder and saves/restores supported timeline positions.
  - Bypasses X's process-local type-only map for unsupported timelines so profiles cannot inherit another profile's position.

## Validation

Build and patch validation used the exact target APK:

```text
./gradlew :patches:build
./patch-twitter.sh
ADB="$HOME/.cache/piko/platform-tools-36.0.0/adb" ./patch-twitter.sh --install
```

The patcher reported all relevant patches as `Applied`, including:

- `NewX: Disable automatic timeline refresh`
- `NewX: Filter For You by topic`
- `NewX: Restore timeline position`

Static checks on the final APK confirmed:

- `pikoRefreshForYouTopicFilter()` calls `markForYouFilterRefresh()`;
- the repository and event hooks call `isTimelineDataEmpty()`;
- both deep-link activity hooks call `markPostDeepLink()`.

Runtime checks on the device confirmed:

1. **Normal populated startup with refresh disabling enabled** — existing timeline content remained loaded instead of jumping/reloading.
2. **External post link without special extras** — `https://x.com/jack/status/20` loaded the post into the intended For You/Home injection route instead of leaving a skeleton.
3. **Internal post deep link** — opened the post detail view successfully.
4. **For You topic snooze** — the sheet closed and the refreshed, populated timeline loaded instead of remaining on an empty skeleton.
5. No fatal application exception occurred during these flows.

The local Morphe version does not support the attempted `test-patch` command (`UNKNOWN_COMMAND`), so bytecode and device validation were used instead.

## Future-agent checklist

When a similar skeleton is reported:

1. Check whether the screen is Home/For You injection or the actual Post screen. Do not “fix” the intentional notification injection route first.
2. Capture the exact `MainActivity` intent data and extras. Do not assume every post deep link has an internal/notification extra.
3. Inspect both the URT event handler and repository request method. Either hook can suppress the load.
4. Inspect the data shape. For this target, the flow is a list of pages, not a flat list of posts.
5. Do not use saved scroll position existence as proof that data is loaded; early layout can persist `0,0`.
6. Distinguish lifecycle refreshes from explicit user actions with a narrowly scoped, single-use capability gate.
7. Preserve `AUTO_REFRESH` for explicit deep-link/filter loads. Only convert ordinary restored startup loads to `VIEWPORT_AWARE_AUTO_REFRESH`.
8. Test negative paths: normal launcher startup, populated warm startup, external status link, notification/internal status link, topic reset, topic snooze, and a truly empty initial timeline.
9. Build and patch the exact APK. Treat fingerprint cardinality failures as contract changes, not as reasons to silently skip the patch.

## Known limits

- The status-link detector intentionally uses the `/status/` path because ordinary external links do not carry X's private extras. If X changes its canonical post URL shape, update the semantic detector and its tests.
- The page-content helper is intentionally conservative. An unexpected non-list page is considered populated so a changed contract fails closed rather than enabling uncontrolled refreshes.
- The gates are process-local and expire after 15 seconds. They cover the synchronous event/request chain; they are not a general retry mechanism for a failed network response.
- This fix targets X `12.25.0-alpha.01`. Future releases must re-resolve the owners, signatures, page-flow shape, and event/request callsite from the target APK.
- Profile scroll restoration is disabled by default. When enabled, positions are stored separately for each profile ID and profile timeline tab; old type-only profile keys are intentionally ignored.
