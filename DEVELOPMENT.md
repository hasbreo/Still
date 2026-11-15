# Still development

Build with JDK 17+, Android SDK 35/build tools 35.0.0 and Gradle 8.10.2.

```
gradle :app:assembleDebug :app:lintDebug
```

For releases set STILL_KEYSTORE and STILL_STORE_PASSWORD to the existing private signing material, alias `still`. Signing backup is private and is never part of this repository. Do not create a replacement key for updates.

```
gradle :app:assembleRelease :app:lintRelease
```

## Distribution

APK page: https://hasbreo.com/freeze
Update manifest: https://hasbreo.com/still/update.json

Manifest shape:

```json
{"schemaVersion":1,"app":"Freeze","available":true,"latest":{"versionCode":3,"versionName":"0.3.0","url":"https://hasbreo.com/freeze/Freeze-0.3.0.apk","sha256":"lowercase hexadecimal SHA256","notes":"Release notes"}}
```

The updater accepts only HTTPS hasbreo.com download URLs, verifies SHA256, application ID, a newer version code, and matching APK signing certificates. Android still requires the user to approve installation. Update the hardcoded installed-version checks when bumping the version (replace with BuildConfig in a later cleanup).

## Architecture

Native Java Android UI, MediaCodec with a nearby-frame cache for preview, MediaMetadataRetriever for original-resolution exports/platform fallback, MediaExtractor for actual sample presentation timestamps (sorted for B frames), Media3 Transformer for microsecond-boundary trims, MediaStore for gallery exports. Cropping uses source pixel coordinates. No external media-processing service.

A short pause during frame stepping is possible while decoding from a preceding keyframe. Intermediate display requests are coalesced; frame selection itself remains one sample per tap.

## Validation limits for the initial build

Compile/static checks and MP4 date tests do not replace testing on a physical Android phone. Verify portrait/landscape, HDR colours, variable-rate footage, audio sync and metadata on real shared files. Immich/Cloudflare sign-in and permissions must be tested against the actual server. Google sign-in may disallow embedded WebView authentication; in that case use the local trusted HTTPS endpoint or a server-supported authentication flow rather than disabling protections.

Replacement requires a checksum match. Transcoded shared files will not match. Local save remains available if the file has a readable capture date. Immich replacement intentionally retains originals on any failed verification or unsupported face/stack/live-photo relationship. Album/tag errors can leave a new copy in the library, but do not trash the original. Locked originals are not uploaded through replacement.

`tests/Mp4DatesTest.java` validates v0/v1 ISO BMFF dates and unchanged media payload. Run with javac/java and app/src/main/java/com/hasbreo/still/Mp4Dates.java.

### Automated device smoke test
Run `bash scripts/generate-test-media.sh` (requires FFmpeg) first. Then `gradle :app:assembleDebug :app:assembleDebugAndroidTest` builds the target and the built-in instrumentation runner. Install both APKs, then run `adb shell am instrument -w com.hasbreo.still.test/com.hasbreo.still.Smoke`. The test opens synthetic portrait footage and checks preview, independent playhead/trim movement, one-frame stepping and Close Video. Screenshots are written to the debug app's files/qa directory. These synthetic tests do not cover the user's Immich authentication or original media.

### Freeze 0.3.0 playback path
Media3 ExoPlayer 1.9.3 and a TextureView render continuous playback and scrubbing. Scrubbing uses ExoPlayer’s dedicated scrubbing mode; exact frame previews/exports use the separate extractor after drag release or explicit frame steps. Frame extraction is suspended during playback and scrubbing. Playback mute changes ExoPlayer volume only and is not passed to Transformer. Phone-copy saving is the normal backup path; direct server upload is reserved for replacing a matched original. Closing the editor prompts before discarding the selection.
