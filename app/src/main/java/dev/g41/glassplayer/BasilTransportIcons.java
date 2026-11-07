package dev.g41.glassplayer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Pixel-faithful Android Canvas paths converted from the user-supplied
 * basil_skip-prev-outline.svg and basil_skip-next-outline.svg (53 x 53).
 *
 * Do not load these via SystemUI resources: this Xposed module draws from a
 * foreign (com.android.systemui) Context. Inline Path geometry is self-contained
 * and receives the already-established Monet controlsColor on every frame.
 */
final class BasilTransportIcons {
    private static final float VIEWBOX = 53f;
    private static final Path PREVIOUS = previousPath();
    private static final Path NEXT = nextPath();

    private BasilTransportIcons() { }

    static void draw(Canvas canvas, Paint paint, boolean previous,
                     float centerX, float centerY, float iconSizePx) {
        int save = canvas.save();
        canvas.translate(centerX - iconSizePx / 2f, centerY - iconSizePx / 2f);
        float scale = iconSizePx / VIEWBOX;
        canvas.scale(scale, scale);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(previous ? PREVIOUS : NEXT, paint);
        canvas.restoreToCount(save);
    }

    private static Path previousPath() {
        Path p = new Path();
        p.setFillType(Path.FillType.EVEN_ODD);
        p.moveTo(14.9062f, 15.4584f);
        p.cubicTo(14.9062f, 15.0191f, 14.7318f, 14.5978f, 14.4211f, 14.2872f);
        p.cubicTo(14.1105f, 13.9766f, 13.6893f, 13.8021f, 13.25f, 13.8021f);
        p.cubicTo(12.8107f, 13.8021f, 12.3895f, 13.9766f, 12.0789f, 14.2872f);
        p.cubicTo(11.7682f, 14.5978f, 11.5938f, 15.0191f, 11.5938f, 15.4584f);
        p.lineTo(11.5938f, 37.5417f);
        p.cubicTo(11.5938f, 37.981f, 11.7682f, 38.4022f, 12.0789f, 38.7129f);
        p.cubicTo(12.3895f, 39.0235f, 12.8107f, 39.198f, 13.25f, 39.198f);
        p.cubicTo(13.6893f, 39.198f, 14.1105f, 39.0235f, 14.4211f, 38.7129f);
        p.cubicTo(14.7318f, 38.4022f, 14.9062f, 37.981f, 14.9062f, 37.5417f);
        p.lineTo(14.9062f, 15.4584f);
        p.close();
        p.moveTo(20.7429f, 29.267f);
        p.cubicTo(20.3206f, 28.9401f, 19.9788f, 28.5209f, 19.7437f, 28.0414f);
        p.cubicTo(19.5085f, 27.562f, 19.3863f, 27.0351f, 19.3863f, 26.5011f);
        p.cubicTo(19.3863f, 25.9671f, 19.5085f, 25.4402f, 19.7437f, 24.9607f);
        p.cubicTo(19.9788f, 24.4813f, 20.3206f, 24.062f, 20.7429f, 23.7351f);
        p.cubicTo(25.5119f, 20.0463f, 30.8362f, 17.1378f, 36.517f, 15.1182f);
        p.lineTo(37.5461f, 14.7538f);
        p.cubicTo(39.8052f, 13.95f, 42.1836f, 15.4804f, 42.4883f, 17.8035f);
        p.cubicTo(43.2392f, 23.5231f, 43.2392f, 29.4768f, 42.4883f, 35.1964f);
        p.cubicTo(42.1836f, 37.5195f, 39.8052f, 39.0499f, 37.5461f, 38.2461f);
        p.lineTo(36.517f, 37.8817f);
        p.cubicTo(30.8362f, 35.8621f, 25.5119f, 32.9536f, 20.7429f, 29.2648f);
        p.close(); // implicit SVG fill closure
        p.moveTo(22.7701f, 26.3498f);
        p.cubicTo(22.7477f, 26.3671f, 22.7295f, 26.3894f, 22.717f, 26.4148f);
        p.cubicTo(22.7045f, 26.4403f, 22.698f, 26.4683f, 22.698f, 26.4966f);
        p.cubicTo(22.698f, 26.525f, 22.7045f, 26.553f, 22.717f, 26.5785f);
        p.cubicTo(22.7295f, 26.6039f, 22.7477f, 26.6262f, 22.7701f, 26.6435f);
        p.cubicTo(27.2615f, 30.1168f, 32.2757f, 32.8554f, 37.6256f, 34.7569f);
        p.lineTo(38.6547f, 35.1235f);
        p.cubicTo(38.8755f, 35.2008f, 39.167f, 35.0506f, 39.2045f, 34.7635f);
        p.cubicTo(39.9156f, 29.331f, 39.9156f, 23.6667f, 39.2045f, 18.232f);
        p.cubicTo(39.1973f, 18.1695f, 39.1764f, 18.1093f, 39.1433f, 18.0558f);
        p.cubicTo(39.1102f, 18.0023f, 39.0657f, 17.9568f, 39.013f, 17.9224f);
        p.cubicTo(38.9603f, 17.8881f, 38.9006f, 17.8657f, 38.8383f, 17.857f);
        p.cubicTo(38.776f, 17.8483f, 38.7126f, 17.8534f, 38.6525f, 17.872f);
        p.lineTo(37.6256f, 18.2386f);
        p.cubicTo(32.2748f, 20.1378f, 27.2597f, 22.8739f, 22.7701f, 26.3498f);
        p.close();
        return p;
    }

    private static Path nextPath() {
        Path p = new Path();
        p.setFillType(Path.FillType.EVEN_ODD);
        p.moveTo(32.2571f, 23.7352f);
        p.cubicTo(34.068f, 25.1353f, 34.068f, 27.8648f, 32.2571f, 29.2671f);
        p.cubicTo(27.4878f, 32.9544f, 22.1636f, 35.8613f, 16.483f, 37.8796f);
        p.lineTo(15.4539f, 38.2462f);
        p.cubicTo(13.1948f, 39.05f, 10.8164f, 37.5196f, 10.5117f, 35.1965f);
        p.cubicTo(9.76084f, 29.4769f, 9.76084f, 23.5232f, 10.5117f, 17.8036f);
        p.cubicTo(10.8164f, 15.4805f, 13.1948f, 13.9501f, 15.4539f, 14.7539f);
        p.lineTo(16.483f, 15.1183f);
        p.cubicTo(22.1639f, 17.1379f, 27.4881f, 20.0464f, 32.2571f, 23.7352f);
        p.close();
        p.moveTo(30.2299f, 26.6458f);
        p.cubicTo(30.2519f, 26.6284f, 30.2698f, 26.6063f, 30.282f, 26.581f);
        p.cubicTo(30.2943f, 26.5558f, 30.3006f, 26.5281f, 30.3006f, 26.5f);
        p.cubicTo(30.3006f, 26.472f, 30.2943f, 26.4443f, 30.282f, 26.419f);
        p.cubicTo(30.2698f, 26.3938f, 30.2519f, 26.3717f, 30.2299f, 26.3543f);
        p.cubicTo(25.7385f, 22.881f, 20.7243f, 20.1424f, 15.3744f, 18.2409f);
        p.lineTo(14.3453f, 17.8743f);
        p.cubicTo(14.2854f, 17.8561f, 14.2221f, 17.8513f, 14.1601f, 17.8602f);
        p.cubicTo(14.0981f, 17.8691f, 14.0387f, 17.8915f, 13.9863f, 17.9258f);
        p.cubicTo(13.9339f, 17.9601f, 13.8896f, 18.0055f, 13.8566f, 18.0588f);
        p.cubicTo(13.8237f, 18.1121f, 13.8028f, 18.172f, 13.7955f, 18.2342f);
        p.cubicTo(13.0812f, 23.7216f, 13.0812f, 29.2784f, 13.7955f, 34.7658f);
        p.cubicTo(13.833f, 35.0529f, 14.1267f, 35.2031f, 14.3475f, 35.1258f);
        p.lineTo(15.3744f, 34.7592f);
        p.cubicTo(20.7252f, 32.86f, 25.7403f, 30.1217f, 30.2299f, 26.6458f);
        p.close();
        p.moveTo(39.75f, 13.8021f);
        p.cubicTo(40.1893f, 13.8021f, 40.6105f, 13.9766f, 40.9212f, 14.2872f);
        p.cubicTo(41.2318f, 14.5978f, 41.4063f, 15.0191f, 41.4063f, 15.4584f);
        p.lineTo(41.4063f, 37.5417f);
        p.cubicTo(41.4063f, 37.981f, 41.2318f, 38.4022f, 40.9212f, 38.7129f);
        p.cubicTo(40.6105f, 39.0235f, 40.1893f, 39.198f, 39.75f, 39.198f);
        p.cubicTo(39.3107f, 39.198f, 38.8895f, 39.0235f, 38.5789f, 38.7129f);
        p.cubicTo(38.2682f, 38.4022f, 38.0938f, 37.981f, 38.0938f, 37.5417f);
        p.lineTo(38.0938f, 15.4584f);
        p.cubicTo(38.0938f, 15.0191f, 38.2682f, 14.5978f, 38.5789f, 14.2872f);
        p.cubicTo(38.8895f, 13.9766f, 39.3107f, 13.8021f, 39.75f, 13.8021f);
        p.close();
        return p;
    }
}
