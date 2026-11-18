// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_l10n.dart';

// ignore_for_file: type=lint

/// The translations for Hebrew (`he`).
class AppL10nHe extends AppL10n {
  AppL10nHe([String locale = 'he']) : super(locale);

  @override
  String get homeTitle => 'ZT Camera';

  @override
  String get homeTagline =>
      'תמונות וסרטונים נשמרים בלי מיקום, תאריך או פרטי הטלפון.';

  @override
  String get backToCamera => 'חזרה למצלמה';

  @override
  String get accountSignIn => 'כניסה או יצירת חשבון';

  @override
  String get accountOptional =>
      'לא חובה: המצלמה עובדת גם בלי חשבון. חשבון יידרש לכספת הפרטית ולגיבוי המוצפן, שיגיעו בגרסה מאוחרת יותר.';

  @override
  String get accountManage => 'סיסמה, אבטחה ויציאה מהחשבון';

  @override
  String get privacySection => 'מה נשמר בתמונות';

  @override
  String get privacyLocation => 'מיקום';

  @override
  String get privacyLocationNever =>
      'לא נשמר אף פעם. לאפליקציה אין הרשאת מיקום.';

  @override
  String get privacyDateTime => 'תאריך ושעה';

  @override
  String get privacyModel => 'היצרן והדגם של הטלפון';

  @override
  String get privacyOffByDefault =>
      'לא נשמר, אלא אם מפעילים זאת בהגדרות המצלמה';

  @override
  String get privacyOtherApps => 'תמונות שמצולמות עבור אפליקציות אחרות';

  @override
  String get privacyOtherAppsNever => 'תמיד נשמרות בלי שום פרט נוסף';

  @override
  String get cameraScreensLicence =>
      'מסכי המצלמה התחילו מ-GrapheneOS Camera (רישיון MIT). ZT Camera לא נוצרה על ידי GrapheneOS ואינה מאושרת על ידה.';
}
