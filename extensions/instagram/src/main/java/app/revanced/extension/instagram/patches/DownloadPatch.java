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
     */
    public static void addDownloadButton(Object obj1, Object vpz, Context context) {
        Log.d(TAG, "called the download button");
        Log.d(TAG, "obj1 type: " + (obj1 != null ? obj1.getClass().getName() : "null"));
        Log.d(TAG, "vpz type: " + (vpz != null ? vpz.getClass().getName() : "null"));
        Log.d(TAG, "context type: " + (context != null ? context.getClass().getName() : "null"));
        printObjectFields(obj1, "", 1);
        try {
            // Print contents of A08, A09 and A0A fields
            // printListContents(vpz, "A08");
            // printListContents(vpz, "A09");
            // printListContents(vpz, "A0A");
            
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

            // Reorder: move Download above Report
            reorderDownloadAboveReport(vpz);

            printCollectionContents(vpz.getClass().getField("A08").get(vpz), "    ");
               
        } catch (Exception e) {
            Log.d(TAG, "An error occured", e);
        }
    }
    
    /**
     * Reorders the menu items so that "Download" appears above "Report"
     */
    private static void reorderDownloadAboveReport(Object vpz) {
        try {
            java.lang.reflect.Field field = vpz.getClass().getField("A08");
            Object fieldValue = field.get(vpz);
            
            if (!(fieldValue instanceof java.util.List)) {
                return;
            }
            
            java.util.List<Object> list = (java.util.List<Object>) fieldValue;
            
            int reportIndex = -1;
            int downloadIndex = -1;
            Object downloadItem = null;
            
            // Find Report and Download indices
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                String label = getItemLabel(item);
                
                if ("Report".equals(label)) {
                    reportIndex = i;
                } else if ("Download".equals(label)) {
                    downloadIndex = i;
                    downloadItem = item;
                }
            }
            
            Log.d(TAG, "Report index: " + reportIndex + ", Download index: " + downloadIndex);
            
            // If both found and Download is after Report, move it
            if (reportIndex >= 0 && downloadIndex >= 0 && downloadItem != null && downloadIndex > reportIndex) {
                list.remove(downloadIndex);
                list.add(reportIndex, downloadItem);
                Log.d(TAG, "Moved Download to index " + reportIndex);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error reordering menu items", e);
        }
    }
    
    /**
     * Gets the label (A08 field) from a menu item
     */
    private static String getItemLabel(Object item) {
        try {
            java.lang.reflect.Field labelField = item.getClass().getField("A08");
            Object label = labelField.get(item);
            if (label instanceof String) {
                return (String) label;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    private static void printCollectionContents(Object collection, String prefix) {
        try {
            Log.d(TAG, "=== Contents of collection ===");
            Log.d(TAG, "Collection type: " + (collection != null ? collection.getClass().getName() : "null"));

            if (collection instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) collection;
                Log.d(TAG, "List size: " + list.size());

                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    Log.d(TAG, "  [" + i + "]");
                    printObjectFields(item, "    ", 0);
                }
            } else if (collection instanceof java.util.Collection) {
                java.util.Collection<?> coll = (java.util.Collection<?>) collection;
                Log.d(TAG, "Collection size: " + coll.size());

                int i = 0;
                for (Object item : coll) {
                    Log.d(TAG, "  [" + i + "]");
                    printObjectFields(item, "    ", 0);
                    i++;
                }
            } else {
                Log.d(TAG, "Value: " + (collection != null ? collection.toString() : "null"));
            }
            Log.d(TAG, "=== End of collection ===");
        } catch (Exception e) {
            Log.e(TAG, "Error printing collection contents", e);
        }
    }
    
    /**
     * Prints all String fields of an object using reflection.
     * This is a reusable function that can be used to inspect any object's String fields.
     * 
     * @param obj The object to print fields from
     * @param prefix Optional prefix string to add before each log line (e.g., "    " for indentation)
     */
    public static void printObjectFields(Object obj, String prefix, int maxDepth) {
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

                if (fieldValue instanceof String) {
                    String stringValue = (String) fieldValue;
                    Log.d(TAG, indent + "  " + field.getName() + " (String): \"" + stringValue + "\"");
                } else if (fieldValue instanceof Integer) {
                    Log.d(TAG, indent + "  " + field.getName() + " (int): " + fieldValue);
                } else if (fieldValue instanceof Long) {
                    Log.d(TAG, indent + "  " + field.getName() + " (long): " + fieldValue);
                } else if (fieldValue instanceof Float) {
                    Log.d(TAG, indent + "  " + field.getName() + " (float): " + fieldValue);
                } else if (fieldValue instanceof Double) {
                    Log.d(TAG, indent + "  " + field.getName() + " (double): " + fieldValue);
                } else if (fieldValue instanceof Boolean) {
                    Log.d(TAG, indent + "  " + field.getName() + " (boolean): " + fieldValue);
                } else if (fieldValue instanceof Byte) {
                    Log.d(TAG, indent + "  " + field.getName() + " (byte): " + fieldValue);
                } else if (fieldValue instanceof Short) {
                    Log.d(TAG, indent + "  " + field.getName() + " (short): " + fieldValue);
                } else if (fieldValue instanceof Character) {
                    Log.d(TAG, indent + "  " + field.getName() + " (char): " + fieldValue);
                } else if (fieldValue instanceof java.util.List) {
                    java.util.List<?> list = (java.util.List<?>) fieldValue;
                    Log.d(TAG, indent + "  " + field.getName() + " (List): size=" + list.size());
                } else if (fieldValue != null) {
                    Log.d(TAG, indent + "  " + field.getName() + " (" + fieldValue.getClass().getSimpleName() + "): <object>");
                    if (maxDepth > 0) {
                        printObjectFields(fieldValue, indent + "    ", maxDepth - 1);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, indent + "Error reading fields: " + e.getMessage());
        }
    }
    
}
