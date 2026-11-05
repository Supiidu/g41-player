package dev.g41.glassplayer;

import android.content.Context;
import android.content.res.Resources;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.robv.android.xposed.XposedHelpers;

/**
 * v0.8: attempts to hide ONLY a dedicated, compact InfinityBar View.
 *
 * Android Compose nodes are not necessarily Android Views. Never hide a fullscreen
 * AndroidComposeView or a keyguard root to remove a nested chip. If the ROM
 * renders the chip inside a fullscreen Compose root, report candidates instead
 * of touching unrelated UI; the next step is a targeted Compose hook.
 */
public final class CompactBarVisibility {
    private static final String TAG = "G41GlassPlayer";
    private static final Map<View, Float> ORIGINAL_ALPHA = new IdentityHashMap<>();
    private static final List<View> COMPOSE_CANDIDATES = new ArrayList<>();
    private static int observed;
    private static int logged;

    private CompactBarVisibility() { }

    static void hide(Context context, View glassWindow) {
        restore();
        if (Settings.Global.getInt(context.getContentResolver(),
                "g41_glass_hide_compact", 1) != 1) {
            Log.i(TAG, "Compact v0.8: ocultacao desativada por configuracao");
            return;
        }
        observed = 0;
        logged = 0;
        COMPOSE_CANDIDATES.clear();
        try {
            Class<?> globalClass = Class.forName("android.view.WindowManagerGlobal");
            Object global = XposedHelpers.callStaticMethod(globalClass, "getInstance");
            Object roots;
            try {
                roots = XposedHelpers.callMethod(global, "getWindowViews");
            } catch (Throwable e) {
                roots = XposedHelpers.getObjectField(global, "mViews");
            }
            if (!(roots instanceof List)) {
                Log.w(TAG, "Compact v0.8: WindowManager nao retornou lista de Views");
                return;
            }
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            final int sw = metrics.widthPixels;
            final int sh = metrics.heightPixels;
            final float density = metrics.density;
            for (Object entry : (List<?>) roots) {
                if (!(entry instanceof View) || entry == glassWindow) continue;
                inspect((View) entry, glassWindow, sw, sh, density, 0);
            }
            // Optional, deliberately opt-in fallback. Only a single tight ComposeView
            // below mid-screen is eligible. Not safe to hide a fullscreen Compose root.
            boolean aggressive = Settings.Global.getInt(context.getContentResolver(),
                    "g41_glass_hide_compact_aggressive", 0) == 1;
            if (ORIGINAL_ALPHA.isEmpty() && aggressive && COMPOSE_CANDIDATES.size() == 1) {
                View target = COMPOSE_CANDIDATES.get(0);
                conceal(target, "compose-candidato-unico");
            }
            Log.i(TAG, "Compact v0.8: candidatos nomeados=" + observed
                    + " compose-pequenos=" + COMPOSE_CANDIDATES.size()
                    + " ocultados=" + ORIGINAL_ALPHA.size()
                    + " agressivo=" + aggressive);
            if (ORIGINAL_ALPHA.isEmpty()) {
                Log.w(TAG, "Compact v0.8: nenhuma View isolada. A barra pode ser um no Compose fullscreen; nao ocultarei a lockscreen inteira.");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Compact v0.8: erro de leitura, mantendo barra original", t);
            restore();
        } finally {
            COMPOSE_CANDIDATES.clear();
        }
    }

    private static void inspect(View view, View glassWindow,
                                int screenW, int screenH, float density, int depth) {
        if (view == null || view == glassWindow || depth > 32 || !view.isShown()) return;
        int w = view.getWidth();
        int h = view.getHeight();
        if (w <= 0 || h <= 0) return;
        String name = view.getClass().getName();
        String cls = name.toLowerCase(Locale.ROOT);
        String id = "";
        try {
            if (view.getId() != View.NO_ID) {
                Resources resources = view.getResources();
                id = resources.getResourceEntryName(view.getId()).toLowerCase(Locale.ROOT);
            }
        } catch (Throwable ignored) { }
        int[] xy = {0, 0};
        try { view.getLocationOnScreen(xy); } catch (Throwable ignored) { }
        int minWidth = Math.round(110 * density);
        int minHeight = Math.round(25 * density);
        int maxHeight = Math.round(200 * density);
        boolean tight = w >= minWidth && w <= screenW && h >= minHeight
                && h <= maxHeight && xy[1] >= screenH * 0.50f;
        boolean named = containsAny(id, "ax_dynamic", "dynamicbar", "dynamic_bar",
                "infinitybar", "infinity_bar", "island_chip", "keyguard_chip", "keyguard_island")
                || containsAny(cls, "axdynamicbar", "infinitybar");
        boolean compose = (cls.endsWith("composeview") || cls.endsWith("androidcomposeview"));
        if (named) {
            observed++;
            if (logged++ < 36) {
                Log.i(TAG, "Compact v0.8: VIEW class=" + name + " id=" + id
                        + " xy=" + xy[0] + "," + xy[1]
                        + " size=" + w + "x" + h + " isolada=" + tight);
            }
        }
        if (tight && named) {
            conceal(view, "nome-identificado");
            return;
        }
        // Diagnostic for Compose. Never hide by default: might share its root
        // with clock, wallpaper, notifications or lockscreen actions.
        if (tight && compose && COMPOSE_CANDIDATES.size() < 15) {
            COMPOSE_CANDIDATES.add(view);
            if (logged++ < 36) {
                Log.i(TAG, "Compact v0.8: COMPOSE candidato class=" + name
                        + " id=" + id + " xy=" + xy[0] + "," + xy[1]
                        + " size=" + w + "x" + h);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                inspect(group.getChildAt(i), glassWindow, screenW, screenH, density, depth + 1);
            }
        }
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null || value.isEmpty()) return false;
        for (String s : needles) if (value.contains(s)) return true;
        return false;
    }

    private static void conceal(View view, String reason) {
        if (ORIGINAL_ALPHA.containsKey(view)) return;
        ORIGINAL_ALPHA.put(view, view.getAlpha());
        view.setAlpha(0f);
        Log.i(TAG, "Compact v0.8: OCULTADO " + reason + " " + view.getClass().getName());
    }

    static void restore() {
        if (ORIGINAL_ALPHA.isEmpty()) return;
        int restored = 0;
        for (Map.Entry<View, Float> entry : ORIGINAL_ALPHA.entrySet()) {
            try {
                entry.getKey().setAlpha(entry.getValue());
                restored++;
            } catch (Throwable t) {
                Log.w(TAG, "Compact v0.8: nao foi possivel restaurar view", t);
            }
        }
        ORIGINAL_ALPHA.clear();
        Log.i(TAG, "Compact v0.8: RESTAURADO " + restored + " Views");
    }
}
