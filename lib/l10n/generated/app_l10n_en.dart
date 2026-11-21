// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_l10n.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppL10nEn extends AppL10n {
  AppL10nEn([String locale = 'en']) : super(locale);

  @override
  String get homeTitle => 'ZT Camera';

  @override
  String get homeTagline =>
      'Photos and videos are saved without location, date or phone details.';

  @override
  String get backToCamera => 'Back to camera';

  @override
  String get accountSignIn => 'Sign in or create an account';

  @override
  String get accountOptional =>
      'Optional: the camera and the Private vault work without one. An account is needed only for the encrypted backup of the vault.';

  @override
  String get accountManage => 'Password, security and sign out';

  @override
  String get privacySection => 'What your photos keep';

  @override
  String get privacyLocation => 'Location';

  @override
  String get privacyLocationNever =>
      'Never saved in a photo or video. Only inside the Private vault, and only if you turn it on there.';

  @override
  String get privacyDateTime => 'Date and time';

  @override
  String get privacyModel => 'Phone make and model';

  @override
  String get privacyOffByDefault =>
      'Not saved, unless you turn it on in the camera\'s settings';

  @override
  String get privacyOtherApps => 'Photos taken for other apps';

  @override
  String get privacyOtherAppsNever => 'Always saved with nothing extra';

  @override
  String get cameraScreensLicence =>
      'The camera screens started from GrapheneOS Camera (MIT licence). ZT Camera is not made or endorsed by GrapheneOS.';

  @override
  String get homeVault => 'Private vault';

  @override
  String get homeVaultSubtitle =>
      'Encrypted photos and videos, only in this app';

  @override
  String get vaultTitle => 'Private vault';

  @override
  String get vaultLocked => 'The vault is locked';

  @override
  String get vaultLockedBody =>
      'Photos and videos here are encrypted. Open them with your fingerprint, face or screen lock.';

  @override
  String get vaultUnlock => 'Unlock';

  @override
  String get vaultLock => 'Lock';

  @override
  String get vaultSetUpTitle => 'Keep photos in a Private vault';

  @override
  String get vaultSetUpBody =>
      'New photos and videos are encrypted on this phone and open only here, after your fingerprint, face or screen lock. The gallery and other apps never see them. No account is needed.\n\nIf you remove your phone\'s screen lock, the vault can\'t be opened again.';

  @override
  String get vaultSetUp => 'Set up the vault';

  @override
  String get vaultNeedsScreenLock =>
      'Set a screen lock on your phone first. The vault opens with it.';

  @override
  String get vaultKeyLost =>
      'The vault\'s key is gone because the phone\'s screen lock was removed, so its photos can\'t be opened.';

  @override
  String get vaultFailed => 'That didn\'t work. Try again.';

  @override
  String get vaultAll => 'All';

  @override
  String get vaultFavourites => 'Favourites';

  @override
  String get vaultAlbums => 'Albums';

  @override
  String get vaultTrash => 'Trash';

  @override
  String get vaultEmpty =>
      'Nothing here yet. Photos and videos you take in Private mode, and ones you import, show up here.';

  @override
  String get vaultTrashEmpty => 'The trash is empty';

  @override
  String get vaultTrashNote =>
      'Items in the trash are deleted for good after 30 days.';

  @override
  String get vaultImport => 'Import from gallery';

  @override
  String vaultImported(int count) {
    return 'Added $count. The originals are still in your gallery: delete them there if you want them gone.';
  }

  @override
  String get vaultPrivateMode => 'Private mode for new photos';

  @override
  String get vaultPrivateModeOn => 'New photos and videos go here';

  @override
  String get vaultPrivateModeOff => 'New photos and videos go to the gallery';

  @override
  String get vaultFavourite => 'Favourite';

  @override
  String get vaultAddToAlbum => 'Add to album';

  @override
  String get vaultNewAlbum => 'New album';

  @override
  String get vaultAlbumName => 'Album name';

  @override
  String get vaultCreate => 'Create';

  @override
  String get vaultCancel => 'Cancel';

  @override
  String get vaultDelete => 'Delete';

  @override
  String get vaultSaveToGallery => 'Save to gallery';

  @override
  String vaultSaved(int count) {
    return 'Saved $count to the gallery, without location, date or phone details';
  }

  @override
  String get vaultShare => 'Share without details';

  @override
  String get vaultMoveToTrash => 'Move to trash';

  @override
  String get vaultRestore => 'Restore';

  @override
  String get vaultDeleteForever => 'Delete for good';

  @override
  String vaultDeleteForeverConfirm(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count items',
      one: 'this item',
    );
    return 'Delete $_temp0 for good? This can\'t be undone.';
  }

  @override
  String vaultSelected(int count) {
    return '$count selected';
  }

  @override
  String get vaultRemoveFromAlbum => 'Remove from album';

  @override
  String get vaultDeleteAlbum => 'Delete album';

  @override
  String vaultAlbumCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count items',
      one: '1 item',
    );
    return '$_temp0';
  }

  @override
  String get vaultNoAlbums => 'No albums yet';

  @override
  String get vaultPlay => 'Play';

  @override
  String get scanHistoryTitle => 'Scan history';

  @override
  String get scanHistoryKeep => 'Keep scan history';

  @override
  String get scanHistoryKeepHint =>
      'Codes you scan are kept here, in the Private vault, where only you can open them.';

  @override
  String get scanHistoryEmpty => 'No scans kept yet.';

  @override
  String get scanHistoryDelete => 'Delete';

  @override
  String get scanHistoryClear => 'Clear all';

  @override
  String get scanHistoryClearTitle => 'Clear scan history?';

  @override
  String scanHistoryClearMessage(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count scans will be deleted.',
      one: '1 scan will be deleted.',
    );
    return '$_temp0';
  }

  @override
  String get scanHistoryCopied => 'Copied';

  @override
  String get homeScanDocument => 'Scan a document';

  @override
  String get homeScanDocumentSubtitle =>
      'Straight, clean pages as a PDF, with the text read on your phone';

  @override
  String get homePanorama => 'Panorama (beta)';

  @override
  String get homePanoramaSubtitle =>
      'Turn slowly and the phone joins the view into one wide photo';

  @override
  String get homeProCamera => 'Pro camera';

  @override
  String get homeProCameraSubtitle =>
      'ISO, shutter speed, focus and white balance by hand, and RAW photos';

  @override
  String get vaultCopyText => 'Copy text';

  @override
  String get vaultEdit => 'Edit a copy';

  @override
  String get vaultNoText => 'No text found in this photo';

  @override
  String get vaultTextTitle => 'Text';

  @override
  String get vaultCopy => 'Copy';

  @override
  String get vaultBackup => 'Encrypted backup';

  @override
  String get backupTitle => 'Encrypted backup';

  @override
  String get backupIntro =>
      'Each photo is encrypted on this phone with its own key before it goes to your Zero Trust account. The server can\'t see it. Restore it on a new phone with your account.';

  @override
  String get backupSignIn =>
      'Sign in to your Zero Trust account on the home screen to back up the vault.';

  @override
  String get backupOn => 'Back up the vault';

  @override
  String backupCount(int done, int total) {
    return '$done of $total backed up';
  }

  @override
  String get backupEverything => 'Everything in the vault';

  @override
  String get backupAlbums => 'Only these albums';

  @override
  String get backupVideos => 'Videos too';

  @override
  String get backupVideosSubtitle =>
      'Videos take a lot of storage. Each up to 2 GB; longer ones stay on this phone only.';

  @override
  String get backupNow => 'Back up now';

  @override
  String get backupRestore => 'Restore to this phone';

  @override
  String get backupRestoreSubtitle =>
      'Brings back photos from your account that aren\'t on this phone';

  @override
  String backupDone(int count) {
    return '$count backed up';
  }

  @override
  String backupRestored(int count) {
    return '$count restored';
  }

  @override
  String backupTooLarge(int count) {
    return '$count too large, kept on this phone only';
  }

  @override
  String backupFailed(int count) {
    return '$count didn\'t go through. They\'ll be tried again next time.';
  }

  @override
  String get backupFull =>
      'Your account\'s storage is full. The rest will wait.';

  @override
  String backupProgress(int done, int total) {
    return '$done of $total';
  }

  @override
  String get backupWifiOnly => 'Wi-Fi only';

  @override
  String get backupWifiOnlySubtitle =>
      'The automatic backup waits for Wi-Fi and doesn\'t use mobile data';

  @override
  String get backupChargingOnly => 'Only while charging';

  @override
  String get backupChargingOnlySubtitle =>
      'The automatic backup waits until the phone is on the charger';

  @override
  String get backupNowSubtitle => 'Runs right away, on any network';

  @override
  String get vaultSearch => 'Search';

  @override
  String get vaultSearchClose => 'Close search';

  @override
  String get vaultSearchHint => 'Album, month, year or date';

  @override
  String get vaultSearchDates => 'Pick dates';

  @override
  String get vaultSearchEmpty => 'Nothing in the vault matches';

  @override
  String get protectTitle => 'How your photos are protected';

  @override
  String get protectSubtitle =>
      'What is encrypted, what leaves the phone, and what the app can\'t protect';

  @override
  String get protectGalleryTitle => 'Photos in the gallery';

  @override
  String get protectGalleryBody =>
      'Saved with only what a picture needs: size, rotation and colour. No location, date, phone model or hidden thumbnail, unless you turn date or model on in the camera\'s settings. They are ordinary files: any app you allow to see your photos can open them.';

  @override
  String get protectVaultTitle => 'The Private vault';

  @override
  String get protectVaultBody =>
      'Each photo is encrypted in memory, with its own key, before it reaches storage. That key is sealed to this phone\'s vault key, which opens only after your fingerprint, face or screen lock. From the lock screen the camera can add to the vault but can\'t open it. If you turn on location for the vault, the place is kept in the item\'s encrypted details, never in the picture.';

  @override
  String get protectVideoTitle => 'Private videos';

  @override
  String get protectVideoBody =>
      'A video is recorded to a file only this app can reach, encrypted when you stop, and the file is then overwritten and deleted. Until you stop it is protected by Android\'s storage encryption, not by the vault. \"Record Private videos in memory\" in the camera\'s settings keeps the recording off storage; clips are then limited by the phone\'s memory.';

  @override
  String get protectBackupTitle => 'Encrypted backup';

  @override
  String get protectBackupBody =>
      'Off until you turn it on. Each file is encrypted on this phone with its own key before it goes to your Zero Trust account. The server sees sizes and times, never pictures or album names. Whoever can sign in to your account and unlock it can restore them, so keep its password safe.';

  @override
  String get protectNetworkTitle => 'Network';

  @override
  String get protectNetworkBody =>
      'The camera screens have no network code. The app goes online only for your account and the backup, and only if you use them.';

  @override
  String get protectPermissionsTitle => 'Permissions';

  @override
  String get protectPermissionsBody =>
      'Camera. Microphone, for video only. Notifications, for backup progress and save errors. Network state, to tell Wi-Fi from mobile data for the backup. Location, asked for only if you turn on location for the Private vault. No access to your files.';

  @override
  String get protectLimitsTitle => 'What it can\'t protect';

  @override
  String get protectLimitsBody =>
      'A phone that is unlocked with the vault open. Photos you save or share out of the vault: they leave as ordinary files, without location, date or phone details. A phone whose screen lock someone else knows.';

  @override
  String get defaultCameraTitle => 'Use as your camera';

  @override
  String get defaultCameraSubtitle =>
      'Open ZT Camera from the power button and the lock screen';

  @override
  String get defaultCameraIntro =>
      'Android has no single switch for the default camera, so it takes two steps.';

  @override
  String get defaultCameraStep1 =>
      'Double-press the power button, or open the camera from the lock screen. If Android asks which camera to use, pick ZT Camera and tap Always.';

  @override
  String get defaultCameraStep2 =>
      'If another camera opens without asking, open that camera\'s App info, tap Open by default, clear its defaults, and try step 1 again.';

  @override
  String get defaultCameraOpenSettings => 'Open default apps';

  @override
  String get defaultCameraOtherApps =>
      'Inside other apps, for example a chat app\'s camera button, Android 11 and later always use the camera that came with the phone. No app can change that.';

  @override
  String get defaultCameraCardTitle => 'Make ZT Camera your camera?';

  @override
  String get defaultCameraCardBody =>
      'Open it from the power button and the lock screen instead of the phone\'s own camera.';

  @override
  String get defaultCameraShowMe => 'Show me how';

  @override
  String get defaultCameraNotNow => 'Not now';

  @override
  String get vaultMore => 'More';

  @override
  String get vaultQuickHide => 'Lock when face down';

  @override
  String get vaultQuickHideOn =>
      'The vault now locks when you put the phone face down';

  @override
  String get vaultLocation => 'Add location to vault photos';

  @override
  String get vaultLocationOn =>
      'New Private vault photos keep their place, inside the vault only. Saved and shared copies never carry it.';
}
