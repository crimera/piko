package app.revanced.extension.twitter.settings.featureflags;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.twitter.patches.FeatureSwitchPatch;
import app.revanced.extension.twitter.settings.ActivityHook;
import app.revanced.extension.twitter.settings.Settings;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

@SuppressWarnings("deprecation")
public class FeatureFlagsFragment extends Fragment {
    ArrayList<FeatureFlag> flags;
    private final String bundleFlagNameKey = "flagName";
    private final String bundleFlagValueKey = "flagValue";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        flags = new ArrayList<>();
        String flagsPref = app.revanced.extension.twitter.Utils.getStringPref(Settings.MISC_FEATURE_FLAGS);
        if (!flagsPref.isEmpty()) {
            for (String flag : flagsPref.split(",")) {
                String[] item = flag.split(":");
                flags.add(new FeatureFlag(item[0], Boolean.valueOf(item[1])));
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ActivityHook.toolbar.setTitle(Utils.getResourceString("piko_pref_feature_flags"));
    }

    private void saveFlags() {
        app.revanced.extension.twitter.Utils.setStringPref(Settings.MISC_FEATURE_FLAGS.key, FeatureFlag.toStringPref(flags));
    }

    public void modifyFlag(FeatureFlagAdapter adapter, int position) {
        try {
            FeatureFlag flag = flags.get(position);

            AlertDialog.Builder dia = new AlertDialog.Builder(getContext());
            dia.setTitle(Utils.getResourceString("piko_pref_edit_flag_title"));

            LinearLayout ln = new LinearLayout(getContext());
            ln.setPadding(50, 50, 50, 50);

            EditText flagEditText = new EditText(getContext());
            flagEditText.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            flagEditText.setText(flag.getName());
            ln.addView(flagEditText);

            dia.setPositiveButton(Utils.getResourceString("save"), (dialogInterface, i) -> {
                String editTextValue = flagEditText.getText().toString();
                if (!editTextValue.equals(flag.getName())) {
                    flags.set(position, new FeatureFlag(flagEditText.getText().toString(), flag.getEnabled()));
                    adapter.notifyDataSetChanged();
                    saveFlags();
                }
            });

            dia.setNeutralButton(Utils.getResourceString("remove"), ((dialogInterface, i) -> {
                flags.remove(position);
                adapter.notifyDataSetChanged();
                saveFlags();
            }));

            dia.setNegativeButton(Utils.getResourceString("cancel"), null);

            dia.setView(ln);

            dia.create().show();
        } catch (Exception exception){
            app.revanced.extension.twitter.Utils.toast(exception.toString());
        }
    }

    private void searchFlagsDialog(FeatureFlagAdapter parentAdapter) {
        AlertDialog.Builder dia = new AlertDialog.Builder(getContext());
        dia.setTitle(Utils.getResourceString("piko_pref_add_flag_title"));

        @SuppressLint({"NewApi", "LocalSuppress"}) View view = getLayoutInflater().inflate(Utils.getResourceIdentifier("search_dialog", "layout"), null);
        ListView listView = view.findViewById(Utils.getResourceIdentifier("featureFlagsSearchListView", "id"));
        EditText filter = view.findViewById(Utils.getResourceIdentifier("filterEditText", "id"));

        String[] searchFlags = FeatureSwitchPatch.FLAGS_SEARCH.split(",");
        FeatureFlagSearchAdapter adapter = new FeatureFlagSearchAdapter(getContext(), searchFlags);
        listView.setAdapter(adapter);
        adapter.notifyDataSetChanged();

        filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                adapter.filter(charSequence.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        dia.setView(view);
        AlertDialog dialog = dia.create();

        listView.setOnItemClickListener((adapterView, view1, i, l) -> {
            flags.add(new FeatureFlag(adapter.getItem(i), true));
            parentAdapter.notifyDataSetChanged();
            saveFlags();
            dialog.dismiss();
        });

        dialog.show();
    }

    public void addFlag(FeatureFlagAdapter adapter, Bundle bundle) {
        boolean defaultFlagValue = true;
        AlertDialog.Builder dia = new AlertDialog.Builder(getContext());
        dia.setTitle(Utils.getResourceString("piko_pref_add_flag_title"));

        LinearLayout ln = new LinearLayout(getContext());
        ln.setPadding(50, 50, 50, 50);

        EditText flagEditText = new EditText(getContext());
        // If flag is sent via deepLink.
        if (bundle.containsKey(bundleFlagNameKey)) { // If flag is sent.
            flagEditText.setText(bundle.getString(bundleFlagNameKey));
            defaultFlagValue =  bundle.getBoolean(bundleFlagValueKey, defaultFlagValue);
        }
        // TODO: add string to resources
        flagEditText.setHint("flag");
        flagEditText.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ln.addView(flagEditText);
        final boolean flagValue = defaultFlagValue;
        dia.setPositiveButton(Utils.getResourceString("save"), (dialogInterface, i) -> {
            String editTextValue = flagEditText.getText().toString();
            flags.add(new FeatureFlag(editTextValue, flagValue));
            adapter.notifyDataSetChanged();
            saveFlags();
        });

        // TODO: add string to resources
        dia.setNeutralButton(Utils.getResourceString("piko_pref_search_flags"), ((dialogInterface, i) -> {
            searchFlagsDialog(adapter);
        }));

        dia.setNegativeButton(Utils.getResourceString("cancel"), null);

        dia.setView(ln);

        dia.create().show();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        Bundle bundle = getArguments(); // Required for deepLink flag addition.
        View view = inflater.inflate(Utils.getResourceIdentifier("feature_flags_view", "layout"), container, false);
        FloatingActionButton floatingActionButton = view.findViewById(Utils.getResourceIdentifier("add_flag", "id"));

        ListView rc = view.findViewById(Utils.getResourceIdentifier("list", "id"));
        rc.setClipToPadding(false);
        rc.setPadding(0, 0, 0, 200);

        FeatureFlagAdapter adapter = new FeatureFlagAdapter(getContext(), flags);

        rc.setOnItemClickListener((adapterView, view1, i, l) -> {
            app.revanced.extension.twitter.Utils.toast(adapter.getItem(i).getName());
        });

        floatingActionButton.setOnClickListener(view1 -> addFlag(adapter, bundle));

        adapter.setItemClickListener(position -> modifyFlag(adapter, position));

        adapter.setItemCheckedChangeListener((checked, position) -> {
            FeatureFlag flag = flags.get(position);
            flags.set(position, new FeatureFlag(flag.getName(), checked));
            saveFlags();
        });

        rc.setAdapter(adapter);

        // If flag is sent via deepLink.
        if (bundle.containsKey(bundleFlagNameKey)) { // If flag is sent.
            addFlag(adapter, bundle);
            bundle.remove(bundleFlagNameKey);
            bundle.remove(bundleFlagValueKey);
        }

        return view;
    }
}