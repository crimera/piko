/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 Section 7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.actionbar;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

public class GhostModeQuickToggle {
    private static final String HIDE_ICON_NAME = "design_ic_visibility_off";
    private static final String SHOW_ICON_NAME = "design_ic_visibility";
    private static final String ACTION_BAR_VIEW_TAG = "piko_ghost_mode_quick_toggle_action_bar";
    private static final int DIRECT_INBOX_MODEL_OFFSET_DP = 10;
    private static final int MAX_NATIVE_ANCHOR_SIZE_DP = 96;
    private static final String DIRECT_INBOX_LEADING_CLASS_NAME = "X.5Sf";
    private static final String DIRECT_INBOX_TITLE_CLASS_NAME = "X.L6U";
    private static final String DIRECT_INBOX_ACTION_CLASS_NAME = "X.5Sp";
    private static final String DIRECT_INBOX_PLACEMENT_CLASS_NAME = "X.5So";
    private static final ArrayList<WeakReference<ImageView>> BUTTONS = new ArrayList<>();
    private static final Set<ViewGroup> HOME_ACTION_BARS =
            Collections.newSetFromMap(new WeakHashMap<ViewGroup, Boolean>());
    private static final Set<ViewGroup> DIRECT_THREAD_ACTION_BARS =
            Collections.newSetFromMap(new WeakHashMap<ViewGroup, Boolean>());
    private static final Set<Object> DIRECT_INBOX_ACTIONS =
            Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());
    private static final Set<ViewGroup> PENDING_ACTION_BAR_ORDER_UPDATES =
            Collections.newSetFromMap(new WeakHashMap<ViewGroup, Boolean>());

    public static void addToHomeActionBar(ViewGroup viewGroup) {
        trackActionBar(HOME_ACTION_BARS, viewGroup);
        addToActionBar(viewGroup, Pref.showGhostModeQuickToggleHome());
    }

    public static void addToDirectThreadActionBar(ViewGroup viewGroup) {
        trackActionBar(DIRECT_THREAD_ACTION_BARS, viewGroup);
        addToActionBar(viewGroup, Pref.showGhostModeQuickToggleDirectThread());
    }

    public static void refreshActionBarButtons() {
        refreshActionBars(HOME_ACTION_BARS, Pref.showGhostModeQuickToggleHome(), true);
        refreshActionBars(DIRECT_THREAD_ACTION_BARS, Pref.showGhostModeQuickToggleDirectThread(), false);
    }

    private static void trackActionBar(Set<ViewGroup> actionBars, ViewGroup viewGroup) {
        if (viewGroup == null) {
            return;
        }

        synchronized (actionBars) {
            actionBars.add(viewGroup);
        }
    }

    private static void refreshActionBars(
            Set<ViewGroup> actionBars,
            final boolean showOnSurface,
            final boolean keepOrderStable
    ) {
        ArrayList<ViewGroup> snapshot;
        synchronized (actionBars) {
            snapshot = new ArrayList<>(actionBars);
        }

        for (final ViewGroup viewGroup : snapshot) {
            if (viewGroup == null) {
                continue;
            }

            Runnable refresh = new Runnable() {
                @Override
                public void run() {
                    addToActionBar(viewGroup, showOnSurface);
                    if (keepOrderStable) {
                        keepActionBarButtonOrderStable(viewGroup);
                    }
                }
            };

            if (Looper.myLooper() == Looper.getMainLooper()) {
                refresh.run();
            } else if (!viewGroup.post(refresh)) {
                refresh.run();
            }
        }
    }

    private static void addToActionBar(ViewGroup viewGroup, boolean showOnSurface) {
        try {
            if (viewGroup == null) {
                return;
            }

            View existingView = UI.findDirectChildWithTag(viewGroup, ACTION_BAR_VIEW_TAG);
            if (!shouldShow(showOnSurface)) {
                removeExisting(existingView);
                return;
            }

            if (iconResId() == 0) {
                removeExisting(existingView);
                return;
            }

            if (existingView instanceof ImageView) {
                ImageView imageView = (ImageView) existingView;
                registerButton(imageView);
                updateIcon(imageView);
                return;
            }

            ImageView imageView = UI.addImageViewToViewGroup(viewGroup, currentIcon(), null);
            if (imageView == null) {
                return;
            }

            prepareActionBarButton(imageView);
        } catch (Exception e) {
            Logger.printException(() -> "add ghost mode quick toggle failure", e);
        }
    }

    public static void keepActionBarButtonOrderStable(final ViewGroup viewGroup) {
        if (viewGroup == null) {
            return;
        }

        if (hasMeasuredDirectChild(viewGroup)) {
            stabilizeActionBarOrder(viewGroup);
        }

        postActionBarOrderUpdate(viewGroup);
    }

    private static void postActionBarOrderUpdate(final ViewGroup viewGroup) {
        synchronized (PENDING_ACTION_BAR_ORDER_UPDATES) {
            if (PENDING_ACTION_BAR_ORDER_UPDATES.contains(viewGroup)) {
                return;
            }
            PENDING_ACTION_BAR_ORDER_UPDATES.add(viewGroup);
        }

        boolean posted = viewGroup.post(new Runnable() {
            @Override
            public void run() {
                try {
                    stabilizeActionBarOrder(viewGroup);
                } finally {
                    synchronized (PENDING_ACTION_BAR_ORDER_UPDATES) {
                        PENDING_ACTION_BAR_ORDER_UPDATES.remove(viewGroup);
                    }
                }
            }
        });
        if (!posted) {
            synchronized (PENDING_ACTION_BAR_ORDER_UPDATES) {
                PENDING_ACTION_BAR_ORDER_UPDATES.remove(viewGroup);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static Object addDirectInboxActionModel(Object actionBarModel) {
        try {
            if (actionBarModel == null || !shouldShow(Pref.showGhostModeQuickToggleDirectInbox())) {
                return actionBarModel;
            }

            Class<?> modelClass = actionBarModel.getClass();
            ClassLoader classLoader = modelClass.getClassLoader();
            Class<?> leadingClass = Class.forName(DIRECT_INBOX_LEADING_CLASS_NAME, false, classLoader);
            Class<?> titleClass = Class.forName(DIRECT_INBOX_TITLE_CLASS_NAME, false, classLoader);
            Class<?> actionClass = Class.forName(DIRECT_INBOX_ACTION_CLASS_NAME, false, classLoader);
            Class<?> placementClass = Class.forName(DIRECT_INBOX_PLACEMENT_CLASS_NAME, false, classLoader);
            Object endPlacement = Enum.valueOf((Class<Enum>) placementClass.asSubclass(Enum.class), "END");
            Field leadingField = declaredUniqueFieldByType(modelClass, leadingClass);
            Field titleField = declaredUniqueFieldByType(modelClass, titleClass);
            Field actionsField = declaredActionListField(modelClass, actionBarModel, actionClass);

            Object leading = leadingField.get(actionBarModel);
            Object title = titleField.get(actionBarModel);
            Object actionsObject = actionsField.get(actionBarModel);
            if (!(actionsObject instanceof List)) {
                return actionBarModel;
            }

            List<?> actions = (List<?>) actionsObject;
            if (containsDirectInboxAction(actions)) {
                return actionBarModel;
            }

            ArrayList<Object> newActions = new ArrayList<>(actions);
            Object pikoAction = createDirectInboxAction(
                    classLoader,
                    actionClass,
                    placementClass,
                    endPlacement
            );
            if (pikoAction == null) {
                return actionBarModel;
            }

            int insertIndex = findDirectInboxInsertIndex(
                    newActions,
                    actionClass,
                    placementClass,
                    endPlacement
            );
            newActions.add(insertIndex, pikoAction);
            synchronized (DIRECT_INBOX_ACTIONS) {
                DIRECT_INBOX_ACTIONS.add(pikoAction);
            }

            Constructor<?> constructor = modelClass.getDeclaredConstructor(
                    leadingClass,
                    titleClass,
                    List.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(leading, title, newActions);
        } catch (Exception e) {
            Logger.printException(() -> "add direct inbox ghost mode action failure", e);
            return actionBarModel;
        }
    }

    private static Object createDirectInboxAction(
            final ClassLoader classLoader,
            Class<?> actionClass,
            Class<?> placementClass,
            Object placement
    ) {
        try {
            int iconResId = iconResId();
            if (iconResId == 0) {
                return null;
            }

            Class<?> function0Class = Class.forName("kotlin.jvm.functions.Function0", false, classLoader);
            Class<?> function1Class = Class.forName("kotlin.jvm.functions.Function1", false, classLoader);

            Object clickAction = Proxy.newProxyInstance(
                    classLoader,
                    new Class<?>[]{function1Class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            if (isObjectMethod(method)) {
                                return handleObjectMethod(proxy, method, args);
                            }

                            if (isInvokeCall(method, args, 1)) {
                                toggle();
                                return kotlinUnit(classLoader);
                            }

                            return null;
                        }
                    }
            );
            Object bindAction = Proxy.newProxyInstance(
                    classLoader,
                    new Class<?>[]{function1Class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            if (isObjectMethod(method)) {
                                return handleObjectMethod(proxy, method, args);
                            }

                            if (isInvokeCall(method, args, 1)) {
                                bindDirectInboxButton(args[0]);
                                return kotlinUnit(classLoader);
                            }

                            return null;
                        }
                    }
            );

            Constructor<?> constructor = actionClass.getDeclaredConstructor(
                    placementClass,
                    Integer.class,
                    function0Class,
                    function1Class,
                    function1Class,
                    int.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    placement,
                    titleResId(),
                    null,
                    clickAction,
                    bindAction,
                    iconResId
            );
        } catch (Exception e) {
            Logger.printException(() -> "create direct inbox ghost mode action failure", e);
            return null;
        }
    }

    private static void bindDirectInboxButton(Object view) {
        if (!(view instanceof ImageView)) {
            return;
        }

        ImageView imageView = (ImageView) view;
        registerButton(imageView);
        updateIcon(imageView);
        imageView.setContentDescription(str("piko_ghost_modes_quick_toggle"));
        allowTranslatedButtonOverflow(imageView);
        applyDirectInboxTranslation(imageView);
    }

    private static void applyDirectInboxTranslation(ImageView imageView) {
        int offset = dp(imageView.getContext(), DIRECT_INBOX_MODEL_OFFSET_DP);
        imageView.setTranslationX(imageView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? offset : -offset);
    }

    // The inbox action slot clips translated icons unless the nearby parent chain allows overflow.
    private static void allowTranslatedButtonOverflow(View view) {
        ViewParent parent = view.getParent();
        for (int depth = 0; parent instanceof ViewGroup && depth < 3; depth++) {
            ViewGroup viewGroup = (ViewGroup) parent;
            viewGroup.setClipChildren(false);
            viewGroup.setClipToPadding(false);
            parent = viewGroup.getParent();
        }
    }

    private static void prepareActionBarButton(final ImageView imageView) {
        imageView.setTag(ACTION_BAR_VIEW_TAG);
        imageView.setContentDescription(str("piko_ghost_modes_quick_toggle"));
        registerButton(imageView);
        updateIcon(imageView);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle();
            }
        });
    }

    private static void toggle() {
        try {
            boolean enabled = !Pref.getTurnOnAllGhostModes();
            Pref.setTurnOnAllGhostModes(enabled);
            updateAllIcons(enabled);

            String toastStr = enabled ? str("piko_ghost_modes_on") : str("piko_ghost_modes_default");
            Utils.showToastShort(toastStr);
        } catch (Exception ex) {
            Logger.printException(() -> "ghost icon click failed: ", ex);
        }
    }

    private static boolean shouldShow() {
        return Pref.enableGhostModeQuickToggle() && SettingsStatus.ghostSection();
    }

    private static boolean shouldShow(boolean showOnSurface) {
        return showOnSurface && shouldShow();
    }

    private static String currentIcon() {
        return currentIcon(Pref.getTurnOnAllGhostModes());
    }

    private static String currentIcon(boolean enabled) {
        return enabled ? HIDE_ICON_NAME : SHOW_ICON_NAME;
    }

    private static void updateIcon(ImageView imageView) {
        updateIcon(imageView, Pref.getTurnOnAllGhostModes());
    }

    private static void updateIcon(ImageView imageView, boolean enabled) {
        UI.setThemedIcon(imageView, currentIcon(enabled));
        imageView.setSelected(enabled);
        imageView.setActivated(enabled);
        imageView.refreshDrawableState();
        imageView.invalidate();
    }

    private static void updateAllIcons(boolean enabled) {
        synchronized (BUTTONS) {
            for (int i = BUTTONS.size() - 1; i >= 0; i--) {
                ImageView button = BUTTONS.get(i).get();
                if (button == null || button.getParent() == null) {
                    BUTTONS.remove(i);
                    continue;
                }

                updateIcon(button, enabled);
            }
        }
    }

    private static void registerButton(ImageView imageView) {
        synchronized (BUTTONS) {
            for (int i = BUTTONS.size() - 1; i >= 0; i--) {
                ImageView button = BUTTONS.get(i).get();
                if (button == null || button.getParent() == null) {
                    BUTTONS.remove(i);
                    continue;
                }

                if (button == imageView) {
                    return;
                }
            }

            BUTTONS.add(new WeakReference<>(imageView));
        }
    }

    private static boolean containsDirectInboxAction(List<?> actions) {
        synchronized (DIRECT_INBOX_ACTIONS) {
            for (Object action : actions) {
                if (DIRECT_INBOX_ACTIONS.contains(action)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static int findDirectInboxInsertIndex(
            List<?> actions,
            Class<?> actionClass,
            Class<?> placementClass,
            Object endPlacement
    ) {
        try {
            Field placementField = declaredUniqueFieldByType(actionClass, placementClass);
            for (int i = actions.size() - 1; i >= 0; i--) {
                Object action = actions.get(i);
                if (actionClass.isInstance(action) && endPlacement.equals(placementField.get(action))) {
                    return i;
                }
            }
        } catch (Exception e) {
            Logger.printException(() -> "find direct inbox action insert index failure", e);
        }

        return Math.max(0, actions.size() - 1);
    }

    private static void stabilizeActionBarOrder(ViewGroup viewGroup) {
        View ghostButton = UI.findDirectChildWithTag(viewGroup, ACTION_BAR_VIEW_TAG);
        View settingsButton = UI.findDirectChildWithTag(viewGroup, UI.PIKO_SETTINGS_GEAR_TAG);
        if (ghostButton == null && settingsButton == null) {
            return;
        }

        View anchor = findTrailingNativeButton(viewGroup, ghostButton, settingsButton);
        removeDirectChild(viewGroup, ghostButton);
        removeDirectChild(viewGroup, settingsButton);

        int insertIndex = anchor != null && anchor.getParent() == viewGroup
                ? viewGroup.indexOfChild(anchor)
                : viewGroup.getChildCount();

        insertIndex = addDirectChild(viewGroup, ghostButton, insertIndex);
        addDirectChild(viewGroup, settingsButton, insertIndex);

        viewGroup.requestLayout();
        viewGroup.invalidate();
    }

    private static View findTrailingNativeButton(ViewGroup viewGroup, View ghostButton, View settingsButton) {
        View rightmostButton = findRightmostCompactNativeButton(viewGroup, ghostButton, settingsButton);
        if (rightmostButton != null) {
            return rightmostButton;
        }

        for (int i = viewGroup.getChildCount() - 1; i >= 0; i--) {
            View child = viewGroup.getChildAt(i);
            if (child != ghostButton && child != settingsButton) {
                return child;
            }
        }

        return null;
    }

    private static View findRightmostCompactNativeButton(ViewGroup viewGroup, View ghostButton, View settingsButton) {
        int maxSize = dp(viewGroup.getContext(), MAX_NATIVE_ANCHOR_SIZE_DP);
        View bestView = null;
        float bestRight = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (!isCompactNativeChild(child, ghostButton, settingsButton, maxSize)) {
                continue;
            }

            float right = child.getX() + child.getWidth();
            if (right > bestRight) {
                bestRight = right;
                bestView = child;
            }
        }

        return bestView;
    }

    private static boolean isCompactNativeChild(View child, View ghostButton, View settingsButton, int maxSize) {
        if (child == null || child == ghostButton || child == settingsButton) {
            return false;
        }

        if (child.getVisibility() != View.VISIBLE) {
            return false;
        }

        int width = child.getWidth();
        int height = child.getHeight();
        return width > 0 && height > 0 && width <= maxSize && height <= maxSize;
    }

    private static boolean hasMeasuredDirectChild(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child.getWidth() > 0 && child.getHeight() > 0) {
                return true;
            }
        }

        return false;
    }

    private static void removeDirectChild(ViewGroup viewGroup, View child) {
        if (child != null && child.getParent() == viewGroup) {
            viewGroup.removeView(child);
        }
    }

    private static int addDirectChild(ViewGroup viewGroup, View child, int requestedIndex) {
        if (child == null) {
            return requestedIndex;
        }

        int insertIndex = Math.max(0, Math.min(requestedIndex, viewGroup.getChildCount()));
        viewGroup.addView(child, insertIndex);
        return insertIndex + 1;
    }

    private static void removeExisting(View view) {
        if (view == null) {
            return;
        }

        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) parent;
            viewGroup.removeView(view);
        }
    }

    private static Field declaredUniqueFieldByType(Class<?> cls, Class<?> fieldType) throws NoSuchFieldException {
        Field match = null;
        for (Field field : cls.getDeclaredFields()) {
            if (fieldType.isAssignableFrom(field.getType())) {
                if (match != null) {
                    throw new NoSuchFieldException("Multiple " + fieldType.getName() + " fields in " + cls.getName());
                }
                match = field;
            }
        }

        if (match == null) {
            throw new NoSuchFieldException(fieldType.getName());
        }

        match.setAccessible(true);
        return match;
    }

    private static Field declaredActionListField(
            Class<?> cls,
            Object instance,
            Class<?> actionClass
    ) throws NoSuchFieldException, IllegalAccessException {
        Field onlyListField = null;
        Field onlyEmptyListField = null;
        int listFieldCount = 0;
        int emptyListFieldCount = 0;

        for (Field field : cls.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(field.getType())) {
                continue;
            }

            listFieldCount++;
            field.setAccessible(true);
            Object value = field.get(instance);
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                if (isNonEmptyActionList(list, directInboxActionListItemClass(actionClass))) {
                    return field;
                }

                if (list.isEmpty()) {
                    emptyListFieldCount++;
                    onlyEmptyListField = field;
                }
            }

            onlyListField = field;
        }

        if (emptyListFieldCount == 1 && onlyEmptyListField != null) {
            return onlyEmptyListField;
        }

        if (listFieldCount == 1 && onlyListField != null) {
            return onlyListField;
        }

        throw new NoSuchFieldException("action list in " + cls.getName());
    }

    private static Class<?> directInboxActionListItemClass(Class<?> actionClass) {
        for (Class<?> iface : actionClass.getInterfaces()) {
            String name = iface.getName();
            if (!name.startsWith("java.") && !name.startsWith("kotlin.")) {
                return iface;
            }
        }

        return actionClass;
    }

    private static boolean isNonEmptyActionList(List<?> list, Class<?> listItemClass) {
        if (list.isEmpty()) {
            return false;
        }

        boolean hasAction = false;
        for (Object item : list) {
            if (item == null) {
                continue;
            }

            if (!listItemClass.isInstance(item)) {
                return false;
            }

            hasAction = true;
        }

        return hasAction;
    }

    private static int iconResId() {
        return ResourceUtils.getIdentifier(ResourceType.DRAWABLE, currentIcon());
    }

    private static Integer titleResId() {
        int resId = ResourceUtils.getIdentifier(ResourceType.STRING, "piko_ghost_modes_quick_toggle");
        return resId == 0 ? null : resId;
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    private static boolean isObjectMethod(Method method) {
        return method.getDeclaringClass() == Object.class;
    }

    private static boolean isInvokeCall(Method method, Object[] args, int argumentCount) {
        return "invoke".equals(method.getName()) && args != null && args.length == argumentCount;
    }

    private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        if ("toString".equals(name)) {
            return "PikoGhostModeQuickToggleProxy";
        }

        if ("hashCode".equals(name)) {
            return System.identityHashCode(proxy);
        }

        if ("equals".equals(name)) {
            return args != null && args.length > 0 && proxy == args[0];
        }

        return null;
    }

    private static Object kotlinUnit(ClassLoader classLoader) {
        try {
            Class<?> unitClass = Class.forName("kotlin.Unit", false, classLoader);
            Field instanceField = unitClass.getField("INSTANCE");
            return instanceField.get(null);
        } catch (Exception ignored) {
            return null;
        }
    }

}
