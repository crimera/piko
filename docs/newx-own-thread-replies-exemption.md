# NewX own-thread-replies exemption

## Problem

With timeline filtering off and thread filtering on, a filtered author's own
post stays visible on the timeline, but their follow-up replies inside their
own thread are hidden. Thread posters commonly reply to their own posts to
build a thread, so hiding those self-replies guts the conversation the user
chose to open.

## Rule

A reply that would otherwise be hidden by verified-account type is kept when
walking its `repliedPostId` chain reaches a head post by the same author ID.
Only the head author counts: a nested self-reply inside somebody else's
thread is still hidden. A post with no parent link is not a reply and is
never exempt. An unknown ancestor fails closed to hidden; the reply still
lands in Filtered Replies.

No toggle. No version routing. No patch change: the exemption reuses the
existing `getPostAuthorId` / `getPostRepliedPostId` bridges.

## Implementation

- `FilteredRepliesStore` keeps two bounded (500-entry, LRU) fact maps beside
  the alias graph: `postToAuthor` and `postToParent`. Parent edges are factual
  reply relations used only by the exemption walk; they never merge reply
  buffers, so unrelated timelines cannot join conversations through them.
- `NewXTimelineFilter.filterModule` runs an authorship pre-pass over the
  module's children before any filter decision, so siblings are visible to
  the walk regardless of child order. Per-child read failures only degrade
  the exemption back to hiding.
- `filterItem` records authorship for every visited post while verified
  filtering is active in either scope, so a focal post delivered as a direct
  timeline post is known when later reply modules walk past it. Zero overhead
  when no verified types are hidden.
- `isVerifiedAuthorToHide` exempts only after the type match and whitelist
  checks pass. The walk is bounded at 32 hops; exhaustion, self-loops, and
  unknown ancestors all fail closed to hidden. Model-read exceptions keep the
  existing fail-open timeline semantics via the surrounding `filterItem` /
  `filterObject` handlers.

## Validation

- `keepsThreadOwnerFollowupThroughParentChain`: timeline-off focal plus a
  replier and an owner follow-up in a later module; both kept, nothing
  buffered.
- `hidesStrangerReplyInOwnerThread`: same setup with a stranger reply;
  hidden and buffered under the focal post.
- `hidesOwnReplyWhenParentAuthorUnknown`: unobserved parent; hidden.
- `hidesNestedSelfReplyInAnotherAuthorsThread`: same author replying to
  their own reply inside another author's thread; both hidden and buffered.
- Store facts round-trip (`tracksAuthorshipAndParentEdges`), including the
  self-loop guard.
- Full extension suite green (timeline 48, store 8, no failures); pre-existing
  verified-filter tests unchanged, since fakes without parent links are never
  exempt.

## Known limits

- A payload that omits `repliedPostId` for an intermediate ancestor cannot be
  walked and stays hidden until the link is observed.
- Authorship facts are process-local and bounded like the rest of the store;
  LRU eviction of an ancestor degrades to hiding, never to wrongly keeping.
