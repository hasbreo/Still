package com.hasbreo.still;

import java.io.*;

/** Update ISO BMFF creation/modification dates without touching media payloads. */
final class Mp4Dates {
  static void write(File file, long millis) throws IOException {
    try (RandomAccessFile f = new RandomAccessFile(file, "rw")) {
      walk(f, 0, f.length(), millis / 1000 + 2082844800L);
    }
  }

  static void walk(RandomAccessFile f, long begin, long end, long seconds) throws IOException {
    long p = begin;
    while (p + 8 <= end) {
      f.seek(p);
      long length = Integer.toUnsignedLong(f.readInt());
      byte[] t = new byte[4];
      f.readFully(t);
      String type = new String(t, java.nio.charset.StandardCharsets.US_ASCII);
      int header = 8;
      if (length == 1) {
        length = f.readLong();
        header = 16;
      }
      if (length == 0) length = end - p;
      if (length < header || p + length > end) throw new IOException("Invalid MP4 structure");
      long body = p + header;
      if (type.equals("moov") || type.equals("trak") || type.equals("mdia"))
        walk(f, body, p + length, seconds);
      if (type.equals("mvhd") || type.equals("tkhd") || type.equals("mdhd")) {
        f.seek(body);
        int version = f.readUnsignedByte();
        if (version == 0) {
          f.seek(body + 4);
          f.writeInt((int) seconds);
          f.writeInt((int) seconds);
        } else if (version == 1) {
          f.seek(body + 4);
          f.writeLong(seconds);
          f.writeLong(seconds);
        }
      }
      p += length;
    }
  }
}
