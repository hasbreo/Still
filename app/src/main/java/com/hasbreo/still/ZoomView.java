package com.hasbreo.still;

import android.content.*;
import android.graphics.*;
import android.view.*;

final class ZoomView extends View {
  Bitmap bitmap;
  float zoom = 1, dx, dy, lastX, lastY;
  final Paint paint = new Paint(3);
  final ScaleGestureDetector scale;

  ZoomView(Context c) {
    super(c);
    setBackgroundColor(Color.BLACK);
    scale =
        new ScaleGestureDetector(
            c,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              public boolean onScale(ScaleGestureDetector d) {
                zoom = Math.max(1, Math.min(8, zoom * d.getScaleFactor()));
                clamp();
                invalidate();
                return true;
              }
            });
  }

  void setBitmap(Bitmap b, boolean reset) {
    Bitmap old = bitmap;
    bitmap = b;
    if (reset) {
      zoom = 1;
      dx = dy = 0;
    }
    invalidate();
    if (old != null && old != b) old.recycle();
  }

  float fit() {
    return bitmap == null
        ? 1
        : Math.min(
            (float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
  }

  void clamp() {
    if (bitmap == null) return;
    float s = fit() * zoom;
    dx =
        Math.max(
            -Math.max(0, (bitmap.getWidth() * s - getWidth()) / 2),
            Math.min(Math.max(0, (bitmap.getWidth() * s - getWidth()) / 2), dx));
    dy =
        Math.max(
            -Math.max(0, (bitmap.getHeight() * s - getHeight()) / 2),
            Math.min(Math.max(0, (bitmap.getHeight() * s - getHeight()) / 2), dy));
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    if (bitmap == null) return;
    clamp();
    float s = fit() * zoom;
    c.save();
    c.translate(getWidth() / 2f + dx, getHeight() / 2f + dy);
    c.scale(s, s);
    c.drawBitmap(bitmap, -bitmap.getWidth() / 2f, -bitmap.getHeight() / 2f, paint);
    c.restore();
  }

  public boolean onTouchEvent(android.view.MotionEvent e) {
    scale.onTouchEvent(e);
    if (e.getActionMasked() == 0) {
      lastX = e.getX();
      lastY = e.getY();
    } else if (e.getActionMasked() == 2 && !scale.isInProgress()) {
      dx += e.getX() - lastX;
      dy += e.getY() - lastY;
      lastX = e.getX();
      lastY = e.getY();
      clamp();
      invalidate();
    }
    return true;
  }

  Bitmap copy(boolean crop) {
    if (!crop) return bitmap.copy(Bitmap.Config.ARGB_8888, false);
    float s = fit() * zoom;
    int left = Math.max(0, Math.round(bitmap.getWidth() / 2f - (getWidth() / 2f + dx) / s)),
        top = Math.max(0, Math.round(bitmap.getHeight() / 2f - (getHeight() / 2f + dy) / s));
    int
        right =
            Math.min(
                bitmap.getWidth(), Math.round(bitmap.getWidth() / 2f + (getWidth() / 2f - dx) / s)),
        bottom =
            Math.min(
                bitmap.getHeight(),
                Math.round(bitmap.getHeight() / 2f + (getHeight() / 2f - dy) / s));
    return Bitmap.createBitmap(
            bitmap, left, top, Math.max(1, right - left), Math.max(1, bottom - top))
        .copy(Bitmap.Config.ARGB_8888, false);
  }
}
