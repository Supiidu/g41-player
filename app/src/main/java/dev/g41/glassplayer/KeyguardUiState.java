package dev.g41.glassplayer;

import android.os.Looper;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Observe only the SystemUI bouncer state; never modify the PIN UI.
 *
 * This exact ROM exposes KeyguardStateControllerImpl.mPrimaryBouncerShowing
 * and notifyPrimaryBouncerShowing(boolean) in classes2.dex. When it opens,
 * our compact SystemUI-owned window must not float over the unlock prompt.
 */
final class KeyguardUiState {
    private static final String TAG = G41Hook.TAG;
    private static final String CONTROLLER =
            "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl";
    private static volatile boolean primaryBouncerShowing;

    private KeyguardUiState() { }

    static boolean isBouncerShowing() { return primaryBouncerShowing; }

    static void install(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod(CONTROLLER, loader,
                    "notifyPrimaryBouncerShowing", boolean.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        // Read actual state after the ROM's update rather than
                        // assuming callback argument equals the final value.
                        boolean showing = XposedHelpers.getBooleanField(
                                param.thisObject, "mPrimaryBouncerShowing");
                        if (primaryBouncerShowing == showing) return;
                        primaryBouncerShowing = showing;
                        Log.i(TAG, "Compact v0.10-beta4: bouncer="
                                + (showing ? "ABERTO" : "FECHADO"));
                        CompactBarOverlay.onBouncerChanged(showing);
                    } catch (Throwable t) {
                        Log.w(TAG, "Compact beta4: leitura bouncer indisponivel", t);
                    }
                }
            });
            Log.i(TAG, "Compact beta4: hook bouncer instalado (somente leitura)");
        } catch (Throwable t) {
            Log.w(TAG, "Compact beta4: hook bouncer ausente na ROM; sem ocultacao por PIN", t);
        }
    }
}
