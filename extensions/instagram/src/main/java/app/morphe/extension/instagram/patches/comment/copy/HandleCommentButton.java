/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.comment.copy;

import java.util.List;

import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.crimera.ObjectBrowser;

// Thanks to MyInsta.
@SuppressWarnings("unused")
public class HandleCommentButton {

    public static void addCopyButton(List list) {
        if (Pref.commentCopyButton()) {
            list.add(CopyButton.A00);
        }
    }

    public static boolean checkOnCommentButtonClick(Object button, List list) {
        try {
            if (button.equals(CopyButton.A00)) {
                if (list.isEmpty()) return false;

                Object commentObject = list.get(0);
                Entity commentEntity = new Entity(commentObject);
                String commentText = (String) commentEntity.getField("A0N");
                if (commentText!=null && commentText.length() > 0) {
                    app.morphe.extension.shared.Utils.setClipboard(commentText);
                    PikoUtils.toast(Strings.COMMENT_COPIED_SUCCESS);
                } else {
                    PikoUtils.toast(Strings.COMMENT_COPIED_FAILED);
                }
                return true;
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        return false;
    }

}
