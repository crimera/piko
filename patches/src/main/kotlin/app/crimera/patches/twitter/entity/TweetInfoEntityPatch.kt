/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.entity

import app.crimera.utils.changeFirstString
import app.crimera.utils.getReference
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

val tweetInfoEntityPatch =
    bytecodePatch(
        description = "For tweet info entity reflection",
    ) {
        execute {
            TweetInfoObjectFingerprint.stringMatches?.forEach { match ->
                val str = match.string

                if (str == "lang") {
                    val ref = TweetInfoObjectFingerprint.getReference(match.index + 1) as FieldReference
                    TweetLangFingerprint.changeFirstString(ref.name)

                    var infoField =
                        TweetObjectFingerprint.classDef.fields
                            .first {
                                it.type ==
                                    ref.definingClass
                            }.name
                    TweetInfoFingerprint.changeFirstString(infoField)
                    return@forEach
                }
            }
            // ------------
        }
    }
