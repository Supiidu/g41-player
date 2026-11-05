package dev.g41.glassplayer;

import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * G41 Glass Player v0.2 - selective shape experiment.
 * Only alters the shape argument of the inner media controls Surface
 * while KeyguardMediaPanel is synchronously composing.
 * No global fields are changed, and no original method is skipped.
 */
public final class G41Hook implements IXposedHookLoadPackage {
    private static final String TAG = "G41GlassPlayer";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String COMPOSE =
            "com.android.systemui.axdynamicbar.ui.compose.KeyguardExpandedContentKt";
    private static final String TOKENS =
            "com.android.systemui.axdynamicbar.shared.IslandContentTokensKt";
    private static final String EXPANSION =
            "com.android.systemui.axdynamicbar.ui.AxDynamicBarKeyguardExpansion";

    private static final ThreadLocal<Integer> MEDIA_DEPTH = new ThreadLocal<Integer>() {
        @Override protected Integer initialValue() { return 0; }
    };
    private static volatile long lastMediaLogMs;
    private static volatile long lastShapeLogMs;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SYSTEMUI.equals(lpparam.packageName)) return;
        Log.i(TAG, "v0.2 carregada em " + lpparam.processName);

        // Read existing Compose Shape objects; do not mutate final static fields.
        Object shapeCard = null;
        Object shapeSmall = null;
        try {
            Class<?> tokens = XposedHelpers.findClass(TOKENS, lpparam.classLoader);
            shapeCard = XposedHelpers.getStaticObjectField(tokens, "ShapeCard");
            shapeSmall = XposedHelpers.getStaticObjectField(tokens, "ShapeSm");
            Log.i(TAG, "Shapes obtidos: ShapeCard=28dp, ShapeSm=12dp");
        } catch (Throwable t) {
            Log.e(TAG, "Nao consegui obter ShapeCard/ShapeSm; somente diagnostico", t);
        }

        // Hook only the matching Surface overload. It has the shape as arg[1].
        // Its hook runs for other Surfaces too, but performs only one cheap
        // ThreadLocal check unless KeyguardMediaPanel is composing now.
        if (shapeCard != null && shapeSmall != null) {
            installSelectiveSurfaceHook(lpparam.classLoader, shapeCard, shapeSmall);
        }

        // Wrap synchronous composition so only the media panel can be affected.
        try {
            Class<?> clazz = XposedHelpers.findClass(COMPOSE, lpparam.classLoader);
            int hooks = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"KeyguardMediaPanel".equals(method.getName())) continue;
                Class<?>[] p = method.getParameterTypes();
                if (p.length != 4 || p[3] != int.class) continue;
                if (!p[0].getName().endsWith("IslandEvent$Media")) continue;
                if (!p[1].getName().endsWith("IslandActions")) continue;
                if (!"androidx.compose.runtime.Composer".equals(p[2].getName())) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        MEDIA_DEPTH.set(MEDIA_DEPTH.get() + 1);
                        long now = SystemClock.uptimeMillis();
                        if (now - lastMediaLogMs > 2500L) {
                            lastMediaLogMs = now;
                            Log.i(TAG, "KeyguardMediaPanel executado");
                        }
                    }
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        MEDIA_DEPTH.set(Math.max(0, MEDIA_DEPTH.get() - 1));
                    }
                });
                hooks++;
            }
            Log.i(TAG, "Hooks KeyguardMediaPanel instalados: " + hooks);
        } catch (Throwable t) {
            Log.e(TAG, "Falha no hook de KeyguardMediaPanel", t);
        }

        try {
            XposedHelpers.findAndHookMethod(EXPANSION, lpparam.classLoader,
                    "expand", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            Log.i(TAG, "AxDynamicBarKeyguardExpansion.expand() chamado");
                        }
                    });
        } catch (Throwable t) {
            Log.e(TAG, "Falha no hook de expand()", t);
        }
    }

    private static void installSelectiveSurfaceHook(ClassLoader loader,
                                                    final Object shapeCard,
                                                    final Object shapeSmall) {
        try {
            Class<?> surfaceKt = XposedHelpers.findClass(
                    "androidx.compose.material3.SurfaceKt", loader);
            List<Method> candidates = new ArrayList<>();
            for (Method m : surfaceKt.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                // Decompiled call: Surface(Modifier, Shape, long, long,
                //                         float, float, Function2, Composer, int, int)
                if (!m.getName().startsWith("Surface") || p.length != 10) continue;
                if (!"androidx.compose.ui.Modifier".equals(p[0].getName())) continue;
                if (!"androidx.compose.ui.graphics.Shape".equals(p[1].getName())) continue;
                if (p[2] != long.class || p[3] != long.class) continue;
                if (p[4] != float.class || p[5] != float.class) continue;
                if (!"kotlin.jvm.functions.Function2".equals(p[6].getName())) continue;
                if (!"androidx.compose.runtime.Composer".equals(p[7].getName())) continue;
                if (p[8] != int.class || p[9] != int.class) continue;
                candidates.add(m);
            }
            Log.i(TAG, "Overloads Surface compativeis: " + candidates.size());
            if (candidates.size() != 1) {
                Log.w(TAG, "Surface ambigua/ausente: teste visual desativado com seguranca");
                return;
            }
            XposedBridge.hookMethod(candidates.get(0), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (MEDIA_DEPTH.get() <= 0) return;
                    if (param.args[1] != shapeCard) return;
                    // Change only the shape argument of this Surface invocation.
                    param.args[1] = shapeSmall;
                    long now = SystemClock.uptimeMillis();
                    if (now - lastShapeLogMs > 2500L) {
                        lastShapeLogMs = now;
                        Log.i(TAG, "Shape TESTE: ShapeCard 28dp -> ShapeSm 12dp (media)");
                    }
                }
            });
            Log.i(TAG, "Hook seletivo de Surface instalado");
        } catch (Throwable t) {
            Log.e(TAG, "Falha no hook de Surface; teste visual desativado", t);
        }
    }
}
