package com.hasbreo.still;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import java.io.*;

public class Smoke extends Instrumentation {
  MainActivity a;
  File dir;

  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  void check(boolean b, String message) {
    if (!b) throw new AssertionError(message);
  }

  void waitFor(java.util.function.BooleanSupplier ready) throws Exception {
    long until = System.currentTimeMillis() + 90000;
    while (!ready.getAsBoolean() && System.currentTimeMillis() < until) Thread.sleep(100);
    check(ready.getAsBoolean(), "Timed out");
  }

  void screenshot(String name) throws Exception {
    Bitmap b = getUiAutomation().takeScreenshot();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
      b.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    b.recycle();
  }

  public void onStart() {
    Bundle result = new Bundle();
    try {
      a =
          (MainActivity)
              startActivitySync(
                  new Intent(getTargetContext(), MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      dir = new File(a.getFilesDir(), "qa");
      dir.mkdirs();
      File video = new File(a.getFilesDir(), "test.mp4");
      try (InputStream in = getContext().getAssets().open("test.mp4");
          OutputStream out = new FileOutputStream(video)) {
        IO.copy(in, out);
      }
      waitForIdleSync();
      screenshot("home.png");
      runOnMainSync(() -> a.open(Uri.fromFile(video)));
      waitFor(() -> a.video != null && !a.busy && a.displayIndex == 0);
      waitForIdleSync();
      screenshot("capture.png");
      int start = a.start, end = a.end;
      runOnMainSync(
          () -> {
            a.trim = true;
            a.editor();
            a.move(90);
          });
      waitFor(() -> a.displayIndex == 90);
      check(a.start == start && a.end == end, "Seeking moved trim boundaries");
      runOnMainSync(() -> a.boundary(30, false));
      waitFor(() -> a.displayIndex == 30);
      check(a.cursor == 90, "Trim handle moved playhead");
      runOnMainSync(() -> a.step(1));
      waitFor(() -> a.displayIndex == 31);
      check(
          a.start == 31 && a.cursor == 90,
          "Frame step did not independently adjust selected start");
      screenshot("trim.png");
      runOnMainSync(
          () -> {
            a.beginScrub();
            a.move(120);
          });
      Thread.sleep(500);
      check(
          a.scrubbing && a.preview.getVisibility() == android.view.View.INVISIBLE,
          "Scrubbing did not use video player");
      runOnMainSync(a::endScrub);
      waitFor(() -> a.displayIndex == 120);
      runOnMainSync(
          () -> {
            a.muteButton.performClick();
            check(a.player.getVolume() == 0f, "Playback mute failed");
            a.muteButton.performClick();
            check(a.player.getVolume() == 1f, "Playback unmute failed");
            a.togglePlay();
          });
      Thread.sleep(1500);
      runOnMainSync(
          () -> {
            check(a.player.getCurrentPosition() > 0, "Playback did not advance");
            a.pause();
            a.cursor = a.video.times.length - 1;
            a.reachedEnd = true;
            a.togglePlay();
            check(a.cursor == 0, "Play at end did not restart");
            a.pause();
          });
      Bundle progress = new Bundle();
      progress.putString(
          "checkpoint",
          "Player scrubbing, mute, playback, replay and independent trim checks passed");
      sendStatus(1, progress);
      runOnMainSync(() -> a.settings());
      waitForIdleSync();
      screenshot("settings.png");
      runOnMainSync(() -> a.closeVideo());
      check(a.video == null && !a.editing, "Close video did not release editor");
      result.putString(
          "result",
          "PASS: open, video scrubbing, mute, playback, replay, independent trim, stepping,"
              + " settings and close");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString("error", android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED, result);
    }
  }
}
