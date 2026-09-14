# Recon & Fix: Inline Download Button Missing Icon in 12.27

## Environment
- **Target APK**: Twitter/X `12.27.0-alpha.01` (`com.twitter.android`)
- **Control APK**: Twitter/X `12.26.0-alpha.03`
- **Component**: `InlineDownloadButtonPatch.kt`, `InlineDownloadButton.java`

## Symptom
- The inline download action item was successfully inserted into the post inline actions row.
- The button slot had allocated width/space and the click target was functional (tapping it triggered media download).
- However, the icon inside the circular touch target was completely blank/invisible.

## Root Cause Analysis
1. In `12.27.0-alpha.01`, R8 hoisted `iget p0, p0, Lcom/x/cards/impl/unified/components/appstore/h;->b:F` before the packed switch in `h.invoke`.
2. Register `p0` held the icon size (`b:F`), which was marked negative (`-24.0f`) by `markIconSize` to carry the download classification into `h`.
3. Inside `:pswitch_2` (the Share branch), Twitter invoked `Modifier.size(F)` passing `p0`.
4. `displayIconSize` was being invoked *after* `Modifier.size`.
5. Passing a negative dimension (`-24.0f`) into Compose's `Modifier.size` caused the layout node for the icon composable (`com.x.compose.core.i.d`) to collapse to `0x0 dp`.
6. Therefore, the parent action row allocated the slot and click listener, but the rendered icon had zero size.

## Solution
1. **Thread-Local State in Extension**:
   - `InlineDownloadButton.displayIconSize(float markedIconSize)` records whether `markedIconSize < 0` in `CURRENT_ICON_IS_DOWNLOAD` (`ThreadLocal<Boolean>`) and returns `Math.abs(markedIconSize)` (`+24.0f`).
   - `InlineDownloadButton.selectIcon(Object nativeIcon, Object downloadIcon)` reads and clears the thread-local state during the same UI composition pass, returning `downloadIcon` if true, or `nativeIcon` otherwise.
2. **Patch Mutator Order**:
   - Located `sizeModifierCall` (`Modifier.size(Modifier, F)`) in `:pswitch_2`.
   - Mutated `iconAccess` (`sget-object shareIconField`) first to inject 2-argument `selectIcon(nativeIcon, downloadIcon)`.
   - Injected `displayIconSize` before `sizeModifierCall` so `Modifier.size` receives positive layout size (`+24.0f`).
