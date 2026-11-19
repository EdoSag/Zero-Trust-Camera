# Zero-Trust Camera

The camera of the Zero-Trust suite (on the phone: **ZT Camera**). Photos and
videos are saved without location, time or device details unless you choose
to keep them. Same account, settings and look as the other Zero-Trust apps,
built on [Zero-Trust-Core](https://github.com/EdoSag/Zero-Trust-Core).

## What it does (0.2)

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
- **Private vault** (new in 0.2). Asked once on first start: gallery or
  Private vault, and changeable under More settings. In Private mode a photo
  is encrypted in memory before anything is written, and a video is
  encrypted the moment recording stops, so neither ever reaches the gallery.
  It works from the lock screen too. The vault opens with the fingerprint,
  face or screen lock and locks again as soon as the app is left. Inside:
  grid, viewer, video player, favourites, albums, a trash that empties after
  30 days, import from the gallery, and save or share copies with no
  metadata. Details below.
- **No account needed.** Signing in is optional and only matters for what
  comes next.
- **English and Hebrew**, with right-to-left layout.

Not in 0.2 yet: encrypted backup of the vault, Clean and share from other
apps, the document scanner and ZT app actions for scanned codes. The plan is in the project's
`zt-camera-plan.md`.

## How it is built

Two parts in one APK:

- `android/capture`: the camera itself, native Kotlin on CameraX. It started
  from [GrapheneOS Camera](https://github.com/GrapheneOS/Camera) (MIT), see
  `android/capture/README.md` for what changed and how to take in upstream
  fixes. It is the launcher entry and needs no network.
- `lib/`: a small Flutter app on Zero-Trust-Core for the Private vault
  screens (`lib/private_vault`) and the account, language, appearance and
  about screens. It opens from More settings, or from the gallery button in
  Private mode.
  The language chosen there is passed to the camera over a method channel.

## Private vault

- **Keys.** On setup the phone makes an X25519 key pair (Tink, in plain
  Java, since Android's own X25519 needs Android 13). The public key is kept
  as is, which is how the camera saves while the phone is locked. The private
  key is kept encrypted with an Android Keystore AES key that only works after
  the fingerprint, face or screen lock (per use on Android 11 and newer, for
  15 seconds on Android 10). Adding a fingerprint does not affect it. Removing
  the screen lock makes Android delete that key, and then the vault can't be
  opened: the setup screens say so. Encrypted backup (V4) is the way out.
- **After unlock** the private key lives only in the app's memory, in Kotlin
  (`VaultSession`). Dart never sees it: it asks for opened thumbnails and
  photos over the `dev.sagron.zerotrustcamera/vault` channel. Leaving the app
  wipes it, and screenshots are blocked while the vault is on screen.
- **Files** (`zt/MediaVault.java`, format "ZTV1"): each item gets a fresh
  ephemeral X25519 key, HKDF-SHA256 makes its file key, and the content is
  AES-256-GCM in 1 MiB chunks with the chunk number and a last-chunk flag in
  the nonce, so a cut or reordered file fails to open. Chunks can be opened
  one at a time, which is how videos play without a plain copy. Each item has
  three sealed files (media, thumbnail, details) under `noBackupFilesDir`,
  plus one sealed file for albums, favourites and the trash. The JVM tests
  pin the format with a fixed vector.
- **Leaving the vault.** Save to gallery and Share make a cleaned copy: a JPEG
  loses all Exif, other pictures are redrawn as JPEG, a video has its times
  cleared. Shared copies sit in the app's cache and are wiped on the next
  unlock. Imported originals stay in the gallery (Android's photo picker can't
  delete them), and the app says so.

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
