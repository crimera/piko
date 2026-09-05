# Repository Agent Rules

## Search safety

- Scope every content search to the smallest known file or package directory.
- Never run repository-wide `ffgrep`/grep for X-Lite or APK analysis. Broad indexed searches can hang or crash the coding-agent process.
- Prefer exact-file `read` once a likely file is known.
- Prefer exact-file `rg` for follow-up symbol checks.
- Search one narrow package directory only when the target file is unknown; inspect the best result before searching again.
- Exclude generated, decompiled, build, and vendor trees unless one is the explicit analysis target.
- Do not launch multiple broad searches in parallel.

## X-Lite ports

- Load `.agents/skills/xlite-alpha-porting/SKILL.md` before porting or repairing an X-Lite patch.

## Patch performance

- Load `.agents/skills/morphe-patch-performance/SKILL.md` before optimizing or reviewing fingerprint and patch execution performance.

## NewX patch philosophy and methodology

NewX is an obfuscated app under active refactoring. A patch must survive ordinary R8 churn where possible, but must never guess when the app's behavior or contract has changed.

### Philosophy

- Treat the exact target APK's smali as bytecode truth. Use readable or dogfood builds to understand intent, not to choose production instructions.
- Match semantic invariants, not obfuscated identity. Short class names, method names, field names, package assignments, and numeric instruction positions are reconnaissance evidence only.
- Resolve capabilities from the APK, not versions. Do not write `if (version == ...)` routing. If genuinely different contracts exist, select an explicitly validated shape at patch time and share the common mutation afterward.
- Prefer one deep semantic resolver over a growing list of release-specific fingerprints. A new version should need a compatibility entry and tests, not another hardcoded owner branch.
- Fail closed. Zero matches, ambiguous matches, an unexpected signature, or an unproven control-flow path must raise a useful `PatchException`; never silently skip required behavior.
- Patch the smallest value at the latest safe point that controls the behavior. Avoid modifying shared helpers when the same helper serves unrelated features.
- Keep compile-time model contracts separate from release bytecode. Stable models may be `compileOnly`; unstable owners, methods, enums, and descriptors belong in patch-time resolution and direct smali injection, never runtime reflection.
- Preserve old targets. A resilience improvement must keep the old path's behavior unchanged and must be checked against at least one older declared target.

### Method

1. **Freeze the target.** Record the exact package, version, APK, MPP, and output. Reuse stored decompilations and keep analysis scoped to the relevant package/file.
2. **Trace intent.** Follow the user-visible operation from UI/event through policy gates to the final consumed value. Identify parallel implementations, shared helpers, and downstream gates.
3. **Map exact smali.** Verify the semantic path, registers, result representation, branches, and callsite in the target APK. JADX is navigation; smali is proof.
4. **Choose anchors.** Prefer preserved framework/public types, stable signatures, ordered calls, field relationships, semantic strings, and only then opcode/literal shapes. Remove optional anchors if cardinality remains correct.
5. **Resolve dynamically.** Derive owners, fields, constructors, method references, argument registers, and exact descriptors from matched target instructions. Do not hardcode a method number or an obfuscated descriptor in extension code.
6. **Assert cardinality.** State whether the target must have zero, one, or many matches. Print candidate descriptions in failure messages and validate combined old/new shape counts when variants are necessary.
7. **Mutate minimally.** Preserve register widths, invoke/result pairing, labels, reachability, value representation, and unrelated callers. Mutate from high indexes downward or rematch after edits.
8. **Build and patch exclusively.** Build the real MPP, patch the exact APK, and confirm the intended patches report `Applied` and `Saved to`.
9. **Validate proportionally.** After a failure or explicit deep-validation request, inspect final DEX reachability and run focused old/new runtime tests. Include negative/control paths, not just the modified path.
10. **Document evidence.** Record the target anchors, discarded anchors, cardinality, before/after hook, supported versions, and known limits so the next agent can improve the resolver instead of rediscovering it.

### R8 and refactor policy

Assume both kinds of change are possible. R8 may rename, inline, merge, repackage, remove, or reassign obfuscated classes and methods. Product or generated-code refactors may change parameters, fields, query schemas, UI event paths, and helper ownership. Identical behavior is not proof of identical implementation, and an identical descriptor is not proof of identical class identity.

When only identity moved, broaden semantic discovery. When the contract changed, create a capability/shape adapter with a common downstream mutation. Keep compatibility allowlists as safety metadata for tested releases; they are not behavior-routing branches and must remain centralized.
