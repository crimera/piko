# NewX post-menu filter register shuffle: hardcoded p3 hit the Flow param

- Date: 2026-09-14
- Reporter/session: user `check adb logs`; pi debug session
- APK package/version/build type: `com.twitter.android` 12.27.0-alpha.01 alpha, version code `312270201`
- APK path: on-device `/data/app/~~4338daAqPC_H0nJUqI4hHQ==/com.twitter.android-B6da1tP63uYgIKYFSzgacA==/base.apk` (pulled to `/tmp/piko-crash/base-patched.apk` for analysis)
- Source commit: `6049e089` plus working-tree `CustomizePostOptionsPatch` / `PostOptionsFilter` (pre-fix)
- Failing patch: `NewX: Customize post menu items` (new, unreleased)
- Severity: high (repeat crash loop, `crashed quickly`, MainActivity force-finished)
- Confidence: confirmed (patched-DEX ground truth)

## Symptom

Patched app crashes on launch, repeat loop until ActivityManager kills it.

## Complete error

```
E/AndroidRuntime( 4937): java.lang.IncompatibleClassChangeError:
  Class 'kotlinx.coroutines.flow.d' does not implement interface 'java.util.List'
  in call to 'boolean java.util.List.isEmpty()'
  (declaration of 'app.morphe.extension.newx.misc.PostOptionsFilter'
   appears in .../com.twitter.android-.../base.apk)
E/AndroidRuntime( 4937):   at app.morphe.extension.newx.misc.PostOptionsFilter.filter(PostOptionsFilter.java:18)
E/AndroidRuntime( 4937):   at com.x.urt.items.post.options.j0.<init>(Unknown Source:58)
E/AndroidRuntime( 4937):   at com.x.urt.items.post.options.t.b(Unknown Source:1668)
```

## Cause

The patch template hardcoded `p3` as the options-list register:

```
invoke-static {p3, vH}, ...PostOptionsFilter;->filter(...)...;
move-result-object p3
```

But the patch itself clones the state constructor with `+2` registers, and the
clone preserves original v-numbering by shuffling params down (`p2->p0`,
`p3->p1`, `p5->p3`, ...). Past the shuffle, `p3` holds the
`Lkotlinx/coroutines/flow/j` param, not the options `List`. The filter received
a `flow.d`, and `List.isEmpty()` threw `IncompatibleClassChangeError`.

Verified in pulled patched DEX: `j0.<init>` (11 regs) moves at [06..14] remap
`p3->p1` (List) and `p5->p3` (Flow); the filter call at [26..27] used `p3`.

## Fix

`CustomizePostOptionsPatch.injectPostOptionsFilter` now derives the options
register from the `IPUT_OBJECT` store operand (`TwoRegisterInstruction.registerA`)
instead of computing it from `p0 + param widths`, and emits `v`-registers in the
template. A `!in 0..15` guard fails closed because the call uses non-range
`invoke-static`. Lesson: never trust `p`-names in a method this patch (or an
earlier one) cloned or edited; resolve registers off the anchor instruction.

## Cause classification

- [ ] linter behavior
- [ ] cardinality-helper behavior
- [x] resolver logic
- [ ] APK contract drift
- [ ] tooling or artifact setup
- [ ] runtime behavior
