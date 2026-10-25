# NewX runtime hot-path optimization spec

## Scope and evidence

Optimize extension work at existing hooks. The source audit found repeated work; it did
not measure frame time or establish that any one hook causes scrolling jank. Do not
change patch resolvers, hook placement, descriptors, compatibility metadata, or add
dependencies. Keep Android API 29 compatibility. Dexscope reconnaissance of 12.37
hook alternatives is deferred in `todo.md`.

## 1. Feature-switch observations

Owned files: `extensions/newx/src/main/java/app/morphe/extension/newx/featureswitches/FeatureSwitchStore.java`
and its existing `FeatureSwitchStoreTest.java`.

Current: every intercepted accessor calls `observations.compute`, writes two volatile
fields, and defensively copies observed list values. List overrides are copied on
every read even though stored overrides are already immutable snapshots.

Implementation:

- Represent an observation as an immutable type/value pair. Keep defensive copying
  when publishing a new observation.
- Read the existing observation with `get`. If its type and value equal the current
  input, skip `compute`, writes, and copying. On a change, use `compute` and recheck
  against its current entry before replacing it. Preserve latest-observed semantics
  for overlapping calls; do not introduce locks or retain a mutable caller list.
- Read one observation pair per snapshot entry. Preserve concurrent map safety.
- Reuse an override's already immutable value on resolve; keep caller-input and
  snapshot isolation. Do not cache resolved values across override changes.

Acceptance: repeated equal reads avoid observation publication/list copying; changed
values, changed types, mutated input lists, nulls, and overrides still work. Extend
the existing suite for mutable-list changes and immutable returned override reuse.
Main agent owns this change because its correctness involves concurrency.

## 2. Lazy Compose emoji text copying

Owned production file: `extensions/newx/src/main/java/app/morphe/extension/newx/misc/UpdateFont.java`.
Test file, if needed: `extensions/newx/src/test/java/app/morphe/extension/newx/misc/UpdateFontTest.java`.

Current: with custom emoji enabled and loaded, every nonempty text gets a
`SpannableStringBuilder`, even if no emoji needs replacement.

Implementation: scan the original `CharSequence` with existing emoji boundary logic.
Allocate the builder only at the first matched emoji; preserve all offsets, remove
only AndroidX emoji replacement spans in each matched range, and add the same custom
span as before. Return the input object when no replacement occurs. Never mutate the
input; preserve non-emoji spans and all current Unicode sequence handling. No global
text cache or font-state changes. A new Robolectric test suite is justified only to
guard input-span mutation, no-emoji identity, and sequence/span regressions through
the real processing entry point. Ask the parent before extracting a new API.

## 3. Inline download UI work

Owned files: `extensions/newx/src/main/java/app/morphe/extension/newx/misc/InlineDownloadButton.java`
and its existing `InlineDownloadButtonTest.java`.

Current: composing an action row can eagerly fetch repost media even when canonical
media already qualifies. Capturing diagnostic lambdas are created on disabled-log
paths. A new native action and copied output list are created for eligible rows.

Implementation:

- In `mediaFor`, resolve/test canonical media first and return it immediately if
  downloadable. Resolve repost fields only when canonical media does not qualify.
  Preserve canonical-before-repost selection and click-time download behavior.
- Keep `hasMedia` a boolean path: inspect canonical then repost media with early
  success, without constructing download metadata or replacement lists.
- Guard capturing info-log lambdas in `addAction`, `markIconSize`, and both
  `selectIcon` overloads with `NewXLogger.isLoggingEnabled()`.
- Keep per-row native action creation, list ownership, weak identity classification,
  icon tagging, click routing, and live settings behavior intact. Do not add caches
  keyed by presenter, post ID, media-list identity, or native list identity.

Acceptance: existing media/icon/event tests pass; media precedence and hide-no-media
behavior remain unchanged. Action/list reuse and replacing `toString` media parsing
require native-contract proof and are explicitly deferred to dexscope recon.

## 4. Timeline filtering calls

Owned files: `extensions/newx/src/main/java/app/morphe/extension/newx/timeline/NewXTimelineFilter.java`,
`extensions/newx/src/main/java/app/morphe/extension/newx/postfilter/PostFilterRuleStore.java`,
and existing `NewXTimelineFilterTest.java` (and existing postfilter tests if necessary).

Current: a conversation module records child authorship in a prepass, then records
the same child's authorship again in `filterItem`. Enabled username-only rules still
fetch post text and strip mentions. Verified-only filtering fetches a screen name
before it is needed.

Implementation:

- Have the authorship prepass report completion. Only skip per-child recording when
  the prepass proved that child pass completed without failure; if any child read
  fails, retain the existing per-child retry path for the module. Top-level posts
  and modules without a prepass continue recording exactly as required.
- Add concise snapshot queries `hasContentRules()` and `hasUsernameRules()` based
  on the existing immutable phrase collections (no matcher construction).
- Fetch/prepare keyword text only for content rules. Fetch the keyword screen name
  only for username rules. Keep verified whitelist reads in their existing resolver.
- Evaluate verified rejection before preparing keyword inputs, while preserving
  own-thread exemptions, recording filtered replies, and filter outcomes.
- Preserve filtering identity, lazy output copies, fail-open exception behavior,
  and sibling-order-independent authorship discovery. Do not memoize model reads
  across passes or change native bridge methods.

Acceptance: existing suite passes; extend it with meaningful model-access counters
to prove username-only rules do not read text, verified-only paths avoid unnecessary
screen-name reads, prepass success removes duplicate reads, and prepass failure still
retries. No standalone tests for the trivial snapshot queries.

## Verification and handoff

Delegates should inspect their diffs, then report them; main agent runs Gradle once
after edits to avoid competing builds. Verification command for every delegate:
`./gradlew :extensions:newx:testDebugUnitTest :extensions:newx:lintRelease --no-daemon`.
Main agent also runs `./gradlew :patches:build --no-daemon` to build the actual bundle.
Record results here after review. Do not install or control a device. No performance
speedup claim without measurement; a device scrolling check remains a user follow-up.

## Implementation review and validation (2026-10-25)

- Implemented sections 1–4 at the existing hooks. Three Luna implementers owned the
  independent emoji, inline, and timeline changes; the main agent implemented the
  concurrent observation change, reviewed all diffs, and ran coordinated verification.
- Source: `284dfde05bb696ba83bf6982a2868e7f99d62a7b` plus this working-tree change.
- `./gradlew :extensions:newx:testDebugUnitTest :extensions:newx:lintRelease :patches:build --no-daemon`:
  passed. 263 extension tests and 78 patch tests, no failures/errors/skips; API 29 lint passed.
  Initial test compile caught a counter override returning `String` instead of `Object`;
  corrected before the successful run.
- Bundle: `patches/build/libs/patches-3.11.0-dev.1.mpp`, SHA-256
  `6dcbd85d9b8daf946ffc3ebf4528757ddad9b5df863addc0c003447985a31119`.
- Existing `TimelineFilterBaselineTest` corpus, 2,000 posts: keyword-content now
  reports 4,000 bridge calls, keyword-username 2,000, verified-type 5,198. The
  documented baseline in `docs/newx-runtime-baseline.md` records 6,000 / 6,000 /
  7,198 respectively. Removal counts remain 100 / 100 / 398. This is a call-count
  comparison with the documented baseline, not a fresh before/after timing benchmark.
- Regression tests exercise username/content scope, verified-only screen-name reads,
  completed authorship prepass deduplication, transient wrapper/author-read failures,
  mutable observed-list isolation, and immutable override reuse/live replacement.
- Emoji diff preserves the same Unicode boundary logic and span edits, with the
  builder created only on first match. No new emoji unit suite: the real entry point
  freezes its setting and loads the font/context during static initialization; the
  current test harness has no safe setup for that path. Span/emoji runtime checks
  remain in the manual follow-up; no production test seam was added.
- No APK patch run, dexscope recon, installation, device control, or frame-time
  measurement in this implementation pass. Native action/list reuse and typed media
  parsing remain deferred in `todo.md`; eligible rows still create their own action/list.
