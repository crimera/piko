# NewX 12.29 reply-sorting: reported default ignored (false alarm) + prefetch-seed coverage

- Date: 2026-09-24
- Reporter/session: 12.29 port follow-up
- APK package/version/build type: `com.twitter.android` 12.29.0-alpha.04 alpha
- APK path and checksum: `apks/12.29.0-alpha.04.apk` / `767a55b4897b3d88391d4c53102f502f3f109ba7324b7158e53b2b1ce79fc7b2`
- Source commit: `b46d10dc10b8165f5c6d626f9d5f8ca49895b880` plus the working-tree change below
- MPP path and checksum: `patches/build/libs/patches-3.9.0-dev.4.mpp` (working-tree build)
- Command: `./patch-twitter.sh apks/12.29.0-alpha.04.apk`
- Failing patch: none. `NewX: Set default reply sorting` reported `Applied` throughout.
- Outcome: reported runtime failure was a false alarm — the `Default reply sorting` setting was
  set to `Relevance`, so every seed correctly resolved to `Relevance`. The patch was working.
- Resulting change: optional conversation-prefetch seed coverage (below), validated at patch time
  only; not required by the reported symptom.

## Reported symptom and diagnosis

Reported: "preferred default is ignored, manual change in the sheet works, setting is visible and
persists." A temporary diagnostic in `ReplySortingResolver` resolved it directly:

```text
getDefault remember=false last=null
getDefault loaded=true found=StringSetting raw=Relevance
getDefault configured=Relevance
getEnumDefault enum=com.x.models.gf mode=Relevance
```

`SettingsRegistry.getStringOrDefault("newx.timeline.default_reply_sorting", "Relevance")` returned
`Relevance` because that was the stored/selected value, not because the read failed. Confirming the
setting as a non-`Relevance` value is the only required control; no source defect was present.

## Why the patcher was silent (correctly)

The three fingerprints resolve to exactly one candidate on 12.29.0-alpha.04, so `Applied` was
accurate:

| Hook | 12.27/12.28 | 12.29.0-alpha.04 |
| --- | --- | --- |
| Repository init | `Lcom/x/postdetail/j;->invokeSuspend` | `Lcom/x/postdetail/l;->invokeSuspend` |
| Selection callback | `Lcom/x/payments/transaction/**;->invoke` | `Lcom/x/payments/transaction/list/i0;->invoke` |
| UI state init | `Lcom/x/ui/common/**;->invoke` | `Lcom/x/ui/common/l2;->invoke` |

## Conversation prefetch seed (coverage change)

12.29 adds a conversation prefetch (feature switch `x_android_conversation_prefetch_enabled`,
absent from 12.27/12.28) that seeds a second ranking mode in the post-detail ViewModel
constructor:

```smali
# Lcom/x/postdetail/n;-><init>(...)V
[0150] sget-object v3, Lcom/x/models/gf;->Relevance:Lcom/x/models/gf;
[0156] invoke-virtual v4, v1, v2, v3, Lcom/x/repositories/post/c0;->c(JLcom/x/models/gf;)V
```

The previous patch rewrote only the timeline-repository seed. The change adds an optional fourth
resolution so every known default-seeding site uses the configured mode:

- Fingerprint on `name = "<init>"`, `returnType = "V"`, and `x_android_conversation_prefetch_enabled`
  (no obfuscated owner).
- Require exactly one self-typed `Relevance` read of the enum resolved from the repository seed.
- Require the read register to feed an invoke whose parameters include that enum, else fail closed.
- Reuse the same `getEnumDefault` replacement. Zero matches on 12.27/12.28 keeps old behavior.

Patch-time validation only. Runtime verification with a non-`Relevance` default is still advisable
because the handover logic in `repositories/post/b0->a` compares the prefetch mode against the
repository mode and can invalidate the prefetch when they differ; all seeds are now consistent.

## Validation

- `./gradlew :patches:build :patches:lintNewxResolvers :patches:test --no-daemon` — passed.
- 12.29.0-alpha.04: `Applied`, `Saved to`; `getEnumDefault` at `postdetail/n-><init>`,
  `postdetail/l->invokeSuspend`, `ui/common/l2->invoke`, and `remember` at
  `payments/transaction/list/i0->invoke`.
- 12.28.0-prod.01 / 12.27.0-prod.01: `Applied`; two `getEnumDefault` hooks and one `remember`
  hook each; prefetch fingerprint matched zero.
- `dexscope verify-method` on all hooked 12.29 methods — no `INVALID`.
