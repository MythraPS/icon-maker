package net.mythra.iconmaker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Shows a picture under a circle: drag to move it, pinch to zoom. The circle is
 * what a round home screen shows. Android cuts icons from the middle two thirds
 * of the image, so the export puts the circle's square there and fills the rest
 * from the picture around it (or its average colour past the picture's edge).
 */
public class CropView extends View {
    private final Bitmap src;
    private final RectF frame = new RectF();
    private final Matrix matrix = new Matrix();
    private final Paint dim = new Paint();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector pinch;
    private float scale = 1f, minScale = 1f, tx, ty, lastX, lastY;
    private boolean ready;

    public CropView(Context ctx, Bitmap src) {
        super(ctx);
        this.src = src;
        dim.setColor(0xAA000000);
        line.setColor(Color.WHITE);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3f);
        pinch = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float next = Math.max(minScale, Math.min(scale * d.getScaleFactor(), minScale * 8f));
                float f = next / scale;
                // zoom around the fingers
                tx = d.getFocusX() - (d.getFocusX() - tx) * f;
                ty = d.getFocusY() - (d.getFocusY() - ty) * f;
                scale = next;
                clamp();
                invalidate();
                return true;
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float side = Math.min(w, h) * 0.9f;
        frame.set((w - side) / 2f, (h - side) / 2f, (w + side) / 2f, (h + side) / 2f);
        // smallest zoom that still fills the square
        minScale = Math.max(side / src.getWidth(), side / src.getHeight());
        if (!ready) {
            scale = minScale;
            tx = frame.centerX() - src.getWidth() * scale / 2f;
            ty = frame.centerY() - src.getHeight() * scale / 2f;
            ready = true;
        }
        scale = Math.max(scale, minScale);
        clamp();
    }

    /** Keep the picture covering the whole square. */
    private void clamp() {
        float w = src.getWidth() * scale, h = src.getHeight() * scale;
        tx = Math.min(frame.left, Math.max(frame.right - w, tx));
        ty = Math.min(frame.top, Math.max(frame.bottom - h, ty));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        pinch.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX(); lastY = e.getY();
                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_POINTER_DOWN:
                // restart the drag from the finger that stays, so the picture doesn't jump
                int keep = e.getActionIndex() == 0 ? 1 : 0;
                if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) keep = 0;
                if (keep < e.getPointerCount()) { lastX = e.getX(keep); lastY = e.getY(keep); }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!pinch.isInProgress() && e.getPointerCount() == 1) {
                    tx += e.getX() - lastX;
                    ty += e.getY() - lastY;
                    clamp();
                    invalidate();
                }
                lastX = e.getX(); lastY = e.getY();
                break;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        matrix.setScale(scale, scale);
        matrix.postTranslate(tx, ty);
        c.drawBitmap(src, matrix, null);
        Path outside = new Path();
        outside.setFillType(Path.FillType.EVEN_ODD);
        outside.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        outside.addCircle(frame.centerX(), frame.centerY(), frame.width() / 2f, Path.Direction.CW);
        c.drawPath(outside, dim);
        c.drawCircle(frame.centerX(), frame.centerY(), frame.width() / 2f, line);
    }

    /** The icon, size x size: the circle's square in the middle two thirds. */
    public Bitmap cropped(int size) {
        float left = (frame.left - tx) / scale, top = (frame.top - ty) / scale, side = frame.width() / scale;
        float inner = size * 2f / 3f, off = (size - inner) / 2f, k = inner / side;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        // past the picture's edge: its average colour inside the circle
        int l = Math.max(0, Math.round(left)), t = Math.max(0, Math.round(top));
        int sd = Math.max(1, Math.min(Math.round(side), Math.min(src.getWidth() - l, src.getHeight() - t)));
        c.drawColor(Bitmap.createScaledBitmap(Bitmap.createBitmap(src, l, t, sd, sd), 1, 1, true).getPixel(0, 0) | 0xFF000000);
        Matrix m = new Matrix();
        m.setScale(k, k);
        m.postTranslate(off - left * k, off - top * k);
        c.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return out;
    }
}
