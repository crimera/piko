package app.crimera.patches.twitter.misc.shareMenu.nativeDownloader

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.changeFirstString
import app.crimera.utils.enableSettings
import app.crimera.utils.getFieldName
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val INLINE_ACTION_BAR_DESCRIPTOR = "Lcom/twitter/ui/tweet/inlineactions/InlineActionBar;"
private const val INLINE_ACTION_VIEW_DESCRIPTOR = "Lcom/twitter/ui/tweet/inlineactions/InlineActionView;"
private const val INLINE_ACTIONS_PACKAGE_PREFIX = "Lcom/twitter/ui/tweet/inlineactions/"
private const val COLOR_STATE_LIST_DESCRIPTOR = "Landroid/content/res/ColorStateList;"
private const val BOOLEAN_DESCRIPTOR = "Z"
private const val RENDER_HINTS_BOOLEAN_COUNT = 6
private const val FOCAL_TWEET_FIELD_WRITE_INDEX = 4

private fun placeholderFingerprint(value: String) = object : Fingerprint(strings = listOf(value)) {}

private fun isRenderHintsConstructor(method: Method) =
    method.name == "<init>" &&
        method.parameterTypes.size == RENDER_HINTS_BOOLEAN_COUNT &&
        method.parameterTypes.all { it == BOOLEAN_DESCRIPTOR }

private object OnFinishInflateFingerprint : Fingerprint(
    definingClass = INLINE_ACTION_BAR_DESCRIPTOR,
    name = "onFinishInflate",
    returnType = "V",
)

private object SetTweetFingerprint : Fingerprint(
    definingClass = INLINE_ACTION_BAR_DESCRIPTOR,
    returnType = "V",
    strings = listOf("file:///android_asset/default_heart_v3.json"),
)

private object InlineActionViewConstructorFingerprint : Fingerprint(
    definingClass = INLINE_ACTION_VIEW_DESCRIPTOR,
    strings = listOf("hal_android_lottie_render_mode"),
)

private val inlineDownloadTweetFieldFingerprint = placeholderFingerprint("mTweet")
private val inlineDownloadIconColorFieldFingerprint = placeholderFingerprint("mIconDrawableColorStateList")
private val inlineDownloadRenderHintsFieldFingerprint = placeholderFingerprint("mRenderHints")
private val inlineDownloadRenderHintsFocalFieldFingerprint = placeholderFingerprint("isFocalTweet")

@Suppress("unused")
val inlineDownloadButtonPatch = bytecodePatch(
    description = "Adds an inline 'Download' button to the tweet inline action bar and registers the extension hook.",
) {
    compatibleWith("com.twitter.android")
    dependsOn(settingsPatch)

    execute {
        val onFinishInflateMethod = OnFinishInflateFingerprint.method
        val returnIndex = onFinishInflateMethod.instructions
            .indexOfLast { it.opcode == Opcode.RETURN_VOID }
            .takeIf { it >= 0 }
            ?: throw PatchException("onFinishInflate return not found")

        onFinishInflateMethod.addInstruction(
            returnIndex,
            "invoke-static {p0}, $PATCHES_DESCRIPTOR/InlineDownloadButton;->onFinishInflate(Landroid/view/ViewGroup;)V"
        )

        val setTweetMethod = SetTweetFingerprint.method
        val tweetFieldIndex = setTweetMethod.indexOfFirstInstructionOrThrow(Opcode.IPUT_OBJECT)
        inlineDownloadTweetFieldFingerprint.changeFirstString(SetTweetFingerprint.getFieldName(tweetFieldIndex))

        val constructorMethod = InlineActionViewConstructorFingerprint.method
        val iconColorFieldIndex = constructorMethod.indexOfFirstInstructionOrThrow {
            opcode == Opcode.IPUT_OBJECT &&
                ((this as? ReferenceInstruction)?.reference as? FieldReference)?.type == COLOR_STATE_LIST_DESCRIPTOR
        }
        inlineDownloadIconColorFieldFingerprint.changeFirstString(
            InlineActionViewConstructorFingerprint.getFieldName(iconColorFieldIndex)
        )

        val inlineActionBarClass = classDefByOrNull(INLINE_ACTION_BAR_DESCRIPTOR)
            ?: throw PatchException("InlineActionBar class not found")

        val renderHintsField = inlineActionBarClass.fields.firstOrNull { field ->
            if (!field.type.startsWith(INLINE_ACTIONS_PACKAGE_PREFIX)) return@firstOrNull false

            val candidateClass = classDefByOrNull(field.type) ?: return@firstOrNull false
            candidateClass.fields.count { it.type == BOOLEAN_DESCRIPTOR } >= RENDER_HINTS_BOOLEAN_COUNT &&
                candidateClass.methods.any(::isRenderHintsConstructor)
        } ?: throw PatchException("RenderHints field not found on InlineActionBar")

        inlineDownloadRenderHintsFieldFingerprint.changeFirstString(renderHintsField.name)

        val renderHintsClass = classDefByOrNull(renderHintsField.type)
            ?: throw PatchException("RenderHints class not found: ${renderHintsField.type}")
        val renderHintsConstructor = renderHintsClass.methods.firstOrNull(::isRenderHintsConstructor)
            ?: throw PatchException("RenderHints constructor not found")

        val focalFieldReference = renderHintsConstructor.implementation?.instructions
            ?.filter { it.opcode == Opcode.IPUT_BOOLEAN }
            ?.getOrNull(FOCAL_TWEET_FIELD_WRITE_INDEX)
            ?.let { (it as? ReferenceInstruction)?.reference as? FieldReference }
            ?: throw PatchException("RenderHints focal field write not found")

        inlineDownloadRenderHintsFocalFieldFingerprint.changeFirstString(focalFieldReference.name)

        SettingsStatusLoadFingerprint.enableSettings("inlineDownloadButton")
    }
}
