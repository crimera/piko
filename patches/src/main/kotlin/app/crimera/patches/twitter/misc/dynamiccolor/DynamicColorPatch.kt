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
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.string
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import app.morphe.util.findElementByAttributeValue
import app.morphe.util.findElementByAttributeValueOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val DYNAMIC_COLOR_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/DynamicColor;"
private const val DYNAMIC_COLOR_ICON_ALIAS = "app.morphe.extension.twitter.dynamiccoloricon"
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
)

private val dynamicColorResourcePatch =
    resourcePatch {
        execute {
            copyResources(
                "twitter/dynamiccolor",
                ResourceGroup("values-v31", "styles.xml"),
                ResourceGroup("mipmap-anydpi-v26", "ic_launcher_dynamic_color.xml"),
                ResourceGroup("mipmap-anydpi-v31", "ic_launcher_dynamic_color.xml"),
            )

            document("AndroidManifest.xml").use { document ->
                val applicationNode = document.getElementsByTagName("application").item(0)
                val existingAlias =
                    applicationNode.childNodes.findElementByAttributeValue(
                        "android:name",
                        DYNAMIC_COLOR_ICON_ALIAS,
                    )
                if (existingAlias != null) return@use

                val startActivity =
                    applicationNode.childNodes.findElementByAttributeValueOrThrow(
                        "android:name",
                        "com.twitter.android.StartActivity",
                    )
                val activityAlias =
                    document.createElement("activity-alias").apply {
                        setAttribute("android:enabled", "false")
                        setAttribute("android:exported", "true")
                        setAttribute("android:icon", "@mipmap/ic_launcher_dynamic_color")
                        setAttribute("android:name", DYNAMIC_COLOR_ICON_ALIAS)
                        setAttribute("android:roundIcon", "@mipmap/ic_launcher_dynamic_color")
                        setAttribute("android:targetActivity", "com.twitter.android.StartActivity")
                    }
                val intentFilter = document.createElement("intent-filter")
                intentFilter.appendChild(
                    document.createElement("action").apply {
                        setAttribute("android:name", "android.intent.action.MAIN")
                    },
                )
                intentFilter.appendChild(
                    document.createElement("category").apply {
                        setAttribute("android:name", "android.intent.category.LAUNCHER")
                    },
                )
                activityAlias.appendChild(intentFilter)
                activityAlias.appendChild(
                    document.createElement("meta-data").apply {
                        setAttribute("android:name", "appFamilies")
                        setAttribute("android:value", "twitter")
                    },
                )
                activityAlias.appendChild(
                    document.createElement("meta-data").apply {
                        setAttribute("android:name", "mainActivityAliasForAppFamily")
                        setAttribute("android:value", "true")
                    },
                )
                applicationNode.insertBefore(activityAlias, startActivity.nextSibling)
            }
        }
    }

@Suppress("unused")
val dynamicColorPatch =
    bytecodePatch(
        name = "Dynamic color",
        description = "Adds an option to replace Twitter Blue with the user's Material You palette.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, dynamicColorResourcePatch)

        execute {
            ThemeApplierFingerprint.apply {
                if (!AccessFlags.STATIC.isSet(method.accessFlags) ||
                    method.parameters.size != 3 ||
                    method.parameters.first().type != "Landroid/content/Context;"
                ) {
                    throw PatchException("Unexpected Twitter theme applier signature")
                }

                val intValueIndex = instructionMatches[1].index
                val applyStyleIndex = instructionMatches[2].index
                val styleRegister =
                    method
                        .getInstruction<OneRegisterInstruction>(intValueIndex + 1)
                        .registerA

                method.addInstructions(
                    applyStyleIndex + 1,
                    """
                    invoke-static {p0, v$styleRegister}, $DYNAMIC_COLOR_DESCRIPTOR->applyThemeStyle(Landroid/content/res/Resources${'$'}Theme;I)V
                    """.trimIndent(),
                )
            }

            ComponentFactoryActivityFingerprint.apply {
                if (AccessFlags.STATIC.isSet(method.accessFlags)) {
                    throw PatchException("Unexpected static Twitter component factory method")
                }
                val registerCount =
                    method.implementation?.registerCount
                        ?: throw PatchException("Twitter component factory has no implementation")
                if (registerCount < 5) {
                    throw PatchException("Twitter component factory has no local register")
                }

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
