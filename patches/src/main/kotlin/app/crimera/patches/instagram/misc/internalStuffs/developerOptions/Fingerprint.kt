/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.internalStuffs.developerOptions

import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchFirst
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object FragmentNavigatorTransitionFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Integer;"),
    strings = listOf("FragmentNavigator.transitionInternal", "FragmentNavigator", "Fragment is null when attempting transition"),
)

internal object FragmentNavigatorConstructorFingerprint : Fingerprint(
    classFingerprint = FragmentNavigatorTransitionFingerprint,
    name = "<init>",
    parameters = listOf("Landroidx/fragment/app/FragmentActivity;", "L"),
)

internal object FragmentNavigatorSetFragmentFingerprint : Fingerprint(
    classFingerprint = FragmentNavigatorTransitionFingerprint,
    returnType = "V",
    parameters = listOf("Landroidx/fragment/app/Fragment;"),
    filters = listOf(
        methodCall(definingClass = "this", parameters = listOf("Landroid/os/Bundle;", "Landroidx/fragment/app/Fragment;")),
    ),
)

/**
 * Starts the transition with a transition type.
 */
internal object FragmentNavigatorCommitFingerprint : Fingerprint(
    classFingerprint = FragmentNavigatorTransitionFingerprint,
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        // Transition type.
        fieldAccess(opcode = Opcode.SGET_OBJECT),
        methodCall(definingClass = "this", parameters = listOf("Ljava/lang/Integer;"), location = MatchAfterImmediately()),
        opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately()),
    ),
)

internal object HomeIconOnClickListenerFingerprint : Fingerprint(
    name = "onLongClick",
    strings = listOf("click", "activity"),
    filters = listOf(
        fieldAccess(type = "Lcom/instagram/mainactivity/InstagramMainActivity;", opcode = Opcode.IGET_OBJECT, location = MatchFirst()),
        fieldAccess(type = USER_SESSION_CLASS, opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        methodCall(opcode = Opcode.INVOKE_STATIC, location = MatchAfterImmediately()),
    ),
)

internal object DeveloperOptionCallWithCallableFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.STATIC, AccessFlags.FINAL, AccessFlags.PUBLIC),
    strings = listOf("debug_options_error"),
)

internal object DeveloperOptionsOpenFingerprint : Fingerprint(
    classFingerprint = DeveloperOptionCallWithCallableFingerprint,
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Landroidx/fragment/app/FragmentActivity;", USER_SESSION_CLASS),
)
