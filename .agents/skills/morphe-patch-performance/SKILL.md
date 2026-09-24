---
name: morphe-patch-performance
description: Audit and optimize Morphe Android patch execution, fingerprint searches, dependency initialization, and mutable bytecode work without weakening match correctness. Use when patches are slow, fingerprint scans dominate patching, profiling has cache/order ambiguity, or a new/ported patch needs a performance review.
compatibility: Morphe patch repositories with access to the exact target APK and patcher source or dependency version.
metadata:
  version: "1.0"
---

# Morphe Patch Performance

Optimize only after measuring a cold sequential patch run. Preserve semantic evidence and exact cardinality.

## Read First

Load these companion skills:

- `fingerprinting-guide`
- `morphe-patch-development-workflow`
- `patcher-advanced-apis` when changing lookup or mutation code
- `build-deploy-troubleshoot` when building or running the patcher
- `xlite-alpha-porting` for X-Lite work

Inspect the exact Morphe patcher version used by the repository. At minimum, read:

- `Fingerprint.kt`
- `BytecodePatchContext.kt`
- the class-map/index implementation
- mutable class/method proxy implementations
- patch dependency execution code

Do not assume lookup complexity from API names or documentation. It can differ between `matchOrNull()`, `matchAllOrNull()`, exact strings, partial strings, exact owners, and owner prefixes.

## Workflow

### 1. Freeze the benchmark

Record:

```text
APK package/version/ABI:
Exact APK path:
MPP path/version:
Morphe patcher version:
Selected patch set and order:
Profiling enabled:
Cold or warmed process:
```

Run the sequential bundle before isolated diagnostics. Static fingerprint, class-scope, method-index, dependency, and mutable-proxy caches make later runs artificially fast.

Report these separately:

1. Resource decode
2. DEX decode
3. Dependency execution
4. Fingerprint/search resolution
5. Mutation/helper execution
6. Patch-own execution
7. Bundle-incremental execution
8. Finalization/write/signing when measured

Use at least three fresh processes for a release benchmark and report the median. Never combine decode time with patch execution unless clearly labeled end-to-end.

### 2. Inventory searches narrowly

Scope searches to the patch package under review; never launch broad searches over decompiled/generated trees.

```bash
rg -n 'match(All|OrNull)|originalClassDef|classDefBy|mutableClassDefBy|Fingerprint\(' \
  patches/src/main/kotlin/<app-package>
```

For every lookup, record:

| Field | Required evidence |
|---|---|
| Caller/patch | Exact source location |
| Required cardinality | `0`, `1`, exact `N`, or justified range |
| Owner stability | Exact stable owner, preserved prefix, chained owner, or unknown |
| Candidate anchor | Exact string, partial string, call/field chain, method shape |
| Actual matches | Count plus matched descriptors |
| Cold search time | First sequential use |
| Warm search time | Diagnostic only |
| Mutation time | Separate from lookup |

### 3. Classify actual patcher behavior

For current Morphe versions, verify these implementation paths rather than assuming them:

- A single-match lookup with an exact `definingClass` may use the descriptor-keyed class map.
- `matchAllOrNull()` may not pre-scope from `definingClass`; it can still iterate global candidates and reject each method.
- Exact built-in `string(...)` filters may use a global opcode-string index.
- Legacy `strings = ...` uses partial `contains` matching and may inspect every class containing strings.
- Non-built-in/custom filters can disable indexed candidate selection.
- Multiple exact strings may produce duplicate candidates that the patcher later deduplicates.
- First construction of a global string/method index can dominate the patch row that happens to trigger it.
- Accessing mutable `classDef`/`method` can materialize proxies; `original*` access avoids that when only reading.
- A public patch row can include transitive dependencies, so its elapsed time is not necessarily its own body.

If the installed source differs, follow the installed source.

### 4. Choose the narrowest correct lookup

Use this order:

| Known target | Preferred resolution |
|---|---|
| Stable exact owner and method contract | Descriptor map lookup, local method filter, exact cardinality |
| Stable exact owner with instruction evidence | Match against that one `ClassDef` |
| Preserved package/owner prefix | Prefilter class descriptors, then match only those classes |
| Stable semantic caller | Resolve caller, extract referenced owner/member, then exact-owner match |
| Exact semantic string | Ordered `string(...)` filter plus method shape |
| Unknown owner but stable semantic evidence | Fingerprint with asserted cardinality |
| Only broad method shape | Improve evidence first; use a cached shape index only as a measured last resort |

#### Exact owner with all-match semantics

Do not call global `matchAll()` when the owner is already known:

```kotlin
val owner = context.classDefByOrNull(STABLE_OWNER)
    ?: throw PatchException("Missing target owner: $STABLE_OWNER")
val matches = TargetFingerprint.matchAllOrNull(owner).orEmpty()
requireMatches("target methods", matches, expectedCount = 2)
```

#### Preserved package scope

When complete cardinality is required across a package, prefilter class descriptors once per `BytecodePatchContext`, then match each current class from the class map.

Cache descriptors, not `ClassDef` objects. Patches can replace immutable classes with mutable proxies; cached class objects can become stale.

```kotlin
val matches = scopedDescriptors("Lcom/example/models/")
    .flatMap { descriptor ->
        val classDef = context.classDefByOrNull(descriptor) ?: return@flatMap emptyList()
        TargetFingerprint.matchAllOrNull(classDef).orEmpty()
    }
requireMatches("model adapters", matches, expectedCount = 3)
```

Build the all-descriptor list once per context, then cache scoped subsets. Do not rescan the global class map for every package prefix.

#### Caller chaining

Prefer stable caller evidence over a release-obfuscated callee owner:

1. Resolve a preserved caller with semantic strings/calls.
2. Extract the exact invoked method or accessed field reference.
3. Resolve that exact owner through the class map.
4. Match locally and assert cardinality.

Never persist the observed obfuscated descriptor as a production constant.

### 5. Preserve correctness while optimizing

Mandatory rules:

- Exact target APK bytecode is truth; verify instruction order in smali.
- Never replace all-match behavior with first-match behavior to gain speed.
- Assert required counts and include matched descriptors in failures.
- Nullable matching is only valid for genuinely optional behavior or explicit shape variants with combined cardinality.
- Prefer ordered exact `filters = listOf(string(...))` over legacy partial `strings` after bytecode verification.
- Do not add unstable obfuscated names, numeric method positions, Compose group keys, or arbitrary literals.
- Keep extension runtime boundaries to `Object`, primitives, strings, collections, and verified stable models.
- Do not cache instruction indexes or mutable method/class objects across mutations.
- Mutate highest instruction indexes first or rematch after edits.
- Remove dead zero-match compatibility paths only when compatibility declarations and exact APK evidence prove them unreachable.

Use a clear cardinality helper:

```kotlin
fun requireMatches(label: String, matches: Collection<Match>, expected: Int): List<Match> {
    if (matches.size == expected) return matches.toList()
    throw PatchException(
        "Expected $expected $label matches, found ${matches.size}: " +
            matches.joinToString { it.originalMethod.toString() },
    )
}
```

### 6. Instrument without changing normal behavior

Profiling must be opt-in through a system property or environment variable. Disabled mode should execute the block directly.

Log one line per operation:

```text
[Patch profile] search | label | 12.345 ms
[Patch profile] result | label | matches=2 | targets=L...;->a(...)V, L...;->b(...)V
[Patch profile] function | mutate adapters | 4.321 ms
[Patch profile] dependency | A -> B -> C | own=8.100 ms
```

Required profiler properties:

- Monotonic clock (`System.nanoTime()`)
- `Locale.ROOT` numeric formatting
- Nested timing indentation or explicit parent scope
- Match count and descriptors outside the timed search block
- Cold bundle run before isolated runs
- Fingerprint state cleared between independent contexts
- Diagnostic rematches labeled and excluded from actual apply/search totals
- Human-readable report plus stable machine-readable output when long-term comparisons matter

Do not infer search time by rematching after mutation. That measures warmed, changed bytecode.

### 7. Find the real hotspot

Interpret timing in this order:

```text
Slow patch row
├─ dependency path dominates → optimize/attribute shared dependency once
├─ cold index construction dominates → reuse, scope, or avoid that global index
├─ fingerprint search dominates → narrow candidates without weakening cardinality
├─ mutable proxy creation dominates → use original* for read-only work; avoid repeated conversion
├─ mutation helper dominates → inspect repeated clones/scans/instruction rewrites
├─ resource/settings work dominates → cache parsing/mapping at the correct dependency scope
└─ decode/write dominates → this is not a fingerprint optimization
```

Order effects matter. The first public patch often absorbs extension, settings, resource, and timeline dependencies. Report dependency-own time and bundle-incremental time, not only the public row.

### 8. Validate

After each optimization group:

1. Build the actual MPP.
2. Run a fresh-process sequential benchmark against the exact APK.
3. Confirm every intended patch passes.
4. Compare match counts and descriptors with the pre-change baseline.
5. Exclusively patch the exact APK for changed behavior paths.
6. Stop after successful build/exclusive patch and ask the user to test unless deeper verification was requested.

Reject an optimization if it is faster only because it:

- silently matches zero targets,
- patches fewer targets,
- relies on a release-obfuscated symbol,
- measures a warmed cache,
- shifts work into an unmeasured dependency,
- or omits required decode/finalization labels from an end-to-end claim.

## Performance Review Output

Write a report containing:

```markdown
## Environment
Exact APK, MPP, patcher version, process/cold-run policy

## Before / After
Decode, sequential patch execution, end-to-end, pass count, median and run count

## Hotspots
Patch, dependency path, search, mutation, bundle incremental

## Lookup Changes
Old strategy, new strategy, cardinality before/after, stability evidence

## Remaining Floor
Costs that require upstream indexing, unstable-owner discovery, decode, or unavoidable dependency work

## Validation
Build command/result, exclusive patch result, output path, user test requested
```

## Completion Checklist

```text
[ ] Exact APK and MPP paths fixed
[ ] Patcher source behavior inspected
[ ] Cold sequential run occurs before isolated diagnostics
[ ] Decode, dependency, search, mutation, and bundle time separated
[ ] Every changed lookup preserves asserted cardinality
[ ] Match descriptors unchanged or intentionally documented
[ ] No new obfuscated production names
[ ] Descriptor scopes cached per context; mutable classes not cached
[ ] Profiling disabled by default
[ ] Fresh build succeeds
[ ] All intended patches pass
[ ] Exact APK patched exclusively for handoff
[ ] Report records before/after and remaining floor
```
