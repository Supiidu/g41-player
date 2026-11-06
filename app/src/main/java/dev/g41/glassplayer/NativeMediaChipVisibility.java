package dev.g41.glassplayer;

import android.app.Application;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * v0.10-beta3: suppress ONLY the native compact media chip.
 *
 * Based on the exact SystemUI-original.apk decompilation from the Moto G41:
 *
 * AxDynamicBarKeyguardChipKt.AxDynamicBarKeyguardChip() renders the compact
 * content through AnimatedVisibility(boolean z2, Modifier pointerInput,...).
 * A separate AnimatedVisibility(MutableTransitionState, ...) handles the
 * expanded panel. We hook only the boolean overload and replace its visibility
 * argument with false when the native chip is displaying media and our custom
 * compact window OR expanded player is on screen. This prevents the original
 * chip reappearing behind the expanded player during the audio handoff.
 *
 * Crucially, Compose still invokes its OWN AnimatedVisibility implementation
 * and manages its composition groups, transitions and cleanup. We do NOT
 * call param.setResult() on any composable or alter a fullscreen ComposeView.
 */
final class NativeMediaChipVisibility {
    private static final String TAG = G41Hook.TAG;
    private static final String CHIP_CLASS =
            "com.android.systemui.axdynamicbar.ui.compose.AxDynamicBarKeyguardChipKt";
    private static final String SETTINGS_HIDE = "g41_glass_compact_hide_native";
    private static final ThreadLocal<Boolean> IN_NATIVE_MEDIA = new ThreadLocal<>();
    private static volatile long lastSuppressionLog;

    private NativeMediaChipVisibility() { }

    static void install(ClassLoader loader) {
        try {
            Class<?> chip = XposedHelpers.findClass(CHIP_CLASS, loader);
            Class<?> animation = XposedHelpers.findClass(
                    "androidx.compose.animation.AnimatedVisibilityKt", loader);
            int contextHooks = 0, visibilityHooks = 0;
            for (Method method : chip.getDeclaredMethods()) {
                if (!"AxDynamicBarKeyguardChip".equals(method.getName())
                        || !Modifier.isStatic(method.getModifiers())) continue;
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length < 3
                        || !parameters[0].getName().endsWith(".AxDynamicBarChipViewModel"))
                    continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        IN_NATIVE_MEDIA.set(Boolean.valueOf(
                                maySuppress(param.args[0], loader)));
                    }

                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        IN_NATIVE_MEDIA.remove();
                    }
                });
                contextHooks++;
            }
            for (Method method : animation.getDeclaredMethods()) {
                if (!"AnimatedVisibility".equals(method.getName())
                        || !Modifier.isStatic(method.getModifiers())) continue;
                Class<?>[] parameters = method.getParameterTypes();
                // Only the overload whose first parameter is boolean; do NOT
                // affect the MutableTransitionState used by expanded content.
                if (parameters.length < 3
                        || parameters[0] != boolean.class
                        || !parameters[1].getName().equals("androidx.compose.ui.Modifier"))
                    continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Boolean.TRUE.equals(IN_NATIVE_MEDIA.get())
                                || !Boolean.TRUE.equals(param.args[0])
                                || !directChipCaller()) return;
                        // Leave this function running; Compose performs its
                        // natural exit animation / disposes chip safely.
                        param.args[0] = Boolean.FALSE;
                        long now = SystemClock.uptimeMillis();
                        if (now - lastSuppressionLog > 4500L) {
                            lastSuppressionLog = now;
                            Log.i(TAG, "Compact v0.10-beta3: ocultando somente"
                                    + " AnimatedVisibility do chip nativo de MIDIA");
                        }
                    }
                });
                visibilityHooks++;
            }
            Log.i(TAG, "Compact native v0.10-beta3: hooks Compose="
                    + contextHooks + " AnimatedVisibility=" + visibilityHooks
                    + " (opt-in por " + SETTINGS_HIDE + ")");
        } catch (Throwable t) {
            // No changes to methods if this class is absent; native chip stays.
            Log.w(TAG, "Compact native beta2: ROM nao corresponde ao APK analisado;"
                    + " tentando preservar InfinityBar original", t);
        }
    }

    private static boolean maySuppress(Object viewModel, ClassLoader loader) {
        // The expanded glass player replaces the compact preview. Keep the
        // ROM chip hidden for BOTH states, including the handoff between them.
        if (viewModel == null || Looper.myLooper() != Looper.getMainLooper()
                || (!CompactBarOverlay.isVisible() && !GlassOverlay.isOpen())) {
            return false;
        }
        try {
            Class<?> activityThread = XposedHelpers.findClass(
                    "android.app.ActivityThread", loader);
            Object application = XposedHelpers.callStaticMethod(
                    activityThread, "currentApplication");
            if (!(application instanceof Application)) return false;
            Context context = (Context) application;
            if (Settings.Global.getInt(context.getContentResolver(),
                    SETTINGS_HIDE, 0) != 1
                    || Settings.Global.getInt(context.getContentResolver(),
                    "g41_glass_compact_preview", 0) != 1
                    || Settings.Global.getInt(context.getContentResolver(),
                    G41Hook.SETTING, 0) != 1) return false;
            KeyguardManager keyguard = (KeyguardManager)
                    context.getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguard == null || !keyguard.isKeyguardLocked()) return false;

            // ViewModel.chipState includes notificationAlert and selected event.
            // A notificationAlert has priority in the actual ROM compositor.
            Object chipFlow = XposedHelpers.getObjectField(viewModel, "chipState");
            Object state = XposedHelpers.callMethod(chipFlow, "getValue");
            if (state == null) return false;
            Object alert = XposedHelpers.getObjectField(state, "notificationAlert");
            if (alert != null) return false;
            Object shown = XposedHelpers.getObjectField(state, "event");
            if (!isMedia(shown)) return false;

            Object expansion = XposedHelpers.getObjectField(viewModel,
                    "keyguardExpansion");
            return G41Hook.selectedMedia(expansion) != null;
        } catch (Throwable t) {
            // If ROM field layout changes, never hide the native chip.
            return false;
        }
    }

    private static boolean isMedia(Object event) {
        return event != null
                && event.getClass().getName().endsWith("IslandEvent$Media");
    }

    /**
     * The chip's Composable has other nested calls. Guard on the nearest
     * ROM-specific frame so we do not hide AnimatedVisibility in its children,
     * in other screens or in notification renderers.
     */
    private static boolean directChipCaller() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String cls = frame.getClassName();
            if (CHIP_CLASS.equals(cls)) {
                return "AxDynamicBarKeyguardChip".equals(frame.getMethodName());
            }
            if (cls.startsWith(CHIP_CLASS + "$")) {
                return false;
            }
        }
        return false;
    }
}
