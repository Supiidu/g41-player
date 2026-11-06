package dev.g41.glassplayer;

import android.app.Application;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import java.util.List;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** G41 Glass Player - media expansion plus an opt-in compact Compose chip preview. */
public final class G41Hook implements IXposedHookLoadPackage {
    static final String TAG = "G41GlassPlayer";
    static final String SETTING = "g41_glass_player_enabled";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String EXPANSION =
            "com.android.systemui.axdynamicbar.ui.AxDynamicBarKeyguardExpansion";
    private static final String CHIP_COMPOSABLE =
            "com.android.systemui.axdynamicbar.ui.compose.AxDynamicBarKeyguardChipKt";
    private static long lastCompactObservation;

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
                            // Never suppress native expansion unless our window succeeded.
                            CompactBarOverlay.expandedChanged();
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
        installCompactComposeProbe(lp.classLoader);
    }

    /**
     * Read-only hook on the actual ROM compact chip's Compose body.
     * Never skip the body, replace its Modifier or hide Compose root Views.
     * A separate opt-in overlay is created only after a confirmed pinned
     * IslandEvent.Media and while the keyguard is locked.
     */
    private static void installCompactComposeProbe(ClassLoader loader) {
        try {
            Class<?> chip = XposedHelpers.findClass(CHIP_COMPOSABLE, loader);
            int count = 0;
            for (Method method : chip.getDeclaredMethods()) {
                if (!method.getName().contains("KeyguardChipBody")
                        || !Modifier.isStatic(method.getModifiers())) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length < 7 || !types[0].getName().endsWith(".IslandEvent"))
                    continue;
                int modelIndex = -1;
                for (int i = 1; i < types.length; i++) {
                    if (types[i].getName().endsWith(".AxDynamicBarChipViewModel")) {
                        modelIndex = i;
                        break;
                    }
                }
                if (modelIndex < 0) continue;
                final int idx = modelIndex;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (Looper.myLooper() != Looper.getMainLooper()) return;
                            Object current = param.args[0];
                            if (!isMedia(current)) return;
                            long now = SystemClock.uptimeMillis();
                            if (now - lastCompactObservation < 500L) return;
                            lastCompactObservation = now;
                            Object app = XposedHelpers.callStaticMethod(
                                    XposedHelpers.findClass("android.app.ActivityThread",
                                            loader), "currentApplication");
                            if (!(app instanceof Application)) return;
                            Context context = (Context) app;
                            if (Settings.Global.getInt(context.getContentResolver(),
                                    "g41_glass_compact_preview", 0) != 1) return;
                            Object viewModel = param.args[idx];
                            if (viewModel == null) return;
                            Object expansion = XposedHelpers.getObjectField(
                                    viewModel, "keyguardExpansion");
                            Object pinned = selectedMedia(expansion);
                            if (pinned != null) {
                                // Do not add a new WindowManager view reentrantly
                                // during the Compose render pass. Defer to next loop.
                                new android.os.Handler(Looper.getMainLooper()).post(() ->
                                        CompactBarOverlay.observe(context, pinned, expansion));
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "Compact probe: falha isolada; Compose original intacto", t);
                        }
                    }
                });
                count++;
            }
            Log.i(TAG, "Compact probe v0.10: composables identificados=" + count
                    + " (overlay opt-in; nativo preservado)");
        } catch (Throwable t) {
            Log.w(TAG, "Compact probe v0.10: classe nao encontrada; InfinityBar nativa", t);
        }
    }

    static Object selectedMedia(Object expansion) {
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
