# Restore timeline position VerifyError reconnaissance

## Scope

- Package: `com.twitter.android`
- Source commit inspected: `dec951d008d8a617d234b374563408ba5d364c75`
- 12.27 source APK: `apks/twitter_12.27.0-alpha.01.apk` (`12.27.0-alpha.01`, version code `312270201`)
- 12.26 source APK: `apks/twitter_12.26.0-alpha.03.apk` (`12.26.0-alpha.03`, version code `312260203`)
- Patched APK: `/Users/steven/Downloads/piko-twitter-patched.apk` (`12.27.0-alpha.01`, version code `312270201`)
- Patch source: `patches/src/main/kotlin/app/crimera/patches/newx/timeline/RestoreTimelinePositionPatch.kt`, especially lines 518–580 and 603–614.

## Finding

The VerifyError is caused by register reuse, not by an incorrect repository field descriptor.
`saveRepositoryRegister` stores the numeric register used as the receiver of the earlier
repository-field read. It does not preserve the object value in that register until the later map
write.

In 12.27, the selected field read is:

```smali
iget-object v0, v0, Lcom/x/urt/y;->c:Lcom/x/repositories/urt/n1;
invoke-interface {v0}, Lcom/x/repositories/urt/n1;->a()Lcom/x/models/timelines/u;
move-result-object v0
```

The patch records `registerB == v0` as `saveRepositoryRegister`. The getter result then reuses
`v0`, so at the map-write insertion point `v0` is `Lcom/x/models/timelines/u`, not
`Lcom/x/urt/y`. The patched APK consequently contains:

```smali
iget-object v3, v0, Lcom/x/urt/y;->c:Lcom/x/repositories/urt/n1;
```

at offset `0x16a`. This is the exact instruction named by the runtime crash and explains:
“cannot access instance field ... `y.c` from object of type ... `timelines.u`.”

## Dexscope evidence

### 12.27 source method

`dexscope inspect-method` identified the save handler as
`Lcom/x/urt/y;->j(Lcom/x/urt/r1;)V` with 152 registers and two parameters. The receiver is
`p0 == v150`; the method begins with `move-object/from16 v0, p0`.

The relevant original instructions are:

```text
0x137  #154  iget-object v2, v0, Lcom/x/urt/y;->h:Lcom/x/urt/features/d;
0x139  #155  iget-object v0, v0, Lcom/x/urt/y;->c:Lcom/x/repositories/urt/n1;
0x13b  #156  invoke-interface v0, ...->a()Lcom/x/models/timelines/u;
0x13e  #157  move-result-object v0                 # v0 = timelines.u
0x13f  #158  invoke-virtual v0, Object->getClass()
0x142  #159  iget-object v2, v2, ...ConcurrentHashMap;
0x144  #160  invoke-virtual v2, v0, v1, ...ConcurrentHashMap;->put(...)
```

The resolver at lines 518–557 selects instruction `#155`: its `TwoRegisterInstruction` has
`registerA == v0` (repository result) and `registerB == v0` (the `y` receiver at that point).
The later map write uses `v0` as the timeline key, proving that the same register has been reused.

### Patched 12.27 APK

`cloneMutable(additionalRegisters = numberOfParameterRegisters + 1)` increases the method from
152 to 155 registers. Dexscope reports the patched receiver as `p0 == v153`; the generated method
copies parameters into the old high locals before executing the original body:

```text
0x0000  move-object/from16 v150, p0
0x0002  move-object/from16 v151, p1
0x0004  move-object/from16 v0, v150
```

This parameter shift is visible but is not the root cause: the original local `v0` remains the
receiver alias until the repository getter overwrites it.

The patched save block is:

```text
0x015d  #174  iget-object v2, v0, Lcom/x/urt/y;->h:...
0x015f  #175  iget-object v0, v0, Lcom/x/urt/y;->c:...
0x0161  #176  invoke-interface v0, ...->a()...
0x0164  #177  move-result-object v0             # v0 = timelines.u
0x0165  #178  invoke-virtual v0, Object->getClass()
0x0168  #179  iget-object v2, v2, ...ConcurrentHashMap;
0x016a  #180  iget-object v3, v0, Lcom/x/urt/y;->c:...  # invalid receiver
0x016c  #181  invoke-interface v3, ...->d()...
0x0172  #184  invoke-static v0, v3, v1, TimelineScrollPositionStore;->save(...)
0x0175  #185  invoke-virtual v2, v0, v1, ...ConcurrentHashMap;->put(...)
```

Dexscope's source-versus-patched diff reports three added registers and 25 added instructions;
the patched method contains the failing field read immediately before the new store call.

### 12.26 source method

The semantic counterpart is `Lcom/x/urt/z;->i(Lcom/x/urt/s1;)V` with 153 registers and two
parameters. Its receiver is `p0 == v151`, and its original body begins with `move-object/from16
v0, p0`.

The selected save read and map write are:

```text
0x0141  #159  iget-object v2, v0, Lcom/x/urt/z;->h:Lcom/x/urt/features/d;
0x0143  #160  iget-object v3, v0, Lcom/x/urt/z;->c:Lcom/x/repositories/urt/o1;
0x0145  #161  invoke-interface v3, ...->a()Lcom/x/models/timelines/w;
0x0148  #162  move-result-object v3             # v3 = timelines.w
0x0152  #166  iget-object v2, v2, ...ConcurrentHashMap;
0x0154  #167  invoke-virtual v2, v3, v1, ...ConcurrentHashMap;->put(...)
```

Here the same resolver would record `registerB == v0`, but `v0` still holds the component
receiver at the map write because the timeline result is placed in `v3`. This explains why the
existing implementation can appear to work on 12.26 while failing on 12.27: the register number
is not a stable value-flow handle across release implementations.

## Exact safe fix

At the injection point, use the stable instance receiver `p0`, but first copy it into the existing
allocated low register (`saveIdentityRegister`, obtained with `getFreeRegister4Bit()`):

```smali
move-object/from16 v<saveIdentityRegister>, p0
iget-object v<saveIdentityRegister>, v<saveIdentityRegister>, <repositoryField>
invoke-interface {v<saveIdentityRegister>}, <timelineIdentityGetter>
move-result-object v<saveIdentityRegister>
iget-object v<saveIdentityRegister>, v<saveIdentityRegister>, <timelineIdentityField>
```

This is encoding-safe:

- `move-object/from16` accepts a 16-bit source register. In patched 12.27, `p0` aliases `v153`
  after `cloneMutable`; it is therefore not safe to assume a four-bit parameter register.
- `getFreeRegister4Bit()` guarantees the destination is `v0..v15`.
- `iget-object` uses the four-bit `22c` register form, so both its destination and receiver are
  low registers after the copy.
- The receiver is read from `p0` at the point of use, avoiding all intervening local-register
  reuse.

Do not emit `iget-object v<scratch>, p0, <repositoryField>` directly: the physical `p0` register
is high after register expansion and cannot be encoded as the `iget-object` receiver. Do not use
`saveRepositoryRegister` as the later receiver, because in 12.27 it is `v0 == timelines.u` at the
map write. The semantic repository-read resolver and cardinality checks can remain; only the
injected receiver source must stop depending on the earlier `registerB` value.

## Commands used

```text
dexscope inspect-method ./apks/twitter_12.27.0-alpha.01.apk 'Lcom/x/urt/y;->j(Lcom/x/urt/r1;)V'
dexscope inspect-method ./apks/twitter_12.26.0-alpha.03.apk 'Lcom/x/urt/z;->i(Lcom/x/urt/s1;)V'
dexscope inspect-method /Users/steven/Downloads/piko-twitter-patched.apk 'Lcom/x/urt/y;->j(Lcom/x/urt/r1;)V'
dexscope registers <each APK> '<corresponding method>'
dexscope diff ./apks/twitter_12.27.0-alpha.01.apk /Users/steven/Downloads/piko-twitter-patched.apk 'Lcom/x/urt/y;->j(Lcom/x/urt/r1;)V'
```

The artifact records bytecode evidence only; no device interaction was performed.
