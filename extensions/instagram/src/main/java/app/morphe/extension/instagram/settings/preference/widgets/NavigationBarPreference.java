/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.Preference;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

public class NavigationBarPreference extends Preference {
    private final Context context;

    public NavigationBarPreference(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    protected void onClick() {
        Context dialogContext = InstagramPreferenceStyle.dialogContext(context);
        NavigationBarAdapter adapter = new NavigationBarAdapter(dialogContext);
        AlertDialog dialog = new AlertDialog.Builder(dialogContext)
                .setTitle(getTitle())
                .setAdapter(adapter, null)
                .setNeutralButton(str("piko_reset"), null)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            ListView listView = dialog.getListView();
            adapter.attachListView(listView);
            listView.setOnTouchListener(adapter::onListTouch);
            listView.setOnItemClickListener(
                    (parent, view, position, id) -> adapter.toggle(position)
            );
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(view -> adapter.reset());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (adapter.needsSettingsLockoutWarning()) {
                    NavigationSettingsAccessWarning.show(dialogContext, () -> {
                        if (!adapter.save()) return false;
                        dialog.dismiss();
                        return true;
                    });
                    return;
                }
                if (adapter.save()) dialog.dismiss();
            });
        });
        dialog.show();
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        return InstagramPreferenceStyle.createPreferenceView(
                context,
                InstagramPreferenceStyle.TRAILING_CHEVRON
        );
    }

    @Override
    protected void onBindView(View view) {
        InstagramPreferenceStyle.bindText(this, view);
    }
}
