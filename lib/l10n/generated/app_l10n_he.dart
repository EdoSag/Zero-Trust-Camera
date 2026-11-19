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
      'לא חובה: המצלמה והכספת הפרטית עובדות בלי חשבון. חשבון יידרש לגיבוי מוצפן, שיגיע בגרסה מאוחרת יותר.';

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

  @override
  String get homeVault => 'כספת פרטית';

  @override
  String get homeVaultSubtitle => 'תמונות וסרטונים מוצפנים, רק באפליקציה הזו';

  @override
  String get vaultTitle => 'כספת פרטית';

  @override
  String get vaultLocked => 'הכספת נעולה';

  @override
  String get vaultLockedBody =>
      'התמונות והסרטונים כאן מוצפנים. פותחים אותם בטביעת אצבע, בפנים או בנעילת המסך.';

  @override
  String get vaultUnlock => 'פתיחה';

  @override
  String get vaultLock => 'נעילה';

  @override
  String get vaultSetUpTitle => 'שמירת תמונות בכספת פרטית';

  @override
  String get vaultSetUpBody =>
      'תמונות וסרטונים חדשים מוצפנים בטלפון ונפתחים רק כאן, אחרי טביעת אצבע, פנים או נעילת מסך. הגלריה ואפליקציות אחרות לא רואות אותם. לא צריך חשבון.\n\nאם תסירו את נעילת המסך של הטלפון, לא ניתן יהיה לפתוח את הכספת שוב.';

  @override
  String get vaultSetUp => 'הגדרת הכספת';

  @override
  String get vaultNeedsScreenLock =>
      'קודם צריך להגדיר נעילת מסך בטלפון. הכספת נפתחת איתה.';

  @override
  String get vaultKeyLost =>
      'המפתח של הכספת נמחק כי נעילת המסך של הטלפון הוסרה, ולכן אי אפשר לפתוח את התמונות שבה.';

  @override
  String get vaultFailed => 'זה לא הצליח. נסו שוב.';

  @override
  String get vaultAll => 'הכול';

  @override
  String get vaultFavourites => 'מועדפים';

  @override
  String get vaultAlbums => 'אלבומים';

  @override
  String get vaultTrash => 'אשפה';

  @override
  String get vaultEmpty =>
      'עדיין אין כאן כלום. תמונות וסרטונים שצולמו במצב פרטי, ומה שייובא, יופיעו כאן.';

  @override
  String get vaultTrashEmpty => 'האשפה ריקה';

  @override
  String get vaultTrashNote => 'פריטים באשפה נמחקים לתמיד אחרי 30 יום.';

  @override
  String get vaultImport => 'ייבוא מהגלריה';

  @override
  String vaultImported(int count) {
    return 'נוספו $count. המקור עדיין בגלריה: אפשר למחוק אותו שם.';
  }

  @override
  String get vaultPrivateMode => 'מצב פרטי לתמונות חדשות';

  @override
  String get vaultPrivateModeOn => 'תמונות וסרטונים חדשים נשמרים כאן';

  @override
  String get vaultPrivateModeOff => 'תמונות וסרטונים חדשים נשמרים בגלריה';

  @override
  String get vaultFavourite => 'מועדף';

  @override
  String get vaultAddToAlbum => 'הוספה לאלבום';

  @override
  String get vaultNewAlbum => 'אלבום חדש';

  @override
  String get vaultAlbumName => 'שם האלבום';

  @override
  String get vaultCreate => 'יצירה';

  @override
  String get vaultCancel => 'ביטול';

  @override
  String get vaultDelete => 'מחיקה';

  @override
  String get vaultSaveToGallery => 'שמירה בגלריה';

  @override
  String vaultSaved(int count) {
    return 'נשמרו $count בגלריה, בלי מיקום, תאריך או פרטי טלפון';
  }

  @override
  String get vaultShare => 'שיתוף בלי פרטים';

  @override
  String get vaultMoveToTrash => 'העברה לאשפה';

  @override
  String get vaultRestore => 'שחזור';

  @override
  String get vaultDeleteForever => 'מחיקה לתמיד';

  @override
  String vaultDeleteForeverConfirm(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count פריטים',
      one: 'את הפריט הזה',
    );
    return 'למחוק $_temp0 לתמיד? אי אפשר לבטל.';
  }

  @override
  String vaultSelected(int count) {
    return '$count נבחרו';
  }

  @override
  String get vaultRemoveFromAlbum => 'הסרה מהאלבום';

  @override
  String get vaultDeleteAlbum => 'מחיקת האלבום';

  @override
  String vaultAlbumCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count פריטים',
      one: 'פריט אחד',
    );
    return '$_temp0';
  }

  @override
  String get vaultNoAlbums => 'עדיין אין אלבומים';

  @override
  String get vaultPlay => 'הפעלה';

  @override
  String get scanHistoryTitle => 'היסטוריית סריקות';

  @override
  String get scanHistoryKeep => 'שמירת היסטוריית סריקות';

  @override
  String get scanHistoryKeepHint =>
      'קודים שסורקים נשמרים כאן, בכספת הפרטית, שרק לך יש גישה אליה.';

  @override
  String get scanHistoryEmpty => 'עדיין אין סריקות שמורות.';

  @override
  String get scanHistoryDelete => 'מחיקה';

  @override
  String get scanHistoryClear => 'מחיקת הכול';

  @override
  String get scanHistoryClearTitle => 'למחוק את היסטוריית הסריקות?';

  @override
  String scanHistoryClearMessage(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count סריקות יימחקו.',
      one: 'סריקה אחת תימחק.',
    );
    return '$_temp0';
  }

  @override
  String get scanHistoryCopied => 'הועתק';

  @override
  String get homeScanDocument => 'סריקת מסמך';

  @override
  String get homeScanDocumentSubtitle =>
      'דפים ישרים ונקיים כ-PDF, והטקסט נקרא בטלפון עצמו';

  @override
  String get vaultCopyText => 'העתקת טקסט';

  @override
  String get vaultEdit => 'עריכת עותק';

  @override
  String get vaultNoText => 'לא נמצא טקסט בתמונה';

  @override
  String get vaultTextTitle => 'טקסט';

  @override
  String get vaultCopy => 'העתקה';
}
