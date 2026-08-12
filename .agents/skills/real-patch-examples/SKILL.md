---
name: real-patch-examples
description: Real-world Morphe patch patterns from official morphe-patches repo — method override, list filtering, return value override, class chaining, dynamic fingerprints, string replacement. Use when writing patches and need pattern reference.
---

> **When to use:** Writing patches and need a pattern reference. Pick the simplest pattern that works — return override for premium checks, conditional hook for toggleable features, list filtering for ad removal. Always use `bytecodePatch` (fastest).

# Real-World Patch Patterns

## Pattern 1: Simple Method Override (Video Ads)

```kotlin
// Fingerprints.kt
object LoadVideoAdsFingerprint : Fingerprint(
    strings = listOf(
        "TriggerBundle doesn't have the required metadata specified by the trigger ",
        "Ping migration no associated ping bindings for activated trigger: ",
    )
)

// VideoAdsPatch.kt
val videoAdsPatch = bytecodePatch(name = "Video ads", description = "Removes video ads.") {
    dependsOn(sharedExtensionPatch, settingsPatch)
    compatibleWith(COMPATIBILITY_YOUTUBE)
    execute {
        PreferenceScreen.ADS.addPreferences(SwitchPreference("morphe_hide_video_ads"))
        LoadVideoAdsFingerprint.method.addInstructionsWithLabels(0, """
            invoke-static { }, $EXTENSION->hideVideoAds()Z
            move-result v0
            if-eqz v0, :show
            return-void
            :show
            nop
        """)
    }
}
```

## Pattern 2: List Filtering (Reddit Ads)

```kotlin
// Filter list items by calling extension method
fingerprint.let {
    it.method.apply {
        val index = it.instructionMatches.last().index
        val register = getInstruction<TwoRegisterInstruction>(index).registerA
        addInstructions(index, """
            invoke-static { v$register }, $EXT->filter(Ljava/util/List;)Ljava/util/List;
            move-result-object v$register
        """)
    }
}
```

## Pattern 3: Return Value Override (Background Playback)

```kotlin
// Override all return statements in a method
fingerprint.method.apply {
    findInstructionIndicesReversedOrThrow(Opcode.RETURN).forEach { index ->
        val register = getInstruction<OneRegisterInstruction>(index).registerA
        addInstructionsAtControlFlowLabel(index, """
            invoke-static { v$register }, $EXT->isAllowed(Z)Z
            move-result v$register
        """)
    }
}

// Or simply force return value
SomeFingerprint.method.returnEarly(true)
```

## Pattern 4: Class-Based Fingerprint Chaining (Reddit)

```kotlin
// Find class via toString fingerprint, then find constructor in that class
object AdPostSectionToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(string("AdPostSection(linkId="))
)

object AdPostSectionConstructorFingerprint : Fingerprint(
    classFingerprint = AdPostSectionToStringFingerprint,
    name = "<init>",
    returnType = "V",
    filters = listOf(string("sections"))
)
```

## Pattern 5: Dynamic Fingerprint (using prior match results)

```kotlin
execute {
    val field = SomeToStringFingerprint.method.findFieldFromToString(", fieldName=")

    val dynamicFingerprint = Fingerprint(
        definingClass = SomeToStringFingerprint.originalClassDef.type,
        name = "<init>",
        returnType = "V",
        filters = listOf(fieldAccess(opcode = Opcode.IPUT_BOOLEAN, reference = field))
    )
    dynamicFingerprint.method.apply { /* modify */ }
}
```

## Pattern 6: String Replacement (All Classes)

```kotlin
val stringFilter = string("old string")
Fingerprint(filters = listOf(stringFilter)).matchAllOrNull()?.forEach { match ->
    match.method.apply {
        findInstructionIndicesReversedOrThrow(stringFilter).forEach { index ->
            val register = getInstruction<OneRegisterInstruction>(index).registerA
            replaceInstruction(index, "const-string v$register, \"new string\"")
        }
    }
}
```

## Pattern 7: Method Injection (AdGuard — create new method)

```kotlin
val newMethod = ImmutableMethod(
    classDef.type, "getPaidLicense", null, returnType,
    AccessFlags.STATIC.value, null, null,
    MutableMethodImplementation(7)
).toMutable().apply {
    addInstructions(0, """
        new-instance v0, ${LicenseFingerprint.classDef.type}
        // construct license object with fields
        return-object v0
    """)
}
classDef.methods.add(newMethod)
originalMethod.addInstructions(0, """
    invoke-static {}, $newMethod
    move-result-object v0
    return-object v0
""")
```

## Pattern 8: Root/Emulator Detection Bypass (by class name)

```kotlin
classDefBy("Lcom/google/firebase/crashlytics/internal/common/CommonUtils;")
    .methods.first { it.name == "isRooted" }
    .toMutable()
    .addInstructions(0, "const/4 v0, 0x0\nreturn v0")
```

## Pattern 9: SSL Pinning Removal (OkHttp)

```kotlin
classDefBy("Lokhttp3/CertificatePinner;")
    .methods.filter { it.name == "check" }
    .forEach { it.toMutable().addInstructions(0, "return-void") }
```

## Pattern 10: Pairip License Bypass (universal)

```kotlin
ProcessLicenseResponseFingerprint.method.addInstruction(0, "const/4 p1, 0x0")
ValidateLicenseResponseFingerprint.method.returnEarly()
```

## Patch File Organization (morphe-patches)

```
patches/src/main/kotlin/app/morphe/patches/
├── shared/              # Shared across all apps
│   ├── Fingerprints.kt
│   └── misc/
│       ├── debugging/
│       ├── gms/         # GmsCore/MicroG support
│       ├── settings/    # Settings framework + preferences
│       ├── extension/   # Shared extension loading
│       └── ...
├── youtube/
│   ├── shared/Constants.kt   # COMPATIBILITY_YOUTUBE
│   ├── ad/video/             # Video ads
│   ├── ad/general/           # General ads
│   ├── layout/               # UI modifications
│   ├── misc/                 # Background playback, spoofing, etc.
│   ├── video/                # Playback speed, quality, etc.
│   └── interaction/          # Seekbar, downloads, swipe controls
├── music/
│   ├── shared/Constants.kt   # COMPATIBILITY_YOUTUBE_MUSIC
│   └── ...
└── reddit/
    ├── shared/Constants.kt   # COMPATIBILITY_REDDIT
    └── ...

extensions/
├── youtube/src/main/java/    # YouTube extension code
├── music/src/main/java/      # Music extension code
├── reddit/src/main/java/     # Reddit extension code
├── shared/src/main/java/     # Shared extension code
└── shared-youtube/           # Shared between YT and YT Music
```
