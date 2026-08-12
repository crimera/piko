---
name: patch-anatomy
description: How to write Morphe patches — bytecodePatch DSL, compatibility, extensions, options, finalization. Use when creating new patches or understanding patch structure.
---

> **When to use:** Writing patches — bytecodePatch DSL, compatibility declarations, extensions, options, finalization. Keep patches minimal — put complex logic in extensions.

# Morphe Patch Anatomy — Official Reference

## Patch Types

| Type | Use When | Performance |
|------|----------|-------------|
| `bytecodePatch` | Modifying Dalvik bytecode | Fast — no resource decoding |
| `rawResourcePatch` | Modifying raw files/assets | Medium |
| `resourcePatch` | Modifying decoded XML | Slow — decodes all resources |

Always prefer `bytecodePatch`.

## Complete Patch Example

```kotlin
val COMPATIBILITY_XYZ = Compatibility(
    name = "XYZ App",
    packageName = "app.xyz.mobile",
    appIconColor = 0xFF3300,
    targets = listOf(
        AppTarget(version = "2.0.0"),
        AppTarget(version = "1.0.42"),
    )
)

@Suppress("unused")
val disableAdsPatch = bytecodePatch(
    name = "Disable ads",
    description = "Disables ads in the app.",
    default = true
) {
    compatibleWith(COMPATIBILITY_XYZ)
    dependsOn(disableAdsResourcePatch)
    extendWith("disable-ads.mpe")

    execute {
        showAdsFingerprint.method.addInstructions(0, """
            invoke-static {}, LDisableAdsPatch;->shouldDisableAds()Z
            move-result v0
            return v0
        """)
    }

    finalize {
        // Post-processing after all dependent patches execute
    }
}
```

## Patch Options

```kotlin
val patch = bytecodePatch(name = "Configurable") {
    val value by stringOption(name = "Color")
    val custom by option<String>(name = "Custom option")
    execute { println(value) }
}
```

Options can be shared across patches:
```kotlin
val sharedOption = stringOption(name = "Shared")
bytecodePatch(name = "A") { val v by sharedOption() }
bytecodePatch(name = "B") { val v by sharedOption() }
```

## Extensions (Runtime DEX Code)

Extensions are precompiled DEX files merged into the patched app before patch execution:

```java
public class ComplexPatch {
    public static void doSomething() { /* complex logic */ }
}
```

Referenced in patches:
```kotlin
val patch = bytecodePatch(name = "Complex") {
    extendWith("complex-patch.mpe")
    execute {
        fingerprint.method.addInstructions(0, "invoke-static {}, LComplexPatch;->doSomething()V")
    }
}
```

## Finalization Order

```kotlin
val patch = bytecodePatch(name = "Main") {
    dependsOn(bytecodePatch(name = "Dep") {
        execute { print("1") }
        finalize { print("4") }
    })
    execute { print("2") }
    finalize { print("3") }
}
// Output: 1234 (execute in dependency order, finalize in reverse)
```

## Compatibility Declaration

```kotlin
val COMPAT = Compatibility(
    name = "App Name",
    packageName = "com.example.app",
    apkFileType = ApkFileType.XAPK,     // APK, XAPK, APKM, APK_REQUIRED
    appIconColor = 0x6200EE,
    signatures = setOf("sha256..."),      // Optional
    targets = listOf(
        AppTarget(version = "2.0.0", minSdk = 28, isExperimental = true),
        AppTarget(version = "1.0.0", minSdk = 26),
    )
)
```

## Project Structure

```
patches/src/main/kotlin/app/<group>/patches/
├── shared/              # Shared across all apps
├── <app>/
│   ├── shared/Constants.kt
│   └── <category>/
│       ├── Fingerprints.kt
│       └── SomePatch.kt
extensions/<name>/src/main/java/   # Extension code
```

## Conventions

- Name patches after what they do: "Disable ads", "Remove watermark"
- Description in third person, present tense, ending with period
- Name fingerprints with best guess of target method purpose
- Keep patches minimal — put complex logic in extensions
- Add `@Suppress("unused")` on top-level patch vals
- Document non-obvious code

## Key Imports

```kotlin
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import app.morphe.patcher.methodCall
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.literal
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
```
