# Freeze 0.3.1 validation

Fixed a reproducible seek starvation defect: the old 16ms debounce could be continually reset by faster touch input. A clock-driven regression sends 125 updates per second and confirms previews dispatch throughout the drag, retain the latest target and stop after release/cancel. It passes.

Preview requests now invalidate earlier decode work and reject obsolete results before showing any bitmap. Start/End dragging, selecting a boundary, frame controls and ordinary seeking all drive the native player. Exact still refinement runs only after input settles. Seeking to the final frame while paused does not reset the independent playhead.

Debug APK, instrumentation APK, signed release APK and release lint built successfully. The release certificate matches previous versions. The updated instrumentation exercises actual Timeline touch events and waits for the player's frame presentation timestamp during Start and End drags, rather than checking only model values.

Runtime verification limitation: the software Android emulator booted and both APK installs succeeded, but the instrumentation run timed out after 180 seconds with no result. The system log records an Android System UI ANR. Therefore the gesture assertions and visual checks cannot be reported as passing. Physical-phone playback/scrubbing responsiveness remains to be checked.

Application ID: com.hasbreo.still
Version: 0.3.1 (4)
APK SHA-256: 9bade47c01f552ecd7b9db18ff8e5a1ec6707f094ebbc3c33483224aff23627e
