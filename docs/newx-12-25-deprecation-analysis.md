# NewX 12.24/12.25 deprecation analysis

- Date: 2026-09-14
- Question: drop `12.24`/`12.25` support and baseline on `12.26`, focusing on `12.26` + `12.27`?
- Verdict: **yes, deprecate 12.24/12.25.** The removal buys almost no resolver-code deletion (the `12.20`–`12.23` shims are already gone), but it buys a large validation/triage saving. `12.26` is shape-identical to `12.25` for every model checked and rename-only vs `12.24`; `12.27` is the real structural break and already carries its own capability adapters, so the old targets only add R8-rename triage with zero signal.

Targets today (`../patches/src/main/kotlin/app/crimera/patches/newx/utils/Constants.kt`, `COMPATIBILITY_NEW_X`):

```kotlin
AppTarget(version = "12.24.0-prod.02"),
AppTarget(version = "12.25.0-alpha.01"),
AppTarget(version = "12.25.0-prod.01"),
AppTarget(version = "12.26.0-alpha.01", isExperimental = true),
AppTarget(version = "12.26.0-alpha.02", isExperimental = true),
AppTarget(version = "12.26.0-alpha.03", isExperimental = true),
AppTarget(version = "12.27.0-alpha.01", isExperimental = true),
```

Proposal removes the first three, leaving four (`12.26×3` + `12.27×1`).

APKs used as bytecode truth (`./apks`, `com.twitter.android`):

| Shorthand | File |
|---|---|
| 12.24 | `twitter_12.24.0-prod.02-merged.apk` |
| 12.25a | `twitter_12.25.0-alpha.01.apk` |
| 12.25p | `twitter_12.25.0-prod.01.apk` |
| 12.26a1/a2/a3 | `twitter_12.26.0-alpha.0{1,2,3}.apk` |
| 12.27 | `twitter_12.27.0-alpha.01.apk` |

Prior art: `ea5edef5 refactor(newx): deprecate versions` already removed the `12.20`–`12.23` shims (deleted ~945 lines across 17 files: `LegacyShareSheetCopyFingerprint`, `InlineActionEntryModelWithoutCountFingerprint`, alpha Horizon-tab fallback, beta feature-switch accessors, combined-timeline legacy seed, public-field TODOs). There are **no remaining `ALPHA PATH` / `BETA PATH` comments** in `patches/src/main/kotlin/app/crimera/patches/newx` — verified with a scoped `rg`. What remains is capability duality needed for `12.26`+`12.27` itself, not `12.24`/`12.25` legacy.

---

## 1. Is it worth removing 12.24/12.25?

### 1.1 Inventory: multi-shape / optional resolvers in scope

All paths below were read directly; `OrNull`/`atMostOne` hits that are plain null-safe navigation (`classDefByOrNull`, `firstOrNull`, `getOrNull`) are excluded — only semantic multi-shape/optional branches are listed.

| # | Location | Branch | Live on 12.24–12.27? | Removed by deprecation? |
|---|---|---|---|---|
| 1 | `../patches/src/main/kotlin/app/crimera/patches/newx/models/PostModels.kt` — `resolveInlineActionBarModels()` (~340–410) | Two fingerprints flattened+`distinctBy`: collection flow (`hasInlineActionCollectionResultFlow`: canonical-post interface → collection → iterator → `CHECK_CAST` to entry → `ArrayList.add`) vs direct flow (`hasInlineActionResultFlow`: contextual-post → single entry → `ArrayList.add`); expects exactly 1 | Collection path is live in all four (canonical `inlineActionEntry` is `Lkotlinx/collections/immutable/b;` in 12.24 `r0`, 12.26 `r0`, 12.27 `s0`). No contextual-post single-entry getter exists in the inspected presenters (`o1`/`p1` expose canonical/reposted/media, not a bare entry). Direct path matches **zero** in the current range | **No.** Candidate for independent dead-code removal with patch-matrix validation, not a version gate. Keep the `requireExactlyOne` cardinality either way |
| 2 | `../patches/src/main/kotlin/app/crimera/patches/newx/misc/inlineactions/GlideThumbnailCachePatch.kt` — `resolveGlideThumbnailRuntimeOrNull()` + `../patches/src/main/kotlin/app/crimera/patches/newx/misc/inlineactions/ThumbnailCachePatch.kt` — `applyThumbnailCacheBridge()` | Optional Glide shape: provider → engine → memory/active caches; absent → Coil-only; present → Glide primary + separately-patched Coil fallback (`COIL_CACHED_THUMBNAIL_HELPER`) so a server-side switch flip after patching stays correct | Present in **all** of 12.24/12.25p/12.26a3/12.27 (Glide singleton `Lcom/bumptech/glide/d` has identical 8 instance fields in 12.24 vs 12.26; only a 1-insn delta in `c()`; class counts `23` Glide / `24` coil3 in every APK). See `docs/newx-resolver-linter/incidents/2026-09-10-thumbnail-glide-12-26.md`: the old **version gate was already removed** and validated on 12.22p, 12.24p, 12.25p, 12.25a, 12.26a — lesson recorded there is "do not route this backend by app version" | **No.** Capability branch, not version branch. Must stay for the remote-switch reason even with a 12.26 baseline |
| 3 | `../patches/src/main/kotlin/app/crimera/patches/newx/models/SemanticIntrospection.kt` — `resolveFieldAccessor()`; `../patches/src/main/kotlin/app/crimera/patches/newx/misc/inlineactions/CustomizeInlineActionsPatch.kt` — `patchActionNameBridge()` | Getter-preferring with public-field fallback (`getActionType()` else `iget-object`; generic `get$suffix`/`is$suffix` else public field via `requirePublicFields`) | Public-field path is live everywhere checked: entry `l4`/`k4` fields `a,b,c` are `public final` in 12.24, 12.26a3, and 12.27; no `getActionType` method exists under that name (obfuscated `a()` etc.). Getter path is future-proofing for a private-model release | **No.** Resilience duality, not legacy. Keep; it is what lets a private-model refactor survive without a new branch |
| 4 | `../patches/src/main/kotlin/app/crimera/patches/newx/misc/videoscrolling/DisableVideoScrollingPatch.kt` — `hasVerticalPagerParameters()` | 18-param pager ABI (`[5] == "F"` page-size float, Play-Core-era holder) vs 17-param ABI (float removed, holder repackaged out of Play Core); both under `Lcom/google/` scope anchored on the `Vertical` enum | 18 = 12.24–12.26, 17 = **12.27** (comment states the move explicitly) | **No — must stay.** This is a 12.26↔12.27 duality. Dropping 12.24/12.25 removes zero branches here |
| 5 | `../patches/src/main/kotlin/app/crimera/patches/newx/misc/canonicalurls/CanonicalUrlsPatch.kt` — `resolveUrlPickerShape()` | `UrlPickerShape.Extracted` (`(String,String)->String` helper) vs `UrlPickerShape.Inlined` (helper inlined into two dispatcher branches); `requireExactlyOne(extracted + inlined)` with wire-serializer-derived field indices | Inlined shape is the **12.27** drift (`UrlEntity` moved `m1 → l1`, picker inlined); see `docs/newx-resolver-linter/artifacts/open_canonical_urls_recon.md` and the sweep incident below | **No — must stay.** 12.26↔12.27 adapter by design, no version `if` |
| 6 | `../patches/src/main/kotlin/app/crimera/patches/newx/models/TimelineModels.kt` — `patchTimelineModelBridges()` immutable-list converter | `INSTANCE_OF` + single `fallbackCalls` (`(Iterable)->List` static in same owner) filter; `isJavaListType()` hierarchy walk | Internal shape filter, identical behavior across the four APKs; not version-gated | **No** |
| 7 | `../patches/src/main/kotlin/app/crimera/patches/newx/timeline/postfilter/TimelineTextModelAdapterPatch.kt` — `resolveAuthorFieldGetter()` / `resolveInterfaceMethod()` | Author accessed through the implemented interface (`MinimalUser` getter lifted to the author descriptor via interface walk), not the concrete class | Tolerant-by-construction; survives the `12.27` author-interface renames | **No** |
| 8 | `../patches/src/main/kotlin/app/crimera/patches/newx/settings/SettingsPatch.kt` + `../patches/src/main/kotlin/app/crimera/patches/newx/settings/Fingerprints.kt` | Multi-caller collapse: `ComposeSettingsBasicItemCallerFingerprint` (`Lcom/x/settings/common/` + `COMPOSE_SETTINGS_BASIC_ITEM_PARAMETERS`) → dedup to one renderer → `composeSettingsBasicItemFingerprint`; tolerates Composer-interface vs runtime-impl prologue typing | Needed for Compose lowering in all targets; 12.27 needed its own renderer repair (`ee13d2f7`) but the dedup pattern stays | **No** |

Net: **no resolver block is uniquely attributable to 12.24/12.25.** The `ea5edef5` cleanup already took the version-specific wins. What deprecation deletes is:

- 3 entries in `Constants.kt` (`12.24.0-prod.02`, `12.25.0-alpha.01`, `12.25.0-prod.01`).
- Their rows in any target matrix / `patches-list.json` compatibility entries / docs.
- Nothing else in `models/`, `timeline/`, `settings/`, or `misc/inlineactions/` — I would not touch the dual-shapes above in the same commit; items 1 (direct bar path) and 2 (Glide `OrNull` strictification) are separate proposals requiring their own patch-matrix proof.

### 1.2 Concrete maintenance + performance benefits (the real case)

These are smaller than "delete 500 lines" but they compound, and they are the honest reason to do it:

- **Validation matrix 7 → 4 targets (~43% fewer full patch runs).** Every target costs a full MPP build + exclusive patch + `Applied`/`Saved to` check (~110–190 MB APK, minutes each). The 12.27 sweep (`docs/newx-resolver-linter/incidents/2026-09-13-12-27-sweep.md`) shows the cost model: each new release already needs isolated + full-catalog runs on at least the control (`12.26a3`) and the target (`12.27a1`). Carrying three extra old targets triples the per-change tail.
- **R8-rename triage drops from ~5 distinct renames to 2.** Measured owners (`check-stability --kind string`):
  - `UrtTimelinePost`: 12.24 `k1` / 12.25p+12.26a3 `j1` / 12.27 `l1`
  - `ContextualPost`: 12.25a `q1` / 12.26a1+a2 `p1` / 12.25p+12.26a3 `o1` / 12.27 `p1` (fresh rename, same label set)
  - `CanonicalPost`: 12.25a `t0` / 12.26a1+a2 `s0` / 12.25p+12.26a3 `r0` / 12.27 `s0` (fresh rename + `isPreview`)
  - `InlineActionEntry`: 12.25a `m4` / 12.26a1+a2 `l4` / 12.26a3 `k4` / 12.27 `l4`
  
  Every supported build is a fresh obfuscation with identical semantics. Old targets contribute rename noise and zero resolver signal — when a fingerprint fails you currently diff up to 5 renames before concluding drift; after deprecation you diff 2.
- **No per-run patch-time speedup for a single APK** (fingerprint scan cost is per-APK, not per-target-list). Do not claim it. The perf win is CI/dev-loop throughput and daemon cache pressure: fewer giant APKs pinned in `dexscope` RAM (`DEXSCOPE_MAX_CACHED_APKS=2` default already thrashes on a 4-APK sweep; 7 targets guarantee it), less `~/.cache/dexscope` scope churn, ~350 MB less APK storage if old files are archived out.
- **Failure budget.** Per the repo's fail-closed rule (`../patches/src/main/kotlin/app/crimera/patches/newx/utils/ResolverCardinality.kt` — `requireExactlyOne`/`requireAtMostOne`), every extra target is another chance for an ambiguous/zero-match `PatchException` that blocks the catalog. The 12.27 sweep catalogued **11 independent failures** from one release's drift; old releases can only add failure modes, never fix new ones.
- **12.25p ≈ 12.26a3 (keep-cost exceeds keep-value).** 12.25p and 12.26a3 share owners for timeline post (`j1`), module (`a1`), module-item (`d1`), contextual (`o1`), canonical (`r0`), presenter (`k0`, 25 fields, `42`-reg/`98`-insn constructor), scroll holder (`n0`) and scroll getter (`z.d()`, offset 54). Users on 12.25p lose nothing moving to 12.26; keeping 12.25p only preserves a near-duplicate rename.

### 1.3 What deprecation must NOT do

- Do not collapse the 12.26↔12.27 adapters (pager 18/17, URL-picker extracted/inlined, Coil loader-owner-or-impl, reply-sorting enum, photoviewer capability resolver). Those are the price of supporting 12.27 at all.
- Do not strictify Glide `OrNull` → strict or delete the direct bar path in the same change. Both need isolated before/after patch runs on 12.26a3 + 12.27a1 (and ideally 12.26a1 for the rename variant) with `lintNewxResolvers` green, per the linter-experiment process in `AGENTS.md`.
- Do not treat "lower finding count" as success when touching `ResolverCardinality.kt` or the linter — the committed benchmark (`docs/newx-resolver-linter-benchmark.md`) and fixture corpus (`NewXResolverLinterTest.kt`) are the gate.

---

## 2. Is 12.26 similar in shape to 12.27?

**Models: yes (one additive field). Callsites/Compose/compiler: no — 12.27 is the break.** That split is exactly why 12.26 is a good baseline and 12.24/12.25 are dead weight.

### 2.1 Timeline items — identical

`check-stability --kind string` across 12.24/12.25p/12.26a3/12.27: `ALL_STABLE`, `method_stability=IDENTICAL`, only `class_stability=SHIFTED` (R8 renames):

| Anchor | 12.24 | 12.25p / 12.26a3 | 12.27 |
|---|---|---|---|
| `UrtTimelinePost(postResult=` | `items/k1;` | `items/j1;` | `items/l1;` |
| `UrtTimelineModule(innerContent=` | `items/b1;` | `items/a1;` | `items/c1;` |
| `UrtTimelineModuleItem(item=` | `items/e1;` | `items/d1;` | `items/f1;` |
| `VerticalConversation(allTweetIds=` | `timelinemodule/k;` | `k;` (identical) | `k;` (identical) |

`data-class` field-label sets are byte-identical across all four:

- post: `postResult, sortIndex, entryId, socialContext, promotedMetadata, prerollMetadata, clientEventInfo, displayType, hostingModuleMetadata, feedbackKeys, feedbackMetadata, facepile`
- module: `innerContent, moduleHeader, moduleFooter, displayType, sortIndex, entryId, clientEventInfo`

`TimelineModels.kt` (`resolveTimelineModels`, 7-arg module constructor derived patch-time; `TimelineItemsImmutableListConverterFingerprint`) needs no 12.27 branch. Timeline `Success` (`../patches/src/main/kotlin/app/crimera/patches/newx/timeline/NewXTimelineSuccessFingerprint.kt`, `(L,L,L,Z,Z)`) is also shape-identical: 12.26a3 `urt/b2;` fields `a,b,c,d,e` + `7`-reg constructor vs 12.27 `urt/a2;` same layout (only owner/type renames `timelines/w→u`, `a2→z1`, `c2→b2`). Scroll-position holder is stable too (`ScrollPositionHolder(firstVisibleItemIndex=` → 12.24–12.26a3 `urt/n0;`, 12.27 `urt/m0;`; getter `z.d()` offset 54 → `y.d()` offset 49 — rename + 5-insn shift, same contract in `RestoreTimelinePositionPatch.kt`).

One caveat: `ClientEventInfo(component=` is `AMBIGUOUS` (2 matches) in **every** APK — `Lcom/x/models/u0;` (the resolver's target, `definingClass="Lcom/x/models/"`) plus `Lcom/x/thrift/onboarding/injections/thriftjava/ClientEventInfo;`. The resolver disambiguates by `definingClass` scope, so this is pre-existing and version-independent, not a 12.27 regression.

### 2.2 Post models — identical except one additive 12.27 field

| Anchor | 12.24 | 12.25p / 12.26a3 | 12.27 |
|---|---|---|---|
| `ContextualPost(canonicalPost=` | `models/o1;` | `models/o1;` | `models/p1;` |
| `CanonicalPost(id=` | `models/r0;` | `models/r0;` | `models/s0;` |
| `InlineActionEntry(actionType=` | `models/l4;` | 12.25p `l4;` / 12.26a3 `k4;` | `models/l4;` |

Label sets:

- contextual: `canonicalPost, quotedPost, rePostedPost, tweetInterstitial, mediaVisibilityResults, softInterventionPivot, quotingPost, quotingAuthor, limitedActions` — identical all four.
- entry: `actionType, count, isEnabled` — identical all four (the `WithoutCount` shape died with pre-12.24; `InlineActionEntryModelWithCountFingerprint` in `PostModels.kt` is the only entry fingerprint).
- canonical: identical **except 12.27 adds `isPreview`**. 12.26a3 `r0` has `F:Z G:Z` (two trailing booleans) and a `51`-reg primary constructor; 12.27 `s0` has `F:Z G:Z H:Z` and a `52`-reg constructor (`...ZZZLcom/x/models/a1;...` vs `...ZZLcom/x/models/z0;...`). All resolvers that read canonical by **label** (`, entityList=`, `, author=`, `, repliedPostId=`, `, inlineActionEntry=`, `, media=` in `PostModels.kt` / `TimelineTextModelAdapterPatch.kt`) are immune; anything positional over the constructor would break — none was found in the resolver set.

Entry classes are `public final` with `public final a,b,c` fields in all three inspected builds, with `(ActionType, Long, Z)` constructors — the `TwitterShare` constant + 3-arg constructor resolution in `resolveInlineDownloadModels()` holds across the range.

### 2.3 Inline actions + repository interfaces — same capability, repackaged in 12.27

Presenter (implements `Lcom/x/presenter/a`, 25 fields, 6 methods) — the class `InlineDownloadButtonPatch.kt` / `CustomizeInlineActionsPatch.kt` resolve via `barModels.inlineActionBarDescriptor`:

- 12.24 `inlineactionbar/k0;` vs 12.26a3 `inlineactionbar/k0;`: **identical layout** — same 25 field roles in order (`navigation`, contextual `o1`, `ye/ze`, `b7/a7`, scribing, `scribe/c`, thrift `Item`, `Z`, `d1`, `Z`, `zg`, `repositories/post/actions/h0`, `share/api/a`, `o0`, in-app-notification, limited-actions, subscriptions, `bookmark/i→j`, `inlineactionbar/a`, `hashflag/m`, coroutines, `Context`, `fst/a`, videoreaction, payments), same `42`-reg/`98`-insn constructor, same static `e(k0,n0,...)` at `164–165` regs / `1355` insns.
- 12.27 `inlineactionbar/i0;`: **same 25 roles, renamed owners** (`p1`, `b1` for `d1`, `m0` for `o0`, `post/actions/i0`, `bookmark/l`, `hashflag/m`, `coroutines/g0`, `payments/w`), but the constructor is outlined down to `26` regs / `40` insns and `d(Composer)` returns `v0` (`451` insns) vs `x0` (`469` insns). Package-level renames accompany it: `d1→b1`, layout `h1→a1`, `c0→a0` flips from interface to `Function1` synthetic.

Repository roles inside the presenter (`l` post-actions, `r` bookmark, `t` hashflag) are stable; only owners shift (`h0→i0`, `i/j→l`, `o→m`). The resolvers derive these patch-time (`presenterPostField` by `contextualPostDescriptor` type match; event handler by `IGET_OBJECT entry.actionType + Enum.ordinal`), so they survive — but this is exactly the class of rename the old targets multiply.

Inline renderer fingerprint in `InlineDownloadButtonPatch.kt` (12 params, `MODIFIER` at index 9, `COMPOSER` at 10, `IGET_OBJECT actionType + IGET_BOOLEAN isEnabled` filters) plus the `ic_vector_share` vs `ic_vector_incoming_stroke` icon feed and the `Enum.ordinal` event-handler gate all resolved on both 12.26a3 and 12.27a1 after repair — with 12.27 needing hoisted-size handling, `move-object/from16` high-register support, and 4-bit scratch allocation fixes (see sweep §2.5).

### 2.4 Compose modifiers — the 12.27 break is here, not in models

- **Pager** (`DisableVideoScrollingPatch.kt`): documented in-code — holder moved out of Play Core, page-size `F` removed. `hasVerticalPagerParameters()` keeps both the 18-param (12.24–12.26) and 17-param (12.27) ABIs under `Lcom/google/` + `Vertical`-enum anchoring. Keep both under a 12.26 baseline.
- **Settings row** (`Fingerprints.kt`, `SettingsPatch.kt`): 10-param `COMPOSE_SETTINGS_BASIC_ITEM_PARAMETERS` + caller-collapse; 12.27 needed a renderer port (`ee13d2f7`) but the pattern holds.
- **Reply bar / dividers / compose button / reply sorting** (`HidePostReplyBarPatch.kt`, `HidePostDividersPatch.kt`, `HideNewPostButtonPatch.kt`, `DefaultReplySortingPatch.kt`): 12.27 removed the Kotlin parameter-name strings the old resolvers keyed on (`"onClick"`, `"$this$threadConnector"`, `"replyFacepileState"`, `"inlineComposer"`, `"hazeState"`, `"rankingMode"`) and moved synthetic `Function0` initializers (`Lcom/x/ui/common/` → `Lcom/x/urt/items/post/u0`). Repairs trace modifier→`AnimatedVisibility` flow, canvas-draw structure, ordered 8-param Compose ABIs, and bytecode-shape `Function0` resolution instead of strings. Full per-patch recon in `docs/newx-resolver-linter/artifacts/` (`hide_compose_button_recon.md`, `hide_post_dividers_recon.md`, `hide_post_reply_bar_recon.md`, `set_default_reply_sorting_recon.md`, `restore_timeline_position_recon.md`, `open_canonical_urls_recon.md`, `inline_download_button_recon.md`, `inline_download_coil_recon.md`).

### 2.5 What actually changed 12.26 → 12.27 (vs 12.24/12.25 → 12.26)

| Layer | 12.24/12.25 → 12.26 | 12.26a3 → 12.27a1 |
|---|---|---|
| Timeline/post/inline-entry models | Rename-only (owners shift, label sets identical) | Rename + **one additive field** (`CanonicalPost.isPreview`, `+1 Z` in constructor) |
| Inlineactionbar package | Identical (`k0`, `d1`, same 25-field layout) | Repackaged (`i0`, `b1`, layout `h1→a1`), constructor outlined (`42/98 → 26/40`) |
| Repositories (post-actions/bookmark/hashflag) | Owner renames only, roles stable | Owner renames only, roles stable |
| Timeline Success / scroll holder | Identical (`b2`, `n0`, `z.d()` off. 54) | Rename + small shift (`a2`, `m0`, `y.d()` off. 49) |
| Pager / Compose ABI | Stable (18-param) | **Break** (17-param, holder moved) |
| Kotlin lowering strings | Stable | **Break** (parameter-name strings stripped across ~6 patches) |
| Coil loader owner | Stable (contract-or-impl accepted after repair) | Drift repaired by accepting provider-return **or** its implementation (`CoilThumbnailCachePatch.kt`) |
| URL picker | Stable (extracted helper) | **Break** (inlined into 2 dispatcher branches; `UrlEntity m1→l1`) — dual-shape adapter added |

The 12.27 port is done and green: `dec951d0` + follow-ups repaired all 11 sweep failures; final state is **40/40 patches applied on 12.27a1**, `:patches:test`, `:patches:build`, and `:patches:lintNewxResolvers` passing (`docs/newx-resolver-linter/incidents/2026-09-13-12-27-sweep.md`, `docs/newx-resolver-linter-benchmark.md`). That work is sunk cost that deprecation preserves; keeping 12.24/12.25 only re-exposes the catalog to stale rename variants.

---

## 3. Recommendation + removal checklist

1. **Deprecate 12.24/12.25.** Edit `Constants.kt`: delete the three `AppTarget` lines, keep the four `12.26`/`12.27` experimentals. No resolver-code change in the same commit.
2. **Update matrices/docs**: `patches-list.json` compatibility entries (`dec951d0` touched 280 lines there — mirror for the removals), `patch-twitter.sh` default target if pinned, any version table in `docs/`.
3. **Archive, don't keep validating**: move the three APKs out of `./apks` (or keep one 12.25p as a cold-case control outside the matrix) to relieve daemon cache pressure; record checksums before deleting.
4. **Separate follow-ups (not this change)**:
   - Prove the direct inline-bar path dead: build with the second fingerprint removed, run exclusive `Customize inline actions` + `Inline download button` on 12.26a1/a3 + 12.27a1, expect `Applied`; only then delete `hasInlineActionResultFlow` + its fingerprint.
   - Revisit Glide `OrNull` only if a target without the shape ever appears; the remote-switch justification in `2026-09-10-thumbnail-glide-12-26.md` outlives any baseline bump.
5. **Validation for the deprecation commit**: `:patches:test`, `:patches:build`, `:patches:lintNewxResolvers`, plus full-catalog patch runs on 12.26a3 (control) and 12.27a1 (target) confirming `Applied` + `Saved to`. Per repo test policy, no new unit tests for the constant removal — the aggregate patch runs are the guard. Log the runs per the incident-log format (`docs/newx-resolver-linter/`).

## 4. Reproduction commands (all scoped, per repo search-safety rules)

```bash
# anchor stability across the four bytecode truths
dexscope check-stability --kind string 'UrtTimelinePost(postResult=' ./apks/twitter_12.24.0-prod.02-merged.apk ./apks/twitter_12.25.0-prod.01.apk ./apks/twitter_12.26.0-alpha.03.apk ./apks/twitter_12.27.0-alpha.01.apk
dexscope check-stability --kind string 'CanonicalPost(id=' ./apks/twitter_12.24.0-prod.02-merged.apk ./apks/twitter_12.25.0-prod.01.apk ./apks/twitter_12.26.0-alpha.03.apk ./apks/twitter_12.27.0-alpha.01.apk
dexscope check-stability --kind string 'Success(timelineType=' ./apks/twitter_12.24.0-prod.02-merged.apk ./apks/twitter_12.25.0-prod.01.apk ./apks/twitter_12.26.0-alpha.03.apk ./apks/twitter_12.27.0-alpha.01.apk
dexscope check-stability --kind string 'ScrollPositionHolder(firstVisibleItemIndex=' ./apks/twitter_12.24.0-prod.02-merged.apk ./apks/twitter_12.25.0-prod.01.apk ./apks/twitter_12.26.0-alpha.03.apk ./apks/twitter_12.27.0-alpha.01.apk

# shape detail (representative)
dexscope data-class ./apks/twitter_12.26.0-alpha.03.apk 'Lcom/x/models/r0;'          # no isPreview
dexscope data-class ./apks/twitter_12.27.0-alpha.01.apk 'Lcom/x/models/s0;'          # + isPreview
dexscope inspect-class ./apks/twitter_12.26.0-alpha.03.apk 'Lcom/x/inlineactionbar/k0;'  # 25 fields, 42/98 ctor
dexscope inspect-class ./apks/twitter_12.27.0-alpha.01.apk 'Lcom/x/inlineactionbar/i0;'  # 25 roles, 26/40 ctor
dexscope inspect-class ./apks/twitter_12.26.0-alpha.03.apk 'Lcom/bumptech/glide/d;'      # Glide shape present
dexscope inspect-class ./apks/twitter_12.26.0-alpha.03.apk 'Lcom/x/models/k4;'           # public final a,b,c
dexscope search-classes ./apks/twitter_12.26.0-alpha.03.apk --implements 'Lcom/x/presenter/a'  # presenter lookup
```

Code search was kept to one narrow package directory per `rg` invocation (never repo-wide), and large `dexscope` output was piped through `head`/`grep` per the APK-analysis skill's token rules.
