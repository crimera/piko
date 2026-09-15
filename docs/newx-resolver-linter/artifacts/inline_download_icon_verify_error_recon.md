# Recon: `NewX: Inline download button` VerifyError on Twitter 12.27

## Target & Scope
- Target: `com.twitter.android` 12.27.0-alpha.01 (`./apks/twitter_12.27.0-alpha.01.apk`)
- Control: `com.twitter.android` 12.26.0-alpha.03 (`./apks/twitter_12.26.0-alpha.03.apk`)
- Patched APK: `/Users/steven/Downloads/piko-twitter-patched.apk`
- Source file: `patches/src/main/kotlin/app/crimera/patches/newx/misc/inlineactions/InlineDownloadButtonPatch.kt`
- Target method: `Lcom/x/cards/impl/unified/components/appstore/h;->invoke(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;` (matched by `iconRenderer`)

## Runtime Crash
```text
java.lang.VerifyError: Verifier rejected class com.x.cards.impl.unified.components.appstore.h: 
java.lang.Object com.x.cards.impl.unified.components.appstore.h.invoke(java.lang.Object, java.lang.Object) failed to verify: 
[0xE3] cannot access instance field float com.x.cards.impl.unified.components.appstore.h.b from object of type Reference: com.x.icons.b
```

## Root Cause Analysis
In `InlineDownloadButtonPatch.kt:285-308`:
The patch attempts to:
1. Normalize the size float for Compose layout via `displayIconSize(markedIconSize)` -> `abs(size)`.
2. Select the download icon via `selectIcon(icon, markedIconSize, incomingIcon)` by checking `markedIconSize < 0`.

To pass `markedIconSize` to `selectIcon`, the patch emitted:
```smali
sget-object v$incomingIconRegister, $incomingIconField
iget v$sizeArgumentRegister, p0, $sizeField
invoke-static {v$iconArgumentRegister, v$sizeArgumentRegister, v$incomingIconRegister}, selectIcon(...)
```
And before the switch at `sizeAccess`:
```smali
invoke-static {v$sizeRegister}, displayIconSize(F)F
move-result v$sizeRegister
```

### The Bytecode Truth in 12.27:
In 12.27, R8 hoisted the size field read before `packed-switch`:
```smali
[000a] (06) iget p0, p0, Lcom/x/cards/impl/unified/components/appstore/h;->b:F
[000c] (07) packed-switch v0, :pswitch_data_0
```
Notice that R8 reused `p0` as the destination register!
- Before instruction (06), `p0` is `this` (`Lcom/x/cards/impl/unified/components/appstore/h;`).
- After instruction (06), `p0` holds the float `b:F`. The instance receiver `this` is completely discarded by R8 because it is never used again in the method.
- `sizeRegister` resolves to `p0` (`v14`).

Consequently:
1. Inside `:pswitch_2` (`TwitterShare` branch), `p0` is a float, NOT `this`. Any attempt to read `iget ..., p0, $sizeField` is invalid bytecode.
2. Furthermore, `freeRegisters4Bit` allocated `p0` (`v14`) as `incomingIconRegister`! So `sget-object p0, Lcom/x/icons/a;->V4:Lcom/x/icons/b;` overwrote `p0` with `com.x.icons.b`.
3. The next instruction `iget v10, p0, ...->b:F` attempted to read field `b` from `com.x.icons.b`, causing the runtime `VerifyError` at `[0xE3]`.
4. Also, calling `displayIconSize(p0)` at instruction (07) stripped the negative sign from `p0`, making it impossible for `selectIcon` to determine if the action was a download action if `p0` was read after (07).

## Exact Safe Fix
The signed float in `sizeRegister` (`p0` in 12.27, or branch-local in 12.26) already holds the marked icon size directly from `sizeField`. We do not need to re-read `sizeField` from `p0` at all!

Instead:
1. Do NOT insert `displayIconSize` before the switch or at `sizeAccess`. Leave the signed float in `sizeRegister` intact until icon selection.
2. At `iconAccess.index + 1`:
   - Call `selectIcon(icon, sizeRegister, incomingIcon)`.
   - Pass `sizeRegister` directly (it is still negative for download action).
   - Immediately after `selectIcon`, normalize `sizeRegister` for layout:
     ```smali
     invoke-static {v$sizeRegister}, $EXTENSION->displayIconSize(F)F
     move-result v$sizeRegister
     ```
   - Downstream layout (`Modifier.size(Modifier, sizeRegister)`) receives the normalized positive float.
3. Register Allocation:
   - `sizeRegister` is already available (in 12.27 it is `p0`, $\le 15$; in 12.26 it is $\le 15$).
   - `iconRegister` is `v5` ($\le 15$).
   - For `incomingIconRegister`, allocate a free 4-bit scratch register from `getFreeRegisterProvider` excluding `sizeRegister`, `iconRegister`, and parameter registers, OR use an allocated scratch.
   - Zero instructions reading `p0, $sizeField`.

## Acceptance Criteria
1. `./gradlew :patches:build --no-daemon` passes.
2. `./gradlew :patches:lintNewxResolvers --no-daemon` passes.
3. Exclusive patch validation on 12.27 reports `Applied: NewX: Inline download button`.
4. Inspecting `Lcom/x/cards/impl/unified/components/appstore/h;->invoke` with `dexscope` confirms:
   - No `iget ..., p0, ...` after `p0` holds float.
   - `selectIcon` receives `icon`, `sizeRegister`, `incomingIcon`.
   - `displayIconSize` normalizes `sizeRegister`.
