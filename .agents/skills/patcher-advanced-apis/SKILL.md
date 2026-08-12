---
name: patcher-advanced-apis
description: Advanced Morphe patcher APIs — BytecodeUtils, returnEarly/returnLate, instruction search, navigate(), classDefBy(), resource manipulation. Use when doing complex bytecode navigation, class manipulation, or using utility functions.
---

> **When to use:** Complex patches needing instruction search, return overrides, literal overrides, view hiding, class hierarchy traversal, or string replacement. These utilities come from `morphe-patches-library`.

# Morphe Advanced APIs + BytecodeUtils

## Return Overrides (simplest way to patch)

```kotlin
// Skip method entirely — return void/true/false/null/value
method.returnEarly()           // return-void
method.returnEarly(true)       // return true (boolean)
method.returnEarly(false)      // return false
method.returnEarly(0)          // return 0 (int)
method.returnEarly("string")   // return "string"
method.returnEarly(null)       // return null (object)

// Override all return statements (method still executes)
method.returnLate(true)        // all returns become true
method.returnLate(false)       // all returns become false
```

## Instruction Search

```kotlin
// Find by opcode
val index = method.indexOfFirstInstruction(Opcode.INVOKE_VIRTUAL)
val index = method.indexOfFirstInstructionOrThrow(Opcode.RETURN)

// Find by predicate
val index = method.indexOfFirstInstruction {
    getReference<MethodReference>()?.name == "isPremium"
}

// Find string constant
val index = method.indexOfFirstStringInstruction("premium")
val index = method.indexOfFirstStringInstructionOrThrow("premium")

// Find reversed (from end)
val index = method.indexOfFirstInstructionReversed(Opcode.RETURN)
val index = method.indexOfFirstInstructionReversedOrThrow(startIndex) { opcode == Opcode.IF_EQZ }

// Find all indices (reversed for safe modification)
val indices = method.findInstructionIndicesReversedOrThrow(Opcode.RETURN)
val indices = method.findInstructionIndicesReversedOrThrow { opcode == Opcode.CONST_STRING }
val indices = method.findInstructionIndicesReversedOrThrow(stringFilter)

// Find literal value
val index = method.indexOfFirstLiteralInstructionOrThrow(1337L)
method.containsLiteralInstruction(1337L) // boolean check
```

## Literal & Extension Overrides

```kotlin
// Override literal with extension call
method.insertLiteralOverride(1337L, "Lcom/example/Ext;->check(I)I")

// Override literal with constant boolean
method.insertLiteralOverride(1337L, false)
method.insertLiteralOverride(literalIndex, true)
```

## Control Flow Label Insertion

```kotlin
// Insert at existing control flow label (preserves jump targets)
method.addInstructionsAtControlFlowLabel(index, """
    invoke-static { }, Lcom/example/Ext;->check()Z
    move-result v0
""")
```

## View Hiding

```kotlin
// Hide an Android view
method.injectHideViewCall(insertIndex, viewRegister, "Lcom/example/Ext;", "hideView")
method.injectHideViewCall(moveResultIndex, "Lcom/example/Ext;", "hideView")
```

## String Replacement (all classes)

```kotlin
// Ready-made patch to replace strings globally
replaceStringPatch(from = "old string", to = "new string")
```

## Navigate Method Calls

```kotlin
// Navigate to method called at instruction index
val targetMethod = navigate(someMethod).to(5).original()
val mutable = navigate(someMethod).to(5).stop()

// Chain navigation
val deep = navigate(someMethod).to(5, 10, 2).stop()

// Navigate with predicate
val found = navigate(someMethod).to(2) { it.opcode == Opcode.INVOKE_VIRTUAL }.stop()
```

## Class APIs

```kotlin
val classDef = classDefBy("Lcom/example/Class;")
val mutable = mutableClassDefBy(classDef)
mutable.methods.add(newMethod)

// Traverse class hierarchy
traverseClassHierarchy(mutableClass) { /* called for each class */ }

// Get constructor, field, toString
val ctor = mutableClass.constructor()
val field = mutableClass.fieldByName("fieldName")
val toString = classDef.toStringMethod()
```

## toString() Field/Method Discovery

```kotlin
// Find field or method from toString() StringBuilder pattern
val field = method.findFieldFromToString("fieldName=")
val targetMethod = method.findMethodFromToString("fieldName=")
```

## Resource APIs

```kotlin
val file = get("res/values/strings.xml")
file.writeText(content)
delete("res/values/strings.xml")

document("res/values/strings.xml").use { doc ->
    val el = doc.createElement("string").apply { textContent = "Hello" }
    doc.documentElement.appendChild(el)
}

// Resource ID lookup
val index = method.indexOfFirstResourceIdOrThrow("resource_name")
```

## Utility Helpers

```kotlin
// Access flags
val publicFlags = accessFlags.toPublicAccessFlags()
field.removeFlags(AccessFlags.FINAL)

// Register helpers
val p0 = method.p0Register
val paramCount = method.numberOfParameterRegisters

// Clone method with extra registers
val cloned = method.cloneMutableAndPreserveParameters()

// Add instructions before last return
method.addInstructionsToEnd("invoke-static {}, Lext;->hook()V")
```
