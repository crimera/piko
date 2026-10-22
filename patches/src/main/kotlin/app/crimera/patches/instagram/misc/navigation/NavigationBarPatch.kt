/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.navigation

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.methodReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val LIST = "Ljava/util/List;"
private const val OBJECT = "Ljava/lang/Object;"
private const val STRING = "Ljava/lang/String;"
private const val EXTENSION = "$PATCHES_DESCRIPTOR/navigation/NavigationBarPatch;"
private const val INTENT = "Landroid/content/Intent;"
private const val STARTUP_TAB_EXTRA = "MainActivityAccountHelper.STARTUP_TAB"
private const val FRAGMENT_NAME_BRIDGE = "navigationFragmentName"

private object NavigationBarFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(USER_SESSION_CLASS, "Z"),
    returnType = LIST,
    filters = listOf(
        opcode(Opcode.IF_EQZ),
        // All navigation candidates.
        methodCall(
            definingClass = "this",
            parameters = listOf(USER_SESSION_CLASS),
            returnType = LIST,
            opcode = Opcode.INVOKE_STATIC,
            location = MatchAfterImmediately(),
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        opcode(Opcode.RETURN_OBJECT, MatchAfterImmediately()),
    ),
)

private object NavigationEnumFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.CONSTRUCTOR),
    strings = listOf("SEARCH", "fragment_search"),
    filters = listOf(
        string("FEED"),
        string("fragment_feed", MatchAfterWithin(2)),
        methodCall(definingClass = "this", name = "<init>", location = MatchAfterWithin(2)),
    ),
)

private object StartupFingerprint : Fingerprint(
    strings = listOf(STARTUP_TAB_EXTRA),
    parameters = listOf(INTENT, USER_SESSION_CLASS, "Z"),
    returnType = "V",
)

private object InitialTabPositionFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    parameters = listOf("L", "L", STRING, "Z", "Z"),
    returnType = "V",
    strings = listOf("feed_viewpager_empty_tabs"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/facebook/mobileconfig/factory/MobileConfigUnsafeContext;",
            parameters = listOf("J"),
            returnType = "Z",
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        opcode(Opcode.IGET_OBJECT, MatchAfterImmediately()),
        methodCall(definingClass = LIST, name = "indexOf", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.IF_LEZ, MatchAfterImmediately()),
        methodCall(
            definingClass = "Landroidx/viewpager2/widget/ViewPager2;",
            parameters = listOf("I", "Z"),
            returnType = "V",
            location = MatchAfterImmediately(),
        ),
    ),
)

context(patchContext: BytecodePatchContext)
private fun installInitialTabPosition() {
    InitialTabPositionFingerprint.let {
        // FEED -> FEED skips normal tab changes, so a reordered pager must be positioned during initialization.
        it.method.replaceInstruction(it.instructionMatches[2].index, "nop")
    }
}

/**
 * Fragment name field of the navigation enum, which the constructor stores from the fragment literal parameter.
 */
context(patchContext: BytecodePatchContext)
private fun navigationFragmentField(enumMatch: Match): FieldReference {
    val literalRegister = enumMatch.instructionMatches[1].getInstruction<OneRegisterInstruction>().registerA
    val constructorCall = enumMatch.instructionMatches[2]
    val constructorReference = constructorCall.methodReference
    val constructor = Fingerprint(
        definingClass = constructorReference.definingClass,
        name = constructorReference.name,
        parameters = constructorReference.parameterTypes.map(CharSequence::toString),
    ).originalMethod
    val parameterRegister = constructor.p0Register + constructorCall.instruction.registersUsed.indexOf(literalRegister)

    return constructor.getInstruction(
        constructor.indexOfFirstInstructionOrThrow {
            opcode == Opcode.IPUT_OBJECT && (this as TwoRegisterInstruction).registerA == parameterRegister
        },
    ).getReference<FieldReference>()!!
}

private fun requireLocalRegisters(method: MutableMethod, registers: List<Int>) {
    if (registers.distinct().size != registers.size ||
        registers.any { it !in 0 until method.p0Register || it > 0xf }
    ) {
        throw PatchException("Navigation bridge has no safe local registers: $registers")
    }
}

private fun createFragmentNameBridge(fragmentField: FieldReference): MutableMethod {
    val implementation =
        MethodImplementationBuilder(1).apply {
            addInstruction("check-cast v0, ${fragmentField.definingClass}".toInstruction())
            addInstruction("iget-object v0, v0, $fragmentField".toInstruction())
            addInstruction("return-object v0".toInstruction())
        }.methodImplementation
    return MutableMethod(
        ImmutableMethod(
            EXTENSION,
            FRAGMENT_NAME_BRIDGE,
            listOf(ImmutableMethodParameter(OBJECT, emptySet(), null)),
            STRING,
            AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            implementation,
        ),
    )
}

context(patchContext: BytecodePatchContext)
private fun installFragmentNameBridge(fragmentField: FieldReference) {
    val extensionClass = patchContext.mutableClassDefBy(EXTENSION)
    val bridgeCandidates =
        extensionClass.methods.filter { method ->
            method.name == FRAGMENT_NAME_BRIDGE &&
                method.parameterTypes.map(CharSequence::toString) == listOf(OBJECT) &&
                method.returnType == STRING
        }
    if (bridgeCandidates.size != 1) {
        throw PatchException(
            "Expected one navigation fragment-name bridge, found ${bridgeCandidates.size}",
        )
    }
    val bridgeStub = bridgeCandidates.single()
    if (!AccessFlags.PRIVATE.isSet(bridgeStub.accessFlags) ||
        !AccessFlags.STATIC.isSet(bridgeStub.accessFlags) ||
        !AccessFlags.NATIVE.isSet(bridgeStub.accessFlags)
    ) {
        throw PatchException("Navigation fragment-name bridge has an invalid signature")
    }

    extensionClass.methods.remove(bridgeStub)
    extensionClass.methods.add(createFragmentNameBridge(fragmentField))
}

private fun installTransform(navigationMatch: Match) {
    val method = navigationMatch.method
    val allCandidates = navigationMatch.instructionMatches[1].methodReference
    val returnMatch = navigationMatch.instructionMatches.last()
    val tabsRegister = returnMatch.getInstruction<OneRegisterInstruction>().registerA
    val candidatesRegister = method.findFreeRegister(returnMatch.index, tabsRegister)
    requireLocalRegisters(method, listOf(candidatesRegister))
    val sessionRegister = method.p0Register + 1
    if (tabsRegister !in 0..0xf || sessionRegister !in 0..0xf) {
        throw PatchException("Navigation transform requires 4-bit registers")
    }

    method.addInstructionsAtControlFlowLabel(
        returnMatch.index,
        """
            invoke-static {v$sessionRegister}, $allCandidates
            move-result-object v$candidatesRegister
            invoke-static {v$tabsRegister, v$candidatesRegister}, $EXTENSION->transformNavigationTabs(Ljava/util/List;Ljava/util/List;)Ljava/util/List;
            move-result-object v$tabsRegister
        """.trimIndent(),
    )
}

private fun installStartup(
    method: MutableMethod,
    allCandidates: MethodReference,
) {
    val firstParameter =
        method.p0Register + if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1
    val intentRegister = firstParameter
    val sessionRegister = firstParameter + 1
    val coldRegister = firstParameter + 2
    val candidatesRegister =
        method.findFreeRegister(0, listOf(intentRegister, sessionRegister, coldRegister))
    requireLocalRegisters(method, listOf(candidatesRegister))
    if (listOf(intentRegister, sessionRegister, coldRegister).any { it !in 0..0xf }) {
        throw PatchException("Navigation startup requires 4-bit parameter registers")
    }

    val implementation =
        method.implementation ?: throw PatchException("Navigation startup has no implementation")
    val nativeStart = method.getInstruction(0)
    val resumeNative = nativeStart.location.addNewLabel()
    val candidateStart = "invoke-static {v$sessionRegister}, $allCandidates".toInstruction(method)
    val candidateEnd =
        "invoke-static {v$intentRegister, v$coldRegister, v$candidatesRegister}, $EXTENSION->applyStartupTab(Landroid/content/Intent;ZLjava/util/List;)V"
            .toInstruction(method)
    val candidateFailure = "move-exception v$candidatesRegister".toInstruction(method)
    method.addInstructions(
        0,
        listOf(
            "invoke-static {v$intentRegister, v$coldRegister}, $EXTENSION->preflightStartup(Landroid/content/Intent;Z)Z"
                .toInstruction(method),
            "move-result v$candidatesRegister".toInstruction(method),
            BuilderInstruction21t(Opcode.IF_EQZ, candidatesRegister, resumeNative),
            candidateStart,
            "move-result-object v$candidatesRegister".toInstruction(method),
            candidateEnd,
            BuilderInstruction10t(Opcode.GOTO, resumeNative),
            candidateFailure,
            BuilderInstruction10t(Opcode.GOTO, resumeNative),
        ),
    )
    implementation.addCatch(
        candidateStart.location.addNewLabel(),
        candidateEnd.location.addNewLabel(),
        candidateFailure.location.addNewLabel(),
    )
}

@Suppress("unused")
val navigationBarPatch =
    bytecodePatch(
        name = "Customize navigation bar",
        description = "Choose which tabs appear in the bottom navigation bar and reorder them",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)

        execute {
            val navigationMatch = NavigationBarFingerprint.matchSingle()
            val allCandidates = navigationMatch.instructionMatches[1].methodReference
            // The startup hook calls the candidates provider from another class.
            Fingerprint(
                definingClass = allCandidates.definingClass,
                name = allCandidates.name,
                accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
                parameters = listOf(USER_SESSION_CLASS),
                returnType = LIST,
            ).match()
            val fragmentField = navigationFragmentField(NavigationEnumFingerprint.matchSingle())

            installFragmentNameBridge(fragmentField)
            installStartup(StartupFingerprint.matchSingle().method, allCandidates)
            installTransform(navigationMatch)
            installInitialTabPosition()
            enableSettings("hideNavigationButtons")
        }
    }
