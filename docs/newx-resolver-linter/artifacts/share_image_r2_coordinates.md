# R2 — LayoutCoordinates / window-relative accessor

Recon only. APK: `apks/12.27.0-prod.01.apk` (NewX 12.27.0-prod.01).

Context: R1 renderer `Lcom/x/mappers/subscriptions/a;->a(k5,...)`
attaches `Landroidx/compose/ui/layout/n;->s(Modifier,Function1)` at index 246.

## 1. `Landroidx/compose/ui/layout/e0;` (LayoutCoordinates)

```text
# class Landroidx/compose/ui/layout/e0; (public interface abstract)  dex classes.dex
[methods]
  static synthetic W(e0,e0,I)J
  abstract c()Z                                   # isAttached
  abstract j()J                                   # size: IntSize (packed W<<32|H)
  abstract k()Z
  abstract C(J)J  N(J)J  X(J)J  l(J)J  v(J)J      # Offset transforms (local/window/root)
  abstract f(e0,J)J                               # localPositionOf(source, offset)
  abstract d(e0,J,Z)J                             # localPositionOf / localToWindow variant
  abstract J(e0,Z)Landroidx/compose/ui/geometry/c;  # localBoundingBoxOf(source, clip)
  abstract R()e0                                  # parentCoordinates
  abstract g()e0                                  # parentLayoutCoordinates
  abstract G([F)V                                 # copy transform to matrix
  abstract n(e0,[F)V
```

No 0-arg member returns `geometry/c`; the only rect-returning member is
`J(e0,Z)` (source-relative, 1 bool arg).

## 2. `Landroidx/compose/ui/geometry/c;` (Rect)

```text
# class Landroidx/compose/ui/geometry/c; (public final)  dex classes.dex
[fields]
  public static final e:Landroidx/compose/ui/geometry/c;   # zero rect
  public final a:F   # left
  public final b:F   # top
  public final c:F   # right
  public final d:F   # bottom
```

Constructor is `(FFFF)` and is invoked as `(left, top, right, bottom)` — e.g.
`n->f` builds `new geometry/c(0f, 0f, width, height)`. So `a,b,c,d = l,t,r,b`.
(For `androidx/compose/ui/unit/k` = IntRect, the same `a,b,c,d` order holds.)

## 3. `e0` implementations

```text
$ dexscope search-classes apks/12.27.0-prod.01.apk --implements 'Landroidx/compose/ui/layout/e0;'
public final class Landroidx/compose/ui/layout/w0; ... (1 field, 17 methods)
public abstract class Landroidx/compose/ui/node/n1; ... (36 fields, 71 methods)
```

- `layout/w0` — thin delegating wrapper (`a:node/r0`); only 0-arg object
  accessors are `R()e0` and `g()e0`, i.e. it exposes no rect of its own.
- `node/n1` — the real `NodeCoordinator` coordinate object (subclasses
  `node/c0`, `node/v`). Only 0-arg method returning a rect:
  `public final L1()Landroidx/compose/ui/geometry/c;` (88 instr).
  `L1()` starts from the node's bounds (`M:geometry/c` or `x1.e0()/c0()`),
  subtracts `f1(n1())` (its window position), then walks ancestors with
  `G1(geometry/a,ZZ)` until the window root — i.e. it is the node's own
  window bounds.

## 4. Rect / Offset accessor candidates

Window-relative bounds accessors (class `androidx/compose/ui/layout/n`, the
merged Kt file that also holds R1's modifier factories):

| ref | role | evidence |
| --- | --- | --- |
| `n->g(e0,Z)Landroidx/compose/ui/geometry/c;` | **boundsInWindow** (clip flag) | resolves window root via `n->j(e0)`, reads root `j()` size, calls `root.J(this,clip)`, clips to root size |
| `n->f(e0)Landroidx/compose/ui/geometry/c;` | boundsInParent | `parent.R().J(this,true)`, else `Rect(0,0,size)` |
| `n->w(e0)J` | positionInParent (Offset) | `parent.f(this, 0L)`, 0 when no parent |
| `n->j(e0)Landroidx/compose/ui/layout/e0;` | window-root resolver | walks `R()` parents, then `node/n1.y` to root |

Packed `J` returns on `e0` (candidates for position/size):
`j()` = `size: IntSize` (confirmed: `n->f` unpacks `j()` into `Rect(0,0,w,h)`);
`C/N/X/l/v(J)` and `d/f(e0,J)` = Offset transforms. There is no 0-arg
`positionInWindow` on `e0`; window position is `n->g` bounds top-left
(or `n->w` for parent).

## 5. What the share-image callback actually receives

`n->s` is the synthetic bridge to `n->r(Modifier,J,J,Function1)` →
`layout/n1` element → `n->x(...)` → `androidx/compose/ui/spatial/d;`. The
`layout/n1.m1()` body stores the `Function1` in `spatial/d` and
`spatial/d.a(JJJJ[F)` invokes it with a `Landroidx/compose/ui/spatial/c;`:

```text
[0081] (75) invoke-interface v0, v1, Lkotlin/jvm/functions/Function1;->invoke(Ljava/lang/Object;)Ljava/lang/Object;
```

So the injected `positionCallbackFromIdentifier` lambda gets **`spatial/c`, not
`e0`**. Cross-check: `com.x.media.autoplay.g` has one branch casting to
`androidx/compose/ui/layout/e0` (legacy API) and another casting to
`androidx/compose/ui/spatial/c` and immediately calling `c()` (new spatial API).

`Landroidx/compose/ui/spatial/c;` (fields `a:J b:J c:J d:J e:J f:[F g:s`):

```text
# 0-arg non-primitive methods
b()Landroidx/compose/ui/unit/k;   # IntRect from longs a,b (raw rect)
c()Landroidx/compose/ui/unit/k;   # IntRect from a,b transformed by matrix f:[F (perspective divide)
```

`unit/k` (IntRect) has exactly four int fields `a,b,c,d` = left,top,right,bottom,
so the reflection decoder treats both `b()` and `c()` as rectangles. App
visibility code (`com.x.media.autoplay.g`, `com.x.spaces.ui.room.p`,
`com.x.ui.common.media.editor.text.s`) uses `c()` as the on-screen rect.

**Realized window-relative accessor for the share-image callback:**
`Landroidx/compose/ui/spatial/c;->c()Landroidx/compose/ui/unit/k;`.
(`n->g(e0,Z)` / `node/n1->L1()` are the `e0`-level equivalents used only when a
legacy `e0` callback is involved.)

## 6. Why the current BoundsReader heuristic fails (5 lines)

1. `boundsReader(spatial/c)` reflects every 0-arg method returning an object;
   both `b()` and `c()` return `unit/k`, whose first four int fields are read as
   `l,t,r,b`, so both are accepted as candidate post rectangles.
2. Nothing ties a candidate to the `postId` or to the node's own window layer,
   so the matrix-transformed `c()` (and its descendant/card layers) compete with
   the node's raw `b()`.
3. `BoundsReader.read` then keeps the candidate with the largest `top`
   (tie: largest `left`) — i.e. the lowest rectangle on screen.
4. In a vertical feed the lowest rect among a node's layers is the
   child/card (or adjacent) layer rather than the post row, so the chosen top
   drifts downward toward the next post.
5. Fix: select one explicit accessor (`spatial/c.c()` for this callback; or
   `n->g(e0,clip)` for a legacy `e0` callback) instead of maxing over
   reflection candidates, and validate it against the post's window bounds.

## Result

- `e0` members documented; `geometry/c` `a,b,c,d = l,t,r,b`.
- impls: `layout/w0`, `node/n1` (subclasses `node/c0`, `node/v`).
- callback parameter is `androidx/compose/ui/spatial/c` (not `e0`).
- window-relative accessor: `Landroidx/compose/ui/spatial/c;->c()Landroidx/compose/ui/unit/k;`
  (raw sibling `b()`; LayoutCoordinates equivalent `Landroidx/compose/ui/layout/n;->g(Landroidx/compose/ui/layout/e0;Z)`).
