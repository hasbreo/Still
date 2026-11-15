package com.hasbreo.still;

import android.graphics.*;
import android.view.*;

final class Timeline extends View {
  final MainActivity a;
  final Bitmap[] thumbs = new Bitmap[8];
  final Paint p = new Paint(3);
  final Frames f;
  int drag = -1;
  boolean attached = true;

  Timeline(MainActivity activity) {
    super(activity);
    a = activity;
    f = a.video;
    setContentDescription("Video filmstrip. Drag to seek; in Trim, drag a start or end boundary.");
    a.work.execute(
        () -> {
          for (int i = 0; i < 8; i++) {
            final int n = i;
            try {
              Bitmap b = f.thumb((int) ((f.times.length - 1) * i / 7f));
              a.runOnUiThread(
                  () -> {
                    if (a.video != f || a.destroyed || !attached) {
                      if (b != null) b.recycle();
                      return;
                    }
                    thumbs[n] = b;
                    invalidate();
                  });
            } catch (Exception ignored) {
            }
          }
        });
  }

  float x(int i) {
    return getWidth() * i / (float) Math.max(1, f.times.length - 1);
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    float width = getWidth() / 8f;
    for (int i = 0; i < 8; i++) {
      if (thumbs[i] != null)
        c.drawBitmap(thumbs[i], null, new RectF(i * width, 0, (i + 1) * width, getHeight()), p);
    }
    if (a.trim) {
      p.setColor(0xaa000000);
      c.drawRect(0, 0, x(a.start), getHeight(), p);
      c.drawRect(x(a.end), 0, getWidth(), getHeight(), p);
      for (int i : new int[] {a.start, a.end}) {
        p.setColor((i == a.end) == a.endSelected ? MainActivity.MINT : Color.LTGRAY);
        c.drawRoundRect(x(i) - 5, 0, x(i) + 5, getHeight(), 4, 4, p);
      }
    }
    p.setColor(MainActivity.MINT);
    p.setStrokeWidth(3);
    c.drawLine(x(a.cursor), 0, x(a.cursor), getHeight(), p);
  }

  public boolean onTouchEvent(MotionEvent e) {
    if (a.busy) return false;
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      a.pause();
      drag = -1;
      if (a.trim) {
        float ds = Math.abs(e.getX() - x(a.start)), de = Math.abs(e.getX() - x(a.end));
        float touch = Ui.dp(a, 25);
        if (Math.min(ds, de) <= touch) drag = de < ds ? 1 : 0;
      }
      getParent().requestDisallowInterceptTouchEvent(true);
    }
    if (e.getActionMasked() == 0 || e.getActionMasked() == 2) {
      int target =
          Math.round(
              Math.max(0, Math.min(getWidth(), e.getX())) / getWidth() * (f.times.length - 1));
      if (drag < 0) a.move(target);
      else a.boundary(target, drag == 1);
      return true;
    }
    if (e.getActionMasked() == 1) {
      performClick();
      return true;
    }
    return true;
  }

  public boolean performClick() {
    super.performClick();
    return true;
  }

  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    attached = false;
    for (Bitmap b : thumbs) if (b != null) b.recycle();
  }
}
