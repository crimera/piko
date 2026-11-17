<div align="center">

<img src="https://socialify.git.ci/crimera/piko/image?forks=1&language=1&name=1&owner=1&pattern=Circuit%20Board&stargazers=1&theme=Auto" alt="piko" width="640" height="320" />

<h1>
    Morphe patches focused on Twitter/X & Instagram<br>
    <a href="https://t.me/pikopatches">
        <img src="https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram"/>
    </a>
    <a href="https://crowdin.com/project/piko">
        <img src="https://img.shields.io/badge/Crowdin-1B263B?style=for-the-badge&logo=crowdin&logoColor=white" alt="Crowdin"/>
    </a>
</h1>

</div>

## 🕹️ Usage

Starting with patches v3.0.0, Piko patches use [Morphe](https://morphe.software).

### Morphe Manager

Tap the button to add Piko as a patch source in Morphe Manager:

<a href="https://morphe.software/add-source?github=crimera/piko">
  <img src="docs/images/add-to-morphe.svg" alt="Add to Morphe" height="48">
</a>

Then patch Twitter/X or Instagram:
1. Tap X or Instagram app icon in Morphe
2. Download original APKM file from ApkMirror. Do _not_ unspilt or modify the file, Morphe patches APKM directly
3. Wait for patching to complete, install

### Morphe CLI

```sh
java -jar cli.jar patch --patches piko.mpp input.apkm
```

### X-Shim

Starting `12.5.0-release.0` **you need not** apply X-Shim along with Piko patches. X login and XChat will work fine without X-Shim patches.

<details>

<summary>Open old information</summary>

<del>

To patch with Twitter/X version 11.88 to 12.4, you must include patches from another repo/project called [X-Shim](https://gitlab.com/inotia00/x-shim) developed by [@inotia00](https://github.com/inotia00)

[➕ Add X-Shim to Morphe](https://morphe.software/add-source?gitlab=inotia00/x-shim)

⚠️Important steps⚠️
* Enable `expert mode` in Morphe Manager.
* You must select all patches from the X Shim bundle and recommended/preferred patches from Piko. **DO NOT INCLUDE** patches from any other bundles.
* Select "Proceed anyway" at the prompt warning of using multiple bundles.

⚠️Additional Context⚠️

* An active internet connection is required during patching.
* XChat decryption and XChat video calls work. 
* The X Shim patch only adds a compatibility shim layer and pairip is not removed.

> For an up-to-date patching guide, [follow this Reddit guide](https://www.reddit.com/r/MorpheApp/comments/1r4xt24/x_twitter_can_now_be_patched_with_piko_patches/).

</del>

</details>

## ⚙️ Patch Details

<!-- PATCHES_START -->
> **[v3.12.0-dev.3](https://github.com/crimera/piko/releases/tag/v3.12.0-dev.3)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;194 patches total
<details>
<summary>📦 NewX&nbsp;&nbsp;•&nbsp;&nbsp;52 patches</summary>
<br>

**🎯 Supported versions:**

| 12.41.0-prod.01 | 12.43.0-prod.01 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable Play Store updates](#disable-play-store-updates) | Prevents the patched app from being listed in Play Store updates by setting the version code to the maximum allowed. This also allows downgrading an existing app install to an older app version. |  |
| [NewX: Browse tweet object](#newx-browse-tweet-object) | Adds a debug option to browse the tweet object in NewX post menus. |  |
| [NewX: Clone](#newx-clone) | Changes the package name and the app name. This allows you to install the patched X alongside the original X or other patched X. | • Package name<br>• App name |
| [NewX: Crash logs](#newx-crash-logs) | Saves crash logs and shows a notification with share and copy actions. |  |
| [NewX: Custom font](#newx-custom-font) | Customise font style |  |
| [NewX: Custom sharing domain](#newx-custom-sharing-domain) | Allows for using domains like fxtwitter when sharing tweets/posts. |  |
| [NewX: Customize drawer items](#newx-customize-drawer-items) | Lets you hide selected items from the NewX navigation drawer, and optionally add Messages and Grok shortcuts. |  |
| [NewX: Customize inline actions](#newx-customize-inline-actions) | Lets you hide selected actions from NewX post action bars. |  |
| [NewX: Customize media menu items](#newx-customize-media-menu-items) | Lets you hide selected items from the NewX photo and video long-press menu. |  |
| [NewX: Customize navigation bar](#newx-customize-navigation-bar) | Reorder, hide, and replace NewX bottom navigation bar items. |  |
| [NewX: Customize post menu items](#newx-customize-post-menu-items) | Lets you hide selected items from the NewX post menu. |  |
| [NewX: Customize profile tabs](#newx-customize-profile-tabs) | Reorder and hide the tabs shown on profile screens. |  |
| [NewX: Customize timeline tabs](#newx-customize-timeline-tabs) | Lets you hide the For You or Following tab from the NewX home timeline. |  |
| [NewX: Disable automatic timeline refresh](#newx-disable-automatic-timeline-refresh) | Prevents automatic timeline jumps on startup and foregrounding. |  |
| [NewX: Disable blur effects](#newx-disable-blur-effects) | Disables Haze blur in NewX Compose UI while preserving configured fallback tint and scrim effects. |  |
| [NewX: Disable video player scrolling](#newx-disable-video-player-scrolling) | Disables vertical swipes in the NewX video player while keeping playback controls and other gestures available. |  |
| [NewX: Enable quality-of-life feature flags](#newx-enable-quality-of-life-feature-flags) | Enables profile Photos and Videos tabs and sharing post screenshots. |  |
| [NewX: Feature switch overrides](#newx-feature-switch-overrides) | Adds searchable, typed NewX feature switch overrides. |  |
| [NewX: Filter For You by topic](#newx-filter-for-you-by-topic) | Restricts the NewX For You timeline to selected topics. |  |
| [NewX: Filter posts by keyword](#newx-filter-posts-by-keyword) | Filters NewX posts using user-defined words and phrases. |  |
| [NewX: Gallery profile Photos tab](#newx-gallery-profile-photos-tab) | Replaces the profile Photos timeline with a three-column media gallery. |  |
| [NewX: Hide AI-generated posts](#newx-hide-ai-generated-posts) | Hides selected AI-generated posts from NewX timelines. |  |
| [NewX: Hide Discover more](#newx-hide-discover-more) | Removes the Discover more module from post-detail timelines. |  |
| [NewX: Hide Spaces bar](#newx-hide-spaces-bar) | Hides the Spaces bar above NewX timelines. |  |
| [NewX: Hide compose button](#newx-hide-compose-button) | Removes the compose button from NewX timelines. |  |
| [NewX: Hide new posts pill](#newx-hide-new-posts-pill) | Hides the new posts pill in NewX timelines. |  |
| [NewX: Hide post author actions](#newx-hide-post-author-actions) | Hides the Follow and Message buttons for post authors on post details. |  |
| [NewX: Hide post dividers](#newx-hide-post-dividers) | Removes post and reply dividers shown in NewX timelines. |  |
| [NewX: Hide post reply bar](#newx-hide-post-reply-bar) | Hides the persistent post-detail reply bar while keeping the compose button available. |  |
| [NewX: Hide posts by verified account type](#newx-hide-posts-by-verified-account-type) | Hides posts and replies authored by selected timeline-reported verification types. |  |
| [NewX: Hide premium upsell](#newx-hide-premium-upsell) | Hides the premium upsell chip from the NewX home top bar. |  |
| [NewX: Hide timeline tabs bar](#newx-hide-timeline-tabs-bar) | Removes the For You and Following tabs bar from NewX home timelines, with an option to hide the add-tab (+) button. |  |
| [NewX: Hide who to follow](#newx-hide-who-to-follow) | Hides recommended-user sections ("Who to follow") from NewX timelines and profile pages. |  |
| [NewX: Inline download button](#newx-inline-download-button) | Adds a Download button below NewX posts and saves media to a folder you choose, with a customizable filename template. |  |
| [NewX: Open canonical URLs](#newx-open-canonical-urls) | Opens the expanded (canonical) URL directly when clicking links instead of the shortened t.co link. |  |
| [NewX: Permanent share links](#newx-permanent-share-links) | Uses stable account and post IDs in shared links when enabled. |  |
| [NewX: Redirect downloads to chosen folder](#newx-redirect-downloads-to-chosen-folder) | Saves media downloaded with NewX's own download buttons into the folders chosen for the download feature, using the configured filename template. |  |
| [NewX: Remove ads](#newx-remove-ads) | Filters promoted posts and modules from NewX timelines. |  |
| [NewX: Remove share tracking parameters](#newx-remove-share-tracking-parameters) | Adds a Links setting to remove the a= and s= attribution parameters from shared X URLs. |  |
| [NewX: Restore Twitter branding](#newx-restore-twitter-branding) | Bring back old Twitter icons and name |  |
| [NewX: Restore pinned home tab](#newx-restore-pinned-home-tab) | Reopens the pinned list, topic or community tab you last viewed when the app starts. |  |
| [NewX: Restore timeline position](#newx-restore-timeline-position) | Persists supported timeline positions, then restores them after the app process restarts. |  |
| [NewX: Server error logging](#newx-server-error-logging) | Captures parsed NewX server errors in memory and exports them on demand. |  |
| [NewX: Set default media tab](#newx-set-default-media-tab) | Lets you choose the default sub-tab (Photos or Videos) for the NewX profile media tab. |  |
| [NewX: Set default profile post sorting](#newx-set-default-profile-post-sorting) | Lets you choose whether profile posts open sorted by the most recent or popular posts. |  |
| [NewX: Set default reply sorting](#newx-set-default-reply-sorting) | Lets you choose the default reply sorting order for NewX post detail. |  |
| [NewX: Show poll results](#newx-show-poll-results) | Adds an option to show NewX poll results without voting. |  |
| [NewX: Show sensitive media](#newx-show-sensitive-media) | Shows sensitive and age-restricted media without confirmation in NewX. |  |
| [NewX: Theme](#newx-theme) | Chooses between the original NewX colors and the Material You, high-contrast, and Dim themes. |  |
| [NewX: Translator](#newx-translator) | Adds a Translate post-menu option and optional inline translation with free or custom translators (Google, FxTwitter, OpenAI-compatible, DeepL, LibreTranslate). |  |
| [NewX: Unlock color customization](#newx-unlock-color-customization) | Unlocks the built-in app color selector for non-Premium users. |  |
| [NewX: Unlock downloads](#newx-unlock-downloads) | Unlocks media downloads and offline video saving in NewX. |  |

</details>

<details>
<summary>📦 Instagram&nbsp;&nbsp;•&nbsp;&nbsp;67 patches</summary>
<br>

**🎯 Supported versions:**

| 452.1.0.53.64 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Add settings](#add-settings) | Adds settings to control preferences are patching |  |
| [Change like animation](#change-like-animation) | Change the animation to one from existing Rings like animations |  |
| [Clone](#clone) | Changes the package name and the app name. This allows you to install the patched app alongside the original Instagram app. | • Package name<br>• App name |
| [Copy comment](#copy-comment) | Adds buttons to copy comments and their authors' usernames on posts and reels. |  |
| [Custom font](#custom-font) | Adds an option to replace the app font with a font file from the device storage. |  |
| [Custom sharing domain](#custom-sharing-domain) | Allows for using custom domains when sharing posts, reels and stories. |  |
| [Customise story ring size](#customise-story-ring-size) |  |  |
| [Customise story timestamp](#customise-story-timestamp) | Customise the timestamp that shows when the story was posted |  |
| [Customize navigation bar](#customize-navigation-bar) | Choose which tabs appear in the bottom navigation bar and reorder them |  |
| [Disable Play Store updates](#disable-play-store-updates) | Prevents the patched app from being listed in Play Store updates by setting the version code to the maximum allowed. This also allows downgrading an existing app install to an older app version. |  |
| [Disable Reels scrolling](#disable-reels-scrolling) | Disables the endless scrolling behavior in Instagram Reels, preventing swiping to the next Reel. Note: On a clean install, the 'Tip' animation may appear but will stop on its own after a few seconds. |  |
| [Disable ads](#disable-ads) |  |  |
| [Disable comments](#disable-comments) | Disables comments and hides comment buttons on posts, reels and stories |  |
| [Disable disappearing messages swipe](#disable-disappearing-messages-swipe) | Prevents swiping up in a chat from enabling disappearing messages |  |
| [Disable discover people](#disable-discover-people) | Hides suggested accounts |  |
| [Disable double tap like](#disable-double-tap-like) | Disable double tap like on post, reel, comment and message |  |
| [Disable explore](#disable-explore) |  |  |
| [Disable highlights](#disable-highlights) |  |  |
| [Disable onboarding permission prompts](#disable-onboarding-permission-prompts) | Prevents contacts and location permission onboarding prompts from appearing |  |
| [Disable screenshot detection](#disable-screenshot-detection) | Disables screenshots detection in DM |  |
| [Disable stories](#disable-stories) |  |  |
| [Disable story flipping](#disable-story-flipping) | Disable automatic flipping/moving to next story |  |
| [Disable swipe to create](#disable-swipe-to-create) | Prevents opening the creation screen by swiping right on the home tab. |  |
| [Disable typing status](#disable-typing-status) |  |  |
| [Disable video autoplay](#disable-video-autoplay) |  |  |
| [Download media](#download-media) | Adds ability to download posts, reels, stories and highlights |  |
| [Download voice message](#download-voice-message) | Enables ability to download voice messages |  |
| [External downloader](#external-downloader) | Adds support to share post links directly to external downloader |  |
| [Filter stories](#filter-stories) | Filter stories to hide based on different categories |  |
| [Focus Lock](#focus-lock) | Commitment mode for cutting down on Instagram. Pick what to block (Reels, Explore) and a duration; once locked those protections are forced on and cannot be switched off. Reels shared with you still open. Unlocking early requires a 24 hour cooling-off period, and resetting or importing settings is blocked while locked. |  |
| [Friendship status indicator](#friendship-status-indicator) | Adds a follows you back status label on the profile page and shows a detailed friendship status breakdown on click |  |
| [Hidden chats](#hidden-chats) | Adds an option to the chat menu that moves the chat to a private inbox, opened by tapping the messages button several times. |  |
| [Hide Reels follow button](#hide-reels-follow-button) | Removes the follow button from Reels. |  |
| [Hide feed posts](#hide-feed-posts) | Hides posts from the home feed |  |
| [Hide group creation button on sharesheet](#hide-group-creation-button-on-sharesheet) |  |  |
| [Hide hidden requests](#hide-hidden-requests) | Hides the hidden requests (spam) button in the message requests page |  |
| [Hide like buttons](#hide-like-buttons) | Hides like buttons on posts, reels and stories |  |
| [Hide notes tray](#hide-notes-tray) | Hides notes tray in DM section |  |
| [Hide reshare button](#hide-reshare-button) | Hides the reshare button from both posts and reels. |  |
| [Hide save buttons](#hide-save-buttons) | Hides save buttons on posts and reels |  |
| [Hide share button](#hide-share-button) | Hides the share button on posts, reels and stories |  |
| [Hide stories tray](#hide-stories-tray) | Hides stories tray from main feed. |  |
| [Hide story follow button](#hide-story-follow-button) | Hides the follow button at the top of stories. |  |
| [Hide suggested content](#hide-suggested-content) | Hides suggested stories, reels, threads (Suggested posts will still be shown). |  |
| [Improve image viewing](#improve-image-viewing) | Fetches max resolution images from server. |  |
| [Inbox lock](#inbox-lock) | Asks for your fingerprint, face or screen lock before the inbox is shown. |  |
| [Limit feed to following profiles](#limit-feed-to-following-profiles) | Filters the home feed to display only content from profiles you follow. |  |
| [Loop story](#loop-story) | Replay the current story when it ends |  |
| [Make ephemeral media permanent](#make-ephemeral-media-permanent) | Changes unexpired view once, view twice media to permanent view. |  |
| [Mark as read after reply](#mark-as-read-after-reply) | Adds an option to mark chats as read after replying while viewing DMs anonymously. |  |
| [Mark chat as read manually](#mark-chat-as-read-manually) | Adds option to mark a thread aka message as read manually |  |
| [More options on post](#more-options-on-post) | Adds an overflow menu button to get more options on post/reels, like copy description, copy username etc |  |
| [More options on profile](#more-options-on-profile) | Adds a new button to handle user related data like copy handle, download profile picture etc |  |
| [Open links externally](#open-links-externally) | Changes links to always open in your external browser, instead of the in-app browser. |  |
| [Recommended flags](#recommended-flags) | Developer flags suggested by the community |  |
| [Remove empty bottom space](#remove-empty-bottom-space) | Removes empty space below bottom navigation bar |  |
| [Resume Reels after scrolling](#resume-reels-after-scrolling) | Resumes videos from where you left off when you scroll back to them in Reels |  |
| [Sanitize share links](#sanitize-share-links) |  |  |
| [Save Instants](#save-instants) | Captures view-once Instants as you view them so you can re-view and download them later. |  |
| [Save deleted messages](#save-deleted-messages) | Captures incoming DMs locally as they arrive from the server and marks them when the sender deletes them. |  |
| [Save media comment](#save-media-comment) | Adds a button to save media comments on posts and reels. |  |
| [Theme](#theme) | Adds Material You and AMOLED controls to Piko settings on Android 12 and later. On Android 8–11, it applies a fixed Material You-style theme or an optional AMOLED theme. | • Pure-black AMOLED theme for Android 8–11 |
| [Unlock developer options](#unlock-developer-options) | Unlocks developer option by long pressing home icon |  |
| [View DMs anonymously](#view-dms-anonymously) |  |  |
| [View live anonymously](#view-live-anonymously) |  |  |
| [View stories anonymously](#view-stories-anonymously) |  |  |
| [View story mentions](#view-story-mentions) | Add option to view visible and hidden story mentions. |  |

</details>

<details>
<summary>📦 Twitter&nbsp;&nbsp;•&nbsp;&nbsp;75 patches</summary>
<br>

**🎯 Supported versions:**

| 12.19.1-release.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Add ability to copy media link](#add-ability-to-copy-media-link) |  |  |
| [Block redirecting to X Lite](#block-redirecting-to-x-lite) | Blocks redirecting to the new X Android UI on launch |  |
| [Block update screen](#block-update-screen) | Blocks the 'This app is out of date' update screen from being shown on launch |  |
| [Bring back twitter](#bring-back-twitter) | Bring back old twitter logo and name |  |
| [Change app icon](#change-app-icon) |  |  |
| [Clear tracking params](#clear-tracking-params) | Removes tracking parameters when sharing links |  |
| [Control video auto scroll](#control-video-auto-scroll) | Control video auto scroll in immersive view |  |
| [Custom download folder](#custom-download-folder) | Change the download directory for video downloads |  |
| [Custom emoji font](#custom-emoji-font) | Customise emoji font style |  |
| [Custom font](#custom-font) | Customise font style |  |
| [Custom share menu](#custom-share-menu) | Overrides existing share menu and adds a new one |  |
| [Custom sharing domain](#custom-sharing-domain) | Allows for using domains like fxtwitter when sharing tweets/posts. |  |
| [Customise post font size](#customise-post-font-size) |  |  |
| [Customize Inline action Bar items](#customize-inline-action-bar-items) |  |  |
| [Customize Navigation Bar items](#customize-navigation-bar-items) |  |  |
| [Customize default reply sorting](#customize-default-reply-sorting) |  |  |
| [Customize explore tabs](#customize-explore-tabs) |  |  |
| [Customize notification tabs](#customize-notification-tabs) |  |  |
| [Customize profile tabs](#customize-profile-tabs) |  |  |
| [Customize search suggestions](#customize-search-suggestions) |  |  |
| [Customize search tab items](#customize-search-tab-items) |  |  |
| [Customize side bar items](#customize-side-bar-items) |  |  |
| [Customize timeline top bar](#customize-timeline-top-bar) |  |  |
| [Delete from database](#delete-from-database) | Delete entries from database(cache) |  |
| [Disable Play Store updates](#disable-play-store-updates) | Prevents the patched app from being listed in Play Store updates by setting the version code to the maximum allowed. This also allows downgrading an existing app install to an older app version. |  |
| [Disable auto timeline scroll on launch](#disable-auto-timeline-scroll-on-launch) |  |  |
| [Disable chirp font](#disable-chirp-font) |  |  |
| [Download patch](#download-patch) | Unlocks the ability to download videos and gifs from Twitter/X |  |
| [Dynamic color](#dynamic-color) | Replaces the default Twitter Blue with the user's Material You palette. |  |
| [Enable PiP mode automatically](#enable-pip-mode-automatically) | Enables PiP mode when you close the app |  |
| [Enable Undo Posts](#enable-undo-posts) | Enables ability to undo posts before posting |  |
| [Enable debug menu for posts](#enable-debug-menu-for-posts) |  |  |
| [Enable force HD videos](#enable-force-hd-videos) | Videos will be played in highest quality always |  |
| [Export all activities](#export-all-activities) | Makes all app activities exportable. |  |
| [Force enable translate](#force-enable-translate) | Get translate option for all posts |  |
| [Handle custom twitter links](#handle-custom-twitter-links) | Adds support for opening custom twitter links such as vxtwitter, fxtwitter, and fixupx within the app. These will have to be manually enabled under the "Open by default" section in the app info! | • Custom sharing domains<br>• Include unofficial vxtwitter/fxtwitter instance links |
| [Hide Banner](#hide-banner) | Hide new post banner |  |
| [Hide Community Notes](#hide-community-notes) |  |  |
| [Hide FAB](#hide-fab) | Adds an option to hide Floating action button |  |
| [Hide FAB Menu Buttons](#hide-fab-menu-buttons) |  |  |
| [Hide Live Threads](#hide-live-threads) |  |  |
| [Hide Recommended Users](#hide-recommended-users) | Hide recommended users that pops up when you follow someone |  |
| [Hide badges from navigation bar icons](#hide-badges-from-navigation-bar-icons) | Hides notification nudges & counts from navigation bar icons |  |
| [Hide bookmark icon in timeline](#hide-bookmark-icon-in-timeline) |  |  |
| [Hide community badges](#hide-community-badges) |  |  |
| [Hide followed by context](#hide-followed-by-context) | Hides followed by context under profile |  |
| [Hide hidden replies](#hide-hidden-replies) |  |  |
| [Hide immersive player](#hide-immersive-player) | Removes swipe up for more videos in video player |  |
| [Hide nudge button](#hide-nudge-button) | Hides follow/subscribe/follow back buttons on posts |  |
| [Hide post metrics](#hide-post-metrics) | Hides like, reposts etc counts. |  |
| [Hide promote button](#hide-promote-button) | Hides promote button under self posts |  |
| [Hide recommendation items](#hide-recommendation-items) | Adds options to hide recommendation items such as "Who to follow" and "Today's news" in timeline, search, and replies. |  |
| [Hook feature flag](#hook-feature-flag) |  |  |
| [Import/Export login token](#import-export-login-token) | Adds an feature to export and import the token of accounts. This is useful when logging in on your second device or when re-installing piko. |  |
| [Legacy share links](#legacy-share-links) | Brings back username on post share links. Works post 11.4x.xx |  |
| [Log server response](#log-server-response) | Log json responses received from server |  |
| [More information on profile](#more-information-on-profile) | Adds more details on the profile page |  |
| [Native downloader](#native-downloader) | Requires X 11.0.0-release.0 or higher. |  |
| [Native reader mode](#native-reader-mode) | Requires X 11.0.0-release.0 or higher. |  |
| [Native translator](#native-translator) | Requires X 11.0.0-release.0 or higher. |  |
| [No shortened URL](#no-shortened-url) | Get rid of t.co short urls. |  |
| [Pause search suggestions](#pause-search-suggestions) | Search suggestions will not be saved locally |  |
| [Remove Ads](#remove-ads) | Removed promoted posts, trends and google ads |  |
| [Remove premium upsell](#remove-premium-upsell) | Removes premium upsells |  |
| [Remove search suggestions](#remove-search-suggestions) | Hide/Remove search suggestion in explore section |  |
| [Remove view count](#remove-view-count) | Removes the view count from the bottom of tweets |  |
| [Restore legacy follower lists](#restore-legacy-follower-lists) | Restores the legacy follower and following list screen. |  |
| [Round off numbers](#round-off-numbers) | Enable or disable rounding off numbers |  |
| [Selectable Text](#selectable-text) | Makes bio and username selectable |  |
| [Share Tweet as Image](#share-tweet-as-image) | Share tweets as rendered image. Requires X 11.0.0-release.0 or higher. |  |
| [Show changelogs](#show-changelogs) | Shows changelogs when new a patch is installed. |  |
| [Show poll results](#show-poll-results) | Adds an option to show poll results without voting |  |
| [Show post source label](#show-post-source-label) | Source label will be shown only on public posts |  |
| [Show sensitive media](#show-sensitive-media) |  |  |
| [Support external downloader](#support-external-downloader) |  |  |

</details>

<!-- PATCHES_END -->

## 🛠️ Building

To build Piko Patches, follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

## Contributing

Thank you for considering contributing to Piko Patches.

- If you plan to add a new feature, it is recommended to open an issue to discuss it with the
maintainers of Piko beforehand. This will help you determine whether your feature is acceptable
and whether it is worth your time to implement it. (In the issue, please clearly state that you
intend to work on this feature.)
- Development happens on the `dev` branch. Create your branch from `dev`. Submit a pull request to `dev`.
- Our team will review your pull request and provide feedback. Once your pull request is approved,
it will be merged into the `dev` branch and will be included in the next release of patches.
- You can contribute translations at [Crowdin](https://crowdin.com/project/piko).

## License

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

These patches are fully FOSS redistributable and modifiable under the [GNU General Public License v3](https://www.gnu.org/licenses/gpl.html) or later.
