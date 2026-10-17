# 12.35.0-prod.01 resolver drift and patch-time repair

- Target: `com.twitter.android`, `apks/12.35.0-prod.01.apk` (production).
- Control: `apks/12.34.0-prod.01.apk`.
- Source: `6fc09670` plus pre-existing changes to `gradle/libs.versions.toml`, `patch-twitter.sh`, `patches-list.json`, untracked `patch-twitter-cli.sh`, and the four resolver edits below.
- Bundle: `patches/build/libs/patches-3.10.0-dev.9.mpp`, rebuilt with `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon`.
- Command: `./patch-twitter.sh ./apks/12.35.0-prod.01.apk --continue-on-error` (no install); control command substitutes `12.34.0-prod.01.apk`.
- Output: `/Users/steven/Downloads/piko-twitter-patched.apk` (overwritten by the control run; **not** a validated 12.35 build).
- Classification: APK contract/Compose lowering drift in resolver logic, not a demonstrated linter or cardinality-helper regression.

## Repaired at patch time

- Drawer: 12.35 changes the auxiliary Compose row lambdas from `Function2`/`Function3` to a runtime-internal lambda class. Accept an object in those slots; retain the title, icon, click, selected flag, composer, and trailing flags. The old 12.34 path still matches.
- Navigation bar: the merged content lambda constructor changes its discriminator from `I` to `B`. The renderer caller is `Lcom/x/android/main/dr;->invoke(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;`; the matched renderer is `Lcom/x/main/v;->f(Lcom/x/icons/b;Ljava/lang/String;Lcom/x/main/api/landing/z;Landroidx/compose/runtime/Composer;I)V`. Accept both discriminator types with the same three captured operands.
- For You topic request: the `FOR_YOU` + exact query-constructor pair moved out of `Lcom/x/repositories/home/` into `Landroidx/compose/foundation/pager/c0;->b(...)Lcom/x/android/main/ql;`. Remove the obsolete owner scope; combined match cardinality remains one on 12.35 and 12.34.
- Notification bridge (dependency of inline downloads): 12.35's API constructor now takes `Lcom/x/models/gd;` for both model positions, while `Literal(text=...)` lives in subclass `Lcom/x/models/zc;`. Resolve Literal through its superclass and semantic `toString`, not equality with the second constructor parameter.

## Remaining failures found during the first repair pass (now resolved at patch time)

1. `NewX: Disable video player scrolling`: `Expected one NewX VerticalPager implementation, found 0`.
2. `NewX: Hide post dividers`: `Expected exactly one NewX post divider renderer, found 0: []`.
3. `NewX: Redirect downloads to chosen folder`: `Expected exactly one NewX video download method in Lcom/x/urt/items/post/f6;, found 0: []`. The 12.34 watermarked-video method `Lcom/x/urt/items/post/t5;->d(Lcom/x/urt/items/post/t5;Lcom/x/models/a5;Lkotlin/coroutines/jvm/internal/ContinuationImpl;)Ljava/lang/Object;` was static; 12.35 uses the instance method `Lcom/x/urt/items/post/f6;->j(Lcom/x/models/a5;Lkotlin/coroutines/jvm/internal/ContinuationImpl;)Ljava/lang/Object;`. The coroutine resolver assumes the old static shape.
4. `NewX: Theme`: `Expected exactly one NewX STANDARD Function0 cache allocation, found 0: []`.

The patcher prints `Saved to` with `--continue-on-error` even when required patches failed. The second pass removed that flag and completed successfully on **both** `12.35.0-prod.01` and `12.34.0-prod.01`; all eight repaired patch names reported `Applied` and the APK was saved. `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon` passed (including the existing divider fixtures). No device interaction or runtime test was performed.

Additional resolver repairs: the pager's nested-scroll slot now accepts the concrete pager implementation; the post-divider helper derives its boolean parameter register from its actual signature, accepts reordered parameters and both Compose lambda discriminator widths; the download resolver accepts either the static or instance coroutine shape; the theme factory derives its one-integral-argument constructor rather than assuming `(I)V`. Exact targets and diagnosis were provided by four read-only Claude investigations, then checked against both APKs during implementation.

Final build and patch run: `./gradlew :patches:build :patches:lintNewxResolvers --no-daemon` and `./patch-twitter.sh ./apks/12.35.0-prod.01.apk` (without `--continue-on-error`) passed. All eight repaired patches reported `Applied`; output `/Users/steven/Downloads/piko-twitter-patched.apk` is now the **12.35** artifact. A focused byte-discriminator/reordered-wrapper fixture in `NewXResolverLinterTest` passes. Device behavior and final-DEX reachability remain unverified.
