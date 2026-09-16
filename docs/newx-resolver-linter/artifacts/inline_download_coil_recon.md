# Inline download Coil recon: Twitter/X 12.27.0-alpha.01

## Outcome

The failure is a resolver-shape mismatch, not a missing Coil memory cache.
The provider fingerprint still resolves one provider in both APKs, but the
meaning of its return descriptor changed:

| APK | Provider method | `provider.returnType` | Role of return descriptor |
| --- | --- | --- | --- |
| 12.26.0-alpha.03 | `Lcoil3/x;->a(Landroid/content/Context;)Lcoil3/m;` | `Lcoil3/m;` | ImageLoader interface |
| 12.27.0-alpha.01 | `Lcoil3/w;->a(Landroid/content/Context;)Lcoil3/s;` | `Lcoil3/s;` | Concrete ImageLoader implementation |

In 12.26, `Lcoil3/s;` implements the returned `Lcoil3/m;` interface, so the
current memory-cache fingerprint finds `Lcoil3/s;->d()Lcoil3/memory/d;`. In
12.27, `Lcoil3/s;` is itself the returned concrete loader and has no
interfaces. The current predicate therefore rejects its own `d()` method
because it requires an interface equal to `provider.returnType`.

The memory-cache getter did not change descriptor. It is the same method in
both APKs:

```text
Lcoil3/s;->d()Lcoil3/memory/d;
```

The recommended repair is to resolve a non-static, no-argument method
returning a Coil memory-cache type on either the provider-return class itself
or a class implementing the provider-return interface, then use the shared
`requireExactlyOne` helper. This dynamically supports both contracts without
version routing, obfuscated one-character names, or silent fallback.

## Frozen inputs

Analysis used source commit
`d6efb26d263000ed9960069dd6fdc93348f12d90`. The worktree was already dirty;
this artifact is the only file added by this recon.

| Role | Path | Manifest identity | SHA-256 |
| --- | --- | --- | --- |
| Control | `apks/twitter_12.26.0-alpha.03.apk` | `com.twitter.android`, versionCode `312260203`, versionName `12.26.0-alpha.03` | `4a5bc95c8b6a9cd1ceb6e159333943b11e094ecf168153f14decb14d697ccf5e` |
| Target | `apks/twitter_12.27.0-alpha.01.apk` | `com.twitter.android`, versionCode `312270201`, versionName `12.27.0-alpha.01` | `82d0fe729854d631bb8069b3cab25f47d64361722cb99c483cc7e8e2b178d67d` |

The APKs report minSdk 28 and 32 respectively, and targetSdk 36. No device
was launched or controlled.

## Current resolver and reproduced cause

`CoilImageLoaderProviderFingerprint` searches `Lcoil3/` static methods with a
`Context` parameter, the two `AtomicReference` calls, and a Coil-scoped return
type. Dexscope cross-references show one provider owner in each APK:

```text
12.26:
  Lcoil3/x;->a(Landroid/content/Context;)Lcoil3/m;
  AtomicReference.get() appears at offsets 2, 18, 81;
  AtomicReference.compareAndSet(...) appears at offset 69.

12.27:
  Lcoil3/w;->a(Landroid/content/Context;)Lcoil3/s;
  AtomicReference.get() appears at offsets 2, 18, 79;
  AtomicReference.compareAndSet(...) appears at offset 69.
```

The failing fingerprint is equivalent to:

```kotlin
!AccessFlags.STATIC.isSet(method.accessFlags) &&
    classDef.interfaces.any { it.toString() == imageLoaderDescriptor } &&
    method.returnType.toString().startsWith("Lcoil3/memory/")
```

With `imageLoaderDescriptor == "Lcoil3/m;"`, the control implementation is
valid. With `imageLoaderDescriptor == "Lcoil3/s;"`, the target implementation
is not valid because `Lcoil3/s;` has no interfaces. The target class has the
getter, but the current owner test cannot recognize the provider-return class
as the owner.

## Bytecode evidence

### 12.26.0-alpha.03 control provider

```text
# class Lcoil3/x; (public abstract)
public static final a(Landroid/content/Context;)Lcoil3/m;
```

Relevant provider instructions:

```smali
[0000] sget-object v0, Lcoil3/x;->a:Ljava/util/concurrent/atomic/AtomicReference;
[0002] invoke-virtual v0, AtomicReference->get()Ljava/lang/Object;
[0006] instance-of v2, v1, Lcoil3/m;
[000b] check-cast v1, Lcoil3/m;
...
[002c] invoke-interface v1, p0, Lcoil3/w;->a(Landroid/content/Context;)Lcoil3/m;
...
[0038] invoke-interface v1, p0, Lcoil3/w;->a(Landroid/content/Context;)Lcoil3/m;
...
[0045] invoke-virtual v0, v2, v4, AtomicReference->compareAndSet(...)Z;
[0050] return-object v4
```

The implementation and interface declarations are:

```text
Lcoil3/m;  public interface abstract; no declared methods

Lcoil3/s;  public final; implements Lcoil3/m
  public final d()Lcoil3/memory/d;
```

The provider uses `Lcoil3/m;` as the public ImageLoader contract, while the
concrete object returned at runtime is `Lcoil3/s;`.

### 12.27.0-alpha.01 target provider

```text
# class Lcoil3/w; (public abstract)
public static final a(Landroid/content/Context;)Lcoil3/s;
```

The provider has the same AtomicReference initialization shape, but the
return contract is concrete:

```smali
[0000] sget-object v0, Lcoil3/w;->a:Ljava/util/concurrent/atomic/AtomicReference;
[0002] invoke-virtual v0, AtomicReference->get()Ljava/lang/Object;
[0006] instance-of v2, v1, Lcoil3/s;
[000b] check-cast v1, Lcoil3/s;
...
[002c] invoke-interface v1, p0, Lcoil3/v;->a(Landroid/content/Context;)Lcoil3/s;
...
[0038] invoke-interface v1, p0, Lcoil3/v;->a(Landroid/content/Context;)Lcoil3/s;
...
[0045] invoke-virtual v0, v2, v4, AtomicReference->compareAndSet(...)Z;
[0057] return-object v1
```

The class declarations show the contract change:

```text
Lcoil3/m;  public final; extends Landroid/graphics/drawable/Drawable
           (not the old ImageLoader interface)

Lcoil3/s;  public final; extends Ljava/lang/Object;
           no interfaces
  public final d()Lcoil3/memory/d;
```

Thus the old `Lcoil3/m;` interface was removed/reassigned, and the provider
now exposes the concrete `Lcoil3/s;` implementation directly. The identical
one-character descriptor `Lcoil3/s;` across releases is incidental and must
not be used as a resolver anchor.

### Getter bytecode in both releases

The getter owner and descriptor are identical:

```text
Lcoil3/s;->d()Lcoil3/memory/d;
```

12.26 has two registers and 6 instructions; 12.27 has one register and 6
instructions. The register allocation changed, but the method contract did
not:

```smali
# 12.26, equivalent shape
iget-object v0, p0, Lcoil3/s;->a:Lcoil3/p;
iget-object v0, v0, Lcoil3/p;->c:Lkotlin/d;
invoke-virtual v0, Lkotlin/d;->getValue()Ljava/lang/Object;
move-result-object v0
check-cast v0, Lcoil3/memory/d;
return-object v0

# 12.27
iget-object p0, p0, Lcoil3/s;->a:Lcoil3/p;
iget-object p0, p0, Lcoil3/p;->c:Lkotlin/d;
invoke-virtual p0, Lkotlin/d;->getValue()Ljava/lang/Object;
move-result-object p0
check-cast p0, Lcoil3/memory/d;
return-object p0
```

The `Lcoil3/memory/d;` class also keeps the same public lookup contract in
both releases:

```text
Lcoil3/memory/d;->b(Lcoil3/memory/a;)Lcoil3/memory/b;
```

Its fields remain a strong-cache object, an immutable-map-backed weak-cache
object, and a monitor object. The outer lookup first asks the strong cache,
then reads the weak-cache map backing (`...immutableMap/m;->c` cast to
`LinkedHashMap`) and constructs/returns `Lcoil3/memory/b;` when a live image
is found. The internal strong-cache method was renamed from `e(...)` in
12.26 to `d(...)` in 12.27, but the patch resolves the outer `d.b(key)` method
and does not hardcode that internal method.

## How 12.27 accesses the memory cache

The runtime path is:

```text
static provider w.a(Context)
    -> concrete ImageLoader s
        -> s.d()
            -> s.a : Lcoil3/p;
            -> p.c : Kotlin Lazy;
            -> Lazy.getValue();
            -> Lcoil3/memory/d;
        -> memory/d.b(memory/a key)
            -> Lcoil3/memory/b value
                -> value.a : Lcoil3/l image
```

Dexscope found the target getter used by Coil and the app through direct
`Lcoil3/s;->d()Lcoil3/memory/d;` invokes, including the loader request path,
memory trimming, and app lifecycle callers. This confirms that the patch's
existing generated call
`invoke-virtual {loader}, Lcoil3/s;->d()Lcoil3/memory/d;` remains the correct
operation after the resolver is fixed.

## Recommended resolver repair

Change the loader-owner predicate from “a class implementing the resolved
descriptor” to “the resolved descriptor itself or a class implementing it.”
The descriptor comes from the provider match, so this is capability/shape
selection rather than version routing:

```kotlin
private fun coilImageLoaderMemoryCacheFingerprint(imageLoaderDescriptor: String) = Fingerprint(
    definingClass = COIL_SCOPE,
    parameters = emptyList(),
    custom = { method, classDef ->
        val owner = classDef.type.toString()
        val isResolvedLoaderOwner =
            owner == imageLoaderDescriptor ||
                classDef.interfaces.any { it.toString() == imageLoaderDescriptor }

        !AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameterTypes.isEmpty() &&
            isResolvedLoaderOwner &&
            method.returnType.toString().startsWith(MEMORY_CACHE_SCOPE)
    },
)
```

For a more general implementation, the owner relation should use the
repository's class-graph utilities, if available, to recognize transitive
interface/superclass relationships. The two declared targets need the two
cases above: 12.26 matches the implementation's direct interface, and 12.27
matches the provider-return class itself. Do not add a release/version branch.

Use the repository cardinality helper at the call site:

```kotlin
import app.crimera.patches.newx.utils.requireExactlyOne

val loaderMatch = requireExactlyOne(
    "Coil image-loader memory-cache getter",
    coilImageLoaderMemoryCacheFingerprint(provider.returnType.toString())
        .scopedMatchAllOrNull()
        .orEmpty(),
)
```

The same migration should replace the patch-local `requireSingleCacheMatch`
and `requireSingleCacheValue` wrappers with `requireExactlyOne` where the
cardinality is required to be exactly one. That preserves the repository
standard failure message with the label, cardinality, and candidate
descriptions. The resolver must continue to fail closed for zero or multiple
getter candidates.

Expected resolved candidates after this change:

```text
12.26.0-alpha.03  exactly one
  Lcoil3/s;->d()Lcoil3/memory/d;

12.27.0-alpha.01  exactly one
  Lcoil3/s;->d()Lcoil3/memory/d;
```

The downstream shape checks (`memory/d` lookup, key/value fields, strong
cache key accessor, weak backing map, image type, and image-to-Bitmap
converter) should remain dynamic and cardinality-checked. They are useful
contract validation and should not be replaced by hardcoded owners or
one-character member names.

## Validation required for the implementation

After applying the resolver change:

1. Run `./gradlew :patches:lintNewxResolvers` and the focused NewX tests.
2. Build the real MPP and patch both frozen APKs.
3. Confirm the inline-download patch reports `Applied` and records `Saved to`.
4. Confirm the emitted bridge invokes the dynamically resolved provider,
   `Lcoil3/s;->d()Lcoil3/memory/d;`, and
   `Lcoil3/memory/d;->b(Lcoil3/memory/a;)Lcoil3/memory/b;`.
5. If a real APK failure occurs, record it under
   `docs/newx-resolver-linter/incidents/` with the exact artifact provenance.

No runtime/device validation was performed during this recon, consistent with
the repository device-safety instructions.

## Commands used

The bytecode evidence was obtained with the installed `dexscope` CLI, using
explicit APK paths. Relevant commands included:

```text
dexscope xref <apk> Ljava/util/concurrent/atomic/AtomicReference;->get()Ljava/lang/Object; \
  --direction in --kind method --package Lcoil3/
dexscope xref <apk> Ljava/util/concurrent/atomic/AtomicReference;->compareAndSet(...)Z \
  --direction in --kind method --package Lcoil3/
dexscope inspect-class <apk> <class> --methods
dexscope inspect-method <apk> <method> --limit 100
```

