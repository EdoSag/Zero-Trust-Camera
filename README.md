# Zero-Trust Camera

The camera of the Zero-Trust suite (on the phone: **ZT Camera**). Photos and
videos are saved without location, time or device details unless you choose
to keep them. Same account, settings and look as the other Zero-Trust apps,
built on [Zero-Trust-Core](https://github.com/EdoSag/Zero-Trust-Core).

## What it does (0.5)

- **Photo and video**, with the phone's own camera extensions where it has
  them (Night, HDR, Portrait, Face retouch, Auto), zoom, tap to focus,
  exposure, flash, timer, grid and aspect ratio.
- **QR and barcode scanner**, on the phone only (new in 0.4: it reads what
  a code holds). Nothing opens by itself. A link shows the site it really
  goes to and warns about plain http, lookalike names (paypal-login...),
  letters from other alphabets, short links, bare IP addresses and "@"
  tricks. Wi-Fi codes join through Android's own sheet; contacts, events,
  calls, texts and emails go to the ZT app when it is installed; Zero Trust
  invites open the right ZT app; two-factor secrets are never shown. "Scan
  code" in the share sheet reads a code from a screenshot. Scan history is
  off by default and, when on, kept in the Private vault.
- **Clean files by default.** A saved photo keeps only its orientation. A
  saved video has its recording time cleared. Two switches under More
  settings keep the date and time, or the camera model, for people who want
  them. Location is never written into a photo or video. The Private vault
  can keep the place of new items in their encrypted details if that is
  turned on there (off by default; the location permission is asked for only
  then), and copies saved or shared out of the vault never carry it.
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
- **Clean and share** (new in 0.3). In every app's share sheet for photos
  and videos: it shares copies with no place, time or phone details instead
  of the originals, or keeps them in the Private vault. A JPEG keeps its
  pixels exactly (only the details are cut out, along with anything hidden
  after the picture); other pictures are redrawn; MP4, MOV and 3GP videos
  lose their recording times and tag boxes. The originals are never changed.
- **Ultra HDR photos** (new in 0.3, on phones that have them, Android 14 and
  up). A switch under More settings. Off by default.
- **60 fps video** (new in 0.3, where the camera can do it with the chosen
  quality). A switch under More settings; when a combination can't do 60,
  the app says so and records at 30.
- **Document scanner** (new in 0.5). From the home screen or a long press
  on the launcher icon. It finds the page and takes it once it holds still
  (or on a tap), lets the corners be dragged, straightens it, and offers
  Enhanced, Original, Grayscale and Black and white looks. Pages become one
  PDF in Documents/ZT Camera, or go into the Private vault in Private mode;
  they can also be shared as a PDF or sent to ZT Notes with their text.
- **Text from photos** (new in 0.5), read on the phone with Tesseract
  (English and Hebrew): "Copy text" for scanned pages and for photos in the
  Private vault. Nothing is uploaded.
- **No account needed.** Signing in is optional and only matters for what
  comes next.
- **English and Hebrew**, with right-to-left layout.

Not in 0.5 yet: encrypted backup of the vault, the photo editor, pro controls and RAW and ZT app actions for scanned codes. The plan is in the project's
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
- **Leaving the vault.** Save to gallery and Share make a cleaned copy
  (`zt/MediaCleaner.kt`): a JPEG loses every APP1 to APP15 segment, comments
  and trailing data but keeps its orientation, other pictures are redrawn, a
  video has its times and tag boxes cleared. An Ultra HDR photo leaves as a
  plain photo, since its HDR part lives in the details that are cut out. Shared copies sit in the app's cache and are wiped on the next
  unlock. Imported originals stay in the gallery (Android's photo picker can't
  delete them), and the app says so.
- **Disappearing photos.** Off by default. In the vault's More menu, new
  photos and videos can be set to delete themselves for good after 1 hour,
  1 day, 1 week or 30 days (counted from when each was taken), and any item
  can be put on a timer or taken off it by hand. They skip the trash and are
  never backed up (a copy already in the account is deleted when the timer
  starts), and a video open in the player closes when it goes. The timers live in the sealed library, so an item whose
  time is up is deleted the next time the vault opens, before anything is
  shown, and every 30 seconds while it is open.

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
