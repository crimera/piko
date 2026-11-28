/*
 * Forked from:
 * https://github.com/andrewliang25/morphe-patches/blob/5db2e57e133aede5297c48b419168cf30fd89953/patches/src/main/kotlin/app/andrewliang/patches/facebook/resignedtrust/RestoreTrustPatch.kt
 * Copyright 2026 Andrew Liang (GPL-3.0).
 *
 * Modified for Hushfacebook (Facebook), 2026, and for HushGram (Instagram), 2026.
 * Adapted for Piko (Instagram self-package compatibility), 2026.
 */

package app.crimera.patches.instagram.misc.originalsigners

import app.crimera.patches.common.parameterRegisterStart
import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags

private const val PACKAGE_INFO = "Landroid/content/pm/PackageInfo;"
private const val ORIGINAL_SIGNERS =
    "Lapp/morphe/extension/instagram/utils/InstagramSignature;->originalSigners($PACKAGE_INFO)Ljava/util/List;"

private object PackageSignersFingerprint : Fingerprint(
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/content/pm/SigningInfo;",
            name = "getApkContentsSigners",
        ),
        methodCall(
            definingClass = "Landroid/content/pm/SigningInfo;",
            name = "getSigningCertificateHistory",
        ),
        fieldAccess(
            definingClass = PACKAGE_INFO,
            name = "signatures",
        ),
    ),
)

internal val originalSignersPatch = patch {
    dependsOn(sharedExtensionPatch)
    execute {
        val method = PackageSignersFingerprint.method
        val owner = classDefBy(method.definingClass)
        val packageInfo = owner.fields.singleOrNull {
            it.type == PACKAGE_INFO && !AccessFlags.STATIC.isSet(it.accessFlags)
        } ?: throw PatchException("Expected one instance PackageInfo field in the signer reader")

        val constructor = classDefBy(method.returnType).methods.singleOrNull {
            it.name == "<init>" && it.parameterTypes.map { type -> type.toString() } ==
                listOf("Ljava/util/List;", "Z", "Z")
        } ?: throw PatchException("Expected the signer result to have a (List, boolean, boolean) constructor")
        if (!AccessFlags.PUBLIC.isSet(constructor.accessFlags)) {
            throw PatchException("Expected the signer result constructor to be public")
        }

        method.answerOriginalSigners(packageInfo.name)
    }
}

internal fun MutableMethod.answerOriginalSigners(packageInfo: String) {
    if (AccessFlags.STATIC.isSet(accessFlags) || parameterTypes.isNotEmpty() ||
        parameterRegisterStart(this) < 3
    ) {
        throw PatchException("Expected an instance signer reader with no parameters and at least three locals")
    }
    // Copy this to a low register because iget-object cannot encode a receiver above v15.
    addInstructionsWithLabels(
        0,
        """
            move-object/from16 v0, p0
            iget-object v0, v0, $definingClass->$packageInfo:$PACKAGE_INFO
            invoke-static {v0}, $ORIGINAL_SIGNERS
            move-result-object v1
            if-eqz v1, :original
            new-instance v0, $returnType
            const/4 v2, 0x0
            invoke-direct {v0, v1, v2, v2}, $returnType-><init>(Ljava/util/List;ZZ)V
            return-object v0
        """.trimIndent(),
        ExternalLabel("original", getInstruction(0)),
    )
}
