package app.morphe.extension.newx.settings;

import android.app.Fragment;
import android.os.Bundle;
import android.view.View;

@SuppressWarnings("deprecation")
public abstract class NewXCustomScreenFragment extends Fragment {
    private NewXCustomScreenHost customScreenHost;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        customScreenHost = NewXCustomScreenHost.attach(view);
    }

    @Override
    public void onDestroyView() {
        if (customScreenHost != null) {
            customScreenHost.close();
            customScreenHost = null;
        }
        super.onDestroyView();
    }
}
