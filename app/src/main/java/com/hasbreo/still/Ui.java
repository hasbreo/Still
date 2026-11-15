package com.hasbreo.still;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class Ui {
  static final int BG = 0xff0e1714,
      CARD = 0xff192620,
      BORDER = 0xff34483e,
      MINT = 0xff92f0c1,
      TEXT = 0xffeff8f2,
      MUTED = 0xffa6b8ad;

  static int dp(Context c, float n) {
    return Math.round(c.getResources().getDisplayMetrics().density * n);
  }

  static GradientDrawable shape(Context c, int color, int radius, int stroke) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(c, radius));
    if (stroke != 0) g.setStroke(dp(c, 1), stroke);
    return g;
  }

  static void surface(View v, int color, int radius, int stroke) {
    v.setBackground(shape(v.getContext(), color, radius, stroke));
  }

  static Button button(Context c, String text, Runnable run) {
    Button b = new Button(c);
    b.setText(text);
    b.setAllCaps(false);
    b.setTextSize(14);
    b.setTypeface(
        android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
    b.setTextColor(TEXT);
    b.setMinHeight(dp(c, 48));
    b.setMinimumHeight(0);
    b.setMinWidth(0);
    b.setPadding(dp(c, 14), 0, dp(c, 14), 0);
    b.setStateListAnimator(null);
    b.setBackgroundTintList(null);
    b.setBackground(
        new RippleDrawable(ColorStateList.valueOf(0x338ff5bd), shape(c, CARD, 16, BORDER), null));
    b.setOnClickListener(v -> run.run());
    return b;
  }

  static void primary(Button b) {
    b.setTextColor(0xff062518);
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x33000000), shape(b.getContext(), MINT, 18, 0), null));
  }

  static void selected(Button b, boolean selected) {
    if (Boolean.valueOf(selected).equals(b.getTag())) return;
    b.setTag(selected);
    b.setSelected(selected);
    b.setTextColor(selected ? MINT : TEXT);
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x338ff5bd),
            shape(b.getContext(), selected ? 0xff173e2d : CARD, 14, selected ? MINT : BORDER),
            null));
  }

  static LinearLayout column(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  static TextView text(Context c, String value, int size, int color) {
    TextView t = new TextView(c);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setFontFeatureSettings("tnum");
    return t;
  }

  static Dialog sheet(Activity a, String title, LinearLayout content) {
    Dialog d = new Dialog(a);
    d.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout outer = column(a);
    outer.setPadding(dp(a, 22), dp(a, 12), dp(a, 22), dp(a, 24));
    surface(outer, CARD, 26, BORDER);
    View handle = new View(a);
    surface(handle, MUTED, 3, 0);
    LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(a, 36), dp(a, 4));
    hp.gravity = Gravity.CENTER;
    hp.bottomMargin = dp(a, 18);
    outer.addView(handle, hp);
    TextView heading = text(a, title, 23, TEXT);
    heading.setTypeface(null, android.graphics.Typeface.BOLD);
    outer.addView(heading);
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
    cp.topMargin = dp(a, 18);
    outer.addView(content, cp);
    d.setContentView(outer);
    Window w = d.getWindow();
    if (w != null) {
      w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      w.setDimAmount(.62f);
      w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      w.setGravity(Gravity.BOTTOM);
      w.setLayout(-1, -2);
    }
    return d;
  }

  static void options(
      Activity a, String title, String[] names, java.util.function.IntConsumer action) {
    LinearLayout body = column(a);
    Dialog d = sheet(a, title, body);
    for (int i = 0; i < names.length; i++) {
      final int n = i;
      Button b =
          button(
              a,
              names[i],
              () -> {
                d.dismiss();
                action.accept(n);
              });
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(a, 54));
      p.bottomMargin = dp(a, 10);
      body.addView(b, p);
    }
    Button cancel = button(a, "Cancel", d::dismiss);
    surface(cancel, CARD, 14, 0);
    body.addView(cancel);
    d.show();
    d.getWindow().setLayout(-1, -2);
  }

  static void message(Activity a, String title, String message, String ok, Runnable action) {
    LinearLayout body = column(a);
    TextView t = text(a, message, 15, MUTED);
    t.setLineSpacing(dp(a, 4), 1);
    body.addView(t);
    Dialog d = sheet(a, title, body);
    Button button =
        button(
            a,
            ok,
            () -> {
              d.dismiss();
              if (action != null) action.run();
            });
    primary(button);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(a, 50));
    p.topMargin = dp(a, 22);
    body.addView(button, p);
    d.show();
    d.getWindow().setLayout(-1, -2);
  }

  static void confirm(
      Activity a, String title, String message, String ok, Runnable action, Runnable cancel) {
    LinearLayout body = column(a);
    body.addView(text(a, message, 15, MUTED));
    Dialog d = sheet(a, title, body);
    Button yes =
        button(
            a,
            ok,
            () -> {
              d.dismiss();
              action.run();
            });
    primary(yes);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(a, 50));
    p.topMargin = dp(a, 20);
    body.addView(yes, p);
    Button no =
        button(
            a,
            "Cancel",
            () -> {
              d.dismiss();
              if (cancel != null) cancel.run();
            });
    LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, dp(a, 48));
    np.topMargin = dp(a, 8);
    body.addView(no, np);
    d.setOnCancelListener(
        x -> {
          if (cancel != null) cancel.run();
        });
    d.show();
    d.getWindow().setLayout(-1, -2);
  }
}
