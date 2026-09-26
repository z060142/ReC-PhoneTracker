# ReC PhoneTracker

A phone app that turns a handheld phone into a 6DoF camera tracker and lens controller for
[ReC Sandbox](https://github.com/z060142/ReC-Sandbox) — a sandbox for recording work built on
CRYENGINE 5.7.

## Origin

ReC Sandbox needed a cheap way to move a virtual camera by hand. Dedicated tracking hardware is
expensive and awkward to set up, while almost every phone already ships with a camera, an IMU and
a mature visual-inertial tracking stack. This app reads the phone's pose from that stack and
streams it over Wi-Fi to the PC, where the `CryPhoneTracker` plugin drives an entity or the active
Cinematic Camera. The phone screen also carries lens faders (focal length, aperture, focus
distance, ISO, EV) and an autofocus request, so one hand holds the "camera body" and the other
operates the lens.

## Platform support

- **Android only** (minSdk 24, targetSdk 34).
- **ARCore only** — the pose comes from Google ARCore's motion tracking. The device must be on the
  [ARCore supported devices](https://developers.google.com/ar/devices) list.
- iOS / ARKit is not supported. The wire protocol is platform-neutral, so another client could be
  written later, but nothing exists yet.

## Protocol

Little-endian UDP (TCP also selectable) packets, version 1:

| Packet  | Size | Direction  | Content |
| ------- | ---- | ---------- | ------- |
| Pose    | 64 B | phone → PC | seq, origin epoch, timestamps, position (m), quaternion, tracking state, battery, app fps |
| Lens    | 32 B | phone → PC | focal length (mm), aperture, focus distance (m), EV, ISO, DoF / exposure / AF flags |
| Control | 28 B | PC → phone | ping / pong / recenter / set rate / pause / resume / set focus |

Default port 9050. The authoritative definition lives in
`app/src/main/java/tw/bigspring/phonetracker/net/PacketCodec.kt` and must stay in sync with
`PhoneTrackerProtocol.h` in the CRYENGINE plugin. Coordinates: ARCore `(x, y, z)` maps to
CRYENGINE `(x, -z, y)`; the quaternion maps `(qx, qy, qz, qw)` to `(qx, -qz, qy, qw)`.

## Build

Android Studio (or plain Gradle with JDK 17):

```
./gradlew assembleRelease
```

The release build is signed with the debug keystore on purpose so the APK installs directly on a
personal device. Output: `app/build/outputs/apk/release/app-release.apk`.

## Usage

1. Install the APK and grant the camera permission.
2. In Settings, enter the PC's LAN IP, port (9050) and transport (UDP).
3. In the CRYENGINE level, add a **Phone Tracker** component (Sandbox category *Tracking*) on an
   entity, or leave *Follow Active Camera* on to drive the active Cinematic Camera.
4. Enter game / simulation mode. Hold the phone still for a moment, then move.
5. Use *Recenter* on the phone or the PC to re-anchor the origin.

## Tools

`tools/pose_monitor.py` is a standalone UDP receiver for validating the stream without the engine:
prints rate / loss / jitter, can log to CSV and send recenter / ping commands.

```
uv run tools/pose_monitor.py --port 9050 --log poses.csv
```

## Author

Sherefox ([z060142](https://github.com/z060142))
