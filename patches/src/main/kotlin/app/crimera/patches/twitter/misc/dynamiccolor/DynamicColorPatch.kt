/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.dynamiccolor

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.INTEGRATIONS_PACKAGE
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.string
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private const val DYNAMIC_COLOR_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/DynamicColor;"
private const val APP_DETAILS_ACTIVITY = "android.app.AppDetailsActivity"

private object ThemeApplierClassFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters =
        listOf(
            string("coreTheme"),
        ),
)

private object ThemeApplierFingerprint : Fingerprint(
    classFingerprint = ThemeApplierClassFingerprint,
    returnType = "V",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = "Landroid/content/Context;",
                name = "getTheme",
                returnType = "Landroid/content/res/Resources\$Theme;",
            ),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = "Ljava/lang/Number;",
                name = "intValue",
                returnType = "I",
            ),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = "Landroid/content/res/Resources\$Theme;",
                name = "applyStyle",
                parameters = listOf("I", "Z"),
                returnType = "V",
            ),
        ),
    custom = { methodDef, _ ->
        AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            methodDef.parameters.size == 3 &&
            methodDef.parameters.first().type == "Landroid/content/Context;"
    },
)

private object ComponentFactoryActivityFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/app/di/ComponentFactory;",
    parameters =
        listOf(
            "Ljava/lang/ClassLoader;",
            "Ljava/lang/String;",
            "Landroid/content/Intent;",
        ),
    returnType = "Landroid/app/Activity;",
    custom = { methodDef, _ ->
        !AccessFlags.STATIC.isSet(methodDef.accessFlags) &&
            methodDef.implementation?.registerCount?.let { it >= 5 } == true
    },
)

private val dynamicColorResourcePatch =
    resourcePatch {
        execute {
            copyResources(
                "twitter/dynamiccolor",
                ResourceGroup("values-v31", "styles.xml"),
            )
        }
    }

@Suppress("unused")
val dynamicColorPatch =
    bytecodePatch(
        name = "Dynamic color",
        description = "Adds an option to replace Twitter Blue with the user's Material You palette.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, dynamicColorResourcePatch)

        execute {
            ThemeApplierFingerprint.apply {
                val intValueIndex = instructionMatches[1].index
                val applyStyleIndex = instructionMatches[2].index
                val styleRegister = method.getInstruction(intValueIndex + 1).registersUsed[0]

                method.addInstructions(
                    applyStyleIndex + 1,
                    """
                    invoke-static {p0, v$styleRegister}, $DYNAMIC_COLOR_DESCRIPTOR->applyThemeStyle(Landroid/content/res/Resources${'$'}Theme;I)V
                    """.trimIndent(),
                )
            }

            ComponentFactoryActivityFingerprint.apply {
                method.addInstructions(
                    0,
                    """
                    const-string v0, "$APP_DETAILS_ACTIVITY"
                    invoke-virtual {v0, p2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :piko_component_factory_continue
                    invoke-static {p1, p2, p3}, $DYNAMIC_COLOR_DESCRIPTOR->instantiateFrameworkActivity(Ljava/lang/ClassLoader;Ljava/lang/String;Landroid/content/Intent;)Landroid/app/Activity;
                    move-result-object v0
                    return-object v0
                    :piko_component_factory_continue
                    """.trimIndent(),
                )
            }

            enableSettings("dynamicColor")
        }
    }
