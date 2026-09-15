/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.instagram.patches.dm;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.instagram.model.direct.DirectThreadKey;
import com.instagram.igds.components.button.IgdsButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static app.morphe.extension.instagram.utils.IgStr.str;
import app.morphe.extension.instagram.entity.ProfileInfo;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.InstagramButton;
import app.morphe.extension.instagram.entity.InstagramButtonStyleEnum;
import app.morphe.extension.shared.ui.Dim;

import app.morphe.extension.crimera.PikoUtils;

@SuppressWarnings({"unused", "deprecation"})
public final class HiddenChats {
    private static final String PREF = "piko_hidden_chats";
    private static final String DATA = "data";
    private static final String HIDDEN = "hidden";
    private static final Map<String,String> cache = new LinkedHashMap<>();
    private static boolean initialized;

    private HiddenChats() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        load();
        try {
            final Context c = PikoUtils.getContext();
            if (c != null) {
                c.getApplicationContext();
                android.app.Application app = (android.app.Application)c.getApplicationContext();
                app.registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
                    public void onActivityCreated(final Activity a, android.os.Bundle b) { attach(a); }
                    public void onActivityStarted(Activity a) { attach(a); }
                    public void onActivityResumed(Activity a) { attach(a); }
                    public void onActivityPaused(Activity a) {}
                    public void onActivityStopped(Activity a) {}
                    public void onActivitySaveInstanceState(Activity a, android.os.Bundle b) {}
                    public void onActivityDestroyed(Activity a) {}
                });
            }
        } catch (Throwable t) { PikoUtils.logger(t); }
    }

    private static void attach(final Activity a) {
        if (a == null || a.getWindow() == null) return;
        final View decor = a.getWindow().getDecorView();
        if (decor == null) return;
        decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                if (a.isFinishing() || isSettings(a)) return;
                if (!hasHidden()) return;
                try { filterViewTree(decor); } catch (Throwable ignored) {}
            }
        });
    }

    private static boolean isSettings(Activity a) {
        String n = a.getClass().getName();
        return n.contains("Settings") || n.contains("HiddenChats");
    }

    private static boolean hasHidden() { synchronized(cache) { return !cache.isEmpty(); } }

    private static final String SEP = "\u0001";

    public static synchronized void hideChatByName(Context context, String username, String fullName) {
        String primary = (fullName != null && !fullName.trim().isEmpty()) ? fullName.trim() : username;
        if (primary == null || primary.trim().isEmpty()) return;
        primary = primary.trim();
        String alt = (username != null) ? username.trim() : "";
        String value = primary;
        if (!alt.isEmpty() && !alt.equalsIgnoreCase(primary)) value = primary + SEP + alt;
        synchronized(cache) { cache.put(primary, value); save(); }
        toast(context, "Sohbet gizlendi");
    }

    public static void addHideButton(ViewGroup viewGroup, ProfileInfo profileInfo) {
        try {
            UserData userData = profileInfo.getUserData();
            String username = userData.getUsername();
            String fullName = userData.getFullName();

            Context context = viewGroup.getContext();
            InstagramButton button = new InstagramButton(context);
            button.setText(str("piko_hide_chat_button"));
            button.setStyle(InstagramButtonStyleEnum.SECONDARY);
            button.setOnClickListener(() -> hideChatByName(context, username, fullName));

            int marginPx = app.morphe.extension.shared.ui.Dim.dp12;
            button.setMargins(marginPx, marginPx, marginPx, marginPx);

            IgdsButton igdsButton = button.getIgdsButton();
            viewGroup.addView(igdsButton);
            igdsButton.bringToFront();
            viewGroup.requestLayout();
            viewGroup.invalidate();
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

    public static synchronized void hideChat(Context context, DirectThreadKey key, Object threadInfo) {
        if (key == null || key.A00 == null) return;
        String name = resolveName(threadInfo);
        if (name == null || name.trim().isEmpty()) name = "@" + key.A00;
        synchronized(cache) { cache.put(key.A00, name.trim()); save(); }
        toast(context, "Sohbet gizlendi");
    }

    public static synchronized void showChat(String threadId) {
        if (threadId == null) return;
        synchronized(cache) { cache.remove(threadId); save(); }
    }

    public static void openHiddenChats(final Context context) {
        if (context == null) return;
        final ArrayList<String> ids = new ArrayList<>();
        final ArrayList<String> names = new ArrayList<>();
        synchronized(cache) { for (Map.Entry<String,String> e: cache.entrySet()) { ids.add(e.getKey()); names.add(e.getValue()); } }

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * context.getResources().getDisplayMetrics().density);
        list.setPadding(pad, pad / 2, pad, pad / 2);

        if (names.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Gizlenmiş sohbet yok.");
            empty.setPadding(0, pad, 0, pad);
            list.addView(empty);
        } else {
            for (int i = 0; i < names.size(); i++) {
                final String id = ids.get(i);
                final LinearLayout row = new LinearLayout(context);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(0, pad / 2, 0, pad / 2);

                TextView name = new TextView(context);
                name.setText(ids.get(i));
                name.setTextSize(16);
                name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                Button show = new Button(context);
                show.setText("Göster");
                show.setOnClickListener(v -> {
                    showChat(id);
                    list.removeView(row);
                    if (list.getChildCount() == 0) {
                        TextView empty = new TextView(context);
                        empty.setText("Gizlenmiş sohbet yok.");
                        empty.setPadding(0, pad, 0, pad);
                        list.addView(empty);
                    }
                });

                row.addView(name);
                row.addView(show);
                list.addView(row);
            }
        }

        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        new AlertDialog.Builder(context)
                .setTitle("Gizli sohbetler")
                .setView(scroll)
                .setPositiveButton("Kapat", null)
                .show();
    }

    private static void filterViewTree(View root) {
        ArrayList<String> names = new ArrayList<>();
        synchronized(cache) { names.addAll(cache.values()); }
        filter(root, names);
    }

    private static boolean isHeaderLike(View v) {
        for (View p = v; p != null; ) {
            String cn = p.getClass().getName().toLowerCase();
            if (cn.contains("toolbar") || cn.contains("actionbar") || cn.contains("titlebar") || cn.contains("appbar")) {
                return true;
            }
            Object parentObj = p.getParent();
            if (!(parentObj instanceof View)) break;
            p = (View) parentObj;
        }
        return false;
    }

    private static boolean isNearTop(View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        float density = v.getResources().getDisplayMetrics().density;
        return loc[1] < (150 * density); // top ~150dp is where a thread's own header sits
    }

    private static boolean filter(View v, ArrayList<String> names) {
        if (v instanceof TextView) {
            CharSequence cs=((TextView)v).getText();
            if (cs != null) {
                String text=cs.toString().trim();
                for (String nameGroup:names) {
                    for (String name : nameGroup.split(SEP)) {
                        if (name.isEmpty()) continue;
                        if (text.equals(name) || text.equals("@"+name) || text.equals(name.replace("@", ""))) {
                            if (isHeaderLike(v) || isNearTop(v)) continue;
                            View row=findRow(v);
                            if (row!=null) hideRowCompletely(row);
                            return true;
                        }
                    }
                }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) filter(g.getChildAt(i), names);
        }
        return false;
    }

    private static void hideRowCompletely(View row) {
        row.setVisibility(View.GONE);
        ViewGroup.LayoutParams lp = row.getLayoutParams();
        if (lp != null) {
            lp.height = 0;
            row.setLayoutParams(lp);
        }
        row.measure(0, 0);
        View p = row;
        for (int i = 0; i < 3 && p.getParent() instanceof View; i++) {
            p = (View) p.getParent();
            p.requestLayout();
            p.invalidate();
        }
    }

    private static View findRow(View v) {
        View cur=v;
        View bestCandidate=null;
        int screenWidth = v.getResources().getDisplayMetrics().widthPixels;
        for(int i=0;i<12 && cur.getParent() instanceof View;i++) {
            View p=(View)cur.getParent();
            if (p.getVisibility()!=View.VISIBLE) return p;

            // Strongest signal: p's own parent is a RecyclerView/ListView/
            // AdapterView-like container -> p IS the actual list item,
            // covering the full clickable row (avatar, text, trailing icons).
            Object grandParentObj = p.getParent();
            if (grandParentObj instanceof View) {
                String gpClass = grandParentObj.getClass().getName().toLowerCase();
                if (gpClass.contains("recyclerview") || gpClass.contains("listview") || gpClass.contains("adapterview")) {
                    return p;
                }
            }

            int h=p.getHeight();
            int w=p.getWidth();
            if (h>=40 && h<=320 && w>screenWidth*0.6) {
                bestCandidate=p; // remember as fallback, keep looking for a stronger match
            }
            cur=p;
        }
        return bestCandidate!=null ? bestCandidate : v;
    }

    private static String resolveName(Object o) {
        if (o == null) return null;
        String best=null;
        try {
            Class<?> c=o.getClass();
            for(int depth=0;c!=null && depth<4;depth++,c=c.getSuperclass()) {
                for(Field f:c.getDeclaredFields()) {
                    try {
                        if (f.getType()!=String.class) continue;
                        f.setAccessible(true);
                        Object x=f.get(o); if(!(x instanceof String)) continue;
                        String s=((String)x).trim();
                        if(s.isEmpty() || s.length()>80 || s.matches("\\d+") || s.contains("http")) continue;
                        String fn=f.getName().toLowerCase();
                        if(fn.contains("title")||fn.contains("name")||fn.contains("username")||fn.contains("label")) return s;
                        if(best==null) best=s;
                    } catch(Throwable ignored){}
                }
            }
        } catch(Throwable ignored){}
        return best;
    }

    private static void load() {
        try {
            Context c=PikoUtils.getContext(); if(c==null)return;
            String s=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(DATA, "{}");
            JSONObject o=new JSONObject(s); JSONArray a=o.optJSONArray(HIDDEN); if(a==null)return;
            synchronized(cache){ cache.clear(); for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i); if(x!=null) cache.put(x.optString("id"),x.optString("name"));}}
        } catch(Throwable t){ PikoUtils.logger(t); }
    }

    private static void save() {
        try {
            Context c=PikoUtils.getContext(); if(c==null)return;
            JSONArray a=new JSONArray(); synchronized(cache){ for(Map.Entry<String,String> e:cache.entrySet()){JSONObject x=new JSONObject();x.put("id",e.getKey());x.put("name",e.getValue());a.put(x);} }
            JSONObject o=new JSONObject();o.put(HIDDEN,a);
            c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(DATA,o.toString()).apply();
        } catch(Throwable t){ PikoUtils.logger(t); }
    }

    private static void toast(Context c,String s){ try{ android.widget.Toast.makeText(c,s,android.widget.Toast.LENGTH_SHORT).show(); }catch(Throwable ignored){} }
}