package app.revanced.extension.instagram.patches;

import android.content.Context;
import android.view.View;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

public class DownloadPatch {

    private static final String TAG = "DownloadPatch";

    /**
     * Adds a download button to the bottom sheet by directly creating a 75X menu item
     * and adding it to the VpZ.A08 LinkedList
     * 
     * @param context - Android context (from Fragment.requireContext())
     * @param vpz     - The VpZ bottom sheet builder (LX/VpZ)
     */
    public static void addDownloadButton(Context context, Object vpz) {
        Log.d(TAG, "Adding download button");
        
        try {
            // Get the A08 field (LinkedList) from VpZ
            Field a08Field = vpz.getClass().getField("A08");
            Object a08Value = a08Field.get(vpz);
            
            if (!(a08Value instanceof LinkedList)) {
                Log.e(TAG, "A08 is not a LinkedList: " + (a08Value != null ? a08Value.getClass().getName() : "null"));
                return;
            }
            
            LinkedList<Object> menuList = (LinkedList<Object>) a08Value;
            
            // Print list contents before adding
            Log.d(TAG, "=== VpZ.A08 BEFORE adding download button ===");
            printMenuList(menuList);
            
            // Create click listener for download action
            View.OnClickListener clickListener = v -> {
                Log.d(TAG, "Download button clicked!");
                // TODO: Implement actual download logic here
            };
            
            // Create the 75X menu item using constructor: <init>(Context, OnClickListener, String)
            // This is the X.75X class
            Class<?> menuItemClass = Class.forName("X.75X");
            Constructor<?> constructor = menuItemClass.getConstructor(
                Context.class, 
                View.OnClickListener.class, 
                String.class
            );
            
            Object downloadMenuItem = constructor.newInstance(context, clickListener, "Download");
            
            // Add at the beginning of the list (index 0) so it appears first
            menuList.add(0, downloadMenuItem);
            
            // Print list contents after adding
            Log.d(TAG, "=== VpZ.A08 AFTER adding download button ===");
            printMenuList(menuList);
            
            Log.d(TAG, "Download button added successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to add download button", e);
        }
    }
    
    /**
     * Prints the contents of a menu list (LinkedList of menu items)
     */
    private static void printMenuList(List<?> menuList) {
        Log.d(TAG, "List size: " + menuList.size());
        for (int i = 0; i < menuList.size(); i++) {
            Object item = menuList.get(i);
            Log.d(TAG, "  [" + i + "] " + item.getClass().getName());
            
            // Try to get the A08 field (label) if it exists
            try {
                Field labelField = item.getClass().getField("A08");
                Object label = labelField.get(item);
                if (label instanceof String) {
                    Log.d(TAG, "       Label (A08): \"" + label + "\"");
                }
            } catch (NoSuchFieldException e) {
                // Field doesn't exist, try other common label fields
                tryPrintField(item, "A05", "Label (A05)");
            } catch (Exception e) {
                Log.d(TAG, "       (could not read label)");
            }
            
            // Try to get A07 field (subtitle) if it exists
            tryPrintField(item, "A07", "Subtitle (A07)");
        }
    }
    
    /**
     * Tries to print a specific field from an object
     */
    private static void tryPrintField(Object obj, String fieldName, String displayName) {
        try {
            Field field = obj.getClass().getField(fieldName);
            Object value = field.get(obj);
            if (value instanceof String) {
                Log.d(TAG, "       " + displayName + ": \"" + value + "\"");
            }
        } catch (Exception ignored) {
            // Field doesn't exist or can't be read
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
