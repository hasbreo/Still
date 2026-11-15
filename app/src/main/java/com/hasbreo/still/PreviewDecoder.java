package com.hasbreo.still;

import android.graphics.*;
import android.media.*;
import java.io.*;
import java.nio.*;

/** Sequential decoder: retains codec state between neighbouring frame requests. */
final class PreviewDecoder implements AutoCloseable {
  final Frames source;
  final int maxSize;
  MediaExtractor extractor;
  MediaCodec codec;
  MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
  long last = -1;
  boolean ended;
  final android.util.LruCache<Integer, Bitmap> cache =
      new android.util.LruCache<Integer, Bitmap>(12 * 1024 * 1024) {
        protected int sizeOf(Integer key, Bitmap b) {
          return b.getAllocationByteCount();
        }

        protected void entryRemoved(boolean evicted, Integer key, Bitmap old, Bitmap next) {
          if (old != next) old.recycle();
        }
      };
  int rotation;
  boolean bt709, fullRange;

  PreviewDecoder(Frames f, int size) {
    source = f;
    maxSize = size;
  }

  void init() throws Exception {
    extractor = new MediaExtractor();
    extractor.setDataSource(source.file.getPath());
    MediaFormat format = null;
    for (int i = 0; i < extractor.getTrackCount(); i++) {
      MediaFormat candidate = extractor.getTrackFormat(i);
      if (candidate.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
        extractor.selectTrack(i);
        format = candidate;
        break;
      }
    }
    if (format == null) throw new IOException("No video track");
    rotation =
        format.containsKey(MediaFormat.KEY_ROTATION)
            ? format.getInteger(MediaFormat.KEY_ROTATION)
            : 0;
    bt709 =
        format.containsKey(MediaFormat.KEY_COLOR_STANDARD)
            ? format.getInteger(MediaFormat.KEY_COLOR_STANDARD) == MediaFormat.COLOR_STANDARD_BT709
            : source.width >= 720;
    fullRange =
        format.containsKey(MediaFormat.KEY_COLOR_RANGE)
            && format.getInteger(MediaFormat.KEY_COLOR_RANGE) == MediaFormat.COLOR_RANGE_FULL;
    if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)
        && format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) > MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
      throw new IOException("Use platform colour conversion for HDR preview");
    format.setInteger(
        MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
    codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
    codec.configure(format, null, null, 0);
    codec.start();
  }

  Bitmap get(int frame) throws Exception {
    return get(frame, () -> false);
  }

  Bitmap get(int frame, java.util.function.BooleanSupplier cancelled) throws Exception {
    if (cancelled.getAsBoolean()) return null;
    Bitmap cached = cache.get(frame);
    if (cached != null && !cached.isRecycled()) return cached.copy(Bitmap.Config.ARGB_8888, false);
    if (codec == null) init();
    long target = source.times[frame];
    if (last >= target || last < 0 || target - last > 1000000) {
      extractor.seekTo(target, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
      codec.flush();
      ended = false;
      last = -1;
    }
    long deadline = System.currentTimeMillis() + 15000;
    while (System.currentTimeMillis() < deadline) {
      if (cancelled.getAsBoolean()) return null;
      if (!ended) {
        int i = codec.dequeueInputBuffer(0);
        if (i >= 0) {
          ByteBuffer b = codec.getInputBuffer(i);
          int count = extractor.readSampleData(b, 0);
          if (count < 0) {
            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            ended = true;
          } else {
            codec.queueInputBuffer(i, 0, count, extractor.getSampleTime(), 0);
            extractor.advance();
          }
        }
      }
      int index = codec.dequeueOutputBuffer(info, 10000);
      if (index >= 0) {
        last = info.presentationTimeUs;
        boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        try {
          boolean nearby = last >= target - 600000;
          if (info.size > 0 && nearby) {
            Image image = codec.getOutputImage(index);
            if (image == null) throw new IOException("Decoder does not expose image frames");
            try {
              Bitmap decoded = bitmap(image);
              int actual = source.nearest(last);
              cache.put(actual, decoded);
              if (last >= target) return decoded.copy(Bitmap.Config.ARGB_8888, false);
            } finally {
              image.close();
            }
          }
        } finally {
          codec.releaseOutputBuffer(index, false);
        }
        if (eos) break;
      }
    }
    throw new IOException("Preview frame unavailable");
  }

  Bitmap bitmap(Image image) {
    Rect r = image.getCropRect();
    int ow = r.width(), oh = r.height();
    float factor = Math.min(1f, maxSize / (float) Math.max(ow, oh));
    int w = Math.max(1, Math.round(ow * factor)), h = Math.max(1, Math.round(oh * factor));
    int[] pixels = new int[w * h];
    Image.Plane[] p = image.getPlanes();
    ByteBuffer y = p[0].getBuffer(), u = p[1].getBuffer(), v = p[2].getBuffer();
    int yp = y.position(), up = u.position(), vp = v.position();
    for (int row = 0; row < h; row++) {
      int sy = r.top + Math.min(oh - 1, (int) (row / factor));
      for (int col = 0; col < w; col++) {
        int sx = r.left + Math.min(ow - 1, (int) (col / factor));
        int yy = (y.get(yp + sy * p[0].getRowStride() + sx * p[0].getPixelStride()) & 255) - 16;
        int uu =
            (u.get(up + (sy / 2) * p[1].getRowStride() + (sx / 2) * p[1].getPixelStride()) & 255)
                - 128;
        int vv =
            (v.get(vp + (sy / 2) * p[2].getRowStride() + (sx / 2) * p[2].getPixelStride()) & 255)
                - 128;
        int rr, gg, bb;
        if (fullRange) {
          int yFull = yy + 16;
          rr = yFull + Math.round((bt709 ? 1.5748f : 1.402f) * vv);
          gg = yFull - Math.round((bt709 ? .1873f : .3441f) * uu + (bt709 ? .4681f : .7141f) * vv);
          bb = yFull + Math.round((bt709 ? 1.8556f : 1.772f) * uu);
        } else {
          rr = (298 * yy + (bt709 ? 459 : 409) * vv + 128) >> 8;
          gg = (298 * yy - (bt709 ? 55 : 100) * uu - (bt709 ? 136 : 208) * vv + 128) >> 8;
          bb = (298 * yy + (bt709 ? 541 : 516) * uu + 128) >> 8;
        }
        pixels[row * w + col] = 0xff000000 | clamp(rr) << 16 | clamp(gg) << 8 | clamp(bb);
      }
    }
    Bitmap b = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
    if (rotation != 0) {
      Matrix matrix = new Matrix();
      matrix.postRotate(rotation);
      Bitmap rotated = Bitmap.createBitmap(b, 0, 0, w, h, matrix, true);
      if (rotated != b) b.recycle();
      b = rotated;
    }
    return b;
  }

  int clamp(int n) {
    return Math.max(0, Math.min(255, n));
  }

  public void close() {
    cache.evictAll();
    if (codec != null) {
      try {
        codec.stop();
        codec.release();
      } catch (Exception ignored) {
      }
      codec = null;
    }
    if (extractor != null) {
      extractor.release();
      extractor = null;
    }
  }
}
