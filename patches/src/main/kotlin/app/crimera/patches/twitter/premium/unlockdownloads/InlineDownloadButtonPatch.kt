package app.crimera.patches.twitter.premium.unlockdownloads

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.changeFirstString
import app.crimera.utils.enableSettings
import app.crimera.utils.getFieldName
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

/**
 * Fingerprint that directly matches onFinishInflate() in InlineActionBar.
 * Uses custom filter to match by method name since class is NOT obfuscated.
 * Using Fingerprint.method guarantees a mutable/patchable method reference.
 */
private object OnFinishInflateFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/ui/tweet/inlineactions/InlineActionBar;",
    returnType = "V",
    parameters = emptyList(),
    custom = { methodDef, _ -> methodDef.name == "onFinishInflate" },
)

/**
 * Fingerprint for the internal setTweet implementation method.
 * Contains the stable string "file:///android_asset/default_heart_v3.json".
 * The first IPUT_OBJECT instruction stores the tweet into the obfuscated field.
 */
private object SetTweetFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/ui/tweet/inlineactions/InlineActionBar;",
    returnType = "V",
    strings = listOf("file:///android_asset/default_heart_v3.json"),
)

/**
 * Fingerprint for the extension's getTweetFieldName() method.
 * Contains placeholder "mTweet" which gets replaced at patch time.
 */
private object InlineDownloadTweetFieldFingerprint : Fingerprint(
    strings = listOf("mTweet"),
)

@Suppress("unused")
val inlineDownloadButtonPatch = bytecodePatch(
    name = "Inline download button",
) {
    compatibleWith("com.twitter.android")
    dependsOn(settingsPatch)

    execute {
        // --- Step 1: Hook onFinishInflate via Fingerprint.method (guaranteed mutable) ---
        val onFinishInflateMethod = OnFinishInflateFingerprint.method

        val returnIndex = onFinishInflateMethod.instructions
            .indexOfLast { it.opcode == Opcode.RETURN_VOID }

        onFinishInflateMethod.addInstruction(
            returnIndex,
            "invoke-static {p0}, $PATCHES_DESCRIPTOR/InlineDownloadButton;->onFinishInflate(Landroid/view/ViewGroup;)V"
        )

        // --- Step 2: Resolve the obfuscated mTweet field name via fingerprint ---
        val setTweetMethod = SetTweetFingerprint.method
        val iputIndex = setTweetMethod.indexOfFirstInstruction(Opcode.IPUT_OBJECT)
        val tweetFieldName = SetTweetFingerprint.getFieldName(iputIndex)

        // Replace "mTweet" placeholder in extension code with the real field name
        InlineDownloadTweetFieldFingerprint.changeFirstString(tweetFieldName)

        SettingsStatusLoadFingerprint.enableSettings("inlineDownloadButton")
    }
}
