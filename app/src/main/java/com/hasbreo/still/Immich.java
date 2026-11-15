package com.hasbreo.still;

import android.webkit.CookieManager;
import java.io.*;
import java.net.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.json.*;

final class Immich {
  final MainActivity app;
  final String server, key;

  Immich(MainActivity a) {
    app = a;
    server = a.prefs.getString("server", "https://photos.hasbreo.com");
    key = a.prefs.getString("key", "");
  }

  HttpURLConnection connection(String path, String method) throws Exception {
    URL u = new URL(server + "/api" + path);
    if (!u.getProtocol().equals("https")) throw new IOException("HTTPS required");
    HttpURLConnection c = (HttpURLConnection) u.openConnection();
    c.setRequestMethod(method);
    c.setConnectTimeout(20000);
    c.setReadTimeout(120000);
    c.setInstanceFollowRedirects(false);
    c.setRequestProperty("x-api-key", key);
    String cookies = CookieManager.getInstance().getCookie(server);
    if (cookies != null) c.setRequestProperty("Cookie", cookies);
    c.setRequestProperty("Accept", "application/json");
    return c;
  }

  String response(HttpURLConnection c) throws Exception {
    try {
      int code = c.getResponseCode();
      if (code >= 300 && code < 400) app.prefs.edit().putBoolean("gateway", true).apply();
      if (code < 200 || code >= 300)
        throw new IOException(
            "Server returned " + code + ". Check API permissions and server sign-in in Settings.");
      String type = c.getContentType();
      if (type != null && type.contains("text/html"))
        throw new IOException("Sign in to your server in Settings first");
      try (InputStream in = c.getInputStream()) {
        return new String(IO.read(in), java.nio.charset.StandardCharsets.UTF_8);
      }
    } finally {
      c.disconnect();
    }
  }

  String request(String path, String method, Object data) throws Exception {
    HttpURLConnection c = connection(path, method);
    if (data != null) {
      byte[] bytes = data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
      c.setDoOutput(true);
      c.setRequestProperty("Content-Type", "application/json");
      c.setFixedLengthStreamingMode(bytes.length);
      try (OutputStream out = c.getOutputStream()) {
        out.write(bytes);
      }
    }
    return response(c);
  }

  JSONObject get(String id) throws Exception {
    return new JSONObject(request("/assets/" + id, "GET", null));
  }

  String checksum(File f) throws Exception {
    MessageDigest d = MessageDigest.getInstance("SHA-1");
    try (InputStream in = new FileInputStream(f)) {
      byte[] b = new byte[65536];
      int n;
      while ((n = in.read(b)) != -1) d.update(b, 0, n);
    }
    return Base64.getEncoder().encodeToString(d.digest());
  }

  String match(File f) throws Exception {
    JSONObject entry = new JSONObject().put("id", "source").put("checksum", checksum(f));
    JSONObject body = new JSONObject().put("assets", new JSONArray().put(entry));
    JSONObject r = new JSONObject(request("/assets/bulk-upload-check", "POST", body));
    JSONArray results = r.getJSONArray("results");
    if (results.length() != 1) return null;
    return results.getJSONObject(0).optString("assetId", null);
  }

  void part(OutputStream out, String boundary, String name, String value) throws Exception {
    out.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\""
                + name
                + "\"\r\n\r\n"
                + value
                + "\r\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  String upload(File f, long date) throws Exception {
    String boundary = "Still" + UUID.randomUUID();
    HttpURLConnection c = connection("/assets", "POST");
    c.setDoOutput(true);
    c.setChunkedStreamingMode(65536);
    c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
    try (OutputStream out = c.getOutputStream()) {
      String iso = Instant.ofEpochMilli(date).toString();
      part(out, boundary, "deviceAssetId", UUID.randomUUID().toString());
      part(out, boundary, "deviceId", "Still");
      part(out, boundary, "fileCreatedAt", iso);
      part(out, boundary, "fileModifiedAt", iso);
      out.write(
          ("--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\"assetData\"; filename=\""
                  + f.getName()
                  + "\"\r\nContent-Type: video/mp4\r\n\r\n")
              .getBytes());
      try (InputStream in = new FileInputStream(f)) {
        IO.copy(in, out);
      }
      out.write(("\r\n--" + boundary + "--\r\n").getBytes());
    }
    return new JSONObject(response(c)).getString("id");
  }

  void saveTrim(File f, long date, String old, JSONObject original, boolean replace)
      throws Exception {
    // Refresh source metadata before exporting. Any error leaves the original untouched.
    original = get(old);
    if ("locked".equals(original.optString("visibility")))
      throw new IOException(
          "Locked originals cannot be exported to Immich safely yet; save a local copy instead");
    JSONArray albums = new JSONArray(request("/albums?assetId=" + old, "GET", null));
    if (original.has("livePhotoVideoId") && !original.isNull("livePhotoVideoId"))
      throw new IOException("Live-photo replacement is not supported; save a local copy instead");
    String id = upload(f, date);
    if (id.equals(old)) throw new IOException("Upload matched original; original retained");
    JSONObject uploaded = get(id);
    if (!checksum(f).equals(uploaded.optString("checksum")))
      throw new IOException(
          "Upload verification failed. Original retained; uploaded copy may remain in Immich.");
    JSONObject ex = original.optJSONObject("exifInfo");
    JSONObject update =
        new JSONObject()
            .put("isFavorite", original.optBoolean("isFavorite"))
            .put("dateTimeOriginal", Instant.ofEpochMilli(date).toString());
    for (String k : new String[] {"visibility", "rating"})
      if (original.has(k) && !original.isNull(k)) update.put(k, original.get(k));
    if (ex != null)
      for (String k : new String[] {"description", "latitude", "longitude"})
        if (ex.has(k) && !ex.isNull(k)) update.put(k, ex.get(k));
    request("/assets/" + id, "PUT", update);
    JSONArray tags = original.optJSONArray("tags");
    if (tags != null)
      for (int i = 0; i < tags.length(); i++)
        request(
            "/tags/" + tags.getJSONObject(i).getString("id") + "/assets",
            "PUT",
            new JSONObject().put("ids", new JSONArray().put(id)));
    for (int i = 0; i < albums.length(); i++) {
      JSONObject a = albums.getJSONObject(i);
      JSONArray result =
          new JSONArray(
              request(
                  "/albums/" + a.getString("id") + "/assets",
                  "PUT",
                  new JSONObject().put("ids", new JSONArray().put(id))));
      if (result.length() != 1
          || (!result.getJSONObject(0).optBoolean("success")
              && !"duplicate".equalsIgnoreCase(result.getJSONObject(0).optString("error"))))
        throw new IOException("Could not copy album membership. Original retained.");
    }
    JSONObject verified = get(id);
    if (verified.optBoolean("isFavorite") != original.optBoolean("isFavorite"))
      throw new IOException("Metadata verification failed; original retained");
    JSONObject vx = verified.optJSONObject("exifInfo");
    if (vx == null || MainActivity.parseDate(vx.optString("dateTimeOriginal")) != date)
      throw new IOException("Capture time verification failed; original retained");
    if (ex != null)
      for (String field : new String[] {"description", "latitude", "longitude"})
        if (ex.has(field)
            && !ex.isNull(field)
            && !String.valueOf(ex.get(field)).equals(String.valueOf(vx.opt(field))))
          throw new IOException(
              "Metadata verification failed for " + field + "; original retained");
    if (tags != null) {
      Set<String> actualTags = new HashSet<>();
      JSONArray vt = verified.optJSONArray("tags");
      if (vt != null)
        for (int i = 0; i < vt.length(); i++) actualTags.add(vt.getJSONObject(i).getString("id"));
      for (int i = 0; i < tags.length(); i++)
        if (!actualTags.contains(tags.getJSONObject(i).getString("id")))
          throw new IOException("Tag verification failed; original retained");
    }
    JSONArray after = new JSONArray(request("/albums?assetId=" + id, "GET", null));
    Set<String> memberships = new HashSet<>();
    for (int i = 0; i < after.length(); i++)
      memberships.add(after.getJSONObject(i).getString("id"));
    for (int i = 0; i < albums.length(); i++)
      if (!memberships.contains(albums.getJSONObject(i).getString("id")))
        throw new IOException("Album verification failed; original retained");
    if (replace) {
      if (original.optJSONObject("stack") != null
          || original.optJSONArray("people") != null
              && original.optJSONArray("people").length() > 0)
        throw new IOException(
            "Copy saved. Original retained because it has face/stack relationships that cannot"
                + " safely be transferred.");
      request(
          "/assets",
          "DELETE",
          new JSONObject().put("ids", new JSONArray().put(old)).put("force", false));
    }
  }
}
