package dev.g41.glassplayer;

import android.app.KeyguardManager;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.XposedHelpers;

/**
 * v0.7 — Figma Edition: CAVA-style FFT bars + album-color accents.
 * Original wallpaper is visible through a TRANSPARENT fullscreen overlay;
 * the giant clock is temporarily hidden only if a narrowly identified
 * large-clock View can be found. Restores alpha on close.
 * The compact InfinityBar remains the ROM's original implementation.
 */
public final class GlassOverlay {
    private static final String TAG = G41Hook.TAG;
    private static final int TYPE_STATUS_BAR_SUB_PANEL = 2017; // SystemUI-only type
    private static final int TEXT = 0xFF11131A;
    private static final int MUTED = 0xFF5E6678;
    private static final int BLUE = 0xFF0857DB; // fallback if album art unavailable
    // The card follows wallpaper-derived Monet. Artwork only colors the fluid waves.
    private static final String SETTING_BOTTOM_MARGIN = "g41_glass_player_bottom_margin_dp";
    private static int cardColor = 0xDCF1F1F3;
    private static int primaryTextColor = TEXT;
    private static int secondaryTextColor = MUTED;
    private static int controlsColor = TEXT;
    private static int progressColor = BLUE;
    private static int trackColor = 0xFFE7E9ED;
    private static int borderColor = 0xC6FFFFFF;
    private static int placeholderBackground = 0xFFC7D1DE;
    private static final int BAND_COUNT = 24;
    private static final int AUDIO_PENDING = 0, AUDIO_FFT = 1, AUDIO_WAVEFORM = 2, AUDIO_UNAVAILABLE = 3;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Context context;
    private static WindowManager wm;
    private static FrameLayout root;
    private static MediaController controller;
    private static ImageView artView;
    private static TextView titleView;
    private static TextView artistView;
    private static MediaButton playButton;
    private static ProgressLine progressView;
    private static BroadcastReceiver receiver;
    private static boolean seeking;
    private static long durationMs;
    private static long lastSessionCheck;
    private static String initialTitle = "";
    private static String initialArtist = "";
    private static String lastArtworkKey;
    private static GlassSurface glassSurface;
    private static Visualizer visualizer;
    // Capture callbacks run off the UI thread. Publish immutable snapshots, never mutate them.
    private static volatile float[] fftBands = new float[BAND_COUNT];
    private static volatile float waveformEnergy = 0f;
    private static volatile long lastFftSignalAt = 0L;
    private static volatile long lastWaveSignalAt = 0L;
    private static volatile int fftFrames = 0;
    private static volatile int waveFrames = 0;
    private static long captureStartedAt = 0L;
    private static int audioMode = AUDIO_PENDING;
    private static boolean audioStatsLogged = false;
    private static int accentColor = BLUE;
    private static boolean isPlaying = false;
    private static final Map<View, Float> hiddenClockAlphas = new IdentityHashMap<>();
    private static int clockCandidateLogs = 0;

    private static final Runnable WAVE_TICK = new Runnable() {
        @Override public void run() {
            if (root == null || glassSurface == null) return;
            if (!isPlaying) {
                glassSurface.setResting();
                return;
            }
            long now = SystemClock.elapsedRealtime();
            int selected = AUDIO_PENDING;
            if (now - lastFftSignalAt < 1700L) selected = AUDIO_FFT;
            else if (now - lastWaveSignalAt < 1700L) selected = AUDIO_WAVEFORM;
            else if (now - captureStartedAt > 4000L) selected = AUDIO_UNAVAILABLE;

            if (selected != audioMode) {
                audioMode = selected;
                Log.i(TAG, "Ondas: modo=" + audioModeName(selected)
                        + " fftFrames=" + fftFrames + " waveFrames=" + waveFrames);
            }
            if (!audioStatsLogged && now - captureStartedAt > 6000L) {
                audioStatsLogged = true;
                Log.i(TAG, "Ondas: diagnostico apos 6s: FFT=" + fftFrames
                        + " waveform=" + waveFrames + " modo=" + audioModeName(audioMode));
            }
            glassSurface.animateFrame(selected == AUDIO_FFT ? fftBands : null,
                    selected == AUDIO_WAVEFORM ? waveformEnergy : 0f, selected);
            MAIN.postDelayed(this, 20L); // ~50 FPS only while playing and expanded
        }
    };

    private static String audioModeName(int mode) {
        switch (mode) {
            case AUDIO_FFT: return "REAL_FFT";
            case AUDIO_WAVEFORM: return "REAL_WAVEFORM_RMS";
            case AUDIO_UNAVAILABLE: return "SEM_CAPTURA_REPOUSO";
            default: return "AGUARDANDO_DADOS";
        }
    }

    private GlassOverlay() {}

    /** Safe, read-only signal for the new compact overlay. */
    static boolean isOpen() { return root != null; }

    /** Returns true ONLY if a custom window was actually shown or closed. */
    public static boolean showOrToggle(Context app, Object event) {
        if (root != null) {
            hide();
            return true;
        }
        FrameLayout candidate = null;
        try {
            context = app;
            initialTitle = readString(event, "track");
            initialArtist = readString(event, "artist");
            Drawable initialArt = null;
            try {
                Object a = XposedHelpers.getObjectField(event, "albumArt");
                if (a instanceof Drawable) initialArt = (Drawable) a;
            } catch (Throwable ignored) { }

            wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) { reset(); return false; }
            findController();
            loadMonetPalette(app);
            candidate = buildWindow(app, initialArt);
            applyColorsFromDrawable(initialArt);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    TYPE_STATUS_BAR_SUB_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            lp.setTitle("G41 Glass Player v0.9.0-beta4");
            wm.addView(candidate, lp);

            root = candidate;
            hideLargeClockViews(); // leaves original wallpaper and depth effect intact
            CompactBarVisibility.hide(app, candidate); // v0.8: conservative chip hide
            startAudioCapture();
            registerReceiver();
            refresh();
            MAIN.removeCallbacks(WAVE_TICK);
            MAIN.post(WAVE_TICK);
            MAIN.removeCallbacks(TICK);
            MAIN.postDelayed(TICK, 850L);
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(220).start();
            Log.i(TAG, "GLASS V0.9.0-beta4 ABERTO (Monet, ondas album, card inferior)");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Overlay v0.7 falhou; mantendo player original", t);
            if (candidate != null && wm != null) {
                try { wm.removeViewImmediate(candidate); } catch (Throwable ignored) { }
            }
            unregisterReceiver();
            stopAudioCapture();
            restoreClockViews();
            CompactBarVisibility.restore();
            reset();
            return false;
        }
    }

    public static void hide() {
        MAIN.removeCallbacks(TICK);
        MAIN.removeCallbacks(WAVE_TICK);
        stopAudioCapture();
        restoreClockViews();
        CompactBarVisibility.restore();
        unregisterReceiver();
        FrameLayout old = root;
        WindowManager oldWm = wm;
        reset();
        if (old != null && oldWm != null) {
            try { oldWm.removeViewImmediate(old); }
            catch (Throwable t) { Log.w(TAG, "Falha ao fechar janela", t); }
        }
        Log.i(TAG, "GLASS V0.8 FECHADO: clock e lockscreen restaurados");
    }

    private static void reset() {
        root = null;
        wm = null;
        context = null;
        controller = null;
        artView = null;
        titleView = null;
        artistView = null;
        playButton = null;
        progressView = null;
        seeking = false;
        durationMs = 0L;
        lastSessionCheck = 0L;
        lastArtworkKey = null;
        glassSurface = null;
        isPlaying = false;
        fftBands = new float[BAND_COUNT];
        waveformEnergy = 0f;
        lastFftSignalAt = 0L;
        lastWaveSignalAt = 0L;
        fftFrames = waveFrames = 0;
        captureStartedAt = 0L;
        audioMode = AUDIO_PENDING;
        audioStatsLogged = false;
        accentColor = BLUE;
    }

    private static void registerReceiver() {
        if (context == null) return;
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_USER_PRESENT);
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { hide(); }
            };
            context.registerReceiver(receiver, f);
        } catch (Throwable t) { Log.w(TAG, "Receiver nao registrado", t); }
    }

    private static void unregisterReceiver() {
        if (receiver != null && context != null) {
            try { context.unregisterReceiver(receiver); } catch (Throwable ignored) { }
        }
        receiver = null;
    }

    private static FrameLayout buildWindow(Context app, Drawable album) {
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        final int sw = m.widthPixels;
        final int sh = m.heightPixels;
        FrameLayout fullscreen = new FrameLayout(app);
        fullscreen.setBackgroundColor(Color.TRANSPARENT); // wallpaper real, nao uma copia
        fullscreen.setClipChildren(false);
        fullscreen.setContentDescription("G41 Glass Player. Toque fora do painel para fechar.");

        int cardWidth = Math.min(Math.round(sw * .895f), d(400));
        int albumSize = Math.round(cardWidth * .73f);
        int cardHeight = albumSize + d(141);
        // Center the WHOLE card, not just its cover.
        FrameLayout card = new FrameLayout(app);
        card.setElevation(d(12));
        card.setClipToOutline(true);
        final int corner = d(20);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline out) {
                out.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner);
            }
        });
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(cardWidth, cardHeight);
        // The wallpaper's character is above the player; retain the lower splash.
        // Device-adjustable without rebuilding: settings put global
        // g41_glass_player_bottom_margin_dp 70
        int requestedBottom = Settings.Global.getInt(app.getContentResolver(),
                SETTING_BOTTOM_MARGIN, 70);
        int bottomDp = Math.max(8, Math.min(180, requestedBottom));
        cardLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        cardLp.bottomMargin = Math.min(d(bottomDp), Math.max(d(8), sh - cardHeight - d(24)));
        fullscreen.addView(card, cardLp);

        // Translucent material directly above the ACTUAL SystemUI wallpaper.
        // Avoids mismatches from Figma screenshots or cached wallpaper files.
        glassSurface = new GlassSurface(app);
        card.addView(glassSurface, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(app);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(d(18), d(9), d(18), d(8));
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout artFrame = new FrameLayout(app);
        LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(albumSize, albumSize);
        content.addView(artFrame, coverLp);
        artFrame.setBackgroundColor(placeholderBackground);
        artFrame.setClipToOutline(true);
        artFrame.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline out) {
                out.setRoundRect(0, 0, v.getWidth(), v.getHeight(), d(14));
            }
        });
        TextView placeholder = new TextView(app);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setText("♫");
        placeholder.setTextSize(62);
        placeholder.setTextColor(controlsColor);
        artFrame.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        artView = new ImageView(app);
        artView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (album != null) artView.setImageDrawable(album);
        artFrame.addView(artView, new FrameLayout.LayoutParams(-1, -1));

        titleView = text(app, empty(initialTitle) ? "Reproduzindo agora" : initialTitle,
                18, primaryTextColor, true);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, d(23));
        titleLp.topMargin = d(6);
        content.addView(titleView, titleLp);

        artistView = text(app, initialArtist, 13, secondaryTextColor, false);
        artistView.setMaxLines(1);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        artistView.setGravity(Gravity.CENTER);
        content.addView(artistView, new LinearLayout.LayoutParams(-1, d(16)));

        FrameLayout controlsArea = new FrameLayout(app);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(-1, d(48));
        controlsLp.topMargin = d(5);
        content.addView(controlsArea, controlsLp);
        // A fixed-width control group anchored to CENTER, independent of album/title.
        LinearLayout controls = new LinearLayout(app);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams groupLp = new FrameLayout.LayoutParams(d(204), -1, Gravity.CENTER);
        controlsArea.addView(controls, groupLp);

        MediaButton previous = new MediaButton(app, 0);
        playButton = new MediaButton(app, 1);
        MediaButton next = new MediaButton(app, 2);
        controls.addView(previous, new LinearLayout.LayoutParams(d(68), -1));
        controls.addView(playButton, new LinearLayout.LayoutParams(d(68), -1));
        controls.addView(next, new LinearLayout.LayoutParams(d(68), -1));
        previous.setContentDescription("Faixa anterior");
        playButton.setContentDescription("Reproduzir ou pausar");
        next.setContentDescription("Proxima faixa");
        previous.setOnClickListener(v -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        playButton.setOnClickListener(v -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        next.setOnClickListener(v -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT));

        progressView = new ProgressLine(app);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(-1, d(22));
        lineLp.topMargin = d(0);
        content.addView(progressView, lineLp);
        progressView.setContentDescription("Progresso da musica, deslize para avancar");

        // The full-screen backdrop, not the card, closes the expanded player.
        fullscreen.setOnClickListener(v -> hide());
        card.setOnClickListener(v -> {});
        Log.i(TAG, "Layout v0.9: card=" + cardWidth + "x" + cardHeight
                + "px, cover=" + albumSize + "px, margem inferior=" + cardLp.bottomMargin + "px");
        return fullscreen;
    }

    /**
     * Hide *only* an identifiable large clock view in the SystemUI hierarchy.
     * Never hide unknown containers or the lockscreen root. If it cannot be
     * located, keep the real wallpaper and leave the clock intact.
     */
    private static void hideLargeClockViews() {
        if (root == null) return;
        try {
            Class<?> cls = Class.forName("android.view.WindowManagerGlobal");
            Object global = XposedHelpers.callStaticMethod(cls, "getInstance");
            // Android 16: getRootViews() exige um IBinder; para iterar janelas use getWindowViews().
            Object views;
            try {
                views = XposedHelpers.callMethod(global, "getWindowViews");
            } catch (Throwable apiMismatch) {
                // Campo interno AOSP. Somente leitura; nao altera a lista de janelas.
                views = XposedHelpers.getObjectField(global, "mViews");
            }
            if (!(views instanceof List)) {
                Log.w(TAG,"Clock: consulta de janelas nao retornou lista");
                return;
            }
            Log.i(TAG, "Clock: janelas visiveis ao SystemUI=" + ((List<?>)views).size());
            int before = hiddenClockAlphas.size();
            for (Object candidate : (List<?>)views) {
                if (candidate instanceof View && candidate != root) {
                    hideClockChildren((View) candidate, 0);
                }
            }
            if (before == 0) Log.i(TAG,"Clock: Views ocultadas=" + hiddenClockAlphas.size()
                    + " (0 = clock customizado ainda nao identificado)");
        } catch (Throwable t) {
            Log.w(TAG,"Clock: nao foi possivel acessar Views da lockscreen",t);
        }
    }

    private static void hideClockChildren(View v, int depth) {
        if (v == null || v == root || depth > 32) return;
        boolean candidate = false;
        String name = v.getClass().getName();
        String idName = "";
        try {
            if (v.getId() != View.NO_ID) {
                idName = v.getResources().getResourceEntryName(v.getId()).toLowerCase(Locale.ROOT);
            }
        } catch (Throwable ignored) { }
        String cls = name.toLowerCase(Locale.ROOT);
        boolean large = v.getHeight() >= d(100) && v.getWidth() >= d(100);
        // Keep this deliberately narrow: never hide keyguard root or depth wallpaper.
        if (large && ((cls.equals("com.android.systemui.clocks.clockstyle") && idName.equals("clock_ls"))
                || cls.endsWith("animatableclockview")
                || cls.endsWith("largeclockview")
                || cls.endsWith("clocklayout")
                || idName.equals("large_clock_frame")
                || idName.equals("keyguard_large_clock")
                || idName.equals("large_clock")
                || idName.equals("lockscreen_clock_view_large")
                || idName.equals("keyguard_clock_large"))) {
            candidate = true;
        }
        if ((idName.contains("clock") || cls.contains("clock")) && large
                && clockCandidateLogs < 28) {
            clockCandidateLogs++;
            Log.i(TAG, "Clock: visto " + name + " id=" + idName
                    + " size=" + v.getWidth() + "x" + v.getHeight()
                    + " selecionado=" + candidate);
        }
        if (candidate) {
            if (!hiddenClockAlphas.containsKey(v)) {
                hiddenClockAlphas.put(v, v.getAlpha());
                Log.i(TAG,"Clock: ocultando " + name + " id=" + idName);
            }
            v.setAlpha(0f);
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup)v;
            for (int i=0; i<g.getChildCount(); i++) hideClockChildren(g.getChildAt(i),depth+1);
        }
    }

    private static void restoreClockViews() {
        for (Map.Entry<View,Float> entry : hiddenClockAlphas.entrySet()) {
            try { entry.getKey().setAlpha(entry.getValue()); }
            catch (Throwable t) { Log.w(TAG,"Nao foi possivel restaurar clock",t); }
        }
        hiddenClockAlphas.clear();
    }

    /**
     * Visualizer on session 0 observes the output mix when Android permits it.
     * Initializing the effect is NOT proof that any usable audio was captured.
     * FFT bins (frequency) and waveform RMS (volume) are handled separately.
     */
    private static void startAudioCapture() {
        stopAudioCapture();
        fftBands = new float[BAND_COUNT];
        waveformEnergy = 0f;
        lastFftSignalAt = lastWaveSignalAt = 0L;
        fftFrames = waveFrames = 0;
        captureStartedAt = SystemClock.elapsedRealtime();
        audioMode = AUDIO_PENDING;
        audioStatsLogged = false;
        try {
            if (context != null) {
                boolean record = context.checkSelfPermission("android.permission.RECORD_AUDIO")
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
                boolean modify = context.checkSelfPermission("android.permission.MODIFY_AUDIO_SETTINGS")
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
                Log.i(TAG, "Ondas: permissoes do processo SystemUI: RECORD_AUDIO=" + record
                        + " MODIFY_AUDIO_SETTINGS=" + modify);
            }
            Visualizer v = new Visualizer(0);
            visualizer = v;
            int[] range = Visualizer.getCaptureSizeRange();
            int captureSize = Math.min(range[1], Math.max(range[0], 1024));
            v.setCaptureSize(captureSize);
            int result = v.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer vis, byte[] bytes, int rate) {
                    if (root == null || bytes == null || bytes.length == 0) return;
                    waveFrames++;
                    double sum = 0d;
                    int count = 0;
                    for (int i = 0; i < bytes.length; i += 4) {
                        float sample = ((bytes[i] & 255) - 128f) / 128f;
                        sum += sample * sample;
                        count++;
                    }
                    float rms = (float) Math.sqrt(sum / Math.max(1, count));
                    float level = clamp01((rms - .012f) * 3.8f);
                    waveformEnergy = level;
                    if (level > .02f) lastWaveSignalAt = SystemClock.elapsedRealtime();
                }

                @Override public void onFftDataCapture(Visualizer vis, byte[] fft, int rate) {
                    if (root == null || fft == null || fft.length < 64) return;
                    fftFrames++;
                    // rate is expressed in milliHz; parser handles bin frequency mapping.
                    // Preserve 24 real log-frequency bands with RMS bin magnitudes.
                    // Frequencies are 35-12000 Hz; very low empty bands are
                    // interpolated because the Android FFT has coarse resolution.
                    float[] bands = CavaSpectrum.extractBands(fft, rate);
                    float sum = 0f, maximum = 0f;
                    float low = 0f, mid = 0f, high = 0f;
                    for (int i = 0; i < BAND_COUNT; i++) {
                        sum += bands[i];
                        maximum = Math.max(maximum, bands[i]);
                        if (i < 8) low += bands[i];
                        else if (i < 16) mid += bands[i];
                        else high += bands[i];
                    }
                    fftBands = bands; // atomic snapshot for the UI thread
                    if (sum / BAND_COUNT > .022f || maximum > .085f) {
                        lastFftSignalAt = SystemClock.elapsedRealtime();
                    }
                    if (fftFrames % 160 == 0) {
                        Log.i(TAG, String.format(Locale.ROOT,
                                "CAVA FFT 24: graves=%.3f medios=%.3f agudos=%.3f max=%.3f",
                                low / 8f, mid / 8f, high / 8f, maximum));
                    }
                }
            }, Math.min(20000, Visualizer.getMaxCaptureRate()), true, true);
            if (result != Visualizer.SUCCESS) {
                throw new IllegalStateException("setDataCaptureListener status=" + result);
            }
            v.setEnabled(true);
            Log.i(TAG, "Ondas: Visualizer session=0 habilitado, captureSize=" + captureSize
                    + " rate=" + Math.min(20000, Visualizer.getMaxCaptureRate())
                    + " (aguardando callbacks reais)");
        } catch (Throwable t) {
            Log.w(TAG, "Ondas: Visualizer indisponivel; ondas permanecem em repouso", t);
            stopAudioCapture();
        }
    }

    private static void stopAudioCapture() {
        if (visualizer != null) {
            try { visualizer.setEnabled(false); } catch (Throwable ignored) { }
            try { visualizer.release(); } catch (Throwable ignored) { }
        }
        visualizer = null;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // Resolve the public Android 12+ system tonal resources at runtime. This avoids
    // adding Material libraries to the privileged SystemUI/Xposed module.
    private static int tone(Context app, String name, int fallback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback;
        try {
            int id = app.getResources().getIdentifier(name, "color", "android");
            return id == 0 ? fallback : app.getResources().getColor(id, app.getTheme());
        } catch (Throwable t) {
            Log.w(TAG, "Monet: cor indisponivel: " + name, t);
            return fallback;
        }
    }

    private static int opacity(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static void loadMonetPalette(Context app) {
        boolean dark = (app.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int surface = tone(app, dark ? "system_neutral1_900" : "system_neutral1_50",
                dark ? 0xFF20232D : 0xFFF1F1F3);
        // Keep the glass see-through instead of making it an opaque Material card.
        cardColor = opacity(surface, 208);
        primaryTextColor = tone(app, dark ? "system_neutral1_100" : "system_neutral1_900",
                dark ? 0xFFF4F5FB : TEXT);
        secondaryTextColor = tone(app, dark ? "system_neutral2_300" : "system_neutral2_700",
                dark ? 0xFFBFC7D6 : MUTED);
        controlsColor = tone(app, dark ? "system_accent1_200" : "system_accent1_700",
                dark ? 0xFFA6C7FF : TEXT);
        progressColor = tone(app, dark ? "system_accent1_200" : "system_accent1_600",
                BLUE);
        trackColor = tone(app, dark ? "system_neutral2_700" : "system_neutral2_200",
                dark ? 0xFF555B68 : 0xFFE7E9ED);
        placeholderBackground = tone(app,
                dark ? "system_neutral1_700" : "system_neutral1_200",
                dark ? 0xFF404956 : 0xFFC7D1DE);
        borderColor = opacity(tone(app,
                dark ? "system_neutral2_400" : "system_neutral1_0",
                0xFFFFFFFF), dark ? 135 : 198);
        Log.i(TAG, String.format(Locale.ROOT,
                "Monet: sdk=%d dark=%s vidro=#%08X controle=#%06X progresso=#%06X",
                Build.VERSION.SDK_INT, dark, cardColor,
                controlsColor & 0xFFFFFF, progressColor & 0xFFFFFF));
    }

    /** Album palette only affects the two FFT/RMS waves. */
    private static void applyAccent(int color) {
        if (color == accentColor) return;
        accentColor = color;
        if (glassSurface != null) glassSurface.setAccent(color);
        Log.i(TAG, String.format(Locale.ROOT, "Paleta: album wave=#%06X", color & 0xFFFFFF));
    }

    private static void applyColorsFromDrawable(Drawable art) {
        if (art == null) { applyAccent(BLUE); return; }
        try {
            Bitmap bitmap;
            if (art instanceof BitmapDrawable) {
                bitmap = ((BitmapDrawable) art).getBitmap();
            } else {
                // Render only a tiny copy. Do not mutate the original IslandEvent artwork.
                bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                Drawable clone = art.getConstantState() == null ? null
                        : art.getConstantState().newDrawable().mutate();
                if (clone == null) { applyAccent(BLUE); return; }
                clone.setBounds(0, 0, 64, 64);
                clone.draw(canvas);
            }
            applyAccent(extractAccent(bitmap));
        } catch (Throwable t) {
            Log.w(TAG, "Paleta: erro ao ler Drawable, usando azul padrao", t);
            applyAccent(BLUE);
        }
    }

    /**
     * Lightweight palette extraction without androidx dependencies: sample 14x14,
     * accumulate sufficiently saturated pixels by hue, then clamp saturation/value
     * for legibility on the white glass background.
     */
    private static int extractAccent(Bitmap artwork) {
        if (artwork == null || artwork.isRecycled()) return BLUE;
        Bitmap sampled = null;
        try {
            // Canvas resampling also supports hardware-backed source bitmaps.
            sampled = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(sampled);
            Paint filter = new Paint(Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(artwork, null, new android.graphics.Rect(0, 0, 48, 48), filter);
            final int GROUPS = 24;
            double[] weight = new double[GROUPS];
            double[] hSin = new double[GROUPS];
            double[] hCos = new double[GROUPS];
            double[] sat = new double[GROUPS];
            double[] val = new double[GROUPS];
            float[] hsv = new float[3];
            for (int y = 3; y < 45; y += 3) {
                for (int x = 3; x < 45; x += 3) {
                    int color = sampled.getPixel(x, y);
                    if (Color.alpha(color) < 180) continue;
                    Color.colorToHSV(color, hsv);
                    if (hsv[1] < .17f || hsv[2] < .18f || hsv[2] > .97f && hsv[1] < .3f)
                        continue;
                    int group = Math.min(GROUPS - 1, (int) (hsv[0] / 360f * GROUPS));
                    double score = Math.pow(hsv[1], 1.15) * (0.75 + 0.25 * hsv[2]);
                    weight[group] += score;
                    double rad = Math.toRadians(hsv[0]);
                    hSin[group] += Math.sin(rad) * score;
                    hCos[group] += Math.cos(rad) * score;
                    sat[group] += hsv[1] * score;
                    val[group] += hsv[2] * score;
                }
            }
            int best = -1;
            for (int i = 0; i < GROUPS; i++) {
                if (weight[i] > 0.01 && (best == -1 || weight[i] > weight[best])) best = i;
            }
            if (best == -1) return 0xFF61718B; // grayscale covers
            float hue = (float) ((Math.toDegrees(Math.atan2(hSin[best], hCos[best])) + 360d) % 360d);
            float saturation = Math.max(.48f, Math.min(.84f, (float) (sat[best] / weight[best]) * .95f));
            float brightness = Math.max(.48f, Math.min(.73f, (float) (val[best] / weight[best]) * .88f));
            return Color.HSVToColor(new float[]{hue, saturation, brightness});
        } finally {
            if (sampled != null) sampled.recycle();
        }
    }

    /**
     * v0.9.0-beta4 — CAVA spectrum with present treble and rounded crests.
     * One HD filled 2D wave, bass in the center and treble at both edges.
     * Keeps the original Monet glass and album-reactive accent.
     */
    private static final class GlassSurface extends View {
        // Visual path resolution, not the number of frequency bands.
        private static final int CURVE_SAMPLES = 144;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final float[] waveY = new float[CURVE_SAMPLES + 1];
        private final float[] smoothY = new float[CURVE_SAMPLES + 1];
        private final Path wavePath = new Path();
        private final CavaSpectrum spectrum;
        private final float sensitivity;
        private int color = BLUE;
        private Shader waveGradient;

        GlassSurface(Context c) {
            super(c);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            float configured = Settings.Global.getFloat(c.getContentResolver(),
                    "g41_glass_cava_sensitivity", 1f);
            sensitivity = Math.max(.5f, Math.min(2.5f, configured));
            float treble = Settings.Global.getFloat(c.getContentResolver(),
                    "g41_glass_cava_treble_boost", .80f);
            spectrum = new CavaSpectrum(treble);
            setAccent(accentColor);
            Log.i(TAG, "CAVA HD v0.9.0-beta4: agudos presentes, curva cubica,"
                    + " EQ adaptativo 3 faixas, sensibilidade=" + sensitivity
                    + ", trebleBoost=" + Math.max(.7f, Math.min(2f, treble)));
        }

        void setAccent(int next) {
            color = next;
            waveGradient = null;
            invalidate();
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            waveGradient = null;
        }

        void animateFrame(float[] fft, float rms, int mode) {
            if (mode == AUDIO_FFT && fft != null && fft.length >= BAND_COUNT) {
                spectrum.updateFft(fft, sensitivity);
            } else if (mode == AUDIO_WAVEFORM) {
                spectrum.updateRms(rms, sensitivity);
            } else {
                spectrum.reset();
            }
            invalidate();
        }

        void setResting() {
            spectrum.reset(); // Complete wave disappearance on pause.
            invalidate();
        }

        private void ensureGradient(float h) {
            if (waveGradient != null) return;
            int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
            waveGradient = new android.graphics.LinearGradient(0f, h * .70f, 0f, h,
                    new int[]{Color.argb(168, r, g, b), Color.argb(140, r, g, b),
                            Color.argb(95, r, g, b)},
                    new float[]{0f, .60f, 1f}, Shader.TileMode.CLAMP);
        }

        // Smooth rendered samples, not captured FFT values.
        private void softenGeometry() {
            for (int i = 0; i <= CURVE_SAMPLES; i++) {
                float a = waveY[Math.max(0, i - 2)];
                float b = waveY[Math.max(0, i - 1)];
                float c = waveY[i];
                float d = waveY[Math.min(CURVE_SAMPLES, i + 1)];
                float e = waveY[Math.min(CURVE_SAMPLES, i + 2)];
                smoothY[i] = .10f * a + .20f * b + .40f * c
                        + .20f * d + .10f * e;
            }
            System.arraycopy(smoothY, 0, waveY, 0, waveY.length);
        }

        // Monotone cubic tangent: zero at peaks and valleys, to avoid
        // Catmull-Rom overshoot that could sharpen a frequency spike.
        private float tangentAt(int index) {
            if (index <= 0 || index >= CURVE_SAMPLES) return 0f;
            float left = waveY[index] - waveY[index - 1];
            float right = waveY[index + 1] - waveY[index];
            if (left * right <= 0f) return 0f;
            float limit = 2f * Math.min(Math.abs(left), Math.abs(right));
            float value = Math.min(Math.abs((left + right) * .5f), limit);
            return Math.copySign(value, left);
        }

        private void traceWave(float w, float h) {
            wavePath.rewind();
            wavePath.moveTo(0f, h + 1f);
            wavePath.lineTo(0f, waveY[0]);
            float dx = w / CURVE_SAMPLES;
            for (int i = 0; i < CURVE_SAMPLES; i++) {
                float x0 = i * dx, x1 = (i + 1) * dx;
                float dy0 = tangentAt(i), dy1 = tangentAt(i + 1);
                wavePath.cubicTo(x0 + dx / 3f, waveY[i] + dy0 / 3f,
                        x1 - dx / 3f, waveY[i + 1] - dy1 / 3f,
                        x1, waveY[i + 1]);
            }
            wavePath.lineTo(w, h + 1f);
            wavePath.close();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0f || h <= 0f) return;

            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(cardColor); // unchanged Monet translucency
            canvas.drawRect(0f, 0f, w, h, paint);

            if (spectrum.isVisible()) {
                float maxRise = h * .25f;
                for (int i = 0; i <= CURVE_SAMPLES; i++) {
                    float x = i / (float) CURVE_SAMPLES;
                    waveY[i] = h - spectrum.levelAt(x) * maxRise;
                }
                softenGeometry();
                traceWave(w, h);
                ensureGradient(h);
                paint.setShader(waveGradient);
                canvas.drawPath(wavePath, paint);
                paint.setShader(null);
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d(1));
            paint.setColor(borderColor);
            canvas.drawRoundRect(new RectF(d(.5f), d(.5f), w - d(.5f), h - d(.5f)),
                    d(20), d(20), paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    /** Black outlined transport controls drawn consistently across Android fonts. */
    private static final class MediaButton extends View {
        final int kind; // 0 previous, 1 play/pause, 2 next
        boolean playing = false;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        MediaButton(Context ctx, int action) { super(ctx); kind=action; setClickable(true); }
        void setPlaying(boolean value) { if (playing!=value) { playing=value; invalidate(); } }
        @Override protected void onDraw(Canvas c) {
            float cx=getWidth()/2f, cy=getHeight()/2f;
            p.setColor(controlsColor);
            p.setStrokeWidth(d(2.5f));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            if (kind == 1) {
                p.setStyle(Paint.Style.STROKE);
                c.drawCircle(cx,cy,d(13),p);
                p.setStyle(Paint.Style.FILL);
                if (playing) {
                    c.drawRoundRect(cx-d(5),cy-d(6),cx-d(1.5f),cy+d(6),d(1.5f),d(1.5f),p);
                    c.drawRoundRect(cx+d(1.5f),cy-d(6),cx+d(5),cy+d(6),d(1.5f),d(1.5f),p);
                } else {
                    Path tri=new Path();
                    tri.moveTo(cx-d(4),cy-d(6));
                    tri.lineTo(cx+d(6),cy);
                    tri.lineTo(cx-d(4),cy+d(6));
                    tri.close();
                    c.drawPath(tri,p);
                }
                return;
            }
            p.setStyle(Paint.Style.STROKE);
            boolean prev = kind==0;
            float arrowX = cx + (prev ? d(4) : -d(4));
            Path triangle = new Path();
            if (prev) {
                triangle.moveTo(arrowX+d(7),cy-d(7));
                triangle.lineTo(arrowX-d(5),cy);
                triangle.lineTo(arrowX+d(7),cy+d(7));
                c.drawLine(arrowX-d(8),cy-d(8),arrowX-d(8),cy+d(8),p);
            } else {
                triangle.moveTo(arrowX-d(7),cy-d(7));
                triangle.lineTo(arrowX+d(5),cy);
                triangle.lineTo(arrowX-d(7),cy+d(7));
                c.drawLine(arrowX+d(8),cy-d(8),arrowX+d(8),cy+d(8),p);
            }
            c.drawPath(triangle,p);
        }
    }

    /** Blue Figma progress line with an invisible, touch-friendly scrubbing area. */
    private static final class ProgressLine extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float fraction=0;
        private boolean available=false;
        private final int color = progressColor;
        ProgressLine(Context c) { super(c); setClickable(true); }
        void update(float f, boolean enabled) {
            if (!seeking) fraction=Math.max(0,Math.min(1,f));
            available=enabled;
            invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            float centerY=getHeight()/2f;
            float start=d(3), end=getWidth()-d(3);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(d(6));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(trackColor);
            c.drawLine(start,centerY,end,centerY,p);
            p.setColor(color);
            c.drawLine(start,centerY,start+(end-start)*fraction,centerY,p);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!available) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    seeking=true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    updateTouch(e.getX());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    updateTouch(e.getX());
                    return true;
                case MotionEvent.ACTION_UP:
                    updateTouch(e.getX());
                    seeking=false;
                    if (controller!=null && durationMs>0) {
                        try { controller.getTransportControls().seekTo((long)(durationMs*fraction)); }
                        catch(Throwable t) { Log.w(TAG,"Falha ao avancar faixa", t); }
                    }
                    performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    seeking=false;
                    return true;
            }
            return super.onTouchEvent(e);
        }
        private void updateTouch(float x) {
            fraction=Math.max(0,Math.min(1,(x-d(3))/Math.max(1,getWidth()-d(6))));
            invalidate();
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }

    private static void findController() {
        if (context==null) return;
        long now=SystemClock.uptimeMillis();
        if (now-lastSessionCheck<3500L) return;
        lastSessionCheck=now;
        try {
            MediaSessionManager manager=(MediaSessionManager)
                    context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager==null) return;
            List<MediaController> sessions=manager.getActiveSessions(null);
            MediaController pick=null;
            for (MediaController c:sessions) {
                PlaybackState st=c.getPlaybackState();
                if (st!=null && st.getState()==PlaybackState.STATE_PLAYING) {
                    pick=c;
                    break;
                }
                if (pick==null) pick=c;
            }
            controller=pick;
        } catch(Throwable t) {
            Log.w(TAG,"Sessao de midia indisponivel; usando eventos de tecla",t);
        }
    }

    private static final Runnable TICK = new Runnable() {
        @Override public void run() {
            if (root == null) return;
            try {
                if (context == null || Settings.Global.getInt(context.getContentResolver(),
                        G41Hook.SETTING,0)!=1) { hide(); return; }
                KeyguardManager km=(KeyguardManager)
                        context.getSystemService(Context.KEYGUARD_SERVICE);
                if (km!=null && !km.isKeyguardLocked()) { hide(); return; }
                refresh();
            } catch(Throwable t) { Log.w(TAG,"Atualizacao visual falhou",t); }
            if (root!=null) MAIN.postDelayed(this,900L);
        }
    };

    private static void refresh() {
        if (root==null) return;
        findController();
        if (controller==null) {
            if (progressView!=null) progressView.update(0,false);
            setPlaying(false);
            return;
        }
        try {
            MediaMetadata m=controller.getMetadata();
            PlaybackState st=controller.getPlaybackState();
            if (m!=null) {
                String name=m.getString(MediaMetadata.METADATA_KEY_TITLE);
                String artist=m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (!empty(name) && titleView!=null) titleView.setText(name);
                if (!empty(artist) && artistView!=null) artistView.setText(artist);
                String key=String.valueOf(name)+"/"+String.valueOf(artist)
                        +"/"+controller.getPackageName();
                if (!key.equals(lastArtworkKey) && artView!=null) {
                    lastArtworkKey=key;
                    Bitmap art=m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    if (art==null) art=m.getBitmap(MediaMetadata.METADATA_KEY_ART);
                    if (art==null) art=m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                    if (art!=null) {
                        artView.setImageBitmap(art);
                        try { applyAccent(extractAccent(art)); }
                        catch (Throwable t) { Log.w(TAG, "Paleta: nao foi possivel ler album", t); }
                    } else {
                        // An Event Drawable may still contain the visible cover.
                        applyColorsFromDrawable(artView.getDrawable());
                    }
                }
                durationMs=m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            }
            boolean playingNow=st!=null && st.getState()==PlaybackState.STATE_PLAYING;
            if (playButton!=null) playButton.setPlaying(playingNow);
            setPlaying(playingNow);
            if (progressView!=null) {
                boolean canSeek=durationMs>0 && st!=null;
                float fraction=0f;
                if (canSeek) {
                    long position=st.getPosition();
                    if (st.getState()==PlaybackState.STATE_PLAYING &&
                            st.getLastPositionUpdateTime()>0) {
                        long dt=SystemClock.elapsedRealtime()-st.getLastPositionUpdateTime();
                        position+=(long)(Math.max(0,dt)*st.getPlaybackSpeed());
                    }
                    fraction=Math.max(0f,Math.min(1f,position/(float)durationMs));
                }
                progressView.update(fraction,canSeek);
            }
        } catch(Throwable t) { Log.w(TAG,"Metadados de midia indisponiveis",t); }
    }

    private static void setPlaying(boolean playing) {
        if (isPlaying==playing) return;
        isPlaying=playing;
        if (!playing && glassSurface != null) glassSurface.setResting();
        MAIN.removeCallbacks(WAVE_TICK);
        if (root!=null) MAIN.post(WAVE_TICK);
    }

    private static void mediaKey(int key) {
        try {
            if (controller!=null) {
                MediaController.TransportControls c=controller.getTransportControls();
                if (key==KeyEvent.KEYCODE_MEDIA_PREVIOUS) c.skipToPrevious();
                else if (key==KeyEvent.KEYCODE_MEDIA_NEXT) c.skipToNext();
                else {
                    PlaybackState st=controller.getPlaybackState();
                    if (st!=null && st.getState()==PlaybackState.STATE_PLAYING) c.pause();
                    else c.play();
                }
            } else if (context!=null) {
                AudioManager audio=(AudioManager)
                        context.getSystemService(Context.AUDIO_SERVICE);
                if (audio!=null) {
                    audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));
                    audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));
                }
            }
            MAIN.postDelayed(GlassOverlay::refresh,250L);
        } catch(Throwable t) { Log.e(TAG,"Controle de musica falhou",t); }
    }

    private static String readString(Object obj,String field) {
        try {
            Object v=XposedHelpers.getObjectField(obj,field);
            return v==null?"":v.toString();
        } catch(Throwable ignored) { return ""; }
    }
    private static TextView text(Context ctx,String text,int sp,int color,boolean bold) {
        TextView v=new TextView(ctx);
        v.setText(text);
        v.setTextColor(color);
        v.setTextSize(sp);
        v.setIncludeFontPadding(false);
        v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        return v;
    }
    private static boolean empty(String s) { return s==null || s.isEmpty(); }
    private static int d(float dp) {
        if (context==null) return Math.round(dp);
        return Math.round(dp*context.getResources().getDisplayMetrics().density);
    }
}
