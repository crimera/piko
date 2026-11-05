/*
 * Reply/Like ratio patch for X/Twitter (community addition to piko).
 *
 * Shows the replies-to-likes ratio in every post's inline action bar.
 * See the included NOTICE file for GPLv3 terms.
 */

package app.crimera.patches.twitter.misc.replyLikeRatio

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.string
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import com.android.tools.smali.dexlib2.Opcode

private const val INLINE_ACTION_BAR_DESCRIPTOR = "Lcom/twitter/ui/tweet/inlineactions/InlineActionBar;"
private const val EXTENSION_CLASS = "$PATCHES_DESCRIPTOR/ReplyLikeRatio;"

/**
 * Matches `InlineActionBar.f(com.twitter.model.core.e, boolean)` — the method the tweet-row
 * binder calls to (re)bind a tweet to the action bar. It stores the tweet into the bar's
 * `k` field (`iput-object` to `this`) just before loading the default heart Lottie asset,
 * so that pair of instructions uniquely identifies the bind method without relying on its
 * obfuscated name.
 */
internal object InlineActionBarBindFingerprint : Fingerprint(
    definingClass = INLINE_ACTION_BAR_DESCRIPTOR,
    returnType = "V",
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
            ),
            string("file:///android_asset/default_heart_v3.json"),
        ),
)

private val replyLikeRatioResourcePatch =
    resourcePatch(description = "Copies the reply/like ratio icon drawable into the app.") {
        compatibleWith(COMPATIBILITY_X)
        execute {
            copyResources(
                "twitter/replyLikeRatio",
                ResourceGroup("drawable", "piko_reply_like_ratio.xml"),
            )
        }
    }

@Suppress("unused")
val replyLikeRatioPatch =
    bytecodePatch(
        name = "Reply/Like ratio",
        description = "Shows the replies-to-likes ratio in each post's action bar, next to the view count.",
    ) {
        compatibleWith(COMPATIBILITY_X)
        // settingsPatch merges the twitter extension, adds our string resources, and builds the
        // Piko settings screen we register the toggle into (it pulls in sharedExtensionPatch).
        dependsOn(settingsPatch, replyLikeRatioResourcePatch)

        execute {
            // Register the in-app toggle: Piko Settings -> Timeline -> "Reply/Like ratio".
            enableSettings("replyLikeRatio")

            // Pass (this InlineActionBar, the tweet object `com.twitter.model.core.e`) to the
            // extension. The bind method is large, so its parameter registers are high; a /range
            // invoke over the two consecutive param registers p0..p1 is required.
            InlineActionBarBindFingerprint.method.addInstruction(
                0,
                "invoke-static/range { p0 .. p1 }, $EXTENSION_CLASS->onBind(Landroid/view/ViewGroup;Ljava/lang/Object;)V",
            )
        }
    }
