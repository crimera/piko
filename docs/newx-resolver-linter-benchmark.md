# NewX resolver linter benchmark

This document records reproducible comparisons between the resolver linter and
its fixture corpus. It is intentionally separate from `AGENTS.md`; agents
should update this file when an experiment produces new evidence.

## 2026-09-10: guarded indexed-access experiment

### Compared revisions

| Candidate | Revision | Linter source | Fixture tests |
| --- | --- | ---: | ---: |
| Baseline | `d5834805` | 761 lines | 20 |
| Candidate | `ffe19127` | 881 lines | 23 |

The candidate added narrow recognition for guarded `candidates[0]` access and
three regression fixtures. It also added narrow proofs for fixed mapped lists
and combined guarded collections. It does not add a general data-flow engine.

### Results

Both revisions passed the same executable NewX fixture corpus and directory
lint command:

```text
./gradlew :patches:test :patches:lintNewxResolvers --console=plain
Baseline: BUILD SUCCESSFUL; NewX resolver lint passed
Candidate: BUILD SUCCESSFUL; NewX resolver lint passed
```

Candidate fixture coverage includes:

- guarded indexed access with nullable failure;
- unguarded candidate indexing rejected;
- parameter indexing excluded from resolver detection;
- at-most-one proofs not accepted for required selection;
- fixed `listOf(...).map { ... }` indexing;
- combined exact guards for aliased collections.

### Interpretation

This experiment proves no regression in the existing fixture corpus and adds
coverage for the new patterns. It does **not** yet establish recall or runtime
benefit because the corpus does not contain a measured set of real historical
resolver defects. Future entries must record known unsafe cases, detected
unsafe cases, false positives, false negatives, runtime, and APK patch results.

### Reproduction

Run the candidate from the repository root:

```bash
./gradlew :patches:test :patches:lintNewxResolvers --console=plain
```

For a baseline comparison, create a detached worktree at the recorded baseline
revision and run the same command there. Do not compare results from different
fixture trees or stale generated artifacts.

## 2026-09-13: 12.27 alpha fail-closed patch experiment

This is an APK-level safety result, not a synthetic linter benchmark. It tests
whether the resolver/cardinality method turns release drift into actionable
failures instead of silently selecting an unintended method.

### Inputs and provenance

| Item | Value |
| --- | --- |
| Target APK | `apks/twitter_12.27.0-alpha.01.apk` / SHA-256 `82d0fe729854d631bb8069b3cab25f47d64361722cb99c483cc7e8e2b178d67d` |
| Control APK | `apks/twitter_12.26.0-alpha.03.apk` / SHA-256 `4a5bc95c8b6a9cd1ceb6e159333943b11e094ecf168153f14decb14d697ccf5e` |
| Source | `d6efb26d` plus working-tree resolver repairs |
| MPP | `patches/build/libs/patches-3.9.0-dev.4.mpp` / SHA-256 `3d76f78efb160e1986832ef4bf112c262932c012a899858305645de0d24226c1` |
| Linter revision | `ffe19127658e80548a3b549350d9cc1617481b68` |
| Fixture corpus | `NewXResolverLinterTest.kt`, 23 tests, same corpus as the prior benchmark entry |

### Linter validation

```text
./gradlew :patches:test :patches:lintNewxResolvers --console=plain
BUILD SUCCESSFUL
NewX resolver lint passed
```

Repository findings: `0`. Fixture false positives/false negatives: not
measured by this APK experiment. The fixture corpus passed unchanged.

### APK results

The normal command was run against the target APK. It applied the repaired
patches through `NewX: Disable video player scrolling`, then aborted at
`NewX: Dynamic color` when the old inline-action tint renderer no longer
matched. The exception included the exact resolver location:

```text
patchInlineActionTints(DynamicColorPatch.kt:875)
Failed to match the fingerprint
```

The diagnostic command was then run with `--continue-on-error`. It reported
10 additional required-patch failures: register pressure (2), missing
renderers/Compose callers (4), image-loader drift (1), URL model drift (1),
timeline register allocation (1), and reply-sorting initializer drift (1).
The failures were logged rather than skipped silently. Unaffected patches
reported `Applied`; the output was explicitly treated as non-shipping.

Representative detected unsafe cases:

- Compose paragraph constructor moved package and gained a parameter;
- Pager holder and ABI changed;
- Kotlin lazy construction changed invoke/result shape;
- stale Kotlin `requestType`/`event` strings disappeared;
- drawer title flow moved into generated forwarding lambdas;
- several required renderers, constructors, and getters became zero-match;
- register allocation became impossible under the requested encoding.

### Interpretation

This is positive evidence for the method. The resolver did not guess an owner,
select the first surviving candidate, or silently disable a required feature.
The default script aborted with a useful failure, while `--continue-on-error`
made the remaining failures observable for one diagnostic sweep. The linter
also rejected unsafe candidate indexing introduced during repair work.

This is not proof of runtime correctness: no device/control-path test was run,
patch runtime duration was not captured, and a sufficiently weak semantic
fingerprint could still match the wrong method. The result supports keeping
fail-closed cardinality checks and building capability adapters; it does not
support a single Compose-only bridge for unrelated product, model, and
image-loader contract changes.

Full incident details: `docs/newx-resolver-linter/incidents/2026-09-13-12-27-sweep.md`.
