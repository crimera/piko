---
name: morphe-patch-development-workflow
description: End-to-end workflow for developing, repairing, and validating Morphe Android patches against obfuscated apps, including stable-model stubs and patch-time runtime boundaries. Use when tracing app behavior, designing fingerprints, resolving unstable descriptors, mutating bytecode, debugging a patch that applies but does not work, porting patches across app versions/UI implementations, or proving a patched APK through final DEX and runtime testing.
---

# Morphe Patch Development Workflow

Follow the gates in order. A Kotlin build or `Applied:` log is not proof that a patch works.

## Read First

Detailed rationale, examples, session-derived failures, and evidence templates:

- [`references/fingerprint-development-workflow.md`](references/fingerprint-development-workflow.md)

Read the reference when implementing or debugging a patch. The skill is self-contained; the copy under `x-lite/` remains available for existing project links.

Load companion skills only when needed:

- New APK/recon: `apk-analysis-workflow`, `jadx`, `apktool`, `tool-reference`
- Fingerprints: `fingerprinting-guide`
- Patch DSL: `patch-anatomy`
- Complex mutation/navigation: `patcher-advanced-apis`
- Build/CLI/deploy: `build-deploy-troubleshoot`, `morphe-cli-reference`
- Existing patterns: `real-patch-examples`
- Runtime boundaries: `morphe-runtime-boundaries`

For obfuscation-sensitive fingerprint work, agents must read `fingerprinting-guide`,
`patcher-advanced-apis`, and `morphe-runtime-boundaries` when extension model types or release-specific
method descriptors are involved. For the X-Lite customize-navigation patch, also read
`patch-anatomy` and `morphe-library-reference`, then verify the exact 12.7.1, 12.10.1, and
12.11.0 smali artifacts recorded in `x-lite/navbar/fingerprint-verification.md`.

## Non-Negotiable Rules

1. The exact target APK is bytecode truth.
2. Dogfood/unobfuscated builds explain semantics only.
3. Reuse existing decomps. Never re-decompile when a suitable stored artifact exists.
4. Verify targets in smali/final DEX, not only JADX.
5. Never production-match obfuscated class, method, or field names.
6. Know and enforce match cardinality. Never silently skip zero matches.
7. Apply the smallest mutation that controls the behavior.
8. Inspect control-flow reachability, not only instruction presence.
9. Patch exclusively first; test the production patch set afterward.
10. Runtime verification of the exact original symptom is mandatory.
11. Diagnose the earliest unique root exception; dependent patch failures are usually one cascade.
12. Separate compile-time model contracts from release-specific bytecode: keep only verified stable
    models as `compileOnly` stubs and resolve unstable method descriptors at patch time.
13. Reuse the app family's centralized compatibility constant; never duplicate version targets.
14. Do not accumulate version artifacts. Delete verification smali dirs once `*-verification.md` hashes are recorded; retire superseded `release-*-decomp`/`-smali` trees when the target advances.

## Workflow Gates

| Gate | Action | Exit evidence |
|---|---|---|
| 0. Define | Record package/version/hash, exact UI surface, steps, actual/expected behavior, and a control path | Reproducible symptom statement |
| 1. Reproduce | Trigger failure on exact unpatched target | Screenshot/log/observation; unpatched bytecode captured |
| 2. Freeze artifacts | Resolve exact APK, MPP, output, hashes, timestamps, and centralized compatibility declaration | No ambiguous globs, stale outputs, or duplicate target lists |
| 3. Trace semantics | Follow UI → event → handler → gate → denied/allowed operation in readable build | End-to-end call-path table |
| 4. Map release | Find corresponding path in exact target smali | Exact target method(s) and instructions |
| 5. Enumerate | List parallel UIs, feature gates, eligibility checks, downstream checks | All plausible paths/gates documented |
| 6. Choose hook | Prefer dedicated checker/leaf policy, then call-site result, then handler branch | Smallest justified target |
| 7. Fingerprint/boundary | Use stable types/signatures/ordered calls; classify model references and omit unstable names | Match strategy and runtime-boundary plan survive tested builds |
| 8. Prove matches | Print and assert expected targets/count | Zero, one, or many is intentional |
| 9. Mutate | Inject minimal valid bytecode; preserve registers, labels, control flow, and value representation | Patch compiles against real class context |
| 10. Build/patch | Build actual MPP; patch exact APK in exclusive mode | Correct MPP used; `Applied` and `Saved to` confirmed |
| 11. Inspect output | Search every final DEX; inspect target smali and branch predecessors | Mutation exists on the executed path |
| 12. Install/test | Install with `adb install -r`; run primary and regression matrix | Original symptom gone; controls pass |
| 13. Clean/document | Remove instrumentation; record evidence and tested versions | Reproducible verification record (then delete the verification smali tree — the hashes in the record make it redundant) |

Failure returns to the earliest disproven gate. Do not stack speculative hooks.

## Existing X-Lite Artifacts

Use these before running JADX/apktool:

```bash
test -d x-lite/dogfood-decomp/sources
test -d x-lite/unobfuscated_smali
test -d x-lite/release-12.10.1-decomp/sources
test -d x-lite/release-12.14.0-decomp/sources
test -d x-lite/release-12.14.0-smali
```

| Artifact | Use |
|---|---|
| `x-lite/dogfood-decomp/` | Names, architecture, event flow |
| `x-lite/unobfuscated_smali/` | Dogfood bytecode truth |
| `x-lite/release-decomp/` | 12.7.1 release mapping |
| `x-lite/release-12.10.1-decomp/` | Exact 12.10.1 release mapping |
| `x-lite/release-12.14.0-decomp/` | Current target 12.14.0 JADX mapping |
| `x-lite/release-12.14.0-smali/` | Current target 12.14.0 smali bytecode truth |

For another version, store reusable output under `x-lite/release-<version>-decomp/`, never `/tmp`.

**Disk hygiene** (this repo sits on a space-constrained volume):

- Patch runs self-purge their scratch — never re-add `--disable-purge` to `patch-twitter.sh` (each run's `morphe-data/tmp/patching-*` is ~500M). Scratch can be moved off the volume entirely with `-t /tmp/morphe-tmp`.
- Gate-11 verification smali dirs (~2G per final APK) are disposable once the hash is recorded in `*-verification.md` — delete after the run.
- Retire superseded decomp trees when the target advances: keep dogfood, current target, and `release-12.10.1-decomp` as the anchor; delete the rest (old trees are regenerable and were ~40G combined before the 2025-08 prune, see `morphe-disk-audit.md`).

## Fingerprint Design

Prefer this anchor order:

1. Preserved framework/app type descriptors.
2. Stable signatures and public ABI types.
3. Ordered field/method access chains.
4. Semantic strings/data-class metadata.
5. Literals/opcode shapes only when verified across builds.
6. Obfuscated names for reconnaissance only.

Rules:

- Filters are mandatory conjunctions, not scoring hints. Start minimal; add an anchor only when cardinality requires it.
- Remove each optional anchor and recheck cardinality. If the count stays correct, omit it.
- Use `filters` for ordered matching and `MatchAfterImmediately()` for actual adjacency.
- Scope broad method shapes through a class fingerprint based on stable constructor/dependency field types.
- Use Morphe's `"L"` wildcard for an obfuscated object parameter when its exact descriptor is not stable.
- A preserved package does not make short class names stable. Types such as `kotlinx/coroutines/k0` or Compose `v2` can still be obfuscated.
- Public Kotlin/Compose ABI types such as `Function0` or `Composer` can be valid semantic anchors. Compiler-generated Compose group-key literals are not.
- Kotlin null-check parameter strings such as `"title"` or `"onClick"` are compiler artifacts. Require one only if verified across every target and needed for cardinality.
- Validate every retained string/literal against exact target smali.
- Trace registers and references. Do not select “first,” “last,” or “second branch” without semantic proof.

### Runtime Boundary and Patch-Time Resolution

An extension has two different contracts: the compile-time model contract used by Java/Kotlin and the
release-specific method descriptors present in the target APK. Keep them separate.

Use the general X-Lite stub module only for model classes and method signatures verified across the
supported releases. The dependency must remain `compileOnly`; the stub classes are contracts for the
compiler and must not be shipped in the extension DEX.

When a stable owner exposes an unstable method signature, do not add that return/parameter type to a
stub. Add a small extension bridge with a safe signature (`Object`, a primitive, or a verified stable
model), then resolve the target method once during patch execution and inject the exact smali invoke.
Runtime code should call the bridge directly; it should not reflect over release classes.

```text
stable compileOnly model stubs
        ↓
extension direct calls to stable ABI
        ↑
patch-time indexed owner lookup + exact descriptor injection
```

Choose the patch-time lookup based on what is known:

| Known at patch time | Preferred lookup | Cost/behavior |
|---|---|---|
| Stable class descriptor | `mutableClassDefBy(descriptor)` | Average `O(1)` class-map lookup, then a small method scan |
| Stable descriptor supplied to a fingerprint | `definingClass`/`classFingerprint` | Uses the same indexed class lookup, then matches methods |
| Only stable strings/opcode evidence | Fingerprint indexes | Narrows candidate classes; may still inspect many candidates |
| No stable owner or signature | Fingerprint/class chaining | Required; assert cardinality and verify exact smali |

Morphe does not expose a durable numeric method index. Never hardcode “method 6”. Resolve by verified
owner/signature or fingerprint and assert zero/one/many matches intentionally.

Before accepting a bridge, prove all of the following:

```text
[ ] Stable stubs are compileOnly and minimal
[ ] No release-specific descriptor appears in extension signatures
[ ] Patch-time owner/method lookup is indexed or justified by a fingerprint
[ ] Return/parameter descriptors and match cardinality are validated
[ ] Placeholder bridge instructions are replaced before their return executes
[ ] Extension artifact contains no model stub classes
[ ] Final DEX contains the exact direct invoke and no unresolved extension reference
```

A readable class name is not enough to make its methods stable. Stability is a property of the
complete method signature and the verified model contract. See `morphe-runtime-boundaries` for the
failure modes and bridge pattern.

### Enforce Cardinality

Use the API-compatible equivalent of:

```kotlin
val matches = TargetFingerprint.matchAll().toList()
check(matches.size == EXPECTED_MATCH_COUNT) {
    "Expected $EXPECTED_MATCH_COUNT matches, found ${matches.size}: " +
        matches.joinToString { it.originalMethod.toString() }
}

matches.forEach { match ->
    println("[DEBUG-patch] matched ${match.originalMethod}")
    // mutate match.method
}
```

Do not use `matchAllOrNull()?.forEach` for required behavior: zero matches then looks like success.

When the semantic contract changed (for example object result → boolean result), use explicit
shape variants. Nullable matching is allowed only when the combined cardinality is asserted, then
each shape receives its own mutation:

```kotlin
val oldMatches = OldShapeFingerprint.matchAllOrNull().orEmpty()
val newMatches = NewShapeFingerprint.matchAllOrNull().orEmpty()
val matches = oldMatches + newMatches
check(matches.size == 1) { "Expected one target across known shapes, found ${matches.size}" }

val match = matches.single()
if (match in oldMatches) mutateOldShape(match) else mutateNewShape(match)
```

## Mutation Safety

Prefer an early return when semantically correct:

```smali
const/4 v0, 0x1
return v0
```

Before injecting:

- Check register encoding (`const/4`: `v0..v15`; use `const/16` for higher registers).
- Keep `invoke-*` immediately paired with `move-result*`.
- Avoid clobbering `p0` unless returning before `this` is used.
- Mutate highest index first or rematch after edits.
- Define each label once; use patch-prefixed names.
- Inspect all predecessors and branch targets around the insertion.
- Hook the final consumed value, not a temporary builder value.
- Preserve expected representation. If the app expects an immutable list, reconvert a filtered mutable list before returning it.

Compose/recomposition warning: a visually nearby insertion may be skipped by loop exits, restart-group branches, or labels targeting the original instruction. Verify the injected call is reachable on the real path.

## Build, Patch, and Prove

```bash
./gradlew :patches:build --no-daemon
VER=$(awk -F= '/^version/{gsub(/[[:space:]]/, "", $2); print $2}' gradle.properties)
MPP="patches/build/libs/patches-${VER}.mpp"
test -f "$MPP"

java -jar morphe-desktop-1.11.0-all.jar patch \
  -p "$MPP" \
  --keystore Morphe.keystore \
  --exclusive \
  -e "Patch Name" \
  --force \
  -o /tmp/patch-test.apk \
  "$TARGET"
```

Then:

1. Hash/check timestamp of input, MPP, and output.
2. Extract all `classes*.dex` from output.
3. Find the target class across every DEX.
4. Inspect final smali: mutation, registers, labels, branch predecessors, returned/passed value.
5. Count actual host-app invoke sites, not raw strings or extension definitions/annotations.
6. Install with `adb install -r`.
7. Trigger the exact primary path, then controls and restart.
8. Retest with normal production patches after exclusive mode passes.
9. If a shared fingerprint/dependency changed, run every dependent production patch on the new APK.
10. Repatch every previously declared target version affected by a weakened/changed fingerprint.

## Fast Failure Triage

When many patches fail, first collapse the log by nested root cause. Find the earliest unique
fingerprint/exception and the shared dependency that owns it. Fix and rerun that dependency before
investigating downstream patch names; do not treat each wrapper failure as independent.

```text
Many patches report failures?
  yes → identify earliest unique nested cause/shared dependency; fix and rerun
  no  ↓
Correct APK installed?
  no  → artifact/install problem
  yes ↓
Final DEX contains mutation?
  no  → fingerprint/build/stale output/patch interaction
  yes ↓
Mutation is reachable?
  no  → branch target, Compose skip path, or wrong insertion point
  yes ↓
Target method executes?
  no  → wrong/parallel UI implementation
  yes ↓
Mutated value reaches consumer with expected type?
  no  → overwritten result, intermediate value, or representation mismatch
  yes ↓
Allowed branch invokes real operation?
  no  → second gate/downstream eligibility
  yes ↓
Runtime component/context/theme/storage/network problem
```

## Session-Derived Traps

- Stable type does not imply stable method name (`E()` became `F()`).
- First-match fingerprints can patch legacy UI while X-Lite remains untouched.
- Nullable/all-match APIs can silently turn zero matches into an apparently successful patch.
- Injecting before a branch target can be skipped even when final DEX contains the hook.
- Filtering a builder list is useless if the UI consumes a later immutable conversion.
- Preference helper names may hide inverted semantics; inspect implementation before branching.
- Manifest presence does not prove an activity is runtime-enabled; app-family metadata can disable it.
- Standalone extension activities may need runtime app palette/theme initialization before `super.onCreate()`.
- Compose compiler literals and obfuscated app descriptors are not durable anchors.
- Context initialization can differ between legacy and X-Lite startup paths; verify before reading preferences.
- A single shared settings fingerprint failure can cascade into nearly every preference-backed patch.
- Extra filters reduce resilience: `"onClick"` disappeared while the renderer signature and semantic Compose call stayed stable.
- Namespace preservation is not symbol stability: `kotlinx/coroutines/k0` changed to `l0`; wildcard the unproven object type.
- New releases can change the target contract, not only obfuscation. Keep explicit old/new shape fingerprints and assert one combined match.
- Duplicate compatibility lists drift. Reuse `COMPATIBILITY_X_LITE` (or the app-family equivalent).
- A `compileOnly` model stub can still leak an invalid release-specific method descriptor into the extension; compile-only prevents duplicate classes, not verifier failures.
- A stable class descriptor should not be found with a global fingerprint scan; use the indexed class map and a local cardinality check.
- A placeholder bridge that remains unpatched returns a harmless-looking default and can make a patch appear applied while doing nothing; verify the final direct invoke.

## Definition of Done

Done means all are true:

- Failure reproduced on exact unpatched target.
- Intended method(s) matched with asserted cardinality.
- Actual MPP rebuilt and used.
- Final DEX contains a reachable mutation producing the expected value/type.
- Exact output installed without unintentionally clearing state.
- Original user-visible failure is gone.
- Parallel/legacy/control paths pass.
- Production patch set retested.
- Debug instrumentation removed.
- Stable model stubs are `compileOnly`; unstable descriptors are resolved at patch time and do not leak into extension signatures.
- Evidence records hashes, targets, anchors omitted, match counts, before/after bytecode, runtime-boundary decisions, and tested versions.
