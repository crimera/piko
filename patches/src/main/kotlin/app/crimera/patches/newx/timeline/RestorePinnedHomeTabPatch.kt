package app.crimera.patches.newx.timeline

import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val PINNED_HOME_TAB_RESTORE_DESCRIPTOR =
    "Lapp/morphe/extension/newx/timeline/PinnedHomeTabRestore;"
private const val PINNED_HOME_TAB_DESCRIPTOR =
    "Lapp/morphe/extension/newx/timeline/PinnedHomeTabRestore\$PinnedHomeTab;"
private const val PINNED_HOME_TAB_KEY_NAME = "pikoPinnedTabKey"
private const val ON_TAB_CHANGE_DESCRIPTOR = "$PINNED_HOME_TAB_RESTORE_DESCRIPTOR->onTabChange(II)V"
private const val ON_TAB_SELECTED_DESCRIPTOR =
    "$PINNED_HOME_TAB_RESTORE_DESCRIPTOR->onTabSelected($OBJECT_DESCRIPTOR)V"
private const val PINNED_TAB_TO_RESTORE_DESCRIPTOR =
    "$PINNED_HOME_TAB_RESTORE_DESCRIPTOR->pinnedTabToRestore($LIST_DESCRIPTOR)I"
private const val NOT_PINNED_LABEL = "piko_newx_pinned_tab_key_not_pinned"

/** HomeTabbedComponent's constructor: the only home pager that saves the custom-timeline-tag state. */
private object NewXHomeTabbedComponentFingerprint : Fingerprint(
    definingClass = "Lcom/x/home/tabbed/",
    name = "<init>",
    returnType = "V",
    strings = listOf("custom_timeline_tag_consumed"),
)

/** PinnedTimeline's tab id (`c_`/`l_`/`g_`/`t_` + community, list, generic or topic id). */
private object NewXPinnedTimelineTabIdFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/pinnedtimelines/",
    parameters = emptyList(),
    returnType = STRING_DESCRIPTOR,
    strings = listOf("c_", "l_", "g_", "t_"),
)

/** `PagesNavigation.select(index)` reached through the component's navigation field. */
private data class TabSelectCall(
    val navigationField: FieldReference,
    val select: MethodReference,
)

/** The checked-cast tab route and the read of its tab config, inside the tab-change method. */
private data class TabRouteRead(
    val castIndex: Int,
    val routeRegister: Int,
    val tabConfigField: FieldReference,
)

@Suppress("unused")
val restorePinnedHomeTabPatch =
    bytecodePatch(
        name = "NewX: Restore pinned home tab",
        description = "Reopens the pinned list, topic or community tab you last viewed when the app starts.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        newXToggle(
            id = "newx.timeline.restore_pinned_tab",
            category = Categories.TIMELINE,
            strings = settingStrings("piko_newx_restore_pinned_tab"),
            order = 152,
            defaultValue = true,
        )

        execute {
            val componentType =
                requireExactlyOne(
                    "NewX home tabbed component constructor",
                    NewXHomeTabbedComponentFingerprint.scopedMatchAll(),
                ).originalClassDef.type
            val component = mutableClassDefBy(componentType)

            // The pager's tab-change handler, `onPageSelected(index, currentIndex)`, and the pinned-tab
            // merge, `onPinnedTabsLoaded(routes)`, both select through the same navigation field.
            val tabChange =
                requireExactlyOne(
                    "NewX home tab-change method",
                    component.methods.filter { method ->
                        method.isInstanceMethod(listOf("I", "I")) &&
                            method.tabSelectCalls(componentType).isNotEmpty() &&
                            method.tabRouteReads().isNotEmpty()
                    },
                )
            val pinnedTabsLoaded =
                requireExactlyOne(
                    "NewX home pinned-tab merge method",
                    component.methods.filter { method ->
                        method.isInstanceMethod(listOf(LIST_DESCRIPTOR)) &&
                            method.tabSelectCalls(componentType).isNotEmpty()
                    },
                )
            val select =
                requireExactlyOne(
                    "NewX home tab select call",
                    (tabChange.tabSelectCalls(componentType) + pinnedTabsLoaded.tabSelectCalls(componentType))
                        .distinctBy { call -> "${call.navigationField}|${call.select}" },
                )
            val routeRead = requireExactlyOne("NewX home tab route read", tabChange.tabRouteReads())
            val routeType = routeRead.tabConfigField.definingClass.toString()
            val tabConfigType = routeRead.tabConfigField.type.toString()
            // The route wraps the sealed tab config (For You, Following, pinned, ...), never a concrete tab.
            val tabConfigFlags = mutableClassDefBy(tabConfigType).accessFlags
            if (!AccessFlags.ABSTRACT.isSet(tabConfigFlags) || AccessFlags.INTERFACE.isSet(tabConfigFlags)) {
                throw PatchException("NewX home tab route config is not a sealed tab config class: $tabConfigType")
            }

            val pinnedTabId =
                requireExactlyOne(
                    "NewX pinned timeline tab id",
                    NewXPinnedTimelineTabIdFingerprint.scopedMatchAll(),
                )
            if (AccessFlags.STATIC.isSet(pinnedTabId.originalMethod.accessFlags) ||
                !AccessFlags.PUBLIC.isSet(pinnedTabId.originalMethod.accessFlags)
            ) {
                throw PatchException("NewX pinned timeline tab id is not a public instance method: ${pinnedTabId.originalMethod}")
            }
            val pinnedTimelineType = pinnedTabId.originalClassDef.type

            // The pinned tab config is the home tab config subclass that wraps a pinned timeline.
            val pinnedTabConfigType =
                requireExactlyOne(
                    "NewX pinned home tab config",
                    pinnedTabConfigFingerprint(tabConfigType, pinnedTimelineType).scopedMatchAll(),
                ).originalClassDef.type
            val pinnedTimelineField =
                requireExactlyOne(
                    "NewX pinned home tab config timeline field",
                    mutableClassDefBy(pinnedTabConfigType).fields.filter { field ->
                        !AccessFlags.STATIC.isSet(field.accessFlags) &&
                            field.type == pinnedTimelineType
                    },
                )
            if (!AccessFlags.PUBLIC.isSet(pinnedTimelineField.accessFlags)) {
                throw PatchException("NewX pinned home tab config timeline field is not public: $pinnedTimelineField")
            }

            installPinnedTabKeyBridge(
                routeType = routeType,
                tabConfigField = routeRead.tabConfigField,
                pinnedTabConfigType = pinnedTabConfigType,
                pinnedTimelineField = pinnedTimelineField,
                pinnedTabId = pinnedTabId.originalMethod,
            )

            // Restore before X's own deferred selection, so a deep-linked topic tab still wins. The
            // merge method has a single free low local at entry, so reserve two below its parameters.
            val originalRegisterCount =
                pinnedTabsLoaded.implementation?.registerCount
                    ?: throw PatchException("NewX home pinned-tab merge method has no implementation")
            val restoreMethod =
                pinnedTabsLoaded.cloneMutable(
                    additionalRegisters = pinnedTabsLoaded.numberOfParameterRegisters + 2,
                )
            component.methods.remove(pinnedTabsLoaded)
            component.methods.add(restoreMethod)
            val indexRegister = originalRegisterCount
            val navigationRegister = originalRegisterCount + 1
            val shiftedParameterStart = restoreMethod.p0Register
            if (navigationRegister > 15 || navigationRegister >= shiftedParameterStart) {
                throw PatchException(
                    "NewX home pinned-tab merge scratch registers v$indexRegister..v$navigationRegister " +
                        "are not free 4-bit locals below v$shiftedParameterStart in ${pinnedTabsLoaded}",
                )
            }
            restoreMethod.insertHook(index = 0, relocateBranchTargets = false) {
                invokeStatic(
                    methodReference(PINNED_TAB_TO_RESTORE_DESCRIPTOR),
                    restoreMethod.p0Register + 1,
                )
                moveResult(indexRegister, "I")
                ifLtz(indexRegister, Target.Original)
                iget(navigationRegister, restoreMethod.p0Register, select.navigationField)
                invokeStatic(select.select, navigationRegister, indexRegister)
            }

            // Insert after the route cast first, so the entry hook below does not shift its index.
            tabChange.insertHook(index = routeRead.castIndex + 1, relocateBranchTargets = false) {
                invokeStatic(methodReference(ON_TAB_SELECTED_DESCRIPTOR), routeRead.routeRegister)
            }
            // The two int parameters are untouched at entry: the requested and the current page.
            tabChange.insertHook(index = 0, relocateBranchTargets = false) {
                invokeStatic(
                    methodReference(ON_TAB_CHANGE_DESCRIPTOR),
                    tabChange.p0Register + 1,
                    tabChange.p0Register + 2,
                )
            }
        }
    }

private fun pinnedTabConfigFingerprint(
    tabConfigType: String,
    pinnedTimelineType: String,
) = Fingerprint(
    definingClass = tabConfigType.substringBeforeLast('/') + "/",
    name = "<init>",
    parameters = listOf(pinnedTimelineType),
    returnType = "V",
    custom = { _, classDef -> classDef.superclass == tabConfigType },
)

private fun Method.isInstanceMethod(expectedParameterTypes: List<String>): Boolean =
    !AccessFlags.STATIC.isSet(accessFlags) &&
        returnType == "V" &&
        parameterTypes.map(CharSequence::toString) == expectedParameterTypes

/** `iget-object nav, p0, Component->navigation; invoke-static {nav, index}, select(Nav, I)V`. */
private fun Method.tabSelectCalls(componentType: String): List<TabSelectCall> {
    val methodInstructions = implementation?.instructions?.toList() ?: return emptyList()
    return methodInstructions.indices.mapNotNull { index ->
        val invoke = methodInstructions[index]
        if (invoke.opcode != Opcode.INVOKE_STATIC) return@mapNotNull null
        val select = invoke.getReference<MethodReference>() ?: return@mapNotNull null
        val parameterTypes = select.parameterTypes.map(CharSequence::toString)
        if (select.returnType != "V" || parameterTypes.size != 2 || parameterTypes[1] != "I") {
            return@mapNotNull null
        }
        val navigationRead = methodInstructions.getOrNull(index - 1) ?: return@mapNotNull null
        if (navigationRead.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
        val navigationField = navigationRead.getReference<FieldReference>() ?: return@mapNotNull null
        if (navigationField.definingClass != componentType || navigationField.type != parameterTypes[0]) {
            return@mapNotNull null
        }
        val navigationRegister = (navigationRead as TwoRegisterInstruction).registerA
        if ((invoke as FiveRegisterInstruction).registerC != navigationRegister) return@mapNotNull null
        TabSelectCall(navigationField, select)
    }
}

/** `check-cast route, Route; iget-object config, route, Route->config:TabConfig`. */
private fun Method.tabRouteReads(): List<TabRouteRead> {
    val methodInstructions = implementation?.instructions?.toList() ?: return emptyList()
    return methodInstructions.indices.mapNotNull { index ->
        val cast = methodInstructions[index]
        if (cast.opcode != Opcode.CHECK_CAST) return@mapNotNull null
        val routeType = cast.getReference<TypeReference>()?.type ?: return@mapNotNull null
        if (!routeType.startsWith("Lcom/x/home/")) return@mapNotNull null
        val routeRegister = (cast as OneRegisterInstruction).registerA
        val configRead = methodInstructions.getOrNull(index + 1) ?: return@mapNotNull null
        if (configRead.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
        val configField = configRead.getReference<FieldReference>() ?: return@mapNotNull null
        if (configField.definingClass != routeType ||
            (configRead as TwoRegisterInstruction).registerB != routeRegister
        ) {
            return@mapNotNull null
        }
        TabRouteRead(index, routeRegister, configField)
    }
}

/**
 * Makes the home tab route implement the extension's `PinnedHomeTab`, returning the wrapped pinned
 * timeline's own tab id, or null for For You, Following and the other non-pinned tabs.
 */
context(context: BytecodePatchContext)
private fun installPinnedTabKeyBridge(
    routeType: String,
    tabConfigField: FieldReference,
    pinnedTabConfigType: String,
    pinnedTimelineField: FieldReference,
    pinnedTabId: Method,
) {
    val route = context.mutableClassDefBy(routeType)
    if (route.methods.any { method -> method.name == PINNED_HOME_TAB_KEY_NAME }) {
        throw PatchException("NewX home tab route already has $PINNED_HOME_TAB_KEY_NAME: $routeType")
    }
    route.interfaces.add(PINNED_HOME_TAB_DESCRIPTOR)

    // A typed hook needs an existing implementation, so the bridge starts as an empty
    // three-register body (v0/v1 locals, p0 the route) that the typed block below fills in.
    val bridge =
        MutableMethod(
            ImmutableMethod(
                routeType,
                PINNED_HOME_TAB_KEY_NAME,
                emptyList(),
                STRING_DESCRIPTOR,
                AccessFlags.PUBLIC.value,
                emptySet(),
                emptySet(),
                MutableMethodImplementation(3),
            ),
        )
    route.methods.add(bridge)

    bridge.insertHook(0, relocateBranchTargets = false) {
        val value = 0
        val isPinned = 1
        iget(value, bridge.p0Register, tabConfigField)
        instanceOf(isPinned, value, pinnedTabConfigType)
        ifEqz(isPinned, Target.Local(NOT_PINNED_LABEL))
        checkCast(value, pinnedTabConfigType)
        iget(value, value, pinnedTimelineField)
        invokeVirtual(
            methodReference("${pinnedTabId.definingClass}->${pinnedTabId.name}()${pinnedTabId.returnType}"),
            value,
        )
        moveResult(value, STRING_DESCRIPTOR)
        returnObject(value)
        label(NOT_PINNED_LABEL)
        constInt(value, 0)
        returnObject(value)
    }
}
