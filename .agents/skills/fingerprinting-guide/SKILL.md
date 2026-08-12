---
name: fingerprinting-guide
description: Complete guide to Morphe fingerprinting, indexed class resolution, cardinality, and stable runtime boundaries. Use when creating fingerprints, debugging fingerprint match failures, deciding between direct descriptor lookup and fingerprints, or understanding how filters work.
---

> **When to use:** User needs to create or debug fingerprints, or choose indexed direct lookup versus a fingerprint. NEVER use obfuscated names. Always verify against smali, not jadx. Use `filters` (ordered) over `strings` (unordered). Declare as `object` classes for named stack traces.

# Morphe Fingerprinting — Official Reference

A fingerprint is a partial description of a method used to uniquely match it by stable characteristics that survive app updates. Obfuscated names change every release — fingerprints match on return type, access flags, parameters, and instruction patterns instead.

## Fingerprint Declaration

All fields are optional — use only what's needed to uniquely identify:

```kotlin
object MyFingerprint : Fingerprint(
    definingClass = "Lcom/example/Class;",   // StringComparisonType semantics
    name = "methodName",                      // Only for non-obfuscated methods
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;", "I", "L"),  // "L" for obfuscated classes

    // Ordered instruction filters (must appear in same order as target method)
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET, definingClass = "this", type = "Ljava/util/Map;"),
        string("showBannerAds"),
        methodCall(definingClass = "Ljava/lang/String;", name = "equals"),
        opcode(Opcode.MOVE_RESULT, InstructionLocation.MatchAfterImmediately()),
        literal(1337),
        opcode(Opcode.IF_EQ),
    ),

    // Unordered string matching (for methods with many strings in random order)
    strings = listOf("unordered1", "unordered2"),

    // Custom predicate
    custom = { method, classDef -> classDef.type == "Lcom/target/Class;" },

    // Find class via another fingerprint first
    classFingerprint = AnotherFingerprint,
)
```

## Filter Types

| Filter | Usage |
|--------|-------|
| `string("text")` | Match const-string instruction |
| `methodCall(definingClass, name, parameters, returnType)` | Match invoke-* instruction |
| `methodCall(smali = "Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;")` | Smali shorthand |
| `fieldAccess(opcode, definingClass, name, type)` | Match field get/put |
| `fieldAccess(smali = "Landroid/os/Build;->MODEL:Ljava/lang/String;")` | Smali shorthand |
| `opcode(Opcode.X)` | Match specific opcode |
| `literal(value)` | Match const literal |
| `anyInstruction(filter1, filter2)` | Match any alternative (for version differences) |

## InstructionLocation Options

- Default: match anywhere after previous filter
- `MatchAfterImmediately()`: must be immediately after previous filter
- `MatchAfterWithin(n)`: within n instructions of previous filter
- `MatchFirst()`: must be first instruction in method

## String Declarations — Two Ways

1. **Preferred — ordered via filters**: `filters = listOf(string("foo"), string("bar"))` — order must match target method
2. **Unordered via strings**: `strings = listOf("foo", "bar")` — matches in any order, useful for enums with many strings

## Using Fingerprints in Patches

```kotlin
execute {
    // Auto-matches on first access, cached for reuse
    MyFingerprint.method.addInstructions(0, "...")

    // Access instruction match indices
    val index = MyFingerprint.instructionMatches[0].index
    val reg = MyFingerprint.instructionMatches[0].getInstruction<OneRegisterInstruction>().registerA

    // Access class
    val classDef = MyFingerprint.originalClassDef

    // Null-safe access
    val methodOrNull = MyFingerprint.methodOrNull

    // Match all occurrences
    Fingerprint(filters = listOf(string("target"))).matchAllOrNull()?.forEach { match ->
        match.method.apply { /* modify */ }
    }

    // Manual matching in specific class
    MyFingerprint.match(SomeOtherFingerprint.originalClassDef)
}
```

## Fingerprint Properties

| Property | Returns | On no match |
|----------|---------|-------------|
| `originalClassDef` | Immutable class | Exception |
| `originalClassDefOrNull` | Immutable class | null |
| `originalMethod` | Immutable method | null |
| `classDef` | Mutable class (replaces original) | Exception |
| `method` | Mutable method (replaces original) | Exception |
| `methodOrNull` | Mutable method | null |

Use `original*` for read-only access (avoids creating mutable copy).

## Class-Based Fingerprint Chaining

Find class via one fingerprint, then find method within it:

```kotlin
val showAdFingerprint = Fingerprint(
    classFingerprint = Fingerprint(name = "toString", strings = listOf("classField=")),
    returnType = "Z",
    filters = listOf(
        methodCall(name = "getValue", returnType = "Z"),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately())
    )
)
```

## Indexed Resolution and Runtime Boundaries

Fingerprinting is not the only lookup strategy. Choose the narrowest strategy justified by the target:

| Target knowledge | Preferred strategy | Cost |
|---|---|---|
| Stable owner descriptor and verified method signature | `mutableClassDefBy(descriptor)` plus a local method/cardinality check | Average `O(1)` class-map lookup, then a small method scan |
| Stable owner descriptor used by a fingerprint | `definingClass` or `classFingerprint` | Same indexed class lookup, then method matching |
| Unknown/obfuscated owner with stable semantic evidence | Fingerprint strings/filters/class chaining | Uses candidate indexes when available; may inspect many classes |
| No stable descriptor or semantic anchor | Improve the evidence first | Do not invent numeric indexes or use first-match behavior |

In the current Morphe Patcher, class descriptors are stored in a descriptor-keyed map. A fingerprint
with `definingClass` reaches that map before scanning methods. A `classFingerprint` resolves one owner,
then the child fingerprint scans only that owner's methods. Method matching inside a class is still
linear; there is no durable numeric method index. “Method 6” is not a release-resilient lookup.

Use direct lookup for preserved model classes only after verifying the complete used signature across
targets:

```kotlin
val owner = context.mutableClassDefBy(STABLE_OWNER_DESCRIPTOR)
val matches = owner.methods.filter { method ->
    method.name == VERIFIED_NAME && method.parameterTypes.isEmpty()
}
check(matches.size == 1) {
    "Expected one getter, found ${matches.size}: ${matches.joinToString()}"
}
val getter = matches.single()
check(getter.returnType == EXPECTED_RETURN_DESCRIPTOR) {
    "Unexpected getter shape: $getter"
}
```

A readable owner class can still expose an unstable return/parameter type. Keep stable model classes as
`compileOnly` stubs, but do not put a release-specific type in a stub signature. Expose an extension
bridge with `Object`/primitive/stable-model types, resolve the exact target method at patch time, and
inject the direct invoke. Runtime code then uses the bridge without reflection or a direct reference to
the changing type. See `morphe-runtime-boundaries` for the complete bridge and artifact checks.

Verify both boundaries:

```text
[ ] Direct lookup/fingerprint has asserted cardinality and complete descriptor shape
[ ] Stub dependency is compileOnly and minimal
[ ] Extension artifact contains no model stub classes
[ ] No unstable release descriptor leaks into extension signatures
[ ] Final DEX contains the resolved invoke and paired move-result*
```

## Dynamic Fingerprints (using prior match results)

```kotlin
execute {
    val dynamicFingerprint = Fingerprint(
        definingClass = SomeFingerprint.originalClassDef.type,
        returnType = "V",
        filters = listOf(fieldAccess(opcode = Opcode.IPUT_BOOLEAN, reference = someField))
    )
    dynamicFingerprint.method.apply { /* modify */ }
}
```

## Multiple Modifications — Index Safety

When modifying multiple instructions, work from last index to first:

```kotlin
AdLoaderFingerprint.let {
    // Last filter first
    val filter6 = it.instructionMatches[5]
    it.method.removeInstruction(filter6.index)

    // Then earlier filter
    val filter4 = it.instructionMatches[3]
    val reg = filter4.getInstruction<OneRegisterInstruction>().registerA
    it.method.addInstructions(filter4.index + 1, "const/4 v$reg, 0x0")
}
```

Or use `clearMatch()` + `match()` to refresh indices after modifications.

## Critical Rules

- NEVER use obfuscated class/method names (`a`, `b`, `H`)
- Non-obfuscated names (`isPremium`, `getEntitlements`) are safe
- Filters must appear in same order as target method instructions
- Declare as `object` classes for named stack traces on match failure
- Always verify against smali bytecode, not jadx Java output
- Fingerprints match once and cache — safe to share between patches
- A cached match is not a numeric method index; do not persist instruction/method positions across releases
- Use `"L"` for obfuscated parameter types
- Use `definingClass = "this"` for self-referencing fields
- A stable class descriptor does not make every method signature stable; validate return/parameter descriptors before putting them in stubs
- Prefer patch-time direct descriptor resolution over runtime reflection for release-specific methods
