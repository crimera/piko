# R4 — why scenario B (thread-linked reply union) failed

Recon only. APK: `apks/12.27.0-prod.01.apk` (NewX 12.27.0-prod.01).
Commit under audit: `e47559ff` (`NewXShareImageHandler.java`).

Evidence: A (parent-only) pass, C (standalone) pass, D (timeline single) pass.
B (share thread-linked **tung** reply, expect **Harrison + tung** union) captured
a neighbor action bar + clipped **North Guard**, and no parent.

---

## 1. Parent-id resolution — every reflection step

Call chain in `captureRenderedPost`:
`resolveParentId(post)` → `canonicalPost(post)` → `reflectedParentId(canonical)`.

| # | code | target in 12.27.0-prod.01 | result | break condition |
| --- | --- | --- | --- | --- |
| 1 | `findPresenterData(presenter, "com.x.models.timelines.items.UrtTimelinePost")` | matches any presenter field whose type starts `com.x.models.timelines.items.` → obfuscated `Lcom/x/models/timelines/items/j1;` (toString `UrtTimelinePost(postResult=...)`) | OK | if the presenter no longer stores the wrapper here |
| 2 | `NewXUtils.invokeIfPresent(post, "getPostResult")` | `j1` has **no getter** for field `a:Lcom/x/models/o6;` (only `getId/getText/getUrl` plus obfuscated `a()..z()`); whole-APK `--name getPostResult` = **NO_MATCH** | **null** | R8 inlined/removed the property getter (present in dogfood build) |
| 3 | `invokeIfPresent(postResult, "getCanonicalPost")` | `postResult` already null; `q1`/`o6` also have no `getCanonicalPost` (`--name` = **NO_MATCH**) | **null** | same |
| 4 | `reflectedParentId(canonicalPost)` | unreachable because step 3 returned null | — | — |
| 4a | `invokeIfPresent(canonical, "getRepliedPostId")` | no such method anywhere (`--name` = **NO_MATCH**) | (null) | getter removed |
| 4b | `fieldValue(canonical, "repliedPostId")` | no declared field by that name; the real field is `Lcom/x/models/t0;->i:Ljava/lang/Long;` (toString label `repliedPostId`) | (null) | reflection is by literal name |
| 4c | `ToStringParser.fieldValue(toString(canonical), "repliedPostId")` | the safe fallback; works on `CanonicalPost(...)` | (value) | only reached if 4a/4b fail, and only if `canonical` is non-null |
| 4d | loop declared `Long` fields, return the one whose value string equals the label | `t0` Long fields = `D:quoteCount`, `h:selfThreadId`, `i:repliedPostId`; returns `String.valueOf(field)` | (value) | if the label was found but no `Long` field matches, the method returns **null** (it never returns `labelId` directly); if R8 makes the field primitive `long` or `@JvmField`, `field.getType() != Long.class` skips it |
| 5 | `normalizePostId(...)` + `renderedBounds(id)` | map key must equal `g6.toString() == g6.b` | — | `identifierValue(Long)` returns null for `<= 0` (0 treated as no id), but `t0` can carry `repliedPostId=0` |

**Primary break:** step 2/3. In the production APK the getters
`getPostResult`, `getCanonicalPost`, and `getRepliedPostId` do not exist, so
`canonicalPost()` always returns null and `resolveParentId()` always returns
null. The toString-label fallback is dead code (it lives inside
`reflectedParentId`, which is never called). **Union can never trigger for B.**
The actual reachable parent data is `j1.a (o6) → t0.i`, but the code never
reads field `a` directly, and `j1` has no `getPostResult`.

Secondary breaks if step 2/3 were fixed:
- R8 renaming `t0.i` is harmless for 4d (name-independent value loop) but a
  type change to primitive `long`, `@JvmField`, or removing it breaks 4d.
- `repliedPostId == 0` is accepted as parent id ("0"), then the map lookup
  returns null; only `null` is treated as standalone.
- key mismatch: patch registers by `String.valueOf(g6)` = `g6.b`, capture
  resolves via `identifierToString` → `g6.a()` numeric. `g6.<init>(J)` sets
  `b = String.valueOf(a)`, so they match; `g6.<init>(IJ)` (synthetic) sets
  `b` to the passed String, which would diverge.

## 2. Parent-bounds lookup

`Rect parentBounds = renderedBounds(resolveParentId(post));`

- With the section-1 break, `resolveParentId` is null and lookup is skipped.
- If fixed: the parent is registered only when its own `onPositioned`
  callback fires while the parent row is laid out. In the thread view the
  parent (Harrison) is above tung; if the user opened the menu after scrolling
  so Harrison is off-screen, its last registered rect may be stale
  (negative/off-window) and can never refresh.
- The strong LRU is 128 entries keyed by postId, `removeEldestEntry` evicts on
  `size > 128`. A long timeline/thread scroll can evict the parent entry; then
  `parentBounds == null` and union silently no-ops.
- Key mismatch (section 1, `g6` synthetic constructor) would also make the
  parent resolve to a key that was never registered.

## 3. Adjacency gate

```java
if (parent.bottom > selected.top + 4) return false;
overlap = min(parent.right, selected.right) - max(parent.left, selected.left);
return overlap > 0 && overlap * 2 > selected.width();
```

- Thread rows are contiguous Compose items; parent.bottom == reply.top is
  normal, but a thread connector / divider / item padding can add 1–6 px.
  At 2.75x density a 1dp divider is ~3px, 2dp padding ~6px → the 4px slop can
  reject a valid parent-above pair. The slop is in raw px, so it is denser the
  higher the density/scaling.
- If the parent's measured rect includes the downward thread connector, or the
  reply is partially clipped at the top, `parent.bottom > selected.top` → hard
  reject even though they are adjacent/overlapping.
- Horizontal overlap: replies are inset by the thread gutter, so >50% usually
  holds, but a centered/avatar-inset reply can fail the strict `>`.
- These only matter after section 1 is fixed; today the gate is never reached.

## 4. Fallback path — how a neighbor rect wins

When section 1/3 reject, `captureRenderedPost` captures `bounds` =
selected-only. B already shows a wrong selected rect, so the stored entry for
tung's postId is wrong. Mechanisms:

- **Last-writer-wins on duplicate composition.** `PositionCallback` is created
  per render and `registerRenderedBounds` overwrites when the rect differs
  (only `equals` suppresses). One postId rendered twice (timeline row behind
  the open detail sheet + the thread row) races; the off-screen/clipped copy
  can be written last, so the map holds a rect that belongs to the other slot.
- **Recycled slot / stale spatial snapshot.** The callback reads
  `spatial/c.c()` (R2), which is a snapshot held by
  `androidx.compose.ui.spatial.b/d` keyed by node id. Between menu tap and the
  two `postOnAnimation` frames the list can reuse/relayout a node; the snapshot
  can still describe the previous content that occupied the slot (North Guard).
- **Layer-space rect.** `spatial/c.c()` applies matrix `f:[F` before returning
  the `IntRect`; if the callback node sits inside the bottom-sheet layer, the
  rect can be offset from true window coordinates, so PixelCopy samples a band
  one row lower (neighbor action bar + clipped next post).
- **Snapshot vs PixelCopy race.** `captureBounds` is frozen before the async
  `PixelCopy.request`; a scroll/settle during those frames keeps the
  coordinates but changes the pixels beneath them.

No key collision is expected between postIds; the collision is same-key
multiple writers plus stale node rects, not two different ids.

---

## Ranked hypotheses for B

1. **Parent resolution is dead.** `getPostResult` / `getCanonicalPost` /
   `getRepliedPostId` are absent in 12.27.0-prod.01, so `canonicalPost()` and
   `resolveParentId()` always return null and union is never attempted. This
   fully explains "no parent". (Highest confidence; verified by whole-APK name
   search.)
2. **The selected-only fallback stores the wrong rect for tung** because the
   same postId is written from two composed locations (timeline + thread) or
   from a recycled node's stale `spatial/c` snapshot; last-writer-wins leaves a
   neighbor/North Guard rect. Explains "neighbor action bar + clipped North
   Guard" with no union.
3. **`spatial/c.c()` is offset (layer matrix) or a stale spatial snapshot**,
   so even a correctly keyed selected rect lands one row off in window space.
4. **4px adjacency gate / `parent.bottom > selected.top` overlap** would reject
   a valid Harrison→tung union once sections 1/2 are fixed.
5. **Parent evicted (128 LRU) or never registered** because Harrison was
   scrolled off-screen when the menu opened; secondary once 1 is fixed.

## Minimal log lines to disambiguate

At option-action/capture time:

```text
DEBUG-share-image: post class=<...> id=<selected> source=<sourcePostId>
DEBUG-share-image: canonical class=<...|null> toString=<...|null>
DEBUG-share-image: parentId=<resolvedId|null> path=<getter|field|label|none> labelFound=<bool|field>
DEBUG-share-image: map size=<n> selectedBounds=<l,t,r,b> parentKey=<id|null> parentBounds=<l,t,r,b|null>
DEBUG-share-image: adjacency parent.bottom=<n> selected.top=<n> gap=<n> overlapPx=<n> overlapPct=<n> union=<true|false>
DEBUG-share-image: captureBounds=<l,t,r,b> window=<w>x<h> windowBounds=<raw spatial/c.c()>
DEBUG-share-image: register key=<postId> rect=<l,t,r,b> seq=<n>            # on every registerRenderedBounds
```

`path`/`labelFound` pin whether parent resolution died at getPostResult (2),
the named field (4b), or the Long-field loop (4d). Per-registration `seq`
reveals duplicate writes for one key (same-key last-writer-wins) and whether
the winner is off-screen (`top<0` / `right>window`). Logging the raw
`spatial/c.c()` alongside `captureBounds` shows whether the stored rect is
already offset before any union.

## Result

Top hypothesis: **section 1 — `getPostResult`/`getCanonicalPost` no longer
exist, so `resolveParentId` always returns null and the Harrison+tung union is
never attempted; the North Guard rect comes from the selected-only fallback
whose per-postId bounds were overwritten by a duplicate/recycled row.**
