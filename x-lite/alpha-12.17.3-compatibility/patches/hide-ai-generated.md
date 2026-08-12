# Hide AI-Generated Posts

## Why it broke

The patch assumed stable descriptors and getter names for `UrtTimelinePost`, `ContentDisclosure`, and its source enum. In the alpha, timeline post and content disclosure are obfuscated, and readable getters are absent.

## Work completed

- Changed extension bridge input to `Object`.
- Added the timeline model adapter dependency.
- Fingerprint `ContentDisclosure` through stable data-class `toString()` labels.
- Resolve its AI boolean and source fields from those labels.
- Keep the source behind `Object` and compare `Enum.name()` to `UserMarked` / `AutoDetected`.
- Prepare direct field-read bridge injection without embedding model descriptors in Java.

## Current blocker

The alpha timeline post (`w0`) delegates semantic post properties through its obfuscated post-result field/interface. No direct public method returning the resolved disclosure descriptor was found on the timeline post. The patch run currently fails fast while resolving that post-to-disclosure accessor.

The next step is to resolve the post-result field from the timeline post, then identify/inject the disclosure accessor on that resolved owner or interface.

## Verification

Not complete. Do not mark this patch alpha-compatible until the alpha patch run, final DEX inspection, and runtime filtering test pass.
