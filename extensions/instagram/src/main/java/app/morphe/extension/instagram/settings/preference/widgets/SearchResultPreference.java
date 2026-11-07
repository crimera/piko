/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import android.content.Context;
import android.preference.Preference;
import android.view.View;
import android.view.ViewGroup;

import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.settings.SettingsSearchIndex;

public final class SearchResultPreference extends Preference {
    private final SettingsSearchIndex.Result result;

    public SearchResultPreference(
            Context context,
            SettingsSearchIndex.Result result
    ) {
        super(context);
        this.result = result;
        setTitle(result.title);
        setSummary(result.summary);
        setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ActivityHook.startPikoActivity(
                        SearchResultPreference.this.result.fragmentName,
                        SearchResultPreference.this.result.parentTitle,
                        SearchResultPreference.this.result.preferenceKey
                );
                return true;
            }
        });
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = InstagramPreferenceStyle.createSearchResultView(getContext());
        InstagramPreferenceStyle.setPressedHighlightEnabled(view, true);
        return view;
    }

    @Override
    protected void onBindView(View view) {
        InstagramPreferenceStyle.bindSearchResult(
                this,
                view,
                result.parentTitle,
                result.categoryIconResourceName
        );
    }
}
