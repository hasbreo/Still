# Freeze 0.3.0 validation

- Debug, Android instrumentation APK, signed release APK and release lint built successfully with JDK 17 / Android SDK 35 / Media3 1.9.3.
- Pure Java MP4 timestamp test passed.
- Release signature matches prior Still releases; application ID remains com.hasbreo.still. Existing media folders and update-feed address remain compatible.
- Added instrumented assertions for independent playhead/trim boundaries, frame stepping, player scrubbing visibility, mute/unmute, playback advancement and replay from end.
- The software-only Android emulator booted and accepted both APKs, but the instrumentation invocation did not return a result. This run provides no runtime pass or visual QA evidence for 0.3.0.
- Physical-device playback/scrubbing responsiveness, variable frame rate/HDR exports and live Immich integration remain to be checked on the target phone.

Release APK SHA-256: ce6b6af071e93b409b0325172e0f298be9cf209e57727dede8f03ec510b97b27
