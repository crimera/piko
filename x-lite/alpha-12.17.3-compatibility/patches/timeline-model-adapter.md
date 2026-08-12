# Timeline Model Adapter

## Why it broke

`XLiteTimelineFilter` and `PostFilterMatcher` compiled against readable `compileOnly` timeline stubs. Java casts, `instanceof`, fields, and signatures still embedded those unobfuscated descriptors in the extension DEX. Alpha renamed the concrete models, causing `NoClassDefFoundError` when the extension loaded `UrtTimelineModuleItem`.

## Fix

Added an internal patch dependency that:

- identifies post, module, module-item, and RTB ad models from stable data-class `toString()` labels;
- resolves relevant fields and constructors at patch time;
- replaces `Object`-typed placeholder bridges with direct `instance-of`, `check-cast`, field reads/getters, and constructors for the target release;
- patches the keyword matcher text bridge with the resolved post `getText()` call.

The Java runtime boundary now uses `Object`, `List`, strings, primitives, and standard collections instead of timeline stubs.

## Compatibility strategy

Release descriptors exist only in injected bytecode. They are not extension method signatures. All four filtering patches depend on this adapter.

## Verification

Initial patch bundle build passed. Full final-DEX and runtime checks on alpha, 12.15.1, and 12.14.0 remain pending.
