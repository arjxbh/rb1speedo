package club.midlife.rb1speed;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** A seven-segment speed readout drawn at the size available in the dashboard. */
final class DigitalSpeedView extends View {
    private static final float DIGIT_WIDTH = 56;
    private static final float DIGIT_HEIGHT = 100;
    private static final float DIGIT_GAP = 12;
    // Bits follow the clock display convention: top, upper right, lower right, bottom, lower left, upper left, center.
    private static final int[] SEGMENTS = {0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path[] segments = {
        horizontal(5), vertical(51, 10), vertical(51, 54), horizontal(95),
        vertical(5, 54), vertical(5, 10), horizontal(50)
    };
    private final float inset;
    private String digits = "";
    private int ink;

    DigitalSpeedView(Context context) {
        super(context);
        inset = 4 * getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void update(String digits, int ink) {
        if (this.digits.equals(digits) && this.ink == ink) return;
        this.digits = digits;
        this.ink = ink;
        setContentDescription(digits.isEmpty() ? "Speed unavailable" : digits + " miles per hour");
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = Math.max(1, digits.length());
        float width = count * DIGIT_WIDTH + (count - 1) * DIGIT_GAP;
        float scale = Math.min((getWidth() - 2 * inset) / width, (getHeight() - 2 * inset) / DIGIT_HEIGHT);
        if (scale <= 0) return;
        canvas.save();
        canvas.translate((getWidth() - width * scale) / 2, (getHeight() - DIGIT_HEIGHT * scale) / 2);
        canvas.scale(scale, scale);
        paint.setColor(ink);
        for (int i = 0; i < count; i++) {
            int active = digits.isEmpty() ? 0x40 : SEGMENTS[digits.charAt(i) - '0'];
            for (int segment = 0; segment < segments.length; segment++) {
                paint.setAlpha((active & (1 << segment)) != 0 ? 255 : 17);
                canvas.drawPath(segments[segment], paint);
            }
            canvas.translate(DIGIT_WIDTH + DIGIT_GAP, 0);
        }
        canvas.restore();
    }

    private static Path horizontal(float y) {
        Path path = new Path();
        path.moveTo(10, y);
        path.lineTo(15, y - 4);
        path.lineTo(41, y - 4);
        path.lineTo(46, y);
        path.lineTo(41, y + 4);
        path.lineTo(15, y + 4);
        path.close();
        return path;
    }

    private static Path vertical(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x + 4, y + 5);
        path.lineTo(x + 4, y + 31);
        path.lineTo(x, y + 36);
        path.lineTo(x - 4, y + 31);
        path.lineTo(x - 4, y + 5);
        path.close();
        return path;
    }
}
