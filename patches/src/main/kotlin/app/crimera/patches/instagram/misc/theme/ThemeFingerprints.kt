/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal object LegacyDarkModeFragmentConstructorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("theme_settings"),
)

internal object LegacyDarkModeOnResumeFingerprint : Fingerprint(
    classFingerprint = LegacyDarkModeFragmentConstructorFingerprint,
    name = "onResume",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        // Selected RadioItem id.
        fieldAccess(type = STRING_DESCRIPTOR, opcode = Opcode.IGET_OBJECT),
        // Delegated RadioGroup row factory.
        methodCall(
            parameters = listOf(
                RADIO_GROUP_LISTENER_DESCRIPTOR,
                STRING_DESCRIPTOR,
                "Ljava/util/AbstractCollection;",
                LIST_DESCRIPTOR,
            ),
            returnType = "V",
            opcode = Opcode.INVOKE_STATIC,
            location = MatchAfterWithin(3),
        ),
        // Adapter binding.
        methodCall(
            parameters = listOf(COLLECTION_DESCRIPTOR),
            returnType = "V",
            location = MatchAfterImmediately(),
        ),
    ),
)

internal object InlineLegacyDarkModeOnResumeFingerprint : Fingerprint(
    classFingerprint = LegacyDarkModeFragmentConstructorFingerprint,
    name = "onResume",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(type = STRING_DESCRIPTOR, opcode = Opcode.IGET_OBJECT),
        methodCall(
            name = "<init>",
            parameters = listOf(RADIO_GROUP_LISTENER_DESCRIPTOR, STRING_DESCRIPTOR, LIST_DESCRIPTOR),
            opcode = Opcode.INVOKE_DIRECT,
            location = MatchAfterWithin(5),
        ),
        methodCall(
            definingClass = "Ljava/util/AbstractCollection;",
            name = "add",
            location = MatchAfterImmediately(),
        ),
        methodCall(
            parameters = listOf(COLLECTION_DESCRIPTOR),
            returnType = "V",
            location = MatchAfterImmediately(),
        ),
    ),
)

internal fun legacyRadioRowFactoryFingerprint(factory: MethodReference) = Fingerprint(
    definingClass = factory.definingClass,
    name = factory.name,
    returnType = "V",
    parameters = factory.parameterTypes.map(CharSequence::toString),
    filters = listOf(
        methodCall(
            name = "<init>",
            parameters = listOf(RADIO_GROUP_LISTENER_DESCRIPTOR, STRING_DESCRIPTOR, LIST_DESCRIPTOR),
            opcode = Opcode.INVOKE_DIRECT,
        ),
        methodCall(
            definingClass = "Ljava/util/AbstractCollection;",
            name = "add",
            location = MatchAfterImmediately(),
        ),
    ),
)

/**
 * The RadioItem class initializer, which creates the Dark, Light and System-default items with
 * their id string and native theme mode.
 */
internal fun legacyRadioItemsFingerprint(itemType: String) = Fingerprint(
    definingClass = itemType,
    name = "<clinit>",
    filters = listOf(
        opcode(Opcode.CONST_STRING),
        literal(2, location = MatchAfterImmediately()),
        opcode(Opcode.CONST_STRING),
        literal(1, location = MatchAfterImmediately()),
        opcode(Opcode.CONST_STRING),
        literal(-1, location = MatchAfterImmediately()),
    ),
)

internal object CurrentSystemUiModeFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(
        string("ig_device_theme"),
        string("KEY_CONFIG_CURRENT_SYSTEM_UI_MODE"),
        opcode(Opcode.RETURN),
    ),
)

internal object DarkModeSectionFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", FUNCTION1_DESCRIPTOR, "I"),
    strings = listOf("dark", "light", "system"),
    filters = listOf(
        string(
            "com.instagram.settings.impl.accessibility.DarkModeSection (AccessibilityOptionsComposeFragment.kt:",
            StringComparisonType.STARTS_WITH,
        ),
        // Native theme setting read.
        literal(-1),
        methodCall(
            name = "getInt",
            parameters = listOf(STRING_DESCRIPTOR, "I"),
            returnType = "I",
            location = MatchAfterImmediately(),
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
    ),
)
