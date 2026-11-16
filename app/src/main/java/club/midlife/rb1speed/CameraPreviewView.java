package club.midlife.rb1speed;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/** Fit the complete camera frame; guides stay within it even when letterboxed. */
final class CameraPreviewView extends ImageView {
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
    CameraPreviewView(Context context) {
        super(context); setScaleType(ScaleType.FIT_CENTER); setBackgroundColor(Color.BLACK);
        setContentDescription("Live rear-camera aiming preview with horizon and center guides");
        guide.setColor(0xbbe4edf7); guide.setStrokeWidth(getResources().getDisplayMetrics().density);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Drawable drawable = getDrawable(); if (drawable == null) return;
        RectF frame = new RectF(drawable.getBounds()); getImageMatrix().mapRect(frame);
        for (int i = 1; i < 3; i++) {
            float x = frame.left + frame.width() * i / 3, y = frame.top + frame.height() * i / 3;
            canvas.drawLine(x, frame.top, x, frame.bottom, guide);
            canvas.drawLine(frame.left, y, frame.right, y, guide);
        }
        float cx = frame.centerX(), cy = frame.centerY(), arm = 10 * getResources().getDisplayMetrics().density;
        canvas.drawLine(cx - arm, cy, cx + arm, cy, guide); canvas.drawLine(cx, cy - arm, cx, cy + arm, guide);
    }
}
