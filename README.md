# Still

Android app for extracting video frames and precise trimming, designed as a companion to Immich.

Status: initial Android implementation, version 0.1.0. See DEVELOPMENT.md for build steps and validation limits.

## Agreed experience
- Native Android app, dark emerald/mint interface, optimised for portrait and landscape videos.
- Receive a video through Android Share (ACTION_SEND); also offer Android's Choose video picker.
- Preview playback with pinch zoom and pan.
- Previous/next buttons step through actual decoded frames, including variable-frame-rate footage. Holding repeats continuously and accelerates; release stops.
- Capture exports either the entire full-resolution frame or the currently visible crop, using original pixels.
- Find sharper nearby examines up to one second either side of the cursor, favours closer frames when quality is similar, and proposes rather than silently applies a selection.
- Trim start/end handles plus tappable Start and End fields. Highlight selects the boundary affected by frame-step buttons.
- Exact cuts take priority over speed; re-encode when necessary.
- Save trimmed video as copy, or replace the original only after confident source identification, successful upload and verification. Replacement moves the original to Immich trash rather than deleting it permanently.

## Files and metadata
- Captured pictures go through Android MediaStore to Pictures/Still, visible to gallery apps. Immich/Google Photos backup depends on the user's folder-backup settings.
- Still image capture timestamp: original video capture timestamp + selected frame presentation time.
- Trimmed video timestamp: preserve the original video's capture timestamp unchanged.
- Preserve transferable location, description, favourite, tags, album membership and other supported user metadata for replacement. Duration, encoding/file size/checksum and new asset ID necessarily change.
- Preserve timezone/subsecond timestamps where available. Never substitute export time silently when original capture time is unavailable.

## Distribution
- APK download page: https://hasbreo.com/still
- GitHub: https://github.com/hasbreo/Still
- Future update checks and APK downloads use hasbreo.com.
- Include automatic update checking, a manual Check for updates button, installed version and release notes.
- Keep the same Android application ID and signing key across releases; never commit the signing key or credentials.
- Website update manifest: /still/update.json. Before the first release it explicitly reports available=false; do not interpret that as an installable update.

## Validation required before implementation is considered complete
1. Test real Immich Android shared files: URI access, original vs transcoded file, filename, metadata and temporary-file lifecycle.
2. Match source asset safely (checksum/API where available); never guess from filename/date alone for replacement.
3. Test integration with the user's Cloudflare Access authentication.
4. Verify exact frame stepping, VFR, rotation, audio sync and frame-accurate re-encoded trims.
5. Verify capture timestamps, gallery visibility, full/cropped export, and HDR/colour handling.
6. Verify transferable metadata and album membership with the deployed Immich API before enabling replacement.
7. Handle interrupted uploads, failed verification, large videos, low disk space and cancellation without losing originals.
8. Verify APK signature continuity and updater version/checksum handling.

## Scope
Frame extraction, precise trimming, nearby sharpness suggestions, Immich save integration and updates. Full library browsing, effects, transitions and general video editing are outside the first release.
