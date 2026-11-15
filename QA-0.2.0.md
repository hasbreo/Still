# Still 0.2.0 validation

- Release APK build and Android lint: passed.
- MP4 capture-date/payload regression test: passed.
- APK signature matches 0.1.0; application ID unchanged, version code 2.
- Live hasbreo.com APK download SHA-256: d38d05d54c2f7efd45ec57c4742404b47afeb4c0520e6a75550a185e1a96343c.
- API 30 software-emulator instrumentation assertions passed: open video, preview progression, seeking without changing trim boundaries, moving a trim boundary without changing playhead, selected-boundary frame stepping, invoking settings and closing video.
- Visual verification is incomplete: captured emulator screens were covered by an Android system-process-not-responding dialog. Do not treat the instrumentation assertions as proof of full visual fidelity or phone performance.
- Physical-device confirmation remains necessary for live Immich authentication/sharing, exact media performance, PNG metadata, replacement metadata and Android task navigation.
