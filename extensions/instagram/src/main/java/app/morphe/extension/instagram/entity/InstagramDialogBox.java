/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.entity;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class InstagramDialogBox{

    private final Object igdsDialog;
    private final Class<?> igdsClass;

    public InstagramDialogBox(Context context){
        try {
            igdsClass = Class.forName("className");
            Constructor<?> ctor = igdsClass.getConstructor(Context.class);
            igdsDialog = ctor.newInstance(context);
        } catch (Throwable t) {
            throw new RuntimeException("Contructor failed");
        }
    }

    public void addDialogMenuItems(
            CharSequence[] items,
            DialogInterface.OnClickListener listener
    ) {
        invoke(
                "A0T",
                new Class[]{DialogInterface.OnClickListener.class, CharSequence[].class},
                listener,
                items
        );
    }

    public Dialog getDialog() {
        return (Dialog) invoke("A02", null);
    }

    public void setCancelable(boolean value) {
        invoke("A0h", new Class[]{boolean.class}, value);
    }

    public void setCanceledOnTouchOutside(boolean value) {
        invoke("A0i", new Class[]{boolean.class}, value);
    }

    public void setMessage(CharSequence message) {
        invoke("A0g", new Class[]{CharSequence.class}, message);
    }

    public void setNegativeButton(
            String text,
            DialogInterface.OnClickListener listener
    ) {
        invoke(
                "A0R",
                new Class[]{DialogInterface.OnClickListener.class, String.class},
                listener,
                text
        );
    }

    public void setOnDismissListener(
            DialogInterface.OnDismissListener listener
    ) {
        invoke(
                "A0U",
                new Class[]{DialogInterface.OnDismissListener.class},
                listener
        );
    }

    public void setPositiveButton(
            String text,
            DialogInterface.OnClickListener listener
    ) {
        invoke(
                "A0S",
                new Class[]{DialogInterface.OnClickListener.class, String.class},
                listener,
                text
        );
    }

    public void setTitle(String title) {
        try {
            Field f = igdsClass.getDeclaredField("A04");
            f.setAccessible(true);
            f.set(igdsDialog, title);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to setTitle", t);
        }
    }

    // ---------- reflection helper ----------

    private Object invoke(String name, Class<?>[] argsTypes, Object... args) {
        try {
            Method method = argsTypes!=null ? igdsClass.getDeclaredMethod(name, argsTypes): igdsClass.getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(igdsDialog, args);

        } catch (Throwable t) {
            throw new RuntimeException("Invoke failed: " + name, t);
        }
    }
}