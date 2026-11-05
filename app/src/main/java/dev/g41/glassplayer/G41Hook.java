package dev.g41.glassplayer;

import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Read-only diagnostics for the InfinityBar on the Motorola G41.
 * IMPORTANT: Never setResult(), setThrowable() or change method arguments here.
 */
public final class G41Hook implements IXposedHookLoadPackage {
    private static final String TAG = "G41GlassPlayer";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String KEYGUARD_COMPOSE =
            "com.android.systemui.axdynamicbar.ui.compose.KeyguardExpandedContentKt";
    private static final String EXPANSION =
            "com.android.systemui.axdynamicbar.ui.AxDynamicBarKeyguardExpansion";

    private static volatile long lastMediaLogMs;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SYSTEMUI.equals(lpparam.packageName)) return;
        Log.i(TAG, "Modulo carregado em " + lpparam.processName);

        try {
            Class<?> compose = XposedHelpers.findClassIfExists(KEYGUARD_COMPOSE, lpparam.classLoader);
            if (compose == null) {
                Log.w(TAG, "Classe Compose da InfinityBar nao encontrada");
            } else {
                int hooks = 0;
                for (Method method : compose.getDeclaredMethods()) {
                    if (!"KeyguardMediaPanel".equals(method.getName())) continue;
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length != 4 || params[3] != int.class) continue;
                    if (!params[0].getName().endsWith("IslandEvent$Media")) continue;
                    if (!params[1].getName().endsWith("IslandActions")) continue;
                    if (!params[2].getName().equals("androidx.compose.runtime.Composer")) continue;

                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            long now = SystemClock.uptimeMillis();
                            if (now - lastMediaLogMs > 2500L) {
                                lastMediaLogMs = now;
                                Log.i(TAG, "KeyguardMediaPanel executado (somente diagnostico)");
                            }
                        }
                    });
                    hooks++;
                }
                Log.i(TAG, "Hooks KeyguardMediaPanel instalados: " + hooks);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Falha ao instalar hook de media", t);
        }

        try {
            XposedHelpers.findAndHookMethod(EXPANSION, lpparam.classLoader,
                    "expand", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Log.i(TAG, "AxDynamicBarKeyguardExpansion.expand() chamado");
                        }
                    });
            Log.i(TAG, "Hook de expand() instalado");
        } catch (Throwable t) {
            Log.e(TAG, "Falha ao instalar hook de expand()", t);
        }
    }
}
