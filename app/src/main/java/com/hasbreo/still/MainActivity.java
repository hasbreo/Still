package com.hasbreo.still;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.widget.*;
import androidx.exifinterface.media.ExifInterface;
import androidx.media3.common.*;
import androidx.media3.transformer.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
  static final int BG = 0xff0e1714, MINT = 0xff92f0c1, FG = 0xffeff7f2;
  final Handler handler = new Handler();
  final ExecutorService work = Executors.newSingleThreadExecutor();
  final ExecutorService renders = Executors.newSingleThreadExecutor();
  PreviewDecoder decoder;
  Frames decoderSource;
  boolean previewBoundary = false;
  int displayIndex = -1;
  volatile boolean cancelSharp = false;
  boolean editing = false;
  TextView progressLabel;
  android.content.SharedPreferences prefs;
  LinearLayout root, controls;
  ZoomView preview;
  VideoView player;
  FrameLayout stage;
  TextView clock, status;
  Button startButton, endButton, saveButton, playButton;
  SeekBar seek;
  Timeline timeline;
  Frames video;
  File input;
  int cursor, start, end;
  boolean trim = false, endSelected = false, playing = false, busy = false, destroyed = false;
  long captured = 0;
  String sourceId;
  JSONObjectHolder source;
  Transformer transformer;
  Runnable repeat;
  int renderToken = 0;
  boolean renderQueued = false;
  int requestedFrame;
  Dialog progress;

  protected void onCreate(Bundle b) {
    super.onCreate(b);
    prefs = getSharedPreferences("still", 0);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    if (Build.VERSION.SDK_INT >= 35)
      getWindow()
          .getDecorView()
          .setOnApplyWindowInsetsListener(
              (v, in) -> {
                android.graphics.Insets i = in.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return in;
              });
    home();
    receive(getIntent());
    if (prefs.getBoolean("auto", true)
        && System.currentTimeMillis() - prefs.getLong("checked", 0) > 86400000L)
      new Updater(this).check(false);
  }

  void receive(Intent i) {
    Uri u =
        Intent.ACTION_SEND.equals(i.getAction())
            ? i.getParcelableExtra(Intent.EXTRA_STREAM)
            : i.getData();
    if (u != null) open(u);
  }

  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    receive(i);
  }

  TextView label(String t, int size) {
    TextView v = new TextView(this);
    v.setText(t);
    v.setTextColor(FG);
    v.setTextSize(size);
    v.setPadding(8, 6, 8, 6);
    return v;
  }

  int dp(int p) {
    return (int) (getResources().getDisplayMetrics().density * p);
  }

  Button button(String t, Runnable r) {
    return Ui.button(this, t, r);
  }

  void rowButton(LinearLayout row, Button b, int height) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(height), 1);
    p.setMargins(dp(4), dp(3), dp(4), dp(3));
    row.addView(b, p);
  }

  void base() {
    root = Ui.column(this);
    root.setPadding(dp(16), 0, dp(16), dp(8));
    root.setBackgroundColor(BG);
    setContentView(root);
    LinearLayout bar = new LinearLayout(this);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    Button close =
        button(
            editing ? "×" : "◉",
            () -> {
              if (editing) closeVideo();
            });
    close.setTextSize(25);
    Ui.surface(close, BG, 16, 0);
    bar.addView(close, new LinearLayout.LayoutParams(dp(42), dp(46)));
    TextView name = label("Still", 23);
    name.setTypeface(null, android.graphics.Typeface.BOLD);
    bar.addView(name, new LinearLayout.LayoutParams(0, dp(54), 1));
    Button menu =
        button(
            "⋮",
            () ->
                Ui.options(
                    this,
                    "Still",
                    new String[] {
                      "Choose another video", "Settings", "Check for updates", "About Still"
                    },
                    which -> {
                      if (which == 0) choose();
                      if (which == 1) settings();
                      if (which == 2) new Updater(this).check(true);
                      if (which == 3)
                        message(
                            "Still 0.2.0",
                            "Frame capture and precise trimming. Processed on your phone. Made at"
                                + " Hasbreo.");
                    }));
    menu.setTextSize(24);
    Ui.surface(menu, BG, 16, 0);
    bar.addView(menu, new LinearLayout.LayoutParams(dp(42), dp(46)));
    root.addView(bar);
  }

  void closeVideo() {
    if (busy) {
      toast("Finish or cancel processing first");
      return;
    }
    pause();
    editing = false;
    renderToken++;
    Frames f = video;
    File old = input;
    video = null;
    input = null;
    sourceId = null;
    source = null;
    displayIndex = -1;
    home();
    work.execute(
        () -> {
          if (f != null) f.close();
          if (old != null) old.delete();
        });
    renders.execute(
        () -> {
          if (decoder != null) {
            decoder.close();
            decoder = null;
            decoderSource = null;
          }
        });
  }

  void home() {
    editing = false;
    base();
    LinearLayout centre = Ui.column(this);
    centre.setGravity(Gravity.CENTER_VERTICAL);
    centre.setPadding(dp(8), dp(16), dp(8), dp(20));
    TextView badge = label("VIDEO • ONE MOMENT AT A TIME", 11);
    badge.setTextColor(MINT);
    badge.setLetterSpacing(.12f);
    centre.addView(badge);
    TextView title = label("Keep the moment.\nMake the cut.", 36);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    centre.addView(title);
    TextView intro =
        label("A clearer frame. A precise cut.\nYour videos, just how you want them.", 17);
    intro.setTextColor(Ui.MUTED);
    intro.setLineSpacing(dp(6), 1);
    centre.addView(intro);
    Button choose = button("＋  Choose video", this::choose);
    Ui.primary(choose);
    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(56));
    bp.setMargins(0, dp(30), 0, dp(14));
    centre.addView(choose, bp);
    TextView hint = label("Or share a video from Immich into Still.", 14);
    hint.setTextColor(Ui.MUTED);
    centre.addView(hint);
    root.addView(centre, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout card = Ui.column(this);
    Ui.surface(card, Ui.CARD, 20, Ui.BORDER);
    card.setPadding(dp(14), dp(12), dp(14), dp(12));
    card.addView(label("Made for your gallery", 16));
    TextView detail = label("Photos → Pictures/Still\nVideos → Movies/Still", 13);
    detail.setTextColor(Ui.MUTED);
    card.addView(detail);
    root.addView(card);
    TextView foot = label("HASBREO  ·  STILL 0.2.0", 11);
    foot.setGravity(Gravity.CENTER);
    foot.setTextColor(Ui.MUTED);
    root.addView(foot);
  }

  void choose() {
    if (busy) return;
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.setType("video/*");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(i, 10);
  }

  protected void onActivityResult(int r, int c, Intent d) {
    super.onActivityResult(r, c, d);
    if (r == 10 && c == RESULT_OK && d != null) open(d.getData());
  }

  void open(Uri uri) {
    if (busy) return;
    pause();
    busy = true;
    showProgress("Opening video…");
    work.execute(
        () -> {
          try {
            File file = new File(getCacheDir(), "input-" + System.nanoTime() + ".video");
            try (InputStream in = getContentResolver().openInputStream(uri);
                OutputStream out = new FileOutputStream(file)) {
              if (in == null) throw new IOException("Cannot read shared video");
              IO.copy(in, out);
            }
            Frames f = new Frames(file);
            long date = parseDate(f.date);
            if (date == 0) {
              try (android.database.Cursor q =
                  getContentResolver().query(uri, new String[] {"datetaken"}, null, null, null)) {
                if (q != null && q.moveToFirst()) date = q.getLong(0);
              } catch (Exception ignored) {
              }
            }
            final long taken = date;
            runOnUiThread(
                () -> {
                  Frames old = video;
                  File previous = input;
                  video = f;
                  editing = true;
                  displayIndex = -1;
                  input = file;
                  if (old != null)
                    work.execute(
                        () -> {
                          old.close();
                          if (previous != null) previous.delete();
                        });
                  captured = taken;
                  cursor = start = 0;
                  end = video.times.length - 1;
                  sourceId = null;
                  source = null;
                  trim = false;
                  busy = false;
                  closeProgress();
                  editor();
                  matchSource();
                });
          } catch (Exception e) {
            fail(e);
          }
        });
  }

  static long parseDate(String s) {
    if (s == null) return 0;
    try {
      return Instant.parse(s).toEpochMilli();
    } catch (Exception ignored) {
    }
    for (String p : new String[] {"yyyyMMdd'T'HHmmss.SSSX", "yyyyMMdd'T'HHmmssX"})
      try {
        return OffsetDateTime.parse(s, DateTimeFormatter.ofPattern(p)).toInstant().toEpochMilli();
      } catch (Exception ignored) {
      }
    return 0;
  }

  void editor() {
    editing = true;
    base();
    stage = new FrameLayout(this);
    preview = new ZoomView(this);
    player = new VideoView(this);
    FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER);
    stage.addView(player, vp);
    stage.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout outer = root;
    LinearLayout layout = Ui.column(this);
    boolean landscape =
        getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    layout.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
    outer.addView(layout, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout panel = Ui.column(this);
    if (landscape) {
      layout.addView(stage, new LinearLayout.LayoutParams(0, -1, 1));
      ScrollView scrolling = new ScrollView(this);
      scrolling.addView(panel);
      LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(320), -1);
      cp.leftMargin = dp(12);
      layout.addView(scrolling, cp);
    } else {
      layout.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
      layout.addView(panel, new LinearLayout.LayoutParams(-1, -2));
    }
    root = panel;
    player.setVideoPath(input.getPath());
    player.setOnCompletionListener(mp -> pause());
    clock = label("", 12);
    clock.setGravity(Gravity.CENTER);
    clock.setTextColor(Ui.MUTED);
    root.addView(clock, new LinearLayout.LayoutParams(-1, dp(28)));
    timeline = new Timeline(this);
    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, dp(44));
    tp.setMargins(dp(5), dp(4), dp(5), dp(4));
    root.addView(timeline, tp);
    seek = new SeekBar(this);
    seek.setMax(video.times.length - 1);
    seek.setProgress(cursor);
    seek.setProgressTintList(android.content.res.ColorStateList.valueOf(MINT));
    seek.setThumbTintList(android.content.res.ColorStateList.valueOf(MINT));
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar v) {
            pause();
            previewBoundary = false;
          }

          public void onStopTrackingTouch(SeekBar v) {
            requestFrame();
          }

          public void onProgressChanged(SeekBar v, int p, boolean user) {
            if (user) move(p);
          }
        });
    root.addView(seek, new LinearLayout.LayoutParams(-1, dp(24)));
    LinearLayout ends = new LinearLayout(this);
    startButton = button("", () -> selectBoundary(false));
    endButton = button("", () -> selectBoundary(true));
    rowButton(ends, startButton, 48);
    rowButton(ends, endButton, 48);
    root.addView(ends);
    ends.setVisibility(trim ? View.VISIBLE : View.GONE);
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER);
    Button prev = button("Ⅰ‹", () -> step(-1));
    playButton = button("▶", this::togglePlay);
    Button next = button("›Ⅰ", () -> step(1));
    prev.setTextSize(23);
    next.setTextSize(23);
    playButton.setTextSize(22);
    Ui.primary(playButton);
    prev.setContentDescription("Previous frame. Hold to repeat");
    next.setContentDescription("Next frame. Hold to repeat");
    hold(prev, -1);
    hold(next, 1);
    for (Button b : new Button[] {prev, playButton, next}) {
      LinearLayout.LayoutParams rp =
          new LinearLayout.LayoutParams(
              dp(b == playButton ? 58 : 52), dp(b == playButton ? 58 : 48));
      rp.setMargins(dp(16), dp(7), dp(16), dp(7));
      row.addView(b, rp);
    }
    root.addView(row);
    LinearLayout modes = new LinearLayout(this);
    Ui.surface(modes, Ui.CARD, 18, Ui.BORDER);
    Button capture =
        button(
            "Capture frame",
            () -> {
              pause();
              trim = false;
              previewBoundary = false;
              editor();
            });
    Button cut =
        button(
            "Trim video",
            () -> {
              pause();
              trim = true;
              previewBoundary = false;
              editor();
            });
    if (!trim) Ui.primary(capture);
    if (trim) Ui.primary(cut);
    rowButton(modes, capture, 40);
    rowButton(modes, cut, 40);
    LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
    mp.setMargins(0, dp(4), 0, dp(6));
    root.addView(modes, mp);
    if (!trim) {
      Button sharp = button("✧  Find sharper nearby   ±1 second", this::sharper);
      sharp.setTextColor(MINT);
      LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(46));
      sp.setMargins(0, dp(2), 0, dp(8));
      root.addView(sharp, sp);
    }
    saveButton =
        button(
            trim ? "Save trimmed video" : "Save frame",
            () -> {
              pause();
              if (trim) trimOptions();
              else frameOptions();
            });
    Ui.primary(saveButton);
    root.addView(saveButton, new LinearLayout.LayoutParams(-1, dp(52)));
    status = label(connectionStatus(), 11);
    status.setGravity(Gravity.CENTER);
    status.setTextColor(Ui.MUTED);
    root.addView(status, new LinearLayout.LayoutParams(-1, dp(24)));
    refresh();
    requestFrame();
  }

  String connectionStatus() {
    if (sourceId != null) return "Immich original matched";
    return prefs.getString("key", "").isEmpty()
        ? "Local saving · connect Immich in Settings for replacement"
        : "Immich original not matched · local saving available";
  }

  void selectBoundary(boolean isEnd) {
    pause();
    endSelected = isEnd;
    previewBoundary = true;
    refresh();
    requestFrame();
  }

  void refresh() {
    if (video == null || clock == null) return;
    int wanted = previewBoundary ? (endSelected ? end : start) : cursor;
    clock.setText(
        time(video.times[wanted])
            + "  /  "
            + time(video.duration)
            + (previewBoundary ? "   ·   " + (endSelected ? "End" : "Start") : "")
            + (displayIndex != wanted && !playing ? "   ·   seeking" : ""));
    seek.setProgress(cursor);
    if (timeline != null) timeline.invalidate();
    startButton.setText("Start  " + time(video.times[start]));
    endButton.setText("End  " + time(video.times[end]));
    Ui.selected(startButton, trim && !endSelected);
    Ui.selected(endButton, trim && endSelected);
  }

  static String time(long us) {
    long ms = us / 1000;
    return String.format(Locale.US, "%02d:%02d.%03d", ms / 60000, ms / 1000 % 60, ms % 1000);
  }

  void move(int frame) {
    if (busy || video == null) return;
    previewBoundary = false;
    cursor = Math.max(0, Math.min(video.times.length - 1, frame));
    refresh();
    requestFrame();
  }

  void boundary(int frame, boolean isEnd) {
    if (busy || video == null) return;
    pause();
    endSelected = isEnd;
    previewBoundary = true;
    int target = Math.max(0, Math.min(video.times.length - 1, frame));
    if (isEnd) end = Math.max(start, target);
    else start = Math.min(end, target);
    refresh();
    requestFrame();
  }

  void step(int d) {
    if (video == null || busy) return;
    pause();
    if (trim) boundary((endSelected ? end : start) + d, endSelected);
    else move(cursor + d);
  }

  void hold(Button b, int direction) {
    b.setOnTouchListener(
        (v, e) -> {
          switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
              step(direction);
              final long began = System.currentTimeMillis();
              repeat =
                  new Runnable() {
                    public void run() {
                      step(direction);
                      handler.postDelayed(
                          this, System.currentTimeMillis() - began > 1800 ? 65 : 140);
                    }
                  };
              handler.postDelayed(repeat, prefs.getInt("hold", 300));
              return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
              if (repeat != null) handler.removeCallbacks(repeat);
              repeat = null;
              v.performClick();
              return true;
          }
          return false;
        });
    b.setOnClickListener(v -> {});
  }

  void requestFrame() {
    if (video == null) return;
    requestedFrame = previewBoundary ? (endSelected ? end : start) : cursor;
    if (renderQueued) return;
    renderQueued = true;
    final Frames f = video;
    final int target = requestedFrame;
    renders.execute(
        () -> {
          try {
            if (decoderSource != f) {
              if (decoder != null) decoder.close();
              decoder = new PreviewDecoder(f, 960);
              decoderSource = f;
            }
            Bitmap b;
            try {
              b = decoder.get(target);
            } catch (Exception fallback) {
              decoder.close();
              decoder = new PreviewDecoder(f, 960);
              b = f.preview(target);
            }
            final Bitmap image = b;
            runOnUiThread(
                () -> {
                  renderQueued = false;
                  if (destroyed || f != video || !editing) {
                    if (image != null) image.recycle();
                    if (video != null && editing) requestFrame();
                    return;
                  }
                  if (image != null) {
                    preview.setBitmap(image, false);
                    displayIndex = target;
                    refresh();
                  }
                  if (target != requestedFrame) requestFrame();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  renderQueued = false;
                  if (f == video && editing) toast("Could not decode this frame");
                });
          }
        });
  }

  void togglePlay() {
    if (busy) return;
    if (playing) {
      pause();
      return;
    }
    previewBoundary = false;
    playing = true;
    preview.setVisibility(View.INVISIBLE);
    player.seekTo((int) (video.times[cursor] / 1000));
    player.start();
    playButton.setText("Ⅱ");
    handler.post(ticker);
  }

  final Runnable ticker =
      new Runnable() {
        public void run() {
          if (!playing) return;
          cursor = video.nearest(player.getCurrentPosition() * 1000L);
          refresh();
          handler.postDelayed(this, 100);
        }
      };

  void pause() {
    if (playing && player != null) {
      cursor = video.nearest(player.getCurrentPosition() * 1000L);
      player.pause();
      playing = false;
      preview.setVisibility(View.VISIBLE);
      playButton.setText("▶");
      handler.removeCallbacks(ticker);
      requestFrame();
    }
  }

  void sharper() {
    if (busy) return;
    pause();
    busy = true;
    cancelSharp = false;
    showProgress("Finding a sharper moment…");
    final int original = cursor;
    final Frames f = video;
    final long centre = f.times[cursor];
    LinearLayout body = (LinearLayout) progressLabel.getParent();
    Button cancel = button("Cancel search", () -> cancelSharp = true);
    body.addView(cancel);
    work.execute(
        () -> {
          try (PreviewDecoder scan = new PreviewDecoder(f, 192)) {
            int best = original;
            double score = -1;
            int low = f.nearest(Math.max(0, centre - 1000000)),
                high = f.nearest(Math.min(f.duration, centre + 1000000));
            for (int i = low; i <= high && !cancelSharp; i++) {
              Bitmap b;
              try {
                b = scan.get(i);
              } catch (Exception fallback) {
                b = f.thumb(i);
              }
              if (b == null) continue;
              double v =
                  Frames.sharpness(b) * (1 - .12 * Math.abs(f.times[i] - centre) / 1000000.0);
              b.recycle();
              if (v > score) {
                score = v;
                best = i;
              }
              final int percent = (i - low + 1) * 100 / Math.max(1, high - low + 1);
              runOnUiThread(
                  () -> {
                    if (progressLabel != null)
                      progressLabel.setText("Comparing nearby frames · " + percent + "%");
                  });
            }
            final int suggestion = best;
            runOnUiThread(
                () -> {
                  busy = false;
                  closeProgress();
                  if (cancelSharp) return;
                  move(suggestion);
                  Ui.confirm(
                      this,
                      "A clearer moment?",
                      "Compare this frame with your original selection.",
                      "Keep suggested frame",
                      () -> {},
                      () -> move(original));
                });
          } catch (Exception e) {
            fail(e);
          }
        });
  }

  void frameOptions() {
    if (busy) return;
    Ui.options(
        this,
        "Save frame",
        new String[] {"Full frame · original dimensions", "Current crop · your zoomed view"},
        choice -> saveFrame(choice == 1));
  }

  void requireDate(Runnable action) {
    if (captured > 0) {
      action.run();
      return;
    }
    message(
        "Capture time unavailable",
        "This shared file did not retain a readable capture date. Connect Immich in Settings to"
            + " retrieve it from a checksum-matched original, or select a video that retains its"
            + " date. Nothing has been saved with a guessed date.");
  }

  void saveFrame(boolean crop) {
    if (busy) return;
    requireDate(
        () -> {
          busy = true;
          showProgress("Saving photo…");
          final int index = previewBoundary ? (endSelected ? end : start) : cursor;
          final float zoom = preview.zoom, dx = preview.dx, dy = preview.dy;
          final int vw = preview.getWidth(), vh = preview.getHeight();
          work.execute(
              () -> {
                try {
                  Bitmap original = video.frame(index);
                  Bitmap b = original;
                  if (crop) {
                    float fit =
                        Math.min(
                                (float) vw / original.getWidth(), (float) vh / original.getHeight())
                            * zoom;
                    int
                        l =
                            Math.max(
                                0, Math.round(original.getWidth() / 2f - (vw / 2f + dx) / fit)),
                        t =
                            Math.max(
                                0, Math.round(original.getHeight() / 2f - (vh / 2f + dy) / fit)),
                        r =
                            Math.min(
                                original.getWidth(),
                                Math.round(original.getWidth() / 2f + (vw / 2f - dx) / fit)),
                        bt =
                            Math.min(
                                original.getHeight(),
                                Math.round(original.getHeight() / 2f + (vh / 2f - dy) / fit));
                    b =
                        Bitmap.createBitmap(
                            original, l, t, Math.max(1, r - l), Math.max(1, bt - t));
                  }
                  long when = captured + video.times[index] / 1000;
                  boolean png = prefs.getBoolean("png", false);
                  File f =
                      new File(
                          getCacheDir(),
                          "Still-" + System.currentTimeMillis() + (png ? ".png" : ".jpg"));
                  try (OutputStream out = new FileOutputStream(f)) {
                    b.compress(
                        png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                        prefs.getInt("quality", 95),
                        out);
                  }
                  if (b != original) b.recycle();
                  original.recycle();
                  ExifInterface ex = new ExifInterface(f);
                  java.text.SimpleDateFormat fmt =
                      new java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
                  ex.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, fmt.format(new Date(when)));
                  ex.setAttribute(
                      ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                      String.format(Locale.US, "%03d", when % 1000));
                  ex.setAttribute(ExifInterface.TAG_DATETIME, fmt.format(new Date(when)));
                  ex.setAttribute(
                      ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                      OffsetDateTime.ofInstant(Instant.ofEpochMilli(when), ZoneId.systemDefault())
                          .getOffset()
                          .getId());
                  ex.saveAttributes();
                  publishLocal(f, false, when);
                  f.delete();
                  runOnUiThread(
                      () -> {
                        busy = false;
                        closeProgress();
                        toast("Saved to Pictures/Still");
                      });
                } catch (Exception e) {
                  fail(e);
                }
              });
        });
  }

  Uri publishLocal(File f, boolean movie, long date) throws Exception {
    ContentValues v = new ContentValues();
    v.put(MediaStore.MediaColumns.DISPLAY_NAME, f.getName());
    v.put(
        MediaStore.MediaColumns.MIME_TYPE,
        movie ? "video/mp4" : (f.getName().endsWith(".png") ? "image/png" : "image/jpeg"));
    v.put(MediaStore.MediaColumns.RELATIVE_PATH, movie ? "Movies/Still" : "Pictures/Still");
    v.put("datetaken", date);
    v.put(MediaStore.MediaColumns.DATE_MODIFIED, date / 1000);
    v.put(MediaStore.MediaColumns.IS_PENDING, 1);
    Uri u =
        getContentResolver()
            .insert(
                movie
                    ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                v);
    if (u == null) throw new IOException("Cannot create gallery item");
    try {
      try (OutputStream out = getContentResolver().openOutputStream(u);
          InputStream in = new FileInputStream(f)) {
        IO.copy(in, out);
      }
      v.clear();
      v.put(MediaStore.MediaColumns.IS_PENDING, 0);
      getContentResolver().update(u, v, null, null);
      return u;
    } catch (Exception e) {
      getContentResolver().delete(u, null, null);
      throw e;
    }
  }

  void trimOptions() {
    if (busy) return;
    if (start >= end) {
      toast("Select at least two frames");
      return;
    }
    if (sourceId == null) {
      Ui.options(
          this,
          "Save trimmed video",
          new String[] {"Save copy to phone", "Connect to Immich  ⓘ"},
          choice -> {
            if (choice == 0) exportTrim(0);
            else settings();
          });
      return;
    }
    Ui.options(
        this,
        "Save trimmed video",
        new String[] {"Save copy to phone", "Save copy to Immich", "Replace original in Immich"},
        choice -> {
          if (choice == 2)
            Ui.confirm(
                this,
                "Replace original?",
                "The original moves to Immich’s trash only after the new copy and supported"
                    + " metadata are verified.",
                "Replace original",
                () -> exportTrim(2),
                null);
          else exportTrim(choice);
        });
  }

  void exportTrim(int destination) {
    requireDate(
        () -> {
          busy = true;
          showProgress("Trimming video… Keep Still open.");
          getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          File out = new File(getCacheDir(), "Still-" + System.currentTimeMillis() + ".mp4");
          long endUs = end + 1 < video.times.length ? video.times[end + 1] : video.duration;
          MediaItem item =
              new MediaItem.Builder()
                  .setUri(Uri.fromFile(input))
                  .setClippingConfiguration(
                      new MediaItem.ClippingConfiguration.Builder()
                          .setStartPositionUs(video.times[start])
                          .setEndPositionUs(endUs)
                          .build())
                  .build();
          transformer =
              new Transformer.Builder(this)
                  .setVideoMimeType(MimeTypes.VIDEO_H264)
                  .setAudioMimeType(MimeTypes.AUDIO_AAC)
                  .addListener(
                      new Transformer.Listener() {
                        public void onCompleted(Composition c, ExportResult result) {
                          work.execute(
                              () -> {
                                try {
                                  MediaMetadataRetriever check = new MediaMetadataRetriever();
                                  check.setDataSource(out.getPath());
                                  String dur = check.extractMetadata(9);
                                  check.release();
                                  if (out.length() == 0 || dur == null)
                                    throw new IOException("Export verification failed");
                                  Mp4Dates.write(out, captured);
                                  if (destination == 0) publishLocal(out, true, captured);
                                  else
                                    new Immich(MainActivity.this)
                                        .saveTrim(
                                            out,
                                            captured,
                                            sourceId,
                                            source.value,
                                            destination == 2);
                                  runOnUiThread(
                                      () -> {
                                        busy = false;
                                        closeProgress();
                                        getWindow()
                                            .clearFlags(
                                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                        toast(
                                            destination == 0
                                                ? "Saved to Movies/Still"
                                                : "Saved to Immich");
                                        out.delete();
                                      });
                                } catch (Exception e) {
                                  fail(e);
                                }
                              });
                        }

                        public void onError(Composition c, ExportResult r, ExportException e) {
                          out.delete();
                          fail(e);
                        }
                      })
                  .build();
          transformer.start(new EditedMediaItem.Builder(item).build(), out.getPath());
        });
  }

  void matchSource() {
    if (prefs.getString("key", "").isEmpty()) return;
    status.setText("Matching Immich original…");
    final File f = input;
    work.execute(
        () -> {
          try {
            Immich api = new Immich(this);
            String id = api.match(f);
            org.json.JSONObject info = id == null ? null : api.get(id);
            runOnUiThread(
                () -> {
                  if (input != f) return;
                  sourceId = id;
                  source = info == null ? null : new JSONObjectHolder(info);
                  if (info != null) {
                    captured =
                        parseDate(
                            info.optJSONObject("exifInfo") == null
                                ? null
                                : info.optJSONObject("exifInfo").optString("dateTimeOriginal"));
                    if (captured == 0) captured = parseDate(info.optString("fileCreatedAt"));
                  }
                  status.setText(
                      id == null
                          ? "No exact Immich match · local saving available"
                          : "Immich original matched");
                });
          } catch (Exception e) {
            runOnUiThread(() -> status.setText("Immich: " + e.getMessage()));
          }
        });
  }

  void settings() {
    if (busy) return;
    pause();
    new SettingsScreen(this).show();
  }

  void showProgress(String text) {
    LinearLayout body = Ui.column(this);
    progressLabel = Ui.text(this, text, 15, Ui.MUTED);
    body.addView(progressLabel);
    ProgressBar indicator = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    indicator.setIndeterminate(true);
    indicator.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(MINT));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(16));
    p.topMargin = dp(18);
    body.addView(indicator, p);
    progress = Ui.sheet(this, "Working on your video", body);
    progress.setCancelable(false);
    progress.show();
    progress.getWindow().setLayout(-1, -2);
  }

  void closeProgress() {
    if (progress != null) {
      progress.dismiss();
      progress = null;
    }
    progressLabel = null;
  }

  void fail(Exception e) {
    runOnUiThread(
        () -> {
          busy = false;
          closeProgress();
          getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          if (!destroyed)
            message("Could not complete", e.getMessage() == null ? e.toString() : e.getMessage());
        });
  }

  void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  void message(String t, String m) {
    Ui.message(this, t, m, "Got it", null);
  }

  public void onBackPressed() {
    if (busy) {
      toast("Please wait for processing to finish");
      return;
    }
    if (editing) closeVideo();
    else super.onBackPressed();
  }

  @Override
  public void onConfigurationChanged(android.content.res.Configuration config) {
    super.onConfigurationChanged(config);
    if (!busy) {
      pause();
      if (editing && video != null) editor();
      else home();
    }
  }

  protected void onPause() {
    super.onPause();
    pause();
    if (repeat != null) handler.removeCallbacks(repeat);
  }

  protected void onDestroy() {
    destroyed = true;
    handler.removeCallbacksAndMessages(null);
    if (transformer != null) transformer.cancel();
    work.execute(
        () -> {
          if (video != null) video.close();
        });
    work.shutdown();
    renders.execute(
        () -> {
          if (decoder != null) decoder.close();
        });
    renders.shutdown();
    super.onDestroy();
  }

  static class JSONObjectHolder {
    final org.json.JSONObject value;

    JSONObjectHolder(org.json.JSONObject v) {
      value = v;
    }
  }
}
