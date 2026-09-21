# R3 — thread-parent link for share-image union capture

Recon only. APK: `apks/12.27.0-prod.01.apk` (NewX 12.27.0-prod.01).

Context: R1 renderer `Lcom/x/mappers/subscriptions/a;->a(k5,...)`; R2 callback
accessor `Landroidx/compose/ui/spatial/c;->c()`.

## 1. `k5` reply/thread fields

`Lcom/x/urt/items/post/k5;` (dex classes4.dex) has no direct parent/`ancestor`
field. The only reply/thread-bearing fields:

| field | type | toString label | role |
| --- | --- | --- | --- |
| `q` | `Lcom/x/models/replycontext/b;` | `replyContext` | reply context (wraps canonical post) |
| `y` | `Z` | `showBottomThreadConnector` | thread line below (post has replies) |
| `z` | `Z` | `showTopThreadConnector` | thread line above (post is a linked reply) |
| `G` | `Lcom/x/models/conversationcontrol/c;` | `conversationControl` | conversation controls, not a link |

Field↔label evidence from `k5->toString()` (labels at 91/93/94 map to
`y`,`z`,`A`):

```text
[00e0] const-string ", showBottomThreadConnector="
[00e5] const-string ", showTopThreadConnector="
[00e7] const-string ", softInterventionPivot="
[00e9] iget-boolean v3, k5->y:Z
[00eb] iget-boolean v4, k5->z:Z
[00f0] iget-object v1, k5->A:Lcom/x/models/qc;
```

`displayType` (`c:Lcom/x/models/c6;`) is only the enum
`{CondensedPost, MediaShort, Post}` — not a thread discriminator.

## 2. Reply-context payload

`Lcom/x/models/replycontext/b;` is an interface with `a():List` and `b():List`
(facepile users), no parent id. Implementations:

- `Lcom/x/models/replycontext/d;` — `TimelinePostReplyContext(post=t0)`.
  Fields: `a:Lcom/x/models/t0;`, `b/c:Lkotlin/d` (lazy facepile lists).
- `Lcom/x/composer/t2;` — composer variant (fields `a:fh`,`b:q1`,`c:Set`).

Decisive evidence that `k5.q` wraps the canonical `t0`:

```text
# com/withpersona/.../dispatchers/a;->d(UrtTimelinePost):p5
[0092] iget-object v14, v14, Lcom/x/models/q1;->b:Lcom/x/models/t0;
[0096] iget-object v14, v14, Lcom/x/models/t0;->T:Lcom/x/models/replycontext/b;
```

i.e. k5's `replyContext` argument = `canonicalPost.T`. And `t0.<init>` sets
`T = new replycontext/d(this)`:

```text
# Lcom/x/models/t0;-><init>(...replycontext/b)V  @ 0x1ce
invoke-direct ..., Lcom/x/models/replycontext/d;-><init>(Lcom/x/models/t0;)V
```

So for a rendered timeline post `k5.q` is a
`replycontext/d` whose field `a` is the canonical `t0`.

## 3. Canonical post `t0` (toString labels)

`Lcom/x/models/t0;` (CanonicalPost). Label→field from `t0->toString()`:

```text
id             -> a:Lcom/x/models/g6;
text           -> b:String
timestamp      -> c:Instant
...
author         -> f:Lcom/x/models/nh;
legacyCard     -> g
selfThreadId   -> h:Ljava/lang/Long;     # thread root id   (getter j())
repliedPostId  -> i:Ljava/lang/Long;     # immediate parent id
inlineActionEntry -> j
...
repliedToUserId       -> x:Lcom/x/models/fh;
repliedToUserScreenName -> y:String;
quotedStatusId -> B:Lcom/x/models/g6;    # quote, NOT parent
```

Note the obfuscation collision:
- Java **field** `i:Ljava/lang/Long;` = `repliedPostId` (parent), **no getter**
  in this build (R8 inlined it; only `postdetail/b`, `postdetail/c`,
  `scribing/post/a` read it directly).
- Java **method** `i()Lcom/x/models/replycontext/b;` returns field `T`
  (the reply context), not the parent id.
- `t0.j()Ljava/lang/Long;` returns field `h` = `selfThreadId` (thread root).

`Ljava/lang/Long; repliedPostId` is populated by the API/thread builder and is
null for a standalone post.

## 4. Parent accessor

Immediate thread-parent post id, from a rendered `k5`:

```text
k5.q:Lcom/x/models/replycontext/b;
  -> cast Lcom/x/models/replycontext/d;
  -> field a:Lcom/x/models/t0;            # canonical post
  -> field i:Ljava/lang/Long;             # repliedPostId
```

`parentId != null` ⇒ the post is a reply; null ⇒ standalone.

Key reconciliation for the bounds map: `g6.<init>(J)` sets
`b = String.valueOf(id)` and `g6.toString()` returns `b`, so a RENDERED_BOUNDS
key is `String.valueOf(g6.a)`. Therefore
`String.valueOf(t0.repliedPostId)` == the parent post's RENDERED_BOUNDS key.

## 5. Linked vs standalone discriminator

Use the rendered connector flags, not merely a non-null parent:

| case | `k5.z` top | `k5.y` bottom | repliedPostId | capture |
| --- | --- | --- | --- | --- |
| thread-linked reply | **true** | (any) | non-null | parent + reply union |
| parent of thread | false | **true** | any | parent only |
| standalone Relevant reply | **false** | false | may be non-null | reply only |

`showTopThreadConnector` is exactly the "thread line above" the task refers to;
a Relevant-tab reply can carry a `repliedPostId` while its parent is not
rendered, so `z == true` (not `repliedPostId != null`) is the condition for
union.

## 6. Union feasibility (5 lines)

1. The parent is a separate timeline entry rendered by the same
   `Lcom/x/mappers/subscriptions/a;->a(k5,...)` renderer (only callers are the
   post dispatch lambdas `layout/b1->invoke`, `material/g0->invoke`), so its
   `onPositioned` callback registers its own rect under its own postId.
2. `RENDERED_BOUNDS` is keyed by `g6.toString() == String.valueOf(id)`.
3. `t0.repliedPostId` is the same numeric id, so
   `String.valueOf(repliedPostId)` is exactly the parent's map key.
4. Both rects are `spatial/c.c()` window `IntRect` snapshots, so the union is
   just the min-left/min-top/max-right/max-bottom of reply ∪ parent.
5. Only precondition: the parent must still be on-screen/registered at capture
   time; otherwise fall back to the reply's rect alone.

## Result

- Parent field: `Lcom/x/models/t0;->i:Ljava/lang/Long;` (label `repliedPostId`),
  reachable from `k5` via `k5.q` → `replycontext/d.a` → `t0.i`.
- Thread root: `t0.h` (`selfThreadId`), getter `t0.j()`.
- Discriminator: `k5.z` (`showTopThreadConnector`); parent = `k5.y`.
- Union feasible: parent rendered by same renderer, key =
  `String.valueOf(t0.repliedPostId)`.
