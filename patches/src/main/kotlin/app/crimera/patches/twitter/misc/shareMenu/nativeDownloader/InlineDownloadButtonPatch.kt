package app.crimera.patches.twitter.misc.shareMenu.nativeDownloader

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.crimera.patches.twitter.utils.versionCheckPatch
import app.crimera.utils.changeFirstString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val INLINE_ACTION_BAR_DESCRIPTOR = "Lcom/twitter/ui/tweet/inlineactions/InlineActionBar;"
private const val INLINE_ACTION_VIEW_DESCRIPTOR = "Lcom/twitter/ui/tweet/inlineactions/InlineActionView;"
private const val INLINE_ACTIONS_PACKAGE_PREFIX = "Lcom/twitter/ui/tweet/inlineactions/"
private const val BOOLEAN_DESCRIPTOR = "Z"
private const val RENDER_HINTS_BOOLEAN_COUNT = 6
private const val FOCAL_TWEET_FIELD_WRITE_INDEX = 4

private const val EXTENSION_CLASS = "$PATCHES_DESCRIPTOR/InlineDownloadButton;"

private fun placeholderFingerprint(value: String) = object : Fingerprint(
    definingClass = EXTENSION_CLASS,
    strings = listOf(value)
) {}

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
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this"
        ),
        string("file:///android_asset/default_heart_v3.json")
    )
)

private object InlineActionViewConstructorFingerprint : Fingerprint(
    definingClass = INLINE_ACTION_VIEW_DESCRIPTOR,
    name = "<init>",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Landroid/content/res/ColorStateList;"
        ),
        string("hal_android_lottie_render_mode")
    )
)

private val inlineDownloadTweetFieldFingerprint = placeholderFingerprint("mTweet")
private val inlineDownloadIconColorFieldFingerprint = placeholderFingerprint("mIconDrawableColorStateList")
private val inlineDownloadRenderHintsFieldFingerprint = placeholderFingerprint("mRenderHints")
private val inlineDownloadRenderHintsFocalFieldFingerprint = placeholderFingerprint("isFocalTweet")

@Suppress("unused")
val inlineDownloadButtonPatch =
    bytecodePatch(
        description = "Adds an inline 'Download' button to the tweet inline action bar and registers the extension hook.",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, versionCheckPatch)

        execute {
            OnFinishInflateFingerprint.method.apply {
                val index = indexOfFirstInstructionReversedOrThrow(Opcode.RETURN_VOID)

                addInstruction(
                    index,
                    "invoke-static { p0 }, $EXTENSION_CLASS->onFinishInflate(Landroid/view/ViewGroup;)V",
                )
            }

            val tweetFieldName =
                SetTweetFingerprint.instructionMatches.first().instruction.getReference<FieldReference>()!!.name
            inlineDownloadTweetFieldFingerprint.changeFirstString(tweetFieldName)

            val iconColorFieldName =
                InlineActionViewConstructorFingerprint.instructionMatches.first().instruction.getReference<FieldReference>()!!.name
            inlineDownloadIconColorFieldFingerprint.changeFirstString(iconColorFieldName)

            val inlineActionBarClass =
                classDefByOrNull(INLINE_ACTION_BAR_DESCRIPTOR)
                    ?: throw PatchException("InlineActionBar class not found")

            val renderHintsField =
                inlineActionBarClass.fields.firstOrNull { field ->
                    if (!field.type.startsWith(INLINE_ACTIONS_PACKAGE_PREFIX)) return@firstOrNull false

                    val candidateClass = classDefByOrNull(field.type) ?: return@firstOrNull false
                    candidateClass.fields.count { it.type == BOOLEAN_DESCRIPTOR } >= RENDER_HINTS_BOOLEAN_COUNT &&
                        candidateClass.methods.any(::isRenderHintsConstructor)
                } ?: throw PatchException("RenderHints field not found on InlineActionBar")

            inlineDownloadRenderHintsFieldFingerprint.changeFirstString(renderHintsField.name)

            val renderHintsClass =
                classDefByOrNull(renderHintsField.type)
                    ?: throw PatchException("RenderHints class not found: ${renderHintsField.type}")
            val renderHintsConstructor =
                renderHintsClass.methods.firstOrNull(::isRenderHintsConstructor)
                    ?: throw PatchException("RenderHints constructor not found")

            val focalFieldReference =
                renderHintsConstructor.implementation
                    ?.instructions
                    ?.filter { it.opcode == Opcode.IPUT_BOOLEAN }
                    ?.getOrNull(FOCAL_TWEET_FIELD_WRITE_INDEX)
                    ?.let { (it as? ReferenceInstruction)?.reference as? FieldReference }
                    ?: throw PatchException("RenderHints focal field write not found")

            inlineDownloadRenderHintsFocalFieldFingerprint.changeFirstString(focalFieldReference.name)

            enableSettings("inlineDownloadButton")
        }
    }
