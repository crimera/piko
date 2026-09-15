# Recon: `NewX: Restore timeline position` CCE on Twitter 12.27

## Finding

The CCE is caused by the restore-success block storing the component receiver
(`com.x.urt.y`) in the timeline-position map where the holder (`com.x.urt.m0`)
must be stored.

In the patched getter, the holder is correctly allocated in `v6`, but the
same register is immediately overwritten with `p0` before the map `put`:

```smali
new-instance v6, Lcom/x/urt/m0;       # v6 = new holder
invoke-direct {v6, v7, v2}, ...-><init>(II)V
...
move-object/from16 v6, p0              # v6 = com.x.urt.y; holder lost
...
invoke-virtual {v7, v2, v6}, ConcurrentHashMap->put(...)
```

The original method then reads that map entry and enforces its declared
return type:

```smali
move-result-object v5
check-cast v5, Lcom/x/urt/m0;
```

At that point `v5` contains the `com.x.urt.y` object written by the injected
`put`, so the reported exception is the direct and expected result:

```text
com.x.urt.y cannot be cast to com.x.urt.m0
```

The minimal repair is to keep the holder in `positionsRegister`/`v6` and use
the distinct `mapRegister`/`v7` as the temporary receiver while rebuilding the
map reference. No additional register is needed.

## Frozen scope

| Role | Artifact | Identity |
| --- | --- | --- |
| Patched | `/Users/steven/Downloads/piko-twitter-patched.apk` | `com.twitter.android`, `12.27.0-alpha.01`, version code `312270201` |
| 12.27 original | `./apks/twitter_12.27.0-alpha.01.apk` | `com.twitter.android`, `12.27.0-alpha.01`, version code `312270201` |
| 12.26 control | `./apks/twitter_12.26.0-alpha.03.apk` | `com.twitter.android`, `12.26.0-alpha.03`, version code `312260203` |
| Source | `patches/src/main/kotlin/app/crimera/patches/newx/timeline/RestoreTimelinePositionPatch.kt` | Working-tree content, especially lines 150–467 |

The repository was already dirty when this recon started, including changes to
the target patch source and an unrelated untracked artifact. The source file
was not modified by this session. The inspected repository `HEAD` was
`dec951d008d8a617d234b374563408ba5d364c75`.

No device or `adb` interaction was performed.

## Method identity and release comparison

The required methods are:

| Build | Getter | Registers | Receiver |
| --- | --- | ---: | --- |
| 12.26 original | `Lcom/x/urt/z;->d()Lcom/x/urt/n0;` | 7 | `p0 == v6` |
| 12.27 original | `Lcom/x/urt/y;->d()Lcom/x/urt/m0;` | 6 | `p0 == v5` |
| 12.27 patched | `Lcom/x/urt/y;->d()Lcom/x/urt/m0;` | 12 | `p0 == v11` |

The class and holder descriptors are release-specific obfuscation results;
the semantic shape is unchanged. In both original getters, the repository's
timeline getter returns an enum used as the `ConcurrentHashMap` key, and the
map value is cast to the holder type before returning.

The control method has the same semantic operation but a different register
layout:

```smali
12.26 original:
invoke-interface v0, Lcom/x/repositories/urt/o1;->a()Lcom/x/models/timelines/w;
move-result-object v2
...
invoke-virtual v3, v2, ConcurrentHashMap->get(Ljava/lang/Object;)Ljava/lang/Object;
move-result-object v2
check-cast v2, Lcom/x/urt/n0;
```

The 12.27 original reuses its physical receiver register for the map value:

```smali
12.27 original:
invoke-interface v0, Lcom/x/repositories/urt/n1;->a()Lcom/x/models/timelines/u;
move-result-object v2
...
iget-object p0, p0, Lcom/x/urt/y;->h:Lcom/x/urt/features/d;
iget-object p0, p0, Lcom/x/urt/features/d;->a:Ljava/util/concurrent/ConcurrentHashMap;
invoke-virtual p0, v2, ConcurrentHashMap->get(Ljava/lang/Object;)Ljava/lang/Object;
move-result-object p0
check-cast p0, Lcom/x/urt/m0;
```

The register numbers are therefore not stable handles. The current patch's
dynamic register allocation is the right compatibility direction; the defect
is that two logical values are assigned the same newly allocated register at
the wrong point in their lifetimes.

## `cloneMutable` expansion and parameter shifting

At source lines 163–172, the getter is cloned with:

```kotlin
additionalRegisters =
    parameterRegisterCount +
        RESTORE_TEMPORARY_REGISTER_COUNT +
        FALLBACK_RESTORE_TEMPORARY_REGISTER_COUNT
```

For `d()`:

```text
parameterRegisterCount = 1       # the instance receiver
restore temporaries   = 2
fallback temporaries  = 3
additionalRegisters   = 6
```

The 12.27 original has six registers. The clone therefore has twelve total
registers. The final patched method shows the clone's parameter-preservation
prologue:

```smali
[0000] move-object/from16 v5, p0
[0002] iget-object v0, v5, Lcom/x/urt/y;->e:Landroidx/compose/foundation/layout/v0;
```

The physical receiver is now `p0 == v11`; the old body uses `v5` as its
preserved receiver alias. The source then derives:

```text
originalRegisterCount       = 6
scratchBase                  = 6
restoreRegisters             = v6..v7
fallbackRegisters            = v8..v10
shiftedParameterRegionStart = 11
```

The guard at source lines 176–189 confirms that `v6..v10` are below the
shifted parameter region and encodable by the injected instructions. This
expansion fixed the earlier target-only `FreeRegisterProvider` starvation; it
does not itself cause the CCE.

## Original continuation and external labels

Before the main insertion, source line 284 captures the instruction immediately
after the timeline enum result:

```text
originalContinuation = getterMethod.instructions[timelineResultIndex + 1]
```

For 12.27 this is the original `invoke-virtual v2,
Ljava/lang/Object;->getClass()Ljava/lang/Class;` instruction. The main block
is inserted at source lines 351–391, immediately before that instruction, and
uses:

```kotlin
ExternalLabel(
    "piko_newx_restore_position_continue",
    originalContinuation,
)
```

In the final patched method the external continuation is at offset `0x51`
(instruction ordinal 42). It is the original map-read/check-cast path, not a
return or a bypass:

```smali
[0051] invoke-virtual v2, Ljava/lang/Object;->getClass()Ljava/lang/Class;
[0054] iget-object v5, v5, Lcom/x/urt/y;->h:Lcom/x/urt/features/d;
[0056] iget-object v5, v5, Lcom/x/urt/features/d;->a:Ljava/util/concurrent/ConcurrentHashMap;
[0058] invoke-virtual v5, v2, ConcurrentHashMap->get(Ljava/lang/Object;)Ljava/lang/Object;
[005b] move-result-object v5
[005c] check-cast v5, Lcom/x/urt/m0;
```

The fallback insertion at source lines 441–466 captures the newly inserted
native fallback allocation as `nativeFallbackInstruction` and targets it with
an external label. In the final method that target is the `new-instance
v8, Lcom/x/urt/m0;` at offset `0xd1`. The setting-disabled and
restore-missing branches go there; the successful persistent restore returns
before it. This fallback label is not the CCE site.

`addInstructionsWithLabels` does not preserve a register's previous object or
infer logical lifetimes. It emits the interpolated register numbers and binds
the external labels to the supplied instruction objects. Thus the alias at
source lines 346–347 remains an alias in the final DEX:

```kotlin
val mapOwnerRegister = restoreRegisters.first   // v6
val positionsRegister = mapOwnerRegister       // v6
val mapRegister = restoreRegisters.last        // v7
```

That alias is safe while `v6` contains the map owner or the `int[]` restore
result. It becomes unsafe when `v6` becomes the holder and must remain live
through the map `put`.

## Patched 12.27 getter: exact bytecode flow

The original timeline result is instruction ordinal 6. After the clone
prologue it is ordinal 7 and remains in `v2`:

```smali
[0009] iget-object v0, v5, Lcom/x/urt/y;->c:Lcom/x/repositories/urt/n1;
[000b] invoke-interface v0, Lcom/x/repositories/urt/n1;->a()Lcom/x/models/timelines/u;
[000e] move-result-object v2                  # timeline enum/key
```

The injected main block starts at ordinal 8 / offset `0x0f`:

```smali
[000f] move-object/from16 v6, p0
[0011] iget-object v6, v6, Lcom/x/urt/y;->h:Lcom/x/urt/features/d;
[0013] iget-object v7, v6, Lcom/x/urt/features/d;->a:Ljava/util/concurrent/ConcurrentHashMap;
[0015] invoke-virtual v7, v2, ConcurrentHashMap->get(Ljava/lang/Object;)Ljava/lang/Object;
[0018] move-result-object v6                  # native map value
[0019] invoke-static v2, TimelineScrollPositionStore->useInMemoryPosition(Enum)Z
[001c] move-result v7
[001d] if-eqz v7, :restore_path
[001f] if-nez v6, :original_continuation
```

For the persistent-restore path, the relevant instructions are:

```smali
[0021] const/4 v6, 0
[0022] move-object/from16 v6, p0
[0024] iget-object v6, v6, Lcom/x/urt/y;->h:Lcom/x/urt/features/d;
[0026] iget-object v7, v6, Lcom/x/urt/features/d;->a:Ljava/util/concurrent/ConcurrentHashMap;
[0028] invoke-virtual v7, v2, ConcurrentHashMap->remove(Ljava/lang/Object;)Ljava/lang/Object;
[002b] move-result-object v7
[002c] invoke-interface v0, Lcom/x/repositories/urt/n1;->d()Lcom/x/models/timelines/n;
[002f] move-result-object v7
[0030] iget-object v7, v7, Lcom/x/models/timelines/n;->a:Ljava/lang/String;
[0032] invoke-static v2, v7, TimelineScrollPositionStore->restore(Enum,String)[I
[0035] move-result-object v6                  # int[] positions
[0036] if-eqz v6, :original_continuation
[0038] const/4 v7, 0
[0039] aget v7, v6, v7                       # index
[003b] const/4 v2, 1
[003c] aget v2, v6, v2                       # offset
[003e] new-instance v6, Lcom/x/urt/m0;           # v6 = holder
[0040] invoke-direct {v6, v7, v2}, Lcom/x/urt/m0;-><init>(II)V
[0043] invoke-interface v0, Lcom/x/repositories/urt/n1;->a()Lcom/x/models/timelines/u;
[0046] move-result-object v2                  # v2 = map key again
[0047] move-object/from16 v6, p0                # BUG: overwrites holder with y
[0049] iget-object v7, v6, Lcom/x/urt/y;->h:Lcom/x/urt/features/d;
[004b] iget-object v7, v7, Lcom/x/urt/features/d;->a:Ljava/util/concurrent/ConcurrentHashMap;
[004d] invoke-virtual v7, v2, v6, ConcurrentHashMap->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
[0050] move-result-object v7
```

The block then falls through to the external continuation at `0x51`, which
performs the original map read and cast shown above.

### Register/value trace at the failure

| Offset | Instruction effect | Runtime value |
| ---: | --- | --- |
| `0x0035` | `move-result-object v6` from `restore` | `v6` is `int[]` with index/offset |
| `0x003e` | `new-instance v6, m0` | `v6` is an uninitialized `m0` |
| `0x0040` | `invoke-direct {v6,...}` | `v6` is an initialized `com.x.urt.m0` |
| `0x0047` | `move-object/from16 v6, p0` | `v6` is now the receiver `com.x.urt.y` |
| `0x004d` | `put(map=v7, key=v2, value=v6)` | map entry value is `com.x.urt.y` |
| `0x0058` | map `get(key=v2)` | result object is `com.x.urt.y` in `v5` |
| `0x005c` | `check-cast v5, m0` | throws `ClassCastException` |

The `ConcurrentHashMap` API is erased to `Object`, so the incorrect value is
accepted by `put`. The cast is the first runtime operation that enforces the
holder contract. This is why the exception names the component class and the
holder class rather than reporting an error at the injected `put`.

## Why the failing path is reachable

`TimelineScrollPositionStore.restore(Enum,String)` returns an `int[]` when a
stored index exists. The patch's default timeline-position setting is enabled,
and its save hook persists the holder's two integer positions. After a process
restart or any path with a saved position:

```text
restore(...) != null
  -> new m0(index, offset)
  -> buggy map put (stores p0/y)
  -> original map get
  -> check-cast to m0
  -> CCE
```

Other branches can appear healthy:

* An existing valid in-memory map value takes the `continue` label before the
  injected persistent restore and does not execute the buggy `put`.
* A missing persistent position branches to the original continuation without
  writing a replacement value.
* The setting-disabled/native fallback branch returns an ordinary `m0` and
  does not use the map value.

These branch differences explain why the failure is state- and path-dependent;
they do not make the `put` value safe when the restore result is non-null.

## Root-cause classification

| Candidate cause | Result |
| --- | --- |
| Wrong holder fingerprint or wrong 12.27 owner | Not the cause. `m0` and `y.d()` are the exact target method/return shape. |
| `cloneMutable` parameter shift | Not the cause. The clone correctly preserves the receiver in `v5`, shifts physical `p0` to `v11`, and provides non-overlapping `v6..v10` scratch registers. |
| Register encoding overflow | Not the cause. All injected low-register operands are four-bit encodable; `move-object/from16` handles physical `p0`. |
| External continuation label | Not the cause. It intentionally targets the original map read, where the CCE is observed. |
| Fallback label | Not the cause. It targets the original zero-position holder allocation and is bypassed on restore success. |
| Holder/map value aliasing | **Root cause.** `mapOwnerRegister == positionsRegister == v6`; line 384 overwrites the holder before line 387 passes it as the map value. |

The same source-level alias would also be emitted for the 12.26 shape if the
current patch were applied there (`scratchBase == 7`, holder/map-owner alias in
`v7`). No patched 12.26 APK was supplied, so this recon establishes the 12.27
runtime failure and identifies a source-level defect, but does not claim a
separate 12.26 runtime crash.

## Implementor fix

Keep the dynamic clone/range logic at source lines 163–189. In the restore
success tail of the injected block, source lines 384–386 must use the distinct
`mapRegister` as the temporary map-owner receiver, while the holder remains in
`positionsRegister`:

```smali
# Recommended emitted tail
invoke-interface {v$timelineGetterReceiverRegister}, v$timelineGetterReference
move-result-object v$timelineRegister
move-object/from16 v$mapRegister, p0
iget-object v$mapRegister, v$mapRegister, $componentField
iget-object v$mapRegister, v$mapRegister, $mapField
invoke-virtual {
    v$mapRegister,
    v$timelineRegister,
    v$positionsRegister,
}, ConcurrentHashMap->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
move-result-object v$mapRegister
```

In the current target this emits `v7` for the map-owner/map and keeps `v6` as
the `m0` holder:

```smali
move-object/from16 v7, p0
iget-object v7, v7, Lcom/x/urt/y;->h:Lcom/x/urt/features/d;
iget-object v7, v7, Lcom/x/urt/features/d;->a:Ljava/util/concurrent/ConcurrentHashMap;
invoke-virtual v7, v2, v6, ConcurrentHashMap->put(...)
```

The minimal source edit is therefore to replace only the three receiver/map
setup lines currently interpolating `mapOwnerRegister` in the post-holder
tail:

```kotlin
// Current problematic tail
move-object/from16 v$mapOwnerRegister, p0
iget-object v$mapOwnerRegister, v$mapOwnerRegister, $componentField
iget-object v$mapRegister, v$mapOwnerRegister, $mapField

// Safe tail; positionsRegister still holds the holder for the put value
move-object/from16 v$mapRegister, p0
iget-object v$mapRegister, v$mapRegister, $componentField
iget-object v$mapRegister, v$mapRegister, $mapField
```

Leave the earlier uses of `mapOwnerRegister` at lines 354–356 and 365–367
unchanged; those occur before `positionsRegister` becomes the holder. Leave
the `put` value as `v$positionsRegister` at line 387. For readability, the
implementor may rename that logical value to `holderRegister`, but the required
invariant is:

```text
holderRegister != mapOwnerForPutRegister
holderRegister remains m0 from new-instance through put
```

Do not solve this by hardcoding `v6`, `v7`, `y`, `m0`, or a release version.
Use the already derived scratch registers and dynamically resolved references.

## Post-fix acceptance checks

1. Build the real MPP from the corrected source.
2. Patch both exact original APKs in exclusive mode.
3. Inspect `Lcom/x/urt/y;->d()Lcom/x/urt/m0;` in the 12.27 output and the
   corresponding 12.26 method.
4. Confirm that after the holder constructor, the register passed as the
   `ConcurrentHashMap.put` value is still the holder register and there is no
   intervening copy of `p0` into it.
5. Confirm the external `continue` target still reaches the original map
   read/check-cast and that the fallback target still reaches the native zero
   holder allocation.
6. Exercise a saved-position restore, a missing-position fallback, and a valid
   in-memory map path. The saved-position case is the red-capable regression
   for this CCE.

Useful commands:

```text
~/.local/bin/dexscope inspect-method \
  /Users/steven/Downloads/piko-twitter-patched.apk \
  'Lcom/x/urt/y;->d()Lcom/x/urt/m0;' --limit 500

~/.local/bin/dexscope inspect-method \
  ./apks/twitter_12.27.0-alpha.01.apk \
  'Lcom/x/urt/y;->d()Lcom/x/urt/m0;' --limit 500

~/.local/bin/dexscope inspect-method \
  ./apks/twitter_12.26.0-alpha.03.apk \
  'Lcom/x/urt/z;->d()Lcom/x/urt/n0;' --limit 500
```

`dexscope verify-method` also reports conservative type-join diagnostics for
these methods, including the original release methods. Those diagnostics are
not needed to establish this CCE: the exact patched instruction sequence and
the exception's concrete `com.x.urt.y` value identify the failure directly.
