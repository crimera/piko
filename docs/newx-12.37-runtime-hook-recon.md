# NewX 12.37 runtime hook reconnaissance

Date: 2026-10-26. Source: `9e518d8b` (`Optimize repeated NewX runtime work on UI paths`).
This is read-only analysis of original APK bytecode, not a patch implementation or
frame-time benchmark. The two regression-test file changes from the preceding work
were excluded from that commit and remain local.

## Frozen targets and tools

Primary container:
`apks/com.twitter.android_12.37.2-prod.01-312372001_1arch_2dpi_24lang_40077bd4311e4f25683ffac3caf0472b_apkmirror.com.apkm`.
Extracted its `base.apk` to
`build/reports/newx-hotpath-recon/apks/12.37.2-prod.01-base.apk` (134,173,408 bytes,
ZIP member CRC `7c217e6a`). `dexscope manifest --package` confirms
`com.twitter.android`, versionName `12.37.2-prod.01`, versionCode `312372001`,
minSdk 29, targetSdk 36. The base contains 14 DEX files.

Comparison container:
`apks/com.twitter.android_12.36.1-prod.01-312361001_1arch_2dpi_24lang_3ce7d24c67ba423985991ca627160cfa_apkmirror.com.apkm`.
Extracted base:
`build/reports/newx-hotpath-recon/apks/12.36.1-prod.01-base.apk` (130,416,150 bytes,
ZIP member CRC `c90ccbf6`). Manifest confirms `com.twitter.android`,
`12.36.1-prod.01`, versionCode `312361001`, minSdk 29, targetSdk 36.

The local `dexscope manifest` command did not accept the APKM directly: no root
`AndroidManifest.xml`. This was a container/tooling mismatch, not an app or patch
failure. All subsequent semantic evidence uses the extracted original base APKs.
No whole-app decompilation, installation, or device interaction was performed.

The main agent traced timeline filtering. Focused Luna agents traced feature
switches, emoji processing, and inline actions/media. Method/class names below
are exact-target evidence, not production fingerprint constants. Offsets are DEX
code units. Generated supporting reports and listings are under
`build/reports/newx-hotpath-recon/`; those generated artifacts are not committed.

## Timeline filtering

Keep the success constructor boundary for now. It provides coverage across
repository updates, post-detail transforms, video state, and derived UI states;
no equally complete less-frequent hook has been proven.

In 12.37.2 the semantic strings `Success(timelineType=` and `, timelineItems=`
identify `Lcom/x/urt/b2;`. Its unique five-parameter constructor is:

```text
<init>(Lcom/x/models/timelines/v;Lkotlinx/collections/immutable/b;Lcom/x/urt/a2;ZZ)V
```

The constructor stores `p2` to the timeline-items field at 0x000e. The copy helper
`b2.a(b2, immutable/b, a2, Z, I):b2` tests `mask & 2` at 0x0002 and, when set,
reloads the source object's **same list reference** at 0x0006. It nevertheless
calls the hooked constructor at 0x0027. Metadata-only state changes therefore can
repeat filtering. The 12.36.1 counterpart is `Lcom/x/urt/e2;`, refresh type
`Lcom/x/urt/d2;`, with the same shape. Exact recon constructor dry-runs each match
once. The strings and field relationships survive; the short owners change.

Concrete paths:

- `Lcom/x/urt/y;->l(Lcom/x/urt/s1;)V` creates a refresh-state copy at 0x0058
  using mask 0x1b, retaining items. Its state-flow compare-and-set retry at 0x0060
  can repeat construction.
- `Lcom/x/postdetail/n;->D(Lcom/x/urt/b2;)Lcom/x/urt/b2;` changes lists at
  0x0061/0x0130, then makes a metadata copy retaining the list at 0x0136 (mask 0x17).
- `Lcom/x/urt/g;->emit(Object,Continuation)Object` constructs success at
  0x04ef/0x0502 and makes a list-changing copy at 0x0225 inside a state-flow retry.
- The video Compose function `Lcom/x/video/tab/d7;->x(...)V` makes a list-changing
  copy at 0x020f, but changed checks on state/set at 0x0196/0x019a and remembered
  value checks at 0x019f–0x01ab gate that work. It is not evidence of per-frame calls.

Native xrefs show 10 constructor invoke instructions across 9 caller methods and
13 copy-helper invoke instructions across 9 caller methods. These are static counts.

An upstream candidate, `Lcom/x/urt/y;->F(List)List`, transforms sports context
after reading `x_sports_post_context_*` feature keys. It has only two native
callers (`g.emit` and `com/x/spaces/room/component/sheets/l.emit`). Hooking its
output would miss other constructor/copy paths; hooking its input could alter
native spacing calculations. It cannot replace the current boundary alone.

Do not memoize merely by item-list identity. `g.emit` wraps lists at
0x04df/0x04fd in `Lkotlinx/collections/immutable/adapters/a;`; that adapter's
constructor stores the caller's `List` at 0x0006 without copying, and `get(I)`
delegates to it. This does not prove actual mutation, but the interface name is
insufficient ownership proof. Filtering additionally depends on live rules,
whitelist, settings, and parent/author facts in `FilteredRepliesStore`, and has
recording side effects. A metadata-copy skip needs a complete context revision
and a defined recording policy before it is safe.

Reproduce with the primary base APK path as `$B`:

```sh
dexscope anchors "$B" --string 'Success(timelineType=' --limit 8
dexscope inspect-class "$B" 'Lcom/x/urt/b2;' --limit 35
dexscope inspect-method "$B" 'Lcom/x/urt/b2;->a(Lcom/x/urt/b2;Lkotlinx/collections/immutable/b;Lcom/x/urt/a2;ZI)Lcom/x/urt/b2;' --limit 50
dexscope xref "$B" 'Lcom/x/urt/b2;-><init>(Lcom/x/models/timelines/v;Lkotlinx/collections/immutable/b;Lcom/x/urt/a2;ZZ)V' --direction in --kind method --limit 50
dexscope xref "$B" 'Lcom/x/urt/b2;->a(Lcom/x/urt/b2;Lkotlinx/collections/immutable/b;Lcom/x/urt/a2;ZI)Lcom/x/urt/b2;' --direction in --kind method --limit 50
dexscope inspect-method "$B" 'Lcom/x/urt/y;->l(Lcom/x/urt/s1;)V' --limit 90
dexscope inspect-method "$B" 'Lkotlinx/collections/immutable/adapters/a;-><init>(Ljava/util/List;)V' --limit 20
```

## Feature switches

Keep the typed `get*` return hooks. The optimization committed in `9e518d8b`
removes repeated observation publication there without changing which reads are
observed or which typed overrides apply. Moving the hook to the common lookup or
to configuration-change notifications would change those contracts.

The current implementation is the preserved
`Lcom/x/featureswitches/FeatureSwitchesRepositoryImpl;`. Its seven `getBoolean`,
`getFloat`, `getInt`, `getLong`, `getDouble`, `getString`, and `getList` methods each
call the class-local private `getFeatureSwitchValue(String,Z)Object` at 0x0004
with mode `true`. Their return points follow native conversion and fallback:
Boolean checks/unboxes a Boolean, numeric getters accept `Number` and convert it,
String checks its type, and List checks its type or returns null. The current
patch operates after that native work and applies only the matching override type.

The same private lookup is called with mode `false` by all seven `peek*` methods
and by `getFeatureSwitches()`, which builds a new map by iterating configured keys.
The current patch intentionally leaves these paths unhooked. The lookup reads the
user's cached configuration and app-local override data; mode `true` also enables
impression handling. A raw Float override injected here could be coerced by
`getInt` through `Number.intValue()`, whereas the existing typed extension hook
rejects an override whose stored type is not INT. An earlier hook also cannot
observe the final caller-provided default for an absent or malformed raw value.

Native configuration-change events are coarser, but carry only an owner identifier:
`FeatureSwitchesConfigurationInMemoryCache.put` launches an emitter, the repository
filters by its current owner and maps the event to Unit. Using that event would
require a full snapshot/reconciliation and account/deletion invalidation. It would
mark configured but unread switches as observed while missing unconfigured keys
whose typed calls return defaults. Native-cache injection would additionally need
to track live extension override changes and native refresh replacement.

The accessors do have UI callers: interface xrefs include the Compose MainActivity
method at 0x0185/0x01a5 (`android_twemoji_iran_flag_emoji_enabled` and
`android_composer_fab_always_visible_enabled`) and a timeline helper reading
`post_ctas_fetch_enabled`. This establishes reachable UI work, not runtime frequency.

12.37.2 exact-owner dry-runs find one Boolean accessor and one private lookup;
the class has one implementation of each of the seven typed contracts. 12.36.1
retains these shapes; `dexscope diff` reports identical Boolean accessor bytecode.
The internal user-ID model changes from `Lcom/x/models/dh;` to
`Lcom/x/models/ch;`, supporting the existing strategy of resolving the linked
lookup rather than depending on its internal model owner.

Reproduce with `$B` as above and `$O` the extracted 12.36.1 base:

```sh
dexscope inspect-class "$B" 'Lcom/x/featureswitches/FeatureSwitchesRepositoryImpl;' --methods --limit 35
dexscope inspect-method "$B" 'Lcom/x/featureswitches/FeatureSwitchesRepositoryImpl;->getBoolean(Ljava/lang/String;Z)Z' --limit 30
dexscope inspect-method "$B" 'Lcom/x/featureswitches/FeatureSwitchesRepositoryImpl;->peekBoolean(Ljava/lang/String;Z)Z' --limit 30
dexscope xref "$B" 'Lcom/x/featureswitches/FeatureSwitchesRepositoryImpl;->getFeatureSwitchValue(Ljava/lang/String;Z)Ljava/lang/Object;' --direction in --kind method --limit 20
dexscope diff "$O" "$B" 'Lcom/x/featureswitches/FeatureSwitchesRepositoryImpl;->getBoolean(Ljava/lang/String;Z)Z'
```

## Compose emoji processing

Keep the paragraph-intrinsics constructor boundary. Its two current hook sites
are mutually exclusive; they do not cause double processing of a paragraph.
A common join permits a later maintenance simplification, but no call-count gain.

The exact 12.37.2 constructor is:

```text
Landroidx/compose/ui/text/b;-><init>(Ljava/lang/String;Landroidx/compose/ui/text/c1;Ljava/util/List;Ljava/util/List;Landroidx/compose/ui/text/font/i;Landroidx/compose/ui/unit/c;Z)V
```

It is the unique constructor selected by the Compose text package and
`Paint.setTypeface` evidence. The Emoji2 call at instruction 467 / 0x0398 has the
validated `(I,I,I,CharSequence)CharSequence` shape; its result is in `v9` at 468.
The first extension hook is inserted at 469, before the original 0x039c.
This path then jumps to the shared join at 472 / 0x03a1. The bypass path instead
assigns the original String to `v9` at 471 / 0x03a0, then falls through the second
hook before the join. Because current insertion leaves original branch targets
in place, the Emoji2 path skips that second hook.

One insertion at the join with branch relocation could cover both incoming paths,
subject to the typed bytecode layer's label/handler policy. It would still run
once per constructor execution. Moving earlier to Emoji2 alone misses the bypass;
moving to one higher UI caller misses other paragraph creation paths.

After the join, Compose adds annotation/style spans. The final CharSequence is
stored to `b.i` at instruction 1259 / 0x0994 and passed to the Android text
intrinsics constructor at 1263. This is the consumed value for measurement/drawing.
Xrefs find six constructor callsites in five methods, including String-node
measurement, annotated multi-paragraph construction, overflow/intrinsic helpers,
and background text measurement.

The String modifier path already caches paragraph intrinsics:
`Landroidx/compose/foundation/text/modifiers/g;->d(Landroidx/compose/ui/unit/m;)Landroidx/compose/ui/text/e0;`
reuses its `n` field when density identity is unchanged and `b.a()` reports that
the cached intrinsics are not stale. Density changes clear paragraph/layout caches;
font-resolver state can mark them stale. Thus a recomposition is not itself proof
of another paragraph construction. A new global text-result cache would also have
to preserve input span ownership, style/font state, and background-measurement safety.

The extension's emoji setting is captured at class initialization and marked
`rebootApp=true`; file addition prompts restart. The loaded Typeface is static.
The committed lazy builder is the appropriate bounded optimization at this boundary.

12.36.1 has the same constructor descriptor, instruction indices, and two-branch
join. The Emoji2 owner changes from `.../g;` to `.../h;`; the existing package and
signature predicate abstracts over it. One Emoji2 call matches in each inspected
constructor. No hook relocation was implemented during recon.

```sh
dexscope inspect-method "$B" 'Landroidx/compose/ui/text/b;-><init>(Ljava/lang/String;Landroidx/compose/ui/text/c1;Ljava/util/List;Ljava/util/List;Landroidx/compose/ui/text/font/i;Landroidx/compose/ui/unit/c;Z)V' --around-offset 0x380 --limit 35
dexscope xref "$B" 'Landroidx/compose/ui/text/b;-><init>(Ljava/lang/String;Landroidx/compose/ui/text/c1;Ljava/util/List;Ljava/util/List;Landroidx/compose/ui/text/font/i;Landroidx/compose/ui/unit/c;Z)V' --direction in --kind method --limit 30
dexscope inspect-method "$B" 'Landroidx/compose/foundation/text/modifiers/g;->d(Landroidx/compose/ui/unit/m;)Landroidx/compose/ui/text/e0;' --limit 100
dexscope inspect-method "$O" 'Landroidx/compose/ui/text/b;-><init>(Ljava/lang/String;Landroidx/compose/ui/text/c1;Ljava/util/List;Ljava/util/List;Landroidx/compose/ui/text/font/i;Landroidx/compose/ui/unit/c;Z)V' --around-offset 0x398 --limit 24
```

## Inline actions and media

Retain the current action-list hook. The native builder already memoizes action
lists in Compose; moving the extension into a generic per-icon render hook would
repeat more work and does not supply the list ownership/settings invalidation needed
for output-list caching. The useful next optimization is typed media eligibility
inside the existing hook body.

The 12.37.2 native entry is `Lcom/x/models/k4;`: enum action type, nullable Long
count, and enabled boolean, all final fields. Its constructor stores these fields;
`equals` compares all three values. No class-local setters were found. The injected
download entry carries no post reference, making a shared immutable entry a plausible
candidate. Final fields alone do not prove safety across all consumers: native share
entries can be value-equal, while extension icon classification and click interception
depend on object identity. Sharing requires a complete consumer/key/event audit;
do not replace weak identity classification or reuse a native share entry by equality.

The action list is produced in the post-presenter Compose method `e1.c(Composer)`;
the native list is converted to its immutable-list interface and stored in remembered
state keyed by the post model. The current patch filters after that conversion,
reconverts the extension's result, and then allows native storage. This boundary
already avoids rebuilding for unchanged memoization keys. New list caching outside
that scope would need current post state, hidden-action settings, download settings,
and native action state; list/presenter identity alone has no proven invalidation.

### Typed post-media discovery

`PostModels.kt` resolves CanonicalPost media using the `, media=` toString label.
The exact 12.37.2 field is `Lcom/x/models/r0;->d:Lkotlinx/collections/immutable/b;`.
Native consumer `Lcom/x/urt/items/post/options/v;->d(...)` iterates it and casts
each element to `Lcom/x/models/z4;`; another consumer `m3.z` casts its first element
to the same type. Its three concrete subclasses are:

| Semantic class | 12.37.2 owner | Relevant fields confirmed by toString |
|---|---|---|
| MediaContentImage | `Lcom/x/models/s4;` | `c:String` is imageUrl; `d:J` is originalImgHeight; `e:J` is originalImgWidth |
| MediaContentGif | `Lcom/x/models/p4;` | `d` is variants; `c:String` is previewUrl |
| MediaContentVideo | `Lcom/x/models/v4;` | `e` is variants; `d:s4` is previewImage |
| MediaVariant | `Lcom/x/models/media/x2;` | `a:String` is url; `b:Integer` is bitRate; `c:String` is contentType |

The toString anchors begin `MediaContentImage(mediaId=`,
`MediaContentGif(mediaId=`, and `MediaContentVideo(mediaId=`. The extension checks
shorter prefixes, so these tags are compatible with its current parser. A string
anchor lookup is exact; a zero result for just `MediaContentImage(` is not evidence
that the longer string or class is absent.

Discarded discovery: `com/x/models/media/MediaAttachment*` objects have URI fields
and media-kind values, but form a **different hierarchy**. Nearby package names,
image/video terminology, and unique toString strings do not establish that a generic
post-media List contains them. They must not be used to implement this predicate.
The actual post-media consumer casts above are the semantic linkage proof.

The existing parser tests image HTTP URL eligibility and video/GIF MP4 variants.
A typed replacement should retain those exact decisions, canonical-before-repost
selection, and false on unsupported media. A generic image/video type or HTTP URI
test is insufficient: it could accept video manifests without a downloadable MP4.
Resolve fields and variant item shape from the native contract at patch time, then
emit direct bridges accepting `Object` and returning booleans/strings/lists as
needed. Keep release-obfuscated references out of runtime Java and do not introduce
reflection. No typed bridge was implemented in this recon pass.

### Variant linkage, old target, and remaining limits

The Luna report proved video's variant element type through its generated
serialization path. Parent review then completed the GIF chain:

- `p4.<clinit>` at 0x000b/0x000d builds an `a4` lazy factory with discriminator
  10 and puts it into `p4.m[2]` at 0x0029. `n4.<clinit>` identifies itself as
  `com.x.models.MediaContent.MediaContentGif` and slot 2 as `variants`.
- `n4.childSerializers` at 0x0016–0x001d takes that same lazy slot 2.
- `a4.invoke` switch case 10 at 0x00b5–0x00bc creates
  `f4(Lcom/x/models/media/v2;)`. Video's case 14 at 0x0095–0x009c constructs
  the same list wrapper with the same `v2` serializer. `v2.deserialize`
  constructs `Lcom/x/models/media/x2;`.
- Independently, `p4.e()` returns `p4.d`. Native playback consumer
  `Lcom/google/android/play/core/appupdate/b;->V0(Lcom/x/models/media/n3;Lcom/x/media/playback/v;)Landroidx/media3/common/o0;`
  calls the shared variant interface at 0x0003 and check-casts iterated elements
  to `x2` at 0x001d before reading `contentType` at 0x0022. It prefers HLS for
  playback, demonstrating why that native playback result cannot replace the
  extension's MP4 eligibility rule.

Thus image, GIF, and video have a typed predicate implementation path on 12.37.2.
The native `isDownloadable` flags are not an equivalent substitute: current
extension eligibility is HTTP/MP4-based and must retain that policy.

On 12.36.1 the image, GIF, video, variant, and inline-entry toString anchors each
remain unique. The three media-content owners are again `s4`, `p4`, and `v4`, but
the variant is `Lcom/x/models/media/u2;`, and the action enum is `w5` rather than
12.37.2's `u5`. A direct recon query for the 12.37.2 GIF getter name/signature on
12.36.1 failed: do not assume that accessor name is stable even when the class
owner happens to be unchanged. A production resolver must derive the references
and assert types/cardinality, then build/patch old and new targets. Anchor checks
are not a claim that a new bridge has already been validated on older APKs.

The exact native icon-lambda body and explicit Compose item keys remain unproven
in this pass. They are blockers for **recommending object sharing**, not blockers
for the independent typed media predicate. Native source declarations, final
fields, or value equality alone cannot settle consumer identity behavior.

```sh
dexscope inspect-method "$B" 'Lcom/x/urt/items/post/options/v;->d(Lcom/x/models/r0;)Ljava/util/ArrayList;' --limit 60
dexscope search-classes "$B" --super 'Lcom/x/models/z4;' --limit 10
dexscope anchors "$B" --string 'MediaContentImage(mediaId=' --limit 5
dexscope anchors "$B" --string 'MediaContentGif(mediaId=' --limit 5
dexscope anchors "$B" --string 'MediaContentVideo(mediaId=' --limit 5
dexscope anchors "$B" --string 'MediaVariant(url=' --limit 5
dexscope inspect-method "$B" 'Lcom/x/models/p4;-><clinit>()V' --limit 45
dexscope inspect-method "$B" 'Lcom/x/models/n4;-><clinit>()V' --limit 40
dexscope inspect-method "$B" 'Lcom/x/models/n4;->childSerializers()[Lkotlinx/serialization/KSerializer;' --limit 30
dexscope inspect-method "$B" 'Lcom/x/models/a4;->invoke()Ljava/lang/Object;' --around-offset 0x95 --limit 30
dexscope inspect-method "$B" 'Lcom/google/android/play/core/appupdate/b;->V0(Lcom/x/models/media/n3;Lcom/x/media/playback/v;)Landroidx/media3/common/o0;' --limit 60
```

## Follow-up priorities

1. Implement the typed media predicate at the existing composition hook, with
   patch-time shape resolution and exact old/new APK patch validation. This removes
   whole-model string generation/parsing while preserving the current eligibility rule.
2. Measure state-copy filtering during scrolling/pagination. Optimize it only with
   a complete context revision and side-effect policy; do not cache by list identity.
3. Complete the native item-key/icon/event consumer proof before action sharing.
   Avoid a global output-list cache without a proven ownership/invalidation contract.
4. Keep feature-switch and emoji boundaries. A single emoji join insertion is an
   optional maintenance change, not a runtime optimization.

Recon changed only documentation. The earlier optimization commit excludes tests;
its two test-file diffs remain uncommitted. No additional production edits, builds,
APK patches, or device operations were made during this recon pass.
