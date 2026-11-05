package dev.g41.glassplayer;

import android.app.Application;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** G41 Glass Player v0.5 — centered player, live wallpaper and dynamic waves, on top of Infinity X keyguard. */
public final class G41Hook implements IXposedHookLoadPackage {
    static final String TAG = "G41GlassPlayer";
    static final String SETTING = "g41_glass_player_enabled";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String EXPANSION =
            "com.android.systemui.axdynamicbar.ui.AxDynamicBarKeyguardExpansion";

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!SYSTEMUI.equals(lp.packageName)) return;
        Log.i(TAG, "v0.5 Live Waves iniciada em " + lp.processName);
        try {
            XposedHelpers.findAndHookMethod(EXPANSION, lp.classLoader, "expand",
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    // Keep the original expand() as fallback if anything fails.
                    try {
                        if (Looper.myLooper() != Looper.getMainLooper()) return;
                        Class<?> at = XposedHelpers.findClass(
                                "android.app.ActivityThread", lp.classLoader);
                        Object current = XposedHelpers.callStaticMethod(at, "currentApplication");
                        if (!(current instanceof Application)) return;
                        Context app = (Application) current;
                        if (Settings.Global.getInt(app.getContentResolver(), SETTING, 0) != 1)
                            return;
                        KeyguardManager km = (KeyguardManager)
                                app.getSystemService(Context.KEYGUARD_SERVICE);
                        if (km != null && !km.isKeyguardLocked()) return;
                        Object media = selectedMedia(param.thisObject);
                        if (media == null) return;
                        if (GlassOverlay.showOrToggle(app, media)) {
                            // Only suppress native expansion if our window actually opened/closed.
                            param.setResult(null);
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "v0.5: falha de overlay, InfinityBar original preservada", t);
                    }
                }
            });
            Log.i(TAG, "Hook expand() v0.5 instalado; ativacao: settings put global "
                    + SETTING + " 1");
        } catch (Throwable t) {
            Log.e(TAG, "Hook expand() indisponivel. Modulo nao interfere no SystemUI.", t);
        }
    }

    private static Object selectedMedia(Object expansion) {
        try {
            Object interactor = XposedHelpers.getObjectField(expansion, "interactor");
            Object flow = XposedHelpers.getObjectField(interactor, "uiState");
            Object stateFlow;
            try { stateFlow = XposedHelpers.getObjectField(flow, "$$delegate_0"); }
            catch (Throwable ignored) { stateFlow = flow; }
            Object state = XposedHelpers.callMethod(stateFlow, "getValue");
            if (state == null) return null;
            Object value = XposedHelpers.getObjectField(state, "events");
            if (!(value instanceof List)) return null;
            List<?> events = (List<?>) value;
            if (events.isEmpty()) return null;
            int index = XposedHelpers.getIntField(state, "pinnedEventIndex");
            Object chosen = index >= 0 && index < events.size() ? events.get(index)
                    : events.get(0);
            // Do not swallow the expand action for timers, notifications, etc.
            if (isMedia(chosen)) return chosen;
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "Evento de midia nao identificado; expand() original", t);
            return null;
        }
    }

    private static boolean isMedia(Object event) {
        return event != null && event.getClass().getName().endsWith("IslandEvent$Media");
    }
}
