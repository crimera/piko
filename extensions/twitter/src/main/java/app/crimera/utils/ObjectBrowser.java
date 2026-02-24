package app.crimera.utils;

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
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.shared.Utils;

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
        showObjectDialog(context, obj, title, false, null);
    }

    private static void browseObject(Context context, Object obj, String path) {
        if (context == null || obj == null) {
            Utils.showToastShort("Cannot browse null object");
            return;
        }

        String title = path + "." + getClassName(obj.getClass());
        showObjectDialog(context, obj, title, false, null);
    }

    private static void browseClass(Context context, Class<?> clazz) {
        if (clazz.isPrimitive() || clazz == String.class) {
            Utils.showToastShort("Type: " + clazz.getSimpleName());
            return;
        }

        // For class types, show the object browser with class info header
        showObjectDialog(context, null, getClassName(clazz), true, clazz);
    }

    private static void showObjectDialog(Context context, Object obj, String title, boolean isClassView, Class<?> classView) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);

        LinearLayout mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);

        EditText searchBox = new EditText(context);
        searchBox.setHint("Search fields and methods...");
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

        Class<?> clazz = classView != null ? classView : (obj != null ? obj.getClass() : null);
        if (clazz == null) {
            TextView error = new TextView(context);
            error.setText("Could not resolve class: " + title);
            container.addView(error);
        } else {
            // Class info section at the top
            addClassInfoSection(context, clazz, container, searchableRows);
        }

        if (clazz != null) {
            // Fields section header
            TextView fieldsHeader = new TextView(context);
            fieldsHeader.setText("━━ Fields ━━");
            fieldsHeader.setTextColor(0xFF64B5F6);
            fieldsHeader.setTypeface(null, Typeface.BOLD);
            fieldsHeader.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(8));
            container.addView(fieldsHeader);
            searchableRows.add(new SearchableRow(fieldsHeader, "fields"));

            // Fields
            Field[] fields = clazz.getDeclaredFields();
            List<Field> sortedFields = obj != null ? sortFieldsByNullLast(fields, obj) : Arrays.asList(fields);

            for (Field field : sortedFields) {
                try {
                    field.setAccessible(true);
                    View row = createFieldRow(context, field, obj, title);
                    String searchText = buildFieldSearchText(field, obj);
                    searchableRows.add(new SearchableRow(row, searchText));
                    container.addView(row);
                } catch (Exception e) {
                    TextView errorRow = new TextView(context);
                    errorRow.setText("Error reading field: " + field.getName());
                    container.addView(errorRow);
                }
            }
        }

        // Methods section
        if (clazz != null) {
            TextView methodsHeader = new TextView(context);
            methodsHeader.setText("━━ Methods ━━");
            methodsHeader.setTextColor(0xFF81C784);
            methodsHeader.setTypeface(null, Typeface.BOLD);
            methodsHeader.setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(8));
            container.addView(methodsHeader);
            searchableRows.add(new SearchableRow(methodsHeader, "methods"));

            // Methods
            Method[] methods = clazz.getDeclaredMethods();
            List<Method> sortedMethods = new ArrayList<>(Arrays.asList(methods));
            Collections.sort(sortedMethods, (m1, m2) -> {
                int params1 = m1.getParameterCount();
                int params2 = m2.getParameterCount();
                if (params1 == 0 && params2 > 0) return -1;
                if (params1 > 0 && params2 == 0) return 1;
                return m1.getName().compareTo(m2.getName());
            });

            for (Method method : sortedMethods) {
                try {
                    method.setAccessible(true);
                    View row = createMethodRow(context, method, obj, title);
                    String searchText = buildMethodSearchText(method);
                    searchableRows.add(new SearchableRow(row, searchText));
                    container.addView(row);
                } catch (Exception e) {
                    TextView errorRow = new TextView(context);
                    errorRow.setText("Error reading method: " + method.getName());
                    container.addView(errorRow);
                }
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

    private static void addClassInfoSection(Context context, Class<?> clazz, LinearLayout container, List<SearchableRow> searchableRows) {
        LinearLayout infoContainer = new LinearLayout(context);
        infoContainer.setOrientation(LinearLayout.VERTICAL);
        infoContainer.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));

        // Package
        TextView packageLabel = new TextView(context);
        packageLabel.setText("package: " + (clazz.getPackage() != null ? clazz.getPackage().getName() : "default"));
        packageLabel.setTextColor(0xFFAAAAAA);
        packageLabel.setTypeface(Typeface.MONOSPACE);
        packageLabel.setTextSize(11);
        infoContainer.addView(packageLabel);

        // Superclass
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            LinearLayout superRow = new LinearLayout(context);
            superRow.setOrientation(LinearLayout.HORIZONTAL);
            
            TextView superLabel = new TextView(context);
            superLabel.setText("extends: ");
            superLabel.setTextColor(0xFF888888);
            superLabel.setTypeface(Typeface.MONOSPACE);
            superLabel.setTextSize(11);
            superRow.addView(superLabel);

            TextView superValue = new TextView(context);
            superValue.setText(getClassName(superclass));
            superValue.setTextColor(0xFFFFB74D);
            superValue.setTypeface(Typeface.MONOSPACE);
            superValue.setTextSize(11);
            superValue.setOnClickListener(v -> browseClass(context, superclass));
            superRow.addView(superValue);

            infoContainer.addView(superRow);
        }

        // Interfaces
        Class<?>[] interfaces = clazz.getInterfaces();
        if (interfaces.length > 0) {
            LinearLayout ifaceRow = new LinearLayout(context);
            ifaceRow.setOrientation(LinearLayout.HORIZONTAL);

            TextView ifaceLabel = new TextView(context);
            ifaceLabel.setText("implements: ");
            ifaceLabel.setTextColor(0xFF888888);
            ifaceLabel.setTypeface(Typeface.MONOSPACE);
            ifaceLabel.setTextSize(11);
            ifaceRow.addView(ifaceLabel);

            for (int i = 0; i < interfaces.length; i++) {
                Class<?> iface = interfaces[i];
                TextView ifaceName = new TextView(context);
                ifaceName.setText(getClassName(iface) + (i < interfaces.length - 1 ? ", " : ""));
                ifaceName.setTextColor(0xFFFFB74D);
                ifaceName.setTypeface(Typeface.MONOSPACE);
                ifaceName.setTextSize(11);
                ifaceName.setOnClickListener(v -> browseClass(context, iface));
                ifaceRow.addView(ifaceName);
            }

            infoContainer.addView(ifaceRow);
        }

        container.addView(infoContainer);
        searchableRows.add(new SearchableRow(infoContainer, "class info package superclass interface extends implements"));
    }

    private static Class<?> getClassFromName(String name) {
        try {
            // Try common packages
            String[] commonPackages = {
                "java.lang.",
                "java.util.",
                "android.content.",
                "android.view.",
                "android.widget.",
                "com.twitter."
            };
            
            // Try as-is first (fully qualified)
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {}
            
            // Try with common packages
            for (String pkg : commonPackages) {
                try {
                    return Class.forName(pkg + name);
                } catch (ClassNotFoundException ignored) {}
            }
            
            // Try primitive types
            switch (name) {
                case "int": return int.class;
                case "long": return long.class;
                case "boolean": return boolean.class;
                case "byte": return byte.class;
                case "short": return short.class;
                case "char": return char.class;
                case "float": return float.class;
                case "double": return double.class;
                case "void": return void.class;
            }
        } catch (Exception ignored) {}
        return null;
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
        Object fieldValue = null;
        if (obj != null) {
            try {
                fieldValue = field.get(obj);
            } catch (IllegalAccessException e) {
                fieldValue = "<access denied>";
            }
        }

        final Object finalFieldValue = fieldValue;
        final String fieldPath = parentPath + "." + fieldName;

        String typeName = getTypeName(field);
        String displayValue = getDisplayValue(fieldValue);
        boolean isPrimitive = isPrimitiveOrWrapper(fieldType);

        if (obj == null) {
            if (fieldType.isArray()) {
                String arrayTypeName = fieldType.getComponentType().getSimpleName();
                textView.setText(fieldName + " - " + arrayTypeName + "[] ->");
                textView.setTextColor(0xFF64B5F6);
                textView.setOnClickListener(v -> browseClass(context, fieldType));
                return textView;
            }
            if (List.class.isAssignableFrom(fieldType)) {
                String itemType = getListItemTypeName(field);
                textView.setText(fieldName + " - List<" + itemType + "> ->");
                textView.setTextColor(0xFF81C784);
                textView.setOnClickListener(v -> browseClass(context, fieldType));
                return textView;
            }
            if (!isPrimitive) {
                textView.setText(fieldName + " - " + typeName + " ->");
                textView.setTextColor(0xFFFFB74D);
                textView.setOnClickListener(v -> browseClass(context, fieldType));
                return textView;
            }
            textView.setText(fieldName + " - " + typeName);
            textView.setTextColor(0xFF888888);
            return textView;
        }

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
            textView.setText(fieldName + " - " + arrayTypeName + "[] (" + length + ") ->");
            textView.setTextColor(0xFF64B5F6);
            textView.setOnClickListener(v -> showArrayDialog(context, finalFieldValue, fieldPath));
            return textView;
        }

        if (List.class.isAssignableFrom(fieldType)) {
            String itemType = getListItemTypeName(field);
            List<?> list = (List<?>) finalFieldValue;
            textView.setText(fieldName + " - List<" + itemType + "> (" + list.size() + ") ->");
            textView.setTextColor(0xFF81C784);
            textView.setOnClickListener(v -> showListDialog(context, list, fieldPath));
            return textView;
        }

        String className = getClassName(fieldType);
        textView.setText(fieldName + " - " + className + " ->");
        textView.setTextColor(0xFFFFB74D);
        textView.setOnClickListener(v -> browseObject(context, finalFieldValue, fieldPath));
        return textView;
    }

    private static View createMethodRow(Context context, Method method, Object obj, String parentPath) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        row.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));

        String methodName = method.getName();
        Class<?> returnType = method.getReturnType();
        Parameter[] params = method.getParameters();
        boolean hasNoArgs = params.length == 0;
        boolean isVoid = returnType == void.class || returnType == Void.class;

        final String methodPath = parentPath + "." + methodName;

        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView methodNameLabel = new TextView(context);
        methodNameLabel.setText(methodName + (hasNoArgs ? "()" : "(...)") + ": ");
        methodNameLabel.setTextColor(hasNoArgs ? 0xFF81C784 : 0xFFCCCCCC);
        headerRow.addView(methodNameLabel);

        TextView returnTypeLabel = new TextView(context);
        returnTypeLabel.setText(getClassName(returnType));
        returnTypeLabel.setTextColor(0xFFFFB74D);
        returnTypeLabel.setTypeface(Typeface.MONOSPACE);
        returnTypeLabel.setOnClickListener(v -> browseClass(context, returnType));
        headerRow.addView(returnTypeLabel);

        row.addView(headerRow);

        // Parameters
        if (params.length > 0) {
            LinearLayout paramsContainer = new LinearLayout(context);
            paramsContainer.setOrientation(LinearLayout.VERTICAL);
            paramsContainer.setPadding(dpToPx(16), dpToPx(4), 0, 0);

            for (int i = 0; i < params.length; i++) {
                Parameter param = params[i];
                TextView paramLabel = new TextView(context);
                paramLabel.setText("  param" + i + ": " + getClassName(param.getType()));
                paramLabel.setTextColor(0xFF64B5F6);
                paramLabel.setTypeface(Typeface.MONOSPACE);
                paramLabel.setOnClickListener(v -> browseClass(context, param.getType()));
                paramsContainer.addView(paramLabel);
            }
            row.addView(paramsContainer);
        }

        // Click behavior - only if we have an object to invoke on
        if (hasNoArgs && obj != null) {
            methodNameLabel.setOnClickListener(v -> {
                if (isVoid) {
                    try {
                        method.invoke(obj);
                        Utils.showToastShort("Executed: " + methodName + "()");
                    } catch (Exception e) {
                        Utils.showToastShort("Error: " + e.getMessage());
                    }
                } else {
                    try {
                        Object result = method.invoke(obj);
                        if (result == null) {
                            Utils.showToastShort(methodName + "() = null");
                        } else if (isPrimitiveOrWrapper(result.getClass())) {
                            Utils.showToastShort(methodName + "() = " + result);
                            Utils.setClipboard(String.valueOf(result));
                        } else {
                            browseObject(context, result, methodPath + "()");
                        }
                    } catch (Exception e) {
                        Utils.showToastShort("Error: " + e.getMessage());
                    }
                }
            });
        }

        return row;
    }

    private static void showListDialog(Context context, List<?> list, String parentPath) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(parentPath + " (" + list.size() + " items)");

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
                textView.setText("[" + i + "] null");
                textView.setTextColor(0xFF888888);
                searchableRows.add(new SearchableRow(textView, "[" + i + "] null"));
            } else if (isPrimitiveOrWrapper(item.getClass())) {
                String value = getDisplayValue(item);
                textView.setText("[" + i + "] " + item.getClass().getSimpleName() + ": " + value);
                textView.setTextColor(0xFFCCCCCC);
                textView.setTypeface(Typeface.MONOSPACE);
                searchableRows.add(new SearchableRow(textView, "[" + i + "] " + String.valueOf(item).toLowerCase()));
                final String finalValue = String.valueOf(item);
                textView.setOnClickListener(v -> {
                    Utils.setClipboard(finalValue);
                    Utils.showToastShort("Copied: " + finalValue);
                });
            } else {
                textView.setText("[" + i + "] " + getClassName(item.getClass()) + " ->");
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
                textView.setText("[" + i + "] null");
                textView.setTextColor(0xFF888888);
                searchableRows.add(new SearchableRow(textView, "[" + i + "] null"));
            } else if (isPrimitiveOrWrapper(item.getClass())) {
                String value = getDisplayValue(item);
                textView.setText("[" + i + "] " + item.getClass().getSimpleName() + ": " + value);
                textView.setTextColor(0xFFCCCCCC);
                textView.setTypeface(Typeface.MONOSPACE);
                searchableRows.add(new SearchableRow(textView, "[" + i + "] " + String.valueOf(item).toLowerCase()));
                final String finalValue = String.valueOf(item);
                textView.setOnClickListener(v -> {
                    Utils.setClipboard(finalValue);
                    Utils.showToastShort("Copied: " + finalValue);
                });
            } else {
                textView.setText("[" + i + "] " + getClassName(item.getClass()) + " ->");
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

    private static String buildFieldSearchText(Field field, Object obj) {
        StringBuilder sb = new StringBuilder();
        sb.append("field ").append(field.getName().toLowerCase());
        if (obj != null) {
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value != null) {
                    sb.append(" ").append(String.valueOf(value).toLowerCase());
                }
            } catch (Exception ignored) {}
        }
        return sb.toString();
    }

    private static String buildMethodSearchText(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append("method ").append(method.getName().toLowerCase());
        sb.append(" ").append(getClassName(method.getReturnType()).toLowerCase());
        for (Parameter param : method.getParameters()) {
            sb.append(" ").append(getClassName(param.getType()).toLowerCase());
        }
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
        if (clazz == null) return "void";
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
