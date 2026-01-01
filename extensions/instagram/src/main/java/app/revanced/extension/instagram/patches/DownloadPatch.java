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
        Log.d(TAG, "context type: " + (context != null ? context.getClass().getName() : "null"));
        Log.d(TAG, "vpz type: " + (vpz != null ? vpz.getClass().getName() : "null"));
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
            Log.d(TAG, "An error occured", e);
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
                    Log.d(TAG, "  [" + i + "]");
                    printObjectFields(item, "    ");
                }
            } else if (fieldValue instanceof java.util.Collection) {
                java.util.Collection<?> collection = (java.util.Collection<?>) fieldValue;
                Log.d(TAG, "Collection size: " + collection.size());
                
                int i = 0;
                for (Object item : collection) {
                    Log.d(TAG, "  [" + i + "]");
                    printObjectFields(item, "    ");
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
    
    /**
     * Prints all String fields of an object using reflection.
     * This is a reusable function that can be used to inspect any object's String fields.
     * 
     * @param obj The object to print fields from
     * @param prefix Optional prefix string to add before each log line (e.g., "    " for indentation)
     */
    public static void printObjectFields(Object obj, String prefix) {
        if (obj == null) {
            Log.d(TAG, (prefix != null ? prefix : "") + "null");
            return;
        }
        
        String indent = prefix != null ? prefix : "";
        Log.d(TAG, indent + "Object type: " + obj.getClass().getName());
        
        try {
            java.lang.reflect.Field[] fields = obj.getClass().getDeclaredFields();
            java.lang.reflect.Field[] superFields = obj.getClass().getSuperclass() != null 
                ? obj.getClass().getSuperclass().getDeclaredFields() 
                : new java.lang.reflect.Field[0];
            
            // Combine declared fields and superclass fields
            java.util.List<java.lang.reflect.Field> allFields = new java.util.ArrayList<>();
            for (java.lang.reflect.Field f : fields) {
                allFields.add(f);
            }
            for (java.lang.reflect.Field f : superFields) {
                allFields.add(f);
            }
            
            for (java.lang.reflect.Field field : allFields) {
                field.setAccessible(true);
                Object fieldValue = field.get(obj);
                
                // Print string fields
                if (fieldValue instanceof String) {
                    String stringValue = (String) fieldValue;
                    Log.d(TAG, indent + "  " + field.getName() + " (String): \"" + stringValue + "\"");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, indent + "Error reading fields: " + e.getMessage());
        }
    }
    
}
