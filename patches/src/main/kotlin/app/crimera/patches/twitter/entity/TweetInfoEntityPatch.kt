package app.crimera.patches.twitter.entity

import app.crimera.utils.changeFirstString
import app.crimera.utils.getReference
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

@Suppress("unused")
val tweetInfoEntityPatch =
    bytecodePatch(
        description = "For tweet info entity reflection",
    ) {
        compatibleWith("com.twitter.android")
        execute {
            tweetInfoObjectFingerprint.stringMatches?.forEach { match ->
                val str = match.string

                if (str == "lang") {
                    val ref = tweetInfoObjectFingerprint.method.getReference(match.index + 1) as FieldReference
                    tweetLangFingerprint.method.changeFirstString(ref.name)

                    var infoField =
                        tweetObjectFingerprint.classDef.fields
                            .first {
                                it.type ==
                                    ref.definingClass
                            }.name
                    tweetInfoFingerprint.method.changeFirstString(infoField)
                    return@forEach
                }
            }
            // ------------
        }
    }
