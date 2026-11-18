# Zero-Trust Camera

The camera of the Zero-Trust suite (on the phone: **ZT Camera**). Photos and
videos are saved without location, time or device details unless you choose
to keep them. Same account, settings and look as the other Zero-Trust apps,
built on [Zero-Trust-Core](https://github.com/EdoSag/Zero-Trust-Core).

## What it does (0.1)

- **Photo and video**, with the phone's own camera extensions where it has
  them (Night, HDR, Portrait, Face retouch, Auto), zoom, tap to focus,
  exposure, flash, timer, grid and aspect ratio.
- **QR and barcode scanner**, on the phone only.
- **Clean files by default.** A saved photo keeps only its orientation. A
  saved video has its recording time cleared. Two switches under More
  settings keep the date and time, or the camera model, for people who want
  them. Location is never saved: the app does not ask for the location
  permission at all.
- **Works as the default camera**: other apps can ask it for a photo or a
  video, and those files never carry any metadata. Double-press power opens it
  from the lock screen, where it only shows what was taken in that session.
- **No account needed.** Signing in is optional and only matters for what
  comes next.
- **English and Hebrew**, with right-to-left layout.

Not in 0.1 yet: the private encrypted vault, encrypted backup, the document
scanner and ZT app actions for scanned codes. The plan is in the project's
`zt-camera-plan.md`.

## How it is built

Two parts in one APK:

- `android/capture`: the camera itself, native Kotlin on CameraX. It started
  from [GrapheneOS Camera](https://github.com/GrapheneOS/Camera) (MIT), see
  `android/capture/README.md` for what changed and how to take in upstream
  fixes. It is the launcher entry and needs no network.
- `lib/`: a small Flutter app on Zero-Trust-Core for the account, language,
  appearance and about screens. It opens from More settings in the camera.
  The language chosen there is passed to the camera over a method channel.

## Building

Flutter 3.47.9 (the version CI pins).

```
flutter pub get
flutter analyze
flutter test test/
flutter build apk --release
```

`.env` holds the public Supabase URL and publishable key; CI writes it when it
is not present. The native tests run with
`cd android && ./gradlew :capture:testDebugUnitTest`.

## Branches and releases

Work happens on the `Dev` branch; `main` holds releases. A push to `main`
builds a signed APK and publishes it as a GitHub Release.

## License

AGPL-3.0 (see `LICENSE`). The capture module contains code from GrapheneOS
Camera under the MIT license (`android/capture/LICENSE.upstream`).
