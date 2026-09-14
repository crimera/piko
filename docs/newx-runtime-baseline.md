# NewX runtime baseline (post items 1,2,4,5,6)

Purpose: fixed numbers to compare future runtime optimizations against.
Re-run the guard (`TimelineFilterBaselineTest`, 9 tests, always green) and
diff the BASELINE lines in the test XML system-out.

- Date: 2026-09-14
- Source commit: `5fc1d2fe` + uncommitted items 1,2,4,5,6 (filter lazy-fetch,
  logger Setting cache, filter reorder, whitelist/module lazy entryId)
- APK: `apks/twitter_12.27.0-alpha.01.apk` (116,310,120 bytes)
- MPP: `patches-3.9.0-dev.4.mpp`
- Patches applied on device: 42/42, 0 `Failed NewX`, gfxinfo p50 13ms
- Machine: macOS, JVM unit tests (stubs — bridge methods return fields, so ms
  is a floor; on-device delta is larger due to reflection/`toString`/substring)

## Fixed corpus

- Posts: 2000 (1500 plain + 100 promoted entryId + 50 promoted metadata +
  100 keyword-text + 100 keyword-username + 50 AI disclosure + 100 plain;
  400 carry verified types: 300 User, 100 Business; 2 whitelisted authors)
- Modules: 200 x 4 children (150 plain, 20 who-to-follow, 10 discover-more,
  20 conversation-thread)
- Inline actions: 12 entries, 4 hidden ids
- Matcher: 2000 texts, 20 phrases, 100 hits
- Method: 2 warmup runs, 5 measured, median reported

## Per-feature baseline (current code)

| feature | items | removed | bridgeCalls | medianMs | round 2 |
|---|---|---|---|---|
| promoted | 2000 | 150 | 2000 | 0.51 | 0.44 |
| who-to-follow | 200 | 20 | 200 | 0.23 | 0.24 |
| discover-more | 200 | 10 | 200 | 0.06 | 0.06 |
| keyword-content | 2000 | 100 | 6000 | 1.07 | 1.10 (single-phrase indexOf) |
| keyword-username | 2000 | 100 | 6000 | 0.54 | 0.57 |
| ai-generated | 2000 | 50 | 2100 | 0.33 | 0.31 |
| verified-type | 2000 | 398 | 7198 | 1.07 | 0.61 (noise; path untouched) |
| inline-actions | 12 x 2000 runs | 6/run | n/a (toString parse) | 7.53 total | 7.98 total (noise) |
| matcher-direct | 2000 x 20 phrases | 100 hits | n/a | 1.41 | 1.01 (automaton, -28%) |

Notes:
- promoted bridgeCalls = post entryId only; text/author fetches are 0
  (were 2000/2000 pre-fix — see optimizations doc).
- verified removed = 400 typed - 2 whitelisted.
- keyword-content calls = text + mentions + author per post (all required).
- ai calls = 2000 disclosure reads + 50 checks + 50 source reads.

## Fingerprint anchor cardinality (dexscope, target APK)

All anchors resolve to exactly one method on 12.27.0-alpha.01:

| anchor | owner on 12.27.0-alpha.01 |
|---|---|
| `Success(timelineType=` | `Lcom/x/urt/a2;->toString()` |
| `ScrollPositionHolder(firstVisibleItemIndex=` | `Lcom/x/urt/m0;->toString()` |
| `Restoring scrolling position for ` | `Lcom/x/urt/y;->d()Lcom/x/urt/m0;` |
| `Saving scrolling positions for ` | `Lcom/x/urt/y;->j(Lcom/x/urt/r1;)V` |
| `AvailablePost(entryId=` | `Lcom/x/urt/items/post/e5;->toString()` |
| `InlineActionEntry(actionType=` | `Lcom/x/models/l4;->toString()` |

Recorded via `dexscope anchors <apk> --string <anchor>`.
If a future version shows 0 or 2+ hits, the resolver needs work — do not
broaden matching to regain speed without a compatibility entry + test.

## verify-diff baseline (original -> patched)

`dexscope verify-diff apks/twitter_12.27.0-alpha.01.apk <patched>`:
3382 modified methods, **0 invalid** (122 valid, 3260 inconclusive from
unmodeled exception flow / reference assignability — verifier limits, also
hit stock compose methods). Exit 0.

## Open (compare against this file after implementing)

Done in round 2 (this file, `medianMs` round-2 column): items 3, 7, 8, 9, 10,
11, 12. Remaining idea if rule counts explode (100s): revisit automaton
transition storage; current array-frozen trie is fine to that scale.
