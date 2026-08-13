# Hide AI-Generated Posts

## Why it broke

The original patch assumed stable descriptors and getter names for `UrtTimelinePost`, `ContentDisclosure`, and its source enum. The alpha obfuscates the complete model path:

```text
timeline post → post result/contextual post → canonical post → content disclosure
```

The alpha timeline post does not expose a direct public method returning the disclosure model. The disclosure boolean/source getters are also absent or renamed.

## Fix

- Keep every extension helper signature typed as `Object`.
- Resolve the timeline post through its stable `UrtTimelinePost(postResult=...)` data-class text.
- Resolve contextual post, canonical post, and content disclosure through stable `toString()` labels.
- Resolve the field chain structurally:
  - timeline post's post-result field;
  - contextual post's unique canonical-post field;
  - canonical post's unique content-disclosure field.
- Resolve the disclosure source as its unique non-static object field.
- Resolve `hasAIGeneratedDisclosure` as the second ordered boolean read in the disclosure `toString()` contract (`paid promotion`, `AI generated`, `can edit`).
- Make only the resolved fields public so the extension bridge can use direct `iget` instructions.
- Inject direct casts/field reads into `Object`-typed helpers. No alpha descriptor is embedded in extension Java.
- Keep the source behind `Object`; runtime compares `Enum.name()` with `UserMarked` and `AutoDetected`.
- Expose a third `SourceNotIdentified` setting value for disclosures whose AI source is null; the extension maps that sentinel to `source == null` without naming the alpha enum.

## Verifier correction

The first bridge used `instance-of v0, p0` in a one-register static helper. In that method, `v0` and `p0` alias, so `instance-of` replaced the object with a boolean and the following `check-cast` failed verification.

The final bridge uses the already-proven timeline/contextual model chain directly and needs no temporary register. Unexpected model shapes are contained by the existing per-item runtime exception guard.

## Verification

- Patch bundle builds.
- Exclusive alpha patch run reports `Applied: X-Lite: Hide AI-generated posts` and saves the APK.
- Initial runtime test exposed the aliased-register `VerifyError`; corrected bridge removes it.
- Diagnostic runtime logs on 12.17.3-alpha.01 confirmed the bridge reaches `com.x.models.a1`, reads `hasAi=true`, and sees both `UserMarked` and null source values.
- The `AI source not identified` option is implemented for null-source disclosures; final runtime verification on 12.17.3-alpha.01 is pending.
- Regression patching/runtime tests on 12.15.1 and 12.14.0 remain pending.
