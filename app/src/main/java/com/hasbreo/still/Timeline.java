package com.hasbreo.still;

import android.graphics.*;
import android.view.*;

final class Timeline extends View {
  final MainActivity a;
  final Bitmap[] thumbs = new Bitmap[8];
  final Paint p = new Paint(3);
  final Frames f;
  int drag = -1;
  float dragOffset;
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
    return Ui.dp(a, 12) + (getWidth() - Ui.dp(a, 24)) * i / (float) Math.max(1, f.times.length - 1);
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    float top = Ui.dp(a, a.trim ? 24 : 10),
        bottom = getHeight() - Ui.dp(a, 6),
        left = Ui.dp(a, 12),
        right = getWidth() - Ui.dp(a, 12),
        width = (right - left) / 8f;
    p.setStyle(Paint.Style.FILL);
    p.setColor(Ui.CARD);
    c.drawRoundRect(left, top, right, bottom, Ui.dp(a, 6), Ui.dp(a, 6), p);
    for (int i = 0; i < 8; i++)
      if (thumbs[i] != null)
        c.drawBitmap(
            thumbs[i], null, new RectF(left + i * width, top, left + (i + 1) * width, bottom), p);
    if (a.trim) {
      p.setColor(0x99000000);
      c.drawRect(left, top, x(a.start), bottom, p);
      c.drawRect(x(a.end), top, right, bottom, p);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(Ui.dp(a, 2));
      p.setColor(Ui.MINT);
      c.drawRect(x(a.start), top, x(a.end), bottom, p);
      p.setStyle(Paint.Style.FILL);
      handle(c, x(a.start), top, bottom, "Start", !a.endSelected);
      handle(c, x(a.end), top, bottom, "End", a.endSelected);
    }
    p.setColor(Color.WHITE);
    p.setStrokeWidth(Ui.dp(a, 2));
    c.drawLine(x(a.cursor), top, x(a.cursor), bottom, p);
    c.drawCircle(x(a.cursor), top, Ui.dp(a, 3), p);
  }

  void handle(Canvas c, float x, float top, float bottom, String label, boolean active) {
    int color = active ? Ui.MINT : 0xffcedbd3;
    float half = Ui.dp(a, 7);
    p.setColor(color);
    c.drawRoundRect(
        x - half, top - Ui.dp(a, 3), x + half, bottom + Ui.dp(a, 3), Ui.dp(a, 5), Ui.dp(a, 5), p);
    p.setColor(0xff234634);
    p.setStrokeWidth(Ui.dp(a, 1.5f));
    c.drawLine(x - Ui.dp(a, 2), top + Ui.dp(a, 10), x - Ui.dp(a, 2), bottom - Ui.dp(a, 10), p);
    c.drawLine(x + Ui.dp(a, 2), top + Ui.dp(a, 10), x + Ui.dp(a, 2), bottom - Ui.dp(a, 10), p);
    float width = Ui.dp(a, 44), l = Math.max(0, Math.min(getWidth() - width, x - width / 2));
    p.setColor(color);
    c.drawRoundRect(l, 0, l + width, Ui.dp(a, 18), Ui.dp(a, 9), Ui.dp(a, 9), p);
    p.setColor(0xff0b2d1c);
    p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    p.setTextSize(Ui.dp(a, 11));
    p.setTextAlign(Paint.Align.CENTER);
    c.drawText(label, l + width / 2, Ui.dp(a, 13), p);
    p.setTextAlign(Paint.Align.LEFT);
  }

  public boolean onTouchEvent(MotionEvent e) {
    if (a.busy) return false;
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      a.beginScrub();
      drag = -1;
      dragOffset = 0;
      if (a.trim) {
        float ds = Math.abs(e.getX() - x(a.start)), de = Math.abs(e.getX() - x(a.end));
        float touch = Ui.dp(a, 25);
        if (Math.min(ds, de) <= touch) {
          drag = ds == de ? (a.endSelected ? 1 : 0) : (de < ds ? 1 : 0);
          dragOffset = e.getX() - x(drag == 1 ? a.end : a.start);
        }
      }
      getParent().requestDisallowInterceptTouchEvent(true);
    }
    if (e.getActionMasked() == 0 || e.getActionMasked() == 2) {
      int target =
          Math.round(
              Math.max(0, Math.min(getWidth() - Ui.dp(a, 24), e.getX() - dragOffset - Ui.dp(a, 12)))
                  / (getWidth() - Ui.dp(a, 24))
                  * (f.times.length - 1));
      if (drag < 0) a.move(target);
      else a.boundary(target, drag == 1);
      return true;
    }
    if (e.getActionMasked() == MotionEvent.ACTION_UP
        || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      a.endScrub();
      getParent().requestDisallowInterceptTouchEvent(false);
      drag = -1;
      performClick();
      return true;
    }
    return true;
  }

  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    setSystemGestureExclusionRects(java.util.Collections.singletonList(new Rect(0, 0, w, h)));
  }

  public boolean performClick() {
    super.performClick();
    return true;
  }

  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    attached = false;
    if (a.scrubbing) a.endScrub();
    for (Bitmap b : thumbs) if (b != null) b.recycle();
  }
}
