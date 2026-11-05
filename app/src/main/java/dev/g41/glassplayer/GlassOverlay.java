package dev.g41.glassplayer;

import android.app.KeyguardManager;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
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
import android.media.AudioManager;
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
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.XposedHelpers;

/**
 * v0.4 — Figma Edition.
 * Reuses the proven SystemUI expansion hook and creates a full-screen native
 * overlay. The original keyguard / clock are NOT modified: an opaque copy
 * of the user's supplied wallpaper visually hides the clock until dismissal.
 * The compact InfinityBar belongs to the ROM and stays untouched in v0.4.
 */
public final class GlassOverlay {
    private static final String TAG = G41Hook.TAG;
    private static final int TYPE_STATUS_BAR_SUB_PANEL = 2017; // SystemUI-only type
    private static final int TEXT = 0xFF11131A;
    private static final int MUTED = 0xFF5E6678;
    private static final int BLUE = 0xFF0857DB;
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
    private static Bitmap wallpaper;

    private GlassOverlay() {}

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
            wallpaper = loadWallpaper(app);
            findController();
            candidate = buildWindow(app, initialArt, wallpaper);

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
            lp.setTitle("G41 Glass Player v0.4");
            wm.addView(candidate, lp);

            root = candidate;
            registerReceiver();
            refresh();
            MAIN.removeCallbacks(TICK);
            MAIN.postDelayed(TICK, 850L);
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(220).start();
            Log.i(TAG, "GLASS V0.4 ABERTO (Figma, wallpaper, capa dinamica)");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Overlay v0.4 falhou; mantendo player original", t);
            if (candidate != null && wm != null) {
                try { wm.removeViewImmediate(candidate); } catch (Throwable ignored) { }
            }
            unregisterReceiver();
            reset();
            return false;
        }
    }

    public static void hide() {
        MAIN.removeCallbacks(TICK);
        unregisterReceiver();
        FrameLayout old = root;
        WindowManager oldWm = wm;
        reset();
        if (old != null && oldWm != null) {
            try { oldWm.removeViewImmediate(old); }
            catch (Throwable t) { Log.w(TAG, "Falha ao fechar janela", t); }
        }
        Log.i(TAG, "GLASS V0.4 FECHADO: lockscreen original restaurada");
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
        wallpaper = null;
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

    private static FrameLayout buildWindow(Context app, Drawable album, Bitmap bg) {
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        int sw = m.widthPixels;
        int sh = m.heightPixels;
        FrameLayout fullscreen = new FrameLayout(app);
        fullscreen.setClipChildren(false);
        fullscreen.setContentDescription("G41 Glass Player aberto. Toque fora do card para fechar.");

        if (bg != null) {
            ImageView background = new ImageView(app);
            background.setImageBitmap(bg);
            background.setScaleType(ImageView.ScaleType.CENTER_CROP);
            fullscreen.addView(background, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            // This photo covers the native giant clock only while expanded.
            fullscreen.setBackgroundColor(0xFF101219);
        } else {
            // If the stored wallpaper is unavailable, never black out the keyguard.
            // Clock can remain visible in fallback mode.
            fullscreen.setBackgroundColor(0xB9000000);
            Log.w(TAG, "Wallpaper indisponivel; relogio pode continuar visivel");
        }
        View dim = new View(app);
        dim.setBackgroundColor(bg != null ? 0x57060B11 : 0x16000000);
        fullscreen.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        int cardWidth = Math.min(Math.round(sw * .895f), d(400));
        int albumSize = Math.round(cardWidth * .73f);
        int cardHeight = albumSize + d(129);
        int cardLeft = (sw - cardWidth) / 2;
        int cardTop = Math.min(Math.round(sh * .36f), sh - cardHeight - d(35));
        cardTop = Math.max(d(35), cardTop);

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
        cardLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        cardLp.topMargin = cardTop;
        fullscreen.addView(card, cardLp);

        // Real blur of the user's lockscreen photo inside the glass card (Android 12+).
        if (bg != null) {
            WallpaperInsideCard blur = new WallpaperInsideCard(app, bg, sw, sh,
                    cardLeft, cardTop);
            if (Build.VERSION.SDK_INT >= 31) {
                blur.setRenderEffect(RenderEffect.createBlurEffect(
                        d(14), d(14), Shader.TileMode.CLAMP));
            }
            card.addView(blur, new FrameLayout.LayoutParams(-1, -1));
        }
        // The glass/milky coating and waves are drawn behind content, not baked into art.
        GlassSurface surface = new GlassSurface(app);
        card.addView(surface, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(app);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(d(18), d(9), d(18), d(8));
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout artFrame = new FrameLayout(app);
        LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(albumSize, albumSize);
        content.addView(artFrame, coverLp);
        artFrame.setBackgroundColor(0xFFC7D1DE);
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
        placeholder.setTextColor(0xFF6681A9);
        artFrame.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        artView = new ImageView(app);
        artView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (album != null) artView.setImageDrawable(album);
        artFrame.addView(artView, new FrameLayout.LayoutParams(-1, -1));

        titleView = text(app, empty(initialTitle) ? "Reproduzindo agora" : initialTitle,
                18, TEXT, true);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, d(23));
        titleLp.topMargin = d(6);
        content.addView(titleView, titleLp);

        artistView = text(app, initialArtist, 13, MUTED, false);
        artistView.setMaxLines(1);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        artistView.setGravity(Gravity.CENTER);
        content.addView(artistView, new LinearLayout.LayoutParams(-1, d(16)));

        LinearLayout controls = new LinearLayout(app);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(-1, d(39));
        controlsLp.topMargin = d(2);
        content.addView(controls, controlsLp);

        MediaButton previous = new MediaButton(app, 0);
        playButton = new MediaButton(app, 1);
        MediaButton next = new MediaButton(app, 2);
        controls.addView(previous, new LinearLayout.LayoutParams(d(64), -1));
        controls.addView(playButton, new LinearLayout.LayoutParams(d(64), -1));
        controls.addView(next, new LinearLayout.LayoutParams(d(64), -1));
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
        Log.i(TAG, "Layout Figma: card=" + cardWidth + "x" + cardHeight
                + "px, arte=" + albumSize + "px, y=" + cardTop);
        return fullscreen;
    }

    /** Prefer the actual artwork provided by the user and bundled in this APK. */
    private static Bitmap loadWallpaper(Context sysui) {
        try {
            Context module = sysui.createPackageContext(
                    "dev.g41.glassplayer", Context.CONTEXT_IGNORE_SECURITY);
            try (InputStream stream = module.getAssets().open("g41_lockscreen.jpg")) {
                Bitmap bmp = BitmapFactory.decodeStream(stream);
                if (bmp != null) {
                    Log.i(TAG, "Wallpaper da referencia carregado do modulo");
                    return bmp;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Nao foi possivel abrir o wallpaper empacotado", t);
        }
        try {
            Drawable dw = WallpaperManager.getInstance(sysui).getDrawable();
            if (dw != null && dw.getIntrinsicWidth() > 0 && dw.getIntrinsicHeight() > 0) {
                int w = Math.min(dw.getIntrinsicWidth(), 1200);
                int h = Math.min(dw.getIntrinsicHeight(), 2400);
                Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                dw.setBounds(0, 0, w, h);
                dw.draw(canvas);
                return bitmap;
            }
        } catch (Throwable t) {
            Log.w(TAG, "WallpaperManager indisponivel", t);
        }
        return null;
    }

    private static final class WallpaperInsideCard extends View {
        final Bitmap image;
        final int screenW, screenH, left, top;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        WallpaperInsideCard(Context c, Bitmap img, int sw, int sh, int x, int y) {
            super(c); image=img; screenW=sw; screenH=sh; left=x; top=y;
        }
        @Override protected void onDraw(Canvas c) {
            float scale = Math.max(screenW / (float) image.getWidth(),
                    screenH / (float) image.getHeight());
            float x = (screenW - image.getWidth() * scale) / 2f - left;
            float y = (screenH - image.getHeight() * scale) / 2f - top;
            Matrix mx = new Matrix();
            mx.setScale(scale, scale);
            mx.postTranslate(x, y);
            c.drawBitmap(image, mx, paint);
        }
    }

    /** Rounded milky glass surface and soft pale-blue waves from the Figma mockup. */
    private static final class GlassSurface extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path wave = new Path();
        GlassSurface(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xDCEFF1F5);
            c.drawRect(0, 0, w, h, paint);

            wave.reset();
            wave.moveTo(0, h*.69f);
            wave.cubicTo(w*.12f,h*.56f, w*.13f,h*.70f, w*.29f,h*.73f);
            wave.cubicTo(w*.42f,h*.82f, w*.56f,h*.72f, w*.65f,h*.79f);
            wave.cubicTo(w*.73f,h*.64f, w*.77f,h*.69f, w*.83f,h*.76f);
            wave.cubicTo(w*.91f,h*.86f, w*.91f,h*.60f, w,h*.70f);
            wave.lineTo(w,h); wave.lineTo(0,h); wave.close();
            paint.setColor(0x8391A9D0);
            c.drawPath(wave,paint);

            wave.reset();
            wave.moveTo(0,h*.73f);
            wave.cubicTo(w*.09f,h*.68f, w*.19f,h*.78f, w*.32f,h*.80f);
            wave.cubicTo(w*.49f,h*.83f, w*.58f,h*.75f, w*.67f,h*.77f);
            wave.cubicTo(w*.80f,h*.75f, w*.89f,h*.83f, w,h*.81f);
            wave.lineTo(w,h); wave.lineTo(0,h); wave.close();
            paint.setColor(0x459EB6DB);
            c.drawPath(wave,paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d(1));
            paint.setColor(0x99FFFFFF);
            RectF frame = new RectF(d(.5f), d(.5f),w-d(.5f),h-d(.5f));
            c.drawRoundRect(frame,d(20),d(20),paint);
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
            p.setColor(TEXT);
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
            p.setColor(0xFFE7E9ED);
            c.drawLine(start,centerY,end,centerY,p);
            p.setColor(BLUE);
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
                    if (art!=null) artView.setImageBitmap(art);
                }
                durationMs=m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            }
            if (playButton!=null && st!=null)
                playButton.setPlaying(st.getState()==PlaybackState.STATE_PLAYING);
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
