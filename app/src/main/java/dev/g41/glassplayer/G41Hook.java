package dev.g41.glassplayer;

import android.app.Application;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.AndroidAppHelper;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** G41 Glass Player v0.3 - custom native overlay inside SystemUI. */
public final class G41Hook implements IXposedHookLoadPackage {
    public static final String TAG = "G41GlassPlayer";
    public static final String SETTING = "g41_glass_player_enabled";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String EXPANSION =
        "com.android.systemui.axdynamicbar.ui.AxDynamicBarKeyguardExpansion";

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!SYSTEMUI.equals(lp.packageName)) return;
        Log.i(TAG, "v0.3 carregada em " + lp.processName + " (overlay DESLIGADO por padrao)");
        try {
            XposedHelpers.findAndHookMethod(EXPANSION, lp.classLoader,
                "expand", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        // A falha do overlay NUNCA deve impedir o painel original.
                        try {
                            if (Looper.myLooper() != Looper.getMainLooper()) return;
                            Application app = AndroidAppHelper.currentApplication();
                            if (app == null) return;
                            if (Settings.Global.getInt(app.getContentResolver(), SETTING, 0) != 1) return;
                            KeyguardManager km = (KeyguardManager)
                                app.getSystemService(Context.KEYGUARD_SERVICE);
                            if (km != null && !km.isKeyguardLocked()) return;
                            Object media = findSelectedMedia(param.thisObject);
                            if (media == null) return;
                            if (GlassOverlay.showOrToggle(app, media)) {
                                param.setResult(null); // ignora expand() SOMENTE se nosso card abriu
                            }
                        } catch (Throwable t) {
                            Log.e(TAG, "Overlay falhou; expand() original preservado", t);
                        }
                    }
                });
            Log.i(TAG, "Hook do expand() instalado; ative com settings put global " + SETTING + " 1");
        } catch (Throwable t) {
            Log.e(TAG, "Nao consegui instalar hook expand(); v0.2 ainda pode ser restaurada", t);
        }
    }

    private static Object findSelectedMedia(Object expansion) {
        try {
            Object interactor = XposedHelpers.getObjectField(expansion, "interactor");
            Object uiFlow = XposedHelpers.getObjectField(interactor, "uiState");
            Object actualFlow;
            try {
                actualFlow = XposedHelpers.getObjectField(uiFlow, "$$delegate_0");
            } catch (Throwable ignored) {
                actualFlow = uiFlow;
            }
            Object state = XposedHelpers.callMethod(actualFlow, "getValue");
            if (state == null) return null;
            Object listObj = XposedHelpers.getObjectField(state, "events");
            if (!(listObj instanceof List)) return null;
            List<?> events = (List<?>) listObj;
            if (events.isEmpty()) return null;
            int idx = XposedHelpers.getIntField(state, "pinnedEventIndex");
            Object chosen = (idx >= 0 && idx < events.size()) ? events.get(idx) : events.get(0);
            return chosen != null && chosen.getClass().getName().endsWith("IslandEvent$Media")
                ? chosen : null;
        } catch (Throwable t) {
            Log.w(TAG, "Nao foi possivel identificar o evento Media, usando expand() original", t);
            return null;
        }
    }
}
