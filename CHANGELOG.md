# [3.0.0-dev.2](https://github.com/crimera/piko/compare/v3.0.0-dev.1...v3.0.0-dev.2) (2026-02-17)


### Bug Fixes

* **Twitter:** Fix `Custom sharing domain` not overriding new share sheet ([fd446fa](https://github.com/crimera/piko/commit/fd446fad33d7ceaf98666cbefde1f03e2315c55f))

# [3.0.0-dev.1](https://github.com/crimera/piko/compare/v2.0.0...v3.0.0-dev.1) (2026-02-14)


### Features

* Change to Morphe patcher 1.1.1 ([31eb19e](https://github.com/crimera/piko/commit/31eb19e05f5dae9c4531db3c33dd8bc966b7b515))


### BREAKING CHANGES

* Old ReVanced CLI and Manager no longer work

## [2.0.0](https://github.com/crimera/piko/compare/v1.59.0...v2.0.0) (2026-02-11)

### ⚠ BREAKING CHANGES

* Various APIs have been changed or removed.

### Bug Fixes

* **Custom font:** Fix crash on Android 8 by restricting the functionality to Android 9+ ([477f634](https://github.com/crimera/piko/commit/477f6345a062bc3605dd343d290734074483a61c))
* **Custom font:** Fix crash on Android 8 by restricting the functionality to Android 9+ ([000539b](https://github.com/crimera/piko/commit/000539b8b67d97b83efdf4eab2d311e9b492f167))
* **Dynamic color:** Reflect piko's original changes ([0c91cad](https://github.com/crimera/piko/commit/0c91cad6d4112a02735edc5776a136e9f7c88271))
* **entity:** swap username and display name method mappings in TweetEntityPatch ([b946ebf](https://github.com/crimera/piko/commit/b946ebff8b59081ad3c53eb9ed108c3695dfe5fa))
* some patch has compatibleWith and dependsOn in execute block ([dd25a6c](https://github.com/crimera/piko/commit/dd25a6cd43354b8d6f4cb60d7798c539b06a6e50))
* **Twitter:** `Disunify xchat system` patch ([ad767b1](https://github.com/crimera/piko/commit/ad767b10afcc01c83450988816a60cf32d8fc195))
* **Twitter:** Added missing `notifications` item on hide sidebar elements ([39816e9](https://github.com/crimera/piko/commit/39816e91f85bad367494231e07c8ff1c0d9b58ce))
* **Twitter:** Correct Db clear keyword ([ca71ea7](https://github.com/crimera/piko/commit/ca71ea73a2957e1c64e1e2ed02b64311287bfaa0))
* **Twitter:** Corrected Moon app icon string ([60d0ede](https://github.com/crimera/piko/commit/60d0eded80fd32dbc7f1a1c229ed0c86da31efd5))
* **Twitter:** Fix `Custom download folder` in/from 11.51.xx ([c885395](https://github.com/crimera/piko/commit/c885395fce086f1f6791722cc615b671a6c69027))
* **Twitter:** Fix `Customize timeline top bar` fingerprint (again) ([189dfdd](https://github.com/crimera/piko/commit/189dfdd9e02de3869ed331d0258f4e83da309299))
* **Twitter:** Fix `Customize timeline top bar` logic ([642d092](https://github.com/crimera/piko/commit/642d0927a4819743a40d662db5c70cc3a4ff76d2))
* **Twitter:** Fix `Customize timeline top bar` patch ([2aa2a36](https://github.com/crimera/piko/commit/2aa2a3644956f588c46e9b3f08a94a2def9908d7))
* **Twitter:** Fix `Customize timeline top bar` patch - update tab ids ([c1d66c4](https://github.com/crimera/piko/commit/c1d66c42a56854f67633a07fb50d866c70ae096c))
* **Twitter:** fix `Enable force HD videos` patch ([23f2859](https://github.com/crimera/piko/commit/23f285974c8c12a117b81c794a64e94e6f33e531))
* **Twitter:** Fix `Extension hook` ([6fb52f7](https://github.com/crimera/piko/commit/6fb52f7307b596988ee8b4286717b378d3c2ca4d))
* **Twitter:** Fix `Native reader mode` deeplink ([24be415](https://github.com/crimera/piko/commit/24be4154d5a17a2c065dc8cff66b4087a165090a))
* **Twitter:** Fix `Native reader mode` patch in 10.28 and above ([800da14](https://github.com/crimera/piko/commit/800da149f4179044296d265ff4de9e7b2737e9b8))
* **Twitter:** Fix `Show post source label` patch ([42efbe2](https://github.com/crimera/piko/commit/42efbe23f17b44259fbc597f1ec72b0456bece15))
* **Twitter:** Fix font import bug. ([c8ea53c](https://github.com/crimera/piko/commit/c8ea53c8a50d3854911aec5dd2205661eba54ed5))
* **Twitter:** Fix font section header ([35e9b2b](https://github.com/crimera/piko/commit/35e9b2beb9efdb8d624455714c99cebbffed574e))
* **Twitter:** Fix font section header ([a7a7b2e](https://github.com/crimera/piko/commit/a7a7b2e719293102b45e202505eeb032f81d06c0))
* **Twitter:** Fix remove ads on replies ([8fda05e](https://github.com/crimera/piko/commit/8fda05ec94ffc7a48dfc1eb3a17bb14f6328ca6f))
* **Twitter:** Fix tweet short text extraction for `Custom translator` patch ([18becfb](https://github.com/crimera/piko/commit/18becfb31e68e05d571bd8651ba7ebfe7ecba3d1))
* **Twitter:** Fix unescaped strings ([41405a7](https://github.com/crimera/piko/commit/41405a75e50141e902aaac74f5fad9a2fcb06422))
* **Twitter:** Fixed host name issue due to `Custom sharing domain` patch ([3b26c84](https://github.com/crimera/piko/commit/3b26c84a902200f687cd327fb83281301e653dc1))
* **Twitter:** Fixed preference import ([4623b93](https://github.com/crimera/piko/commit/4623b93b68b18f42d0b8c435d4089f903ddfe4fd))
* **Twitter:** revert `semantic-release` version ([b50c530](https://github.com/crimera/piko/commit/b50c5301139ea1b972b6ccd507c929896219ff26))
* **Twitter:** Update `extensions` hook ([bf20765](https://github.com/crimera/piko/commit/bf2076592f49c11337acedf0d90e8a9d2d8d4ba4))
* **Universal:** Set version code as int ([ee83df7](https://github.com/crimera/piko/commit/ee83df7f8f18cf4cf2306b0a662a845ed565e106))
* wrong index number on multiple medias ([d088d03](https://github.com/crimera/piko/commit/d088d03a5dc743e1e50f403d14b71bc59b38571b))

### Features

* **Change version code:** Add compatibleWith to make it a non-universal patch ([c684cbc](https://github.com/crimera/piko/commit/c684cbc04017549ef247438764d6f10828912c7b))
* **Change version code:** Add compatibleWith to make it a non-universal patch ([3664dbf](https://github.com/crimera/piko/commit/3664dbf560b9dc61cc90de4e6f53f54d86f528b0))
* Migrate ci ([c737ca8](https://github.com/crimera/piko/commit/c737ca8edef074ff1beff17db7d27e5f26c17dc1))
* **Settings:** Allow preference titles to be multiple lines ([5581330](https://github.com/crimera/piko/commit/558133068396d532837c53347288fa42a7fb47f6))
* **Settings:** Allow preference titles to be multiple lines ([3075555](https://github.com/crimera/piko/commit/3075555611163ff67c073afd887d7fff4aeb45a7))
* **Twitter:** Added `Change app icon` patch ([efbd6f7](https://github.com/crimera/piko/commit/efbd6f708357d0256104e31748a035d92acee8f4))
* **Twitter:** Added `Customise font` patch ([e02d7db](https://github.com/crimera/piko/commit/e02d7dbd884ac4daf7c113d44575d085ba792989))
* **Twitter:** Added `Customize notification tabs` patch ([2e902e9](https://github.com/crimera/piko/commit/2e902e92b8cc56e757235cf47bbd6a52151b8c17))
* **Twitter:** Added `Disunify xchat system` patch ([1fa9b7b](https://github.com/crimera/piko/commit/1fa9b7bb949152f536e95d801ded61ead21caac1))
* **Twitter:** Added `Hide badges from navigation bar icons` patch ([1d98886](https://github.com/crimera/piko/commit/1d988863d06418e270d2821e366a5fdf1f3c0d47))
* **Twitter:** Added `Hide post metrics` patch ([81627c2](https://github.com/crimera/piko/commit/81627c2e75abb98a5b42da42187b7d9918a0b46b))
* **Twitter:** Added `Pause search suggestions` patch ([a5f3d8e](https://github.com/crimera/piko/commit/a5f3d8e87b19b61223b4959cdccb68630ceeea4e))
* **Twitter:** Added `Pause search suggestions` patch ([ddbdcfe](https://github.com/crimera/piko/commit/ddbdcfeda9418e83e4206101c9847e866e6a23a4))
* **Twitter:** Added `Remove search suggestions` patch ([33fa525](https://github.com/crimera/piko/commit/33fa5257b2d0ff0ce5423db6df733202e705aac0))
* **Twitter:** Added `Remove search suggestions` patch ([dd8e67b](https://github.com/crimera/piko/commit/dd8e67b3a634036e308a2a99844c7c966f69890b))
* **Twitter:** Added `Show changelogs` patch ([4b3adf7](https://github.com/crimera/piko/commit/4b3adf7a5e8966bc800d41b947349fb36345d5f4))

### Updates

* spanish translation to new strings ([28dbebd](https://github.com/crimera/piko/commit/28dbebd3508bbb8601ac3e706cb081aed69e224a))
* **Translation:** `Japanese` strings ([2f91899](https://github.com/crimera/piko/commit/2f91899580b5cf1e7eaec93ef780c27111a83865))
* **Translation:** `Japanese` strings ([054b2c5](https://github.com/crimera/piko/commit/054b2c5b93a43bc412e03adcb875117e513dd5bb))
* **Translation:** Added `pl` to Bring Back Twitter resource list ([44a5436](https://github.com/crimera/piko/commit/44a5436eabe9e9493984e9413ccbc74592eb8280))
* **Translation:** Added `pt-rBr` to `Bring Back Twitter` Patch ([9e33933](https://github.com/crimera/piko/commit/9e33933279bb80a40532a382726c9ddff53d43a2))
* **Translation:** Added `zh-rHK` ([58a2f4f](https://github.com/crimera/piko/commit/58a2f4fdb64d86a61ed27ad5bf632d260738688c))
* **Translation:** Fix `Vietnamese` strings ([3d1e213](https://github.com/crimera/piko/commit/3d1e213a6691216946ae703ea1ef239e56db584c))
* **Translations:** `Japanese` strings ([09b42d3](https://github.com/crimera/piko/commit/09b42d3fc0ce1199cdd2b4ec0904132ff2a5dc05))
* **Translations:** <Arabic> ([9ae0f3a](https://github.com/crimera/piko/commit/9ae0f3ad2f5a4d1ff7bdcb2f9d9c56ed909520d5))
* **Translations:** Update `Brazilian Portuguese` ([bbe2af9](https://github.com/crimera/piko/commit/bbe2af9a81b62d23885ecbbe93464b7abeabb73f))
* **Translations:** Update `Italian` strings ([837edf9](https://github.com/crimera/piko/commit/837edf922f1d6c2319e51a5a80907f13245fa8ca))
* **Translations:** Update `Korean` strings ([6bdf0cc](https://github.com/crimera/piko/commit/6bdf0ccb08e426b23d51abb9fe9f4907214030d3))
* **Translations:** Update `Polish` ([beba988](https://github.com/crimera/piko/commit/beba988141027bf05066f3b740ff8b7a72f43221))
* **Translations:** Update `Polish` ([6f5e2e9](https://github.com/crimera/piko/commit/6f5e2e9d384868aacba878e6565a3eeb14282154))
* **Translations:** Update `Polish` ([8302efe](https://github.com/crimera/piko/commit/8302efea73fa46b72e00eb0acd388af4a1e6376a))
* **Translations:** Update `Polish` ([f353663](https://github.com/crimera/piko/commit/f353663e2bcdffde840f0cb2097a69071206df23))
* **Translations:** Update `Polish` ([36011a6](https://github.com/crimera/piko/commit/36011a6677013e744f6227bdd1a7eb3550293f43))
* **Translations:** Update `Polish` ([2347537](https://github.com/crimera/piko/commit/23475370b2f9edaaf76f00d85b9fdbb3a4fcbffb))
* **Translations:** Update `Polish` ([15cf2b1](https://github.com/crimera/piko/commit/15cf2b1b1f61b87adde8cd27e59f83d0bba2f1d7))
* **Translations:** Update `Polish` ([1bcacaa](https://github.com/crimera/piko/commit/1bcacaa634e1ccd08ee7b3968c8761b789f2e5d6))
* **Translations:** Update `Polish` ([0983e57](https://github.com/crimera/piko/commit/0983e5723e0b6ea1a171aeefe774f64bb3993edf))
* **Translations:** Update `Polish` ([43da4b5](https://github.com/crimera/piko/commit/43da4b5ebc4b12d155d8f81495bbd6a09b1f44ad))
* **Translations:** Update `Polish` strings ([e32b911](https://github.com/crimera/piko/commit/e32b91113dc1b9dedea73cc606a6fd3bbf4f9a9d))
* **Translations:** Update Arabic ([a4f3df8](https://github.com/crimera/piko/commit/a4f3df857d4b82253bcae1cd268d5ec05d8d5b54))
* **Translations:** Update Korean ([f337585](https://github.com/crimera/piko/commit/f337585a3672f9b77f0da4fb76bb240cf84a8fdb))
* **Translations:** Update Korean ([a67efa4](https://github.com/crimera/piko/commit/a67efa42283b9ca42a43990cf3b0718384f8f527))
* **Translations:** Update Korean ([89820de](https://github.com/crimera/piko/commit/89820de1be72efe1af24826a5b57bf43f30dd37c))
* **Translations:** Update Korean ([23f1f25](https://github.com/crimera/piko/commit/23f1f25895787b168bce81ec6e6a66742b2b5ee1))
* **Translations:** Update Korean ([b1edad5](https://github.com/crimera/piko/commit/b1edad5c433e9d6f06010fe438e995ab02da6f2b))
* **Translations:** Updated `Arabic` strings [#735](https://github.com/crimera/piko/issues/735) ([61a05c6](https://github.com/crimera/piko/commit/61a05c64c3f40f906980c3e99b89a4c68b280184))
* **Translations:** Updated `Italian` ([071835c](https://github.com/crimera/piko/commit/071835c37ceae0f7e5b5e8e396e91327f9ae01ba))
* **Translations:** Updated `Japanese` strings ([cd5a888](https://github.com/crimera/piko/commit/cd5a8880c9265bf070f2f0793db9a11c741bf22c))
* **Translations:** Updated `Spanish` strings [#732](https://github.com/crimera/piko/issues/732) ([8137cd5](https://github.com/crimera/piko/commit/8137cd5da6ddf57d6088ddbdcb9640e77682b40f))
* **Translations:** Updated `Turkish` ([ae0df82](https://github.com/crimera/piko/commit/ae0df82a1601bd6588bd9f5db0f4071c83f428f2))
* **Translation:** Update `Japanese` ([6c22ae2](https://github.com/crimera/piko/commit/6c22ae255149a057680ad85f6a59ce829100bb65))
* **Translation:** Update `Vietnamese` ([9343e0e](https://github.com/crimera/piko/commit/9343e0e5c750566b4a86ff72de84050950c9551a))
* **Translation:** Update Traditional Chinese ([9f4d654](https://github.com/crimera/piko/commit/9f4d654f3bf91d7e68adf0b426c02feed32d3eed))
* **Translation:** Updated `Brazilian Portuguese` strings ([bb448a1](https://github.com/crimera/piko/commit/bb448a1367b692f267cd176e0d9daab547088667))
* **Translation:** Updated `Brazilian Portuguese` strings ([a53f3a0](https://github.com/crimera/piko/commit/a53f3a020eaf3b88570bda8dcb01c9968873c9e2))
* **Translation:** Updated `Japanese` strings ([373e275](https://github.com/crimera/piko/commit/373e275683c7c2ee83053751be851c9757f48ff8))
* **Translation:** Updated `Polish` strings ([4ba908b](https://github.com/crimera/piko/commit/4ba908b54e24f8fc78a884bdeb19aa59f0dff0c0))
* **Translation:** Updated `Polish` strings ([b6773c3](https://github.com/crimera/piko/commit/b6773c34549239a5580761faad5067f5a33c522c))
* **Twitter:** `Custom sharing domain` : added support for new share sheet ([a27675a](https://github.com/crimera/piko/commit/a27675a2b6324623e3ec09236fe6be2a51b2a22a))
* **Twitter:** Added more elements to hide from navigation bar ([8e6706c](https://github.com/crimera/piko/commit/8e6706c8578dbe86e97315da1da48b228071378d))
* **Twitter:** Include translations for app icon strings ([edad4a6](https://github.com/crimera/piko/commit/edad4a6438cf49a6226b02d94f4f41ccae31e683))
* **Twitter:** Set Splashscreen color to blue in `Bring back twitter` patch ([fbb9a09](https://github.com/crimera/piko/commit/fbb9a093dabe0634792f6b342489ed06d75f6bc6))
* **Twitter:** update `Custom sharing domain` patch in xchat share sheet ([4726ddb](https://github.com/crimera/piko/commit/4726ddb247468435ad22266cb148a4ea62ab3421))
* **Twitter:** Update `Customize timeline top bar` fingerprint ([38b706f](https://github.com/crimera/piko/commit/38b706fd3b866425284aa1e4a423f99c684f545c))
* **Twitter:** Updated missing patch names in `About section` ([fed6cee](https://github.com/crimera/piko/commit/fed6ceedd21501d06ad1e3a562135f5f65a335c2))
* **Twitter:** Updated side bar elements to hide ([45f93a1](https://github.com/crimera/piko/commit/45f93a1d6aeaddbf06011cea05abc8f79ba25b12))

### Refactors

* **Bring Back Twitter:** Added more numeric character  <-> emoji pairs ([5534c57](https://github.com/crimera/piko/commit/5534c5726d216148b07a5ad8dab29c88dd40fa7f))
* **Bring Back Twitter:** Refactor English strings ([18b68b3](https://github.com/crimera/piko/commit/18b68b3014ed69017207bcaced90a228f6791e90))
* **Dynamic color:** Do not get elements repeatedly in XML operation ([cc41d35](https://github.com/crimera/piko/commit/cc41d3525a6a1588f857a0b602e8a5208cec0fd3))
* **Dynamic color:** Do not get elements repeatedly in XML operation ([3417fe1](https://github.com/crimera/piko/commit/3417fe15d2d558b042a44a06e9c21a1523d12540))
* **Twitter:** Correct ja strings replacement in BBT patch ([ee200a5](https://github.com/crimera/piko/commit/ee200a5c5ae14e1a935903b5de9e7ecf7f03e0f9))
* **Twitter:** fix `Remove promoted trends` ([0e1d4ed](https://github.com/crimera/piko/commit/0e1d4ed2ac090451172f2bed985310061b987428))
* **Twitter:** handle piko resources programmatically ([3cc0f07](https://github.com/crimera/piko/commit/3cc0f0715c6ad62c14d6cd13e91f0965886e1534))
* **Twitter:** potential fix for resource compilation failing in manager ([fa0dfac](https://github.com/crimera/piko/commit/fa0dfac2b959ba9a958bb458cc4572f28ff94902))
* **Twitter:** refactor `Bring back twitter` patch ([f24cb72](https://github.com/crimera/piko/commit/f24cb72309f648493b209fffb1e7d19bd5c705f6))
* **Twitter:** refactor fingerprint for tweet short text ([062aeb3](https://github.com/crimera/piko/commit/062aeb3b4902185a041b73689bad56e8636b951b))
* **Twitter:** refactor fingerprint for tweet short text ([c00264a](https://github.com/crimera/piko/commit/c00264aebbe4717e61dd08b0998ff814f92ba3bd))
* **Twitter:** refactor native reader theme system. ([2ab5f35](https://github.com/crimera/piko/commit/2ab5f355103a3c65ddbfde63259da9eec90cc10b))
* **Twitter:** remove old comments ([1655283](https://github.com/crimera/piko/commit/165528374dadeab472a5f7d28bc60be12a09bbe0))

## [2.0.0-dev.23](https://github.com/crimera/piko/compare/v2.0.0-dev.22...v2.0.0-dev.23) (2026-01-28)

### Updates

* **Twitter:** update `Custom sharing domain` patch in xchat share sheet ([4726ddb](https://github.com/crimera/piko/commit/4726ddb247468435ad22266cb148a4ea62ab3421))

## [2.0.0-dev.22](https://github.com/crimera/piko/compare/v2.0.0-dev.21...v2.0.0-dev.22) (2026-01-27)

### Bug Fixes

* **Twitter:** Fix `Customize timeline top bar` patch - update tab ids ([c1d66c4](https://github.com/crimera/piko/commit/c1d66c42a56854f67633a07fb50d866c70ae096c))

## [2.0.0-dev.21](https://github.com/crimera/piko/compare/v2.0.0-dev.20...v2.0.0-dev.21) (2026-01-25)

### Bug Fixes

* **Twitter:** Fix `Customize timeline top bar` patch ([2aa2a36](https://github.com/crimera/piko/commit/2aa2a3644956f588c46e9b3f08a94a2def9908d7))
* **Twitter:** Fix tweet short text extraction for `Custom translator` patch ([18becfb](https://github.com/crimera/piko/commit/18becfb31e68e05d571bd8651ba7ebfe7ecba3d1))

### Updates

* **Translations:** Update `Brazilian Portuguese` ([bbe2af9](https://github.com/crimera/piko/commit/bbe2af9a81b62d23885ecbbe93464b7abeabb73f))
* **Translations:** Update `Polish` ([beba988](https://github.com/crimera/piko/commit/beba988141027bf05066f3b740ff8b7a72f43221))

## [2.0.0-dev.20](https://github.com/crimera/piko/compare/v2.0.0-dev.19...v2.0.0-dev.20) (2026-01-04)

### Bug Fixes

* **Twitter:** revert `semantic-release` version ([b50c530](https://github.com/crimera/piko/commit/b50c5301139ea1b972b6ccd507c929896219ff26))

### Updates

* **Translations:** Update `Polish` ([6f5e2e9](https://github.com/crimera/piko/commit/6f5e2e9d384868aacba878e6565a3eeb14282154))
* **Translations:** Update Korean ([f337585](https://github.com/crimera/piko/commit/f337585a3672f9b77f0da4fb76bb240cf84a8fdb))
* **Twitter:** `Custom sharing domain` : added support for new share sheet ([a27675a](https://github.com/crimera/piko/commit/a27675a2b6324623e3ec09236fe6be2a51b2a22a))

### Refactors

* **Twitter:** refactor native reader theme system. ([2ab5f35](https://github.com/crimera/piko/commit/2ab5f355103a3c65ddbfde63259da9eec90cc10b))

## [2.0.0-dev.19](https://github.com/crimera/piko/compare/v2.0.0-dev.18...v2.0.0-dev.19) (2025-12-27)

### Bug Fixes

* **Twitter:** Fix `Custom download folder` in/from 11.51.xx ([c885395](https://github.com/crimera/piko/commit/c885395fce086f1f6791722cc615b671a6c69027))

## [2.0.0-dev.18](https://github.com/crimera/piko/compare/v2.0.0-dev.17...v2.0.0-dev.18) (2025-12-15)

### Bug Fixes

* **Twitter:** `Disunify xchat system` patch ([ad767b1](https://github.com/crimera/piko/commit/ad767b10afcc01c83450988816a60cf32d8fc195))
* **Twitter:** Fix `Customize timeline top bar` logic ([642d092](https://github.com/crimera/piko/commit/642d0927a4819743a40d662db5c70cc3a4ff76d2))

### Updates

* **Translations:** Update `Polish` ([8302efe](https://github.com/crimera/piko/commit/8302efea73fa46b72e00eb0acd388af4a1e6376a))

## [2.0.0-dev.17](https://github.com/crimera/piko/compare/v2.0.0-dev.16...v2.0.0-dev.17) (2025-12-14)

### Bug Fixes

* **Twitter:** Fix `Customize timeline top bar` fingerprint (again) ([189dfdd](https://github.com/crimera/piko/commit/189dfdd9e02de3869ed331d0258f4e83da309299))

### Features

* **Twitter:** Added `Disunify xchat system` patch ([1fa9b7b](https://github.com/crimera/piko/commit/1fa9b7bb949152f536e95d801ded61ead21caac1))

## [2.0.0-dev.16](https://github.com/crimera/piko/compare/v2.0.0-dev.15...v2.0.0-dev.16) (2025-12-10)

### Bug Fixes

* **Twitter:** Fix `Native reader mode` deeplink ([24be415](https://github.com/crimera/piko/commit/24be4154d5a17a2c065dc8cff66b4087a165090a))

## [2.0.0-dev.15](https://github.com/crimera/piko/compare/v2.0.0-dev.14...v2.0.0-dev.15) (2025-12-10)

### Bug Fixes

* **Twitter:** Fix unescaped strings ([41405a7](https://github.com/crimera/piko/commit/41405a75e50141e902aaac74f5fad9a2fcb06422))

### Updates

* **Translations:** Update `Polish` ([f353663](https://github.com/crimera/piko/commit/f353663e2bcdffde840f0cb2097a69071206df23))
* **Translations:** Update Arabic ([a4f3df8](https://github.com/crimera/piko/commit/a4f3df857d4b82253bcae1cd268d5ec05d8d5b54))
* **Translations:** Update Korean ([a67efa4](https://github.com/crimera/piko/commit/a67efa42283b9ca42a43990cf3b0718384f8f527))
* **Twitter:** Update `Customize timeline top bar` fingerprint ([38b706f](https://github.com/crimera/piko/commit/38b706fd3b866425284aa1e4a423f99c684f545c))

## [2.0.0-dev.14](https://github.com/crimera/piko/compare/v2.0.0-dev.13...v2.0.0-dev.14) (2025-12-07)

### Features

* **Twitter:** Added `Hide post metrics` patch ([81627c2](https://github.com/crimera/piko/commit/81627c2e75abb98a5b42da42187b7d9918a0b46b))

### Updates

* **Translations:** `Japanese` strings ([09b42d3](https://github.com/crimera/piko/commit/09b42d3fc0ce1199cdd2b4ec0904132ff2a5dc05))
* **Translations:** Update `Polish` ([36011a6](https://github.com/crimera/piko/commit/36011a6677013e744f6227bdd1a7eb3550293f43))
* **Translations:** Update `Polish` strings ([e32b911](https://github.com/crimera/piko/commit/e32b91113dc1b9dedea73cc606a6fd3bbf4f9a9d))
* **Translations:** Update Korean ([89820de](https://github.com/crimera/piko/commit/89820de1be72efe1af24826a5b57bf43f30dd37c))
* **Translations:** Updated `Italian` ([071835c](https://github.com/crimera/piko/commit/071835c37ceae0f7e5b5e8e396e91327f9ae01ba))
* **Twitter:** Updated missing patch names in `About section` ([fed6cee](https://github.com/crimera/piko/commit/fed6ceedd21501d06ad1e3a562135f5f65a335c2))

## [2.0.0-dev.13](https://github.com/crimera/piko/compare/v2.0.0-dev.12...v2.0.0-dev.13) (2025-11-27)

### Features

* **Twitter:** Added `Hide badges from navigation bar icons` patch ([1d98886](https://github.com/crimera/piko/commit/1d988863d06418e270d2821e366a5fdf1f3c0d47))

### Updates

* **Translations:** Update Korean ([23f1f25](https://github.com/crimera/piko/commit/23f1f25895787b168bce81ec6e6a66742b2b5ee1))
* **Translations:** Updated `Turkish` ([ae0df82](https://github.com/crimera/piko/commit/ae0df82a1601bd6588bd9f5db0f4071c83f428f2))
* **Twitter:** Added more elements to hide from navigation bar ([8e6706c](https://github.com/crimera/piko/commit/8e6706c8578dbe86e97315da1da48b228071378d))

### Refactors

* **Bring Back Twitter:** Added more numeric character  <-> emoji pairs ([5534c57](https://github.com/crimera/piko/commit/5534c5726d216148b07a5ad8dab29c88dd40fa7f))
* **Bring Back Twitter:** Refactor English strings ([18b68b3](https://github.com/crimera/piko/commit/18b68b3014ed69017207bcaced90a228f6791e90))

## [2.0.0-dev.12](https://github.com/crimera/piko/compare/v2.0.0-dev.11...v2.0.0-dev.12) (2025-11-23)

### Bug Fixes

* **Twitter:** Fixed preference import ([4623b93](https://github.com/crimera/piko/commit/4623b93b68b18f42d0b8c435d4089f903ddfe4fd))

### Updates

* spanish translation to new strings ([28dbebd](https://github.com/crimera/piko/commit/28dbebd3508bbb8601ac3e706cb081aed69e224a))
* **Translations:** <Arabic> ([9ae0f3a](https://github.com/crimera/piko/commit/9ae0f3ad2f5a4d1ff7bdcb2f9d9c56ed909520d5))
* **Translations:** Updated `Arabic` strings [#735](https://github.com/crimera/piko/issues/735) ([61a05c6](https://github.com/crimera/piko/commit/61a05c64c3f40f906980c3e99b89a4c68b280184))
* **Translations:** Updated `Spanish` strings [#732](https://github.com/crimera/piko/issues/732) ([8137cd5](https://github.com/crimera/piko/commit/8137cd5da6ddf57d6088ddbdcb9640e77682b40f))
* **Twitter:** Updated side bar elements to hide ([45f93a1](https://github.com/crimera/piko/commit/45f93a1d6aeaddbf06011cea05abc8f79ba25b12))

## [2.0.0-dev.11](https://github.com/crimera/piko/compare/v2.0.0-dev.10...v2.0.0-dev.11) (2025-11-18)

### Bug Fixes

* **Twitter:** Corrected Moon app icon string ([60d0ede](https://github.com/crimera/piko/commit/60d0eded80fd32dbc7f1a1c229ed0c86da31efd5))
* **Twitter:** Fix font import bug. ([c8ea53c](https://github.com/crimera/piko/commit/c8ea53c8a50d3854911aec5dd2205661eba54ed5))

### Updates

* **Translations:** Update `Korean` strings ([6bdf0cc](https://github.com/crimera/piko/commit/6bdf0ccb08e426b23d51abb9fe9f4907214030d3))
* **Translations:** Update `Polish` ([2347537](https://github.com/crimera/piko/commit/23475370b2f9edaaf76f00d85b9fdbb3a4fcbffb))
* **Twitter:** Include translations for app icon strings ([edad4a6](https://github.com/crimera/piko/commit/edad4a6438cf49a6226b02d94f4f41ccae31e683))

## [2.0.0-dev.10](https://github.com/crimera/piko/compare/v2.0.0-dev.9...v2.0.0-dev.10) (2025-11-13)

### Bug Fixes

* **Twitter:** Fix `Show post source label` patch ([42efbe2](https://github.com/crimera/piko/commit/42efbe23f17b44259fbc597f1ec72b0456bece15))

### Features

* **Twitter:** Added `Change app icon` patch ([efbd6f7](https://github.com/crimera/piko/commit/efbd6f708357d0256104e31748a035d92acee8f4))

### Updates

* **Translations:** Update `Polish` ([15cf2b1](https://github.com/crimera/piko/commit/15cf2b1b1f61b87adde8cd27e59f83d0bba2f1d7))
* **Translations:** Update Korean ([b1edad5](https://github.com/crimera/piko/commit/b1edad5c433e9d6f06010fe438e995ab02da6f2b))

## [2.0.0-dev.9](https://github.com/crimera/piko/compare/v2.0.0-dev.8...v2.0.0-dev.9) (2025-11-05)

### Bug Fixes

* **entity:** swap username and display name method mappings in TweetEntityPatch ([b946ebf](https://github.com/crimera/piko/commit/b946ebff8b59081ad3c53eb9ed108c3695dfe5fa))

## [2.0.0-dev.8](https://github.com/crimera/piko/compare/v2.0.0-dev.7...v2.0.0-dev.8) (2025-11-02)

### Bug Fixes

* **Custom font:** Fix crash on Android 8 by restricting the functionality to Android 9+ ([477f634](https://github.com/crimera/piko/commit/477f6345a062bc3605dd343d290734074483a61c))
* **Twitter:** Added missing `notifications` item on hide sidebar elements ([39816e9](https://github.com/crimera/piko/commit/39816e91f85bad367494231e07c8ff1c0d9b58ce))
* **Twitter:** Fix `Extension hook` ([6fb52f7](https://github.com/crimera/piko/commit/6fb52f7307b596988ee8b4286717b378d3c2ca4d))
* **Twitter:** Fix font section header ([35e9b2b](https://github.com/crimera/piko/commit/35e9b2beb9efdb8d624455714c99cebbffed574e))

### Features

* **Change version code:** Add compatibleWith to make it a non-universal patch ([c684cbc](https://github.com/crimera/piko/commit/c684cbc04017549ef247438764d6f10828912c7b))
* **Settings:** Allow preference titles to be multiple lines ([5581330](https://github.com/crimera/piko/commit/558133068396d532837c53347288fa42a7fb47f6))
* **Twitter:** Added `Customize notification tabs` patch ([2e902e9](https://github.com/crimera/piko/commit/2e902e92b8cc56e757235cf47bbd6a52151b8c17))
* **Twitter:** Added `Pause search suggestions` patch ([a5f3d8e](https://github.com/crimera/piko/commit/a5f3d8e87b19b61223b4959cdccb68630ceeea4e))
* **Twitter:** Added `Remove search suggestions` patch ([33fa525](https://github.com/crimera/piko/commit/33fa5257b2d0ff0ce5423db6df733202e705aac0))

### Updates

* **Translation:** `Japanese` strings ([2f91899](https://github.com/crimera/piko/commit/2f91899580b5cf1e7eaec93ef780c27111a83865))
* **Translations:** Update `Italian` strings ([837edf9](https://github.com/crimera/piko/commit/837edf922f1d6c2319e51a5a80907f13245fa8ca))
* **Translations:** Update `Polish` ([1bcacaa](https://github.com/crimera/piko/commit/1bcacaa634e1ccd08ee7b3968c8761b789f2e5d6))
* **Translations:** Updated `Japanese` strings ([cd5a888](https://github.com/crimera/piko/commit/cd5a8880c9265bf070f2f0793db9a11c741bf22c))
* **Translation:** Updated `Brazilian Portuguese` strings ([bb448a1](https://github.com/crimera/piko/commit/bb448a1367b692f267cd176e0d9daab547088667))
* **Translation:** Updated `Polish` strings ([4ba908b](https://github.com/crimera/piko/commit/4ba908b54e24f8fc78a884bdeb19aa59f0dff0c0))

### Refactors

* **Dynamic color:** Do not get elements repeatedly in XML operation ([cc41d35](https://github.com/crimera/piko/commit/cc41d3525a6a1588f857a0b602e8a5208cec0fd3))
* **Twitter:** refactor fingerprint for tweet short text ([062aeb3](https://github.com/crimera/piko/commit/062aeb3b4902185a041b73689bad56e8636b951b))

## [2.0.0-dev.7](https://github.com/crimera/piko/compare/v2.0.0-dev.6...v2.0.0-dev.7) (2025-10-26)

### Bug Fixes

* **Custom font:** Fix crash on Android 8 by restricting the functionality to Android 9+ ([000539b](https://github.com/crimera/piko/commit/000539b8b67d97b83efdf4eab2d311e9b492f167))
* **Twitter:** Fix font section header ([a7a7b2e](https://github.com/crimera/piko/commit/a7a7b2e719293102b45e202505eeb032f81d06c0))

### Features

* **Change version code:** Add compatibleWith to make it a non-universal patch ([3664dbf](https://github.com/crimera/piko/commit/3664dbf560b9dc61cc90de4e6f53f54d86f528b0))
* **Settings:** Allow preference titles to be multiple lines ([3075555](https://github.com/crimera/piko/commit/3075555611163ff67c073afd887d7fff4aeb45a7))
* **Twitter:** Added `Pause search suggestions` patch ([ddbdcfe](https://github.com/crimera/piko/commit/ddbdcfeda9418e83e4206101c9847e866e6a23a4))
* **Twitter:** Added `Remove search suggestions` patch ([dd8e67b](https://github.com/crimera/piko/commit/dd8e67b3a634036e308a2a99844c7c966f69890b))

### Updates

* **Translation:** Updated `Brazilian Portuguese` strings ([a53f3a0](https://github.com/crimera/piko/commit/a53f3a020eaf3b88570bda8dcb01c9968873c9e2))
* **Translation:** Updated `Japanese` strings ([373e275](https://github.com/crimera/piko/commit/373e275683c7c2ee83053751be851c9757f48ff8))
* **Translation:** Updated `Polish` strings ([b6773c3](https://github.com/crimera/piko/commit/b6773c34549239a5580761faad5067f5a33c522c))

### Refactors

* **Dynamic color:** Do not get elements repeatedly in XML operation ([3417fe1](https://github.com/crimera/piko/commit/3417fe15d2d558b042a44a06e9c21a1523d12540))
* **Twitter:** refactor fingerprint for tweet short text ([c00264a](https://github.com/crimera/piko/commit/c00264aebbe4717e61dd08b0998ff814f92ba3bd))

## [2.0.0-dev.6](https://github.com/crimera/piko/compare/v2.0.0-dev.5...v2.0.0-dev.6) (2025-10-18)

### Bug Fixes

* **Dynamic color:** Reflect piko's original changes ([0c91cad](https://github.com/crimera/piko/commit/0c91cad6d4112a02735edc5776a136e9f7c88271))
* **Twitter:** fix `Enable force HD videos` patch ([23f2859](https://github.com/crimera/piko/commit/23f285974c8c12a117b81c794a64e94e6f33e531))
* **Twitter:** Update `extensions` hook ([bf20765](https://github.com/crimera/piko/commit/bf2076592f49c11337acedf0d90e8a9d2d8d4ba4))

### Features

* **Twitter:** Added `Customise font` patch ([e02d7db](https://github.com/crimera/piko/commit/e02d7dbd884ac4daf7c113d44575d085ba792989))

### Updates

* **Translations:** Update `Polish` ([0983e57](https://github.com/crimera/piko/commit/0983e5723e0b6ea1a171aeefe774f64bb3993edf))

## [2.0.0-dev.5](https://github.com/crimera/piko/compare/v2.0.0-dev.4...v2.0.0-dev.5) (2025-10-14)

### Bug Fixes

* **Twitter:** Correct Db clear keyword ([ca71ea7](https://github.com/crimera/piko/commit/ca71ea73a2957e1c64e1e2ed02b64311287bfaa0))
* **Twitter:** Fix `Native reader mode` patch in 10.28 and above ([800da14](https://github.com/crimera/piko/commit/800da149f4179044296d265ff4de9e7b2737e9b8))
* **Twitter:** Fixed host name issue due to `Custom sharing domain` patch ([3b26c84](https://github.com/crimera/piko/commit/3b26c84a902200f687cd327fb83281301e653dc1))

### Features

* **Twitter:** Added `Show changelogs` patch ([4b3adf7](https://github.com/crimera/piko/commit/4b3adf7a5e8966bc800d41b947349fb36345d5f4))

### Updates

* **Translation:** `Japanese` strings ([054b2c5](https://github.com/crimera/piko/commit/054b2c5b93a43bc412e03adcb875117e513dd5bb))
* **Translation:** Added `pl` to Bring Back Twitter resource list ([44a5436](https://github.com/crimera/piko/commit/44a5436eabe9e9493984e9413ccbc74592eb8280))
* **Translation:** Added `pt-rBr` to `Bring Back Twitter` Patch ([9e33933](https://github.com/crimera/piko/commit/9e33933279bb80a40532a382726c9ddff53d43a2))
* **Translation:** Added `zh-rHK` ([58a2f4f](https://github.com/crimera/piko/commit/58a2f4fdb64d86a61ed27ad5bf632d260738688c))
* **Translation:** Fix `Vietnamese` strings ([3d1e213](https://github.com/crimera/piko/commit/3d1e213a6691216946ae703ea1ef239e56db584c))
* **Translation:** Update `Japanese` ([6c22ae2](https://github.com/crimera/piko/commit/6c22ae255149a057680ad85f6a59ce829100bb65))
* **Translation:** Update `Vietnamese` ([9343e0e](https://github.com/crimera/piko/commit/9343e0e5c750566b4a86ff72de84050950c9551a))
* **Translation:** Update Traditional Chinese ([9f4d654](https://github.com/crimera/piko/commit/9f4d654f3bf91d7e68adf0b426c02feed32d3eed))
* **Twitter:** Set Splashscreen color to blue in `Bring back twitter` patch ([fbb9a09](https://github.com/crimera/piko/commit/fbb9a093dabe0634792f6b342489ed06d75f6bc6))

## [2.0.0-dev.4](https://github.com/crimera/piko/compare/v2.0.0-dev.3...v2.0.0-dev.4) (2025-10-08)

### Updates

* **Translations:** Update `Polish` ([43da4b5](https://github.com/crimera/piko/commit/43da4b5ebc4b12d155d8f81495bbd6a09b1f44ad))

## [2.0.0-dev.3](https://github.com/crimera/piko/compare/v2.0.0-dev.2...v2.0.0-dev.3) (2025-10-04)

### Refactors

* **Twitter:** Correct ja strings replacement in BBT patch ([ee200a5](https://github.com/crimera/piko/commit/ee200a5c5ae14e1a935903b5de9e7ecf7f03e0f9))
* **Twitter:** fix `Remove promoted trends` ([0e1d4ed](https://github.com/crimera/piko/commit/0e1d4ed2ac090451172f2bed985310061b987428))
* **Twitter:** handle piko resources programmatically ([3cc0f07](https://github.com/crimera/piko/commit/3cc0f0715c6ad62c14d6cd13e91f0965886e1534))
* **Twitter:** refactor `Bring back twitter` patch ([f24cb72](https://github.com/crimera/piko/commit/f24cb72309f648493b209fffb1e7d19bd5c705f6))
* **Twitter:** remove old comments ([1655283](https://github.com/crimera/piko/commit/165528374dadeab472a5f7d28bc60be12a09bbe0))

## [2.0.0-dev.2](https://github.com/crimera/piko/compare/v2.0.0-dev.1...v2.0.0-dev.2) (2025-09-27)

### Bug Fixes

* **Twitter:** Fix remove ads on replies ([8fda05e](https://github.com/crimera/piko/commit/8fda05ec94ffc7a48dfc1eb3a17bb14f6328ca6f))
* **Universal:** Set version code as int ([ee83df7](https://github.com/crimera/piko/commit/ee83df7f8f18cf4cf2306b0a662a845ed565e106))

### Refactors

* **Twitter:** potential fix for resource compilation failing in manager ([fa0dfac](https://github.com/crimera/piko/commit/fa0dfac2b959ba9a958bb458cc4572f28ff94902))

## [2.0.0-dev.1](https://github.com/crimera/piko/compare/v1.59.0...v2.0.0-dev.1) (2025-09-24)

### ⚠ BREAKING CHANGES

* Various APIs have been changed or removed.

### Bug Fixes

* some patch has compatibleWith and dependsOn in execute block ([dd25a6c](https://github.com/crimera/piko/commit/dd25a6cd43354b8d6f4cb60d7798c539b06a6e50))
* wrong index number on multiple medias ([d088d03](https://github.com/crimera/piko/commit/d088d03a5dc743e1e50f403d14b71bc59b38571b))

### Features

* Migrate ci ([c737ca8](https://github.com/crimera/piko/commit/c737ca8edef074ff1beff17db7d27e5f26c17dc1))

## [1.59.0](https://github.com/crimera/piko/compare/v1.58.1...v1.59.0) (2025-09-11)

### Features

* **Twitter:** Added 'Native reader mode'  patch ([308e1e6](https://github.com/crimera/piko/commit/308e1e6ae392eb6c0b9ade6383957fd2983e68f5))

### Updates

* **Translations:** New translations for `pt-br` ([850aa0d](https://github.com/crimera/piko/commit/850aa0d17e0cd9a83a9c897be11b45784a82860a))
* **Translations:** Update `Polish` ([973391d](https://github.com/crimera/piko/commit/973391d6e0cfb1735dbc2102a419b9df783fa8f9))

### Refactors

* **Twitter:** consolidate native features ([79a37af](https://github.com/crimera/piko/commit/79a37afded871a185545afcd1c6a36815ec89b41))
* **Twitter:** Moved to `models` approach ([4bc56cb](https://github.com/crimera/piko/commit/4bc56cbb3de7147706786e186ebf7f2ea9df15a2))

## [1.59.0-dev.3](https://github.com/crimera/piko/compare/v1.59.0-dev.2...v1.59.0-dev.3) (2025-09-11)

### Updates

* **Translations:** New translations for `pt-br` ([850aa0d](https://github.com/crimera/piko/commit/850aa0d17e0cd9a83a9c897be11b45784a82860a))

## [1.59.0-dev.2](https://github.com/crimera/piko/compare/v1.59.0-dev.1...v1.59.0-dev.2) (2025-09-03)

### Updates

* **Translations:** Update `Polish` ([973391d](https://github.com/crimera/piko/commit/973391d6e0cfb1735dbc2102a419b9df783fa8f9))

## [1.59.0-dev.1](https://github.com/crimera/piko/compare/v1.58.1...v1.59.0-dev.1) (2025-09-02)

### Features

* **Twitter:** Added 'Native reader mode'  patch ([308e1e6](https://github.com/crimera/piko/commit/308e1e6ae392eb6c0b9ade6383957fd2983e68f5))

### Refactors

* **Twitter:** consolidate native features ([79a37af](https://github.com/crimera/piko/commit/79a37afded871a185545afcd1c6a36815ec89b41))
* **Twitter:** Moved to `models` approach ([4bc56cb](https://github.com/crimera/piko/commit/4bc56cbb3de7147706786e186ebf7f2ea9df15a2))

## [1.58.1](https://github.com/crimera/piko/compare/v1.58.0...v1.58.1) (2025-09-02)

### Updates

* **Translations:** Update `Japanese` ([c28d9c7](https://github.com/crimera/piko/commit/c28d9c7850794cb8aede686201e5d80482c42f1e))

## [1.58.1-dev.1](https://github.com/crimera/piko/compare/v1.58.0...v1.58.1-dev.1) (2025-09-01)

### Updates

* **Translations:** Update `Japanese` ([c28d9c7](https://github.com/crimera/piko/commit/c28d9c7850794cb8aede686201e5d80482c42f1e))

## [1.58.0](https://github.com/crimera/piko/compare/v1.57.1...v1.58.0) (2025-08-30)

### Features

* **Translations:** Update `Polish` ([97b3790](https://github.com/crimera/piko/commit/97b379029deba1044436cb5a983335b515e7f959))

## [1.58.0-dev.1](https://github.com/crimera/piko/compare/v1.57.1...v1.58.0-dev.1) (2025-08-21)

### Features

* **Translations:** Update `Polish` ([97b3790](https://github.com/crimera/piko/commit/97b379029deba1044436cb5a983335b515e7f959))

## [1.57.1](https://github.com/crimera/piko/compare/v1.57.0...v1.57.1) (2025-08-11)

### Bug Fixes

* Unescaped apostrophe ([487e992](https://github.com/crimera/piko/commit/487e99259b772cbf44f09028807db223f464df85))

## [1.57.1-dev.1](https://github.com/crimera/piko/compare/v1.57.0...v1.57.1-dev.1) (2025-08-11)

### Bug Fixes

* Unescaped apostrophe ([487e992](https://github.com/crimera/piko/commit/487e99259b772cbf44f09028807db223f464df85))

## [1.57.0](https://github.com/crimera/piko/compare/v1.56.0...v1.57.0) (2025-08-11)

### Bug Fixes

* **Twitter:** Fix `Customize side bar items` patch ([4664f02](https://github.com/crimera/piko/commit/4664f02c065396dc28406fe5b47478eff6b6c4aa))
* **Twitter:** Fix App icon error toast ([2997b56](https://github.com/crimera/piko/commit/2997b56f13c37681acbf89efcbd12647ee1c5056))

### Features

* **Twitter:** support custom deeplink hosts ([499cc5e](https://github.com/crimera/piko/commit/499cc5ea3e32d3e6d551755df2f94b7dbc3da9af))

### Refactors

* **Twitter:** Move redirect BM to settings patch dependancy ([4dbc491](https://github.com/crimera/piko/commit/4dbc49198ecd3a10d288f8f7cebcbca255aaa9b7))

## [1.57.0-dev.2](https://github.com/crimera/piko/compare/v1.57.0-dev.1...v1.57.0-dev.2) (2025-08-03)

### Bug Fixes

* **Twitter:** Fix `Customize side bar items` patch ([4664f02](https://github.com/crimera/piko/commit/4664f02c065396dc28406fe5b47478eff6b6c4aa))
* **Twitter:** Fix App icon error toast ([2997b56](https://github.com/crimera/piko/commit/2997b56f13c37681acbf89efcbd12647ee1c5056))

### Refactors

* **Twitter:** Move redirect BM to settings patch dependancy ([4dbc491](https://github.com/crimera/piko/commit/4dbc49198ecd3a10d288f8f7cebcbca255aaa9b7))

## [1.57.0-dev.1](https://github.com/crimera/piko/compare/v1.56.0...v1.57.0-dev.1) (2025-07-26)

### Features

* **Twitter:** support custom deeplink hosts ([499cc5e](https://github.com/crimera/piko/commit/499cc5ea3e32d3e6d551755df2f94b7dbc3da9af))

## [1.56.0](https://github.com/crimera/piko/compare/v1.55.0...v1.56.0) (2025-07-22)

### Features

* **Translations:** Update `Polish` ([e49393e](https://github.com/crimera/piko/commit/e49393eefe75118c29575324f5832c7cc3589530))
* **Twitter:** Added `Show post source label` patch ([931bc76](https://github.com/crimera/piko/commit/931bc762c3fc03535bf427b3e691f09e76271c85))

### Updates

* **Translations:** Update `Polish` ([#613](https://github.com/crimera/piko/issues/613)) ([b6ea9de](https://github.com/crimera/piko/commit/b6ea9dec045eeb5172fc1d01b1365929e127b4b0))

## [1.56.0-dev.2](https://github.com/crimera/piko/compare/v1.56.0-dev.1...v1.56.0-dev.2) (2025-07-19)

### Features

* **Translations:** Update `Polish` ([e49393e](https://github.com/crimera/piko/commit/e49393eefe75118c29575324f5832c7cc3589530))

## [1.56.0-dev.1](https://github.com/crimera/piko/compare/v1.55.1-dev.1...v1.56.0-dev.1) (2025-07-13)

### Features

* **Twitter:** Added `Show post source label` patch ([931bc76](https://github.com/crimera/piko/commit/931bc762c3fc03535bf427b3e691f09e76271c85))

## [1.55.1-dev.1](https://github.com/crimera/piko/compare/v1.55.0...v1.55.1-dev.1) (2025-07-10)

### Updates

* **Translations:** Update `Polish` ([#613](https://github.com/crimera/piko/issues/613)) ([b6ea9de](https://github.com/crimera/piko/commit/b6ea9dec045eeb5172fc1d01b1365929e127b4b0))

## [1.55.0](https://github.com/crimera/piko/compare/v1.54.2...v1.55.0) (2025-07-05)

### Bug Fixes

* **Twitter - Log server response:** Escape single qoutes ([cedbfaf](https://github.com/crimera/piko/commit/cedbfaf77913afb81b0363207a9839ea1624fa63))
* **Twitter:** `Hide community badges` patch ([f539260](https://github.com/crimera/piko/commit/f5392600b3e51a3c32fa3ffb4da5d751da3b8612))

### Features

* **Twitter:** Added `Hide community badges` patch ([57936a1](https://github.com/crimera/piko/commit/57936a167eb23eeb992ca39015149e71e04270c5))
* **Twitter:** Added `Log server response` patch ([bff4363](https://github.com/crimera/piko/commit/bff4363a56386ec6e1c80f2a57bdc70a28827be6))

## [1.55.0-dev.2](https://github.com/crimera/piko/compare/v1.55.0-dev.1...v1.55.0-dev.2) (2025-07-05)

### Bug Fixes

* **Twitter - Log server response:** Escape single qoutes ([cedbfaf](https://github.com/crimera/piko/commit/cedbfaf77913afb81b0363207a9839ea1624fa63))

## [1.55.0-dev.1](https://github.com/crimera/piko/compare/v1.54.2-dev.1...v1.55.0-dev.1) (2025-07-03)

### Bug Fixes

* **Twitter:** `Hide community badges` patch ([f539260](https://github.com/crimera/piko/commit/f5392600b3e51a3c32fa3ffb4da5d751da3b8612))

### Features

* **Twitter:** Added `Hide community badges` patch ([57936a1](https://github.com/crimera/piko/commit/57936a167eb23eeb992ca39015149e71e04270c5))
* **Twitter:** Added `Log server response` patch ([bff4363](https://github.com/crimera/piko/commit/bff4363a56386ec6e1c80f2a57bdc70a28827be6))

## [1.54.2-dev.1](https://github.com/crimera/piko/compare/v1.54.1...v1.54.2-dev.1) (2025-06-17)
=======
## [1.54.2](https://github.com/crimera/piko/compare/v1.54.1...v1.54.2) (2025-06-17)

### Bug Fixes

* **Twitter:** fix `Enable force HD videos` patch ([6d8d200](https://github.com/crimera/piko/commit/6d8d200a521375252c675117365e1fb85f701083))
* **Twitter:** fix `Hide nudge button` patch (again) ([be2cd9d](https://github.com/crimera/piko/commit/be2cd9de0aa2545274369ed0c724ffead77148ee))

## [1.54.1-dev.2](https://github.com/crimera/piko/compare/v1.54.1-dev.1...v1.54.1-dev.2) (2025-06-16)

### Bug Fixes

* **Twitter:** fix `Enable force HD videos` patch ([6d8d200](https://github.com/crimera/piko/commit/6d8d200a521375252c675117365e1fb85f701083))
* **Twitter:** fix `Hide nudge button` patch (again) ([be2cd9d](https://github.com/crimera/piko/commit/be2cd9de0aa2545274369ed0c724ffead77148ee))

## [1.54.1-dev.1](https://github.com/crimera/piko/compare/v1.54.0...v1.54.1-dev.1) (2025-06-16)

## [1.54.1](https://github.com/crimera/piko/compare/v1.54.0...v1.54.1) (2025-06-16)

### Bug Fixes

* **Twitter:** Fix unescaped characters ([829eedd](https://github.com/crimera/piko/commit/829eedd90eabc13575b49da9cc5551f894787b76))

## [1.54.0](https://github.com/crimera/piko/compare/v1.53.0...v1.54.0) (2025-06-16)

### Bug Fixes

* **Translations:** Fix unescaped strings in fr and vi ([be94c4c](https://github.com/crimera/piko/commit/be94c4c3b6785a1f9ecfdc1dbad48a4a9885a539))
* **Twitter - copy media link + Custom download folder:** Update fingerprint to work on latest twitter release ([cc8fadb](https://github.com/crimera/piko/commit/cc8fadbac2d31139ae8b9572dd1aeb2565564e75))
* **Twitter:** Fix `Enable Undo Posts` patch ([883b6cb](https://github.com/crimera/piko/commit/883b6cb92c52d1a475f6663bdf474518280b4da4))
* **Twitter:** Fix `Hide nudge button` patch ([87ceb49](https://github.com/crimera/piko/commit/87ceb49f841c99012fcbbc315edc1c4657376e37))
* **Twitter:** Fix Settings crash ([015c5bd](https://github.com/crimera/piko/commit/015c5bdd1247ec6263fa194ca6e580690e13e223))
* **Twitter:** Fix share menu patches ([ba561be](https://github.com/crimera/piko/commit/ba561be67cf5d854cfff477a3a80b85928a4170a))
* **ui:** adjust monet-light colors to have a white background ([0906e93](https://github.com/crimera/piko/commit/0906e93027747e43becd1d94bca24f54ecc97683))

### Features

* **Translations:** Add italian translation ([88e22fb](https://github.com/crimera/piko/commit/88e22fbb64eae5b2b185c0c785d75a29bb342458))
* **Translations:** Korean translation added ([492e631](https://github.com/crimera/piko/commit/492e6312df05d3bdae618e350a929a8b22914c98))
* **Translations:** Update `Japanese` ([80282f0](https://github.com/crimera/piko/commit/80282f0e4eb3b6f1cde7eb6e30f7d00ba9b26a0c))
* **Translations:** Update `Japanese` ([a7da7d7](https://github.com/crimera/piko/commit/a7da7d7468203a3244496b4ed692a8d09da18b45))

### Updates

* **Translations:** Add French & Korean to Language List ([#567](https://github.com/crimera/piko/issues/567)) ([6e139be](https://github.com/crimera/piko/commit/6e139be56dbc4f754c892d7c8ba2ed28ee8fa67f))

## [1.54.0-dev.8](https://github.com/crimera/piko/compare/v1.54.0-dev.7...v1.54.0-dev.8) (2025-06-15)

### Bug Fixes

* **Twitter:** Fix share menu patches ([ba561be](https://github.com/crimera/piko/commit/ba561be67cf5d854cfff477a3a80b85928a4170a))

## [1.54.0-dev.7](https://github.com/crimera/piko/compare/v1.54.0-dev.6...v1.54.0-dev.7) (2025-06-09)

### Bug Fixes

* **Twitter:** Fix `Enable Undo Posts` patch ([883b6cb](https://github.com/crimera/piko/commit/883b6cb92c52d1a475f6663bdf474518280b4da4))
* **Twitter:** Fix `Hide nudge button` patch ([87ceb49](https://github.com/crimera/piko/commit/87ceb49f841c99012fcbbc315edc1c4657376e37))
* **Twitter:** Fix Settings crash ([015c5bd](https://github.com/crimera/piko/commit/015c5bdd1247ec6263fa194ca6e580690e13e223))
* **ui:** adjust monet-light colors to have a white background ([0906e93](https://github.com/crimera/piko/commit/0906e93027747e43becd1d94bca24f54ecc97683))

## [1.54.0-dev.6](https://github.com/crimera/piko/compare/v1.54.0-dev.5...v1.54.0-dev.6) (2025-05-19)

### Features

* **Translations:** Update `Japanese` ([80282f0](https://github.com/crimera/piko/commit/80282f0e4eb3b6f1cde7eb6e30f7d00ba9b26a0c))

## [1.54.0-dev.5](https://github.com/crimera/piko/compare/v1.54.0-dev.4...v1.54.0-dev.5) (2025-05-09)

### Bug Fixes

* **Twitter - copy media link + Custom download folder:** Update fingerprint to work on latest twitter release ([cc8fadb](https://github.com/crimera/piko/commit/cc8fadbac2d31139ae8b9572dd1aeb2565564e75))

## [1.54.0-dev.4](https://github.com/crimera/piko/compare/v1.54.0-dev.3...v1.54.0-dev.4) (2025-04-25)

### Bug Fixes

* **Translations:** Fix unescaped strings in fr and vi ([be94c4c](https://github.com/crimera/piko/commit/be94c4c3b6785a1f9ecfdc1dbad48a4a9885a539))

## [1.54.0-dev.3](https://github.com/crimera/piko/compare/v1.54.0-dev.2...v1.54.0-dev.3) (2025-04-25)

### Updates

* **Translations:** Add French & Korean to Language List ([#567](https://github.com/crimera/piko/issues/567)) ([6e139be](https://github.com/crimera/piko/commit/6e139be56dbc4f754c892d7c8ba2ed28ee8fa67f))

## [1.54.0-dev.2](https://github.com/crimera/piko/compare/v1.54.0-dev.1...v1.54.0-dev.2) (2025-04-20)

### Features

* **Translations:** Update `Japanese` ([a7da7d7](https://github.com/crimera/piko/commit/a7da7d7468203a3244496b4ed692a8d09da18b45))

## [1.54.0-dev.1](https://github.com/crimera/piko/compare/v1.53.0...v1.54.0-dev.1) (2025-04-14)

### Features

* **Translations:** Korean translation added ([492e631](https://github.com/crimera/piko/commit/492e6312df05d3bdae618e350a929a8b22914c98))

## [1.53.0](https://github.com/crimera/piko/compare/v1.52.0...v1.53.0) (2025-04-06)

### Bug Fixes

* **Twitter - Custom Downloader:** Added VideoDataClass Fingerprint ([99ff271](https://github.com/crimera/piko/commit/99ff271b3fcbf1a6f3666b97c256528435f6501c))
* **Twitter - Custom Downloader:** Improve fingerprints ([f962310](https://github.com/crimera/piko/commit/f9623106106bd21b9b595983e8db244c8c35e227))

### Features

* **Translations:** Update `Polish` ([d81f93c](https://github.com/crimera/piko/commit/d81f93cff2c31c9fe82ff8e43598ab90e6d899ab))

## [1.53.0-dev.1](https://github.com/crimera/piko/compare/v1.52.0...v1.53.0-dev.1) (2025-04-06)

### Bug Fixes

* **Twitter - Custom Downloader:** Added VideoDataClass Fingerprint ([99ff271](https://github.com/crimera/piko/commit/99ff271b3fcbf1a6f3666b97c256528435f6501c))
* **Twitter - Custom Downloader:** Improve fingerprints ([f962310](https://github.com/crimera/piko/commit/f9623106106bd21b9b595983e8db244c8c35e227))

### Features

* **Translations:** Update `Polish` ([d81f93c](https://github.com/crimera/piko/commit/d81f93cff2c31c9fe82ff8e43598ab90e6d899ab))

## [1.52.0-dev.1](https://github.com/crimera/piko/compare/v1.51.2-dev.1...v1.52.0-dev.1) (2025-04-05)

### Features

* **Translations:** Update `Polish` ([d81f93c](https://github.com/crimera/piko/commit/d81f93cff2c31c9fe82ff8e43598ab90e6d899ab))

## [1.51.2-dev.1](https://github.com/crimera/piko/compare/v1.51.1...v1.51.2-dev.1) (2025-04-05)

### Bug Fixes

* **Twitter - Custom Downloader:** Added VideoDataClass Fingerprint ([99ff271](https://github.com/crimera/piko/commit/99ff271b3fcbf1a6f3666b97c256528435f6501c))
* **Twitter - Custom Downloader:** Improve fingerprints ([f962310](https://github.com/crimera/piko/commit/f9623106106bd21b9b595983e8db244c8c35e227))

## [1.52.0](https://github.com/crimera/piko/compare/v1.51.1...v1.52.0) (2025-04-06)

### Features

* **Translations:** French translation added ([a6f650c](https://github.com/crimera/piko/commit/a6f650c3cbea3b3964fc4b58962067

## [1.51.1](https://github.com/crimera/piko/compare/v1.51.0...v1.51.1) (2025-03-26)

### Bug Fixes

* Add support for 10.83.0 releases ([9344d52](https://github.com/crimera/piko/commit/9344d527d1ec5e0fd812bd835c45304a6619df95))

## [1.51.1-dev.1](https://github.com/crimera/piko/compare/v1.51.0...v1.51.1-dev.1) (2025-03-20)

### Bug Fixes

* Add support for 10.83.0 releases ([9344d52](https://github.com/crimera/piko/commit/9344d527d1ec5e0fd812bd835c45304a6619df95))

## [1.51.0](https://github.com/crimera/piko/compare/v1.50.0...v1.51.0) (2025-03-20)

### Bug Fixes

* Add back settings for the custom sharemenu buttons ([bc7e777](https://github.com/crimera/piko/commit/bc7e777e59091801238d4692823415b0d7d2855b))
* Adding debug menu patch results in a crash ([caf82eb](https://github.com/crimera/piko/commit/caf82ebd95bd113dbd5cacda1deffa9194cb280b))
* **Bring back twitter:** Resource compilation fails on ReVanced Manager ([a364bec](https://github.com/crimera/piko/commit/a364becc7f6f70724c37b9499e79885fb09f0436))
* **Enable Reader Mode:** Specify the compatible version, show a warning instead of throwing an exception when failed ([b1c52e7](https://github.com/crimera/piko/commit/b1c52e73daabe3222e925ba5d6b866af31a35045))
* **Remove Detailed posts:** Change the settings label from "detailed" to "related" ([3cf5f9e](https://github.com/crimera/piko/commit/3cf5f9ec9e383deb17919f2b3c5254954502c825))
* Remove obsolete `Open browser chooser on opening links` patch ([a1e2b76](https://github.com/crimera/piko/commit/a1e2b766a872c3ee2facef1444bfa92ab4236144))

### Features

* **Settings:** Add a description to "Native features" page ([be7aa54](https://github.com/crimera/piko/commit/be7aa5472c9b12ff6207992b21292b4fb4d337cf))

### Updates

* Improve adding new buttons in share menu ([16cc3bd](https://github.com/crimera/piko/commit/16cc3bd01c03bfc800d61f0844b455c5d086422a))

## [1.51.0-dev.4](https://github.com/crimera/piko/compare/v1.51.0-dev.3...v1.51.0-dev.4) (2025-03-20)

### Bug Fixes

* Adding debug menu patch results in a crash ([caf82eb](https://github.com/crimera/piko/commit/caf82ebd95bd113dbd5cacda1deffa9194cb280b))

## [1.51.0-dev.3](https://github.com/crimera/piko/compare/v1.51.0-dev.2...v1.51.0-dev.3) (2025-03-20)

### Bug Fixes

* Add back settings for the custom sharemenu buttons ([bc7e777](https://github.com/crimera/piko/commit/bc7e777e59091801238d4692823415b0d7d2855b))

## [1.51.0-dev.2](https://github.com/crimera/piko/compare/v1.51.0-dev.1...v1.51.0-dev.2) (2025-03-20)

### Updates

* Improve adding new buttons in share menu ([16cc3bd](https://github.com/crimera/piko/commit/16cc3bd01c03bfc800d61f0844b455c5d086422a))

## [1.51.0-dev.1](https://github.com/crimera/piko/compare/v1.50.0...v1.51.0-dev.1) (2025-03-17)

### Bug Fixes

* **Bring back twitter:** Resource compilation fails on ReVanced Manager ([a364bec](https://github.com/crimera/piko/commit/a364becc7f6f70724c37b9499e79885fb09f0436))
* **Enable Reader Mode:** Specify the compatible version, show a warning instead of throwing an exception when failed ([b1c52e7](https://github.com/crimera/piko/commit/b1c52e73daabe3222e925ba5d6b866af31a35045))
* **Remove Detailed posts:** Change the settings label from "detailed" to "related" ([3cf5f9e](https://github.com/crimera/piko/commit/3cf5f9ec9e383deb17919f2b3c5254954502c825))
* Remove obsolete `Open browser chooser on opening links` patch ([a1e2b76](https://github.com/crimera/piko/commit/a1e2b766a872c3ee2facef1444bfa92ab4236144))

### Features

* **Settings:** Add a description to "Native features" page ([be7aa54](https://github.com/crimera/piko/commit/be7aa5472c9b12ff6207992b21292b4fb4d337cf))

## [1.50.0](https://github.com/crimera/piko/compare/v1.49.1...v1.50.0) (2025-02-08)

### Features

* **Translations:** Update `Japanese` ([039aac3](https://github.com/crimera/piko/commit/039aac304215858769c794684f837719edad9790))

## [1.50.0-dev.1](https://github.com/crimera/piko/compare/v1.49.1...v1.50.0-dev.1) (2025-01-31)

### Features

* **Translations:** Update `Japanese` ([039aac3](https://github.com/crimera/piko/commit/039aac304215858769c794684f837719edad9790))

## [1.49.1](https://github.com/crimera/piko/compare/v1.49.0...v1.49.1) (2025-01-29)

### Bug Fixes

* **Twitter:** fix `Custom downloader` patch ([f4d953a](https://github.com/crimera/piko/commit/f4d953a3dc7cb8325e56e515cd38a8ab707d2162))
* **Twitter:** fix `Custom translator` patch ([8065fa3](https://github.com/crimera/piko/commit/8065fa32019d1d1bd4c9bd116f1774e345203356))
* **Twitter:** fix `Enable debug menu for posts` patch ([72a72f2](https://github.com/crimera/piko/commit/72a72f2114d0ee9fe2cac0e5eae5b03e196dd6e2))

### Refactors

* **Twitter:** refactor button class Hook ([2bf411a](https://github.com/crimera/piko/commit/2bf411a61fd5181d5266d1496becf7b93f4d0d08))
* **Twitter:** Removed unused string from pt-BR ([cdb2067](https://github.com/crimera/piko/commit/cdb2067360944309a9156b71bf9776457c44c49a))

## [1.49.1-dev.1](https://github.com/crimera/piko/compare/v1.49.0...v1.49.1-dev.1) (2025-01-28)

### Bug Fixes

* **Twitter:** fix `Custom downloader` patch ([f4d953a](https://github.com/crimera/piko/commit/f4d953a3dc7cb8325e56e515cd38a8ab707d2162))
* **Twitter:** fix `Custom translator` patch ([8065fa3](https://github.com/crimera/piko/commit/8065fa32019d1d1bd4c9bd116f1774e345203356))
* **Twitter:** fix `Enable debug menu for posts` patch ([72a72f2](https://github.com/crimera/piko/commit/72a72f2114d0ee9fe2cac0e5eae5b03e196dd6e2))

### Refactors

* **Twitter:** refactor button class Hook ([2bf411a](https://github.com/crimera/piko/commit/2bf411a61fd5181d5266d1496becf7b93f4d0d08))
* **Twitter:** Removed unused string from pt-BR ([cdb2067](https://github.com/crimera/piko/commit/cdb2067360944309a9156b71bf9776457c44c49a))

## [1.49.0](https://github.com/crimera/piko/compare/v1.48.0...v1.49.0) (2025-01-28)

### Features

* **Twitter:** Added `Remove Todays news` patch ([8324633](https://github.com/crimera/piko/commit/8324633509fdf61756751fdde67e5752ff3dee5c))

### Refactors

* **Twitter:** refactor strings-pl ([9aacfa1](https://github.com/crimera/piko/commit/9aacfa1baf7ed0301850f30ae8053c101ef8b27b))

## [1.49.0-dev.1](https://github.com/crimera/piko/compare/v1.48.0...v1.49.0-dev.1) (2025-01-22)

### Features

* **Twitter:** Added `Remove Todays news` patch ([8324633](https://github.com/crimera/piko/commit/8324633509fdf61756751fdde67e5752ff3dee5c))

### Refactors

* **Twitter:** refactor strings-pl ([9aacfa1](https://github.com/crimera/piko/commit/9aacfa1baf7ed0301850f30ae8053c101ef8b27b))

## [1.48.0](https://github.com/crimera/piko/compare/v1.47.1...v1.48.0) (2025-01-07)

### Features

* **Translations:** Updated Traditional Chinese string-zh-rCN.xml ([6cc2373](https://github.com/crimera/piko/commit/6cc2373e417a1ed386a2682f970662e367033b43))
* **Translations:** Updated Traditional Chinese string-zh-rTW.xml ([6787c28](https://github.com/crimera/piko/commit/6787c28318762382d73f1eb8c6ad9abc4b2ee2e6))

### Refactors

* **Twitter:** Added 'Native features' section ([12d2bda](https://github.com/crimera/piko/commit/12d2bdaafd111eee81f22bdafa306c3493754c80))
* **Twitter:** Added Native download filename customization ([99a10b1](https://github.com/crimera/piko/commit/99a10b1615ffa3fb72df0ad283255e9c837a012f))

## [1.48.0-dev.2](https://github.com/crimera/piko/compare/v1.48.0-dev.1...v1.48.0-dev.2) (2024-12-24)

### Refactors

* **Twitter:** Added 'Native features' section ([12d2bda](https://github.com/crimera/piko/commit/12d2bdaafd111eee81f22bdafa306c3493754c80))
* **Twitter:** Added Native download filename customization ([99a10b1](https://github.com/crimera/piko/commit/99a10b1615ffa3fb72df0ad283255e9c837a012f))

## [1.48.0-dev.1](https://github.com/crimera/piko/compare/v1.47.1-dev.1...v1.48.0-dev.1) (2024-12-22)

### Features

* **Translations:** Updated Traditional Chinese string-zh-rCN.xml ([6cc2373](https://github.com/crimera/piko/commit/6cc2373e417a1ed386a2682f970662e367033b43))
* **Translations:** Updated Traditional Chinese string-zh-rTW.xml ([6787c28](https://github.com/crimera/piko/commit/6787c28318762382d73f1eb8c6ad9abc4b2ee2e6))

## [1.47.1-dev.1](https://github.com/crimera/piko/compare/v1.47.0...v1.47.1-dev.1) (2024-12-22)

### Bug Fixes

* Improve getUsername fingerprint ([af617af](https://github.com/crimera/piko/commit/af617af523bfa7df00a232a51364ea9e8ea4d34f))

## [1.47.0](https://github.com/crimera/piko/compare/v1.46.1...v1.47.0) (2024-12-14)

### Features

* **Twitter:** Added `Customize search suggestions` patch ([aaca565](https://github.com/crimera/piko/commit/aaca565c650d6cf4b2cbb8abf2e206efe9983cb1))
* **Twitter:** Added `Customize search tab items` patch ([9edc97a](https://github.com/crimera/piko/commit/9edc97a89e61f28a5cd9a419d167a6c51a1a9816))
* **Twitter:** Added `Remove top people in search` patch ([95b5ef9](https://github.com/crimera/piko/commit/95b5ef902e6f595093b2304e5b2a8189f273389c))

### Refactors

* **Twitter:** Strings in 'Customization' ([c52c2c0](https://github.com/crimera/piko/commit/c52c2c01948bdea5593636a99502d8b5c6c41dca))
* **Twitter:** Strings in 'Customization' ([a75221b](https://github.com/crimera/piko/commit/a75221b80c1d3223b88890a95ca1610432defbe2))

## [1.47.0-dev.1](https://github.com/crimera/piko/compare/v1.46.1...v1.47.0-dev.1) (2024-12-09)

### Features

* **Twitter:** Added `Customize search suggestions` patch ([aaca565](https://github.com/crimera/piko/commit/aaca565c650d6cf4b2cbb8abf2e206efe9983cb1))
* **Twitter:** Added `Customize search tab items` patch ([9edc97a](https://github.com/crimera/piko/commit/9edc97a89e61f28a5cd9a419d167a6c51a1a9816))
* **Twitter:** Added `Remove top people in search` patch ([95b5ef9](https://github.com/crimera/piko/commit/95b5ef902e6f595093b2304e5b2a8189f273389c))

### Refactors

* **Twitter:** Strings in 'Customization' ([c52c2c0](https://github.com/crimera/piko/commit/c52c2c01948bdea5593636a99502d8b5c6c41dca))
* **Twitter:** Strings in 'Customization' ([a75221b](https://github.com/crimera/piko/commit/a75221b80c1d3223b88890a95ca1610432defbe2))

## [1.46.1](https://github.com/crimera/piko/compare/v1.46.0...v1.46.1) (2024-12-04)

### Refactors

* **Twitter:** Changed `Top Articles ` string ([b590695](https://github.com/crimera/piko/commit/b5906957cb8cda07a40e02008b5ad69ad6389637))

## [1.46.1-dev.1](https://github.com/crimera/piko/compare/v1.46.0...v1.46.1-dev.1) (2024-12-03)

### Refactors

* **Twitter:** Changed `Top Articles ` string ([b590695](https://github.com/crimera/piko/commit/b5906957cb8cda07a40e02008b5ad69ad6389637))

## [1.46.0](https://github.com/crimera/piko/compare/v1.45.1...v1.46.0) (2024-12-01)

### Features

* **Twitter:** Added `Customize explore tabs` patch ([4be6a7f](https://github.com/crimera/piko/commit/4be6a7fe6ace42ca299d2e4bc7f9a963774f3bcd))

## [1.46.0-dev.1](https://github.com/crimera/piko/compare/v1.45.1...v1.46.0-dev.1) (2024-11-27)

### Features

* **Twitter:** Added `Customize explore tabs` patch ([4be6a7f](https://github.com/crimera/piko/commit/4be6a7fe6ace42ca299d2e4bc7f9a963774f3bcd))

## [1.45.1](https://github.com/crimera/piko/compare/v1.45.0...v1.45.1) (2024-11-27)

### Bug Fixes

* **Twitter:** Change patch name from `Enable app icon settings` -> `Enable app icons` ([5a7ef46](https://github.com/crimera/piko/commit/5a7ef469557e5cf4ecc9ce8465d73c20501dc524))

### Refactors

* **Twitter:** Added toggle for `Show sensitive media` patch ([cac4fe7](https://github.com/crimera/piko/commit/cac4fe747640e37dc9155c63f23082c44963c05d))

## [1.45.1-dev.2](https://github.com/crimera/piko/compare/v1.45.1-dev.1...v1.45.1-dev.2) (2024-11-25)

### Refactors

* **Twitter:** Added toggle for `Show sensitive media` patch ([cac4fe7](https://github.com/crimera/piko/commit/cac4fe747640e37dc9155c63f23082c44963c05d))

## [1.45.1-dev.1](https://github.com/crimera/piko/compare/v1.45.0...v1.45.1-dev.1) (2024-11-24)

### Bug Fixes

* **Twitter:** Change patch name from `Enable app icon settings` -> `Enable app icons` ([5a7ef46](https://github.com/crimera/piko/commit/5a7ef469557e5cf4ecc9ce8465d73c20501dc524))

## [1.45.0](https://github.com/crimera/piko/compare/v1.44.0...v1.45.0) (2024-11-14)

### Bug Fixes

* **Twitter:** Share menu button add hook in/from 10.67 ([a640946](https://github.com/crimera/piko/commit/a640946e7045be359535ed0eead00ec80d2e10c4))

### Features

* **Twitter:** Added `Customise post font size` patch ([ea608e0](https://github.com/crimera/piko/commit/ea608e09b7b0775f38ca1914503e1b54dc7b73f9))

### Refactors

* **Twitter:** refactor values of list preference ([e0e5c5b](https://github.com/crimera/piko/commit/e0e5c5b8639efcd5eecd6476bc59b7f125d5b851))

### Perfomance

* **Bring back twitter:** Optimize string replacement ([#440](https://github.com/crimera/piko/issues/440)) ([7e61023](https://github.com/crimera/piko/commit/7e6102351d9488f89489f87df2ec45d6d0d178d8))

## [1.45.0-dev.1](https://github.com/crimera/piko/compare/v1.44.1-dev.2...v1.45.0-dev.1) (2024-11-12)

### Features

* **Twitter:** Added `Customise post font size` patch ([ea608e0](https://github.com/crimera/piko/commit/ea608e09b7b0775f38ca1914503e1b54dc7b73f9))

## [1.44.1-dev.2](https://github.com/crimera/piko/compare/v1.44.1-dev.1...v1.44.1-dev.2) (2024-11-11)

### Perfomance

* **Bring back twitter:** Optimize string replacement ([#440](https://github.com/crimera/piko/issues/440)) ([7e61023](https://github.com/crimera/piko/commit/7e6102351d9488f89489f87df2ec45d6d0d178d8))

## [1.44.1-dev.1](https://github.com/crimera/piko/compare/v1.44.0...v1.44.1-dev.1) (2024-11-09)

### Bug Fixes

* **Twitter:** Share menu button add hook in/from 10.67 ([a640946](https://github.com/crimera/piko/commit/a640946e7045be359535ed0eead00ec80d2e10c4))

### Refactors

* **Twitter:** refactor values of list preference ([e0e5c5b](https://github.com/crimera/piko/commit/e0e5c5b8639efcd5eecd6476bc59b7f125d5b851))

## [1.44.0](https://github.com/crimera/piko/compare/v1.43.0...v1.44.0) (2024-11-06)

### Features

* **Twitter:** Added `Custom translator` patch ([737fb9e](https://github.com/crimera/piko/commit/737fb9eaa0b8da7f020b93c314f5da60f961e345))

### Refactors

* **Twitter:** Change translator icon ([bf5f027](https://github.com/crimera/piko/commit/bf5f027f80507cf0db5b3dfd208d26b131987eca))
* **Twitter:** refactor `Custom downloader` patch ([42cc752](https://github.com/crimera/piko/commit/42cc752c599f978f455f84ae9a917f7bb457af98))
* **Twitter:** Refactor `Custom downloader` patch ([1e966f7](https://github.com/crimera/piko/commit/1e966f7f519b7d947e0b516f8bc0ca0c55c1d623))
* **Twitter:** Refactor hooks filename ([2daa292](https://github.com/crimera/piko/commit/2daa2922c7225711fd1aa5d0556493e068026e0a))
* **Twitter:** renamed 'Share menu button' fingerprints to hooks ([395c221](https://github.com/crimera/piko/commit/395c2217fc5f5b18cdc76568c3845140f76940d6))

## [1.44.0-dev.1](https://github.com/crimera/piko/compare/v1.43.0...v1.44.0-dev.1) (2024-11-02)

### Features

* **Twitter:** Added `Custom translator` patch ([737fb9e](https://github.com/crimera/piko/commit/737fb9eaa0b8da7f020b93c314f5da60f961e345))

### Refactors

* **Twitter:** Change translator icon ([bf5f027](https://github.com/crimera/piko/commit/bf5f027f80507cf0db5b3dfd208d26b131987eca))
* **Twitter:** refactor `Custom downloader` patch ([42cc752](https://github.com/crimera/piko/commit/42cc752c599f978f455f84ae9a917f7bb457af98))
* **Twitter:** Refactor `Custom downloader` patch ([1e966f7](https://github.com/crimera/piko/commit/1e966f7f519b7d947e0b516f8bc0ca0c55c1d623))
* **Twitter:** Refactor hooks filename ([2daa292](https://github.com/crimera/piko/commit/2daa2922c7225711fd1aa5d0556493e068026e0a))
* **Twitter:** renamed 'Share menu button' fingerprints to hooks ([395c221](https://github.com/crimera/piko/commit/395c2217fc5f5b18cdc76568c3845140f76940d6))

## [1.43.0](https://github.com/crimera/piko/compare/v1.42.1...v1.43.0) (2024-10-29)

### Bug Fixes

* Remove debug prints ([33e4d08](https://github.com/crimera/piko/commit/33e4d08a58451217781914953c1ffb711ea8480b))

### Features

* **Customize side bar items:** Allow for hiding of the "Jobs" item ([14ce811](https://github.com/crimera/piko/commit/14ce811a7b4d885fa6779524aad0b41034a2d8d0))

## [1.43.0-dev.1](https://github.com/crimera/piko/compare/v1.42.2-dev.1...v1.43.0-dev.1) (2024-10-26)

### Features

* **Customize side bar items:** Allow for hiding of the "Jobs" item ([14ce811](https://github.com/crimera/piko/commit/14ce811a7b4d885fa6779524aad0b41034a2d8d0))

## [1.42.2-dev.1](https://github.com/crimera/piko/compare/v1.42.1...v1.42.2-dev.1) (2024-10-26)

### Bug Fixes

* Remove debug prints ([33e4d08](https://github.com/crimera/piko/commit/33e4d08a58451217781914953c1ffb711ea8480b))

## [1.42.1](https://github.com/crimera/piko/compare/v1.42.0...v1.42.1) (2024-10-24)

### Bug Fixes

* **Bring back twitter:** Change the character "𝕏" into "Twitter". ([#441](https://github.com/crimera/piko/issues/441)) ([c20e16f](https://github.com/crimera/piko/commit/c20e16f80499df75176e9080f156e529f4f7d326))
* **Custom download folder:** Restore compatibility with versions prior to 10.64 ([b88b00b](https://github.com/crimera/piko/commit/b88b00ba97632d62517c0e52a924c453ea022d7a))
* **Custom downloader:** Improve fingerprint ([2a2a4a4](https://github.com/crimera/piko/commit/2a2a4a4e2cdc6587c5c3b91bb3888918d7cd720d))
* Resource strings getting corrupted, resulting on failure to patch on revanced manager ([#439](https://github.com/crimera/piko/issues/439)) ([1763137](https://github.com/crimera/piko/commit/1763137126b9a95df51fe23ce816853ccf0a8f44))
* **Twitter:** Add support for version 10.64.0-beta.1 ([c81378d](https://github.com/crimera/piko/commit/c81378d80275465acbf9caed5f48235b56395d56))

### Refactors

* **Custom Downloader:** Improve getting of tweet method declarations ([5237e49](https://github.com/crimera/piko/commit/5237e496140cc94fb9beb4a72057d927527dd78b))

## [1.42.1-dev.6](https://github.com/crimera/piko/compare/v1.42.1-dev.5...v1.42.1-dev.6) (2024-10-23)

### Bug Fixes

* **Custom downloader:** Improve fingerprint ([2a2a4a4](https://github.com/crimera/piko/commit/2a2a4a4e2cdc6587c5c3b91bb3888918d7cd720d))

## [1.42.1-dev.5](https://github.com/crimera/piko/compare/v1.42.1-dev.4...v1.42.1-dev.5) (2024-10-22)

### Bug Fixes

* **Bring back twitter:** Change the character "𝕏" into "Twitter". ([#441](https://github.com/crimera/piko/issues/441)) ([c20e16f](https://github.com/crimera/piko/commit/c20e16f80499df75176e9080f156e529f4f7d326))

## [1.42.1-dev.4](https://github.com/crimera/piko/compare/v1.42.1-dev.3...v1.42.1-dev.4) (2024-10-21)

### Bug Fixes

* **Custom download folder:** Restore compatibility with versions prior to 10.64 ([b88b00b](https://github.com/crimera/piko/commit/b88b00ba97632d62517c0e52a924c453ea022d7a))

## [1.42.1-dev.3](https://github.com/crimera/piko/compare/v1.42.1-dev.2...v1.42.1-dev.3) (2024-10-20)

### Bug Fixes

* Resource strings getting corrupted, resulting on failure to patch on revanced manager ([#439](https://github.com/crimera/piko/issues/439)) ([1763137](https://github.com/crimera/piko/commit/1763137126b9a95df51fe23ce816853ccf0a8f44))

## [1.42.1-dev.2](https://github.com/crimera/piko/compare/v1.42.1-dev.1...v1.42.1-dev.2) (2024-10-20)

### Refactors

* **Custom Downloader:** Improve getting of tweet method declarations ([5237e49](https://github.com/crimera/piko/commit/5237e496140cc94fb9beb4a72057d927527dd78b))

## [1.42.1-dev.1](https://github.com/crimera/piko/compare/v1.42.0...v1.42.1-dev.1) (2024-10-20)

### Bug Fixes

* **Twitter:** Add support for version 10.64.0-beta.1 ([c81378d](https://github.com/crimera/piko/commit/c81378d80275465acbf9caed5f48235b56395d56))

## [1.42.0](https://github.com/crimera/piko/compare/v1.41.0...v1.42.0) (2024-10-15)

### Bug Fixes

* **Remove Google Ad:** Include ads in the comments ([79d6e35](https://github.com/crimera/piko/commit/79d6e35144ffc6c81d3bb36499fc2c22e5a089ac))

### Features

* **Bring back twitter:** Add Japanese ([4eb08b5](https://github.com/crimera/piko/commit/4eb08b554eaf92958e0e159647fc71b35340e2b2))

### Updates

* **Translations:** Update `Japanese` ([b326d10](https://github.com/crimera/piko/commit/b326d10599db407e1e922ac8d6d2694a67c6e42b))

## [1.42.0-dev.2](https://github.com/crimera/piko/compare/v1.42.0-dev.1...v1.42.0-dev.2) (2024-10-15)

### Bug Fixes

* **Remove Google Ad:** Include ads in the comments ([79d6e35](https://github.com/crimera/piko/commit/79d6e35144ffc6c81d3bb36499fc2c22e5a089ac))

## [1.42.0-dev.1](https://github.com/crimera/piko/compare/v1.41.1-dev.1...v1.42.0-dev.1) (2024-10-13)

### Features

* **Bring back twitter:** Add Japanese ([4eb08b5](https://github.com/crimera/piko/commit/4eb08b554eaf92958e0e159647fc71b35340e2b2))

## [1.41.1-dev.1](https://github.com/crimera/piko/compare/v1.41.0...v1.41.1-dev.1) (2024-10-09)

### Updates

* **Translations:** Update `Japanese` ([b326d10](https://github.com/crimera/piko/commit/b326d10599db407e1e922ac8d6d2694a67c6e42b))

## [1.41.0](https://github.com/crimera/piko/compare/v1.40.0...v1.41.0) (2024-10-03)

### Bug Fixes

* **Twitter:** Fix `Hide nudge button` patch ([d7572fe](https://github.com/crimera/piko/commit/d7572feac66d44f0fe669073ee09bc09637adb9a))

### Features

* **Twitter:** Added `Hide followed by context` patch ([261e1d0](https://github.com/crimera/piko/commit/261e1d0156702a4a72105b430d50e59e88795198))

## [1.41.0-dev.1](https://github.com/crimera/piko/compare/v1.40.0...v1.41.0-dev.1) (2024-09-27)

### Bug Fixes

* **Twitter:** Fix `Hide nudge button` patch ([d7572fe](https://github.com/crimera/piko/commit/d7572feac66d44f0fe669073ee09bc09637adb9a))

### Features

* **Twitter:** Added `Hide followed by context` patch ([261e1d0](https://github.com/crimera/piko/commit/261e1d0156702a4a72105b430d50e59e88795198))

## [1.40.0](https://github.com/crimera/piko/compare/v1.39.3...v1.40.0) (2024-09-25)

### Features

* **Twitter:** Added `Hide nudge button` patch ([ad27484](https://github.com/crimera/piko/commit/ad27484f4cc0d727cf07cb4f202c8ddd6b582d67))

## [1.40.0-dev.1](https://github.com/crimera/piko/compare/v1.39.3...v1.40.0-dev.1) (2024-09-25)

### Features

* **Twitter:** Added `Hide nudge button` patch ([ad27484](https://github.com/crimera/piko/commit/ad27484f4cc0d727cf07cb4f202c8ddd6b582d67))

## [1.39.3](https://github.com/crimera/piko/compare/v1.39.2...v1.39.3) (2024-09-11)

### Updates

* **Translations:** Update translations ([9686863](https://github.com/crimera/piko/commit/968686390263fa700488deb5d87085470267c7cf))

## [1.39.2](https://github.com/crimera/piko/compare/v1.39.1...v1.39.2) (2024-09-09)

### Bug Fixes

* **Twitter:** fix `Control video auto scroll` in 10.57 ([b7ae78c](https://github.com/crimera/piko/commit/b7ae78cd61b2f75d266e3fe3d57c5c21cd798e79))
* **Twitter:** fix `Enable app icon settings` patch ([c2b6f79](https://github.com/crimera/piko/commit/c2b6f79b5c833c2441432b2511374361318e5507))
* **Twitter:** fix video entity hook in `10.58` ([e8a833d](https://github.com/crimera/piko/commit/e8a833d2123b92a40caf68b4f6798e822a6d1737))

## [1.39.2-dev.2](https://github.com/crimera/piko/compare/v1.39.2-dev.1...v1.39.2-dev.2) (2024-09-09)

### Bug Fixes

* **Twitter:** fix `Enable app icon settings` patch ([c2b6f79](https://github.com/crimera/piko/commit/c2b6f79b5c833c2441432b2511374361318e5507))
* **Twitter:** fix video entity hook in `10.58` ([e8a833d](https://github.com/crimera/piko/commit/e8a833d2123b92a40caf68b4f6798e822a6d1737))

## [1.39.2-dev.1](https://github.com/crimera/piko/compare/v1.39.1...v1.39.2-dev.1) (2024-09-05)

### Bug Fixes

* **Twitter:** fix `Control video auto scroll` in 10.57 ([b7ae78c](https://github.com/crimera/piko/commit/b7ae78cd61b2f75d266e3fe3d57c5c21cd798e79))

## [1.39.1](https://github.com/crimera/piko/compare/v1.39.0...v1.39.1) (2024-08-30)

### Bug Fixes

* Remove duplicate strings ([94c6642](https://github.com/crimera/piko/commit/94c66422e0ced2cbdfd820dfee60cedd2c68e636))

## [1.39.0](https://github.com/crimera/piko/compare/v1.38.0...v1.39.0) (2024-08-30)

### Features

* **Twitter:** Custom deeplinks ([325a281](https://github.com/crimera/piko/commit/325a281225b1379b310d8ba14629bdbae0b314e5))

### Updates

* **Twitter:** Added strings ([fad9b1a](https://github.com/crimera/piko/commit/fad9b1a9fd79b164c713fe35b61bad8ed3d9373b))
* **Twitter:** Change Quick Btn path ([7febdf6](https://github.com/crimera/piko/commit/7febdf612212ba61638fd818121c8cc4b9db8ed8))
* **Twitter:** Updated strings ([3cb159e](https://github.com/crimera/piko/commit/3cb159e57d66e22b7c2f638e5d0b7422a4ef2a10))

## [1.39.0-dev.1](https://github.com/crimera/piko/compare/v1.38.0...v1.39.0-dev.1) (2024-08-25)

### Features

* **Twitter:** Custom deeplinks ([325a281](https://github.com/crimera/piko/commit/325a281225b1379b310d8ba14629bdbae0b314e5))

### Updates

* **Twitter:** Added strings ([fad9b1a](https://github.com/crimera/piko/commit/fad9b1a9fd79b164c713fe35b61bad8ed3d9373b))
* **Twitter:** Change Quick Btn path ([7febdf6](https://github.com/crimera/piko/commit/7febdf612212ba61638fd818121c8cc4b9db8ed8))
* **Twitter:** Updated strings ([3cb159e](https://github.com/crimera/piko/commit/3cb159e57d66e22b7c2f638e5d0b7422a4ef2a10))

## [1.38.0](https://github.com/crimera/piko/compare/v1.37.1...v1.38.0) (2024-08-20)

### Bug Fixes

* **Feature Flags:** Use ListView instead of RecyclerView for a more reliable list. ([6f16b7f](https://github.com/crimera/piko/commit/6f16b7f7d61c81e7a21394f74b6e0d0e80ed9a55))

### Features

* **Twitter:** Added `Enable force HD videos` patch ([d316612](https://github.com/crimera/piko/commit/d3166121ac39adf45440e6888b43487e398f61e6))

### Updates

* **Twitter:** Updated `Enable force HD videos` patch description ([86ea041](https://github.com/crimera/piko/commit/86ea0418d3606b6e04f8fbea8fc530aba5316f0f))

## [1.38.0-dev.1](https://github.com/crimera/piko/compare/v1.37.2-dev.1...v1.38.0-dev.1) (2024-08-18)

### Features

* **Twitter:** Added `Enable force HD videos` patch ([d316612](https://github.com/crimera/piko/commit/d3166121ac39adf45440e6888b43487e398f61e6))

### Updates

* **Twitter:** Updated `Enable force HD videos` patch description ([86ea041](https://github.com/crimera/piko/commit/86ea0418d3606b6e04f8fbea8fc530aba5316f0f))

## [1.37.2-dev.1](https://github.com/crimera/piko/compare/v1.37.1...v1.37.2-dev.1) (2024-08-18)

### Bug Fixes

* **Feature Flags:** Use ListView instead of RecyclerView for a more reliable list. ([6f16b7f](https://github.com/crimera/piko/commit/6f16b7f7d61c81e7a21394f74b6e0d0e80ed9a55))

## [1.37.1](https://github.com/crimera/piko/compare/v1.37.0...v1.37.1) (2024-08-17)

### Updates

* **Twitter:** Added hide 'Settings and privacy' in `Customize side bar items` ([cb01885](https://github.com/crimera/piko/commit/cb018852c8af8caaf4b986444208ab00a39eab41))

## [1.37.1-dev.1](https://github.com/crimera/piko/compare/v1.37.0...v1.37.1-dev.1) (2024-08-16)

### Updates

* **Twitter:** Added hide 'Settings and privacy' in `Customize side bar items` ([cb01885](https://github.com/crimera/piko/commit/cb018852c8af8caaf4b986444208ab00a39eab41))

## [1.37.0](https://github.com/crimera/piko/compare/v1.36.0...v1.37.0) (2024-08-15)

### Features

* Enable recent patches by default ([e84f358](https://github.com/crimera/piko/commit/e84f358e494830fdecdc3dd12662b5f5ccd644b3))
* Quick settings button is now optional ([e9e54e5](https://github.com/crimera/piko/commit/e9e54e5e44437b92db9700c363f0abd9117cc325))

### Updates

* Improve quick settings appearance ([6dc27b4](https://github.com/crimera/piko/commit/6dc27b4a888d0028e455be1e1b41b83cdd7f7940))

## [1.37.0-dev.3](https://github.com/crimera/piko/compare/v1.37.0-dev.2...v1.37.0-dev.3) (2024-08-15)

### Features

* Quick settings button is now optional ([e9e54e5](https://github.com/crimera/piko/commit/e9e54e5e44437b92db9700c363f0abd9117cc325))

## [1.37.0-dev.2](https://github.com/crimera/piko/compare/v1.37.0-dev.1...v1.37.0-dev.2) (2024-08-14)

### Updates

* Improve quick settings appearance ([6dc27b4](https://github.com/crimera/piko/commit/6dc27b4a888d0028e455be1e1b41b83cdd7f7940))

## [1.37.0-dev.1](https://github.com/crimera/piko/compare/v1.36.0...v1.37.0-dev.1) (2024-08-13)

### Features

* Enable recent patches by default ([e84f358](https://github.com/crimera/piko/commit/e84f358e494830fdecdc3dd12662b5f5ccd644b3))

## [1.36.0](https://github.com/crimera/piko/compare/v1.35.0...v1.36.0) (2024-08-12)

### Features

* **Twitter:** Added `Customize reply sort filter` patch ([121b8a6](https://github.com/crimera/piko/commit/121b8a6e9345ea462ad9cb06f7b20f863fc08d4a))
* **Twitter:** Added Remember filter for `Customize reply sort filter` ([24df398](https://github.com/crimera/piko/commit/24df3987e5008aeca52c11a8b20714766cdafff1))

### Updates

* **Twitter:** updated string ([60afc68](https://github.com/crimera/piko/commit/60afc683c4be02ba4d5cdc92879aad2e6755b14b))

## [1.36.0-dev.1](https://github.com/crimera/piko/compare/v1.35.0...v1.36.0-dev.1) (2024-08-11)

### Features

* **Twitter:** Added `Customize reply sort filter` patch ([121b8a6](https://github.com/crimera/piko/commit/121b8a6e9345ea462ad9cb06f7b20f863fc08d4a))
* **Twitter:** Added Remember filter for `Customize reply sort filter` ([24df398](https://github.com/crimera/piko/commit/24df3987e5008aeca52c11a8b20714766cdafff1))

### Updates

* **Twitter:** updated string ([60afc68](https://github.com/crimera/piko/commit/60afc683c4be02ba4d5cdc92879aad2e6755b14b))

## [1.35.0](https://github.com/crimera/piko/compare/v1.34.1...v1.35.0) (2024-08-10)

### Bug Fixes

* **Twitter:** fix `Remove videos for you` patch ([b19f943](https://github.com/crimera/piko/commit/b19f943ee8a19ea158e89cc031ae037e43da6e50))

### Features

* **Hook feature flag:** Added ability to search for supported flags. ([28a2c0a](https://github.com/crimera/piko/commit/28a2c0a6ca97fa50711ce44cfa1f20b5147e906f))
* **Twitter:** Added `Remove videos for you` patch ([25934a2](https://github.com/crimera/piko/commit/25934a2b58a8eefc05d4313b623580371d964a12))

### Updates

* **Hook feature flag:** Improve flag matching in search. ([d7f0827](https://github.com/crimera/piko/commit/d7f0827218a9501a0b2e061990ce60a0af18ba0e))

### Refactors

* **Twitter:** Added quick settings button in side drawer ([c0152e3](https://github.com/crimera/piko/commit/c0152e327e8b2e3fa2ad57d63c35738d4575cc98))

## [1.35.0-dev.3](https://github.com/crimera/piko/compare/v1.35.0-dev.2...v1.35.0-dev.3) (2024-08-10)

### Bug Fixes

* **Twitter:** fix `Remove videos for you` patch ([b19f943](https://github.com/crimera/piko/commit/b19f943ee8a19ea158e89cc031ae037e43da6e50))

### Features

* **Twitter:** Added `Remove videos for you` patch ([25934a2](https://github.com/crimera/piko/commit/25934a2b58a8eefc05d4313b623580371d964a12))

### Refactors

* **Twitter:** Added quick settings button in side drawer ([c0152e3](https://github.com/crimera/piko/commit/c0152e327e8b2e3fa2ad57d63c35738d4575cc98))

## [1.35.0-dev.2](https://github.com/crimera/piko/compare/v1.35.0-dev.1...v1.35.0-dev.2) (2024-08-09)

### Updates

* **Hook feature flag:** Improve flag matching in search. ([d7f0827](https://github.com/crimera/piko/commit/d7f0827218a9501a0b2e061990ce60a0af18ba0e))

## [1.35.0-dev.1](https://github.com/crimera/piko/compare/v1.34.1...v1.35.0-dev.1) (2024-08-09)

### Features

* **Hook feature flag:** Added ability to search for supported flags. ([28a2c0a](https://github.com/crimera/piko/commit/28a2c0a6ca97fa50711ce44cfa1f20b5147e906f))

## [1.34.1](https://github.com/crimera/piko/compare/v1.34.0...v1.34.1) (2024-08-06)

### Bug Fixes

* **Twitter:** Fix aapt breakage due to tag mismatch ([730a51c](https://github.com/crimera/piko/commit/730a51c20f464bbe2b680004721161dd5f34dadf))

## [1.34.0](https://github.com/crimera/piko/compare/v1.33.0...v1.34.0) (2024-08-06)

### Bug Fixes

* **Bring back twitter:** Change x icon to legacy twitter. ([3006f47](https://github.com/crimera/piko/commit/3006f47d973ffde1622ae1544590a11e923c68ab))
* **Twitter:** `Customize Navigation Bar items` in/from `10.53.0-beta.1` ([e8154fb](https://github.com/crimera/piko/commit/e8154fb0ffa91e6d3ed2812bed3f71a034f3bc01))
* **Twitter:** Fix `Remove premium upsell` patch ([4df5e32](https://github.com/crimera/piko/commit/4df5e32088eb57702c5f30e20b3c75d2ec11ee87))

### Features

* **Twitter:** Added `Remove main event` patch ([806598d](https://github.com/crimera/piko/commit/806598d7154c538fb238f3d9b186746cb8c956bf))

### Updates

* **Translations:** Translation updates ([4bf8a8d](https://github.com/crimera/piko/commit/4bf8a8dd954a7013a65ceee9d39521fd445d069d))

### Refactors

* **Twitter:** Seperated/Added `Remove superhero event` patch ([d60ee17](https://github.com/crimera/piko/commit/d60ee17294b311bb964e3cc6200465218d7baca3))

## [1.34.0-dev.2](https://github.com/crimera/piko/compare/v1.34.0-dev.1...v1.34.0-dev.2) (2024-08-05)

### Updates

* **Translations:** Translation updates ([4bf8a8d](https://github.com/crimera/piko/commit/4bf8a8dd954a7013a65ceee9d39521fd445d069d))

## [1.34.0-dev.1](https://github.com/crimera/piko/compare/v1.33.1-dev.2...v1.34.0-dev.1) (2024-08-03)

### Bug Fixes

* **Twitter:** `Customize Navigation Bar items` in/from `10.53.0-beta.1` ([e8154fb](https://github.com/crimera/piko/commit/e8154fb0ffa91e6d3ed2812bed3f71a034f3bc01))

### Features

* **Twitter:** Added `Remove main event` patch ([806598d](https://github.com/crimera/piko/commit/806598d7154c538fb238f3d9b186746cb8c956bf))

### Refactors

* **Twitter:** Seperated/Added `Remove superhero event` patch ([d60ee17](https://github.com/crimera/piko/commit/d60ee17294b311bb964e3cc6200465218d7baca3))

## [1.33.1-dev.2](https://github.com/crimera/piko/compare/v1.33.1-dev.1...v1.33.1-dev.2) (2024-08-03)

### Bug Fixes

* **Bring back twitter:** Change x icon to legacy twitter. ([3006f47](https://github.com/crimera/piko/commit/3006f47d973ffde1622ae1544590a11e923c68ab))

## [1.33.1-dev.1](https://github.com/crimera/piko/compare/v1.33.0...v1.33.1-dev.1) (2024-08-02)

### Bug Fixes

* **Twitter:** Fix `Remove premium upsell` patch ([4df5e32](https://github.com/crimera/piko/commit/4df5e32088eb57702c5f30e20b3c75d2ec11ee87))

## [1.33.0](https://github.com/crimera/piko/compare/v1.32.0...v1.33.0) (2024-07-31)

### Features

* **Twitter:** Added `Control video auto scroll` patch ([7a21924](https://github.com/crimera/piko/commit/7a21924dc7f30a15feef1797b0416c91de27d1e4))
* **Twitter:** Added `Enable PiP mode automatically` patch ([59d6b67](https://github.com/crimera/piko/commit/59d6b6723cbff6fa96218fb6210e2cb0b8bc1338))
* **Twitter:** Added `Remove premium upsell` patch ([37525de](https://github.com/crimera/piko/commit/37525de868c38254cbfd20b58d4fd077b0c14ca2))

### Updates

* Add spanish to languages ([e9b8efd](https://github.com/crimera/piko/commit/e9b8efdb3570fa516f06a736f2709d1c166df1bf))

### Refactors

* **Twitter:** More strings refactor ([d5a629f](https://github.com/crimera/piko/commit/d5a629f39bc4c15be1914a6ab98034129ed53f64))
* **Twitter:** Strings refactor ([2db8866](https://github.com/crimera/piko/commit/2db88663e70745ef81024d896ad5204c29b1ba06))

## [1.33.0-dev.1](https://github.com/crimera/piko/compare/v1.32.1-dev.1...v1.33.0-dev.1) (2024-07-30)

### Features

* **Twitter:** Added `Control video auto scroll` patch ([7a21924](https://github.com/crimera/piko/commit/7a21924dc7f30a15feef1797b0416c91de27d1e4))
* **Twitter:** Added `Enable PiP mode automatically` patch ([59d6b67](https://github.com/crimera/piko/commit/59d6b6723cbff6fa96218fb6210e2cb0b8bc1338))
* **Twitter:** Added `Remove premium upsell` patch ([37525de](https://github.com/crimera/piko/commit/37525de868c38254cbfd20b58d4fd077b0c14ca2))

### Refactors

* **Twitter:** More strings refactor ([d5a629f](https://github.com/crimera/piko/commit/d5a629f39bc4c15be1914a6ab98034129ed53f64))
* **Twitter:** Strings refactor ([2db8866](https://github.com/crimera/piko/commit/2db88663e70745ef81024d896ad5204c29b1ba06))

## [1.32.1-dev.1](https://github.com/crimera/piko/compare/v1.32.0...v1.32.1-dev.1) (2024-07-26)

### Updates

* Add spanish to languages ([e9b8efd](https://github.com/crimera/piko/commit/e9b8efdb3570fa516f06a736f2709d1c166df1bf))

## [1.32.0](https://github.com/crimera/piko/compare/v1.31.2...v1.32.0) (2024-07-17)


### Bug Fixes

* **Twitter:** Missing download option in immersive view ([6b0aa2c](https://github.com/crimera/piko/commit/6b0aa2c0d8d0f9192630e42abef418a834a95df0))


### Features

* **Twitter:** Added `Custom downloader` ([3d5941e](https://github.com/crimera/piko/commit/3d5941e226c1fb9402edeeb138c1c974026e0598))


### Updates

* Russian translation ([42b68e9](https://github.com/crimera/piko/commit/42b68e96fccca505a60d6eb2682cbfb7f7acfb17))

## [1.32.0-dev.3](https://github.com/crimera/piko/compare/v1.32.0-dev.2...v1.32.0-dev.3) (2024-07-13)


### Updates

* Russian translation ([42b68e9](https://github.com/crimera/piko/commit/42b68e96fccca505a60d6eb2682cbfb7f7acfb17))

## [1.32.0-dev.2](https://github.com/crimera/piko/compare/v1.32.0-dev.1...v1.32.0-dev.2) (2024-07-12)


### Bug Fixes

* **Twitter:** Missing download option in immersive view ([6b0aa2c](https://github.com/crimera/piko/commit/6b0aa2c0d8d0f9192630e42abef418a834a95df0))

## [1.32.0-dev.1](https://github.com/crimera/piko/compare/v1.31.2...v1.32.0-dev.1) (2024-07-06)


### Features

* **Twitter:** Added `Custom downloader` ([3d5941e](https://github.com/crimera/piko/commit/3d5941e226c1fb9402edeeb138c1c974026e0598))

## [1.31.2](https://github.com/crimera/piko/compare/v1.31.1...v1.31.2) (2024-06-25)


### Updates

* Translation updates ([509dfbb](https://github.com/crimera/piko/commit/509dfbb9e92511379406979a19b131a685229fc6))

## [1.31.2-dev.1](https://github.com/crimera/piko/compare/v1.31.1...v1.31.2-dev.1) (2024-06-25)


### Updates

* Translation updates ([509dfbb](https://github.com/crimera/piko/commit/509dfbb9e92511379406979a19b131a685229fc6))

## [1.31.1](https://github.com/crimera/piko/compare/v1.31.0...v1.31.1) (2024-06-11)


### Updates

* Improved the hooking of the recyclerview methods. also fixes the crash when opening feature flags. ([25e5bc4](https://github.com/crimera/piko/commit/25e5bc422c3893747b8fadd0744833a4ecb7b6ea))

## [1.31.1-dev.1](https://github.com/crimera/piko/compare/v1.31.0...v1.31.1-dev.1) (2024-06-11)


### Updates

* Improved the hooking of the recyclerview methods. also fixes the crash when opening feature flags. ([25e5bc4](https://github.com/crimera/piko/commit/25e5bc422c3893747b8fadd0744833a4ecb7b6ea))

## [1.31.0](https://github.com/crimera/piko/compare/v1.30.2...v1.31.0) (2024-06-11)


### Bug Fixes

* Fix crash when opening feature flags ([2b1484d](https://github.com/crimera/piko/commit/2b1484d6c291a997283d98358cff98b0195acfed))


### Features

* **all:** Added `Export all activities` patch from ReVanced ([7cc405f](https://github.com/crimera/piko/commit/7cc405f7e900b85aaa172e8693cd4ca2e790451d))
* **Twitter:** Added `Delete from database` patch ([d785660](https://github.com/crimera/piko/commit/d7856602786d3fb9d7a8721f05d80be97bc4fd3b))
* **Twitter:** Added tab redirects hook (for Bookmark Navbar) ([f01b418](https://github.com/crimera/piko/commit/f01b418fcc13e9fe54d845eab3bea1cd62687f9f))


### Updates

* Enable `Customize profile tabs` patch by default ([a8543bf](https://github.com/crimera/piko/commit/a8543bf0d2eebe8a1580870b95c75a890e845ff4))
* Enable `Hide Live Threads` patch by default ([b9862b5](https://github.com/crimera/piko/commit/b9862b50b97741721448c0ea667769b0024961c9))


### Refactors

* **Twitter:** Major string changes ([f22fdf0](https://github.com/crimera/piko/commit/f22fdf086184f71447d2696461d76914dbef731d))

## [1.31.0-dev.6](https://github.com/crimera/piko/compare/v1.31.0-dev.5...v1.31.0-dev.6) (2024-06-11)


### Updates

* **Translations:** Update polish translations ([6200d65](https://github.com/crimera/piko/commit/6200d657b5db4040f10ec4f82054ad440b09221c))

## [1.31.0-dev.5](https://github.com/crimera/piko/compare/v1.31.0-dev.4...v1.31.0-dev.5) (2024-06-11)


### Bug Fixes

* Fix crash when opening feature flags ([2b1484d](https://github.com/crimera/piko/commit/2b1484d6c291a997283d98358cff98b0195acfed))


### Updates

* Enable `Hide Live Threads` patch by default ([b9862b5](https://github.com/crimera/piko/commit/b9862b50b97741721448c0ea667769b0024961c9))

## [1.31.0-dev.4](https://github.com/crimera/piko/compare/v1.31.0-dev.3...v1.31.0-dev.4) (2024-06-09)


### Features

* **Twitter:** Added tab redirects hook (for Bookmark Navbar) ([f01b418](https://github.com/crimera/piko/commit/f01b418fcc13e9fe54d845eab3bea1cd62687f9f))

## [1.31.0-dev.3](https://github.com/crimera/piko/compare/v1.31.0-dev.2...v1.31.0-dev.3) (2024-06-06)


### Features

* **all:** Added `Export all activities` patch from ReVanced ([7cc405f](https://github.com/crimera/piko/commit/7cc405f7e900b85aaa172e8693cd4ca2e790451d))

## [1.31.0-dev.2](https://github.com/crimera/piko/compare/v1.31.0-dev.1...v1.31.0-dev.2) (2024-06-04)


### Refactors

* **Twitter:** Major string changes ([f22fdf0](https://github.com/crimera/piko/commit/f22fdf086184f71447d2696461d76914dbef731d))

## [1.31.0-dev.1](https://github.com/crimera/piko/compare/v1.30.1...v1.31.0-dev.1) (2024-06-02)


### Features

* **Twitter:** Added `Delete from database` patch ([d785660](https://github.com/crimera/piko/commit/d7856602786d3fb9d7a8721f05d80be97bc4fd3b))

### Updates

* Enable `Customize profile tabs` patch by default ([a8543bf](https://github.com/crimera/piko/commit/a8543bf0d2eebe8a1580870b95c75a890e845ff4))

## [1.30.1](https://github.com/crimera/piko/compare/v1.30.0...v1.30.1) (2024-06-01)


### Bug Fixes

* tweet highlight color on hold ([#243](https://github.com/crimera/piko/issues/243)) ([b22ea42](https://github.com/crimera/piko/commit/b22ea426830b250abaa336e3b158dea34f82f6d6))
* **Twitter:** Fix `Customize profile tabs` crash after `10.43` ([a38d621](https://github.com/crimera/piko/commit/a38d6213e06404fde21f6b5b8eddfcdfe8edd73e))


### Updates

* Translations ([d3ae8ec](https://github.com/crimera/piko/commit/d3ae8ec55dced8d7d09146c224d332f72414382a))
* **Translations:** update simplified chinese translation ([3e5a8fd](https://github.com/crimera/piko/commit/3e5a8fdd07a7f7646e860a76fc81165b67b211bf))


### Refactors

* **Twitter:** Added `No shortened URL` as a settings toggle ([ea33a30](https://github.com/crimera/piko/commit/ea33a308c277e3e40e085112e970406cee64f0cd))
* **Twitter:** Renamed patch `Remove Buy Premium Banner` to `Remove message prompts Banner` ([45dbad8](https://github.com/crimera/piko/commit/45dbad85abb22e0ab74d1dd0bcd977c4aeef3349))

## [1.30.1-dev.4](https://github.com/crimera/piko/compare/v1.30.1-dev.3...v1.30.1-dev.4) (2024-06-01)


### Bug Fixes

* **Twitter:** Fix `Customize profile tabs` crash after `10.43` ([a38d621](https://github.com/crimera/piko/commit/a38d6213e06404fde21f6b5b8eddfcdfe8edd73e))


### Refactors

* **Twitter:** Added `No shortened URL` as a settings toggle ([ea33a30](https://github.com/crimera/piko/commit/ea33a308c277e3e40e085112e970406cee64f0cd))
* **Twitter:** Renamed patch `Remove Buy Premium Banner` to `Remove message prompts Banner` ([45dbad8](https://github.com/crimera/piko/commit/45dbad85abb22e0ab74d1dd0bcd977c4aeef3349))

## [1.30.1-dev.3](https://github.com/crimera/piko/compare/v1.30.1-dev.2...v1.30.1-dev.3) (2024-05-27)


### Updates

* Translations ([d3ae8ec](https://github.com/crimera/piko/commit/d3ae8ec55dced8d7d09146c224d332f72414382a))

## [1.30.1-dev.2](https://github.com/crimera/piko/compare/v1.30.1-dev.1...v1.30.1-dev.2) (2024-05-26)


### Bug Fixes

* tweet highlight color on hold ([#243](https://github.com/crimera/piko/issues/243)) ([b22ea42](https://github.com/crimera/piko/commit/b22ea426830b250abaa336e3b158dea34f82f6d6))

## [1.30.1-dev.1](https://github.com/crimera/piko/compare/v1.30.0...v1.30.1-dev.1) (2024-05-25)


### Updates

* **Translations:** update simplified chinese translation ([3e5a8fd](https://github.com/crimera/piko/commit/3e5a8fdd07a7f7646e860a76fc81165b67b211bf))

## [1.30.0](https://github.com/crimera/piko/compare/v1.29.0...v1.30.0) (2024-05-22)


### Bug Fixes

* URL decode path to JAR containing spaces by osumatrix ([e6671ba](https://github.com/crimera/piko/commit/e6671ba4a8359db7aefa1ab20600e1f17144e002))
* Use UrlDecoder API available in older Android versions ([d9e6374](https://github.com/crimera/piko/commit/d9e63740b7eb7c8a4f44abf6d59998ea14194346))


### Features

* Remove throw file ([86bff99](https://github.com/crimera/piko/commit/86bff993636928c67ca95dabeebcf1f5cc191a36))
* **Twitter:** Added `Customize Inline action Bar items` patch ([05b06f9](https://github.com/crimera/piko/commit/05b06f9fa4a183c9626acc7fda69c0bebdbae761))
* **Twitter:** Added `Debug Menu` patch ([7d5ca77](https://github.com/crimera/piko/commit/7d5ca77bf633a8dfcd8657d12b24bd076b2f5d11))
* **Twitter:** Added `Hide Buy Premium Banner` patch ([7ba6419](https://github.com/crimera/piko/commit/7ba64198f289ffac2e3d397e5bf0f1137ba2aa2b))
* **Twitter:** Added `Hide hidden replies` patch ([431acc4](https://github.com/crimera/piko/commit/431acc435675002c7d9f068b4fc3ebade2487db3))
* **Twitter:** Added `Round off numbers` patch ([b1c1b13](https://github.com/crimera/piko/commit/b1c1b1361b55f08e2eba64e086943b30b2dc1212))
* **Twitter:** Added ability to reset pref and flags ([4aaeb09](https://github.com/crimera/piko/commit/4aaeb09ce6c38c6513be6293140a7e7e0b796f28))


### Updates

* **Twitter:** Enable new patches by default ([19f9149](https://github.com/crimera/piko/commit/19f9149452fafae83584508fe7ae9d594fcf5543))


### Refactors

* **Twitter:** Combined `customize patch` into single class ([6c04aed](https://github.com/crimera/piko/commit/6c04aed5ec958cb86471d94dd1729c4ad7952c8d))
* **Twitter:** Force enable `Enable app icon settings` patch ([ee605f1](https://github.com/crimera/piko/commit/ee605f11f8c95e6b8cf5d1d1cf2357593e1ea318))

## [1.30.0-dev.8](https://github.com/crimera/piko/compare/v1.30.0-dev.7...v1.30.0-dev.8) (2024-05-22)


### Features

* Remove throw file ([86bff99](https://github.com/crimera/piko/commit/86bff993636928c67ca95dabeebcf1f5cc191a36))

## [1.30.0-dev.7](https://github.com/crimera/piko/compare/v1.30.0-dev.6...v1.30.0-dev.7) (2024-05-22)


### Bug Fixes

* URL decode path to JAR containing spaces by osumatrix ([e6671ba](https://github.com/crimera/piko/commit/e6671ba4a8359db7aefa1ab20600e1f17144e002))
* Use UrlDecoder API available in older Android versions ([d9e6374](https://github.com/crimera/piko/commit/d9e63740b7eb7c8a4f44abf6d59998ea14194346))

## [1.30.0-dev.6](https://github.com/crimera/piko/compare/v1.30.0-dev.5...v1.30.0-dev.6) (2024-05-22)


### Updates

* **Twitter:** Enable new patches by default ([19f9149](https://github.com/crimera/piko/commit/19f9149452fafae83584508fe7ae9d594fcf5543))

## [1.30.0-dev.5](https://github.com/crimera/piko/compare/v1.30.0-dev.4...v1.30.0-dev.5) (2024-05-22)


### Features

* **Twitter:** Added `Hide Buy Premium Banner` patch ([7ba6419](https://github.com/crimera/piko/commit/7ba64198f289ffac2e3d397e5bf0f1137ba2aa2b))
* **Twitter:** Added `Hide hidden replies` patch ([431acc4](https://github.com/crimera/piko/commit/431acc435675002c7d9f068b4fc3ebade2487db3))

## [1.30.0-dev.4](https://github.com/crimera/piko/compare/v1.30.0-dev.3...v1.30.0-dev.4) (2024-05-20)


### Features

* **Twitter:** Added `Debug Menu` patch ([7d5ca77](https://github.com/crimera/piko/commit/7d5ca77bf633a8dfcd8657d12b24bd076b2f5d11))

## [1.30.0-dev.3](https://github.com/crimera/piko/compare/v1.30.0-dev.2...v1.30.0-dev.3) (2024-05-19)


### Features

* **Twitter:** Added `Customize Inline action Bar items` patch ([05b06f9](https://github.com/crimera/piko/commit/05b06f9fa4a183c9626acc7fda69c0bebdbae761))


### Refactors

* **Twitter:** Combined `customize patch` into single class ([6c04aed](https://github.com/crimera/piko/commit/6c04aed5ec958cb86471d94dd1729c4ad7952c8d))

## [1.30.0-dev.2](https://github.com/crimera/piko/compare/v1.30.0-dev.1...v1.30.0-dev.2) (2024-05-18)


### Features

* **Twitter:** Added ability to reset pref and flags ([4aaeb09](https://github.com/crimera/piko/commit/4aaeb09ce6c38c6513be6293140a7e7e0b796f28))

## [1.30.0-dev.1](https://github.com/crimera/piko/compare/v1.29.0...v1.30.0-dev.1) (2024-05-18)


### Features

* **Twitter:** Added `Round off numbers` patch ([b1c1b13](https://github.com/crimera/piko/commit/b1c1b1361b55f08e2eba64e086943b30b2dc1212))


### Refactors

* **Twitter:** Force enable `Enable app icon settings` patch ([ee605f1](https://github.com/crimera/piko/commit/ee605f11f8c95e6b8cf5d1d1cf2357593e1ea318))

## [1.29.0](https://github.com/crimera/piko/compare/v1.28.0...v1.29.0) (2024-05-18)


### Features

* **ui:** app wide monet theming in light mode ([46f920b](https://github.com/crimera/piko/commit/46f920bd35841e3df85617aed6a439f0df114d7b))


### Refactors

* **Twitter:** Refactor `Enable app icon settings` patch ([e7b3d0e](https://github.com/crimera/piko/commit/e7b3d0e3bfdfb5fc7dff3564c021c04d7d2d564c))
* **Twitter:** Separated `App icon` and `Navigation icon` patch ([8a67f12](https://github.com/crimera/piko/commit/8a67f12e0430dbd06a6184a150ba75da832a2c54))

## [1.29.0-dev.1](https://github.com/crimera/piko/compare/v1.28.1-dev.2...v1.29.0-dev.1) (2024-05-17)


### Features

* **ui:** app wide monet theming in light mode ([46f920b](https://github.com/crimera/piko/commit/46f920bd35841e3df85617aed6a439f0df114d7b))

## [1.28.1-dev.2](https://github.com/crimera/piko/compare/v1.28.1-dev.1...v1.28.1-dev.2) (2024-05-17)


### Refactors

* **Twitter:** Refactor `Enable app icon settings` patch ([e7b3d0e](https://github.com/crimera/piko/commit/e7b3d0e3bfdfb5fc7dff3564c021c04d7d2d564c))

## [1.28.1-dev.1](https://github.com/crimera/piko/compare/v1.28.0...v1.28.1-dev.1) (2024-05-16)


### Refactors

* **Twitter:** Separated `App icon` and `Navigation icon` patch ([8a67f12](https://github.com/crimera/piko/commit/8a67f12e0430dbd06a6184a150ba75da832a2c54))

## [1.28.0](https://github.com/crimera/piko/compare/v1.27.1...v1.28.0) (2024-05-15)


### Features

* **Translations:** Update `japanese` ([444623f](https://github.com/crimera/piko/commit/444623faaa5761b33445705e04e5c3078ff88124))
* **Twitter:** Added `Customize Navigation Bar items` patch ([9c6f59c](https://github.com/crimera/piko/commit/9c6f59cf6f40cc4d4d61b13bf496f800f452c0c1))
* **Twitter:** Added `Customize side bar items` patch ([ca0d28f](https://github.com/crimera/piko/commit/ca0d28f96d65d7d7f300f6089029ec24e55802b4))
* **Twitter:** Added `Disable auto timeline scroll on launch` patch ([b8d431e](https://github.com/crimera/piko/commit/b8d431ea44a2b53f9065c226116d86ec1fb7b70f))


### Updates

* **Translations:** Update Brazilian Portuguese ([8745f61](https://github.com/crimera/piko/commit/8745f61c060a62152bd84ee510d835ac8346e01e))


### Refactors

* **Twitter:** Added missing patches names ([b3fda95](https://github.com/crimera/piko/commit/b3fda9558251eda91e6e3b43e994a9dbcdb1daac))

## [1.28.0-dev.5](https://github.com/crimera/piko/compare/v1.28.0-dev.4...v1.28.0-dev.5) (2024-05-15)


### Updates

* **Translations:** Improved & updates polish translations ([9bbb9f4](https://github.com/crimera/piko/commit/9bbb9f4ea8c5aa3fee0be0580a281dc967f7981a))

## [1.28.0-dev.4](https://github.com/crimera/piko/compare/v1.28.0-dev.3...v1.28.0-dev.4) (2024-05-13)


### Features

* **Twitter:** Added `Customize Navigation Bar items` patch ([9c6f59c](https://github.com/crimera/piko/commit/9c6f59cf6f40cc4d4d61b13bf496f800f452c0c1))

## [1.28.0-dev.3](https://github.com/crimera/piko/compare/v1.28.0-dev.2...v1.28.0-dev.3) (2024-05-12)


### Features

* **Twitter:** Added `Customize side bar items` patch ([ca0d28f](https://github.com/crimera/piko/commit/ca0d28f96d65d7d7f300f6089029ec24e55802b4))


### Refactors

* **Twitter:** Added missing patches names ([b3fda95](https://github.com/crimera/piko/commit/b3fda9558251eda91e6e3b43e994a9dbcdb1daac))

## [1.28.0-dev.2](https://github.com/crimera/piko/compare/v1.28.0-dev.1...v1.28.0-dev.2) (2024-05-11)


### Features

* **Twitter:** Added `Disable auto timeline scroll on launch` patch ([b8d431e](https://github.com/crimera/piko/commit/b8d431ea44a2b53f9065c226116d86ec1fb7b70f))

## [1.28.0-dev.1](https://github.com/crimera/piko/compare/v1.27.0...v1.28.0-dev.1) (2024-05-09)


### Features

* **Translations:** Update `japanese` ([444623f](https://github.com/crimera/piko/commit/444623faaa5761b33445705e04e5c3078ff88124))


### Updates

* **Translations:** Update Brazilian Portuguese ([8745f61](https://github.com/crimera/piko/commit/8745f61c060a62152bd84ee510d835ac8346e01e))

## [1.27.0](https://github.com/crimera/piko/compare/v1.26.0...v1.27.0) (2024-05-08)


### Bug Fixes

* add "SettingsStatusLoadFingerprint" to fingerprints of `Hide Community Notes` and `Hide Live Threads` patch ([9c0232a](https://github.com/crimera/piko/commit/9c0232afd684024cc7028960aa2c65a82ce90cb7))
* **Twitter:** Bookmark in nav bar dix ([03fb96c](https://github.com/crimera/piko/commit/03fb96c485edb8931e9d37452f5a45c0b8a0103b))
* **Twitter:** Bookmark nav bar fix ([a6e8ab9](https://github.com/crimera/piko/commit/a6e8ab9c6817e15fa7c9ba182650f65ee2e65132))
* Unescaped characters ([8e539df](https://github.com/crimera/piko/commit/8e539dfd8c4e1ef6889357b9850a842db54a2a99))


### Features

* **Twitter:** Added `Ability to copy video media link` patch ([14bfdc7](https://github.com/crimera/piko/commit/14bfdc7d0b2d1fbf6644e496cbc186bd5ade3494))
* **Twitter:** Added `Patch info` section ([7f794c3](https://github.com/crimera/piko/commit/7f794c3cc88b1fd1170f43589aaff356532bb9e4))


### Updates

* Improve the retrieval of the patches version ([ba940da](https://github.com/crimera/piko/commit/ba940da7b84e52345533dd3802410811b7fa4cb7))
* **Translations:** Update `Indonesian (Bahasa)` ([1442b6c](https://github.com/crimera/piko/commit/1442b6c798f5fd58e1381f1e23581cc8f55ca0fc))
* **Translations:** Update `japanese` ([9fce4a2](https://github.com/crimera/piko/commit/9fce4a20d7db77992bd92eaa5ada75af8af56103))
* **Translations:** update simplified chinese translation ([b24f7cc](https://github.com/crimera/piko/commit/b24f7cca23c6b003c7246951de8a361ff5ef2dfb))


### Refactors

* Add `enableSettings` helper function ([7d6ebd5](https://github.com/crimera/piko/commit/7d6ebd579a32161bf782f2ce0843817dbbf8b69d))
* Add `SettingsPatch ` dependencies to patches ([6d252f8](https://github.com/crimera/piko/commit/6d252f84c6086b15e7be4a58676254748bd8ff73))
* Optimize imports ([2f42add](https://github.com/crimera/piko/commit/2f42add01463d36336423574b76670c6cbf2659a))
* **Twitter:** `Add ability to copy media link` patch ([e8ef551](https://github.com/crimera/piko/commit/e8ef5519abf64594676f0de1e0e3e01b06571079))
* **Twitter:** refactor default feature flag hook ([6fd6001](https://github.com/crimera/piko/commit/6fd60012c74c83944fd80b57e444c3f54da5823c))

## [1.27.0-dev.8](https://github.com/crimera/piko/compare/v1.27.0-dev.7...v1.27.0-dev.8) (2024-05-08)


### Updates

* **Translations:** Update `Indonesian (Bahasa)` ([1442b6c](https://github.com/crimera/piko/commit/1442b6c798f5fd58e1381f1e23581cc8f55ca0fc))
* **Translations:** update simplified chinese translation ([b24f7cc](https://github.com/crimera/piko/commit/b24f7cca23c6b003c7246951de8a361ff5ef2dfb))

## [1.27.0-dev.7](https://github.com/crimera/piko/compare/v1.27.0-dev.6...v1.27.0-dev.7) (2024-05-08)


### Updates

* Improve the retrieval of the patches version ([ba940da](https://github.com/crimera/piko/commit/ba940da7b84e52345533dd3802410811b7fa4cb7))

## [1.27.0-dev.6](https://github.com/crimera/piko/compare/v1.27.0-dev.5...v1.27.0-dev.6) (2024-05-07)


### Features

* **Twitter:** Added `Patch info` section ([7f794c3](https://github.com/crimera/piko/commit/7f794c3cc88b1fd1170f43589aaff356532bb9e4))

## [1.27.0-dev.5](https://github.com/crimera/piko/compare/v1.27.0-dev.4...v1.27.0-dev.5) (2024-05-05)


### Refactors

* **Twitter:** `Add ability to copy media link` patch ([e8ef551](https://github.com/crimera/piko/commit/e8ef5519abf64594676f0de1e0e3e01b06571079))

## [1.27.0-dev.4](https://github.com/crimera/piko/compare/v1.27.0-dev.3...v1.27.0-dev.4) (2024-05-02)


### Bug Fixes

* **Twitter:** Bookmark in nav bar dix ([03fb96c](https://github.com/crimera/piko/commit/03fb96c485edb8931e9d37452f5a45c0b8a0103b))
* **Twitter:** Bookmark nav bar fix ([a6e8ab9](https://github.com/crimera/piko/commit/a6e8ab9c6817e15fa7c9ba182650f65ee2e65132))


### Refactors

* **Twitter:** refactor default feature flag hook ([6fd6001](https://github.com/crimera/piko/commit/6fd60012c74c83944fd80b57e444c3f54da5823c))

## [1.27.0-dev.3](https://github.com/crimera/piko/compare/v1.27.0-dev.2...v1.27.0-dev.3) (2024-05-02)


### Updates

* **Translations:** Update `japanese` ([9fce4a2](https://github.com/crimera/piko/commit/9fce4a20d7db77992bd92eaa5ada75af8af56103))

## [1.27.0-dev.2](https://github.com/crimera/piko/compare/v1.27.0-dev.1...v1.27.0-dev.2) (2024-05-01)


### Bug Fixes

* Unescaped characters ([8e539df](https://github.com/crimera/piko/commit/8e539dfd8c4e1ef6889357b9850a842db54a2a99))

## [1.27.0-dev.1](https://github.com/crimera/piko/compare/v1.26.1-dev.3...v1.27.0-dev.1) (2024-05-01)


### Features

* **Twitter:** Added `Ability to copy video media link` patch ([14bfdc7](https://github.com/crimera/piko/commit/14bfdc7d0b2d1fbf6644e496cbc186bd5ade3494))

## [1.26.1-dev.3](https://github.com/crimera/piko/compare/v1.26.1-dev.2...v1.26.1-dev.3) (2024-04-30)


### Refactors

* Add `enableSettings` helper function ([7d6ebd5](https://github.com/crimera/piko/commit/7d6ebd579a32161bf782f2ce0843817dbbf8b69d))
* Optimize imports ([2f42add](https://github.com/crimera/piko/commit/2f42add01463d36336423574b76670c6cbf2659a))

## [1.26.1-dev.2](https://github.com/crimera/piko/compare/v1.26.1-dev.1...v1.26.1-dev.2) (2024-04-30)


### Refactors

* Add `SettingsPatch ` dependencies to patches ([6d252f8](https://github.com/crimera/piko/commit/6d252f84c6086b15e7be4a58676254748bd8ff73))

## [1.26.1-dev.1](https://github.com/crimera/piko/compare/v1.26.0...v1.26.1-dev.1) (2024-04-30)


### Bug Fixes

* add "SettingsStatusLoadFingerprint" to fingerprints of `Hide Community Notes` and `Hide Live Threads` patch ([9c0232a](https://github.com/crimera/piko/commit/9c0232afd684024cc7028960aa2c65a82ce90cb7))

## [1.26.0](https://github.com/crimera/piko/compare/v1.25.1...v1.26.0) (2024-04-30)


### Bug Fixes

* **Twitter:** Fix `Hide Promoted Trends` patch ([7052d25](https://github.com/crimera/piko/commit/7052d2583527354ee1a719f808993049364c8718))


### Features

* Add toggle for `Open browser chooser on opening links` patch ([6f217f0](https://github.com/crimera/piko/commit/6f217f08a8037be067791b318b750d7f6d91b4ea))
* **Translations:** Add Brazilian Portuguese translations [Bring back Twitter] ([3a831d3](https://github.com/crimera/piko/commit/3a831d3869a5fbbb22342cbfe1859241b2126fa5))
* **Translations:** Add Simplified Chinese translations and Taiwanese Traditional Chinese translations[Bring back Twitter] ([d44bfd7](https://github.com/crimera/piko/commit/d44bfd73f0063703179c097d68738063a000d80b))


### Updates

* **Bring back twitter:** Create strings file if not found ([9f51a5c](https://github.com/crimera/piko/commit/9f51a5cf9525e829166a5b82261abe209823eb5b))
* **Custom sharing domain:** require settings patch also turned it on by default. ([6a3777f](https://github.com/crimera/piko/commit/6a3777f0631a97dff3a31129a45cfc671e71283d))
* **Translations:** Update `Indonesian (Bahasa)` ([b9488d6](https://github.com/crimera/piko/commit/b9488d6fed4e521f80ca9c292112130d806f77aa))
* **Translations:** Update Brazilian Portuguese ([7de9493](https://github.com/crimera/piko/commit/7de949327844e06e1fda1946bed5555fb150b67c))
* **Translations:** Update Japanese ([61ff288](https://github.com/crimera/piko/commit/61ff288ba6aa086c984a317a52fb7228710878f5))
* **Translations:** Update Russian translation ([8b1c4c8](https://github.com/crimera/piko/commit/8b1c4c8185a482a72ecc7cdc2dbb31940660a70d))
* **Translations:** Update simplified chinese translation ([147cb29](https://github.com/crimera/piko/commit/147cb29cd4e4f260fd168934584971e6620e7f9b))
* **Translations:** Update Turkish translations ([bab6ae9](https://github.com/crimera/piko/commit/bab6ae97e400d89f7551eebfc1ee04f385a1c62c))
* **Translation:** Update Hindi translations [Bring back Twitter] ([20ac044](https://github.com/crimera/piko/commit/20ac044efe3f96f2cefadce6cb42a00e1a820840))


### Refactors

* **Twitter:** Added multiple language support for `Bring back twitter` ([9eacab3](https://github.com/crimera/piko/commit/9eacab308dab4f4ea1de834d81308e9d616766c2))
* **Twitter:** Rearrange `Bring back twitter` resources ([a476924](https://github.com/crimera/piko/commit/a47692437d7f80c44b67ff61204b02db93743b86))

## [1.26.0-dev.9](https://github.com/crimera/piko/compare/v1.26.0-dev.8...v1.26.0-dev.9) (2024-04-30)


### Features

* **Translations:** Add Simplified Chinese translations and Taiwanese Traditional Chinese translations[Bring back Twitter] ([d44bfd7](https://github.com/crimera/piko/commit/d44bfd73f0063703179c097d68738063a000d80b))

## [1.26.0-dev.8](https://github.com/crimera/piko/compare/v1.26.0-dev.7...v1.26.0-dev.8) (2024-04-30)


### Updates

* **Bring back twitter:** Create strings file if not found ([9f51a5c](https://github.com/crimera/piko/commit/9f51a5cf9525e829166a5b82261abe209823eb5b))
* **Custom sharing domain:** require settings patch also turned it on by default. ([6a3777f](https://github.com/crimera/piko/commit/6a3777f0631a97dff3a31129a45cfc671e71283d))

## [1.26.0-dev.7](https://github.com/crimera/piko/compare/v1.26.0-dev.6...v1.26.0-dev.7) (2024-04-30)


### Updates

* **Translations:** Update polish ([9d8f4fc](https://github.com/crimera/piko/commit/9d8f4fc57174b0cc28540e29b7b9725af5e65421))

## [1.26.0-dev.6](https://github.com/crimera/piko/compare/v1.26.0-dev.5...v1.26.0-dev.6) (2024-04-29)


### Features

* **Translations:** Add Brazilian Portuguese translations [Bring back Twitter] ([3a831d3](https://github.com/crimera/piko/commit/3a831d3869a5fbbb22342cbfe1859241b2126fa5))

## [1.26.0-dev.5](https://github.com/crimera/piko/compare/v1.26.0-dev.4...v1.26.0-dev.5) (2024-04-28)


### Updates

* **Translation:** Update Hindi translations [Bring back Twitter] ([20ac044](https://github.com/crimera/piko/commit/20ac044efe3f96f2cefadce6cb42a00e1a820840))


### Refactors

* **Twitter:** Added multiple language support for `Bring back twitter` ([9eacab3](https://github.com/crimera/piko/commit/9eacab308dab4f4ea1de834d81308e9d616766c2))
* **Twitter:** Rearrange `Bring back twitter` resources ([a476924](https://github.com/crimera/piko/commit/a47692437d7f80c44b67ff61204b02db93743b86))

## [1.26.0-dev.4](https://github.com/crimera/piko/compare/v1.26.0-dev.3...v1.26.0-dev.4) (2024-04-27)


### Updates

* **Translations:** Update Turkish translations ([bab6ae9](https://github.com/crimera/piko/commit/bab6ae97e400d89f7551eebfc1ee04f385a1c62c))

## [1.26.0-dev.3](https://github.com/crimera/piko/compare/v1.26.0-dev.2...v1.26.0-dev.3) (2024-04-27)


### Bug Fixes

* **Twitter:** Fix `Hide Promoted Trends` patch ([7052d25](https://github.com/crimera/piko/commit/7052d2583527354ee1a719f808993049364c8718))

## [1.26.0-dev.2](https://github.com/crimera/piko/compare/v1.26.0-dev.1...v1.26.0-dev.2) (2024-04-27)


### Updates

* **Translations:** Update `Indonesian (Bahasa)` ([b9488d6](https://github.com/crimera/piko/commit/b9488d6fed4e521f80ca9c292112130d806f77aa))
* **Translations:** Update Japanese ([61ff288](https://github.com/crimera/piko/commit/61ff288ba6aa086c984a317a52fb7228710878f5))
* **Translations:** Update simplified chinese translation ([147cb29](https://github.com/crimera/piko/commit/147cb29cd4e4f260fd168934584971e6620e7f9b))

## [1.26.0-dev.1](https://github.com/crimera/piko/compare/v1.25.1-dev.1...v1.26.0-dev.1) (2024-04-26)


### Features

* Add toggle for `Open browser chooser on opening links` patch ([6f217f0](https://github.com/crimera/piko/commit/6f217f08a8037be067791b318b750d7f6d91b4ea))


### Updates

* **Translations:** Update Russian translation ([8b1c4c8](https://github.com/crimera/piko/commit/8b1c4c8185a482a72ecc7cdc2dbb31940660a70d))

## [1.25.1-dev.1](https://github.com/crimera/piko/compare/v1.25.0...v1.25.1-dev.1) (2024-04-26)


### Updates

* **Translations:** Update Brazilian Portuguese ([7de9493](https://github.com/crimera/piko/commit/7de949327844e06e1fda1946bed5555fb150b67c))

## [1.25.0](https://github.com/crimera/piko/compare/v1.24.0...v1.25.0) (2024-04-25)


### Features

* **Twitter:** Added `Customize timeline top bar` patch ([245c5f6](https://github.com/crimera/piko/commit/245c5f6f8f82e98b405ec853624c3cad52ae1764))
* use browser chooser when opening links ([cc165f4](https://github.com/crimera/piko/commit/cc165f41db4392d9dcc554ee8800333e87dc8cdf))

## [1.25.0-dev.2](https://github.com/crimera/piko/compare/v1.25.0-dev.1...v1.25.0-dev.2) (2024-04-25)


### Features

* **Twitter:** Added `Customize timeline top bar` patch ([245c5f6](https://github.com/crimera/piko/commit/245c5f6f8f82e98b405ec853624c3cad52ae1764))

## [1.25.0-dev.1](https://github.com/crimera/piko/compare/v1.24.0...v1.25.0-dev.1) (2024-04-25)


### Features

* use browser chooser when opening links ([cc165f4](https://github.com/crimera/piko/commit/cc165f41db4392d9dcc554ee8800333e87dc8cdf))

## [1.24.0](https://github.com/crimera/piko/compare/v1.23.0...v1.24.0) (2024-04-25)


### Bug Fixes

* Opening mod settings with root installation results in crash ([8161644](https://github.com/crimera/piko/commit/81616443f2104eb1633c2c92c555f45341a7680e))
* Opening mod settings with root installation results in crash ([47e7670](https://github.com/crimera/piko/commit/47e7670cc09da736cbca6c9447f82d9fa343ee56))
* unescaped character on `pl` string values ([a6444be](https://github.com/crimera/piko/commit/a6444bed45872df6a55be17a2b64dd2d40e2d16a))


### Features

* **Translations:** Added Traditional Chinese translation ([1c0b10a](https://github.com/crimera/piko/commit/1c0b10af732a6e159862a99369b57368cbbdab91))
* **Twitter:** Added `Profile tabs customisation` ([db0298b](https://github.com/crimera/piko/commit/db0298b9f0cc6066974630d29812a2e8a11e7262))


### Refactors

* **Twitter:** updated profile tab string ([1af7846](https://github.com/crimera/piko/commit/1af78461de97661a82d5eb638300402eaab00afe))

## [1.24.0-dev.3](https://github.com/crimera/piko/compare/v1.24.0-dev.2...v1.24.0-dev.3) (2024-04-23)


### Features

* **Translations:** Added Traditional Chinese translation ([1c0b10a](https://github.com/crimera/piko/commit/1c0b10af732a6e159862a99369b57368cbbdab91))

## [1.24.0-dev.2](https://github.com/crimera/piko/compare/v1.24.0-dev.1...v1.24.0-dev.2) (2024-04-21)


### Bug Fixes

* Opening mod settings with root installation results in crash ([8161644](https://github.com/crimera/piko/commit/81616443f2104eb1633c2c92c555f45341a7680e))
* Opening mod settings with root installation results in crash ([47e7670](https://github.com/crimera/piko/commit/47e7670cc09da736cbca6c9447f82d9fa343ee56))
* unescaped character on `pl` string values ([a6444be](https://github.com/crimera/piko/commit/a6444bed45872df6a55be17a2b64dd2d40e2d16a))

## [1.24.0-dev.1](https://github.com/crimera/piko/compare/v1.23.0...v1.24.0-dev.1) (2024-04-19)


### Features

* **Twitter:** Added `Profile tabs customisation` ([db0298b](https://github.com/crimera/piko/commit/db0298b9f0cc6066974630d29812a2e8a11e7262))


### Refactors

* **Twitter:** updated profile tab string ([1af7846](https://github.com/crimera/piko/commit/1af78461de97661a82d5eb638300402eaab00afe))

## [1.23.0](https://github.com/crimera/piko/compare/v1.22.0...v1.23.0) (2024-04-19)


### Bug Fixes

* Fix oopsie ([2af661f](https://github.com/crimera/piko/commit/2af661f5dfa1cc08e436ce04800ab230030a5079))
* Missing `,` ([67675fe](https://github.com/crimera/piko/commit/67675fe381f0554cb69c0f061ec792df9e523479))
* Not creating `values-night-v31` dir ([e3f3491](https://github.com/crimera/piko/commit/e3f3491fea403f55d5200d1afc4aebd5c7507c2e))
* switch jp to ja for Japanese translation ([c13cc87](https://github.com/crimera/piko/commit/c13cc87afeaea8cb623e7652a154a507ce532d61))


### Features

* **Translations:** Add `Turkish` ([bf70b91](https://github.com/crimera/piko/commit/bf70b913775ec299a87725b15d30bb2506da7a5d))
* **Translations:** Add Arabic translations ([c1d797b](https://github.com/crimera/piko/commit/c1d797b21bcbf0752bb48c94e4009e8221e46b36))
* **Translations:** Add japanese ([c123d8e](https://github.com/crimera/piko/commit/c123d8ed58f92dce6337cd194be64e21b3d9559c))
* **Translations:** Add simplified Chinese translation ([7662a34](https://github.com/crimera/piko/commit/7662a34a87e2dd5d819188993054a8fc4808ef03))
* **Translations:** Fix Simplified Chinese Translation ([ec0a3dc](https://github.com/crimera/piko/commit/ec0a3dcadf4027c3a6a6d411d44043d245b5ca29))
* **Translations:** Update `japanese` ([f5ece19](https://github.com/crimera/piko/commit/f5ece191a724f7bc0aed0cb90df8902f6d9e110b))
* **Translations:** Update Russian translation ([54ba664](https://github.com/crimera/piko/commit/54ba664921218ce79218e4ce4a9a2d4d2d1cf1b6))
* **Translations:** Updated Brazilian Portuguese ([e25ec0e](https://github.com/crimera/piko/commit/e25ec0ecee9e576f6fe74c2bf70d750399275b7e))
* **translation:** Update Simplified Chinese translation ([4f3027a](https://github.com/crimera/piko/commit/4f3027a2279e9cd1a2cc010d190cab3551c45cd4))
* **Translation:** Update Simplified Chinese translation ([b9fe0e8](https://github.com/crimera/piko/commit/b9fe0e83a3ddd1fd2633dca6403723c4dbea32ce))
* **Twitter:** Added `Enable app downgrading` patch ([9f581ce](https://github.com/crimera/piko/commit/9f581ce41b202fe7e5dfbb634a18a5bf6fb8faa5))
* **Twitter:** Added ability to export and import prefs & feature flags ([01cdd6b](https://github.com/crimera/piko/commit/01cdd6b7b25f08a3b258704f38c25b11aa1a9703))
* **Twitter:** Added ability to export and import prefs & feature flags ([a591133](https://github.com/crimera/piko/commit/a591133381a0b620a82d83580757300042680c85))
* **ui:** full app wide material theming in dim mode replacing dark blue ([52d7444](https://github.com/crimera/piko/commit/52d74441f0ae509ec66ba04b3939c90deb4755c5))


### Refactors

* **Twitter:** load `Utils.load()` dynamically ([5379276](https://github.com/crimera/piko/commit/53792768e4e5465559478041f51e516159c1f93f))

## [1.23.0-dev.11](https://github.com/crimera/piko/compare/v1.23.0-dev.10...v1.23.0-dev.11) (2024-04-18)


### Features

* **Twitter:** Added `Enable app downgrading` patch ([9f581ce](https://github.com/crimera/piko/commit/9f581ce41b202fe7e5dfbb634a18a5bf6fb8faa5))


### Refactors

* **Twitter:** load `Utils.load()` dynamically ([5379276](https://github.com/crimera/piko/commit/53792768e4e5465559478041f51e516159c1f93f))

## [1.23.0-dev.10](https://github.com/crimera/piko/compare/v1.23.0-dev.9...v1.23.0-dev.10) (2024-04-17)


### Features

* **Translations:** Fix Simplified Chinese Translation ([ec0a3dc](https://github.com/crimera/piko/commit/ec0a3dcadf4027c3a6a6d411d44043d245b5ca29))

## [1.23.0-dev.9](https://github.com/crimera/piko/compare/v1.23.0-dev.8...v1.23.0-dev.9) (2024-04-17)


### Bug Fixes

* switch jp to ja for Japanese translation ([c13cc87](https://github.com/crimera/piko/commit/c13cc87afeaea8cb623e7652a154a507ce532d61))

## [1.23.0-dev.8](https://github.com/crimera/piko/compare/v1.23.0-dev.7...v1.23.0-dev.8) (2024-04-17)


### Bug Fixes

* Not creating `values-night-v31` dir ([e3f3491](https://github.com/crimera/piko/commit/e3f3491fea403f55d5200d1afc4aebd5c7507c2e))

## [1.23.0-dev.7](https://github.com/crimera/piko/compare/v1.23.0-dev.6...v1.23.0-dev.7) (2024-04-17)


### Features

* **ui:** full app wide material theming in dim mode replacing dark blue ([52d7444](https://github.com/crimera/piko/commit/52d74441f0ae509ec66ba04b3939c90deb4755c5))

## [1.23.0-dev.6](https://github.com/crimera/piko/compare/v1.23.0-dev.5...v1.23.0-dev.6) (2024-04-16)


### Bug Fixes

* Missing `,` ([67675fe](https://github.com/crimera/piko/commit/67675fe381f0554cb69c0f061ec792df9e523479))


### Features

* **Translations:** Add `Turkish` ([bf70b91](https://github.com/crimera/piko/commit/bf70b913775ec299a87725b15d30bb2506da7a5d))
* **Translations:** Update Russian translation ([54ba664](https://github.com/crimera/piko/commit/54ba664921218ce79218e4ce4a9a2d4d2d1cf1b6))
* **Translations:** Updated Brazilian Portuguese ([e25ec0e](https://github.com/crimera/piko/commit/e25ec0ecee9e576f6fe74c2bf70d750399275b7e))

## [1.23.0-dev.5](https://github.com/crimera/piko/compare/v1.23.0-dev.4...v1.23.0-dev.5) (2024-04-16)


### Features

* **Translations:** Update `japanese` ([f5ece19](https://github.com/crimera/piko/commit/f5ece191a724f7bc0aed0cb90df8902f6d9e110b))
* **translation:** Update Simplified Chinese translation ([4f3027a](https://github.com/crimera/piko/commit/4f3027a2279e9cd1a2cc010d190cab3551c45cd4))
* **Translation:** Update Simplified Chinese translation ([b9fe0e8](https://github.com/crimera/piko/commit/b9fe0e83a3ddd1fd2633dca6403723c4dbea32ce))

## [1.23.0-dev.4](https://github.com/crimera/piko/compare/v1.23.0-dev.3...v1.23.0-dev.4) (2024-04-15)


### Features

* **Twitter:** Added ability to export and import prefs & feature flags ([01cdd6b](https://github.com/crimera/piko/commit/01cdd6b7b25f08a3b258704f38c25b11aa1a9703))
* **Twitter:** Added ability to export and import prefs & feature flags ([a591133](https://github.com/crimera/piko/commit/a591133381a0b620a82d83580757300042680c85))

## [1.23.0-dev.3](https://github.com/crimera/piko/compare/v1.23.0-dev.2...v1.23.0-dev.3) (2024-04-15)


### Features

* **Translations:** Add japanese ([c123d8e](https://github.com/crimera/piko/commit/c123d8ed58f92dce6337cd194be64e21b3d9559c))
* **Translations:** Add simplified Chinese translation ([7662a34](https://github.com/crimera/piko/commit/7662a34a87e2dd5d819188993054a8fc4808ef03))

## [1.23.0-dev.2](https://github.com/crimera/piko/compare/v1.23.0-dev.1...v1.23.0-dev.2) (2024-04-15)


### Bug Fixes

* Fix oopsie ([2af661f](https://github.com/crimera/piko/commit/2af661f5dfa1cc08e436ce04800ab230030a5079))

## [1.23.0-dev.1](https://github.com/crimera/piko/compare/v1.22.0...v1.23.0-dev.1) (2024-04-15)


### Features

* **Translations:** Add Arabic translations ([c1d797b](https://github.com/crimera/piko/commit/c1d797b21bcbf0752bb48c94e4009e8221e46b36))

## [1.22.0](https://github.com/crimera/piko/compare/v1.21.0...v1.22.0) (2024-04-15)


### Features

* **Translations:** Add polish translation ([8102f35](https://github.com/crimera/piko/commit/8102f35bab392acd8337fdf363c8891e3b96f629))
* **Translations:** Update Russian translation ([3606c10](https://github.com/crimera/piko/commit/3606c101ae1630c303a8ed55c4e21ab75346fbbe))
* **Translations:** Updated Brazilian Portuguese ([45aac73](https://github.com/crimera/piko/commit/45aac7374731e2d3b4a24e3c22ad5aaaccbbc33a))

## [1.22.0](https://github.com/crimera/piko/compare/v1.21.0...v1.22.0) (2024-04-15)


### Features

* **Translations:** Add polish translation ([8102f35](https://github.com/crimera/piko/commit/8102f35bab392acd8337fdf363c8891e3b96f629))
* **Translations:** Update Russian translation ([3606c10](https://github.com/crimera/piko/commit/3606c101ae1630c303a8ed55c4e21ab75346fbbe))

## [1.21.0](https://github.com/crimera/piko/compare/v1.20.0...v1.21.0) (2024-04-14)


### Features

* Add russian translation ([2dc9cee](https://github.com/crimera/piko/commit/2dc9ceed63a5a965f9979e3df1094f6b7a12a001))
* Add support for adding custom feature flags ([a6a91d1](https://github.com/crimera/piko/commit/a6a91d13a8623b505ba299ee23845f2a62399f33))
* Added resource file for strings ([181fd29](https://github.com/crimera/piko/commit/181fd298748c38dc53c10609b6714d8ce255d5be))
* **Translations:** add hindi ( हिन्दी ) translations [#91](https://github.com/crimera/piko/issues/91) ([a766771](https://github.com/crimera/piko/commit/a766771d848a43c3a4d2ba5e955ab72015dfb69d))
* **Translations:** Added Brazilian Portuguese [#90](https://github.com/crimera/piko/issues/90) ([ad307da](https://github.com/crimera/piko/commit/ad307da5ba7f0739024c7a30de2cb3b09d8aee28))
* **Twitter:** Added `Feature Flag Hook` ([2328baf](https://github.com/crimera/piko/commit/2328baf92c733cf99ca364c978514b14b6aae2e0))
* **Twitter:** Added `Hide immersive player` patch ([a649652](https://github.com/crimera/piko/commit/a649652c982903ac29100933122db47e9ed86d17))


### Updates

* Change settings string id ([b9c86d7](https://github.com/crimera/piko/commit/b9c86d715c0ae4ec2da63384462be3c784db7b73))
* **Twitter:** Made some features to use flags ([04c366a](https://github.com/crimera/piko/commit/04c366a6cfac0e8d3ce0bcc41f61df76b6043f42))

## [1.21.0-dev.7](https://github.com/crimera/piko/compare/v1.21.0-dev.6...v1.21.0-dev.7) (2024-04-14)


### Features

* Add support for adding custom feature flags ([a6a91d1](https://github.com/crimera/piko/commit/a6a91d13a8623b505ba299ee23845f2a62399f33))

## [1.21.0-dev.6](https://github.com/crimera/piko/compare/v1.21.0-dev.5...v1.21.0-dev.6) (2024-04-14)


### Features

* **Translations:** add hindi ( हिन्दी ) translations [#91](https://github.com/crimera/piko/issues/91) ([a766771](https://github.com/crimera/piko/commit/a766771d848a43c3a4d2ba5e955ab72015dfb69d))

## [1.21.0-dev.5](https://github.com/crimera/piko/compare/v1.21.0-dev.4...v1.21.0-dev.5) (2024-04-14)


### Features

* **Translations:** Added Brazilian Portuguese [#90](https://github.com/crimera/piko/issues/90) ([ad307da](https://github.com/crimera/piko/commit/ad307da5ba7f0739024c7a30de2cb3b09d8aee28))

## [1.21.0-dev.4](https://github.com/crimera/piko/compare/v1.21.0-dev.3...v1.21.0-dev.4) (2024-04-14)


### Updates

* Change settings string id ([b9c86d7](https://github.com/crimera/piko/commit/b9c86d715c0ae4ec2da63384462be3c784db7b73))

## [1.21.0-dev.3](https://github.com/crimera/piko/compare/v1.21.0-dev.2...v1.21.0-dev.3) (2024-04-14)


### Features

* Add russian translation ([2dc9cee](https://github.com/crimera/piko/commit/2dc9ceed63a5a965f9979e3df1094f6b7a12a001))

## [1.21.0-dev.2](https://github.com/crimera/piko/compare/v1.21.0-dev.1...v1.21.0-dev.2) (2024-04-11)


### Features

* Added resource file for strings ([181fd29](https://github.com/crimera/piko/commit/181fd298748c38dc53c10609b6714d8ce255d5be))

## [1.21.0-dev.1](https://github.com/crimera/piko/compare/v1.20.0...v1.21.0-dev.1) (2024-04-09)


### Features

* **Twitter:** Added `Feature Flag Hook` ([2328baf](https://github.com/crimera/piko/commit/2328baf92c733cf99ca364c978514b14b6aae2e0))
* **Twitter:** Added `Hide immersive player` patch ([a649652](https://github.com/crimera/piko/commit/a649652c982903ac29100933122db47e9ed86d17))


### Updates

* **Twitter:** Made some features to use flags ([04c366a](https://github.com/crimera/piko/commit/04c366a6cfac0e8d3ce0bcc41f61df76b6043f42))

## [1.20.0](https://github.com/crimera/piko/compare/v1.19.0...v1.20.0) (2024-04-08)


### Bug Fixes

* **Custom sharing domain:** Now works on "Copy Link" ([8f71415](https://github.com/crimera/piko/commit/8f71415e2c7cb5cf351bbcf11d2942d5f0771995))
* **Hide FAB:** Add description ([ae418a8](https://github.com/crimera/piko/commit/ae418a83d94cbb1091ba9fec3ce07a8704ab5c00))
* **Hide For You:** Included by default ([3b51053](https://github.com/crimera/piko/commit/3b51053658847d9d4d1864c5664c5247dd029bcf))


### Features

* Add `Show poll results` patch ([7b48f1b](https://github.com/crimera/piko/commit/7b48f1b9da05deb9aae73f0982f65eb527666fdc))
* **ci:** Add updates and refactors to changelog ([5fb8ef7](https://github.com/crimera/piko/commit/5fb8ef7902e6be592305c34cf3add0f06e477a68))
* **Show poll results:** Add a toggle on mod settings ([2624561](https://github.com/crimera/piko/commit/262456164e84d81b6c33436ac9f8fa21fa05e2ba))


### Updates

* **Twitter:** Added stubs to `No shortened URL` patch ([6d4f436](https://github.com/crimera/piko/commit/6d4f436f4773a667076930cbfb32eef2691b28bc))
* **Twitter:** refactor Timeline entry Hook ([271da73](https://github.com/crimera/piko/commit/271da73408735e8a3ef83e36bd2462d072c16bf2))
* **Twitter:** use stubs for Timeline entry Hook ([3ce1aee](https://github.com/crimera/piko/commit/3ce1aee32df656c64ba105cb45d148cd88db990a))

## [1.20.0-dev.4](https://github.com/crimera/piko/compare/v1.20.0-dev.3...v1.20.0-dev.4) (2024-04-08)


### Features

* **ci:** Add updates and refactors to changelog ([5fb8ef7](https://github.com/crimera/piko/commit/5fb8ef7902e6be592305c34cf3add0f06e477a68))

# [1.20.0-dev.3](https://github.com/crimera/piko/compare/v1.20.0-dev.2...v1.20.0-dev.3) (2024-04-07)

# [1.20.0-dev.2](https://github.com/crimera/piko/compare/v1.20.0-dev.1...v1.20.0-dev.2) (2024-04-07)


### Features

* **Show poll results:** Add a toggle on mod settings ([2624561](https://github.com/crimera/piko/commit/262456164e84d81b6c33436ac9f8fa21fa05e2ba))

# [1.20.0-dev.1](https://github.com/crimera/piko/compare/v1.19.1-dev.2...v1.20.0-dev.1) (2024-04-07)


### Features

* Add `Show poll results` patch ([7b48f1b](https://github.com/crimera/piko/commit/7b48f1b9da05deb9aae73f0982f65eb527666fdc))

## [1.19.1-dev.2](https://github.com/crimera/piko/compare/v1.19.1-dev.1...v1.19.1-dev.2) (2024-04-06)


### Bug Fixes

* **Custom sharing domain:** Now works on "Copy Link" ([8f71415](https://github.com/crimera/piko/commit/8f71415e2c7cb5cf351bbcf11d2942d5f0771995))
* **Hide For You:** Included by default ([3b51053](https://github.com/crimera/piko/commit/3b51053658847d9d4d1864c5664c5247dd029bcf))

## [1.19.1-dev.1](https://github.com/crimera/piko/compare/v1.19.0...v1.19.1-dev.1) (2024-04-05)


### Bug Fixes

* **Hide FAB:** Add description ([ae418a8](https://github.com/crimera/piko/commit/ae418a83d94cbb1091ba9fec3ce07a8704ab5c00))

# [1.19.0](https://github.com/crimera/piko/compare/v1.18.0...v1.19.0) (2024-04-02)


### Features

* **Twitter:** Added `Hide bookmark icon in timeline` ([ff66167](https://github.com/crimera/piko/commit/ff66167e5678adff4fde4e092a03b030a2a49693))

# [1.19.0-dev.1](https://github.com/crimera/piko/compare/v1.18.0...v1.19.0-dev.1) (2024-04-01)


### Features

* **Twitter:** Added `Hide bookmark icon in timeline` ([ff66167](https://github.com/crimera/piko/commit/ff66167e5678adff4fde4e092a03b030a2a49693))

# [1.18.0](https://github.com/crimera/piko/compare/v1.17.20...v1.18.0) (2024-04-01)


### Bug Fixes

* **Twitter - Custom sharing domain:** Disable by default ([6e7c170](https://github.com/crimera/piko/commit/6e7c17014b75d098eaef5ba3fbe4ed777eb416b1))


### Features

* **Twitter:** Add `Custom sharing domain` patch ([8ffd36c](https://github.com/crimera/piko/commit/8ffd36c4097f3dc1de9f654b44f077e8ed1e9481))

# [1.18.0-dev.2](https://github.com/crimera/piko/compare/v1.18.0-dev.1...v1.18.0-dev.2) (2024-04-01)

# [1.18.0-dev.1](https://github.com/crimera/piko/compare/v1.17.1...v1.18.0-dev.1) (2024-03-31)


### Bug Fixes

* **Twitter - Custom sharing domain:** Disable by default ([6e7c170](https://github.com/crimera/piko/commit/6e7c17014b75d098eaef5ba3fbe4ed777eb416b1))


### Features

* **Twitter:** Add `Custom sharing domain` patch ([8ffd36c](https://github.com/crimera/piko/commit/8ffd36c4097f3dc1de9f654b44f077e8ed1e9481))


## [1.17.1](https://github.com/crimera/piko/compare/v1.17.0...v1.17.1) (2024-03-29)


### Bug Fixes

* **Twitter - Bring back twitter:** Exclude by default ([e654549](https://github.com/crimera/piko/commit/e654549c95412b50932453732b215ebb097d4c19))
* **Twitter - Bring back twitter:** Now changes the app name by writing directly to AndroidManifest (Should make patching faster) ([f61468c](https://github.com/crimera/piko/commit/f61468c4cd1d42d7697a3179a4711dcb9ea01c9e))

## [1.17.1-dev.2](https://github.com/crimera/piko/compare/v1.17.1-dev.1...v1.17.1-dev.2) (2024-03-29)


### Bug Fixes

* **Twitter - Bring back twitter:** Now changes the app name by writing directly to AndroidManifest (Should make patching faster) ([f61468c](https://github.com/crimera/piko/commit/f61468c4cd1d42d7697a3179a4711dcb9ea01c9e))

## [1.17.1-dev.1](https://github.com/crimera/piko/compare/v1.17.0...v1.17.1-dev.1) (2024-03-29)


### Bug Fixes

* **Twitter - Bring back twitter:** Exclude by default ([e654549](https://github.com/crimera/piko/commit/e654549c95412b50932453732b215ebb097d4c19))

# [1.17.0](https://github.com/crimera/piko/compare/v1.16.0...v1.17.0) (2024-03-29)


### Bug Fixes

* **Twitter - Bring back twitter:** Add other languages ([2c63ff1](https://github.com/crimera/piko/commit/2c63ff1bc1444ed6d83f911becca4289001c306c))
* **Twitter - Bring back twitter:** Now changes notification icon ([7e281e4](https://github.com/crimera/piko/commit/7e281e42f2c809ad669113b81f9f888b168882ca))
* **Twitter - Bring back twitter:** Now changes notification icon ([8b57a07](https://github.com/crimera/piko/commit/8b57a07dfb7ca6ce3d92af92b09f2a786e0544f2))
* **Twitter:** Add splash screen icon ([b30e083](https://github.com/crimera/piko/commit/b30e0838de4ac5f72848dcc813d477e52a6d3f1c))


### Features

* **Twitter:** Add `Bring back twitter` patch ([b299b0c](https://github.com/crimera/piko/commit/b299b0c076a9ef68970bc57547830027d336b442))

# [1.17.0-dev.3](https://github.com/crimera/piko/compare/v1.17.0-dev.2...v1.17.0-dev.3) (2024-03-29)


### Bug Fixes

* **Twitter - Bring back twitter:** Now changes notification icon ([7e281e4](https://github.com/crimera/piko/commit/7e281e42f2c809ad669113b81f9f888b168882ca))
* **Twitter - Bring back twitter:** Now changes notification icon ([8b57a07](https://github.com/crimera/piko/commit/8b57a07dfb7ca6ce3d92af92b09f2a786e0544f2))

# [1.17.0-dev.2](https://github.com/crimera/piko/compare/v1.17.0-dev.1...v1.17.0-dev.2) (2024-03-29)


### Bug Fixes

* **Twitter - Bring back twitter:** Add other languages ([2c63ff1](https://github.com/crimera/piko/commit/2c63ff1bc1444ed6d83f911becca4289001c306c))

# [1.17.0-dev.1](https://github.com/crimera/piko/compare/v1.16.0...v1.17.0-dev.1) (2024-03-29)


### Bug Fixes

* **Twitter:** Add splash screen icon ([b30e083](https://github.com/crimera/piko/commit/b30e0838de4ac5f72848dcc813d477e52a6d3f1c))


### Features

* **Twitter:** Add `Bring back twitter` patch ([b299b0c](https://github.com/crimera/piko/commit/b299b0c076a9ef68970bc57547830027d336b442))

# [1.16.0](https://github.com/crimera/piko/compare/v1.15.3...v1.16.0) (2024-03-27)


### Features

* Update README.md ([7d801a9](https://github.com/crimera/piko/commit/7d801a983d7f36eeba927514a26d943e0ea371d5))

# [1.16.0-dev.3](https://github.com/crimera/piko/compare/v1.16.0-dev.2...v1.16.0-dev.3) (2024-03-27)


### Features

* Update README.md ([7d801a9](https://github.com/crimera/piko/commit/7d801a983d7f36eeba927514a26d943e0ea371d5))

## [1.15.3](https://github.com/crimera/piko/compare/v1.15.2...v1.15.3) (2024-03-27)


### Bug Fixes

* refactor ([eab113e](https://github.com/crimera/piko/commit/eab113e471c2b588bbda05acba57e669a312f52c))

# [1.16.0-dev.2](https://github.com/crimera/piko/compare/v1.16.0-dev.1...v1.16.0-dev.2) (2024-03-27)


### Bug Fixes

* idk ([0a4ade0](https://github.com/crimera/piko/commit/0a4ade0e4cf65078cfbdde349c8f7ee92754067f))
* refactor ([eab113e](https://github.com/crimera/piko/commit/eab113e471c2b588bbda05acba57e669a312f52c))
* update pr if it already exists ([6d616d2](https://github.com/crimera/piko/commit/6d616d20ea0a0a4880e5fb339dd569db1cc530a2))

## [1.15.2](https://github.com/crimera/piko/compare/v1.15.1...v1.15.2) (2024-03-27)


### Bug Fixes

* update pr if it already exists ([6d616d2](https://github.com/crimera/piko/commit/6d616d20ea0a0a4880e5fb339dd569db1cc530a2))

# [1.15.0](https://github.com/crimera/piko/compare/v1.14.0...v1.15.0) (2024-03-27)


### Bug Fixes

* Activate release so i can test ([34b7227](https://github.com/crimera/piko/commit/34b7227934bc77e024d37ae5f14b719a9d165288))
* Change run config ([18d4dce](https://github.com/crimera/piko/commit/18d4dcebb67a568e460931c6cd2003a0df7ae5ec))
* idk ([0a4ade0](https://github.com/crimera/piko/commit/0a4ade0e4cf65078cfbdde349c8f7ee92754067f))
* Remove semicolon ;-) ([46e60f7](https://github.com/crimera/piko/commit/46e60f71d3eefc2eb81fbca01451d52731c397c1))
* **Twitter:** fix `Reader mode` for 10.34 ([01cb8c9](https://github.com/crimera/piko/commit/01cb8c94c951c103f0ebea56f8daf88214eb77ae))


### Features

* Add support for patches.json ([f5f63a3](https://github.com/crimera/piko/commit/f5f63a330a06d03a10e1a3232d96764fc2d1ac7c))
* **Twitter:** Add `Dynamic color` patch from revanced ([d2bd0ef](https://github.com/crimera/piko/commit/d2bd0ef4a6c36650edde7c6d8c0c7587dcfb46ac))
* **Twitter:** Add `Premium settings` patch, Unlocks premium options from settings like custom app icon and custom navbar ([066e953](https://github.com/crimera/piko/commit/066e9538a983e6c261a56b1b568375132c0ca33c))
* **Twitter:** Added `App Icon and NavBar Customisation` settings ([7e67d2f](https://github.com/crimera/piko/commit/7e67d2f84049007fe0b836567fd5572ad62b8a43))
* **Twitter:** Added `Undo Post Patch` in mod settings ([54c722e](https://github.com/crimera/piko/commit/54c722e409e3367a9a1e42a7c51878ba81c3aa98))
* **Twitter:** Hide `Pinned posts by followers` ([5807471](https://github.com/crimera/piko/commit/5807471dda09eb379337f55edd650be12e66ff57))

# [1.15.0](https://github.com/crimera/piko/compare/v1.14.0...v1.15.0) (2024-03-27)


### Bug Fixes

* Activate release so i can test ([34b7227](https://github.com/crimera/piko/commit/34b7227934bc77e024d37ae5f14b719a9d165288))
* Change run config ([18d4dce](https://github.com/crimera/piko/commit/18d4dcebb67a568e460931c6cd2003a0df7ae5ec))
* Remove semicolon ;-) ([46e60f7](https://github.com/crimera/piko/commit/46e60f71d3eefc2eb81fbca01451d52731c397c1))
* **Twitter:** fix `Reader mode` for 10.34 ([01cb8c9](https://github.com/crimera/piko/commit/01cb8c94c951c103f0ebea56f8daf88214eb77ae))


### Features

* Add support for patches.json ([f5f63a3](https://github.com/crimera/piko/commit/f5f63a330a06d03a10e1a3232d96764fc2d1ac7c))
* **Twitter:** Add `Dynamic color` patch from revanced ([d2bd0ef](https://github.com/crimera/piko/commit/d2bd0ef4a6c36650edde7c6d8c0c7587dcfb46ac))
* **Twitter:** Add `Premium settings` patch, Unlocks premium options from settings like custom app icon and custom navbar ([066e953](https://github.com/crimera/piko/commit/066e9538a983e6c261a56b1b568375132c0ca33c))
* **Twitter:** Added `App Icon and NavBar Customisation` settings ([7e67d2f](https://github.com/crimera/piko/commit/7e67d2f84049007fe0b836567fd5572ad62b8a43))
* **Twitter:** Added `Undo Post Patch` in mod settings ([54c722e](https://github.com/crimera/piko/commit/54c722e409e3367a9a1e42a7c51878ba81c3aa98))
* **Twitter:** Hide `Pinned posts by followers` ([5807471](https://github.com/crimera/piko/commit/5807471dda09eb379337f55edd650be12e66ff57))

# [1.16.0-dev.1](https://github.com/crimera/piko/compare/v1.15.0-dev.3...v1.16.0-dev.1) (2024-03-27)


### Bug Fixes

* Activate release so i can test ([34b7227](https://github.com/crimera/piko/commit/34b7227934bc77e024d37ae5f14b719a9d165288))
* Remove semicolon ;-) ([46e60f7](https://github.com/crimera/piko/commit/46e60f71d3eefc2eb81fbca01451d52731c397c1))

# [1.16.0-dev.1](https://github.com/crimera/piko/compare/v1.15.0-dev.3...v1.16.0-dev.1) (2024-03-27)


### Bug Fixes

* Activate release so i can test ([34b7227](https://github.com/crimera/piko/commit/34b7227934bc77e024d37ae5f14b719a9d165288))

# [1.16.0-dev.1](https://github.com/crimera/piko/compare/v1.15.0-dev.3...v1.16.0-dev.1) (2024-03-27)



### Bug Fixes

* Change run config ([18d4dce](https://github.com/crimera/piko/commit/18d4dcebb67a568e460931c6cd2003a0df7ae5ec))
* **Twitter:** fix `Reader mode` for 10.34 ([01cb8c9](https://github.com/crimera/piko/commit/01cb8c94c951c103f0ebea56f8daf88214eb77ae))


### Features

* Add support for patches.json ([f5f63a3](https://github.com/crimera/piko/commit/f5f63a330a06d03a10e1a3232d96764fc2d1ac7c))
* **Twitter:** Add `Dynamic color` patch from revanced ([d2bd0ef](https://github.com/crimera/piko/commit/d2bd0ef4a6c36650edde7c6d8c0c7587dcfb46ac))
* **Twitter:** Add `Premium settings` patch, Unlocks premium options from settings like custom app icon and custom navbar ([066e953](https://github.com/crimera/piko/commit/066e9538a983e6c261a56b1b568375132c0ca33c))
* **Twitter:** Added `App Icon and NavBar Customisation` settings ([7e67d2f](https://github.com/crimera/piko/commit/7e67d2f84049007fe0b836567fd5572ad62b8a43))
* **Twitter:** Added `Undo Post Patch` in mod settings ([54c722e](https://github.com/crimera/piko/commit/54c722e409e3367a9a1e42a7c51878ba81c3aa98))
* **Twitter:** Hide `Pinned posts by followers` ([5807471](https://github.com/crimera/piko/commit/5807471dda09eb379337f55edd650be12e66ff57))

# [1.15.0](https://github.com/crimera/piko/compare/v1.14.0...v1.15.0) (2024-03-27)


### Bug Fixes

* Change run config ([18d4dce](https://github.com/crimera/piko/commit/18d4dcebb67a568e460931c6cd2003a0df7ae5ec))
* **Twitter:** fix `Reader mode` for 10.34 ([01cb8c9](https://github.com/crimera/piko/commit/01cb8c94c951c103f0ebea56f8daf88214eb77ae))


### Features

* Add support for patches.json ([f5f63a3](https://github.com/crimera/piko/commit/f5f63a330a06d03a10e1a3232d96764fc2d1ac7c))
* **Twitter:** Add `Dynamic color` patch from revanced ([d2bd0ef](https://github.com/crimera/piko/commit/d2bd0ef4a6c36650edde7c6d8c0c7587dcfb46ac))
* **Twitter:** Add `Premium settings` patch, Unlocks premium options from settings like custom app icon and custom navbar ([066e953](https://github.com/crimera/piko/commit/066e9538a983e6c261a56b1b568375132c0ca33c))
* **Twitter:** Added `App Icon and NavBar Customisation` settings ([7e67d2f](https://github.com/crimera/piko/commit/7e67d2f84049007fe0b836567fd5572ad62b8a43))
* **Twitter:** Added `Undo Post Patch` in mod settings ([54c722e](https://github.com/crimera/piko/commit/54c722e409e3367a9a1e42a7c51878ba81c3aa98))
* **Twitter:** Hide `Pinned posts by followers` ([5807471](https://github.com/crimera/piko/commit/5807471dda09eb379337f55edd650be12e66ff57))

# [1.15.0](https://github.com/crimera/piko/compare/v1.14.0...v1.15.0) (2024-03-27)


### Bug Fixes

* Change run config ([18d4dce](https://github.com/crimera/piko/commit/18d4dcebb67a568e460931c6cd2003a0df7ae5ec))
* **Twitter:** fix `Reader mode` for 10.34 ([01cb8c9](https://github.com/crimera/piko/commit/01cb8c94c951c103f0ebea56f8daf88214eb77ae))


### Features

* Add support for patches.json ([f5f63a3](https://github.com/crimera/piko/commit/f5f63a330a06d03a10e1a3232d96764fc2d1ac7c))
* **Twitter:** Add `Dynamic color` patch from revanced ([d2bd0ef](https://github.com/crimera/piko/commit/d2bd0ef4a6c36650edde7c6d8c0c7587dcfb46ac))
* **Twitter:** Add `Premium settings` patch, Unlocks premium options from settings like custom app icon and custom navbar ([066e953](https://github.com/crimera/piko/commit/066e9538a983e6c261a56b1b568375132c0ca33c))
* **Twitter:** Added `App Icon and NavBar Customisation` settings ([7e67d2f](https://github.com/crimera/piko/commit/7e67d2f84049007fe0b836567fd5572ad62b8a43))
* **Twitter:** Added `Undo Post Patch` in mod settings ([54c722e](https://github.com/crimera/piko/commit/54c722e409e3367a9a1e42a7c51878ba81c3aa98))
* **Twitter:** Hide `Pinned posts by followers` ([5807471](https://github.com/crimera/piko/commit/5807471dda09eb379337f55edd650be12e66ff57))


# [1.15.0-dev.3](https://github.com/crimera/piko/compare/v1.15.0-dev.2...v1.15.0-dev.3) (2024-03-27)


### Bug Fixes

* Change run config ([18d4dce](https://github.com/crimera/piko/commit/18d4dcebb67a568e460931c6cd2003a0df7ae5ec))
* **Twitter:** fix `Reader mode` for 10.34 ([01cb8c9](https://github.com/crimera/piko/commit/01cb8c94c951c103f0ebea56f8daf88214eb77ae))


### Features

* Add support for patches.json ([f5f63a3](https://github.com/crimera/piko/commit/f5f63a330a06d03a10e1a3232d96764fc2d1ac7c))
* **Twitter:** Add `Premium settings` patch, Unlocks premium options from settings like custom app icon and custom navbar ([066e953](https://github.com/crimera/piko/commit/066e9538a983e6c261a56b1b568375132c0ca33c))
* **Twitter:** Added `App Icon and NavBar Customisation` settings ([7e67d2f](https://github.com/crimera/piko/commit/7e67d2f84049007fe0b836567fd5572ad62b8a43))
* **Twitter:** Added `Undo Post Patch` in mod settings ([54c722e](https://github.com/crimera/piko/commit/54c722e409e3367a9a1e42a7c51878ba81c3aa98))

# [1.15.0-dev.3](https://github.com/crimera/piko/compare/v1.15.0-dev.2...v1.15.0-dev.3) (2024-03-27)


### Bug Fixes

* Change run config ([18d4dce](https://github.com/crimera/piko/commit/18d4dcebb67a568e460931c6cd2003a0df7ae5ec))
* **Twitter:** fix `Reader mode` for 10.34 ([01cb8c9](https://github.com/crimera/piko/commit/01cb8c94c951c103f0ebea56f8daf88214eb77ae))


### Features

* Add support for patches.json ([f5f63a3](https://github.com/crimera/piko/commit/f5f63a330a06d03a10e1a3232d96764fc2d1ac7c))
* **Twitter:** Add `Premium settings` patch, Unlocks premium options from settings like custom app icon and custom navbar ([066e953](https://github.com/crimera/piko/commit/066e9538a983e6c261a56b1b568375132c0ca33c))
* **Twitter:** Added `App Icon and NavBar Customisation` settings ([7e67d2f](https://github.com/crimera/piko/commit/7e67d2f84049007fe0b836567fd5572ad62b8a43))
* **Twitter:** Added `Undo Post Patch` in mod settings ([54c722e](https://github.com/crimera/piko/commit/54c722e409e3367a9a1e42a7c51878ba81c3aa98))

# [1.15.0-dev.3](https://github.com/crimera/piko/compare/v1.15.0-dev.2...v1.15.0-dev.3) (2024-03-27)


### Bug Fixes

* Change run config ([18d4dce](https://github.com/crimera/piko/commit/18d4dcebb67a568e460931c6cd2003a0df7ae5ec))
* **Twitter:** fix `Reader mode` for 10.34 ([01cb8c9](https://github.com/crimera/piko/commit/01cb8c94c951c103f0ebea56f8daf88214eb77ae))


### Features

* Add support for patches.json ([f5f63a3](https://github.com/crimera/piko/commit/f5f63a330a06d03a10e1a3232d96764fc2d1ac7c))
* **Twitter:** Add `Premium settings` patch, Unlocks premium options from settings like custom app icon and custom navbar ([066e953](https://github.com/crimera/piko/commit/066e9538a983e6c261a56b1b568375132c0ca33c))
* **Twitter:** Added `App Icon and NavBar Customisation` settings ([7e67d2f](https://github.com/crimera/piko/commit/7e67d2f84049007fe0b836567fd5572ad62b8a43))
* **Twitter:** Added `Undo Post Patch` in mod settings ([54c722e](https://github.com/crimera/piko/commit/54c722e409e3367a9a1e42a7c51878ba81c3aa98))

# [1.15.0-dev.3](https://github.com/crimera/piko/compare/v1.15.0-dev.2...v1.15.0-dev.3) (2024-03-27)


### Bug Fixes

* Change run config ([18d4dce](https://github.com/crimera/piko/commit/18d4dcebb67a568e460931c6cd2003a0df7ae5ec))
* **Twitter:** fix `Reader mode` for 10.34 ([01cb8c9](https://github.com/crimera/piko/commit/01cb8c94c951c103f0ebea56f8daf88214eb77ae))


### Features

* Add support for patches.json ([f5f63a3](https://github.com/crimera/piko/commit/f5f63a330a06d03a10e1a3232d96764fc2d1ac7c))
* **Twitter:** Add `Premium settings` patch, Unlocks premium options from settings like custom app icon and custom navbar ([066e953](https://github.com/crimera/piko/commit/066e9538a983e6c261a56b1b568375132c0ca33c))
* **Twitter:** Added `App Icon and NavBar Customisation` settings ([7e67d2f](https://github.com/crimera/piko/commit/7e67d2f84049007fe0b836567fd5572ad62b8a43))
* **Twitter:** Added `Undo Post Patch` in mod settings ([54c722e](https://github.com/crimera/piko/commit/54c722e409e3367a9a1e42a7c51878ba81c3aa98))

# [1.15.0-dev.3](https://github.com/crimera/piko/compare/v1.15.0-dev.2...v1.15.0-dev.3) (2024-03-27)


### Bug Fixes

* Change run config ([18d4dce](https://github.com/crimera/piko/commit/18d4dcebb67a568e460931c6cd2003a0df7ae5ec))


### Features

* Add support for patches.json ([f5f63a3](https://github.com/crimera/piko/commit/f5f63a330a06d03a10e1a3232d96764fc2d1ac7c))
* **Twitter:** Add `Premium settings` patch, Unlocks premium options from settings like custom app icon and custom navbar ([066e953](https://github.com/crimera/piko/commit/066e9538a983e6c261a56b1b568375132c0ca33c))

# [1.15.0-dev.3](https://github.com/crimera/piko/compare/v1.15.0-dev.2...v1.15.0-dev.3) (2024-03-27)


### Bug Fixes

* Change run config ([18d4dce](https://github.com/crimera/piko/commit/18d4dcebb67a568e460931c6cd2003a0df7ae5ec))


### Features

* Add support for patches.json ([f5f63a3](https://github.com/crimera/piko/commit/f5f63a330a06d03a10e1a3232d96764fc2d1ac7c))
* **Twitter:** Add `Premium settings` patch, Unlocks premium options from settings like custom app icon and custom navbar ([066e953](https://github.com/crimera/piko/commit/066e9538a983e6c261a56b1b568375132c0ca33c))

# [1.15.0-dev.3](https://github.com/crimera/piko/compare/v1.15.0-dev.2...v1.15.0-dev.3) (2024-03-25)


### Bug Fixes

* Change run config ([18d4dce](https://github.com/crimera/piko/commit/18d4dcebb67a568e460931c6cd2003a0df7ae5ec))


### Features

* Add support for patches.json ([f5f63a3](https://github.com/crimera/piko/commit/f5f63a330a06d03a10e1a3232d96764fc2d1ac7c))
* **Twitter:** Add `Premium settings` patch, Unlocks premium options from settings like custom app icon and custom navbar ([066e953](https://github.com/crimera/piko/commit/066e9538a983e6c261a56b1b568375132c0ca33c))

# [1.15.0](https://github.com/crimera/piko/compare/v1.14.0...v1.15.0) (2024-03-22)


### Bug Fixes

* Change run config ([9dc5772](https://github.com/crimera/piko/commit/9dc5772d527189b01d10c0ce8cb22acdb9c38bcd))


### Features

* Add support for packages.json ([f133c12](https://github.com/crimera/piko/commit/f133c1205f88d6fa1b00800a98d1895a52be412a))
* **Twitter:** Add `Dynamic color` patch from revanced ([d2bd0ef](https://github.com/crimera/piko/commit/d2bd0ef4a6c36650edde7c6d8c0c7587dcfb46ac))
* **Twitter:** Hide `Pinned posts by followers` ([5807471](https://github.com/crimera/piko/commit/5807471dda09eb379337f55edd650be12e66ff57))

# [1.15.0-dev.3](https://github.com/crimera/piko/compare/v1.15.0-dev.2...v1.15.0-dev.3) (2024-03-22)


### Bug Fixes

* Change run config ([9dc5772](https://github.com/crimera/piko/commit/9dc5772d527189b01d10c0ce8cb22acdb9c38bcd))


### Features

* Add support for packages.json ([f133c12](https://github.com/crimera/piko/commit/f133c1205f88d6fa1b00800a98d1895a52be412a))

# [1.15.0-dev.2](https://github.com/crimera/piko/compare/v1.15.0-dev.1...v1.15.0-dev.2) (2024-03-22)


### Features

* **Twitter:** Hide `Pinned posts by followers` ([5807471](https://github.com/crimera/piko/commit/5807471dda09eb379337f55edd650be12e66ff57))

# [1.15.0-dev.1](https://github.com/crimera/piko/compare/v1.14.0...v1.15.0-dev.1) (2024-03-22)


### Features

* **Twitter:** Add `Dynamic color` patch from revanced ([d2bd0ef](https://github.com/crimera/piko/commit/d2bd0ef4a6c36650edde7c6d8c0c7587dcfb46ac))

# [1.14.0](https://github.com/crimera/piko/compare/v1.13.0...v1.14.0) (2024-03-22)


### Features

* **Twitter:** Add settings for `Hide FAB` patch ([d5dde7c](https://github.com/crimera/piko/commit/d5dde7cb2e843bb0c8b90b7d1d5989bcf91e712b))
* **Twitter:** Add Settings for `Hide Recommended Users` patch ([f43f027](https://github.com/crimera/piko/commit/f43f027ef6ab0d5ab0bc3433762e9200a3d8e50d))
* **Twitter:** Add settings for `Remove view count` patch ([67bb8ad](https://github.com/crimera/piko/commit/67bb8ad9837bdc21c2fffcad7851e67a8e37054d))
* **Twitter:** Added `Hide Community Notes` to mod settings ([157a32b](https://github.com/crimera/piko/commit/157a32b1e9f24157ad81c2434206f6e13404bbc3))
* **Twitter:** Added `Hide Promoted Trends` to Mod Settings ([9d29f45](https://github.com/crimera/piko/commit/9d29f45ba67ab2d682afe466c2d30af8b3421975))
* **Twitter:** Added Ad block hooks ([4811584](https://github.com/crimera/piko/commit/4811584abfb764c3539733818cf1d2dc96ef1349))

# [1.14.0-dev.5](https://github.com/crimera/piko/compare/v1.14.0-dev.4...v1.14.0-dev.5) (2024-03-22)


### Features

* **Twitter:** Added Ad block hooks ([4811584](https://github.com/crimera/piko/commit/4811584abfb764c3539733818cf1d2dc96ef1349))

# [1.14.0-dev.4](https://github.com/crimera/piko/compare/v1.14.0-dev.3...v1.14.0-dev.4) (2024-03-18)


### Features

* **Twitter:** Added `Hide Community Notes` to mod settings ([157a32b](https://github.com/crimera/piko/commit/157a32b1e9f24157ad81c2434206f6e13404bbc3))
* **Twitter:** Added `Hide Promoted Trends` to Mod Settings ([9d29f45](https://github.com/crimera/piko/commit/9d29f45ba67ab2d682afe466c2d30af8b3421975))

# [1.14.0-dev.3](https://github.com/crimera/piko/compare/v1.14.0-dev.2...v1.14.0-dev.3) (2024-03-18)


### Features

* **Twitter:** Add settings for `Remove view count` patch ([67bb8ad](https://github.com/crimera/piko/commit/67bb8ad9837bdc21c2fffcad7851e67a8e37054d))

# [1.14.0-dev.2](https://github.com/crimera/piko/compare/v1.14.0-dev.1...v1.14.0-dev.2) (2024-03-18)


### Features

* **Twitter:** Add settings for `Hide FAB` patch ([d5dde7c](https://github.com/crimera/piko/commit/d5dde7cb2e843bb0c8b90b7d1d5989bcf91e712b))

# [1.14.0-dev.1](https://github.com/crimera/piko/compare/v1.13.0...v1.14.0-dev.1) (2024-03-18)


### Features

* **Twitter:** Add Settings for `Hide Recommended Users` patch ([f43f027](https://github.com/crimera/piko/commit/f43f027ef6ab0d5ab0bc3433762e9200a3d8e50d))

# [1.13.0](https://github.com/crimera/piko/compare/v1.12.0...v1.13.0) (2024-03-17)


### Features

* Add settings for `Disable chirp font` patch ([dad0a70](https://github.com/crimera/piko/commit/dad0a70b13a281937ea608e7866b20ae30863cb9))
* **Twitter:** Add Settings for `Hide Live Threads` and `Hide Banner` patch ([ca474d4](https://github.com/crimera/piko/commit/ca474d4e7a1baa5f9cfcfd03a0640a4867089131))
* **Twitter:** Added `Hide For You` in Mod Settings ([86ea20f](https://github.com/crimera/piko/commit/86ea20f7c4309de0fcae2ba8fa2fa47990290a5a))

# [1.13.0-dev.3](https://github.com/crimera/piko/compare/v1.13.0-dev.2...v1.13.0-dev.3) (2024-03-17)


### Features

* **Twitter:** Added `Hide For You` in Mod Settings ([86ea20f](https://github.com/crimera/piko/commit/86ea20f7c4309de0fcae2ba8fa2fa47990290a5a))

# [1.13.0-dev.2](https://github.com/crimera/piko/compare/v1.13.0-dev.1...v1.13.0-dev.2) (2024-03-17)


### Features

* **Twitter:** Add Settings for `Hide Live Threads` and `Hide Banner` patch ([ca474d4](https://github.com/crimera/piko/commit/ca474d4e7a1baa5f9cfcfd03a0640a4867089131))

# [1.13.0-dev.1](https://github.com/crimera/piko/compare/v1.12.0...v1.13.0-dev.1) (2024-03-16)


### Features

* Add settings for `Disable chirp font` patch ([dad0a70](https://github.com/crimera/piko/commit/dad0a70b13a281937ea608e7866b20ae30863cb9))

# [1.12.0](https://github.com/crimera/piko/compare/v1.11.1...v1.12.0) (2024-03-16)


### Features

* **Twitter:** added `Reader Mode patch` ([1348099](https://github.com/crimera/piko/commit/134809970363b6eb76b15f1c76b779307a663cc6))
* **Twitter:** added `Undo Posts Patch` ([ced73c6](https://github.com/crimera/piko/commit/ced73c6e316dbbdfaa60858ae5bc8a75b9afc3c0))
* **Twitter:** Hide Community Notes ([ab48b40](https://github.com/crimera/piko/commit/ab48b402009c8603bd9ad9507a444742642282df))

# [1.12.0-dev.1](https://github.com/crimera/piko/compare/v1.11.1...v1.12.0-dev.1) (2024-03-16)


### Features

* **Twitter:** added `Reader Mode patch` ([1348099](https://github.com/crimera/piko/commit/134809970363b6eb76b15f1c76b779307a663cc6))
* **Twitter:** added `Undo Posts Patch` ([ced73c6](https://github.com/crimera/piko/commit/ced73c6e316dbbdfaa60858ae5bc8a75b9afc3c0))
* **Twitter:** Hide Community Notes ([ab48b40](https://github.com/crimera/piko/commit/ab48b402009c8603bd9ad9507a444742642282df))

# [1.12.0-dev.2](https://github.com/crimera/piko/compare/v1.12.0-dev.1...v1.12.0-dev.2) (2024-03-13)


### Features

* **Twitter:** added `Reader Mode patch` ([e4c9e1a](https://github.com/crimera/piko/commit/e4c9e1ade4125c1e75d3becb7fa6271f83d526de))
* **Twitter:** added `Undo Posts Patch` ([9411550](https://github.com/crimera/piko/commit/9411550e26a18bbf90ee049bc3da558350275d81))

# [1.12.0-dev.1](https://github.com/crimera/piko/compare/v1.11.1...v1.12.0-dev.1) (2024-03-11)


### Features

* **Twitter:** Hide Community Notes ([2f018a4](https://github.com/crimera/piko/commit/2f018a443202d90c7de6324de00becca57a3de21))

## [1.11.1](https://github.com/crimera/piko/compare/v1.11.0...v1.11.1) (2024-03-08)


### Bug Fixes

* **Twitter:** Bring back download and viewcount patch ([3eea91f](https://github.com/crimera/piko/commit/3eea91f06a65433275bbd7437b290212654a308d))

## [1.11.1-dev.1](https://github.com/crimera/piko/compare/v1.11.0...v1.11.1-dev.1) (2024-03-08)


### Bug Fixes

* **Twitter:** Brick back download and viewcount patch ([8bf9de4](https://github.com/crimera/piko/commit/8bf9de44a875c2cfd3d248df5a6aa4d1e18903ee))

# [1.11.0](https://github.com/crimera/piko/compare/v1.10.0...v1.11.0) (2024-03-08)


### Bug Fixes

* **Twitter:** Download patch ([f87dee7](https://github.com/crimera/piko/commit/f87dee798632528595ac3efab1349d7a3fecafa0))
* unshort url for 10.30 ([ef3e00f](https://github.com/crimera/piko/commit/ef3e00f80fc469e5f09f8d7e43136791dbd94872))


### Features

* no t.co links ([776e700](https://github.com/crimera/piko/commit/776e7007b571cc2803d566891c72a42057297bb6))
* **Twitter:** Disable in favor of official patch bundle ([9a81fab](https://github.com/crimera/piko/commit/9a81fabed7457d1be967250faa548a67accc27bc))
* **Twitter:** Hide FAB ([4cdf50f](https://github.com/crimera/piko/commit/4cdf50fb036ed8596c9e4a3e3f1702ae78525d28))
* **Twitter:** Hide FAB Menu Buttons ([4f25bd6](https://github.com/crimera/piko/commit/4f25bd60888b70ccd4518a42459583b6ef8fcc54))
* **Twitter:** Hide Promoted Trends ([1be736e](https://github.com/crimera/piko/commit/1be736e31ae11d21a62f7a3f4aad2689647ada92))

# [1.11.0-dev.3](https://github.com/crimera/piko/compare/v1.11.0-dev.2...v1.11.0-dev.3) (2024-03-08)


### Bug Fixes

* **Twitter:** Download patch ([f87dee7](https://github.com/crimera/piko/commit/f87dee798632528595ac3efab1349d7a3fecafa0))

# [1.11.0-dev.2](https://github.com/crimera/piko/compare/v1.11.0-dev.1...v1.11.0-dev.2) (2024-03-04)


### Features

* **Twitter:** Hide FAB ([4cdf50f](https://github.com/crimera/piko/commit/4cdf50fb036ed8596c9e4a3e3f1702ae78525d28))
* **Twitter:** Hide FAB Menu Buttons ([4f25bd6](https://github.com/crimera/piko/commit/4f25bd60888b70ccd4518a42459583b6ef8fcc54))
* **Twitter:** Hide Promoted Trends ([1be736e](https://github.com/crimera/piko/commit/1be736e31ae11d21a62f7a3f4aad2689647ada92))

# [1.11.0-dev.1](https://github.com/crimera/piko/compare/v1.10.0...v1.11.0-dev.1) (2024-03-03)


### Bug Fixes

* unshort url for 10.30 ([ef3e00f](https://github.com/crimera/piko/commit/ef3e00f80fc469e5f09f8d7e43136791dbd94872))


### Features

* no t.co links ([776e700](https://github.com/crimera/piko/commit/776e7007b571cc2803d566891c72a42057297bb6))
* **Twitter:** Disable in favor of official patch bundle ([9a81fab](https://github.com/crimera/piko/commit/9a81fabed7457d1be967250faa548a67accc27bc))

# [1.10.0](https://github.com/crimera/piko/compare/v1.9.1...v1.10.0) (2024-03-03)


### Bug Fixes

* **Twitter:** Fixup patch dependecy ([867926a](https://github.com/crimera/piko/commit/867926acccd1b768b63475aa79d376940a87e075))


### Features

* **Twitter:** Add `Disable chirp font` patch ([59eafd1](https://github.com/crimera/piko/commit/59eafd1ecf9459123fd8565121fd0a42404956bc))

## [1.9.1](https://github.com/crimera/piko/compare/v1.9.0...v1.9.1) (2024-03-02)


### Bug Fixes

* **Twitter:** Only show settings category if patch is applied ([2141680](https://github.com/crimera/piko/commit/21416800ce7176c713b1521649d9760fcd450158))

## [1.9.1-dev.1](https://github.com/crimera/piko/compare/v1.9.0...v1.9.1-dev.1) (2024-03-02)


### Bug Fixes

* **Twitter:** Only show settings category if patch is applied ([2141680](https://github.com/crimera/piko/commit/21416800ce7176c713b1521649d9760fcd450158))

# [1.9.0](https://github.com/crimera/piko/compare/v1.8.1...v1.9.0) (2024-03-02)


### Features

* **Twitter:** Allow choosing public folder ([6c36202](https://github.com/crimera/piko/commit/6c362027f608ea2a84407d681d9b47775ccb0f4d))

# [1.9.0-dev.1](https://github.com/crimera/piko/compare/v1.8.1...v1.9.0-dev.1) (2024-03-02)


### Features

* **Twitter:** Allow choosing public folder ([6c36202](https://github.com/crimera/piko/commit/6c362027f608ea2a84407d681d9b47775ccb0f4d))

## [1.8.1](https://github.com/crimera/piko/compare/v1.8.0...v1.8.1) (2024-03-02)


### Bug Fixes

* Update pull_request.yml[skip ci] ([30e1e4f](https://github.com/crimera/piko/commit/30e1e4f828e4018b3595ea99d21d2a527145b2f5))

# [1.8.0](https://github.com/crimera/piko/compare/v1.7.0...v1.8.0) (2024-03-02)


### Features

* **Twitter:** Add `Custom download folder` ([7c93541](https://github.com/crimera/piko/commit/7c935415329303066cd69753bdeba9657be0f742))
* **Twitter:** Add back settings ([5ba3580](https://github.com/crimera/piko/commit/5ba3580ec94a158c2e1043393aab76a0fae3481c))

# [1.8.0-dev.2](https://github.com/crimera/piko/compare/v1.8.0-dev.1...v1.8.0-dev.2) (2024-03-02)


### Features

* **Twitter:** Add `Custom download folder` ([7c93541](https://github.com/crimera/piko/commit/7c935415329303066cd69753bdeba9657be0f742))

# [1.8.0-dev.1](https://github.com/crimera/piko/compare/v1.7.0...v1.8.0-dev.1) (2024-03-02)


### Features

* **Twitter:** Add back settings ([5ba3580](https://github.com/crimera/piko/commit/5ba3580ec94a158c2e1043393aab76a0fae3481c))

# [1.7.0](https://github.com/crimera/piko/compare/v1.6.0...v1.7.0) (2024-03-02)


### Bug Fixes

* **Twitter:** disable `Hide For You` by default. ([b933c19](https://github.com/crimera/piko/commit/b933c196f8325a02e82520419c049260cc3a4590))


### Features

* **Twitter:** Hide Banner ([b653c84](https://github.com/crimera/piko/commit/b653c84d4992be90386ce3e9031b138a4310b9fa))
* **Twitter:** Hide Live Threads ([0202771](https://github.com/crimera/piko/commit/0202771020f97c32370d9ee7d110399eae6c264c))

# [1.7.0-dev.2](https://github.com/crimera/piko/compare/v1.7.0-dev.1...v1.7.0-dev.2) (2024-03-02)


### Bug Fixes

* **Twitter:** disable `Hide For You` by default. ([b933c19](https://github.com/crimera/piko/commit/b933c196f8325a02e82520419c049260cc3a4590))

# [1.7.0-dev.1](https://github.com/crimera/piko/compare/v1.6.0...v1.7.0-dev.1) (2024-03-02)


### Features

* **Twitter:** Hide Banner ([b653c84](https://github.com/crimera/piko/commit/b653c84d4992be90386ce3e9031b138a4310b9fa))
* **Twitter:** Hide Live Threads ([0202771](https://github.com/crimera/piko/commit/0202771020f97c32370d9ee7d110399eae6c264c))

# [1.6.0](https://github.com/crimera/piko/compare/v1.5.1...v1.6.0) (2024-03-01)


### Features

* **Twitter:** Hide For You ([c18f143](https://github.com/crimera/piko/commit/c18f1437882d6aba85c076a198a1a3c87067b716))
* **Twitter:** Hide Recommended Users ([2a55292](https://github.com/crimera/piko/commit/2a55292f81cd2d6f06058c8b4f347016700695eb))

# [1.6.0-dev.1](https://github.com/crimera/piko/compare/v1.5.1...v1.6.0-dev.1) (2024-03-01)


### Features

* **Twitter:** Hide For You ([c18f143](https://github.com/crimera/piko/commit/c18f1437882d6aba85c076a198a1a3c87067b716))
* **Twitter:** Hide Recommended Users ([2a55292](https://github.com/crimera/piko/commit/2a55292f81cd2d6f06058c8b4f347016700695eb))

## [1.5.1](https://github.com/crimera/piko/compare/v1.5.0...v1.5.1) (2024-03-01)


### Reverts

* Revert "feat(Twitter): Added mod settings" ([c252333](https://github.com/crimera/piko/commit/c2523332865ce6338361e49a6c862d491ef3323b))

# [1.5.0](https://github.com/crimera/piko/compare/v1.4.0...v1.5.0) (2024-02-27)


### Features

* **Twitter:** Added mod settings ([76ee8a1](https://github.com/crimera/piko/commit/76ee8a1cfbfe895d006d57afd0c56ed2373d96e6))

# [1.4.0](https://github.com/crimera/piko/compare/v1.3.2...v1.4.0) (2024-02-10)


### Features

* **twitter:** Add `Clear tracking params` patch @FrozenAlex's ([c1f3148](https://github.com/crimera/piko/commit/c1f3148c8b701d76fa518256b320552b9b8a2dd7))

## [1.3.2](https://github.com/crimera/piko/compare/v1.3.1...v1.3.2) (2024-02-10)


### Bug Fixes

* **twitter:** Exclude `Google Ads Patch` by default ([a26e03a](https://github.com/crimera/piko/commit/a26e03a157815445d8416a68dd43aa96b2859ce1))

## [1.3.1](https://github.com/crimera/piko/compare/v1.3.0...v1.3.1) (2024-02-10)


### Bug Fixes

* **twitter:** Remove debug code ([2eb2fa3](https://github.com/crimera/piko/commit/2eb2fa34e101a6d838b28eabc3c0c7ae08af9dc4))

# [1.3.0](https://github.com/crimera/piko/compare/v1.2.1...v1.3.0) (2024-02-08)


### Features

* **twitter:** Add Show sensitive media patch ([21c3fb4](https://github.com/crimera/piko/commit/21c3fb429b82acfb3a6b497c06c2a4e2ad596a9f))

## [1.2.1](https://github.com/crimera/piko/compare/v1.2.0...v1.2.1) (2024-02-08)


### Bug Fixes

* **twitter:** Add twitter to compatible package for SelectableTextPatch ([0b6ee33](https://github.com/crimera/piko/commit/0b6ee33a044433c498c7d522b92b33623dca1a93))

# [1.2.0](https://github.com/crimera/piko/compare/v1.1.0...v1.2.0) (2024-02-08)


### Features

* **twitter:** Add selectable text patch ([ee3bcf1](https://github.com/crimera/piko/commit/ee3bcf17900dfe549a07426182a2149bcf16f87a))

# [1.1.0](https://github.com/crimera/piko/compare/v1.0.0...v1.1.0) (2024-01-31)


### Features

* **twitter:** Add google ads patch ([152b118](https://github.com/crimera/piko/commit/152b118737f84ebc155377ca58c7131486bad8a9))

# 1.0.0 (2024-01-30)


### Bug Fixes

* **twitter:** Refactor packages ([8a5cea6](https://github.com/crimera/piko/commit/8a5cea659350a774fe97ec52bc467f10ba1be341))


### Features

* **twitter:** Add download patch ([b9612bf](https://github.com/crimera/piko/commit/b9612bfc69656681f3b84d0c8837e5d3dad9bc0d))
* **twitter:** Add remove view count patch ([e31f222](https://github.com/crimera/piko/commit/e31f22278e6a5704037f3e3ed1583c25025abed8))

# 1.0.0 (2024-01-26)


### Features

* Init ([66be625](https://github.com/ReVanced/revanced-patches-template/commit/66be625f25ee2d678dac62a5bf4daa631284f8f6))

# 1.0.0-dev.1 (2024-01-26)


### Features

* Init ([66be625](https://github.com/ReVanced/revanced-patches-template/commit/66be625f25ee2d678dac62a5bf4daa631284f8f6))
