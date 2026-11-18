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
  /// **'Optional: the camera works without one. An account will be needed for the private vault and encrypted backup, which come in a later version.'**
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
