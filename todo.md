# TODO

- [ ] Use dexscope to analyze the latest available 12.37 APK for better NewX runtime
  hook points. Current local newest candidate is
  `apks/com.twitter.android_12.37.2-prod.01-312372001_1arch_2dpi_24lang_40077bd4311e4f25683ffac3caf0472b_apkmirror.com.apkm`;
  confirm the exact base/splits, package, version, hashes, and supported target before
  analysis. Read the android-apk-analysis and Morphe workflow skills first.
  - Trace feature-switch accessors and their timeline/Compose callers: determine
    whether observation/override work can run at a less frequent semantic boundary.
  - Trace Compose emoji paragraph processing, both merge/bypass paths, and whether
    the same text is processed repeatedly. Find a boundary preserving spans/font changes.
  - Trace inline action creation, native entry mutation/equality, Compose keys,
    icon capture, and click routing before proposing action or output-list reuse.
    Resolve typed media capability checks to replace composition-time `toString`
    parsing, with exact target smali proof and old-target validation.
  - Trace timeline success-state construction and module filtering frequency;
    determine whether the same immutable items are repeatedly filtered on UI updates.
  - For each proposal record current/proposed callsite, control flow, cardinality,
    stable semantic anchors, invalidation/ownership constraints, and old/new compatibility.
    Use focused Luna reconnaissance tasks. Do not change hooks without this evidence.
- [ ] Measure the implemented runtime optimizations under normal timeline scrolling
  and pagination, with custom emoji, feature switches, inline downloads, and filters
  both enabled and disabled. Record allocations and frame timing separately from
  source-level call reductions. Ask the user to exercise the app; no device control.
