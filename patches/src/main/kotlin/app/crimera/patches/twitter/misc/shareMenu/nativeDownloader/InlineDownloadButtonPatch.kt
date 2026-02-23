package app.crimera.patches.twitter.misc.shareMenu.nativeDownloader

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.changeFirstString
import app.crimera.utils.enableSettings
import app.crimera.utils.getFieldName
import app.crimera.utils.getReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

/**
 * Fingerprint that directly matches onFinishInflate() in InlineActionBar.
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
 */
private object SetTweetFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/ui/tweet/inlineactions/InlineActionBar;",
    returnType = "V",
    strings = listOf("file:///android_asset/default_heart_v3.json"),
)

/**
 * Fingerprint for InlineActionView's setIconDrawable method (decompiled: b(Drawable, ImageView)).
 * This method calls drawable.mutate(), then reads mIconDrawableColorStateList,
 * then calls drawable.setTintList(). The constructor has a stable string we can target.
 */
private object InlineActionViewConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/ui/tweet/inlineactions/InlineActionView;",
    strings = listOf("hal_android_lottie_render_mode"),
)

/** Placeholder in extension: getTweetFieldName() returns "mTweet" */
private object InlineDownloadTweetFieldFingerprint : Fingerprint(
    strings = listOf("mTweet"),
)

/** Placeholder in extension: getIconColorFieldName() returns "mIconDrawableColorStateList" */
private object InlineDownloadIconColorFieldFingerprint : Fingerprint(
    strings = listOf("mIconDrawableColorStateList"),
)

@Suppress("unused")
val inlineDownloadButtonPatch = bytecodePatch(
    name = "Inline download button",
) {
    compatibleWith("com.twitter.android")
    dependsOn(settingsPatch)

    execute {
        // --- Step 1: Hook onFinishInflate ---
        val onFinishInflateMethod = OnFinishInflateFingerprint.method

        val returnIndex = onFinishInflateMethod.instructions
            .indexOfLast { it.opcode == Opcode.RETURN_VOID }

        onFinishInflateMethod.addInstruction(
            returnIndex,
            "invoke-static {p0}, $PATCHES_DESCRIPTOR/InlineDownloadButton;->onFinishInflate(Landroid/view/ViewGroup;)V"
        )

        // --- Step 2: Resolve obfuscated mTweet field name ---
        val setTweetMethod = SetTweetFingerprint.method
        val iputIndex = setTweetMethod.indexOfFirstInstruction(Opcode.IPUT_OBJECT)
        val tweetFieldName = SetTweetFingerprint.getFieldName(iputIndex)
        InlineDownloadTweetFieldFingerprint.changeFirstString(tweetFieldName)

        // --- Step 3: Resolve obfuscated mIconDrawableColorStateList field name ---
        // The constructor assigns this field via iput-object ... ColorStateList
        // Find the iput-object for the ColorStateList field in InlineActionView
        val constructorMethod = InlineActionViewConstructorFingerprint.method
        // Use getReference to check field type
        val colorFieldIndex = constructorMethod.instructions.indexOfFirst { inst ->
            if (inst.opcode != Opcode.IPUT_OBJECT) return@indexOfFirst false
            try {
                val fieldRef = InlineActionViewConstructorFingerprint.getReference(
                    constructorMethod.instructions.indexOf(inst)
                ) as com.android.tools.smali.dexlib2.iface.reference.FieldReference
                fieldRef.type == "Landroid/content/res/ColorStateList;"
            } catch (e: Exception) {
                false
            }
        }
        val iconColorFieldName = InlineActionViewConstructorFingerprint.getFieldName(colorFieldIndex)
        InlineDownloadIconColorFieldFingerprint.changeFirstString(iconColorFieldName)

        SettingsStatusLoadFingerprint.enableSettings("inlineDownloadButton")
    }
}
