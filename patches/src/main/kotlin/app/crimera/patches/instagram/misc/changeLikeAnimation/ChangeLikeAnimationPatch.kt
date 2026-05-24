/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.changeLikeAnimation

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

private const val EXTENSION_CLASS_DESCRIPTOR = "$PATCHES_DESCRIPTOR/feed/ChangeLikeAnimationPatch;"

internal object ChangeLikeAnimationExtensionFingerprint : Fingerprint(
    name = "changeLikeAnimation",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object XDTUserActivationMetadataImplInitFingerprint : Fingerprint(
    name = "<init>",
    definingClass = "Lcom/instagram/api/schemas/XDTUserActivationMetadataImpl;",
)

@Suppress("unused")
val changeLikeAnimationPatch =
    bytecodePatch(
        name = "Change like animation",
        description = "Change the animation to one from existing Rings like animations",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {

            XDTUserActivationMetadataImplInitFingerprint.method.apply {
                val animationEnumClassType = parameters[0].type
                ChangeLikeAnimationExtensionFingerprint.changeFirstString(classNameToExtension(animationEnumClassType))

                addInstructionsWithLabels(
                    0,
                    """
                    sget-object p2, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                    invoke-static {p1}, $EXTENSION_CLASS_DESCRIPTOR->changeLikeAnimation(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v0
                    if-eqz v0, :piko
                    sget-object p2, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                    check-cast v0, $animationEnumClassType
                    move-object/from16 p1, v0
                    """.trimIndent(),
                    ExternalLabel("piko", getInstruction(0)),
                )
                enableSettings("changeLikeAnimation")
            }
        }
    }
