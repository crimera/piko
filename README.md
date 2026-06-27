<div align="center">

<p align="center">
    <img src="https://socialify.git.ci/crimera/piko/image?forks=1&language=1&name=1&owner=1&pattern=Circuit%20Board&stargazers=1&theme=Auto" alt="piko" width="640" height="320" />
</p>

<h1 align="center">
    Morphe patches focused on Twitter/X & Instagram<br>
    <a href="https://t.me/pikopatches">
        <img src="https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white"/>
    </a>
    <a href="https://crowdin.com/project/piko">
        <img src="https://img.shields.io/badge/Crowdin-1B263B?style=for-the-badge&logo=crowdin&logoColor=white"/>
    </a>
</h1>

</div>

## 🕹️ Usage

> Starting with patches v3.0.0, Piko patches use [Morphe](https://morphe.software).

**Morphe Manager**

<details>
<summary>Show steps</summary>

Use the deep link to add Piko as a patch source in Morphe Manager:

[➕ Add Piko to Morphe](https://morphe.software/add-source?github=crimera/piko)

Then patch Twitter/X or Instagram:
1. Tap X or Instagram app icon in Morphe
2. Download original APKM file from ApkMirror. Do _not_ unspilt or modify the file, Morphe patches APKM directly
3. Wait for patching to complete, install



</details>

**Morphe CLI**

```sh
java -jar cli.jar patch --patches piko.mpp input.apkm
```

**X-Shim**

To patch with Twitter/X version 11.88 and above, you must include patches from another repo/project called [Piko-Shim](https://gitlab.com/inotia00/piko-shim/) developed by [@inotia00](https://github.com/inotia00)

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

## ⚙️ Patch Details

<!-- PATCHES_START -->
> **[v3.7.0](https://github.com/crimera/piko/releases/tag/v3.7.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;123 patches total
<details>
<summary>📦 X&nbsp;&nbsp;•&nbsp;&nbsp;72 patches</summary>
<br>

**🎯 Supported versions:**

| 11.81.0-release.0 | 11.99.0-release-ripped.1 | 12.0.0-release.0 | 12.2.0-release.0 |
| :---: | :---: | :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Add ability to copy media link](#add-ability-to-copy-media-link) |  |  |
| [Block redirecting to X Lite](#block-redirecting-to-x-lite) | Blocks redirecting to the new X Android UI on launch |  |
| [Bring back twitter](#bring-back-twitter) | Bring back old twitter logo and name |  |
| [Browse tweet object](#browse-tweet-object) | Adds an option to browse the tweet object in the share menu. |  |
| [Change app icon](#change-app-icon) |  |  |
| [Change version code](#change-version-code) | Changes the version code of the app. This will turn off app store updates and allows downgrading an existing app install to an older app version. | • Version code |
| [Clear tracking params](#clear-tracking-params) | Removes tracking parameters when sharing links |  |
| [Control video auto scroll](#control-video-auto-scroll) | Control video auto scroll in immersive view |  |
| [Custom download folder](#custom-download-folder) | Change the download directory for video downloads |  |
| [Custom emoji font](#custom-emoji-font) | Customise emoji font style |  |
| [Custom font](#custom-font) | Customise font style |  |
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
| [Disable auto timeline scroll on launch](#disable-auto-timeline-scroll-on-launch) |  |  |
| [Disable chirp font](#disable-chirp-font) |  |  |
| [Disunify xchat system](#disunify-xchat-system) | Bring back legacy features like messages and share sheet. |  |
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
| [Native downloader](#native-downloader) | Requires X 11.0.0-release.0 or higher. |  |
| [Native reader mode](#native-reader-mode) | Requires X 11.0.0-release.0 or higher. |  |
| [Native translator](#native-translator) | Requires X 11.0.0-release.0 or higher. |  |
| [No shortened URL](#no-shortened-url) | Get rid of t.co short urls. |  |
| [Pause search suggestions](#pause-search-suggestions) | Search suggestions will not be saved locally |  |
| [Remove Ads](#remove-ads) | Removed promoted posts, trends and google ads |  |
| [Remove premium upsell](#remove-premium-upsell) | Removes premium upsell in home timeline |  |
| [Remove search suggestions](#remove-search-suggestions) | Hide/Remove search suggestion in explore section |  |
| [Remove view count](#remove-view-count) | Removes the view count from the bottom of tweets |  |
| [Round off numbers](#round-off-numbers) | Enable or disable rounding off numbers |  |
| [Selectable Text](#selectable-text) | Makes bio and username selectable |  |
| [Share Tweet as Image](#share-tweet-as-image) | Share tweets as rendered image. Requires X 11.0.0-release.0 or higher. |  |
| [Show changelogs](#show-changelogs) | Shows changelogs when new a patch is installed. |  |
| [Show poll results](#show-poll-results) | Adds an option to show poll results without voting |  |
| [Show post source label](#show-post-source-label) | Source label will be shown only on public posts |  |
| [Show sensitive media](#show-sensitive-media) |  |  |

</details>

<details>
<summary>📦 Instagram&nbsp;&nbsp;•&nbsp;&nbsp;51 patches</summary>
<br>

**🎯 Supported versions:**

| 435.0.0.37.76 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Add settings](#add-settings) | Adds settings to control preferences are patching |  |
| [Allow user network certificate](#allow-user-network-certificate) | Allows user network certificate for whitehat testing |  |
| [Amoled theme](#amoled-theme) | Replaces Instagram's dark-mode background greys with pure black for AMOLED displays. |  |
| [Change like animation](#change-like-animation) | Change the animation to one from existing Rings like animations |  |
| [Change version code](#change-version-code) | Changes the version code of the app. This will turn off app store updates and allows downgrading an existing app install to an older app version. | • Version code |
| [Clone](#clone) | Changes the package name and the app name. This allows you to install the patched app alongside the original Instagram app.<br>Caution: Do not select the official Morphe's "Change package name" universal patch. | • Package name<br>• App name |
| [Copy comment](#copy-comment) | Adds a button to copy comments on posts and reels. |  |
| [Customise story ring size](#customise-story-ring-size) |  |  |
| [Customise story timestamp](#customise-story-timestamp) | Customise the timestamp that shows when the story was posted |  |
| [Disable Reels scrolling](#disable-reels-scrolling) | Disables the endless scrolling behavior in Instagram Reels, preventing swiping to the next Reel. Note: On a clean install, the 'Tip' animation may appear but will stop on its own after a few seconds. |  |
| [Disable ads](#disable-ads) |  |  |
| [Disable analytics](#disable-analytics) | Block analytics that are sent to Instagram/Facebook servers. |  |
| [Disable comments](#disable-comments) |  |  |
| [Disable discover people](#disable-discover-people) | Disables discover people section on user profile |  |
| [Disable double tap like](#disable-double-tap-like) | Disable double tap like on post, reel, comment and message |  |
| [Disable explore](#disable-explore) |  |  |
| [Disable highlights](#disable-highlights) |  |  |
| [Disable screenshot detection](#disable-screenshot-detection) | Disables screenshots detection in DM |  |
| [Disable stories](#disable-stories) |  |  |
| [Disable story flipping](#disable-story-flipping) | Disable automatic flipping/moving to next story |  |
| [Disable typing status](#disable-typing-status) |  |  |
| [Disable video autoplay](#disable-video-autoplay) |  |  |
| [Download media](#download-media) | Adds ability to download posts, reels, stories and highlights |  |
| [Download voice message](#download-voice-message) | Enables ability to download voice messages |  |
| [External downloader](#external-downloader) | Adds support to share post links directly to external downloader |  |
| [Friendship status indicator](#friendship-status-indicator) | Adds a follows you back status label on the profile page andshows a detailed friendship status breakdown on click |  |
| [Hide group creation button on sharesheet](#hide-group-creation-button-on-sharesheet) |  |  |
| [Hide navigation buttons](#hide-navigation-buttons) | Hides navigation bar buttons, such as the Reels and Create button. |  |
| [Hide notes tray](#hide-notes-tray) | Hides notes tray in DM section |  |
| [Hide reshare button](#hide-reshare-button) | Hides the reshare button from both posts and reels. |  |
| [Hide stories tray](#hide-stories-tray) | Hides stories tray from main feed. |  |
| [Hide suggested content](#hide-suggested-content) | Hides suggested stories, reels, threads (Suggested posts will still be shown). |  |
| [Improve image viewing](#improve-image-viewing) | Fetches max resolution images from server. |  |
| [Limit feed to following profiles](#limit-feed-to-following-profiles) | Filters the home feed to display only content from profiles you follow. |  |
| [Make ephemeral media permanent](#make-ephemeral-media-permanent) | Changes unexpired view once, view twice media to permanent view. |  |
| [More options on post](#more-options-on-post) | Adds an overflow menu button to get more options on post/reels, like copy description, copy username etc |  |
| [More options on profile](#more-options-on-profile) | Adds a new button to handle user related data like copy handle, download profile picture etc |  |
| [Open links externally](#open-links-externally) | Changes links to always open in your external browser, instead of the in-app browser. |  |
| [Remove build expired popup](#remove-build-expired-popup) | Removes the popup that appears after a while, when the app version ages. |  |
| [Remove empty bottom space](#remove-empty-bottom-space) | Removes empty space below bottom navigation bar |  |
| [Sanitize share links](#sanitize-share-links) |  |  |
| [Save media comment](#save-media-comment) | Adds a button to save media comments on posts and reels. |  |
| [Stories audio autoplay](#stories-audio-autoplay) |  |  |
| [Unlock Plus benefits](#unlock-plus-benefits) | Unlocks 'Plus' subscription benefits that are checked locally. USE IT AT YOUR OWN RISK |  |
| [Unlock developer options](#unlock-developer-options) | Unlocks developer option by long pressing home icon |  |
| [Unlock employee options](#unlock-employee-options) | Unlocks all options using by employee for debugging |  |
| [Validate links](#validate-links) | Fixes app crashing issue while opening links from a different app |  |
| [View DMs anonymously](#view-dms-anonymously) |  |  |
| [View live anonymously](#view-live-anonymously) |  |  |
| [View stories anonymously](#view-stories-anonymously) |  |  |
| [View story mentions](#view-story-mentions) | Add option to view visible and hidden story mentions. |  |

</details>

<!-- PATCHES_END -->

## 🛠️ Building

To build Piko Patches, follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

## ✨ Stargazers over time

<p align="center">
    <img src="https://starchart.cc/crimera/piko.svg?variant=light" alt="piko" width="640" height="320" />
</p>

## License

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

These patches are fully FOSS redistributable and modifiable under the [GNU General Public License v3](https://www.gnu.org/licenses/gpl.html) or later.
