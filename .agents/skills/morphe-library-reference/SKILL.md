---
name: morphe-library-reference
description: Complete reference for all Morphe utility libraries — BytecodeUtils, ResourceUtils, FreeRegisterProvider, Settings, UI, Extensions. Use when writing patches that need utility functions, settings, extensions, or advanced bytecode operations.
---

> **When to use:** Writing patches that need more than basic addInstructions. This covers all utility code copied from morphe-patches-library into our repo.

# Morphe Library Reference

## Patch Utilities (Kotlin — `app.morphe.util.*`)

### BytecodeUtils.kt — Most Used

**Return overrides (simplest patching):**
```kotlin
import app.morphe.util.returnEarly
import app.morphe.util.returnLate

method.returnEarly()           // return-void
method.returnEarly(true)       // return true at start, skip all code
method.returnEarly(false)      // return false
method.returnEarly(0)          // return int 0
method.returnEarly("text")     // return string
method.returnEarly(null)       // return null object
method.returnLate(true)        // override ALL return statements to true
method.returnLate(false)       // override ALL return statements to false
```

**Instruction search:**
```kotlin
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstStringInstruction
import app.morphe.util.findInstructionIndicesReversedOrThrow

// By opcode
method.indexOfFirstInstruction(Opcode.INVOKE_VIRTUAL)
method.indexOfFirstInstructionOrThrow(Opcode.RETURN)
method.indexOfFirstInstructionOrThrow(startIndex, Opcode.IF_EQZ)

// By predicate
method.indexOfFirstInstruction { getReference<MethodReference>()?.name == "isPremium" }

// By string
method.indexOfFirstStringInstructionOrThrow("premium")

// Reversed (from end — safe for modification)
method.indexOfFirstInstructionReversedOrThrow(Opcode.RETURN)
method.findInstructionIndicesReversedOrThrow(Opcode.RETURN)  // all indices
method.findInstructionIndicesReversedOrThrow(stringFilter)    // all filter matches

// By literal value
method.indexOfFirstLiteralInstructionOrThrow(1337L)
method.containsLiteralInstruction(1337L)
```

**Literal overrides:**
```kotlin
import app.morphe.util.insertLiteralOverride

method.insertLiteralOverride(1337L, "Lext;->check(I)I")  // call extension
method.insertLiteralOverride(1337L, false)                 // constant override
```

**Control flow:**
```kotlin
import app.morphe.util.addInstructionsAtControlFlowLabel

method.addInstructionsAtControlFlowLabel(index, "const/4 v0, 0x1")
```

**View hiding:**
```kotlin
import app.morphe.util.injectHideViewCall

method.injectHideViewCall(insertIndex, viewRegister, "Lext;", "hideView")
```

**toString() discovery:**
```kotlin
import app.morphe.util.findFieldFromToString
import app.morphe.util.findMethodFromToString

val field = method.findFieldFromToString("fieldName=")
val targetMethod = method.findMethodFromToString("fieldName=")
```

**Class utilities:**
```kotlin
import app.morphe.util.traverseClassHierarchy
import app.morphe.util.toPublicAccessFlags

traverseClassHierarchy(mutableClass) { /* each class in hierarchy */ }
val flags = accessFlags.toPublicAccessFlags()
field.removeFlags(AccessFlags.FINAL)
mutableClass.constructor()
mutableClass.fieldByName("name")
classDef.toStringMethod()
```

**Method cloning:**
```kotlin
import app.morphe.util.cloneMutableAndPreserveParameters

val cloned = method.cloneMutableAndPreserveParameters()
```

**Helpers:**
```kotlin
method.addInstructionsToEnd("invoke-static {}, Lext;->hook()V")
method.p0Register          // actual register number of p0
method.numberOfParameterRegisters
true.toHexString()         // "0x1"
```

### FreeRegisterProvider.kt
Find unused registers in a method for temporary use without increasing register count.

### MemoryUtils.kt
Memory-efficient DEX operations.

### ResourceUtils.kt
XML/resource manipulation for ResourcePatch contexts.

### PatchListGenerator.kt
Generates patches-list.json during build.

## Patch Templates (Kotlin — `app.morphe.patches.all.misc.*`)

### ReplaceStringPatch
```kotlin
import app.morphe.patches.all.misc.string.replaceStringPatch

// Replace string globally across all classes
val myPatch = replaceStringPatch(from = "old text", to = "new text")
```

### ResourceMappingPatch
Maps resource IDs for use in bytecode patches.
```kotlin
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.patches.all.misc.resources.ResourceType

val id = getResourceId(ResourceType.ID, "resource_name")
val index = method.indexOfFirstResourceIdOrThrow("resource_name")
```

### HexPatchBuilder
Hex-level binary patching for native libraries.

### TransformInstructionsPatch + MethodCall
Transform method calls across entire classes.

## Extension Library (Java — `app.morphe.extension.shared.*`)

Runtime code that runs inside the patched app. Use with `extendWith()` in patches.

### Core
| Class | Purpose |
|-------|---------|
| `Utils.java` | Context access, toast, reboot, network checks |
| `Logger.java` | Logging with tag support |
| `ResourceType.java` | Android resource type enum |
| `ResourceUtils.java` | Runtime resource access |
| `StringRef.java` | Lazy string reference |

### Search
| Class | Purpose |
|-------|---------|
| `TrieSearch.java` | Base trie search |
| `StringTrieSearch.java` | String pattern matching |
| `ByteTrieSearch.java` | Byte pattern matching |

### Settings (for patches with user preferences)
| Class | Purpose |
|-------|---------|
| `Setting.java` | Base setting class |
| `BooleanSetting.java` | Boolean toggle |
| `IntegerSetting.java` | Integer value |
| `LongSetting.java` | Long value |
| `FloatSetting.java` | Float value |
| `StringSetting.java` | String value |
| `EnumSetting.java` | Enum selection |
| `BaseSettings.java` | Common settings |
| `AppLanguage.java` | Language setting |
| `SharedPrefCategory.java` | SharedPreferences wrapper |

### UI (for patches with custom UI)
| Class | Purpose |
|-------|---------|
| `CustomDialog.java` | Custom dialog builder |
| `SheetBottomDialog.java` | Bottom sheet dialog |
| `ColorDot.java` | Color indicator view |
| `ColorPickerPreference.java` | Color picker setting |
| `ColorPickerView.java` | Color picker widget |
| `Dim.java` | Dimension utilities |

## File Locations

```
paresh-patches/patches/src/main/kotlin/
├── app/morphe/util/                    # Kotlin utilities
│   ├── BytecodeUtils.kt               # ★ Most used
│   ├── FreeRegisterProvider.kt
│   ├── MemoryUtils.kt
│   ├── ResourceUtils.kt
│   └── PatchListGenerator.kt
├── app/morphe/patches/all/misc/        # Reusable patch templates
│   ├── string/ReplaceStringPatch.kt
│   ├── resources/ResourceMappingPatch.kt
│   ├── hex/HexPatchBuilder.kt
│   └── transformation/
│       ├── MethodCall.kt
│       └── TransformInstructionsPatch.kt
└── app/paresh/patches/<app>/           # Our patches

paresh-patches/extensions/extension/src/main/java/
└── app/morphe/extension/shared/        # Java runtime code
    ├── Utils.java, Logger.java, etc.
    ├── settings/                        # Settings framework
    └── ui/                              # UI components
```
