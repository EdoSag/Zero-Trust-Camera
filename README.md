# Zero-Trust Camera

The camera of the Zero-Trust suite. Photos and videos are saved without
location, time or device details unless you choose to keep them, and the
ones that matter most go straight into an encrypted vault. On the phone it
is called **ZT Camera**; in stores and on release pages, **Zero-Trust
Camera**. Same account, settings and look as the other Zero-Trust apps,
built on [Zero-Trust-Core](https://github.com/EdoSag/Zero-Trust-Core). Part
of the [Zero-Trust suite](https://zerotrust.bz).

## What it does

- **Photo and video**, with the phone's own camera extensions where it has
  them (Night, HDR, Portrait, Face retouch, Auto), zoom, tap to focus,
  exposure, flash, timer, grid and aspect ratio. **Ultra HDR photos** (on
  phones that have them, Android 14 and up) and **60 fps video** (where
  the camera can do it with the chosen quality) are switches under More
  settings.
- **Pro camera:** manual ISO, shutter, focus and white balance, with RAW
  (DNG) on phones that support it.
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
- **Private vault.** Asked once on first start: gallery or Private vault,
  and changeable under More settings. In Private mode a photo is encrypted
  in memory before anything is written, and a video is encrypted the moment
  recording stops, so neither ever reaches the gallery. It works from the
  lock screen too. The vault opens with the fingerprint, face or screen
  lock and locks again as soon as the app is left. Inside: grid, viewer,
  video player, search, favourites, albums, a trash that empties after 30
  days, disappearing photos, import from the gallery, and save or share
  copies with no metadata. Details below.
- **Encrypted backup of the vault** (off until you turn it on, needs an
  account): each file is encrypted on the phone with its own key before it
  goes to your Zero-Trust account. Everything or only chosen albums, videos
  too (up to 2 GB each), Wi-Fi only and only while charging, and a space
  saver that uploads smaller copies of photos. Restore on a new phone, and
  "In your account" opens what was backed up from another phone.
- **Photo editor:** crop, rotate, adjust (brightness, contrast, colour,
  warmth), draw, and hide faces, number plates or anything else ("Find
  faces" runs on the phone). It saves a new photo; the original is
  unchanged.
- **Clean and share.** In every app's share sheet for photos and videos: it
  shares copies with no place, time or phone details instead of the
  originals, or keeps them in the Private vault. A JPEG keeps its pixels
  exactly (only the details are cut out, along with anything hidden after
  the picture); other pictures are redrawn; MP4, MOV and 3GP videos lose
  their recording times and tag boxes. The originals are never changed.
- **QR and barcode scanner**, on the phone only. Nothing opens by itself. A
  link shows the site it really goes to and warns about plain http,
  lookalike names (paypal-login...), letters from other alphabets, short
  links, bare IP addresses and "@" tricks. Wi-Fi codes join through
  Android's own sheet; contacts, events, calls, texts and emails go to the
  ZT app when it is installed; Zero Trust invites open the right ZT app;
  two-factor secrets are never shown. "Scan code" in the share sheet reads
  a code from a screenshot. Scan history is off by default and, when on,
  kept in the Private vault.
- **Document scanner.** From the home screen or a long press on the
  launcher icon. It finds the page and takes it once it holds still (or on
  a tap), lets the corners be dragged, straightens it, and offers Enhanced,
  Original, Grayscale and Black and white looks. Pages become one PDF in
  Documents/ZT Camera, or go into the Private vault in Private mode; they
  can also be shared as a PDF or sent to ZT Notes with their text.
- **Text from photos**, read on the phone with Tesseract (English and
  Hebrew): "Copy text" for scanned pages and for photos in the Private
  vault. Nothing is uploaded.
- **No account needed.** Signing in is optional and only matters for the
  encrypted backup.
- **Offline edition** (`…-offline.apk` in each release): the same camera
  with no internet permission at all, so no account and no backup. It is
  its own app, "ZT Camera Offline", and installs next to the normal one.

## Privacy

ZT Camera works without an account. When you sign in, it is zero-knowledge like every Zero-Trust app: your data is encrypted on
your device, with keys made from your password, before anything is sent.
The server stores only ciphertext and your password itself is never
sent, so neither we nor anyone who breaks into the server can read your
data. The recovery key shown at sign-up is the only way back in if you
forget the password. How the keys work:
[Zero-Trust-Core](https://github.com/EdoSag/Zero-Trust-Core#key-hierarchy) and
[zerotrust.bz/security](https://zerotrust.bz/security/).

- The camera screens have no network code. The app goes online only for
  your account and the backup, and only if you use them.
- Scanning, text recognition, picture search and face finding all run on
  the phone.
- "How your photos are protected" in the app says what is encrypted, what
  leaves the phone and what the app can't protect.

## One account, one core

Signing in is optional. The account is the one Zero-Trust account that opens every app in the suite. Sign-in, unlock
(fingerprint or face, auto-lock), the recovery key, settings, the look,
sync and languages come from
[Zero-Trust-Core](https://github.com/EdoSag/Zero-Trust-Core), so they work
the same in every app. Accounts made before the core still sign in and
see their data.

## Install

Android: get the latest signed APK from this repository's
[Releases](https://github.com/EdoSag/Zero-Trust-Camera/releases/latest),
or from [zerotrust.bz/install](https://zerotrust.bz/install/#camera),
which also shows each file's SHA-256, the signing fingerprint and an
Obtainium link. GitHub Releases are the main way the apps are published.

Builds from GitHub check for a newer release themselves (Settings > About,
one anonymous request a day, can be turned off). Every Zero-Trust app is signed
with the same key, and Android only installs an update signed with it.

Each release has two APKs: ZT Camera, and ZT Camera Offline (no internet
permission).

## Languages

English and Hebrew, with right-to-left layout. Choose under
Settings > Appearance > Language.

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
  opened: the setup screens say so. The encrypted backup is the way out.
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

- **Search by what is in photos.** On by default, switched off in the
  vault's More menu (which also forgets what was worked out). While the vault
  is open, each photo is looked at on the phone, one at a time: a small
  picture model (MediaPipe EfficientNet-Lite0, Apache 2.0, run with LiteRT)
  names what is in it, and Tesseract reads its text. Videos are looked at
  from their thumbnail. The results live only in the sealed library, are
  never backed up, and let the search bar find "dog", "beach", "food",
  "כלב", "ים" or a word written in the photo. The model is fetched at build
  time by `tool/fetch_vision_model.sh` and checked against a pinned SHA-256.

## Build from source

Flutter 3.47.9, the version CI pins (3.50 deprecates `material.dart` and
fails analyze).

```bash
# .env (not committed; CI writes its own). Public client values only:
# never a service-role key, because .env is bundled into the APK.
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_ANON_KEY=<publishable key>

flutter pub get
dart run zt_release_checks --app camera   # shared release checks
flutter analyze && flutter test test/
flutter build apk --release
cd android && ./gradlew :capture:testDebugUnitTest   # the native tests
```

Never add `cryptography_flutter`: it breaks the core's key derivation and
sign-in fails.

## Branches and releases

Work happens on `Dev`: pull requests target `Dev`, never `main`, and get a
test build. A release is one `Dev` → `main` pull request. Every push to
`main` runs the suite's release checks, builds both editions as signed APKs and a Google Play bundle
and publishes them as a GitHub Release named "Zero-Trust Camera"
(`.github/workflows/android-apk.yml`). The workflow needs the repository
secrets `ANDROID_KEYSTORE_BASE64` and `ANDROID_KEYSTORE_PASSWORD`, the
keystore shared by all Zero-Trust apps.

## Support and security

- Help centre: [zerotrust.bz/help](https://zerotrust.bz/help/). Questions and
  account deletion: support@zerotrust.bz.
- Security problems: please report them privately to cyber@zerotrust.bz. What
  the encryption does and doesn't protect against is on
  [zerotrust.bz/security](https://zerotrust.bz/security/).
- [Privacy policy](https://zerotrust.bz/privacy/) and
  [terms](https://zerotrust.bz/terms/).

## License

Copyright (C) 2026 Edo Sagron (https://zerotrust.bz)

Zero-Trust is open source under the [GNU Affero General Public License v3.0](LICENSE)
(AGPL-3.0-only), the same licence Signal uses.

- You may use, study, change and share the code, including in your own projects.
- If you share a changed version, or run it for other people over a network, you must
  publish your full source code under the same licence.
- This program comes with no warranty; see the licence for details.

Third-party libraries keep their own licences; the app lists them under
Settings > Open-source licences. The capture module contains code from GrapheneOS
Camera under the MIT license (`android/capture/LICENSE.upstream`).

### Name and logo

The AGPL covers the code, not the brand. "Zero-Trust", "ZT", the app names and icons are
reserved. If you publish a fork, give it a different name and icon so nobody mistakes it
for an official Zero-Trust app.

### Servers

The official apps use the Zero-Trust servers. A fork must use its own backend (for
example its own Supabase project) and must not connect to the official servers.

### Contributing

Pull requests are welcome. By sending one you agree that your contribution is licensed
under the AGPL-3.0 and that the copyright holder may also license it under other terms,
including commercially. This keeps the project able to offer paid plans and other licences.
