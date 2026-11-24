package dev.oscar.powermenuplus;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;

/** 48x48 line icons drawn in code (module resources are not reachable from SystemUI). */
final class ActionIcon extends Drawable {
    static final int SOFT_REBOOT = 0, SYSTEMUI = 1;
    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int alpha = 255;

    ActionIcon(int kind) { this.kind = kind; }

    private void arrowHead(Canvas c, float x, float y, float dx1, float dy1, float dx2, float dy2) {
        Path p = new Path();
        p.moveTo(x + dx1, y + dy1); p.lineTo(x, y); p.lineTo(x + dx2, y + dy2);
        c.drawPath(p, paint);
    }

    @Override public void draw(Canvas c) {
        int save = c.save();
        try {
            c.translate(getBounds().exactCenterX(), getBounds().exactCenterY());
            float s = Math.min(getBounds().width(), getBounds().height()) / 48f;
            c.scale(s, s);
            c.translate(-24f, -24f);
            paint.setColor(-1);
            paint.setAlpha(alpha);
            paint.setStrokeWidth(1.9f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            if (kind == SOFT_REBOOT) {
                c.drawArc(10f, 10f, 38f, 38f, -60f, 300f, false, paint);   // circular arrow
                arrowHead(c, 33f, 12f, -1f, -7f, 7f, 1f);
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(24f, 24f, 2.2f, paint);                         // "soft" dot
            } else {
                c.drawRoundRect(14f, 6f, 34f, 42f, 4f, 4f, paint);           // phone outline
                c.drawLine(21f, 37f, 27f, 37f, paint);
                c.drawArc(18f, 15f, 30f, 27f, -60f, 280f, false, paint);     // refresh arc
                arrowHead(c, 28.5f, 15.5f, -0.5f, -4f, 4f, 0.5f);
            }
        } finally { c.restoreToCount(save); }
    }

    @Override public int getIntrinsicHeight() { return 96; }
    @Override public int getIntrinsicWidth() { return 96; }
    @Override public int getOpacity() { return -3; }
    @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); invalidateSelf(); }
}
