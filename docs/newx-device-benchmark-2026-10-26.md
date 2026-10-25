# NewX device scrolling baseline — 2026-10-26

Current Piko was modestly higher in HWUI jank than official X in this session: median
2.69% versus 2.11%, with median run P95 frame time 20.5 ms versus 19.5 ms. This
is a baseline for the actual app configurations, not an isolated measurement of the
four runtime optimizations. The live feed, native UI, custom font/theme, ads, cache
state, and installation/profile history differ. Per-run ranges overlap, and the
official app itself became slower in its second block. Do not attribute the gap
to a particular extension method or treat the old-version result as a before/after.

## Primary HWUI results

Each entry is the **median of per-run reported metrics**, not a pooled percentile.
HWUI uses its own deadline-based jank classification. No fixed 60 Hz threshold was applied.
HWUI percentiles are reported histogram bins; a median across six runs can fall
between bins. The exact official jank median is 2.105%, displayed rounded to 2.11%.

| App | Runs | HWUI janky frames | Run range | P50 frame time | P95 frame time | P99 frame time |
|---|---:|---:|---:|---:|---:|---:|
| Installed Piko 12.33.0 | 3 | 2.51% | 2.47–2.71% | 11.0 ms | 19.0 ms | 31.0 ms |
| Official X 12.37.2 | 6 | 2.11% | 1.96–2.92% | 12.0 ms | 19.5 ms | 30.0 ms |
| Current Piko 12.37.2 | 6 | 2.69% | 2.41–2.84% | 11.0 ms | 20.5 ms | 33.0 ms |

Pooled counts are a different aggregation and are provided separately:

| App | Rendered frames | Janky frames | Pooled jank rate |
|---|---:|---:|---:|
| Installed Piko 12.33.0 | 5,110 | 131 | 2.56% |
| Official X 12.37.2 | 10,240 | 233 | 2.28% |
| Current Piko 12.37.2 | 10,194 | 271 | 2.66% |

## Perfetto cross-check

MainActivity surface-frame events beginning inside each measured window are selected.
Only complete events (`dur >= 0`) are used. These counts and duration definitions
differ from HWUI; their rates/percentiles must not be mixed. Surface-frame duration
is app frame work including GPU completion, **not gesture latency, presentation
latency, or an FPS measurement**. App-deadline misses are selected by the actual
`jank_type` value rather than inferred from a fixed duration threshold.

| App | Median app-deadline-miss rate | Frame-work P50 | Frame-work P95 | Frame-work P99 | Buffer-stuffing events |
|---|---:|---:|---:|---:|---:|
| Installed Piko 12.33.0 | 3.04% | 10.57 ms | 16.05 ms | 28.66 ms | 40.69% |
| Official X 12.37.2 | 2.48% | 11.94 ms | 17.41 ms | 27.72 ms | 44.62% |
| Current Piko 12.37.2 | 3.26% | 10.79 ms | 16.73 ms | 31.38 ms | 40.59% |

Buffer stuffing indicates queueing/high-latency state and is not interchangeable
with visible jank. See [Perfetto FrameTimeline definitions](https://android.googlesource.com/platform/external/perfetto/+/refs/heads/main/docs/data-sources/frametimeline.md).
Refresh-rate-dependent deadline interpretation is also documented in
[Android trace queries](https://developer.android.com/topic/performance/tracing/profiling-manager/querying-profiles).

## CPU and memory context

CPU is scheduler running time clipped to the measurement window, normalized to one
core: 100% means one core busy throughout the window; process totals may exceed 100%.
Only the exact `com.twitter.android` process is included, not named child processes.
PSS is a post-trial snapshot of proportional memory usage, not allocation rate or
a leak measurement. Feed/image/font caches and different base-app versions affect it.

| App | Main-thread CPU | RenderThread CPU | Main-process total CPU | Post-trial PSS | HWUI GPU P95 |
|---|---:|---:|---:|---:|---:|
| Installed Piko 12.33.0 | 30.0% | 46.7% | 99.6% | 372.9 MiB | 7.0 ms |
| Official X 12.37.2 | 28.4% | 54.8% | 108.4% | 534.9 MiB | 7.5 ms |
| Current Piko 12.37.2 | 30.2% | 46.8% | 103.5% | 552.6 MiB | 8.0 ms |

## Run-order variation

Order was old ×3, official A ×3, Piko A ×3, official B ×3, Piko B ×3.
This is repeated ABAB order, not a randomized/counterbalanced experiment.

| Block | Median HWUI jank | Median HWUI P95 | Median post-trial PSS |
|---|---:|---:|---:|
| old | 2.51% | 19.0 ms | 372.9 MiB |
| official_a | 2.03% | 19.0 ms | 527.6 MiB |
| current_a | 2.52% | 20.0 ms | 544.5 MiB |
| official_b | 2.54% | 21.0 ms | 542.1 MiB |
| current_b | 2.81% | 22.0 ms | 557.6 MiB |

The official block median shifted from 2.03% to 2.54% even without changing its
APK. This variation is comparable to the aggregate official/Piko difference.
Battery temperature increased from 32.5°C to 35.0°C across accepted trials; every
before/after thermal-service status was 0 (no reported thermal throttling at those
snapshots). CPU frequency, background load, network traffic, and content were not locked.

## Device and protocol

- Device: M2102J20SG, `vayu`, Android 16 / API 36; build `BP4A.251205.006 release-keys`.
- Resolution 1080×2400. Existing refresh settings remained peak 120 Hz / minimum 30 Hz.
  These are allowed limits, not a claim of a fixed 120 Hz during all frames.
- HWUI reported Skia/OpenGL. Device remained on external power; no display, font-scale,
  network, battery, or performance-mode setting was changed for the benchmark.
- Same account and For you timeline; the session persisted through all installs.
  Piko custom text font and inline download buttons were visually present. Feature
  toggles/logging preferences were not reset or exhaustively inventoried.
- Eight forward warm-up swipes after each install, then three measured trials.
  Each trial: three cycles of four forward and four backward swipes (24 total),
  x=540, y=1800→700 and reverse, duration 350 ms, 150 ms inter-swipe pause,
  with 800 ms final settling. The timeline remained foreground and PID stayed stable.
- Accepted trials: 15, 360 measured gestures, 236.98 seconds of measured windows,
  25,544 HWUI frames and 25,519 selected Perfetto surface-frame events.
- Launch/install/warm-up excluded. Launch timing output exists as provenance only;
  it is not a cold-start benchmark. The feed was not frozen/offline and could prefetch.
- HWUI counters reset before each capture. Whole-run native histograms provided
  its percentiles; the small trailing framestats ring was not used as the full run.
- Perfetto v58.2 (`add693d8b`) captured FrameTimeline, compact scheduler switches,
  CPU frequency, app gfx/view/dalvik annotations, and process stats. The same 128 MiB
  ring-buffer configuration was used for every accepted trial. CPU slices are clipped
  to `/proc/uptime` start/end; frame events are selected by start timestamp.
- All accepted traces had no nonzero `error`/`data_loss` health counters. There
  were no detected process restarts or foreground losses during accepted trials.
- Setup exclusions: Perfetto could not write to `/data/local/tmp`; output moved
  to `/data/misc/perfetto-traces`. Three old-version pilot captures filled a 32 MiB
  buffer; they are under `pilots/`, excluded, and replaced by three clean captures.
- Only launch/stop/install and vertical scroll gestures were performed. No post,
  like, follow, message, uninstall, or app-data-clear action was issued.
- Final state verified: current patched 12.37.2-prod.01 (312372001), single base APK,
  MainActivity foreground; peak/minimum refresh settings still 120/30. Original
  installed 12.33 APK was backed up before replacement.

## Build and APK provenance

- Current production source: `9e518d8b` (`Optimize repeated NewX runtime work on UI paths`).
  No production source changed during benchmarking. The pre-existing uncommitted
  test diffs are not packaged into the app.
- Ran `./gradlew :patches:build --no-daemon` successfully before patching.
- Ran `./patch-twitter.sh apks/com.twitter.android_12.37.2-prod.01-312372001_1arch_2dpi_24lang_40077bd4311e4f25683ffac3caf0472b_apkmirror.com.apkm`.
  All 46 selected patches Applied; output Saved to `~/Downloads/piko-twitter-patched.apk`.
- Actual MPP: `patches/build/libs/patches-3.11.0-dev.1.mpp`; patcher script used
  `../piko/morphe-desktop-1.19.0-all.jar` with its normal 2 GiB heap.
- Official arm: unmodified base plus all 27 config/ABI/language split APKs from
  that exact same 12.37.2 APKM. Current arm: the merged standalone patched output.
- Old arm: pulled as installed before any replacement, version 12.33.0-prod.01
  (312330001). Its Piko patch revision is unknown; only its APK identity is frozen.
- Installs used `adb install-multiple -r` / `adb install -r`, with successful
  replacement across signatures using the user-provided device capability. No
  uninstall or data reset was needed. Full paths and package outputs are preserved.
- Exact sizes and SHA-256 identities for old backup, current APK, official base,
  and MPP are in the local `artifacts.json` evidence file.

## Evidence and reproduction

Local raw evidence (not committed):
`build/reports/newx-device-benchmark/20261026-025608/`.

- `gfx-summary.csv/json`: all per-run values, medians, ranges, pooled counts, temperatures.
- `perfetto-summary.csv/json`: FrameTimeline and CPU results.
- Each trial directory: exact trace, measurement window/PID/focus metadata,
  gfxinfo/meminfo dumps, battery/thermal snapshots, analysis SQL and trace-health result.
- `trace-config.pbtxt`, `collect.py`, `install_variant.py`: exact capture and swap protocol.
- `summarize_gfx.py`, `analyze_perfetto.py`: offline reducers. Re-run these after adding
  a complete trial; they exclude nested pilots and gate valid data before aggregation.
- `build.log`, `patch.log`, APK artifacts, install/launch logs, initial/final device metadata.
  Phase screenshots are local only; their account/feed content is not in this report.

```sh
R=build/reports/newx-device-benchmark/20261026-025608
python3 "$R/summarize_gfx.py"
python3 "$R/analyze_perfetto.py"
```

Re-running collection controls the connected phone and requires explicit user
authorization. This session explicitly authorized installs and scrolling. The
offline reducers do not control the phone.

## What this baseline does not establish

It does not isolate the four optimizations, the cost of an individual feature,
method-call frequency, allocation rate, memory leaks, cold startup, sustained forward
pagination, or long-run battery behavior. Old Piko differs in base-app version and
unknown patch revision. Official/current share the base version but have intentionally
different UI, ads, fonts, themes, and runtime behavior. Fresh-install/JIT/cache state
and progressively warmer hardware are additional confounders.

The next typed-media change should be compared against this **current Piko** build
on the same base APK/settings, with a more tightly controlled feed and temperature.
Feature-off/on trials and a separate forward-pagination scenario remain follow-ups.
