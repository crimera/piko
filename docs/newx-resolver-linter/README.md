# NewX resolver safety experiment

## Purpose

NewX is obfuscated and changes frequently. Resolver cardinality helpers and a
source linter add code, rules, and patch-development friction. This experiment
measures whether that cost is justified by fewer incorrect, ambiguous, silent,
or difficult-to-diagnose patches.

The claim under test is:

> Fail-closed cardinality checks and resolver linting improve patch correctness
> and safety more than they increase development cost and compatibility friction.

This is not a claim of application security in the broad sense. The scope is
patch-time resolver safety and preservation of intended behavior.

## What counts as evidence

### Benefits

- A resolver change is rejected before producing a misleading or broken APK.
- Zero or ambiguous candidates produce a useful diagnostic.
- A patch no longer selects an unintended method, field, or call site.
- Optional probes preserve their fallback behavior.
- A real APK regression is detected earlier or diagnosed faster.

### Costs

- Added resolver/helper code and review complexity.
- Linter false positives or false negatives.
- Extra fixture maintenance.
- Longer lint/build/patch cycles.
- APKs rejected even though the patch could have applied safely.
- More difficult diagnosis caused by helper or linter behavior.

## Required validation layers

1. **Unit fixtures** — safe and unsafe source patterns with expected findings.
2. **Historical comparison** — baseline and candidate linter on the same source.
3. **Real APK matrix** — old, production, and alpha APKs from known versions.
4. **Failure isolation** — reproduce with linter/helper changes isolated or
   reverted before assigning causality.
5. **Runtime/control paths** — verify both modified behavior and fallback or
   negative paths where practical.

## Incident log

Each real failure or reported bug gets a file under this directory, for
example:

```text
docs/newx-resolver-linter/incidents/2026-09-10-drawer-footer.md
```

Use this template:

```markdown
# Short incident title

- Date:
- Reporter/session:
- APK package/version/build type:
- APK path and checksum:
- Source commit:
- MPP path/checksum:
- Extension artifact paths/checksums:
- Command:
- Failing patch:
- Severity:
- Confidence: confirmed / likely / unknown

## Symptom

## Complete error

## Reproduction

## Cause classification

- [ ] linter behavior
- [ ] cardinality-helper behavior
- [ ] resolver logic
- [ ] APK contract drift
- [ ] tooling or artifact setup
- [ ] runtime behavior
- [ ] unrelated

## Isolation comparison

Describe the result with the linter/helper change reverted or bypassed, without
using stale artifacts. Record what changed and what did not.

## Fix and validation

## Fixture or test added

## Lessons
```

## Metrics to maintain

For each benchmark entry, record:

- baseline and candidate commits;
- fixture corpus revision and safe/unsafe fixture counts;
- findings by rule;
- known unsafe cases, detected unsafe cases, false positives, and false
  negatives;
- lint, test, compile, and patch runtime;
- APK versions tested and patch success/failure;
- incidents prevented, incidents caused, and incidents unrelated;
- time to diagnose and fix failures.

Finding count alone is not a success metric. A lower count can mean missed
unsafe cases; a higher count can mean false positives. The desired result is
better unsafe-case detection with no unacceptable false-positive or fallback
regressions.

## Artifact rules

- Keep generated reports in `build/reports/` unless deliberately promoted.
- Keep committed fixtures, incident notes, and benchmark summaries under this
  directory or the linked test source.
- Record checksums or exact paths for APKs, MPPs, and extensions when an
  artifact affects the result.
- Never mix outputs from different source commits, versions, or toolchains.
