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
