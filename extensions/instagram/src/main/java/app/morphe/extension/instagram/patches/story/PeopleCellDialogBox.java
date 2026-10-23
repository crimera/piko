/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.story;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.instagram.igds.components.peoplecell.IgdsPeopleCell;

import java.util.ArrayList;

import app.morphe.extension.instagram.entity.InstagramDialogBox;

public class PeopleCellDialogBox {
    public static void showPeopleDialog(@NonNull Context context,
                                       @Nullable ArrayList<IgdsPeopleCell> peopleCells) {
        InstagramDialogBox builder = new InstagramDialogBox(context);
        builder.setTitle(str("piko_vsm_title"));
        builder.setNegativeButton(str("piko_close"), (dialog, which) -> dialog.dismiss());
        builder.setCancelable(true);
        builder.setCanceledOnTouchOutside(true);

        boolean hasMentions = peopleCells != null && !peopleCells.isEmpty();
        if (hasMentions) {
            CharSequence[] names = new CharSequence[peopleCells.size()];
            for (int index = 0; index < peopleCells.size(); index++) {
                IgdsPeopleCell cell = peopleCells.get(index);
                ViewGroup parent = (ViewGroup) cell.getParent();
                if (parent != null) parent.removeView(cell);
                names[index] = cell.getPrimaryTextView().getText();
            }
            builder.addDialogMenuItems(names, (dialog, which) -> peopleCells.get(which).performClick());
        } else {
            builder.setMessage(str("piko_vsm_no_mentions"));
        }

        Dialog dialog = builder.getDialog();
        if (hasMentions) {
            ListView list = dialog.findViewById(android.R.id.list);
            if (list == null) {
                throw new IllegalStateException("Instagram dialog list is unavailable");
            }
            list.setAdapter(new BaseAdapter() {
                @Override
                public int getCount() {
                    return peopleCells.size();
                }

                @Override
                public IgdsPeopleCell getItem(int position) {
                    return peopleCells.get(position);
                }

                @Override
                public long getItemId(int position) {
                    return position;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    return getItem(position);
                }
            });
            list.setOnItemClickListener((parent, view, position, id) -> peopleCells.get(position).performClick());
        }

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.CENTER);
        }
    }
}
