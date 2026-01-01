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
            // Print contents of A08, A09 and A0A fields
            printListContents(vpz, "A08");
            printListContents(vpz, "A09");
            printListContents(vpz, "A0A");
            
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
        		Log.d(TAG, "An error occured");
            e.printStackTrace();
        }
		}
    
    private static void printListContents(Object vpz, String fieldName) {
        try {
            java.lang.reflect.Field field = vpz.getClass().getField(fieldName);
            Object fieldValue = field.get(vpz);
            
            Log.d(TAG, "=== Contents of VpZ." + fieldName + " ===");
            Log.d(TAG, "Field type: " + (fieldValue != null ? fieldValue.getClass().getName() : "null"));
            
            if (fieldValue instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) fieldValue;
                Log.d(TAG, "List size: " + list.size());
                
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    Log.d(TAG, "  [" + i + "] " + (item != null ? item.toString() : "null") + 
                          " (type: " + (item != null ? item.getClass().getName() : "null") + ")");
                }
            } else if (fieldValue instanceof java.util.Collection) {
                java.util.Collection<?> collection = (java.util.Collection<?>) fieldValue;
                Log.d(TAG, "Collection size: " + collection.size());
                
                int i = 0;
                for (Object item : collection) {
                    Log.d(TAG, "  [" + i + "] " + (item != null ? item.toString() : "null") + 
                          " (type: " + (item != null ? item.getClass().getName() : "null") + ")");
                    i++;
                }
            } else {
                Log.d(TAG, "Value: " + (fieldValue != null ? fieldValue.toString() : "null"));
            }
            Log.d(TAG, "=== End of VpZ." + fieldName + " ===");
        } catch (Exception e) {
            Log.e(TAG, "Error printing contents of " + fieldName, e);
        }
    }
}
