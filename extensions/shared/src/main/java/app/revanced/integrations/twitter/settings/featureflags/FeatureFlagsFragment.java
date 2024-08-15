package app.revanced.integrations.twitter.settings.featureflags;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.revanced.integrations.shared.Utils;
import app.revanced.integrations.twitter.patches.FeatureSwitchPatch;
import app.revanced.integrations.twitter.settings.ActivityHook;
import app.revanced.integrations.twitter.settings.Settings;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

@SuppressWarnings("deprecation")
public class FeatureFlagsFragment extends Fragment {
    ArrayList<FeatureFlag> flags;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        flags = new ArrayList<>();
        String flagsPref = app.revanced.integrations.twitter.Utils.getStringPref(Settings.MISC_FEATURE_FLAGS);
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
        app.revanced.integrations.twitter.Utils.setStringPref(Settings.MISC_FEATURE_FLAGS.key, FeatureFlag.toStringPref(flags));
    }

    public void modifyFlag(CustomAdapter adapter, int position) {
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
                adapter.notifyItemChanged(position);
                saveFlags();
            }
        });

        dia.setNeutralButton(Utils.getResourceString("remove"), ((dialogInterface, i) -> {
            flags.remove(position);
            adapter.c.f(position, 1);
            saveFlags();
        }));

        dia.setNegativeButton(Utils.getResourceString("cancel"), null);

        dia.setView(ln);

        dia.create().show();
    }

    private void searchFlagsDialog(CustomAdapter parentAdapter) {
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
            parentAdapter.notifyItemChanged(flags.size());
            dialog.dismiss();
        });

        dialog.show();
    }

    public void addFlag(CustomAdapter adapter) {
        AlertDialog.Builder dia = new AlertDialog.Builder(getContext());
        dia.setTitle(Utils.getResourceString("piko_pref_add_flag_title"));

        LinearLayout ln = new LinearLayout(getContext());
        ln.setPadding(50, 50, 50, 50);

        EditText flagEditText = new EditText(getContext());
        // TODO: add string to resources
        flagEditText.setHint("flag");
        flagEditText.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ln.addView(flagEditText);

        dia.setPositiveButton(Utils.getResourceString("save"), (dialogInterface, i) -> {
            String editTextValue = flagEditText.getText().toString();
            flags.add(new FeatureFlag(editTextValue, true));
            adapter.notifyItemChanged(flags.size());
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
        View view = inflater.inflate(Utils.getResourceIdentifier("feature_flags_view", "layout"), container, false);
        FloatingActionButton floatingActionButton = view.findViewById(Utils.getResourceIdentifier("add_flag", "id"));

        RecyclerView rc = view.findViewById(Utils.getResourceIdentifier("list", "id"));
        rc.setLayoutManager(new LinearLayoutManager(1));
        rc.setClipToPadding(false);
        rc.setPadding(0, 0, 0, 200);

        CustomAdapter adapter = new CustomAdapter(flags);

        floatingActionButton.setOnClickListener(view1 -> addFlag(adapter));

        adapter.setItemClickListener(position -> modifyFlag(adapter, position));

        adapter.setItemCheckedChangeListener((checked, position) -> {
            FeatureFlag flag = flags.get(position);
            flags.set(position, new FeatureFlag(flag.getName(), checked));
            saveFlags();
        });

        rc.setAdapter(adapter);

        return view;
    }
}