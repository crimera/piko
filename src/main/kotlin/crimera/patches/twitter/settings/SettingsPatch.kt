package crimera.patches.twitter.settings

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.settings.fingerprints.InitActivityFingerprint
import crimera.patches.twitter.settings.fingerprints.SettingsFingerprint
import crimera.patches.twitter.settings.fingerprints.SettingsInitFingerprint

@Patch(
    name = "Settings",
    description = "Adds settings",
    requiresIntegrations = true,
    dependencies = [SettingsResourcePatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
object SettingsPatch : BytecodePatch(
    setOf(SettingsFingerprint, SettingsInitFingerprint, InitActivityFingerprint)
) {
    private const val INTEGRATIONS_PACKAGE = "Lapp/revanced/integrations/twitter"
    const val UTILS_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/settings/Utils"
    private const val START_ACTIVITY_DESCRIPTOR =
        "invoke-static {}, $UTILS_DESCRIPTOR;->startActivity()V"

    private const val ADD_CONTEXT_DESCRIPTOR = "invoke-static {p0}, $UTILS_DESCRIPTOR;->setCtx(Landroid/app/Application;)V"

    override fun execute(context: BytecodeContext) {
        val result = SettingsInitFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val initMethod = result.mutableClass.methods.first()

        val arrayCreation = initMethod.getInstructions()
            .first { it.opcode == Opcode.FILLED_NEW_ARRAY_RANGE }.location.index

        initMethod.addInstructions(
            arrayCreation + 2, """
            array-length v1, v0

            add-int/lit8 v1, v1, 0x1

            invoke-static {v0, v1}, Ljava/util/Arrays;->copyOf([Ljava/lang/Object;I)[Ljava/lang/Object;

            move-result-object v1

            check-cast v1, [Ljava/lang/String;

            .local v1, "bigger":[Ljava/lang/String;
            array-length v2, v0

            const-string v3, "pref_mod"

            aput-object v3, v1, v2
    
            move-object v0, v1
        """.trimIndent()
        )

        val h0Method = result.mutableClass.methods.first { it.returnType == "Z" }

        val igetObjectIndex = h0Method.getInstructions()
            .first { it.opcode == Opcode.IGET_OBJECT }.location.index

        // startActivity
        h0Method.addInstructions(
            igetObjectIndex + 1, """
            const-string v0, "Working"
            $START_ACTIVITY_DESCRIPTOR
            const/4 v3, 0x1
            return v3
        """.trimIndent(),
        )

        // if block end
        val ifBlockEnd = h0Method.getInstructions().first { it.opcode == Opcode.RETURN }.location.index+1

        h0Method.addInstructionsWithLabels(igetObjectIndex+1,
        """
            const-string v1, "pref_mod" 
            invoke-virtual {p1, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
            move-result v2

            if-nez v2, :cond_idk
            goto :end
        """.trimIndent(),
            ExternalLabel("end", h0Method.getInstructions().first { it.opcode == Opcode.CONST_STRING }),
            ExternalLabel("cond_idk", h0Method.getInstruction(ifBlockEnd)),
        )

        // Add context reference
        val initActivity = InitActivityFingerprint.result!!.mutableClass.methods.last{ it.name == "onCreate" }
        print(initActivity.name)

        initActivity.addInstruction(initActivity.getInstructions().lastIndex, ADD_CONTEXT_DESCRIPTOR)
    }
}