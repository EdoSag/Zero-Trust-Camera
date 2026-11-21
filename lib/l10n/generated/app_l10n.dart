import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_l10n_en.dart';
import 'app_l10n_he.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppL10n
/// returned by `AppL10n.of(context)`.
///
/// Applications need to include `AppL10n.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'generated/app_l10n.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppL10n.localizationsDelegates,
///   supportedLocales: AppL10n.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppL10n.supportedLocales
/// property.
abstract class AppL10n {
  AppL10n(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppL10n of(BuildContext context) {
    return Localizations.of<AppL10n>(context, AppL10n)!;
  }

  static const LocalizationsDelegate<AppL10n> delegate = _AppL10nDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('he'),
  ];

  /// No description provided for @homeTitle.
  ///
  /// In en, this message translates to:
  /// **'ZT Camera'**
  String get homeTitle;

  /// No description provided for @homeTagline.
  ///
  /// In en, this message translates to:
  /// **'Photos and videos are saved without location, date or phone details.'**
  String get homeTagline;

  /// No description provided for @backToCamera.
  ///
  /// In en, this message translates to:
  /// **'Back to camera'**
  String get backToCamera;

  /// No description provided for @accountSignIn.
  ///
  /// In en, this message translates to:
  /// **'Sign in or create an account'**
  String get accountSignIn;

  /// No description provided for @accountOptional.
  ///
  /// In en, this message translates to:
  /// **'Optional: the camera and the Private vault work without one. An account will be needed for encrypted backup, which comes in a later version.'**
  String get accountOptional;

  /// No description provided for @accountManage.
  ///
  /// In en, this message translates to:
  /// **'Password, security and sign out'**
  String get accountManage;

  /// No description provided for @privacySection.
  ///
  /// In en, this message translates to:
  /// **'What your photos keep'**
  String get privacySection;

  /// No description provided for @privacyLocation.
  ///
  /// In en, this message translates to:
  /// **'Location'**
  String get privacyLocation;

  /// No description provided for @privacyLocationNever.
  ///
  /// In en, this message translates to:
  /// **'Never saved. The app has no location permission.'**
  String get privacyLocationNever;

  /// No description provided for @privacyDateTime.
  ///
  /// In en, this message translates to:
  /// **'Date and time'**
  String get privacyDateTime;

  /// No description provided for @privacyModel.
  ///
  /// In en, this message translates to:
  /// **'Phone make and model'**
  String get privacyModel;

  /// No description provided for @privacyOffByDefault.
  ///
  /// In en, this message translates to:
  /// **'Not saved, unless you turn it on in the camera\'s settings'**
  String get privacyOffByDefault;

  /// No description provided for @privacyOtherApps.
  ///
  /// In en, this message translates to:
  /// **'Photos taken for other apps'**
  String get privacyOtherApps;

  /// No description provided for @privacyOtherAppsNever.
  ///
  /// In en, this message translates to:
  /// **'Always saved with nothing extra'**
  String get privacyOtherAppsNever;

  /// No description provided for @cameraScreensLicence.
  ///
  /// In en, this message translates to:
  /// **'The camera screens started from GrapheneOS Camera (MIT licence). ZT Camera is not made or endorsed by GrapheneOS.'**
  String get cameraScreensLicence;

  /// No description provided for @homeVault.
  ///
  /// In en, this message translates to:
  /// **'Private vault'**
  String get homeVault;

  /// No description provided for @homeVaultSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Encrypted photos and videos, only in this app'**
  String get homeVaultSubtitle;

  /// No description provided for @vaultTitle.
  ///
  /// In en, this message translates to:
  /// **'Private vault'**
  String get vaultTitle;

  /// No description provided for @vaultLocked.
  ///
  /// In en, this message translates to:
  /// **'The vault is locked'**
  String get vaultLocked;

  /// No description provided for @vaultLockedBody.
  ///
  /// In en, this message translates to:
  /// **'Photos and videos here are encrypted. Open them with your fingerprint, face or screen lock.'**
  String get vaultLockedBody;

  /// No description provided for @vaultUnlock.
  ///
  /// In en, this message translates to:
  /// **'Unlock'**
  String get vaultUnlock;

  /// No description provided for @vaultLock.
  ///
  /// In en, this message translates to:
  /// **'Lock'**
  String get vaultLock;

  /// No description provided for @vaultSetUpTitle.
  ///
  /// In en, this message translates to:
  /// **'Keep photos in a Private vault'**
  String get vaultSetUpTitle;

  /// No description provided for @vaultSetUpBody.
  ///
  /// In en, this message translates to:
  /// **'New photos and videos are encrypted on this phone and open only here, after your fingerprint, face or screen lock. The gallery and other apps never see them. No account is needed.\n\nIf you remove your phone\'s screen lock, the vault can\'t be opened again.'**
  String get vaultSetUpBody;

  /// No description provided for @vaultSetUp.
  ///
  /// In en, this message translates to:
  /// **'Set up the vault'**
  String get vaultSetUp;

  /// No description provided for @vaultNeedsScreenLock.
  ///
  /// In en, this message translates to:
  /// **'Set a screen lock on your phone first. The vault opens with it.'**
  String get vaultNeedsScreenLock;

  /// No description provided for @vaultKeyLost.
  ///
  /// In en, this message translates to:
  /// **'The vault\'s key is gone because the phone\'s screen lock was removed, so its photos can\'t be opened.'**
  String get vaultKeyLost;

  /// No description provided for @vaultFailed.
  ///
  /// In en, this message translates to:
  /// **'That didn\'t work. Try again.'**
  String get vaultFailed;

  /// No description provided for @vaultAll.
  ///
  /// In en, this message translates to:
  /// **'All'**
  String get vaultAll;

  /// No description provided for @vaultFavourites.
  ///
  /// In en, this message translates to:
  /// **'Favourites'**
  String get vaultFavourites;

  /// No description provided for @vaultAlbums.
  ///
  /// In en, this message translates to:
  /// **'Albums'**
  String get vaultAlbums;

  /// No description provided for @vaultTrash.
  ///
  /// In en, this message translates to:
  /// **'Trash'**
  String get vaultTrash;

  /// No description provided for @vaultEmpty.
  ///
  /// In en, this message translates to:
  /// **'Nothing here yet. Photos and videos you take in Private mode, and ones you import, show up here.'**
  String get vaultEmpty;

  /// No description provided for @vaultTrashEmpty.
  ///
  /// In en, this message translates to:
  /// **'The trash is empty'**
  String get vaultTrashEmpty;

  /// No description provided for @vaultTrashNote.
  ///
  /// In en, this message translates to:
  /// **'Items in the trash are deleted for good after 30 days.'**
  String get vaultTrashNote;

  /// No description provided for @vaultImport.
  ///
  /// In en, this message translates to:
  /// **'Import from gallery'**
  String get vaultImport;

  /// No description provided for @vaultImported.
  ///
  /// In en, this message translates to:
  /// **'Added {count}. The originals are still in your gallery: delete them there if you want them gone.'**
  String vaultImported(int count);

  /// No description provided for @vaultPrivateMode.
  ///
  /// In en, this message translates to:
  /// **'Private mode for new photos'**
  String get vaultPrivateMode;

  /// No description provided for @vaultPrivateModeOn.
  ///
  /// In en, this message translates to:
  /// **'New photos and videos go here'**
  String get vaultPrivateModeOn;

  /// No description provided for @vaultPrivateModeOff.
  ///
  /// In en, this message translates to:
  /// **'New photos and videos go to the gallery'**
  String get vaultPrivateModeOff;

  /// No description provided for @vaultFavourite.
  ///
  /// In en, this message translates to:
  /// **'Favourite'**
  String get vaultFavourite;

  /// No description provided for @vaultAddToAlbum.
  ///
  /// In en, this message translates to:
  /// **'Add to album'**
  String get vaultAddToAlbum;

  /// No description provided for @vaultNewAlbum.
  ///
  /// In en, this message translates to:
  /// **'New album'**
  String get vaultNewAlbum;

  /// No description provided for @vaultAlbumName.
  ///
  /// In en, this message translates to:
  /// **'Album name'**
  String get vaultAlbumName;

  /// No description provided for @vaultCreate.
  ///
  /// In en, this message translates to:
  /// **'Create'**
  String get vaultCreate;

  /// No description provided for @vaultCancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get vaultCancel;

  /// No description provided for @vaultDelete.
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get vaultDelete;

  /// No description provided for @vaultSaveToGallery.
  ///
  /// In en, this message translates to:
  /// **'Save to gallery'**
  String get vaultSaveToGallery;

  /// No description provided for @vaultSaved.
  ///
  /// In en, this message translates to:
  /// **'Saved {count} to the gallery, without location, date or phone details'**
  String vaultSaved(int count);

  /// No description provided for @vaultShare.
  ///
  /// In en, this message translates to:
  /// **'Share without details'**
  String get vaultShare;

  /// No description provided for @vaultMoveToTrash.
  ///
  /// In en, this message translates to:
  /// **'Move to trash'**
  String get vaultMoveToTrash;

  /// No description provided for @vaultRestore.
  ///
  /// In en, this message translates to:
  /// **'Restore'**
  String get vaultRestore;

  /// No description provided for @vaultDeleteForever.
  ///
  /// In en, this message translates to:
  /// **'Delete for good'**
  String get vaultDeleteForever;

  /// No description provided for @vaultDeleteForeverConfirm.
  ///
  /// In en, this message translates to:
  /// **'Delete {count, plural, =1{this item} other{{count} items}} for good? This can\'t be undone.'**
  String vaultDeleteForeverConfirm(int count);

  /// No description provided for @vaultSelected.
  ///
  /// In en, this message translates to:
  /// **'{count} selected'**
  String vaultSelected(int count);

  /// No description provided for @vaultRemoveFromAlbum.
  ///
  /// In en, this message translates to:
  /// **'Remove from album'**
  String get vaultRemoveFromAlbum;

  /// No description provided for @vaultDeleteAlbum.
  ///
  /// In en, this message translates to:
  /// **'Delete album'**
  String get vaultDeleteAlbum;

  /// No description provided for @vaultAlbumCount.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 item} other{{count} items}}'**
  String vaultAlbumCount(int count);

  /// No description provided for @vaultNoAlbums.
  ///
  /// In en, this message translates to:
  /// **'No albums yet'**
  String get vaultNoAlbums;

  /// No description provided for @vaultPlay.
  ///
  /// In en, this message translates to:
  /// **'Play'**
  String get vaultPlay;

  /// No description provided for @scanHistoryTitle.
  ///
  /// In en, this message translates to:
  /// **'Scan history'**
  String get scanHistoryTitle;

  /// No description provided for @scanHistoryKeep.
  ///
  /// In en, this message translates to:
  /// **'Keep scan history'**
  String get scanHistoryKeep;

  /// No description provided for @scanHistoryKeepHint.
  ///
  /// In en, this message translates to:
  /// **'Codes you scan are kept here, in the Private vault, where only you can open them.'**
  String get scanHistoryKeepHint;

  /// No description provided for @scanHistoryEmpty.
  ///
  /// In en, this message translates to:
  /// **'No scans kept yet.'**
  String get scanHistoryEmpty;

  /// No description provided for @scanHistoryDelete.
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get scanHistoryDelete;

  /// No description provided for @scanHistoryClear.
  ///
  /// In en, this message translates to:
  /// **'Clear all'**
  String get scanHistoryClear;

  /// No description provided for @scanHistoryClearTitle.
  ///
  /// In en, this message translates to:
  /// **'Clear scan history?'**
  String get scanHistoryClearTitle;

  /// No description provided for @scanHistoryClearMessage.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 scan will be deleted.} other{{count} scans will be deleted.}}'**
  String scanHistoryClearMessage(int count);

  /// No description provided for @scanHistoryCopied.
  ///
  /// In en, this message translates to:
  /// **'Copied'**
  String get scanHistoryCopied;

  /// No description provided for @homeScanDocument.
  ///
  /// In en, this message translates to:
  /// **'Scan a document'**
  String get homeScanDocument;

  /// No description provided for @homeScanDocumentSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Straight, clean pages as a PDF, with the text read on your phone'**
  String get homeScanDocumentSubtitle;

  /// No description provided for @homePanorama.
  ///
  /// In en, this message translates to:
  /// **'Panorama (beta)'**
  String get homePanorama;

  /// No description provided for @homePanoramaSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Turn slowly and the phone joins the view into one wide photo'**
  String get homePanoramaSubtitle;

  /// No description provided for @homeProCamera.
  ///
  /// In en, this message translates to:
  /// **'Pro camera'**
  String get homeProCamera;

  /// No description provided for @homeProCameraSubtitle.
  ///
  /// In en, this message translates to:
  /// **'ISO, shutter speed, focus and white balance by hand, and RAW photos'**
  String get homeProCameraSubtitle;

  /// No description provided for @vaultCopyText.
  ///
  /// In en, this message translates to:
  /// **'Copy text'**
  String get vaultCopyText;

  /// No description provided for @vaultEdit.
  ///
  /// In en, this message translates to:
  /// **'Edit a copy'**
  String get vaultEdit;

  /// No description provided for @vaultNoText.
  ///
  /// In en, this message translates to:
  /// **'No text found in this photo'**
  String get vaultNoText;

  /// No description provided for @vaultTextTitle.
  ///
  /// In en, this message translates to:
  /// **'Text'**
  String get vaultTextTitle;

  /// No description provided for @vaultCopy.
  ///
  /// In en, this message translates to:
  /// **'Copy'**
  String get vaultCopy;

  /// No description provided for @vaultBackup.
  ///
  /// In en, this message translates to:
  /// **'Encrypted backup'**
  String get vaultBackup;

  /// No description provided for @backupTitle.
  ///
  /// In en, this message translates to:
  /// **'Encrypted backup'**
  String get backupTitle;

  /// No description provided for @backupIntro.
  ///
  /// In en, this message translates to:
  /// **'Each photo is encrypted on this phone with its own key before it goes to your Zero Trust account. The server can\'t see it. Restore it on a new phone with your account.'**
  String get backupIntro;

  /// No description provided for @backupSignIn.
  ///
  /// In en, this message translates to:
  /// **'Sign in to your Zero Trust account on the home screen to back up the vault.'**
  String get backupSignIn;

  /// No description provided for @backupOn.
  ///
  /// In en, this message translates to:
  /// **'Back up the vault'**
  String get backupOn;

  /// No description provided for @backupCount.
  ///
  /// In en, this message translates to:
  /// **'{done} of {total} backed up'**
  String backupCount(int done, int total);

  /// No description provided for @backupEverything.
  ///
  /// In en, this message translates to:
  /// **'Everything in the vault'**
  String get backupEverything;

  /// No description provided for @backupAlbums.
  ///
  /// In en, this message translates to:
  /// **'Only these albums'**
  String get backupAlbums;

  /// No description provided for @backupVideos.
  ///
  /// In en, this message translates to:
  /// **'Videos too'**
  String get backupVideos;

  /// No description provided for @backupVideosSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Videos take a lot of storage. Each up to 2 GB; longer ones stay on this phone only.'**
  String get backupVideosSubtitle;

  /// No description provided for @backupNow.
  ///
  /// In en, this message translates to:
  /// **'Back up now'**
  String get backupNow;

  /// No description provided for @backupRestore.
  ///
  /// In en, this message translates to:
  /// **'Restore to this phone'**
  String get backupRestore;

  /// No description provided for @backupRestoreSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Brings back photos from your account that aren\'t on this phone'**
  String get backupRestoreSubtitle;

  /// No description provided for @backupDone.
  ///
  /// In en, this message translates to:
  /// **'{count} backed up'**
  String backupDone(int count);

  /// No description provided for @backupRestored.
  ///
  /// In en, this message translates to:
  /// **'{count} restored'**
  String backupRestored(int count);

  /// No description provided for @backupTooLarge.
  ///
  /// In en, this message translates to:
  /// **'{count} too large, kept on this phone only'**
  String backupTooLarge(int count);

  /// No description provided for @backupFailed.
  ///
  /// In en, this message translates to:
  /// **'{count} didn\'t go through. They\'ll be tried again next time.'**
  String backupFailed(int count);

  /// No description provided for @backupFull.
  ///
  /// In en, this message translates to:
  /// **'Your account\'s storage is full. The rest will wait.'**
  String get backupFull;

  /// No description provided for @backupProgress.
  ///
  /// In en, this message translates to:
  /// **'{done} of {total}'**
  String backupProgress(int done, int total);

  /// No description provided for @backupWifiOnly.
  ///
  /// In en, this message translates to:
  /// **'Wi-Fi only'**
  String get backupWifiOnly;

  /// No description provided for @backupWifiOnlySubtitle.
  ///
  /// In en, this message translates to:
  /// **'The automatic backup waits for Wi-Fi and doesn\'t use mobile data'**
  String get backupWifiOnlySubtitle;

  /// No description provided for @backupChargingOnly.
  ///
  /// In en, this message translates to:
  /// **'Only while charging'**
  String get backupChargingOnly;

  /// No description provided for @backupChargingOnlySubtitle.
  ///
  /// In en, this message translates to:
  /// **'The automatic backup waits until the phone is on the charger'**
  String get backupChargingOnlySubtitle;

  /// No description provided for @backupNowSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Runs right away, on any network'**
  String get backupNowSubtitle;

  /// No description provided for @vaultSearch.
  ///
  /// In en, this message translates to:
  /// **'Search'**
  String get vaultSearch;

  /// No description provided for @vaultSearchClose.
  ///
  /// In en, this message translates to:
  /// **'Close search'**
  String get vaultSearchClose;

  /// No description provided for @vaultSearchHint.
  ///
  /// In en, this message translates to:
  /// **'Album, month, year or date'**
  String get vaultSearchHint;

  /// No description provided for @vaultSearchDates.
  ///
  /// In en, this message translates to:
  /// **'Pick dates'**
  String get vaultSearchDates;

  /// No description provided for @vaultSearchEmpty.
  ///
  /// In en, this message translates to:
  /// **'Nothing in the vault matches'**
  String get vaultSearchEmpty;
}

class _AppL10nDelegate extends LocalizationsDelegate<AppL10n> {
  const _AppL10nDelegate();

  @override
  Future<AppL10n> load(Locale locale) {
    return SynchronousFuture<AppL10n>(lookupAppL10n(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'he'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppL10nDelegate old) => false;
}

AppL10n lookupAppL10n(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppL10nEn();
    case 'he':
      return AppL10nHe();
  }

  throw FlutterError(
    'AppL10n.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
