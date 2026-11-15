package com.hasbreo.still;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Geometric transport icons; independent of font metrics and glyph fallback. */
final class IconButton extends View {
  String kind;
  final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Path path = new Path();
  boolean primary;

  IconButton(Context c, String icon, boolean main, Runnable action) {
    super(c);
    kind = icon;
    primary = main;
    setMinimumWidth(0);
    setMinimumHeight(0);
    setPadding(0, 0, 0, 0);
    setStateListAnimator(null);
    setBackgroundTintList(null);
    Ui.surface(this, main ? Ui.MINT : Ui.CARD, main ? 29 : 18, main ? 0 : Ui.BORDER);
    setContentDescription(icon);
    setFocusable(true);
    setOnClickListener(v -> action.run());
  }

  @Override
  public CharSequence getAccessibilityClassName() {
    return "android.widget.Button";
  }

  void icon(String value) {
    kind = value;
    invalidate();
  }

  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    canvas.save();
    canvas.translate(getWidth() / 2f, getHeight() / 2f);
    float s = getResources().getDisplayMetrics().density;
    canvas.scale(s, s);
    ink.setColor(primary ? 0xff072d1c : Ui.TEXT);
    ink.setStyle(Paint.Style.FILL);
    path.reset();
    switch (kind) {
      case "play":
        path.moveTo(-5, -9);
        path.lineTo(10, 0);
        path.lineTo(-5, 9);
        path.close();
        canvas.drawPath(path, ink);
        break;
      case "pause":
        canvas.drawRoundRect(-7, -9, -2, 9, 1, 1, ink);
        canvas.drawRoundRect(2, -9, 7, 9, 1, 1, ink);
        break;
      case "previous":
        canvas.drawRoundRect(-9, -9, -6, 9, 1, 1, ink);
        path.moveTo(8, -9);
        path.lineTo(-3, 0);
        path.lineTo(8, 9);
        path.close();
        canvas.drawPath(path, ink);
        break;
      case "next":
        canvas.drawRoundRect(6, -9, 9, 9, 1, 1, ink);
        path.moveTo(-8, -9);
        path.lineTo(3, 0);
        path.lineTo(-8, 9);
        path.close();
        canvas.drawPath(path, ink);
        break;
      default:
        canvas.drawRoundRect(-10, -4, -5, 4, 1, 1, ink);
        path.moveTo(-5, -4);
        path.lineTo(1, -9);
        path.lineTo(1, 9);
        path.lineTo(-5, 4);
        path.close();
        canvas.drawPath(path, ink);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(2);
        ink.setStrokeCap(Paint.Cap.ROUND);
        if (kind.equals("muted")) {
          canvas.drawLine(6, -4, 12, 4, ink);
          canvas.drawLine(6, 4, 12, -4, ink);
        } else {
          canvas.drawArc(new RectF(-3, -7, 11, 7), -60, 120, false, ink);
          canvas.drawArc(new RectF(-5, -11, 17, 11), -55, 110, false, ink);
        }
        break;
    }
    canvas.restore();
  }
}
