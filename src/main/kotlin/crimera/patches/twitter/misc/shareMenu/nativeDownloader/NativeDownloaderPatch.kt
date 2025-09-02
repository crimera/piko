package crimera.patches.twitter.misc.shareMenu.nativeDownloader

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.shared.misc.mapping.ResourceMappingPatch
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.iface.Annotation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.MethodImplementation
import com.android.tools.smali.dexlib2.iface.MethodParameter
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.misc.shareMenu.fingerprints.ActionEnumsFingerprint
import crimera.patches.twitter.misc.shareMenu.fingerprints.ShareMenuButtonFuncCallFingerprint
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonAddHook
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonInitHook
import crimera.patches.twitter.models.ExtMediaEntityPatch
import crimera.patches.twitter.models.TweetEntityPatch
import crimera.patches.twitter.models.extractDescriptors

class InitMethod(
    private val validator: () -> Unit,
    private val compare: (other: MethodReference) -> Int,
    private val definingClass: String,
    private val name: String,
    private val parameterTypes: MutableList<out CharSequence>,
    private val returnType: String,
    private val annotations: MutableSet<out Annotation>,
    private val accessFlags: Int,
    private val hiddenApiRestrictions: MutableSet<HiddenApiRestriction>,
    private val parameters: MutableList<out MethodParameter>,
    private val implementation: MethodImplementation,
) : Method {
    override fun validateReference() = validator()

    override fun compareTo(other: MethodReference): Int = this.compare(other)

    override fun getDefiningClass(): String = definingClass

    override fun getName(): String = name

    override fun getParameterTypes(): MutableList<out CharSequence> = parameterTypes

    override fun getReturnType(): String = returnType

    override fun getAnnotations(): MutableSet<out Annotation> = annotations

    override fun getAccessFlags(): Int = accessFlags

    override fun getHiddenApiRestrictions(): MutableSet<HiddenApiRestriction> = hiddenApiRestrictions

    override fun getParameters(): MutableList<out MethodParameter> = parameters

    override fun getImplementation(): MethodImplementation = implementation
}

fun instructionToString(ins: Instruction): String =
    when (ins) {
        is Instruction21c -> {
            if (ins.opcode == Opcode.CONST_STRING) {
                "${ins.opcode.name} v${ins.registerA}, \"${ins.reference}\""
            } else {
                "${ins.opcode.name} v${ins.registerA}, ${ins.reference}"
            }
        }

        is Instruction11n -> {
            "${ins.opcode.name} v${ins.registerA}, ${ins.narrowLiteral}"
        }

        is Instruction35c -> {
            var regs = ""

            val regMap =
                mapOf(
                    1 to ins.registerC,
                    2 to ins.registerD,
                    3 to ins.registerE,
                    4 to ins.registerF,
                    5 to ins.registerG,
                )

            for (i in 1..ins.registerCount) {
                regs += "v${regMap[i]}"

                if (ins.registerCount != i) {
                    regs += ", "
                }
            }
            "${ins.opcode.name} {$regs}, ${ins.reference}"
        }

        is Instruction21s -> {
            "${ins.opcode.name} v${ins.registerA}, ${ins.narrowLiteral}"
        }

        is Instruction22x -> {
            "${ins.opcode.name} v${ins.registerA}, v${ins.registerB}"
        }

        is Instruction3rc -> {
            "${ins.opcode.name} {v${ins.startRegister} .. v${ins.registerCount - 1}}, ${ins.reference}"
        }

        is Instruction11x -> {
            "${ins.opcode.name} v${ins.registerA}"
        }

        is Instruction10x -> {
            ins.opcode.name
        }

        is Instruction31i -> {
            "${ins.opcode.name} v${ins.registerA}, ${ins.narrowLiteral}"
        }

        else -> {
            throw PatchException("${ins.javaClass} is not supported")
        }
    }

val MethodFingerprint.exception: PatchException
    get() = PatchException("${this.javaClass.name} is not found")

val MutableList<BuilderInstruction>.indexOfLastNewInstance
    get() = this.indexOfLast { it.opcode == Opcode.NEW_INSTANCE }

val MutableList<BuilderInstruction>.indexOfLastFilledNewArrayRange
    get() = this.indexOfLast { it.opcode == Opcode.FILLED_NEW_ARRAY_RANGE }

@Patch(
    name = "Custom downloader",
    description = "Requires X 11.0.0-release.0 or higher.",
    dependencies = [SettingsPatch::class, TweetEntityPatch::class, ExtMediaEntityPatch::class, ResourceMappingPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true,
)
@Suppress("unused")
object NativeDownloaderPatch : BytecodePatch(
    setOf(
        ShareMenuButtonFuncCallFingerprint,
        ShareMenuButtonInitHook,
        SettingsStatusLoadFingerprint,
        ShareMenuButtonAddHook,
        ActionEnumsFingerprint,
    ),
) {
    //  var offset: Boolean = false

    override fun execute(context: BytecodeContext) {
        // TODO: make this whole button addition in a new function or a class?

        val buttonName = "Download"

        // Add action
        val downloadActionReference = ActionEnumsFingerprint.addAction(buttonName, ActionEnumsFingerprint.result!!)

        // Register button
        ShareMenuButtonAddHook.registerButton(buttonName, "enableNativeDownloader")
        val viewDebugDialogReference =
            (
                ShareMenuButtonAddHook.result
                    ?.method
                    ?.implementation
                    ?.instructions
                    ?.first { it.opcode == Opcode.SGET_OBJECT } as Instruction21c
            ).reference

        // Set Button Text
        ShareMenuButtonInitHook.setButtonText(buttonName, "piko_pref_native_downloader_alert_title")
        ShareMenuButtonInitHook.setButtonIcon(buttonName, "ic_vector_incoming")

        // Add Button function
        // TODO: handle possible nulls
        val buttonFunc = ShareMenuButtonFuncCallFingerprint.result
        val buttonFuncMethod =
            ShareMenuButtonFuncCallFingerprint.result
                ?.method
                ?.implementation
                ?.instructions
                ?.toList()
        val deleteStatusLoc =
            buttonFunc
                ?.scanResult
                ?.stringsScanResult
                ?.matches
                ?.first { it.string == "Delete Status" }
                ?.index
                ?: throw PatchException("Delete status not found")
        val OkLoc =
            buttonFunc
                ?.scanResult
                ?.stringsScanResult
                ?.matches
                ?.first { it.string == "OK" }
                ?.index
                ?: throw PatchException("OK not found")
        val conversationalRepliesLoc =
            buttonFunc.scanResult.stringsScanResult
                ?.matches
                ?.first {
                    it.string ==
                        "conversational_replies_android_pinned_replies_creation_enabled"
                }?.index
                ?: throw PatchException("conversational_replies_android_pinned_replies_creation_enabled not found")
        val timelineRef =
            (
                buttonFuncMethod
                    ?.filterIndexed { i, ins ->
                        i > conversationalRepliesLoc && ins.opcode == Opcode.IGET_OBJECT
                    }?.first() as Instruction22c?
            ) ?: throw PatchException("Failed to find timelineRef")
        val timelineRefReg = (buttonFuncMethod?.get(deleteStatusLoc - 1) as Instruction35c).registerD
        val activityRefReg = (buttonFuncMethod[OkLoc - 3] as Instruction35c).registerD

        ShareMenuButtonFuncCallFingerprint.addButtonInstructions(
            downloadActionReference,
            """
            check-cast v$timelineRefReg, ${timelineRef.reference.extractDescriptors()[0]}
            iget-object v1, v$timelineRefReg, ${timelineRef.reference}
                
            invoke-static {v$activityRefReg, v1}, ${SettingsPatch.NATIVE_DESCRIPTOR}/NativeDownloader;->downloader(Landroid/content/Context;Ljava/lang/Object;)V
            """.trimIndent(),
            viewDebugDialogReference,
        )

        SettingsStatusLoadFingerprint.enableSettings("nativeDownloader")
    }
}
