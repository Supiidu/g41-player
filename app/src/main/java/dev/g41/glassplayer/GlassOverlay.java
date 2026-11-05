package dev.g41.glassplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.List;

import de.robv.android.xposed.XposedHelpers;

/**
 * Native Android overlay drawn by the com.android.systemui process.
 * Avoids modifying global Compose colors/shapes or replacing SystemUI.apk.
 * NOTE: 2017 is Android's hidden TYPE_STATUS_BAR_SUB_PANEL window type.
 * This window requires SystemUI privileges; if addView fails, expand() falls back.
 */
public final class GlassOverlay {
    private static final String TAG = G41Hook.TAG;
    private static final int TYPE_STATUS_BAR_SUB_PANEL = 2017;
    private static final int PINK = 0xFFF0B0E1;
    private static final int WHITE = 0xFFF9F6FB;
    private static final int MUTED = 0xFFB9B4C7;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WindowManager wm;
    private static FrameLayout windowRoot;
    private static Context context;
    private static MediaController controller;
    private static TextView titleView, artistView, playView, timeView, endView;
    private static ImageView coverView;
    private static SeekBar seekBar;
    private static boolean updatingSeek;
    private static BroadcastReceiver receiver;
    private static long lastSessionProbeMs;
    private static String initialTitle = "";
    private static String initialArtist = "";
    private static String displayedArtworkTrack = null;

    private GlassOverlay() {}

    /** Returns true only when the overlay was opened or closed successfully. */
    public static boolean showOrToggle(Context app, Object media) {
        if (windowRoot != null) {
            hide();
            return true;
        }
        try {
            context = app;
            initialTitle = safeString(media, "track");
            initialArtist = safeString(media, "artist");
            Drawable albumArt = null;
            try {
                Object a = XposedHelpers.getObjectField(media, "albumArt");
                if (a instanceof Drawable) albumArt = (Drawable) a;
            } catch (Throwable ignored) { }
            wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return false;
            findController();
            FrameLayout candidate = createWindow(app, albumArt);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                TYPE_STATUS_BAR_SUB_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.y = d(100);
            lp.setTitle("G41 Glass Player");
            try {
                wm.addView(candidate, lp);
            } catch (Throwable t) {
                Log.e(TAG, "WindowManager.addView negado ou falhou. Mantendo player original.", t);
                candidate = null;
                clearReferences();
                return false;
            }
            windowRoot = candidate;
            registerScreenReceiver();
            refresh();
            MAIN.postDelayed(TICK, 700L);
            Log.i(TAG, "OVERLAY ABERTO: player customizado substituiu a expansao original");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Erro ao montar painel, preservando expansao original", t);
            hide();
            return false;
        }
    }

    public static void hide() {
        MAIN.removeCallbacks(TICK);
        if (receiver != null && context != null) {
            try { context.unregisterReceiver(receiver); } catch (Throwable ignored) { }
        }
        receiver = null;
        FrameLayout previous = windowRoot;
        windowRoot = null;
        if (previous != null && wm != null) {
            try { wm.removeViewImmediate(previous); } catch (Throwable t) { Log.w(TAG, "Falha ao remover window", t); }
        }
        clearReferences();
        Log.i(TAG, "Overlay fechado");
    }

    private static void clearReferences() {
        controller = null;
        wm = null;
        context = null;
        titleView = artistView = playView = timeView = endView = null;
        coverView = null;
        seekBar = null;
        updatingSeek = false;
        lastSessionProbeMs = 0L;
        displayedArtworkTrack = null;
    }

    private static void registerScreenReceiver() {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context ctx, Intent intent) {
                    hide();
                }
            };
            context.registerReceiver(receiver, filter);
        } catch (Throwable t) {
            Log.w(TAG, "Receiver de fechamento nao disponivel", t);
        }
    }

    private static String safeString(Object obj, String field) {
        try {
            Object value = XposedHelpers.getObjectField(obj, field);
            return value == null ? "" : value.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void findController() {
        if (context == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastSessionProbeMs < 5000L) return;
        lastSessionProbeMs = now;
        controller = null;
        try {
            MediaSessionManager msm = (MediaSessionManager)
                context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return;
            // SystemUI normally holds MEDIA_CONTENT_CONTROL. If this ROM doesn't,
            // media keys still work via AudioManager.dispatchMediaKeyEvent.
            List<MediaController> sessions = msm.getActiveSessions(null);
            MediaController selected = null;
            for (MediaController c : sessions) {
                MediaMetadata m = c.getMetadata();
                if (m != null && initialTitle.equalsIgnoreCase(
                        m.getString(MediaMetadata.METADATA_KEY_TITLE))) {
                    selected = c;
                    break;
                }
                PlaybackState ps = c.getPlaybackState();
                if (selected == null && ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                    selected = c;
                }
            }
            if (selected == null && !sessions.isEmpty()) selected = sessions.get(0);
            controller = selected;
            Log.i(TAG, "Sessao de midia " + (selected == null ? "indisponivel" : "conectada"));
        } catch (Throwable t) {
            Log.w(TAG, "Sem acesso a sessoes; usando teclas de midia", t);
        }
    }

    private static FrameLayout createWindow(Context ctx, Drawable albumArt) {
        FrameLayout frame = new FrameLayout(ctx);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(d(18), d(15), d(18), d(19));
        GradientDrawable glass = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{0xE6222238, 0xDE131725, 0xEB100F1E});
        glass.setCornerRadius(d(18));
        glass.setStroke(d(1), 0x8EDABEEB);
        card.setBackground(glass);
        card.setElevation(d(18));
        FrameLayout.LayoutParams c = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        c.setMargins(d(18), 0, d(18), 0);
        frame.addView(card, c);

        // Glossy header
        LinearLayout header = new LinearLayout(ctx);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView eyebrow = label("G41  /  NOW PLAYING", 10, PINK, true);
        eyebrow.setLetterSpacing(0.18f);
        header.addView(eyebrow, new LinearLayout.LayoutParams(0, d(34), 1));
        eyebrow.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = label("✕", 19, WHITE, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(0x283F3B51, 12));
        close.setOnClickListener(v -> hide());
        header.addView(close, new LinearLayout.LayoutParams(d(36), d(34)));
        card.addView(header);

        View topRule = new View(ctx);
        topRule.setBackground(new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0x90FFE9FF, 0x18E0C7FF, 0x00FFFFFF}));
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, d(1));
        ruleLp.topMargin = d(2); ruleLp.bottomMargin = d(15);
        card.addView(topRule, ruleLp);

        LinearLayout infoRow = new LinearLayout(ctx);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        coverView = new ImageView(ctx);
        coverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverView.setBackground(round(0xFF40425D, 12));
        coverView.setClipToOutline(true);
        if (albumArt != null) coverView.setImageDrawable(albumArt);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(d(112), d(112));
        infoRow.addView(coverView, artLp);

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, d(112), 1);
        textLp.leftMargin = d(16);
        titleView = label(empty(initialTitle) ? "Now playing" : initialTitle, 21, WHITE, true);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        artistView = label(initialArtist, 14, MUTED, false);
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView);
        LinearLayout.LayoutParams artistLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        artistLp.topMargin = d(6);
        texts.addView(artistView, artistLp);
        TextView insignia = label("GLASS SESSION  •  G41", 9, PINK, true);
        insignia.setLetterSpacing(.09f);
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iLp.topMargin = d(11);
        texts.addView(insignia, iLp);
        infoRow.addView(texts, textLp);
        card.addView(infoRow);

        seekBar = new SeekBar(ctx);
        seekBar.setMax(1000);
        seekBar.setProgressTintList(ColorStateList.valueOf(PINK));
        seekBar.setThumbTintList(ColorStateList.valueOf(PINK));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x555F5F7D));
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, d(37));
        seekLp.topMargin = d(16);
        card.addView(seekBar, seekLp);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { updatingSeek = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                updatingSeek = false;
                if (controller == null || controller.getMetadata() == null) return;
                long duration = controller.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (duration > 0) {
                    controller.getTransportControls().seekTo(duration * bar.getProgress() / 1000L);
                }
            }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { }
        });

        LinearLayout times = new LinearLayout(ctx);
        timeView = label("0:00", 11, MUTED, false);
        endView = label("--:--", 11, MUTED, false);
        times.addView(timeView, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        times.addView(endView);
        card.addView(times);

        LinearLayout controls = new LinearLayout(ctx);
        controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, d(68));
        controlsLp.topMargin = d(9);
        card.addView(controls, controlsLp);
        TextView back = control("|◀", 25, false);
        TextView play = control("▶", 27, true);
        TextView next = control("▶|", 25, false);
        controls.addView(back, new LinearLayout.LayoutParams(0, d(60), 1));
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(d(66), d(60));
        controls.addView(play, playLp);
        controls.addView(next, new LinearLayout.LayoutParams(0, d(60), 1));
        playView = play;
        back.setOnClickListener(v -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        next.setOnClickListener(v -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT));
        play.setOnClickListener(v -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        return frame;
    }

    private static void mediaKey(int key) {
        try {
            if (controller != null) {
                MediaController.TransportControls t = controller.getTransportControls();
                if (key == KeyEvent.KEYCODE_MEDIA_PREVIOUS) t.skipToPrevious();
                else if (key == KeyEvent.KEYCODE_MEDIA_NEXT) t.skipToNext();
                else {
                    PlaybackState p = controller.getPlaybackState();
                    if (p != null && p.getState() == PlaybackState.STATE_PLAYING) t.pause();
                    else t.play();
                }
            } else if (context != null) {
                AudioManager a = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (a != null) {
                    a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
                    a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
                }
            }
            MAIN.postDelayed(() -> refresh(), 250L);
        } catch (Throwable t) {
            Log.e(TAG, "Controle de midia falhou", t);
        }
    }

    private static final Runnable TICK = new Runnable() {
        @Override public void run() {
            if (windowRoot == null) return;
            try {
                if (context == null || Settings.Global.getInt(context.getContentResolver(),
                        G41Hook.SETTING, 0) != 1) {
                    hide();
                    return;
                }
                refresh();
            } catch (Throwable t) { Log.w(TAG, "Falha ao atualizar painel", t); }
            MAIN.postDelayed(this, 1000L);
        }
    };

    private static void refresh() {
        if (windowRoot == null) return;
        if (controller == null) {
            findController();
            if (controller == null) {
                if (seekBar != null) seekBar.setEnabled(false);
                return;
            }
        }
        MediaMetadata m = controller.getMetadata();
        PlaybackState p = controller.getPlaybackState();
        if (m != null) {
            String name = m.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (titleView != null && !empty(name)) titleView.setText(name);
            if (artistView != null && !empty(artist)) artistView.setText(artist);
            if (coverView != null && !java.util.Objects.equals(displayedArtworkTrack, name)) {
                displayedArtworkTrack = name;
                android.graphics.Bitmap art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (art != null) coverView.setImageBitmap(art);
            }
        }
        if (playView != null && p != null) {
            boolean playing = p.getState() == PlaybackState.STATE_PLAYING;
            playView.setText(playing ? "Ⅱ" : "▶");
        }
        long duration = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (seekBar != null) {
            seekBar.setEnabled(duration > 0 && p != null);
            if (duration > 0 && p != null) {
                long pos = p.getPosition();
                if (p.getState() == PlaybackState.STATE_PLAYING && p.getLastPositionUpdateTime() > 0) {
                    long elapsed = SystemClock.elapsedRealtime() - p.getLastPositionUpdateTime();
                    pos += (long)(elapsed * p.getPlaybackSpeed());
                }
                pos = Math.max(0L, Math.min(duration, pos));
                if (!updatingSeek) seekBar.setProgress((int)(1000L * pos / duration));
                if (timeView != null) timeView.setText(clock(pos));
                if (endView != null) endView.setText(clock(duration));
            }
        }
    }

    private static String clock(long ms) {
        long seconds = ms / 1000L;
        return (seconds / 60) + ":" + String.format(java.util.Locale.US, "%02d", seconds % 60);
    }

    private static TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setIncludeFontPadding(false);
        return t;
    }
    private static TextView control(String s, int size, boolean prominent) {
        TextView t = label(s, size, prominent ? 0xFF241525 : WHITE, true);
        t.setGravity(Gravity.CENTER);
        if (prominent) t.setBackground(round(PINK, 22));
        return t;
    }
    private static GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(d(radius));
        return d;
    }
    private static int d(float dp) {
        if (context == null) return (int) dp;
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
    private static boolean empty(String s) { return s == null || s.length() == 0; }
}
