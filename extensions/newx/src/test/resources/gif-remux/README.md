# MP4 extraction fixtures

Synthetic black 16×16 H.264 Main GIF-class MP4s: nine frames, B-frames, no audio,
`iso5` brand and a `Twitter-vork muxer` handler name. No downloaded/user imagery.
Base64 keeps the tiny binary fixtures editable by the repository's patch tooling.

`DownloadRemuxerTest` sends both through the production Media3 extraction/bridge entry point.
It checks CSD/sample representation, keyframes, reordered presentation timestamps, and identical
sample hashes across front/end `moov` layouts. Truncation, cancellation and sink errors must escape.
The Android `MediaMuxer` implementation and Gallery acceptance require device validation.

Generation (FFmpeg, libx264):

```sh
ffmpeg -f lavfi -i 'color=c=black:s=16x16:r=25' -frames:v 9 \
  -c:v libx264 -profile:v main -bf 2 -g 30 -an -brand iso5 \
  -metadata:s:v handler_name='Twitter-vork muxer' bframes-moov-last.mp4
ffmpeg -i bframes-moov-last.mp4 -c copy -movflags +faststart -brand iso5 \
  -metadata:s:v handler_name='Twitter-vork muxer' bframes-moov-first.mp4
```

For a local incident corpus, set `PIKO_GIF_EXTRA_SAMPLES` to path-separator-delimited MP4 paths
when running `:extensions:newx:testDebugUnitTest` (ensure the test task is rerun). The same
integration test parses these and prints sample counts/track diagnostics into its XML report.
The synthetic fixtures always run; incident media need not be committed.
