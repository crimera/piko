package app.revanced.extension.instagram.patches;

import android.content.Context;
import android.view.View;
import android.util.Log;

public class DownloadPatch {

    private static final String TAG = "DownloadPatch";

		/**
     * Adds a download button to the bottom sheet
     * 
     * @param context  - Android context (from Fragment.requireContext())
     * @param vpz      - The VpZ bottom sheet builder (LX/VpZ)
     * @param media    - The media object (LX/4bl)
     */
    public static void addDownloadButton(Context context, Object vpz) {
        Log.d(TAG, "called the download button");
        try {
            // Create click listener
            View.OnClickListener clickListener = v -> {
            	Log.d(TAG, "handleDownload called");
            };
            
            // Add menu item to VpZ
            // VpZ.A02(Context, OnClickListener, String title, int icon, boolean)
            vpz.getClass()
               .getMethod("A02", Context.class, View.OnClickListener.class, 
                          String.class, int.class, boolean.class)
               .invoke(vpz, context, clickListener, "Download", 0x7f08217b, false);
               
        } catch (Exception e) {
            e.printStackTrace();
        }
		}
}
