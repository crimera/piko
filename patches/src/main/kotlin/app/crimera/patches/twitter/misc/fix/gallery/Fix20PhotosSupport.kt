/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.fix.gallery

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.patch

/* Obfuscated names are hard-coded for 12.19.1-release.0. */
private const val ADAPTER = "Lcom/twitter/app/gallery/p0;" // A RecyclerView adapter
private const val PHOTO = "Lcom/twitter/app/gallery/z0;"

@Suppress("unused")
val fix20PhotosSupportPatch = patch(
    name = "Fix 20 photos support",
    description = "Fix the issue where photos turn black in gallery for posts containing more than 4 images. " +
            "This patch does not increase the limit for posting.",
) {
    compatibleWith(COMPATIBILITY_X)
    category("Old patches")
    execute {
        Fingerprint(
            definingClass = ADAPTER,
            name = "onViewRecycled"
        ).method.addInstructionsWithLabels(
            0,
            """
            instance-of v0, p1, $PHOTO
            if-eqz v0, :original_non_photo
            move-object v0, p1
            check-cast v0, $PHOTO
            invoke-virtual {v0}, $PHOTO->A()V
            iget-object v0, v0, $PHOTO->g:Lio/reactivex/disposables/b;
            invoke-virtual {v0}, Lio/reactivex/disposables/b;->e()V
            iget-object v0, p0, $ADAPTER->g:Landroid/util/SparseArray;
            :remove_photo_mapping
            invoke-virtual {v0, p1}, Landroid/util/SparseArray;->indexOfValue(Ljava/lang/Object;)I
            move-result p0
            if-ltz p0, :photo_done
            invoke-virtual {v0, p0}, Landroid/util/SparseArray;->removeAt(I)V
            goto :remove_photo_mapping
            :photo_done
            return-void
            :original_non_photo
            nop
            """
        )
    }
}