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

import android.widget.FrameLayout;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.entity.InstagramButtonStyleEnum;

import com.instagram.igds.components.button.IgdsButton;

public class InstagramButton extends FrameLayout {
    private IgdsButton igdsButton;

    public InstagramButton(Context context) {
        super(context);
        this.igdsButton = new IgdsButton(context);
    }

    public IgdsButton getIgdsButton(){
        return this.igdsButton;
    }

    public void setText(String text) {
        this.igdsButton.setText(text);
    }

    public void setOnClickListener(Runnable action) {
        this.igdsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    action.run();
                } catch (Exception ex) {
                    Logger.printException(() -> "Button click failed: ", ex);
                }
            }
        });
    }

    public void setStyleObject(Object style){
        // The function call for adding the button style to the button will be injected here from patches.
    }

    public void setStyle(InstagramButtonStyleEnum style){
        try {
            String styleName = style.name;
            Entity entity = new Entity();
            Class<?> styleClass = Class.forName("X.0X3");
            Object buttonStyle = entity.getMethod(
                    styleClass,
                    "valueOf",
                    styleName
            );
            setStyleObject(buttonStyle);

        } catch (Exception ex) {
            Logger.printException(() -> "Button setStyle failed: ", ex);
        }
    }

    public void setMargins(int left, int top, int right, int bottom){
        MarginLayoutParams params = new MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(left, top, right, bottom);
        this.igdsButton.setLayoutParams(params);
    }
}