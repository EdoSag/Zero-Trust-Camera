# Capture module

The camera screen of ZT Camera: native Kotlin on CameraX, built as an Android
library that the Flutter app module depends on.

## Where it comes from

[GrapheneOS Camera](https://github.com/GrapheneOS/Camera), MIT license, at
commit `5ddbe5a65c8928b4a0ccc126c4a50ff9d0707340` (2026-11-17). The upstream
license text is in `LICENSE.upstream` and is shown in the app under About,
Licences.

## What changed

- Package `app.grapheneos.camera` became `dev.sagron.zerotrustcamera.capture`.
  The module is a library, not an application; the app module owns the
  application id, the launcher icon and the label.
- Upstream name, icon and backup rules were removed.
- **Location is gone.** The location permissions are not declared, the geo
  tagging switch is hidden and `requireLocation` is always false.
- **Metadata allowlist** (`ExifHelper.kt`, `ImageSaver.kt`): every EXIF tag
  is cleared and only the orientation is kept. Date and time, and camera make
  and model, are written back only when their switch is on (`CamConfig`,
  both off by default). A capture made for another app keeps nothing.
- **Videos** (`zt/Mp4TimeScrubber.java`, used by `VideoCapturer.kt`): the
  creation and modification times in the MP4 header boxes are set to zero,
  unless date and time are kept. A video recorded for another app is always
  scrubbed.
- **More settings** has a row that opens the Flutter screens (`zt/ZtApp.kt`)
  and the two "keep" switches in place of upstream's "remove EXIF" switch.
- **Language** (`zt/ZtLocale.kt`, `zt/ZtBaseActivity.kt`): follows the
  language picked in the Flutter settings. Hebrew strings were added
  (`res/values-iw`).

- **Private vault** (`zt/MediaVault.java`, `zt/VaultKeys.kt`,
  `zt/VaultStore.kt`, `zt/VaultTransfer.kt`, `zt/VaultVideoActivity.kt`,
  `zt/PrivateVaultSetup.kt`): in Private mode `ImageSaver` seals the finished
  JPEG instead of writing it to MediaStore, and `VideoCapturer` records into a
  file inside the app that is sealed and wiped when recording stops. The
  gallery button opens the vault (Flutter) instead of the in-app gallery, and
  the viewfinder no longer shows the gallery's last photo. More settings has a
  Private vault switch, and the first start asks gallery or vault.

Everything added for Zero-Trust lives under `zt/`, to keep upstream files as
close to the original as possible.

## Taking in upstream changes

1. Diff upstream between the commit above and the new one.
2. Apply the changes under `src/main/kotlin` and `src/main/res`, with the
   package renamed.
3. Check the list above still holds, above all that nothing re-adds location
   or writes metadata outside the allowlist. `test/arb_test.dart` in the
   Flutter app fails when a location permission returns or a string has no
   Hebrew.
4. Update the commit in this file.

## Tests

`./gradlew :capture:testDebugUnitTest` from `android/` runs the JVM tests,
including `MediaVaultTest` (sealing round trips at every chunk boundary, any
changed byte, cut, dropped or swapped chunks, ranged reads, the RFC 7748
X25519 vector and a fixed vector for the file format).
