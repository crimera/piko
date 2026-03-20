<div align="center">

<p align="center">
    <img src="https://socialify.git.ci/crimera/piko/image?forks=1&language=1&name=1&owner=1&pattern=Circuit%20Board&stargazers=1&theme=Auto" alt="piko" width="640" height="320" />
</p>

<h1 align="center">
    Morphe patches focused on Twitter/X <br>
    <a href="https://t.me/pikopatches">
        <img src="https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white"/>
    </a>
    <a href="https://crowdin.com/project/piko">
        <img src="https://img.shields.io/badge/Crowdin-1B263B?style=for-the-badge&logo=crowdin&logoColor=white"/>
    </a>
</h1>

<h2>🕹️ Usage</h2>

**Morphe Manager**

Use the deep link to add Piko as a patch source in Morphe Manager:

[➕ Add Piko to Morphe](https://morphe.software/add-source?github=crimera/piko)

Then patch Twitter/X:
1. Download an original unpatched X APKM from [APKMirror.com](https://www.apkmirror.com/apk/x-corp/twitter/)
2. Do _not_ unsplit or otherwise change the APKM.
3. Select "Other apps" on the Morphe Manager home screen
4. Provide the original APKM file
5. Wait for patching to complete, install

**Morphe CLI**

```sh
java -jar cli.jar patch --patches piko.mpp input.apkm
```

<summary><h2>⚙️ Patch Details</h2></summary>
<details>
<table>
<thead>
<tr>
<th>Patch Name</th>
<th>Patch Description</th>
</tr>
</thead>

<tbody>

<tr>
<td><code>Add ability to copy media link</code></td>
<td></td>
</tr>

<tr>
<td><code>Bring back twitter</code></td>
<td>Bring back old twitter logo and name</td>
</tr>

<tr>
<td><code>Browse tweet object</code></td>
<td>Adds an option to browse the tweet object in the share menu.</td>
</tr>

<tr>
<td><code>Change app icon</code></td>
<td>Ability to change app icons from a wide collection</td>
</tr>

<tr>
<td><code>Change version code</code></td>
<td>Changes the version code of the app. This will turn off app store updates and allows downgrading an existing app install to an older app version.</td>
</tr>

<tr>
<td><code>Clear tracking params</code></td>
<td>Removes tracking parameters when sharing links</td>
</tr>

<tr>
<td><code>Control video auto scroll</code></td>
<td>Control video auto scroll in immersive view</td>
</tr>

<tr>
<td><code>Custom download folder</code></td>
<td>Change the download directory for video downloads</td>
</tr>

<tr>
<td><code>Custom emoji font</code></td>
<td>Customise emoji font style</td>
</tr>

<tr>
<td><code>Custom font</code></td>
<td>Customise font style</td>
</tr>

<tr>
<td><code>Custom sharing domain</code></td>
<td>Allows for using domains like fxtwitter when sharing tweets/posts.</td>
</tr>

<tr>
<td><code>Custom translator</code></td>
<td>A custom translator with multiple providers.</td>
</tr>

<tr>
<td><code>Customise post font size</code></td>
<td></td>
</tr>

<tr>
<td><code>Customize Inline action Bar items</code></td>
<td></td>
</tr>

<tr>
<td><code>Customize Navigation Bar items</code></td>
<td></td>
</tr>

<tr>
<td><code>Customize default reply sorting</code></td>
<td></td>
</tr>

<tr>
<td><code>Customize explore tabs</code></td>
<td></td>
</tr>

<tr>
<td><code>Customize notification tabs</code></td>
<td></td>
</tr>

<tr>
<td><code>Customize profile tabs</code></td>
<td></td>
</tr>

<tr>
<td><code>Customize search suggestions</code></td>
<td></td>
</tr>

<tr>
<td><code>Customize search tab items</code></td>
<td></td>
</tr>

<tr>
<td><code>Customize side bar items</code></td>
<td></td>
</tr>

<tr>
<td><code>Customize timeline top bar</code></td>
<td></td>
</tr>

<tr>
<td><code>Delete from database</code></td>
<td>Delete entries from database(cache)</td>
</tr>

<tr>
<td><code>Disable auto timeline scroll on launch</code></td>
<td></td>
</tr>

<tr>
<td><code>Disable chirp font</code></td>
<td></td>
</tr>

<tr>
<td><code>Disunify xchat system</code></td>
<td>Bring back legacy features like messages and share sheet.</td>
</tr>

<tr>
<td><code>Download patch</code></td>
<td>Unlocks the ability to download videos and gifs from Twitter/X</td>
</tr>

<tr>
<td><code>Dynamic color</code></td>
<td>Replaces the default Twitter Blue with the user's Material You palette.</td>
</tr>

<tr>
<td><code>Enable PiP mode automatically</code></td>
<td>Enables PiP mode when you close the app</td>
</tr>

<tr>
<td><code>Enable Undo Posts</code></td>
<td>Enables ability to undo posts before posting</td>
</tr>

<tr>
<td><code>Enable debug menu for posts</code></td>
<td>Debug tool used to explore post details.</td>
</tr>

<tr>
<td><code>Enable force HD videos</code></td>
<td>Videos will be played in highest quality always</td>
</tr>

<tr>
<td><code>Force enable translate</code></td>
<td>Get translate option for all posts</td>
</tr>

<tr>
<td><code>Handle custom twitter links</code></td>
<td>Adds support for opening custom twitter links such as vxtwitter, fxtwitter, and fixupx within the app. These will have to be manually enabled under the "Open by default" section in the app info!</td>
</tr>

<tr>
<td><code>Hide Banner</code></td>
<td>Hide new post banner</td>
</tr>

<tr>
<td><code>Hide Community Notes</code></td>
<td></td>
</tr>

<tr>
<td><code>Hide FAB</code></td>
<td>Adds an option to hide Floating action button</td>
</tr>

<tr>
<td><code>Hide FAB Menu Buttons</code></td>
<td>Hides options from floating action button.</td>
</tr>

<tr>
<td><code>Hide Live Threads</code></td>
<td>Hides live, spaces section from home timeline.</td>
</tr>

<tr>
<td><code>Hide Recommended Users</code></td>
<td>Hide recommended users that pops up when you follow someone</td>
</tr>

<tr>
<td><code>Hide badges from navigation bar icons</code></td>
<td>Hides notification nudges & counts from navigation bar icons</td>
</tr>

<tr>
<td><code>Hide bookmark icon in timeline</code></td>
<td>Hide the bookmark icon from tweet inline action.</td>
</tr>

<tr>
<td><code>Hide community badges</code></td>
<td></td>
</tr>

<tr>
<td><code>Hide followed by context</code></td>
<td>Hides followed by context under profile</td>
</tr>

<tr>
<td><code>Hide hidden replies</code></td>
<td></td>
</tr>

<tr>
<td><code>Hide immersive player</code></td>
<td>Removes swipe up for more videos in video player</td>
</tr>

<tr>
<td><code>Hide nudge button</code></td>
<td>Hides follow/subscribe/follow back buttons on posts</td>
</tr>

<tr>
<td><code>Hide post metrics</code></td>
<td>Hides like, reposts etc counts.</td>
</tr>

<tr>
<td><code>Hide promote button</code></td>
<td>Hides promote button under self posts</td>
</tr>

<tr>
<td><code>Hide timeline posts by category</code></td>
<td>Hides different post category like who to follow, news today etc from timeline.</td>
</tr>

<tr>
<td><code>Hook feature flag</code></td>
<td>Toggle feature flags to enable development features.</td>
</tr>

<tr>
<td><code>Legacy share links</code></td>
<td>Brings back username on post share links. Works post 11.4x.xx</td>
</tr>

<tr>
<td><code>Log server response</code></td>
<td>Log json responses received from server</td>
</tr>

<tr>
<td><code>Native downloader</code></td>
<td>Adds a native media downloader</td>
</tr>

<tr>
<td><code>Native reader mode</code></td>
<td>Option to view threads in reader mode.</td>
</tr>

<tr>
<td><code>No shortened URL</code></td>
<td>Get rid of t.co short urls.</td>
</tr>

<tr>
<td><code>Pause search suggestions</code></td>
<td>Search suggestions will not be saved locally</td>
</tr>

<tr>
<td><code>Remove Ads</code></td>
<td>Removed promoted posts, trends and google ads</td>
</tr>

<tr>
<td><code>Remove premium upsell</code></td>
<td>Removes premium upsell in home timeline</td>
</tr>

<tr>
<td><code>Remove search suggestions</code></td>
<td>Hide/Remove search suggestion in explore section</td>
</tr>

<tr>
<td><code>Remove view count</code></td>
<td>Removes the view count from the bottom of tweets</td>
</tr>

<tr>
<td><code>Round off numbers</code></td>
<td>Enable or disable rounding off numbers</td>
</tr>

<tr>
<td><code>Selectable Text</code></td>
<td>Makes bio and username selectable</td>
</tr>

<tr>
<td><code>Share Tweet as Image</code></td>
<td>Share tweets as rendered image. Requires X 11.0.0-release.0 or higher.</td>
</tr>

<tr>
<td><code>Show changelogs</code></td>
<td>Shows changelogs when new a patch is installed.</td>
</tr>

<tr>
<td><code>Show poll results</code></td>
<td>Adds an option to show poll results without voting</td>
</tr>

<tr>
<td><code>Show post source label</code></td>
<td>Source label will be shown only on public posts</td>
</tr>

<tr>
<td><code>Show sensitive media</code></td>
<td>Disables the sensitive media banner</td>
</tr>

</tbody>
</table>
</details>


<summary><h2>🛠️ Building</h2></summary>
To build Piko Patches, you can follow the <a href="https://github.com/MorpheApp/morphe-documentation">Morphe documentation</a>.


<summary><h2>✨Stargazers over time</h2></summary>
<p align="center">
    <img src="https://starchart.cc/crimera/piko.svg?variant=light" alt="piko" width="640" height="320" />
</p>

</div>

## License
[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

These patches are fully FOSS. You can use, study, share and modify it at your will. They can be redistributed and/or modified under the terms of the [GNU General Public License](https://www.gnu.org/licenses/gpl.html) version 3 or later published by the Free Software Foundation.

---
