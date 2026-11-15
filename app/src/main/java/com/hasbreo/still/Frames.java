package com.hasbreo.still;

import android.graphics.*;
import android.media.*;
import java.io.*;
import java.util.*;

final class Frames implements AutoCloseable {
  final File file;
  final MediaMetadataRetriever reader = new MediaMetadataRetriever();
  final long[] times;
  final long duration;
  final int width, height;
  final String date;

  Frames(File f) throws Exception {
    file = f;
    reader.setDataSource(f.getPath());
    duration = Long.parseLong(reader.extractMetadata(9)) * 1000;
    date = reader.extractMetadata(5);
    width = Integer.parseInt(reader.extractMetadata(18));
    height = Integer.parseInt(reader.extractMetadata(19));
    MediaExtractor ex = new MediaExtractor();
    ArrayList<Long> list = new ArrayList<>();
    try {
      ex.setDataSource(f.getPath());
      for (int i = 0; i < ex.getTrackCount(); i++)
        if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("video/")) {
          ex.selectTrack(i);
          break;
        }
      while (ex.getSampleTime() >= 0) {
        list.add(ex.getSampleTime());
        if (!ex.advance()) break;
      }
    } finally {
      ex.release();
    }
    times = list.stream().mapToLong(Long::longValue).distinct().sorted().toArray();
    if (times.length == 0) throw new IOException("No video frames found");
  }

  int nearest(long us) {
    int p = Arrays.binarySearch(times, us);
    if (p >= 0) return p;
    p = -p - 1;
    if (p == 0) return 0;
    if (p == times.length) return p - 1;
    return us - times[p - 1] <= times[p] - us ? p - 1 : p;
  }

  synchronized Bitmap frame(int index) {
    return reader.getFrameAtTime(times[index], MediaMetadataRetriever.OPTION_CLOSEST);
  }

  synchronized Bitmap preview(int index) {
    return reader.getScaledFrameAtTime(
        times[index], MediaMetadataRetriever.OPTION_CLOSEST, 960, 960);
  }

  synchronized Bitmap thumb(int index) {
    return reader.getScaledFrameAtTime(
        times[index], MediaMetadataRetriever.OPTION_CLOSEST, 240, 240);
  }

  static double sharpness(Bitmap b) {
    int w = b.getWidth(), h = b.getHeight();
    int[] px = new int[w * h];
    b.getPixels(px, 0, w, 0, 0, w, h);
    double sum = 0, sq = 0;
    int n = 0;
    for (int y = 1; y < h - 1; y++)
      for (int x = 1; x < w - 1; x++) {
        int i = y * w + x;
        double l =
            4 * gray(px[i]) - gray(px[i - 1]) - gray(px[i + 1]) - gray(px[i - w]) - gray(px[i + w]);
        sum += l;
        sq += l * l;
        n++;
      }
    return n == 0 ? 0 : sq / n - (sum / n) * (sum / n);
  }

  static double gray(int c) {
    return .299 * ((c >> 16) & 255) + .587 * ((c >> 8) & 255) + .114 * (c & 255);
  }

  public synchronized void close() {
    try {
      reader.release();
    } catch (Exception ignored) {
    }
  }
}
