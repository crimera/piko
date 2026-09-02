/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.sensitivemediasettings

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.crimera.utils.instructionToString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.findFreeRegister
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

// Credits to @Cradlesofashes

private const val EXTENSION_CLASS =
    "$PATCHES_DESCRIPTOR/TimelineEntry;"
private const val EXTENSION_JSON_SENSITIVE_MEDIA_WARNING_INTERFACE =
    $$"$$PATCHES_DESCRIPTOR/TimelineEntry$JsonSensitiveMediaWarningPatchInterface;"
private const val EXTENSION_JSON_BLURRED_IMAGE_INTERSTITIAL_INTERFACE =
    $$"$$PATCHES_DESCRIPTOR/TimelineEntry$JsonBlurredImageInterstitialPatchInterface;"
private const val JSON_SENSITIVE_MEDIA_WARNING_CLASS_PREFIX =
    "Lcom/twitter/model/json/core/JsonSensitiveMediaWarning"
private const val JSON_BLURRED_IMAGE_INTERSTITIAL_CLASS_PREFIX =
    "Lcom/twitter/model/json/mediavisibility/JsonBlurredImageInterstitial"
private const val BLURRED_IMAGE_INTERSTITIAL_CLASS =
    "Lcom/x/models/interstitial/BlurImageInterstitial;"

private inline fun applyOptionalPatch(block: () -> Unit): Boolean =
    try {
        block()
        true
    } catch (_: PatchException) {
        false
    }

internal fun getJsonFingerprint(classPrefix: String) = object : Fingerprint(
    definingClass = "$classPrefix;",
    name = "<init>"
) {}

internal fun getJsonObjectMapperFingerprint(classPrefix: String) = object : Fingerprint(
    definingClass = $$$"$$$classPrefix$$JsonObjectMapper;",
    name = "parse",
    returnType = "Ljava/lang/Object",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            returnType = "$classPrefix;"
        ),
        opcode(
            opcode = Opcode.MOVE_RESULT_OBJECT,
            location = MatchAfterImmediately()
        )
    )
) {}

private object SensitiveMediaInterstitialProfileFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/view/View;->setVisibility(I)V"
        ),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            name = "getTitle",
            parameters = listOf(),
            returnType = "Ljava/lang/String;"
        ),
        resourceLiteral(
            type = ResourceType.ID,
            name = "warning_body"
        ),
        resourceLiteral(
            type = ResourceType.ID,
            name = "primary_button"
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Landroid/view/View;->setOnClickListener(Landroid/view/View\$OnClickListener;)V",
            location = MatchAfterWithin(10)
        )
    )
)

private object SensitiveMediaInterstitialViewFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    filters = listOf(
        resourceLiteral(
            type = ResourceType.STRING,
            name = "sensitive_media_interstitial_show"
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "setText"
        )
    )
)

private object ComposeSensitiveMediaInterstitialFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Landroidx/compose/runtime/Composer;", "I"),
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            smali = "Lcom/x/models/interstitial/MediaVisibilityResults;->getBlurImageInterstitial()$BLURRED_IMAGE_INTERSTITIAL_CLASS",
        ),
        opcode(
            opcode = Opcode.MOVE_RESULT_OBJECT,
            location = MatchAfterImmediately()
        )
    )
)

@Suppress("unused")
val sensitiveMediaPatch =
    bytecodePatch(
        name = "Show sensitive media",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(
            settingsPatch,
            resourceMappingPatch
        )

        execute {
            // region Override the sensitive media fields in API response
            val appliedLegacyApiHooks = applyOptionalPatch {
                getJsonFingerprint(JSON_BLURRED_IMAGE_INTERSTITIAL_CLASS_PREFIX).classDef.apply {
                    interfaces.add(EXTENSION_JSON_BLURRED_IMAGE_INTERSTITIAL_INTERFACE)

                    val interstitialActionField = fields.find { field ->
                        field.type == "Ljava/lang/String;"
                    }
                    val verificationOptionsField = fields.find { field ->
                        field.type == "Ljava/lang/Object;"
                    }

                    methods.add(
                        ImmutableMethod(
                            type,
                            "patch_showSensitiveMedia",
                            listOf(),
                            "V",
                            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                            null,
                            null,
                            MutableMethodImplementation(2),
                        ).toMutable().apply {
                            addInstructions(
                                0,
                                """
                                    const-string v0, ""
                                    iput-object v0, p0, $interstitialActionField
                                    invoke-static { }, Ljava/util/Collections;->emptyList()Ljava/util/List;
                                    move-result-object v0
                                    iput-object v0, p0, $verificationOptionsField
                                    return-void
                                """
                            )
                        }
                    )
                }

                getJsonFingerprint(JSON_SENSITIVE_MEDIA_WARNING_CLASS_PREFIX).classDef.apply {
                    interfaces.add(EXTENSION_JSON_SENSITIVE_MEDIA_WARNING_INTERFACE)

                    var smaliInstructions =
                        """
                            const/4 v0, 0x0
                        """
                    fields.filter { it.type == "Z" }.forEach { field ->
                        smaliInstructions +=
                            """
                                iput-boolean v0, p0, $field
                            """
                    }
                    smaliInstructions +=
                        """
                            return-void
                        """

                    methods.add(
                        ImmutableMethod(
                            type,
                            "patch_showSensitiveMedia",
                            listOf(),
                            "V",
                            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                            null,
                            null,
                            MutableMethodImplementation(2),
                        ).toMutable().apply {
                            addInstructions(0, smaliInstructions)
                        }
                    )
                }

                mapOf(
                    JSON_BLURRED_IMAGE_INTERSTITIAL_CLASS_PREFIX to EXTENSION_JSON_BLURRED_IMAGE_INTERSTITIAL_INTERFACE,
                    JSON_SENSITIVE_MEDIA_WARNING_CLASS_PREFIX to EXTENSION_JSON_SENSITIVE_MEDIA_WARNING_INTERFACE,
                ).forEach { (classPrefix, patchInterface) ->
                    getJsonObjectMapperFingerprint(classPrefix).let {
                        it.method.apply {
                            val match = it.instructionMatches.last()
                            val index = match.index
                            val register = match.instruction.registersUsed[0]

                            addInstructions(
                                index + 1,
                                """
                                    invoke-static { v$register }, $EXTENSION_CLASS->showSensitiveMedia($patchInterface)$classPrefix;
                                    move-result-object v$register
                                """
                            )
                        }
                    }
                }
            }

            // endregion

            // region Close the profile warning alert dialog (Caution: This profile may include potentially sensitive content)

            applyOptionalPatch {
                SensitiveMediaInterstitialProfileFingerprint.let {
                    it.method.apply {
                        val dialogVisibilityMatch = it.instructionMatches[0]
                        val dialogVisibilityIndex = dialogVisibilityMatch.index
                        val dialogVisibilityRegister = dialogVisibilityMatch.instruction.registersUsed[1]
                        val freeRegister = findFreeRegister(dialogVisibilityIndex)

                        val titleInstruction = instructionToString(it.instructionMatches[1].instruction)

                        val buttonMatch = it.instructionMatches.last()
                        val buttonIndex = buttonMatch.index
                        val buttonRegister = buttonMatch.instruction.registersUsed[0]

                        // If it is a general sensitive media warning, also click the dismiss button on the alert dialog
                        // This is to prevent the UI from breaking due to incorrect WindowInsets calculations, even though the alert dialog is hidden
                        addInstruction(
                            buttonIndex + 1,
                            "invoke-static { v$buttonRegister }, $EXTENSION_CLASS->showSensitiveProfile(Landroid/view/View;)V"
                        )

                        // Check the title of the alert dialog to prevent other profile warnings (such as racism or terrorism) from closing
                        // If it is a general sensitive media warning, hide the alert dialog
                        addInstructions(
                            dialogVisibilityIndex,
                            """
                                $titleInstruction
                                move-result-object v$freeRegister
                                invoke-static { v$freeRegister, v$dialogVisibilityRegister }, $EXTENSION_CLASS->setSensitiveProfileWarningDialogTitle(Ljava/lang/String;I)I
                                move-result v$dialogVisibilityRegister
                            """
                        )
                    }
                }
            }

            // endregion

            // region Click the 'Show' button on the timeline to make the blurred image visible

            applyOptionalPatch {
                SensitiveMediaInterstitialViewFingerprint.let {
                    it.method.apply {
                        val match = it.instructionMatches.last()
                        val index = match.index
                        val register = match.instruction.registersUsed[0]

                        addInstruction(
                            index,
                            "invoke-static { v$register }, $EXTENSION_CLASS->showSensitiveImage(Landroid/view/View;)V"
                        )
                    }
                }
            }

            // endregion

            // region Bypass the Compose sensitive-media interstitial

            val appliedComposeHook = applyOptionalPatch {
                ComposeSensitiveMediaInterstitialFingerprint.let {
                    it.method.apply {
                        val match = it.instructionMatches.last()
                        val index = match.index
                        val register = match.instruction.registersUsed[0]

                        addInstructions(
                            index + 1,
                            """
                                invoke-static { v$register }, $EXTENSION_CLASS->showSensitiveMedia($BLURRED_IMAGE_INTERSTITIAL_CLASS)$BLURRED_IMAGE_INTERSTITIAL_CLASS
                                move-result-object v$register
                            """
                        )
                    }
                }
            }

            if (!appliedLegacyApiHooks && !appliedComposeHook) {
                throw PatchException("Could not find a supported sensitive-media implementation")
            }

            // endregion

            enableSettings("showSensitiveMedia")
        }
    }
