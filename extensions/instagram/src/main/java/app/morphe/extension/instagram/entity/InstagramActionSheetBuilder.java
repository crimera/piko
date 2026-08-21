/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.entity;

import android.view.View;

import java.lang.reflect.Method;

public final class InstagramActionSheetBuilder extends Entity {
    public InstagramActionSheetBuilder(Object obj) {
        super(obj);
    }

    public void addNormalAction(String title, View.OnClickListener listener) throws Exception {
        Method method = getObjClass().getDeclaredMethod(
                "normalActionMethod",
                String.class,
                View.OnClickListener.class
        );
        method.setAccessible(true);
        method.invoke(getObject(), title, listener);
    }
}
