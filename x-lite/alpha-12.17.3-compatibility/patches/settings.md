# X-Lite Settings

## Why it broke

The alpha changed its Compose runtime call from the older interface shape to an obfuscated/relocated virtual call (`androidx/compose/runtime/l0`). The shared settings fingerprint required `INVOKE_INTERFACE` on `Composer`, so it no longer matched.

The standalone settings activity also depended on host resources removed from the alpha:

- `Twitter`, `Twitter.Standard`, and `Twitter.LightsOut` styles
- `preference_fragment_activity`
- a fully functional AppCompat `Toolbar`

The host still publishes `fragment_container`, and Android may restore a fragment using that ID.

## Fix

- Removed the strict Compose invocation opcode and defining-class constraints while retaining the renderer signature, `title` string, and semantic `(I) -> Composer` call shape.
- Made Twitter style application optional and added `Theme.AppCompat.DayNight.NoActionBar` as a fallback.
- Replaced the missing host layout with a programmatic extension-owned root, toolbar row, and fragment container.
- Replaced AppCompat `Toolbar` with framework views to avoid stripped internal state.
- Reused the host `fragment_container` ID so saved fragment restoration finds the new container; retained a fixed fallback ID.

## Verification

- Extension release build passes.
- User runtime-tested the alpha settings screen after each crash was fixed and confirmed it opens.
- Older-version regression test remains pending.
