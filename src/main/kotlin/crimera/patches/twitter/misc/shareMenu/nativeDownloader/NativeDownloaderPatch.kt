package crimera.patches.twitter.misc.shareMenu.nativeDownloader

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
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
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.misc.shareMenu.fingerprints.ActionEnumsFingerprint
import crimera.patches.twitter.misc.shareMenu.fingerprints.ShareMenuButtonFuncCallFingerprint
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonAddHook
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonInitHook

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
    private val implementation: MethodImplementation
) : Method {
    override fun validateReference() {
        return validator()
    }

    override fun compareTo(other: MethodReference): Int {
        return this.compare(other)
    }

    override fun getDefiningClass(): String {
        return definingClass
    }

    override fun getName(): String {
        return name
    }

    override fun getParameterTypes(): MutableList<out CharSequence> {
        return parameterTypes
    }

    override fun getReturnType(): String {
        return returnType
    }

    override fun getAnnotations(): MutableSet<out Annotation> {
        return annotations
    }

    override fun getAccessFlags(): Int {
        return accessFlags
    }

    override fun getHiddenApiRestrictions(): MutableSet<HiddenApiRestriction> {
        return hiddenApiRestrictions
    }

    override fun getParameters(): MutableList<out MethodParameter> {
        return parameters
    }

    override fun getImplementation(): MethodImplementation {
        return implementation
    }
}

fun instructionToString(ins: Instruction): String {

    return when (ins) {
        is Instruction21c -> {
            if (ins.opcode == Opcode.CONST_STRING) "${ins.opcode.name} v${ins.registerA}, \"${ins.reference}\""
            else "${ins.opcode.name} v${ins.registerA}, ${ins.reference}"
        }

        is Instruction11n -> {
            "${ins.opcode.name} v${ins.registerA}, ${ins.narrowLiteral}"
        }

        is Instruction35c -> {
            var regs = ""

            val regMap = mapOf(
                1 to ins.registerC, 2 to ins.registerD, 3 to ins.registerE
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
}

val MethodFingerprint.exception: PatchException
    get() = PatchException("${this.javaClass.name} is not found")

val MutableList<BuilderInstruction>.indexOfLastNewInstance
    get() = this.indexOfLast { it.opcode == Opcode.NEW_INSTANCE }

val MutableList<BuilderInstruction>.indexOfLastFilledNewArrayRange
    get() = this.indexOfLast { it.opcode == Opcode.FILLED_NEW_ARRAY_RANGE }

@Patch(
    name = "Custom downloader",
    description = "",
    dependencies = [SettingsPatch::class, NativeDownloaderHooksPatch::class, ResourceMappingPatch::class],
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
        ActionEnumsFingerprint
    ),
) {
    var offset: Boolean = false

    override fun execute(context: BytecodeContext) {
        // TODO: make this whole button addition in a new function or a class?

        val buttonName = "Download"

        // Add action
        val downloadActionReference = ActionEnumsFingerprint.addAction(buttonName, ActionEnumsFingerprint.result!!)

        // Register button
        ShareMenuButtonAddHook.registerButton(buttonName, "enableNativeDownloader")
        val viewDebugDialogReference =
            (ShareMenuButtonAddHook.result?.method?.implementation?.instructions?.last { it.opcode == Opcode.SGET_OBJECT } as Instruction21c).reference

        // Set Button Text
        ShareMenuButtonInitHook.setButtonText(buttonName, "piko_pref_native_downloader_alert_title")
        ShareMenuButtonInitHook.setButtonIcon(buttonName, "ic_vector_incoming")

        // Add Button function
        // TODO: handle possible nulls
        val buttonFunc = ShareMenuButtonFuncCallFingerprint.result
        val buttonFuncMethod = ShareMenuButtonFuncCallFingerprint.result?.mutableMethod
        val deleteStatusLoc = buttonFunc?.scanResult?.stringsScanResult?.matches!!.first().index
        val activityRefReg = buttonFuncMethod?.getInstruction<TwoRegisterInstruction>(deleteStatusLoc + 1)?.registerA
        val timelineRefReg = buttonFuncMethod?.getInstruction<Instruction35c>(deleteStatusLoc - 1)?.registerD

        ShareMenuButtonFuncCallFingerprint.addButtonInstructions(
            downloadActionReference, """
            check-cast v$timelineRefReg, Lcom/twitter/model/timeline/n2;
            iget-object v1, v$timelineRefReg, Lcom/twitter/model/timeline/n2;->k:Lcom/twitter/model/core/e;
            
            invoke-virtual/range{v$activityRefReg .. v$activityRefReg}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
            move-result-object v0
            check-cast v0, Landroid/app/Activity;
                
            invoke-static {v0, v1}, ${SettingsPatch.PATCHES_DESCRIPTOR}/NativeDownloader;->downloader(Landroid/content/Context;Ljava/lang/Object;)V
        """.trimIndent(), viewDebugDialogReference
        )

        SettingsStatusLoadFingerprint.enableSettings("nativeDownloader")
    }
}
