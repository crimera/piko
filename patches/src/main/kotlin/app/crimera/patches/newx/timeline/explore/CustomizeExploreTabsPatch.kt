/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.timeline.explore

import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.EXPLORE_TAB_FILTER_DESCRIPTOR
import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.settings.group
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.settings.toggle
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val COLLECTIONS_KT_DESCRIPTOR = "Lkotlin/collections/CollectionsKt;"

/** The one anchor the UrpTimeline model cannot shed: the prefix of its generated toString. */
private const val URP_TIMELINE_TOSTRING_ANCHOR = "UrpTimeline(title="

private const val EXPLORE_TAB_FILTER_METHOD =
    "$EXPLORE_TAB_FILTER_DESCRIPTOR->filter(Ljava/util/List;)Ljava/util/List;"

/**
 * The tab factory is the one static List-returning method that builds at least two
 * UrpTimeline instances (the single-timeline shortcut and the mapped tab) and falls
 * back to CollectionsKt.emptyList when the response carries no tab section.
 */
private fun Method.isUrpTimelineFactory(modelType: String): Boolean {
    if (!AccessFlags.STATIC.isSet(accessFlags)) return false
    if (returnType != LIST_DESCRIPTOR || parameterTypes.size != 1) return false

    val instructions = implementation?.instructions?.toList() ?: return false
    val constructions =
        instructions.count { instruction ->
            instruction.opcode == Opcode.NEW_INSTANCE &&
                instruction.getReference<TypeReference>()?.type == modelType
        }
    if (constructions < 2) return false

    return instructions.any { instruction ->
        (instruction.opcode == Opcode.INVOKE_STATIC ||
            instruction.opcode == Opcode.INVOKE_STATIC_RANGE) &&
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.definingClass == COLLECTIONS_KT_DESCRIPTOR &&
                    reference.name == "emptyList"
            } == true
    }
}

private fun resolveUrpTimelineType(): String {
    val candidates = mutableListOf<String>()
    classDefForEach { classDef ->
        val anchored =
            classDef.methods.any { method ->
                method.implementation?.instructions?.any { instruction ->
                    instruction.opcode == Opcode.CONST_STRING &&
                        instruction.getReference<StringReference>()?.string ==
                            URP_TIMELINE_TOSTRING_ANCHOR
                } == true
            }
        if (anchored) candidates += classDef.type
    }
    return requireExactlyOne("NewX UrpTimeline model class", candidates) { it }
}

private fun resolveUrpTimelineFactory(modelType: String): MutableMethod {
    val classCandidates = mutableListOf<String>()
    classDefForEach { classDef ->
        if (classDef.methods.any { method ->
                method.implementation != null && method.isUrpTimelineFactory(modelType)
            }
        ) {
            classCandidates += classDef.type
        }
    }
    val factoryClass =
        requireExactlyOne("NewX UrpTimeline factory class", classCandidates) { it }
    val methods =
        mutableClassDefBy(factoryClass).methods.filter { method ->
            method.isUrpTimelineFactory(modelType)
        }
    return requireExactlyOne("NewX UrpTimeline factory method", methods) { it.toString() }
}

@Suppress("unused")
val customizeNewXExploreTabsPatch =
    patch(
        name = "NewX: Customize explore tabs",
        description =
            "Lets you hide tabs (For You, Trending, News, Sports, Entertainment) " +
                "from the NewX Explore screen.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)
        category("NewX patches")
        dependsOn(newXExtensionPatch)

        newXSettings {
            category(Categories.TIMELINE) {
                group(Groups.EXPLORE_TABS) {
                    toggle(
                        id = "newx.explore_tabs.hide_for_you",
                        strings = settingStrings("piko_newx_explore_tabs_hide_for_you"),
                        order = 100,
                        defaultValue = false,
                        rebootApp = true,
                    )
                    toggle(
                        id = "newx.explore_tabs.hide_trending",
                        strings = settingStrings("piko_newx_explore_tabs_hide_trending"),
                        order = 110,
                        defaultValue = false,
                        rebootApp = true,
                    )
                    toggle(
                        id = "newx.explore_tabs.hide_news",
                        strings = settingStrings("piko_newx_explore_tabs_hide_news"),
                        order = 120,
                        defaultValue = false,
                        rebootApp = true,
                    )
                    toggle(
                        id = "newx.explore_tabs.hide_sports",
                        strings = settingStrings("piko_newx_explore_tabs_hide_sports"),
                        order = 130,
                        defaultValue = false,
                        rebootApp = true,
                    )
                    toggle(
                        id = "newx.explore_tabs.hide_entertainment",
                        strings = settingStrings("piko_newx_explore_tabs_hide_entertainment"),
                        order = 140,
                        defaultValue = false,
                        rebootApp = true,
                    )
                }
            }
        }

        execute {
            val modelType = resolveUrpTimelineType()
            val factory = resolveUrpTimelineFactory(modelType)

            val returns =
                factory.instructions.withIndex().filter {
                    it.value.opcode == Opcode.RETURN_OBJECT
                }
            if (returns.isEmpty()) {
                throw PatchException(
                    "Expected at least one return in the NewX UrpTimeline factory: $factory",
                )
            }

            // Insert from the last return backwards so earlier indices stay valid.
            // addInstructionsAtControlFlowLabel retargets branches that land on a
            // return to the hook, so every exit path passes through the filter.
            for ((index, instruction) in returns.asReversed()) {
                val register = (instruction as OneRegisterInstruction).registerA
                factory.addInstructionsAtControlFlowLabel(
                    index,
                    """
                    invoke-static {v$register}, $EXPLORE_TAB_FILTER_METHOD
                    move-result-object v$register
                    """.trimIndent(),
                )
            }
        }
    }
