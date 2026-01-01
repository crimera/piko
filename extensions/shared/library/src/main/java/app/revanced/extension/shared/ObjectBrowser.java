package app.revanced.extension.shared;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

public class ObjectBrowser {

    private static final int MAX_DISPLAY_LENGTH = 50;

    public static void browseObject(Context context, Object obj) {
        if (context == null || obj == null) {
            Utils.showToastShort("Cannot browse null object");
            return;
        }

        String title = obj.getClass().getSimpleName();
        if (title == null || title.isEmpty()) {
            title = obj.getClass().getName();
        }
        showFieldsDialog(context, obj, title);
    }

    private static void showFieldsDialog(Context context, Object obj, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container);

        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                View row = createFieldRow(context, field, obj);
                container.addView(row);
            } catch (Exception e) {
                TextView errorRow = new TextView(context);
                errorRow.setText("Error reading field: " + field.getName());
                container.addView(errorRow);
            }
        }

        builder.setView(scrollView);
        builder.setNeutralButton("Close", null);
        builder.show();
    }

    private static View createFieldRow(Context context, Field field, Object obj) {
        TextView textView = new TextView(context);
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        textView.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        String fieldName = field.getName();
        Class<?> fieldType = field.getType();
        Object fieldValue;
        try {
            fieldValue = field.get(obj);
        } catch (IllegalAccessException e) {
            fieldValue = "<access denied>";
        }

        final Object finalFieldValue = fieldValue;
        final String finalFieldName = fieldName;

        String typeName = getTypeName(field);
        String displayValue = getDisplayValue(fieldValue);
        boolean isPrimitive = isPrimitiveOrWrapper(fieldType);

        if (fieldValue == null) {
            textView.setText(fieldName + " - " + typeName + ": null");
            textView.setTextColor(0xFF888888);
            return textView;
        }

        if (isPrimitive) {
            textView.setText(fieldName + " - " + typeName + ": " + displayValue);
            textView.setTextColor(0xFFCCCCCC);
            textView.setTypeface(Typeface.MONOSPACE);
            textView.setOnClickListener(v -> {
                Utils.setClipboard(String.valueOf(finalFieldValue));
                Utils.showToastShort("Copied: " + String.valueOf(finalFieldValue));
            });
            return textView;
        }

        if (fieldType.isArray()) {
            String arrayTypeName = fieldType.getComponentType().getSimpleName();
            int length = Array.getLength(finalFieldValue);
            textView.setText(fieldName + " - " + arrayTypeName + "[] (" + length + ") →");
            textView.setTextColor(0xFF64B5F6);
            textView.setOnClickListener(v -> showArrayDialog(context, finalFieldValue, finalFieldName));
            return textView;
        }

        if (List.class.isAssignableFrom(fieldType)) {
            String itemType = getListItemTypeName(field);
            List<?> list = (List<?>) finalFieldValue;
            textView.setText(fieldName + " - List<" + itemType + "> (" + list.size() + ") →");
            textView.setTextColor(0xFF81C784);
            textView.setOnClickListener(v -> showListDialog(context, list, finalFieldName));
            return textView;
        }

        String className = fieldType.getSimpleName();
        if (className == null || className.isEmpty()) {
            className = fieldType.getName();
        }
        textView.setText(fieldName + " - " + className + " →");
        textView.setTextColor(0xFFFFB74D);
        textView.setOnClickListener(v -> browseObject(context, finalFieldValue));
        return textView;
    }

    private static void showListDialog(Context context, List<?> list, String fieldName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(fieldName + " (List)");

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container);

        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            TextView textView = new TextView(context);
            textView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            textView.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

            if (item == null) {
                textView.setText("[" + i + "] - null");
                textView.setTextColor(0xFF888888);
            } else if (isPrimitiveOrWrapper(item.getClass())) {
                String value = getDisplayValue(item);
                textView.setText("[" + i + "] - " + item.getClass().getSimpleName() + ": " + value);
                textView.setTextColor(0xFFCCCCCC);
                textView.setTypeface(Typeface.MONOSPACE);
                final String finalValue = String.valueOf(item);
                textView.setOnClickListener(v -> {
                    Utils.setClipboard(finalValue);
                    Utils.showToastShort("Copied: " + finalValue);
                });
            } else {
                textView.setText("[" + i + "] - " + item.getClass().getSimpleName() + " →");
                textView.setTextColor(0xFFFFB74D);
                textView.setOnClickListener(v -> browseObject(context, item));
            }
            container.addView(textView);
        }

        builder.setView(scrollView);
        builder.setNeutralButton("Close", null);
        builder.show();
    }

    private static void showArrayDialog(Context context, Object array, String fieldName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(fieldName + " (Array)");

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container);

        int length = Array.getLength(array);
        Class<?> componentType = array.getClass().getComponentType();

        for (int i = 0; i < length; i++) {
            Object item = Array.get(array, i);
            TextView textView = new TextView(context);
            textView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            textView.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

            if (item == null) {
                textView.setText("[" + i + "] - null");
                textView.setTextColor(0xFF888888);
            } else if (isPrimitiveOrWrapper(item.getClass())) {
                String value = getDisplayValue(item);
                textView.setText("[" + i + "] - " + item.getClass().getSimpleName() + ": " + value);
                textView.setTextColor(0xFFCCCCCC);
                textView.setTypeface(Typeface.MONOSPACE);
                final String finalValue = String.valueOf(item);
                textView.setOnClickListener(v -> {
                    Utils.setClipboard(finalValue);
                    Utils.showToastShort("Copied: " + finalValue);
                });
            } else {
                textView.setText("[" + i + "] - " + item.getClass().getSimpleName() + " →");
                textView.setTextColor(0xFFFFB74D);
                textView.setOnClickListener(v -> browseObject(context, item));
            }
            container.addView(textView);
        }

        builder.setView(scrollView);
        builder.setNeutralButton("Close", null);
        builder.show();
    }

    private static boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || type == Integer.class
                || type == Long.class
                || type == Double.class
                || type == Float.class
                || type == Boolean.class
                || type == Byte.class
                || type == Short.class
                || type == Character.class;
    }

    private static String getTypeName(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length > 0) {
                StringBuilder sb = new StringBuilder("List<");
                for (int i = 0; i < typeArgs.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(typeArgs[i].getTypeName());
                }
                sb.append(">");
                return sb.toString();
            }
        }
        return field.getType().getSimpleName();
    }

    private static String getListItemTypeName(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length > 0) {
                String name = typeArgs[0].getTypeName();
                int lastDot = name.lastIndexOf('.');
                return lastDot > 0 ? name.substring(lastDot + 1) : name;
            }
        }
        return "?";
    }

    private static String getDisplayValue(Object value) {
        if (value == null) return "null";
        String str = String.valueOf(value);
        if (str.length() > MAX_DISPLAY_LENGTH) {
            return str.substring(0, MAX_DISPLAY_LENGTH) + "...";
        }
        return str;
    }

    private static int dpToPx(int dp) {
        return (int) (dp * 1.5f);
    }
}
