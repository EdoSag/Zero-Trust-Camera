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
      'Optional: the camera works without one. An account will be needed for the private vault and encrypted backup, which come in a later version.';

  @override
  String get accountManage => 'Password, security and sign out';

  @override
  String get privacySection => 'What your photos keep';

  @override
  String get privacyLocation => 'Location';

  @override
  String get privacyLocationNever =>
      'Never saved. The app has no location permission.';

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
}
