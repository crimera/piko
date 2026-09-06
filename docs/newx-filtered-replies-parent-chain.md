# NewX filtered-replies parent-chain investigation

## Scope and target

This investigation concerns replies removed by X-Lite's **Hide posts by verified
account type** patch. It does not concern X's native hidden-replies feature.

- Package: `com.twitter.android`
- Version: `12.23.1-prod.01`
- APK: `twitter_12.23.1-prod.01.apk`
- APK SHA-256: `b76afdb1ddb4ac8b292c0ca6cfb9304b9ea29fa1aaa30dbea85364aa69578198`
- Patch bundle tested: `patches-3.9.0-dev.4.mpp`
- Morphe CLI tested: `1.11.0`
- Older behavior retained by the existing timeline and store tests

The observed failure was specific to ordinary post-detail conversations. The
verified-account filter removed the expected replies, but selecting **Filtered
replies** from the focal post's overflow menu showed `No filtered replies for
this post`.

## Root cause

NewX does not deliver a post-detail conversation as one stable module rooted at
the focal post. It can deliver the screen incrementally:

1. The focal post arrives as a direct timeline post whose entry ID is only its
   numeric post ID.
2. Reply branches arrive later as separate
   `conversationthread-<reply-or-ancestor-id>` modules.
3. A reply hidden by the verified-account filter is captured while processing
   one of those modules, so the old store keys it under that module's apparent
   root.
4. The overflow-menu presenter supplies the canonical ID of the selected focal
   post.
5. Because the module key and focal-post ID differ, the old exact-key lookup
   returns an empty list even though the reply was successfully filtered and
   buffered.

The prior assumption that `conversationthread-<id>` always names the focal post
was therefore false. Entry-ID parsing remains useful for identifying a
conversation module, but it is not sufficient for conversation ownership.

Home timelines also use `home-conversation-<id>` modules. These must be treated
as thread-scoped modules when the thread filter is enabled; relying only on
`conversationthread-` and `conversation-` misses that shape.

## Exact APK evidence

JADX was used only for navigation. The target APK's DEX/smali established the
model contract.

- Canonical post model: target class observed as `com.x.models.r0`.
- Contextual post model: target class observed as `com.x.models.o1`.
- Timeline post model: target class observed as `com.x.models.l1`.
- `CanonicalPost.toString()` contains the stable labels
  `CanonicalPost(id=` and `, repliedPostId=`.
- The field following `, repliedPostId=` has descriptor `Ljava/lang/Long;` on
  this target (observed as field `i`).
- Each reply's `repliedPostId` identifies its immediate parent, allowing
  independently delivered modules to be connected to the focal post.

The obfuscated owners and field name above are evidence for this frozen APK,
not production anchors. The patch resolves the canonical model through the
existing semantic `CanonicalPost(id=` fingerprint, resolves the field by the
`, repliedPostId=` label, requires exactly one model match, and verifies the
field type is `java.lang.Long`. Zero matches, ambiguity, or a changed field
contract fails patching rather than guessing.

## Implemented design

`TimelineTextModelAdapterPatch` injects an `Object`-returning
`getPostRepliedPostId` bridge. The injected smali:

1. casts the timeline post to the patch-resolved target type;
2. reads its result object;
3. accepts only the patch-resolved contextual-post shape;
4. follows contextual post to canonical post;
5. reads the patch-resolved `Long repliedPostId` field; and
6. returns `null` for a non-contextual result.

No obfuscated descriptor is present in extension source and no runtime
reflection is introduced.

While processing every post child in a conversation module,
`NewXTimelineFilter` now:

1. associates the post ID with the module root;
2. associates the post ID with its immediate `repliedPostId`, when present; and
3. records a removed verified-account reply as before.

`FilteredRepliesStore` now maintains a bounded alias graph from any observed
post ID to the currently known conversation root. Parent links may arrive after
a reply has already been buffered, so joining two roots also migrates and
deduplicates their buffered replies. Alias updates are transitive, and lookups
from the focal post, an intermediate parent, the module root, or the hidden
reply all resolve to the merged buffer.

This is capability-based rather than version-routed: releases are selected by
the semantic model shape, and the common extension mutation does not branch on
an app version or hardcoded obfuscated owner.

## Discarded approaches

- Treating the module entry ID as the definitive focal ID failed on incremental
  post-detail payloads.
- Treating a bare numeric direct-post entry ID as a conversation root would
  require screen-state heuristics and could incorrectly group ordinary home
  timeline posts.
- Looking up only by captured reply ID cannot connect a sibling or descendant
  module to the selected focal post.
- Hardcoding `com.x.models.r0.i` would work only for the inspected build and
  violate the runtime-boundary policy.
- Runtime reflection over obfuscated model accessors would defer contract
  failure until users run the patched app.

## Validation

Regression coverage includes:

- `home-conversation-` modules use thread filtering and retain their captured
  replies;
- any post associated with a conversation root can retrieve its replies;
- buffers captured before all parent links arrive are migrated along an
  incremental parent chain;
- a focal post delivered separately from its reply module resolves the hidden
  descendant through `repliedPostId`; and
- existing deduplication, bounds, timeline filtering, and older conversation
  module behavior remain covered.

The focused extension suite passed with 154 tests. The patch bundle built, and
an exclusive patch of the frozen APK reported both `Applied` and `Saved to`.
The patched APK was installed on a physical device and tested from Home -> an
ordinary post -> overflow menu -> **Filtered replies**. Instrumented lookups for
three focal post IDs returned 8, 6, and 7 buffered replies respectively and
opened the dialog; none produced the empty-state toast. The same trace showed
the store merging replies originally observed under different module-root IDs.

Temporary diagnostics used for that proof were removed before the final build
and commit.

## Known limits

- The store is process-local and bounded; it is intentionally not a durable
  archive of filtered replies.
- A future product refactor that removes `repliedPostId` or changes its type
  will fail the semantic resolver and requires a newly proven shape adapter.
- A payload that omits both parent relationships and a shared canonical root
  cannot be safely joined without another semantic ownership signal; the code
  must not guess in that case.
