package app.crimera.patches.newx.timeline.postfilter

import app.crimera.patches.newx.misc.postoptions.FILTERED_REPLIES_ACTION
import app.crimera.patches.newx.misc.postoptions.newXPostOption
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.customScreen
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.multiChoice
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.timeline.NewXTimelineSuccessFingerprint
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.numberOfParameterRegistersLogical
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val FILTERED_REPLIES_HANDLER =
    "Lapp/morphe/extension/newx/misc/FilteredRepliesPostOptionHandler;"

@Suppress("unused")
val newXHideVerifiedPostsPatch =
    bytecodePatch(
        name = "NewX: Hide posts by verified account type",
        description = "Hides posts and replies authored by selected timeline-reported verification types.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXTimelineTextModelAdapterPatch)

        val (filterTimeline, filterThread, verifiedTypesToHide) =
            newXSettings {
                category(Categories.CONTENT) {
                    group(Groups.VERIFIED_ACCOUNT_FILTERING) {
                        val filterTimeline =
                            toggle(
                                id = "newx.content.verified_account_filtering.timeline",
                                strings = settingStrings("piko_newx_verified_account_timeline_filter"),
                                order = 100,
                                defaultValue = false,
                            )
                        val filterThread =
                            toggle(
                                id = "newx.content.verified_account_filtering.thread",
                                strings = settingStrings("piko_newx_verified_account_thread_filter"),
                                order = 200,
                                defaultValue = false,
                            )
                        val verifiedTypesToHide =
                            multiChoice(
                                id = "newx.content.hide_verified_account_types",
                                strings = settingStrings("piko_newx_hide_verified_account_types"),
                                order = 300,
                                defaultValue = emptySet(),
                                options =
                                    listOf(
                                        choice("Business", "piko_newx_hide_verified_account_types_business"),
                                        choice("Government", "piko_newx_hide_verified_account_types_government"),
                                        choice("User", "piko_newx_hide_verified_account_types_user"),
                                        choice("Unknown", "piko_newx_hide_verified_account_types_unknown"),
                                    ),
                            )
                        customScreen(
                            id = "newx.content.verified_account_whitelist",
                            strings = settingStrings("piko_newx_verified_account_whitelist"),
                            order = 400,
                            fragmentClassDescriptor =
                                "Lapp/morphe/extension/newx/postfilter/VerifiedAccountWhitelistFragment;",
                        )
                        Triple(filterTimeline, filterThread, verifiedTypesToHide)
                    }
                }
            }

        newXPostOption(
            handlerDescriptor = FILTERED_REPLIES_HANDLER,
            actionName = FILTERED_REPLIES_ACTION,
            iconResourceName = "ic_vector_filter",
            order = 270,
        )

        execute {
            val matches = NewXTimelineSuccessFingerprint.scopedMatchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX timeline success constructor, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            val match = matches.single()
            val originalMethod = match.method
            val method =
                originalMethod.cloneMutable(
                    additionalRegisters = originalMethod.numberOfParameterRegisters + 3,
                ).also { expandedMethod ->
                    match.classDef.methods.remove(originalMethod)
                    match.classDef.methods.add(expandedMethod)
                }
            val timelineItemsRegister =
                resolveTimelineItemsRegister(
                    originalMethod = originalMethod,
                    clonedMethod = method,
                )
            if (timelineItemsRegister > 15) {
                throw PatchException(
                    "NewX timelineItems register must fit a four-bit invoke: " +
                        "v$timelineItemsRegister in $originalMethod",
                )
            }

            method.apply {
                val insertionIndex = originalMethod.numberOfParameterRegistersLogical
                val timelineRead =
                    filterTimeline.injectRead(
                        method = this,
                        index = insertionIndex,
                        excludedRegisters = listOf(timelineItemsRegister),
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                val threadRead =
                    filterThread.injectRead(
                        method = this,
                        index = timelineRead.nextIndex,
                        excludedRegisters = listOf(timelineItemsRegister, timelineRead.register),
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                val typesRead =
                    verifiedTypesToHide.injectRead(
                        method = this,
                        index = threadRead.nextIndex,
                        excludedRegisters = listOf(timelineItemsRegister, timelineRead.register, threadRead.register),
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                addInstructions(
                    typesRead.nextIndex,
                    """
                        invoke-static {v$timelineItemsRegister, v${typesRead.register}, v${timelineRead.register}, v${threadRead.register}}, $TIMELINE_FILTER_DESCRIPTOR->filterPostsByVerifiedType(Ljava/lang/Object;Ljava/util/Set;ZZ)Ljava/lang/Object;
                        move-result-object v$timelineItemsRegister
                    """.trimIndent(),
                )
            }
        }
    }

private fun resolveTimelineItemsRegister(
    originalMethod: app.morphe.patcher.util.proxy.mutableTypes.MutableMethod,
    clonedMethod: app.morphe.patcher.util.proxy.mutableTypes.MutableMethod,
): Int {
    val timelineItemsType =
        originalMethod.parameterTypes.getOrNull(1)?.toString()
            ?: throw PatchException(
                "NewX timeline success constructor has no timelineItems parameter: $originalMethod",
            )
    val originalWrites =
        originalMethod.instructions.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.IPUT_OBJECT) return@mapNotNull null
            val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
            if (field.type != timelineItemsType) return@mapNotNull null
            field
        }.distinct()
    if (originalWrites.size != 1) {
        throw PatchException(
            "Expected one NewX timelineItems field write for type $timelineItemsType, " +
                "found ${originalWrites.size}: ${originalWrites.joinToString()}",
        )
    }
    val timelineItemsField = originalWrites.single()
    val clonedWrites =
        clonedMethod.instructions.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.IPUT_OBJECT) return@mapNotNull null
            val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
            if (field != timelineItemsField) return@mapNotNull null
            val write = instruction as? TwoRegisterInstruction
                ?: throw PatchException(
                    "NewX timelineItems field write has no two-register layout: $instruction",
                )
            write.registerA
        }
    if (clonedWrites.size != 1) {
        throw PatchException(
            "Expected one cloned NewX timelineItems field write for $timelineItemsField, " +
                "found ${clonedWrites.size}",
        )
    }
    return clonedWrites.single()
}
