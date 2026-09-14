# Open canonical URLs recon: Twitter/X 12.27.0-alpha.01

## Outcome

The reported failure is real and is caused by compiler/R8 lowering, not by a
change to the `UrlEntity` data contract.

The URL entity moved from `Lcom/x/models/text/m1;` in the control APK to
`Lcom/x/models/text/l1;` in the target APK. Both classes still have the same
five semantic properties and the same field layout:

| Semantic property | 12.26 field | 12.27 field | Type |
| --- | --- | --- | --- |
| `displayUrl` | `m1.a` | `l1.a` | `String` |
| `expandedUrl` | `m1.b` | `l1.b` | `String` |
| `url` | `m1.c` | `l1.c` | `String` |
| `startIdx` | `m1.d` | `l1.d` | `int` |
| `endIdx` | `m1.e` | `l1.e` | `int` |

In 12.26, the ordinary constructor contains three
`Intrinsics.checkNotNullParameter` calls and the strings `displayUrl`,
`expandedUrl`, and `url`. In 12.27, those checks were folded into one call to
`Lads_mobile_sdk/v4;->z(String,String,String)V`; that helper only invokes
`Object.getClass()` on the three arguments. The names are therefore absent
from the ordinary constructor even though its signature, assignments, and
field meanings are unchanged.

There is also a second, independent 12.27 incompatibility which the current
failure masks: the old `(String url, String expanded) -> String` URL-picker
helper was inlined into two arms of the timeline-URL dispatcher. After
`resolveUrlEntityFields` is repaired, the current `urlPicker` fingerprint will
find zero methods in 12.27.

The remaining Canonical URLs model fingerprints and mutation paths retain
their semantic shapes in the target APK. `MentionEntity` and the card `Url`
model experienced the same null-check lowering in their constructors, but
this patch only identifies those classes from their `toString()` contracts;
it does not try to recover their fields from named constructor parameters.

Recommended repair:

1. Resolve the three URL fields from the retained Kotlin serialization schema
   and its element-index-to-field encoding flow. This is one semantic resolver
   for both releases and does not use constructor parameter names, obfuscated
   owners, or release routing.
2. Resolve the URL-picker capability as exactly one of two validated shapes:
   the extracted helper (12.26) or one dispatcher containing the two validated
   inlined selection sites (12.27). Share the same setting-controlled
   selection mutation after shape resolution.
3. Replace local `.singleOrNull()`/`lastOrNull()` cardinality decisions with
   the shared `requireExactlyOne`. In particular, derive the contextual-post
   register in card navigation from the matched `getId` data flow rather than
   from the last preceding `check-cast`.

## Frozen inputs

Analysis was performed from source commit
`d6efb26d263000ed9960069dd6fdc93348f12d90`. The worktree was already dirty;
this recon adds only this artifact.

| Role | Path | Manifest identity | Size | SHA-256 |
| --- | --- | --- | ---: | --- |
| Control | `apks/twitter_12.26.0-alpha.03.apk` | `com.twitter.android`, versionCode `312260203`, versionName `12.26.0-alpha.03`, minSdk 28, targetSdk 36 | 123,674,627 bytes | `4a5bc95c8b6a9cd1ceb6e159333943b11e094ecf168153f14decb14d697ccf5e` |
| Target | `apks/twitter_12.27.0-alpha.01.apk` | `com.twitter.android`, versionCode `312270201`, versionName `12.27.0-alpha.01`, minSdk 32, targetSdk 36 | 116,310,120 bytes | `82d0fe729854d631bb8069b3cab25f47d64361722cb99c483cc7e8e2b178d67d` |

The encompassing 12.27 sweep incident is recorded in
`docs/newx-resolver-linter/incidents/2026-09-13-12-27-sweep.md`.

## URL entity identity, fields, and methods

The existing `UrlEntityModelFingerprint` remains good. The string
`UrlEntity(displayUrl=` has exactly one method anchor under
`Lcom/x/models/text/` in each APK:

```text
12.26  Lcom/x/models/text/m1;->toString()Ljava/lang/String;
12.27  Lcom/x/models/text/l1;->toString()Ljava/lang/String;
```

### Class shape

12.26 class declaration:

```text
public final Lcom/x/models/text/m1;
implements d0, k, q0

static final Companion:Lcom/x/models/text/l1;
final a:Ljava/lang/String;
final b:Ljava/lang/String;
final c:Ljava/lang/String;
final d:I
final e:I

<clinit>()V
<init>(IIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V  [synthetic]
<init>(ILjava/lang/String;ILjava/lang/String;Ljava/lang/String;)V
a()I
b()I
equals(Ljava/lang/Object;)Z
hashCode()I
toString()Ljava/lang/String;
```

12.27 class declaration:

```text
public final Lcom/x/models/text/l1;
implements d0, k, p0

static final Companion:Lcom/x/models/text/k1;
final a:Ljava/lang/String;
final b:Ljava/lang/String;
final c:Ljava/lang/String;
final d:I
final e:I

<clinit>()V
<init>(IIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V  [synthetic]
<init>(ILjava/lang/String;ILjava/lang/String;Ljava/lang/String;)V
a()I
b()I
equals(Ljava/lang/Object;)Z
hashCode()I
toString()Ljava/lang/String;
```

The changed obfuscated class/interface/Companion descriptors are not usable
anchors. The exact public field types, constructor shapes, serialization
contract, and formatted property labels are the surviving invariants.

`a()I` returns field `d` and `b()I` returns field `e` in both versions. There
are no String getters on this model, so the integer getter names provide no
URL-field mapping.

### Ordinary constructor: 12.26

```smali
Lcom/x/models/text/m1;-><init>(ILjava/lang/String;ILjava/lang/String;Ljava/lang/String;)V

const-string v0, "displayUrl"
invoke-static p2, v0, Intrinsics->checkNotNullParameter(Object,String)V
const-string v0, "expandedUrl"
invoke-static p4, v0, Intrinsics->checkNotNullParameter(Object,String)V
const-string v0, "url"
invoke-static p5, v0, Intrinsics->checkNotNullParameter(Object,String)V

invoke-direct p0, Object-><init>()V
iput-object p2, p0, m1->a:String
iput-object p4, p0, m1->b:String
iput-object p5, p0, m1->c:String
iput p1, p0, m1->d:I
iput p3, p0, m1->e:I
```

This is the shape on which `hasNamedParameter` and
`fieldWrittenFromNamedParameter` currently depend.

### Ordinary constructor: 12.27

```smali
Lcom/x/models/text/l1;-><init>(ILjava/lang/String;ILjava/lang/String;Ljava/lang/String;)V

invoke-static p2, p4, p5,
    Lads_mobile_sdk/v4;->z(String,String,String)V
invoke-direct p0, Object-><init>()V
iput-object p2, p0, l1->a:String
iput-object p4, p0, l1->b:String
iput-object p5, p0, l1->c:String
iput p1, p0, l1->d:I
iput p3, p0, l1->e:I
```

The called helper is:

```smali
Lads_mobile_sdk/v4;->z(String,String,String)V

invoke-virtual p0, Object->getClass()Class
invoke-virtual p1, Object->getClass()Class
invoke-virtual p2, Object->getClass()Class
return-void
```

The assignments prove that the constructor contract survived. Following this
obfuscated helper would only prove non-nullness, however; it cannot recover
the three semantic names.

### Synthetic serialization constructor

The synthetic constructor has identical parameter types and direct String
assignments in both releases:

```smali
<init>(IIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V

# after the 0x1f serialization mask check
iput-object p4, p0, UrlEntity->a:String
iput-object p5, p0, UrlEntity->b:String
iput-object p6, p0, UrlEntity->c:String
iput p2, p0, UrlEntity->d:I
iput p3, p0, UrlEntity->e:I
```

This independently confirms the stable field layout, but raw parameter order
alone should not be the new semantic anchor. The serialization encoder below
provides an explicit property index for each field.

### `toString()` mapping

The relevant bytecode is structurally identical in both versions:

```smali
const-string v0, ", expandedUrl="
const-string v1, ", url="
const-string v2, "UrlEntity(displayUrl="
iget-object v3, p0, UrlEntity->a:String
iget-object v4, p0, UrlEntity->b:String
invoke-static {v2,v3,v0,v4,v1}, (...)StringBuilder
...
iget v3, p0, UrlEntity->d:I
iget-object v4, p0, UrlEntity->c:String
invoke-static {v3,v4,", startIdx=",", endIdx=",builder}, (...)V
...
iget ..., p0, UrlEntity->e:I
```

The decompiled format is:

```text
UrlEntity(displayUrl=a, expandedUrl=b, url=c, startIdx=d, endIdx=e)
```

Thus `a`, `b`, and `c` map to `displayUrl`, `expandedUrl`, and `url`. The
helper owners and names (`Lg;`, `ads_mobile_sdk/sh;`, `q7;`, `tg;`) churned
and must not become anchors.

### Serialization schema and encoder mapping

The generated serializer moved from `Lcom/x/models/text/k1;` to
`Lcom/x/models/text/j1;`. Its static initializer retains the wire-model name
and all property names in the same element order:

```smali
const-string ..., "com.x.models.text.UrlEntity"
const/4 ..., 5
invoke-direct ..., PluginGeneratedSerialDescriptor-like <init>

const-string ..., "displayUrl"   # element 0
invoke-virtual ..., (String,Z)V
const-string ..., "expandedUrl"  # element 1
invoke-virtual ..., (String,Z)V
const-string ..., "url"          # element 2
invoke-virtual ..., (String,Z)V
const-string ..., "startIdx"     # element 3
invoke-virtual ..., (String,Z)V
const-string ..., "endIdx"       # element 4
invoke-virtual ..., (String,Z)V
```

The public `serialize(Encoder,Object)V` bridge explicitly binds the first
three descriptor indices to the URL entity fields in both APKs:

```smali
const/4 index, 0
iget-object value, entity, UrlEntity->a:String
invoke-interface encoder, descriptor, index, value, (...;IString)V

const/4 index, 1
iget-object value, entity, UrlEntity->b:String
invoke-interface encoder, descriptor, index, value, (...;IString)V

const/4 index, 2
iget-object value, entity, UrlEntity->c:String
invoke-interface encoder, descriptor, index, value, (...;IString)V
```

This is the strongest available field resolver: semantic property names are
retained by the serialization schema, while the encoding flow supplies an
explicit index-to-field binding. It is independent of the ordinary
constructor's choice of null-check lowering.

## Other model fingerprints

### Mention entity

`MentionEntityModelFingerprint` remains unique in both APKs:

```text
12.26  Lcom/x/models/text/y;->toString()String
12.27  Lcom/x/models/text/y;->toString()String
```

The class still has fields `a:Lcom/x/models/zg;`, `b:I`, `c:I`, and
`d:String`, and methods consisting of the two constructors, `a()I`, `b()I`,
`equals`, `hashCode`, and `toString`. Its normal constructor demonstrates the
same compiler change:

```smali
12.26: const-string "screenName"
       invoke-static p4, label, Intrinsics->checkNotNullParameter(...)

12.27: invoke-virtual p4, Object->getClass()Class
```

Assignments are unchanged. Canonical URLs only consumes the resolved mention
type for an `instance-of` anchor, so this lowering does not break the patch.

### Card URL action

`CardUrlActionModelFingerprint` remains unique under `Lcom/x/cards/api/` and
resolves `Lcom/x/cards/api/n;` in both APKs. The class still has:

```text
a:String       # url
b:Integer      # slideIndex
c:boolean      # scribeAsClick

<init>(String,Integer,boolean)V
<init>(String,int,Integer)V [synthetic]
a()boolean
b()Integer
c()String
equals/hashCode/toString
```

The ordinary constructor changed from a named `url` Intrinsics check in 12.26
to `p1.getClass()` in 12.27, but the patch does not inspect that constructor.
The only no-argument String getter remains `c()String`, and it returns field
`a` in both APKs. `toString()` still formats `Url(url=a, slideIndex=b,
scribeAsClick=c)`.

## Downstream Canonical URLs audit

The table reports semantic candidate cardinality after applying the recovered
URL field mapping. Obfuscated descriptors are evidence only, not recommended
anchors.

| Resolver/path | 12.26 | 12.27 | Result |
| --- | ---: | ---: | --- |
| URL entity model | 1 (`m1.toString`) | 1 (`l1.toString`) | Survives |
| Mention entity model | 1 (`y.toString`) | 1 (`y.toString`) | Survives |
| Text-entity navigation | 1 (`navigation/hb.e`) | 1 (`navigation/kb.e`) | Survives |
| Extracted URL picker `(String,String)->String` in navigation owner | 1 (`hb.g`) | 0 | **Broken by inlining** |
| Inlined URL-picker dispatcher shape | 0 | 1 method with 2 sites (`kb.d`) | New capability shape |
| Post link click handler | 1 (`urt/items/post/z4.y`) | 1 (`urt/items/post/z4.z`) | Survives |
| Profile link values | 1 (`imageloader/lifecycle/b.b`) | 1 (`imageloader/lifecycle/b.c`) | Survives |
| Rich-text URL display | 1 (`ui/common/text/g.g`) | 1 (`ui/common/text/g.g`) | Survives |
| Card URL action model | 1 (`cards/api/n`) | 1 (`cards/api/n`) | Survives |
| Card navigation callback | 1 method (`compose/animation/core/a.invoke`) | 1 method (same obfuscated descriptor) | Survives; register resolver should be tightened |

### Text-entity navigation

Control:

```text
Lcom/x/navigation/hb;->e(Lcom/x/navigation/ij;Lcom/x/models/text/g1;)V
```

Relevant flow:

```smali
instance-of ..., entity, MentionEntity
...
instance-of ..., entity, Lcom/x/models/text/m1;
check-cast entity, Lcom/x/models/text/m1;
iget-object selected, entity, m1->b:String       # expandedUrl, offset 0x35
invoke-static selected, Uri->parse(String)Uri
invoke-virtual uri, Uri->getAuthority()String
...
iget-object selected, entity, m1->c:String       # url, offset 0x53
```

Target:

```text
Lcom/x/navigation/kb;->e(Lcom/x/navigation/lj;Lcom/x/models/text/f1;)V
```

The same sequence occurs at offsets `0x2a` through `0x51`, with `l1.b` at
`0x31` and `l1.c` at `0x4f`. The only material prologue change is two
`Object.getClass()` null checks. The existing ordered filters and
`TEXT_ENTITY_URL_FIELD_FILTER_INDEX = 5` still identify the `url` read which
is replaced with `expandedUrl`.

### URL picker: extracted in 12.26, inlined in 12.27

The control method is:

```text
Lcom/x/navigation/hb;->g(String url,String expanded)String
```

Its behavior is:

```smali
if-eqz expanded, return_url
normalized = navigation/hb.c(expanded)
authority = Uri.parse(normalized).getAuthority() ?: ""
if-eqz allowedAuthority(authority), return_url
return-object normalized
return_url:
return-object url
```

It has two callers, both arms of
`navigation/hb.d(navigator,timelineUrl,Long,options,int)V`. The current patch
injects at the helper entry and, when enabled and `expanded != null`, returns
the raw expanded argument before normalization/authority filtering.

In 12.27, `Lcom/x/navigation/kb;` has no `(String,String)->String` method at
all. Its method list contains `a(String,ue,boolean)String`,
`b(String,ue)String`, `c(String)String`, `d(lj,bg,Long,ue,int)V`,
`e(lj,text/f1)V`, and unrelated one-argument helpers. The picker logic is
duplicated inside the two `rf`/`uf` arms of `kb.d`:

```smali
# site 1, instructions 14..32 (offsets 0x1a..0x3c)
iget-object base, model, rf->b:String
iget-object expanded, model, rf->c:String
invoke-virtual base, Object->getClass()Class
if-eqz expanded, :continue
normalized = kb.c(expanded)
authority = Uri.parse(normalized).getAuthority() ?: ""
if-eqz allowedAuthority(authority), :continue
move-object base, normalized
:continue
invoke-static base, options, true, kb.a(String,ue,Z)String

# site 2, instructions 39..57 (offsets 0x4a..0x6c)
iget-object base, model, uf->b:String
iget-object expanded, model, uf->c:String
# same null/normalize/parse/authority/conditional-select flow
invoke-static base, options, true, kb.a(String,ue,Z)String
```

Therefore `resolveCanonicalUrlMatches` is not otherwise fully compatible with
12.27: `textEntityNavigation` resolves, but its subsequent `urlPicker`
fingerprint will fail with cardinality zero.

### Post link click

Control candidate:

```text
Lcom/x/urt/items/post/z4;->y(
  Lcom/x/urt/items/post/f4;,
  Lcom/x/models/o1;,                # contextual post
  Lcom/x/scribing/post/a;,
  Lcom/x/urt/items/post/e5;,
  Lkotlinx/coroutines/i0;,
  Lcom/x/payments/qrcode/e;,
  Lcom/x/cards/api/p;
)V
```

Target candidate:

```text
Lcom/x/urt/items/post/z4;->z(
  Lcom/x/urt/items/post/g4;,
  Lcom/x/models/p1;,                # contextual post
  Lcom/x/scribing/post/a;,
  Lcom/x/urt/items/post/d5;,
  Lkotlinx/coroutines/g0;,
  Lcom/x/payments/screens/externalcontactlist/create/steps/z4;,
  Lcom/x/cards/api/p;
)V
```

The URL branch remains semantically aligned:

```text
12.26 expandedUrl read m1.b @ 0x22b; Uri.parse; getAuthority;
      url read m1.c @ 0x243; getId; long id read; URT navigation call
12.27 expandedUrl read l1.b @ 0x204; Uri.parse; getAuthority;
      url read l1.c @ 0x21c; getId; long id read; URT navigation call
```

The method names, parameter owners, helper owners, and register counts churned,
but the current package/signature/field/framework/downstream-call fingerprint
still selects one method. `POST_URL_FIELD_FILTER_INDEX = 3` still denotes the
later `url` read, so replacing it with `expandedUrl` remains correct.

### Profile link values

The semantic pair is unique in the image-loader scope in both APKs:

```text
12.26 Lcom/x/media/imageloader/lifecycle/b;->b(Object)Object
      m1.a displayUrl @ 0x43d
      m1.c url        @ 0x441

12.27 Lcom/x/media/imageloader/lifecycle/b;->c(Object)Object
      l1.a displayUrl @ 0x42e
      l1.c url        @ 0x432
```

Both fields are consumed while constructing the profile header link value.
The patch's high-index-first mutation remains necessary and correct.

### Rich-text URL display

Control has the URL entity branch in:

```text
Lcom/x/ui/common/text/g;->g(...,Composer,I)Landroidx/compose/ui/text/g;
instance-of entity, m1 @ 0x1c4
move receiver, entity
check-cast receiver, m1
iget-object display, receiver, m1.a @ 0x1cb
```

Target has the same data flow in the same stable package, with a churned
Compose return type:

```text
Lcom/x/ui/common/text/g;->g(...,Composer,I)Landroidx/compose/ui/text/i;
instance-of entity, l1 @ 0x1b5
move receiver, entity
check-cast receiver, l1
iget-object display, receiver, l1.a @ 0x1bc
```

`hasUrlEntityDisplayFlow` deliberately ignores the Compose return type and
method name, follows the move/cast receiver, and proves the selected
`instance-of` branch. It remains true for exactly one method in 12.27.

### Card navigation

Both APKs contain two calls to the card action's only no-argument String
getter inside one callback method. The first call supplies the direct card
navigation URL and is the intended insertion point:

```text
12.26 Landroidx/compose/animation/core/a;->invoke(Object)Object
      card n.c()String @ 0x389
      move-result-object url
      navigation/hb.b(String,options)String
      contextualPost field -> getId -> long id
      card n.c()String again
      canonical lookup helper
      urt/items/post/z4.C(String,J,L,String)L...

12.27 same callback descriptor
      card n.c()String @ 0x354
      move-result-object url
      navigation/kb.b(String,options)String
      contextualPost field -> getId -> long id
      card n.c()String again
      canonical lookup helper
      urt/items/post/z4.D(String,J,L,String)L...
```

The current fingerprint still resolves one callback method in each APK and
the first matched getter is followed by `move-result-object` in both.

The register resolver is nevertheless weaker than the surrounding
fingerprint. `resolveContextualPostRegister` currently collects every
preceding `check-cast` to the contextual-post type and returns
`registers.lastOrNull()`. In 12.26 there are already two such casts before the
first card URL getter: index 301 writes `v4`, while index 321 writes the
actually consumed `v13`. Selection by “last” succeeds only because of current
instruction order. In 12.27 the consumed cast is index 301 to `v13`; another
cast exists later at index 642. This has not broken 12.27, but it is an
unasserted R8-sensitive choice and violates the repository's cardinality
methodology.

## Recommended resolver design

### 1. Resolve URL fields from serialization element bindings

Keep `UrlEntityModelFingerprint` as the model identity anchor. Add one
serializer fingerprint scoped to `Lcom/x/models/text/` whose static
initializer contains, in semantic order:

```text
com.x.models.text.UrlEntity
displayUrl
expandedUrl
url
startIdx
endIdx
```

Do not hardcode `k1`, `j1`, serializer implementation classes, or their
obfuscated method names. On the serializer owner, resolve exactly one public
`serialize(Encoder,Object)V` bridge which:

- casts the value to the URL entity type;
- reads String fields owned by that URL entity;
- passes each read to a String-element encoder call carrying an integer
  element index.

Trace the encoder invocation's index and value registers to their defining
`const` and `iget-object` instructions. Build `SerializerElementBinding`
candidates and use the shared helper independently for indices 0, 1, and 2:

```kotlin
val displayUrl = requireExactlyOne(
    "URL entity serializer displayUrl element",
    bindings.filter { it.index == 0 },
).field
val expandedUrl = requireExactlyOne(
    "URL entity serializer expandedUrl element",
    bindings.filter { it.index == 1 },
).field
val url = requireExactlyOne(
    "URL entity serializer url element",
    bindings.filter { it.index == 2 },
).field
```

Validate that all three fields:

- are non-static `String` fields defined by the matched URL entity;
- are distinct;
- are exactly the three URL-entity String fields read by its matched
  `toString()` method.

This yields `m1.a/m1.b/m1.c` in 12.26 and `l1.a/l1.b/l1.c` in 12.27 from the
same code path. Unexpected serializer inlining, duplicate bindings, missing
schema elements, additional String fields, or a changed model contract fails
closed with candidate descriptions.

A smaller resolver based on the three ordered owner-String reads in
`toString()` would also work for both supplied APKs because the formatted
labels make that order contractual. The serializer-index resolver is
preferred because it supplies explicit numeric property bindings and does not
depend on R8's chosen StringBuilder helper grouping.

Delete `hasNamedParameter`, `findNamedParameterRegister`, and
`fieldWrittenFromNamedParameter`; following `getClass()` helpers would merely
recreate a brittle compiler-shape dependency.

### 2. Adapt the URL-picker capability, not the release number

Resolve two possible semantic shapes within the navigation owner discovered
from text-entity navigation:

- **Extracted shape:** exactly one `(String,String)->String` method with an
  optional-expanded null gate, normalization, `Uri.parse`,
  `Uri.getAuthority`, an authority-policy call, and selection between the two
  input values.
- **Inlined shape:** exactly one dispatcher method containing exactly the two
  validated model arms described above. Each site must prove two String reads
  from the same cast receiver, a non-null base, nullable expanded gate,
  normalization, `Uri.parse/getAuthority`, conditional replacement of the
  base register, and downstream consumption of the selected register.

Represent a fully validated helper or a fully validated dispatcher-plus-site
pair as one `UrlPickerShape` candidate, then require exactly one shape across
all candidates:

```kotlin
val shape = requireExactlyOne(
    "URL picker capability shape",
    extractedShapeCandidates + inlinedShapeCandidates,
)
```

Expected cardinality is:

```text
12.26: extracted=1, inlined=0, combined=1
12.27: extracted=0, inlined=1 (two validated sites), combined=1
```

Use no `if (version == ...)` branch. Apply a common setting-controlled
selection mutation after resolving the shape. To preserve the old target's
existing patch behavior exactly, the 12.27 inlined-site mutation should, when
the setting is enabled and the expanded value is non-null, copy the raw
expanded register into the base/selected register and jump to the site's
post-selection continuation. When disabled, execute the original
normalize/authority flow unchanged. Mutate the later site first so earlier
indices remain valid.

All branch targets, source registers, selected registers, null gates, and
continuations must be derived from the matched instructions. Do not hardcode
the observed `p1`, `p2`, instruction numbers, `rf`/`uf`, `hb`/`kb`, or helper
names.

### 3. Tighten cardinality and card register provenance

Replace the file-local `Fingerprint.requireSingleMatch` implementation with a
thin call to the shared
`app.crimera.patches.newx.utils.requireExactlyOne`. A `Match` description
lambda using `originalMethod.toString()` is justified if `Match.toString()`
does not identify its method.

For card navigation, use the already matched `getId` instruction and direct
data flow:

1. Read the `getId` invoke receiver register.
2. In the interval after the first card URL result and before that invoke,
   find field reads whose output defines that receiver and whose field is an
   instance field of the resolved contextual-post type.
3. `requireExactlyOne("card navigation contextual-post source", candidates)`.
4. Return that field read's receiver register as the post register.

This resolves `v13` in both supplied APKs without relying on the last
`check-cast`, a one-character owner, or raw instruction position.

## Anchors to retain and discard

Retain:

- `UrlEntity(displayUrl=`, `, expandedUrl=`, and `, url=` as formatted model
  identity/evidence;
- the serialization model name and property labels;
- serializer element indices paired to actual encoder field values;
- exact field owner/type relationships;
- `Uri.parse(String)Uri` and `Uri.getAuthority()String` public framework calls;
- stable method signatures, invoke/result adjacency, cast/field data flow,
  null gates, and downstream consumption;
- stable package scopes only where needed for cardinality;
- shared `requireExactlyOne` checks with semantic labels.

Discard:

- ordinary-constructor parameter-name strings and Intrinsics calls;
- `Object.getClass()` null-check lowering as an identity anchor;
- one-character class, method, interface, and field names;
- serializer implementation owner names;
- raw instruction positions and “last candidate” selection;
- release/version routing.

## Validation required with the implementation

This recon did not modify or build the patch. The implementing change should:

1. Add a focused regression fixture to the existing
   `NewXResolverLinterTest.kt` corpus for the real named-parameter-to-
   `getClass()` resolver failure; do not create a new test file.
2. Run `./gradlew :patches:test` and
   `./gradlew :patches:lintNewxResolvers`.
3. Build the real MPP and patch both exact frozen APKs exclusively. Record the
   output paths and confirm `NewX: Open canonical URLs` reports `Applied` and
   the patcher reports `Saved to`.
4. Verify the extracted helper remains the selected shape on 12.26 and the
   two-site inlined dispatcher is selected on 12.27.
5. Inspect emitted bytecode for setting-read branches, invoke/result pairing,
   branch reachability, scratch-register safety, and high-to-low mutation.
6. Runtime-test both setting states and both positive and control paths on old
   and new builds. Device interaction must be performed by the user unless
   explicit device-control permission is given.

## Dexscope commands used

Representative commands (all used explicit APK paths):

```bash
dexscope anchors --apk CONTROL --apk TARGET --string 'UrlEntity(displayUrl='
dexscope inspect-class APK 'Lcom/x/models/text/m1;' --fields
dexscope inspect-class APK 'Lcom/x/models/text/l1;' --methods
dexscope inspect-method APK 'URL_ENTITY-><init>(IStringIStringString)V'
dexscope inspect-method APK 'URL_ENTITY->toString()String'
dexscope inspect-method APK 'SERIALIZER-><clinit>()V'
dexscope inspect-method APK 'SERIALIZER->serialize(Encoder,Object)V'
dexscope xref APK 'URL_ENTITY->a:String' --direction in --kind field
dexscope xref APK 'URL_ENTITY->b:String' --direction in --kind field
dexscope xref APK 'URL_ENTITY->c:String' --direction in --kind field
dexscope inspect-method APK 'NAVIGATION_OWNER->e(...)V'
dexscope inspect-method APK 'NAVIGATION_OWNER->d(...)V'
dexscope xref APK 'CARD_URL_ACTION->c()String' --direction in --kind method
dexscope inspect-method APK 'Landroidx/compose/animation/core/a;->invoke(Object)Object'
```

Descriptors in these examples were filled from the preceding anchor/xref
results for each APK; none is proposed as production identity.
