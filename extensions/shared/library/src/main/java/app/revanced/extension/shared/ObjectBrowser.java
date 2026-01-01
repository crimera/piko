package app.revanced.extension.shared;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ObjectBrowser {

    private static final int MAX_DISPLAY_LENGTH = 50;

    private static class SearchableRow {
        View view;
        String searchText;

        SearchableRow(View view, String searchText) {
            this.view = view;
            this.searchText = searchText;
        }
    }

    public static void browseObject(Context context, Object obj) {
        if (context == null || obj == null) {
            Utils.showToastShort("Cannot browse null object");
            return;
        }

        String title = getClassName(obj.getClass());
        showFieldsDialog(context, obj, title);
    }

    private static void browseObject(Context context, Object obj, String path) {
        if (context == null || obj == null) {
            Utils.showToastShort("Cannot browse null object");
            return;
        }

        String title = path + "." + getClassName(obj.getClass());
        showFieldsDialog(context, obj, title);
    }

    private static void showFieldsDialog(Context context, Object obj, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);

        LinearLayout mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);

        EditText searchBox = new EditText(context);
        searchBox.setHint("Search fields...");
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        searchParams.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        searchBox.setLayoutParams(searchParams);
        mainContainer.addView(searchBox);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container);

        Field[] fields = obj.getClass().getDeclaredFields();
        List<Field> sortedFields = sortFieldsByNullLast(fields, obj);

        List<SearchableRow> searchableRows = new ArrayList<>();

        for (Field field : sortedFields) {
            try {
                field.setAccessible(true);
                View row = createFieldRow(context, field, obj, title);
                String searchText = buildSearchText(field, obj);
                searchableRows.add(new SearchableRow(row, searchText));
                container.addView(row);
            } catch (Exception e) {
                TextView errorRow = new TextView(context);
                errorRow.setText("Error reading field: " + field.getName());
                container.addView(errorRow);
            }
        }

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().toLowerCase().trim();
                for (SearchableRow row : searchableRows) {
                    boolean visible = query.isEmpty() || row.searchText.contains(query);
                    row.view.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            }
        });

        mainContainer.addView(scrollView);
        builder.setView(mainContainer);
        builder.setNeutralButton("Close", null);
        builder.show();
    }

    private static View createFieldRow(Context context, Field field, Object obj, String parentPath) {
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
        final String fieldPath = parentPath + "." + fieldName;

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
            textView.setOnClickListener(v -> showArrayDialog(context, finalFieldValue, fieldPath));
            return textView;
        }

        if (List.class.isAssignableFrom(fieldType)) {
            String itemType = getListItemTypeName(field);
            List<?> list = (List<?>) finalFieldValue;
            textView.setText(fieldName + " - List<" + itemType + "> (" + list.size() + ") →");
            textView.setTextColor(0xFF81C784);
            textView.setOnClickListener(v -> showListDialog(context, list, fieldPath));
            return textView;
        }

        String className = getClassName(fieldType);
        textView.setText(fieldName + " - " + className + " →");
        textView.setTextColor(0xFFFFB74D);
        textView.setOnClickListener(v -> browseObject(context, finalFieldValue, fieldPath));
        return textView;
    }

    private static void showListDialog(Context context, List<?> list, String parentPath) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(parentPath + " (List)");

        LinearLayout mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);

        EditText searchBox = new EditText(context);
        searchBox.setHint("Search items...");
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        searchParams.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        searchBox.setLayoutParams(searchParams);
        mainContainer.addView(searchBox);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container);

        List<SearchableRow> searchableRows = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            TextView textView = new TextView(context);
            textView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            textView.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

            String itemPath = parentPath + "[" + i + "]";

            if (item == null) {
                textView.setText("[" + i + "] - null");
                textView.setTextColor(0xFF888888);
                searchableRows.add(new SearchableRow(textView, "[" + i + "] null"));
            } else if (isPrimitiveOrWrapper(item.getClass())) {
                String value = getDisplayValue(item);
                textView.setText("[" + i + "] - " + item.getClass().getSimpleName() + ": " + value);
                textView.setTextColor(0xFFCCCCCC);
                textView.setTypeface(Typeface.MONOSPACE);
                searchableRows.add(new SearchableRow(textView, "[" + i + "] " + String.valueOf(item).toLowerCase()));
                final String finalValue = String.valueOf(item);
                textView.setOnClickListener(v -> {
                    Utils.setClipboard(finalValue);
                    Utils.showToastShort("Copied: " + finalValue);
                });
            } else {
                textView.setText("[" + i + "] - " + getClassName(item.getClass()) + " →");
                textView.setTextColor(0xFFFFB74D);
                searchableRows.add(new SearchableRow(textView, "[" + i + "] " + getClassName(item.getClass()).toLowerCase()));
                textView.setOnClickListener(v -> browseObject(context, item, itemPath));
            }
            container.addView(textView);
        }

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().toLowerCase().trim();
                for (SearchableRow row : searchableRows) {
                    boolean visible = query.isEmpty() || row.searchText.contains(query);
                    row.view.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            }
        });

        mainContainer.addView(scrollView);
        builder.setView(mainContainer);
        builder.setNeutralButton("Close", null);
        builder.show();
    }

    private static void showArrayDialog(Context context, Object array, String parentPath) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(parentPath + " (Array)");

        LinearLayout mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);

        EditText searchBox = new EditText(context);
        searchBox.setHint("Search items...");
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        searchParams.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        searchBox.setLayoutParams(searchParams);
        mainContainer.addView(searchBox);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container);

        int length = Array.getLength(array);
        Class<?> componentType = array.getClass().getComponentType();

        List<SearchableRow> searchableRows = new ArrayList<>();

        for (int i = 0; i < length; i++) {
            Object item = Array.get(array, i);
            TextView textView = new TextView(context);
            textView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            textView.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

            String itemPath = parentPath + "[" + i + "]";

            if (item == null) {
                textView.setText("[" + i + "] - null");
                textView.setTextColor(0xFF888888);
                searchableRows.add(new SearchableRow(textView, "[" + i + "] null"));
            } else if (isPrimitiveOrWrapper(item.getClass())) {
                String value = getDisplayValue(item);
                textView.setText("[" + i + "] - " + item.getClass().getSimpleName() + ": " + value);
                textView.setTextColor(0xFFCCCCCC);
                textView.setTypeface(Typeface.MONOSPACE);
                searchableRows.add(new SearchableRow(textView, "[" + i + "] " + String.valueOf(item).toLowerCase()));
                final String finalValue = String.valueOf(item);
                textView.setOnClickListener(v -> {
                    Utils.setClipboard(finalValue);
                    Utils.showToastShort("Copied: " + finalValue);
                });
            } else {
                textView.setText("[" + i + "] - " + getClassName(item.getClass()) + " →");
                textView.setTextColor(0xFFFFB74D);
                searchableRows.add(new SearchableRow(textView, "[" + i + "] " + getClassName(item.getClass()).toLowerCase()));
                textView.setOnClickListener(v -> browseObject(context, item, itemPath));
            }
            container.addView(textView);
        }

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().toLowerCase().trim();
                for (SearchableRow row : searchableRows) {
                    boolean visible = query.isEmpty() || row.searchText.contains(query);
                    row.view.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            }
        });

        mainContainer.addView(scrollView);
        builder.setView(mainContainer);
        builder.setNeutralButton("Close", null);
        builder.show();
    }

    private static List<Field> sortFieldsByNullLast(Field[] fields, Object obj) {
        List<Field> list = new ArrayList<>(Arrays.asList(fields));
        Collections.sort(list, (f1, f2) -> {
            Object v1 = getFieldValueSafe(f1, obj);
            Object v2 = getFieldValueSafe(f2, obj);
            if (v1 == null && v2 != null) return 1;
            if (v1 != null && v2 == null) return -1;
            return 0;
        });
        return list;
    }

    private static Object getFieldValueSafe(Field field, Object obj) {
        try {
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static String buildSearchText(Field field, Object obj) {
        StringBuilder sb = new StringBuilder();
        sb.append(field.getName().toLowerCase());
        try {
            field.setAccessible(true);
            Object value = field.get(obj);
            if (value != null) {
                sb.append(" ").append(String.valueOf(value).toLowerCase());
            }
        } catch (Exception ignored) {}
        return sb.toString();
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

    private static String getClassName(Class<?> clazz) {
        String name = clazz.getSimpleName();
        if (name == null || name.isEmpty()) {
            name = clazz.getName();
            int lastDot = name.lastIndexOf('.');
            if (lastDot >= 0) {
                name = name.substring(lastDot + 1);
            }
        }
        return name;
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
                    String argName = typeArgs[i].getTypeName();
                    int lastDot = argName.lastIndexOf('.');
                    if (lastDot >= 0) {
                        argName = argName.substring(lastDot + 1);
                    }
                    sb.append(argName);
                }
                sb.append(">");
                return sb.toString();
            }
        }
        return getClassName(field.getType());
    }

    private static String getListItemTypeName(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length > 0) {
                String name = typeArgs[0].getTypeName();
                int lastDot = name.lastIndexOf('.');
                if (lastDot > 0) {
                    return name.substring(lastDot + 1);
                }
                return name;
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
