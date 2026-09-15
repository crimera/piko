# Restore timeline position recon: Twitter/X 12.27.0-alpha.01

## Outcome

The 12.27 target still contains the same NewX timeline-position behavior and
all of the patch's semantic anchors. The failure is register allocation only.

`RestoreTimelinePositionPatch.kt` expands the matched getter by six registers,
then asks `FreeRegisterProvider` for two four-bit scratch registers at the
instruction immediately after the timeline result. On the control APK the
provider exposes two candidates (`v3`, `v4`). On the target it exposes only
`v3`; the first `getFreeRegister4Bit()` consumes it and the second call throws
`IllegalStateException("No free registers available")`.

The target getter has one fewer original local register and R8 reuses the
receiver register as a local. More importantly, its post-result continuation
does not contain the early single-register writes that make `v3` and `v4`
appear available to the provider in the control shape. The appended clone
registers are not returned as usable candidates at this insertion point.

The clean repair is to reserve the clone's new local range structurally, using
the original method register count as the base, and use those reserved
registers directly. For the two APKs this gives:

```text
12.26: original total=7, expanded total=13, reserved getter locals v7..v11
12.27: original total=6, expanded total=12, reserved getter locals v6..v10
```

Use the first two reserved locals for the main restore block and the remaining
three for the zero-position fallback. Validate that the entire range is
four-bit encodable and does not overlap the shifted parameter registers; throw
a descriptive `PatchException` when it is not. This derives the range from
the matched method shape, has no release routing, and keeps all class, method,
field, and enum references dynamically resolved.

## Frozen inputs

Analysis used source commit `d6efb26d263000ed9960069dd6fdc93348f12d90` with an
already-dirty worktree. The production source file was restored after
temporary diagnostics; this recon is the only file added for this task.

| Role | APK | Identity | SHA-256 |
| --- | --- | --- | --- |
| Control | `apks/twitter_12.26.0-alpha.03.apk` | `com.twitter.android`, `12.26.0-alpha.03` | `4a5bc95c8b6a9cd1ceb6e159333943b11e094ecf168153f14decb14d697ccf5e` |
| Target | `apks/twitter_12.27.0-alpha.01.apk` | `com.twitter.android`, `12.27.0-alpha.01` | `82d0fe729854d631bb8069b3cab25f47d64361722cb99c483cc7e8e2b178d67d` |

The clean MPP used for reproduction was
`patches/build/libs/patches-3.9.0-dev.4.mpp`, SHA-256
`cc798c383b96412f8adf2434db7503255819b24d86079163c17d07bb8b78ea2f`.
It was built locally from this checkout by `:patches:build`; no external MPP
or extension override was supplied. The patch runner was Morphe desktop 1.11.0
(`morphe-desktop-1.11.0-all.jar`).

The existing incident record is
`docs/newx-resolver-linter/incidents/2026-09-13-12-27-sweep.md`.

## Reproduction and validation commands

The analysis used the required `dexscope` CLI, including `inspect-method`,
`inspect-class`, `registers --json`, and string-anchor queries. The clean
validation commands were:

```text
./gradlew :patches:build --no-daemon
./gradlew :patches:lintNewxResolvers --no-daemon
```

Both completed successfully. The resolver lint reported:

```text
NewX resolver lint passed: /Volumes/realme/Dev/piko-x-lite/patches/src/main/kotlin/app/crimera/patches/newx
```

Using the clean MPP with the exclusive patch command from the incident:

```text
java -jar /Volumes/realme/Dev/piko/morphe-desktop-1.11.0-all.jar patch \
  -p patches/build/libs/patches-3.9.0-dev.4.mpp \
  --keystore Morphe.keystore --exclusive \
  -e 'NewX: Restore timeline position' --force \
  -o /tmp/piko-restore-clean-12.26.apk \
  -t /tmp/piko-restore-clean-12.26.tmp \
  ./apks/twitter_12.26.0-alpha.03.apk
```

Result: exit 0, `Applied: NewX: Restore timeline position`, saved to
`/tmp/piko-restore-clean-12.26.apk`.

The same command against `twitter_12.27.0-alpha.01.apk` returned exit 1:

```text
SEVERE: FAILED: NewX: Restore timeline position
app.morphe.patcher.patch.PatchException: Could not allocate NewX timeline-position restore registers
Caused by: java.lang.IllegalStateException: No free registers available
SEVERE: Patching aborted: FAILED: NewX: Restore timeline position
```

## Fingerprint and resolver comparison

The current source locations are `RestoreTimelinePositionPatch.kt:46-105`
for the holder/getter/save fingerprints, `:133-177` for holder/getter
resolution and cloning, `:179-207` for the map read, `:208-322` for the
timeline and identity chain, `:333-351` for the failing allocation,
`:396-449` for the fallback, and `:502-644` for the save hook.

| Semantic item | 12.26 control | 12.27 target | Result |
| --- | --- | --- | --- |
| Holder string anchor | `Lcom/x/urt/n0;->toString()Ljava/lang/String;` | `Lcom/x/urt/m0;->toString()Ljava/lang/String;` | One match each; same two position fields and constructor `(II)V`. |
| Getter fingerprint | `Lcom/x/urt/z;->d()Lcom/x/urt/n0;` | `Lcom/x/urt/y;->d()Lcom/x/urt/m0;` | One match each. |
| Timeline getter | `Lcom/x/repositories/urt/o1;->a()Lcom/x/models/timelines/w;` | `Lcom/x/repositories/urt/n1;->a()Lcom/x/models/timelines/u;` | Dynamically derived; both return an enum. |
| Position map | `Lcom/x/urt/features/d;->a:Ljava/util/concurrent/ConcurrentHashMap;` | Same descriptor and field relationship | Unique map field; public definition. |
| Component repository | `Lcom/x/urt/z;->c:Lcom/x/repositories/urt/o1;` | `Lcom/x/urt/y;->c:Lcom/x/repositories/urt/n1;` | Unique component-to-repository field. |
| Identity getter | `o1->d()Lcom/x/models/timelines/n;` | `n1->d()Lcom/x/models/timelines/n;` | Unique semantic model getter. |
| Identity field | `Lcom/x/models/timelines/n;->a:Ljava/lang/String;` | Same descriptor and field | Exactly one public `String` field. |
| Save hook | `Lcom/x/urt/z;->i(Lcom/x/urt/s1;)V` | `Lcom/x/urt/y;->j(Lcom/x/urt/r1;)V` | One match each from holder field, save string, and CHM `put`. |
| Layout policy gate | `IGET_BOOLEAN` followed by `IF_EQZ` | Same shape | Exactly one target gate; no adaptation needed. |

The string-anchor locations are:

```text
12.26  ScrollPositionHolder(...)       -> n0.toString() @ 0x6
12.26  Restoring scrolling position    -> z.d()          @ 0x36
12.26  Saving scrolling positions      -> z.i(s1)        @ 0x10f

12.27  ScrollPositionHolder(...)       -> m0.toString() @ 0x6
12.27  Restoring scrolling position    -> y.d()          @ 0x31
12.27  Saving scrolling positions      -> y.j(r1)        @ 0x105
```

No resolver failed before the register allocation. The temporary bypass
described below reached the fallback and save hooks, confirming their
cardinality and descriptor resolution on the target.

## Getter bytecode evidence

### Control: `Lcom/x/urt/z;->d()Lcom/x/urt/n0;`

`dexscope` reports 7 total registers, 1 parameter register, locals `v0..v5`,
and receiver `p0 == v6`.

The relevant data flow is:

```smali
[0000] iget-object v0, p0, Lcom/x/urt/z;->e:Landroidx/compose/foundation/layout/v0;
[0002] iget-boolean v0, v0, Landroidx/compose/foundation/layout/v0;->b:Z
[0004] const/4 v1, 0
[0005] if-eqz v0, :cond_0
[0007] iget-object v0, p0, Lcom/x/urt/z;->c:Lcom/x/repositories/urt/o1;
[0009] invoke-interface v0, Lcom/x/repositories/urt/o1;->a()Lcom/x/models/timelines/w;
[000c] move-result-object v2
[000d] iget-object v3, p0, Lcom/x/urt/z;->h:Lcom/x/urt/features/d;
[000f] invoke-virtual v3, Ljava/lang/Object;->getClass()Ljava/lang/Class;
[0012] const-string v4, "timelineType"
[0014] invoke-static v2, v4, Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V
[0017] iget-object v3, v3, Lcom/x/urt/features/d;->a:Ljava/util/concurrent/ConcurrentHashMap;
[0019] invoke-virtual v3, v2, Ljava/util/concurrent/ConcurrentHashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;
[001c] move-result-object v2
[001d] check-cast v2, Lcom/x/urt/n0;
[001f] if-nez v2, :cond_1
...
[002e] if-eqz v1, :cond_2
[0030] invoke-interface v0, Lcom/x/repositories/urt/o1;->a()Lcom/x/models/timelines/w;
...
[0036] const-string v4, "Restoring scrolling position for "
...
[0069] return-object v2
```

The original timeline result is instruction ordinal 6 (`move-result-object
v2`); the map read is ordinal 12. The original continuation at ordinal 7 is
`iget-object v3, p0, ...->h`, which writes `v3` immediately after the hook.

### Target: `Lcom/x/urt/y;->d()Lcom/x/urt/m0;`

`dexscope` reports 6 total registers, 1 parameter register, locals `v0..v4`,
and receiver `p0 == v5`. R8 reuses the receiver register after the repository
result is no longer needed:

```smali
[0000] iget-object v0, p0, Lcom/x/urt/y;->e:Landroidx/compose/foundation/layout/v0;
[0002] iget-boolean v0, v0, Landroidx/compose/foundation/layout/v0;->b:Z
[0004] const/4 v1, 0
[0005] if-eqz v0, :cond_0
[0007] iget-object v0, p0, Lcom/x/urt/y;->c:Lcom/x/repositories/urt/n1;
[0009] invoke-interface v0, Lcom/x/repositories/urt/n1;->a()Lcom/x/models/timelines/u;
[000c] move-result-object v2
[000d] invoke-virtual v2, Ljava/lang/Object;->getClass()Ljava/lang/Class;
[0010] iget-object p0, p0, Lcom/x/urt/y;->h:Lcom/x/urt/features/d;
[0012] iget-object p0, p0, Lcom/x/urt/features/d;->a:Ljava/util/concurrent/ConcurrentHashMap;
[0014] invoke-virtual p0, v2, Ljava/util/concurrent/ConcurrentHashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;
[0017] move-result-object p0
[0018] check-cast p0, Lcom/x/urt/m0;
[001a] if-nez p0, :cond_1
[001c] new-instance p0, Lcom/x/urt/m0;
[001e] invoke-direct p0, v1, v1, Lcom/x/urt/m0;-><init>(II)V
...
[002b] invoke-interface v0, Lcom/x/repositories/urt/n1;->a()Lcom/x/models/timelines/u;
...
[0031] const-string v3, "Restoring scrolling position for "
...
[0064] return-object p0
```

The target's original timeline result is also ordinal 6 (`v2`), but its map
read is ordinal 10. Thus the semantic insertion point is the same original
ordinal (`timeline result + 1 == 7`), despite the changed map sequence. After
`cloneMutable` adds its parameter-preservation prologue, both methods report
`timelineResultIndex == 7` and call the allocator at insertion index 8.

The important control-flow difference is the instruction immediately after
that insertion point:

```text
control: iget-object v3, p0, ...->h       # single-register destination
target:  invoke-virtual v2, Object->getClass()  # no destination register
        iget-object p0, p0, ...->h        # receiver/destination alias
```

The target has no spare local at this point. It keeps `v0` live as the
repository receiver, `v1` live for zero/default and logging, `v2` as the
timeline key and later builder value, and `v3`/`v4` for the later logger path.
`v5` is the original receiver and is also the register that the un-cloned
method reuses for the map and returned holder.

## Register and liveness evidence

The direct `dexscope registers --json` results for the original methods were:

```text
control: total=7, params=1, locals=v0..v5, receiver=v6
  scratch_register_available=false
  safe_scratch_registers=[]
  warnings:
    [REFERENCE_RECEIVER] v0,v1,v2,v3
    [BRANCH_LIVE] v0,v1,v2,v3,v4,v5
    [NO_SAFE_SCRATCH]

target:  total=6, params=1, locals=v0..v4, receiver=v5
  scratch_register_available=false
  safe_scratch_registers=[]
  warnings:
    [REFERENCE_RECEIVER] v0,v1,v2
    [BRANCH_LIVE] v0,v1,v2,v3,v4
    [NO_SAFE_SCRATCH]
```

The temporary diagnostic was compiled into an isolated MPP only; it was not
left in source. It printed the provider state immediately before the failing
allocation:

```text
control method=Lcom/x/urt/z;->d()Lcom/x/urt/n0;
  registers=13 params=1 timelineResultIndex=7 insertion=8
  timeline=v2 receiver=v0 available=2
  used-and-unavailable=[2, 0, 1, 5, 6, 7, 8, 9, 10, 11, 12]

target method=Lcom/x/urt/y;->d()Lcom/x/urt/m0;
  registers=12 params=1 timelineResultIndex=7 insertion=8
  timeline=v2 receiver=v0 available=1
  used-and-unavailable=[2, 0, 1, 4, 5, 6, 7, 8, 9, 10, 11]
```

Therefore the provider candidate sets are `{v3, v4}` for control and `{v3}`
for target. The current source calls `getFreeRegister4Bit()` twice in
`List(RESTORE_TEMPORARY_REGISTER_COUNT)`. The first target call returns `v3`;
the second call sees an empty deque and produces the reported exception.

`cloneMutable` from the pinned Morphe patches library (1.5.0) increases the
method's declared register count and, for an instance method, inserts a
parameter-preservation move before the original instructions. It does not
make every newly appended register a candidate in `FreeRegisterProvider`'s
path-sensitive queue. In this run the new blank locals were treated as
unavailable by the provider at this hook:

```text
control expanded total=13: new local range v7..v11, shifted p0=v12
target  expanded total=12: new local range v6..v10, shifted p0=v11
```

This is allocator candidate starvation, not an invoke-format overflow or a
missing Dalvik register in the expanded method. The control result is
incidental to its generated instruction order; relying on it is unsafe under
ordinary R8 churn.

## Validation of the proposed register shape

For a temporary isolated check, the restore and fallback allocations were
replaced with the structurally derived ranges:

```text
scratchBase = expandedRegisterCount
              - restoreCount - fallbackCount - parameterRegisterCount
12.27 target: scratchBase=6
  restore=v6,v7
  fallback=v8,v9,v10
```

The diagnostic MPP applied the target successfully and emitted
`/tmp/piko-restore-debug2-12.27.apk`. Its getter DEX begins:

```smali
# registers: total=12; receiver p0=v11
[0000] move-object/from16 v5, p0
...
[000f] iget-object v6, p0, Lcom/x/urt/y;->h:Lcom/x/urt/features/d;
[0011] iget-object v7, v6, Lcom/x/urt/features/d;->a:Ljava/util/concurrent/ConcurrentHashMap;
[0013] invoke-virtual v7, v2, ...->get(Ljava/lang/Object;)Ljava/lang/Object;
[0016] move-result-object v6
```

The complete diagnostic output was inspected with `dexscope`; the fallback
uses `v8..v10`, and the original body continues with its receiver-preservation
alias at `v5`. This confirms the range is non-overlapping and encodable on
both declared targets.

The same diagnostic run also showed that later register paths are not hidden
12.27 failures:

```text
fallback provider after the restore injection: available=9, used=[0,5,1]
save identity provider after the save clone: registers=155, available=152, used=[0,1,2]
```

The temporary diagnostic MPP had SHA-256
`77e095fc4c7cdd3ed593b4336c7478635d13a2460553bfab8775fc7c66007159` and was
discarded by rebuilding the clean MPP.

## Other fingerprints and hooks

### Holder and fallback

Both holder classes have public final integer fields `a` and `b`, a public
constructor `<init>(II)V`, and the two required `toString` strings. The target
holder is only a regenerated/renamed `m0`; the holder fingerprint remains
unique. The fallback shape is still exactly one `new-instance` followed by
the matching constructor and `return-object` on the target.

The fallback setting read currently uses `injectRead` with a four-bit
constraint. It is not the reported failure. After the temporary restore
allocation, the target diagnostic saw `fallbackRead.register == v0`, holder
`v5`, and nine available candidates for the three fallback temporaries.

### Repository, identity, and map

The target repository interface is `Lcom/x/repositories/urt/n1;`. Its
`a()Lcom/x/models/timelines/u;` result is the enum map key. Its
`d()Lcom/x/models/timelines/n;` result is the identity object. The identity
class has exactly one public `String` field,
`Lcom/x/models/timelines/n;->a:Ljava/lang/String;`, in both APKs. These are
resolved from the matched timeline getter and model shape; there is no need to
add a target-specific class or method name.

The target map path is:

```smali
iget-object p0, p0, Lcom/x/urt/y;->h:Lcom/x/urt/features/d;
iget-object p0, p0, Lcom/x/urt/features/d;->a:Ljava/util/concurrent/ConcurrentHashMap;
invoke-virtual p0, v2, Ljava/util/concurrent/ConcurrentHashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;
```

The target `features/d` class has exactly one public final field `a` of the
required `ConcurrentHashMap` type. The component repository field is uniquely
`y->c:n1`, and the repository class is not the component class.

### Setter/save hook

The target save method is `Lcom/x/urt/y;->j(Lcom/x/urt/r1;)V` with 152 original
registers and two parameter registers (`p0=v150`, `p1=v151`). Its relevant
sequence is:

```smali
[00eb] iget-object v1, v1, Lcom/x/urt/j1;->a:Lcom/x/urt/m0;
[00ed] iget-object v2, v0, Lcom/x/urt/y;->e:Landroidx/compose/foundation/layout/v0;
[00ef] iget-boolean v2, v2, Landroidx/compose/foundation/layout/v0;->a:Z
[00f1] if-eqz v2, :cond_3
...
[0105] const-string v5, "Saving scrolling positions for "
...
[0137] iget-object v2, v0, Lcom/x/urt/y;->h:Lcom/x/urt/features/d;
[0139] iget-object v0, v0, Lcom/x/urt/y;->c:Lcom/x/repositories/urt/n1;
[013b] invoke-interface v0, Lcom/x/repositories/urt/n1;->a()Lcom/x/models/timelines/u;
[013e] move-result-object v0
[0142] iget-object v2, v2, Lcom/x/urt/features/d;->a:Ljava/util/concurrent/ConcurrentHashMap;
[0144] invoke-virtual v2, v0, v1, ...->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
```

The layout-policy gate is still one `IGET_BOOLEAN` followed by `IF_EQZ`; the
target's changed Kotlin string-empty helper (`g0` instead of control `q0`)
does not affect the save fingerprint. The target map-put registers are
`v2` (map), `v0` (timeline), and `v1` (holder), all four-bit encodable. The
temporary diagnostic reached the save identity allocation with 152 available
register candidates, so no 12.27 adaptation is required there.

## Proposed implementation

1. Capture the matched getter's original implementation register count and
   `numberOfParameterRegisters` before cloning.
2. Keep the existing clone size: `parameterRegisterCount + 2 + 3`. The clone
   library reserves the additional non-parameter locals immediately after the
   original register range and shifts the instance parameter region above
   them.
3. Define `scratchBase = originalRegisterCount` and reserve the five-register
   range `[scratchBase, scratchBase + 4]`. Assert that the range is within
   `0..15` and below the shifted parameter region. On failure, raise a
   `PatchException` containing the matched method, original/expanded counts,
   parameter count, and attempted range.
4. Assign `restoreRegisters = scratchBase..scratchBase + 1` and
   `fallbackRegisters = scratchBase + 2..scratchBase + 4`. Do not call
   `getFreeRegister4Bit()` for these newly reserved locals.
5. Keep the setting read separate from the reserved range. The existing
   `injectRead` accepts `excludedRegisters`; pass the reserved scratch
   registers (and the fallback holder register) to it, or add an equivalent
   range-aware overload if the setting helpers are consolidated. Retain its
   four-bit constraint and fail closed if no suitable read register exists.
6. Leave the semantic resolver and all exact-one assertions intact. If any
   candidate collection is touched while implementing this change, use the
   shared `requireExactlyOne`/`requireAtMostOne` helpers with semantic labels
   and candidate descriptions.

This is shape-based, not release-based. It preserves the control behavior,
fixes the target's one-local reduction, avoids reusing branch-live registers,
and remains fail-closed if a future method grows beyond the four-bit encoding
required by the injected `35c`/`22c` instructions. An invoke-static helper is
not necessary for the current contract; it would only be an alternative for a
future shape whose safe scratch range cannot fit in four-bit registers.

No device/runtime validation was performed because the repository rules
require explicit user permission before controlling a device. The patch-time
DEX validation above is sufficient to establish the allocation fix for these
two APK shapes.
