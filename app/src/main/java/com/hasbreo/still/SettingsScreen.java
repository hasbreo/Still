package com.hasbreo.still;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class SettingsScreen {
  final MainActivity a;
  Dialog dialog;
  LinearLayout content;
  TextView status;
  EditText server, key;
  boolean png;
  int quality, hold;

  SettingsScreen(MainActivity activity) {
    a = activity;
  }

  TextView text(String s, int size, int color) {
    return Ui.text(a, s, size, color);
  }

  void section(String s) {
    TextView title = text(s.toUpperCase(java.util.Locale.ROOT), 11, Ui.MINT);
    title.setLetterSpacing(.13f);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, Ui.dp(a, 24), 0, Ui.dp(a, 10));
    content.addView(title, p);
  }

  LinearLayout card() {
    LinearLayout l = Ui.column(a);
    l.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
    Ui.surface(l, Ui.CARD, 20, Ui.BORDER);
    content.addView(l);
    return l;
  }

  EditText field(String value, String hint, boolean secret) {
    EditText e = new EditText(a);
    e.setText(value);
    e.setHint(hint);
    e.setSingleLine(true);
    e.setTextSize(14);
    e.setTextColor(Ui.TEXT);
    e.setHintTextColor(Ui.MUTED);
    e.setInputType(secret ? 129 : 17);
    e.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
    Ui.surface(e, Ui.BG, 12, Ui.BORDER);
    return e;
  }

  void addField(LinearLayout l, EditText e) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Ui.dp(a, 50));
    p.setMargins(0, Ui.dp(a, 8), 0, Ui.dp(a, 12));
    l.addView(e, p);
  }

  void show() {
    dialog = new Dialog(a);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout root = Ui.column(a);
    root.setBackgroundColor(Ui.BG);
    root.setPadding(Ui.dp(a, 20), Ui.dp(a, 18), Ui.dp(a, 20), Ui.dp(a, 16));
    LinearLayout top = new LinearLayout(a);
    top.setGravity(Gravity.CENTER_VERTICAL);
    Button back = Ui.button(a, "‹", dialog::dismiss);
    Ui.surface(back, Ui.BG, 14, 0);
    back.setTextSize(26);
    top.addView(back, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 48)));
    TextView name = text("Settings", 25, Ui.TEXT);
    name.setTypeface(null, android.graphics.Typeface.BOLD);
    top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(top);
    ScrollView scroll = new ScrollView(a);
    scroll.setFillViewport(true);
    content = Ui.column(a);
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    section("Immich");
    LinearLayout connection = card();
    LinearLayout heading = new LinearLayout(a);
    heading.addView(
        text("Connect to Immich", 17, Ui.TEXT), new LinearLayout.LayoutParams(0, -2, 1));
    Button info =
        Ui.button(
            a,
            "ⓘ",
            () ->
                Ui.message(
                    a,
                    "Why connect?",
                    "Local frame capture and trimming work without a connection.\n\n"
                        + "An Immich API key lets Freeze identify the original video, preserve its"
                        + " albums and supported metadata, upload a copy and move the original to"
                        + " trash when replacing it.\n\n"
                        + "Create a key in Immich’s Account settings → API keys. It needs asset"
                        + " read/upload/update/delete, album read/update and tag read/update"
                        + " permissions. Keep the key private.",
                    "Got it",
                    null));
    heading.addView(info, new LinearLayout.LayoutParams(Ui.dp(a, 42), Ui.dp(a, 42)));
    connection.addView(heading);
    status =
        text(
            a.prefs.getString("connection", "Not connected · local editing available"),
            13,
            Ui.MUTED);
    connection.addView(status);
    server =
        field(
            a.prefs.getString("server", "https://photos.hasbreo.com"),
            "https://your-immich-server",
            false);
    key = field(a.prefs.getString("key", ""), "Paste your Immich API key", true);
    addField(connection, server);
    addField(connection, key);
    Button connect =
        Ui.button(
            a,
            "Save & test connection",
            () -> {
              if (!save()) return;
              status.setText("Checking connection…");
              a.work.execute(
                  () -> {
                    try {
                      new Immich(a).request("/users/me", "GET", null);
                      a.prefs
                          .edit()
                          .putString("connection", "Connected")
                          .putBoolean("gateway", false)
                          .apply();
                      a.runOnUiThread(
                          () -> {
                            status.setText("Connected");
                            if (a.video != null) a.matchSource();
                          });
                    } catch (Exception error) {
                      a.runOnUiThread(
                          () -> {
                            status.setText("Connection needs attention");
                            Ui.message(a, "Could not connect", error.getMessage(), "Got it", null);
                            if (a.prefs.getBoolean("gateway", false)) gateway(connection);
                          });
                    }
                  });
            });
    Ui.primary(connect);
    connection.addView(connect, new LinearLayout.LayoutParams(-1, Ui.dp(a, 48)));
    if (a.prefs.getBoolean("gateway", false)) gateway(connection);
    section("Frame export");
    LinearLayout export = card();
    export.addView(text("Image format", 16, Ui.TEXT));
    LinearLayout formats = new LinearLayout(a);
    png = a.prefs.getBoolean("png", false);
    Button jpeg = Ui.button(a, "JPEG", () -> {}), lossless = Ui.button(a, "PNG", () -> {});
    Runnable styles =
        () -> {
          Ui.selected(jpeg, !png);
          Ui.selected(lossless, png);
        };
    jpeg.setOnClickListener(
        v -> {
          png = false;
          styles.run();
        });
    lossless.setOnClickListener(
        v -> {
          png = true;
          styles.run();
        });
    a.rowButton(formats, jpeg, 44);
    a.rowButton(formats, lossless, 44);
    export.addView(formats);
    styles.run();
    quality = a.prefs.getInt("quality", 95);
    TextView qualityText = text("JPEG quality  " + quality, 14, Ui.TEXT);
    export.addView(qualityText);
    SeekBar slider = new SeekBar(a);
    slider.setMax(30);
    slider.setProgress(quality - 70);
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar v) {}

          public void onStopTrackingTouch(SeekBar v) {}

          public void onProgressChanged(SeekBar v, int p, boolean user) {
            quality = p + 70;
            qualityText.setText("JPEG quality  " + quality);
          }
        });
    export.addView(slider);
    export.addView(
        text(
            "PNG is lossless and usually larger. Full frame keeps the original dimensions.",
            12,
            Ui.MUTED));
    section("Controls");
    LinearLayout control = card();
    control.addView(text("Hold-to-step response", 16, Ui.TEXT));
    hold = a.prefs.getInt("hold", 300);
    LinearLayout speeds = new LinearLayout(a);
    Button slow = Ui.button(a, "Relaxed", () -> {}),
        medium = Ui.button(a, "Normal", () -> {}),
        fast = Ui.button(a, "Quick", () -> {});
    Button[] bs = {slow, medium, fast};
    int[] vals = {500, 300, 180};
    Runnable speedStyle =
        () -> {
          for (int i = 0; i < 3; i++) Ui.selected(bs[i], hold == vals[i]);
        };
    for (int i = 0; i < 3; i++) {
      final int n = i;
      bs[i].setOnClickListener(
          v -> {
            hold = vals[n];
            speedStyle.run();
          });
      a.rowButton(speeds, bs[i], 44);
    }
    speedStyle.run();
    control.addView(speeds);
    section("Updates");
    LinearLayout updates = card();
    Switch auto = new Switch(a);
    auto.setText("Check automatically");
    auto.setTextSize(15);
    auto.setTextColor(Ui.TEXT);
    auto.setChecked(a.prefs.getBoolean("auto", true));
    auto.setOnCheckedChangeListener((v, yes) -> a.prefs.edit().putBoolean("auto", yes).apply());
    updates.addView(auto);
    TextView version = text("Installed version  0.3.0", 13, Ui.MUTED);
    version.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 10));
    updates.addView(version);
    updates.addView(
        Ui.button(a, "Check for updates", () -> new Updater(a).check(true)),
        new LinearLayout.LayoutParams(-1, Ui.dp(a, 48)));
    section("Saved on your phone");
    content.addView(
        text(
            "Pictures/Still  ·  Movies/Still\nEnable these folders in your photo-backup apps.",
            13,
            Ui.MUTED));
    Button done =
        Ui.button(
            a,
            "Save settings",
            () -> {
              if (save()) {
                a.prefs
                    .edit()
                    .putBoolean("png", png)
                    .putInt("quality", quality)
                    .putInt("hold", hold)
                    .apply();
                dialog.dismiss();
              }
            });
    Ui.primary(done);
    LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, Ui.dp(a, 52));
    dp.topMargin = Ui.dp(a, 16);
    root.addView(done, dp);
    dialog.setContentView(root);
    dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Ui.BG));
    dialog.getWindow().setStatusBarColor(Ui.BG);
    dialog.getWindow().setNavigationBarColor(Ui.BG);
    dialog.show();
    dialog.getWindow().setLayout(-1, -1);
  }

  boolean save() {
    String s = server.getText().toString().trim().replaceAll("/+$", "");
    if (!s.startsWith("https://")) {
      Ui.message(
          a,
          "Server address",
          "Use the full HTTPS address for your Immich server.",
          "Got it",
          null);
      return false;
    }
    String value = key.getText().toString().trim();
    if (!s.equals(a.prefs.getString("server", "")) || !value.equals(a.prefs.getString("key", ""))) {
      a.sourceId = null;
      a.source = null;
      a.prefs
          .edit()
          .putString(
              "connection",
              value.isEmpty() ? "Not connected · local editing available" : "Connection not tested")
          .apply();
    }
    a.prefs.edit().putString("server", s).putString("key", value).apply();
    return true;
  }

  void gateway(LinearLayout l) {
    if (l.findViewWithTag("gateway") != null) return;
    Button sign =
        Ui.button(
            a,
            "Authenticate server access",
            () -> {
              if (save()) {
                Intent i = new Intent(a, AccessActivity.class);
                i.putExtra("url", a.prefs.getString("server", ""));
                a.startActivity(i);
              }
            });
    sign.setTag("gateway");
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Ui.dp(a, 48));
    p.topMargin = Ui.dp(a, 12);
    l.addView(sign, p);
    l.addView(
        text(
            "Your server’s access gate needs a browser sign-in. This is separate from the Immich"
                + " API key.",
            12,
            Ui.MUTED));
  }
}
