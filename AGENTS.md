# Repository Agent Rules

## Search safety

- Scope every content search to the smallest known file or package directory.
- Never run repository-wide `ffgrep`/grep for X-Lite or APK analysis. Broad indexed searches can hang or crash the coding-agent process.
- Prefer exact-file `read` once a likely file is known.
- Prefer exact-file `rg` for follow-up symbol checks.
- Search one narrow package directory only when the target file is unknown; inspect the best result before searching again.
- Exclude generated, decompiled, build, and vendor trees unless one is the explicit analysis target.
- Do not launch multiple broad searches in parallel.

## X-Lite ports

- Load `.agents/skills/xlite-alpha-porting/SKILL.md` before porting or repairing an X-Lite patch.

## Patch performance

- Load `.agents/skills/morphe-patch-performance/SKILL.md` before optimizing or reviewing fingerprint and patch execution performance.
