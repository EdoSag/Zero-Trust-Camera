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
      'לא חובה: המצלמה והכספת הפרטית עובדות בלי חשבון. חשבון נדרש רק לגיבוי המוצפן של הכספת.';

  @override
  String get accountManage => 'סיסמה, אבטחה ויציאה מהחשבון';

  @override
  String get privacySection => 'מה נשמר בתמונות';

  @override
  String get privacyLocation => 'מיקום';

  @override
  String get privacyLocationNever =>
      'לא נשמר אף פעם בתמונה או בסרטון. רק בתוך הכספת הפרטית, ורק אם מפעילים זאת שם.';

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
  String get homePanorama => 'פנורמה (בטא)';

  @override
  String get homePanoramaSubtitle =>
      'מסתובבים לאט והטלפון מחבר את הנוף לתמונה רחבה אחת';

  @override
  String get homeProCamera => 'מצלמה מקצועית';

  @override
  String get homeProCameraSubtitle =>
      'ISO, מהירות תריס, מיקוד ואיזון לבן ידניים, וצילומי RAW';

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

  @override
  String get vaultBackup => 'גיבוי מוצפן';

  @override
  String get backupTitle => 'גיבוי מוצפן';

  @override
  String get backupIntro =>
      'כל תמונה מוצפנת בטלפון הזה במפתח משלה לפני שהיא עוברת לחשבון Zero Trust שלך. השרת לא יכול לראות אותה. אפשר לשחזר אותה בטלפון חדש עם החשבון.';

  @override
  String get backupSignIn =>
      'כדי לגבות את הכספת, צריך להתחבר לחשבון Zero Trust במסך הבית.';

  @override
  String get backupOn => 'גיבוי הכספת';

  @override
  String backupCount(int done, int total) {
    return '$done מתוך $total גובו';
  }

  @override
  String get backupEverything => 'כל מה שבכספת';

  @override
  String get backupAlbums => 'רק האלבומים האלה';

  @override
  String get backupVideos => 'גם סרטונים';

  @override
  String get backupVideosSubtitle =>
      'סרטונים תופסים הרבה מקום. עד 2GB לכל סרטון; ארוכים יותר נשארים רק בטלפון הזה.';

  @override
  String get backupNow => 'גיבוי עכשיו';

  @override
  String get backupRestore => 'שחזור לטלפון הזה';

  @override
  String get backupRestoreSubtitle =>
      'מחזיר תמונות מהחשבון שלא נמצאות בטלפון הזה';

  @override
  String backupDone(int count) {
    return '$count גובו';
  }

  @override
  String backupRestored(int count) {
    return '$count שוחזרו';
  }

  @override
  String backupTooLarge(int count) {
    return '$count גדולים מדי, נשמרים רק בטלפון הזה';
  }

  @override
  String backupFailed(int count) {
    return '$count לא עברו. ננסה שוב בפעם הבאה.';
  }

  @override
  String get backupFull => 'האחסון בחשבון מלא. השאר יחכה.';

  @override
  String backupProgress(int done, int total) {
    return '$done מתוך $total';
  }

  @override
  String get backupWifiOnly => 'רק ב-Wi-Fi';

  @override
  String get backupWifiOnlySubtitle =>
      'הגיבוי האוטומטי מחכה ל-Wi-Fi ואינו משתמש בנתונים סלולריים';

  @override
  String get backupChargingOnly => 'רק בזמן טעינה';

  @override
  String get backupChargingOnlySubtitle =>
      'הגיבוי האוטומטי מחכה עד שהטלפון מחובר למטען';

  @override
  String get backupNowSubtitle => 'רץ מיד, בכל רשת';

  @override
  String get vaultSearch => 'חיפוש';

  @override
  String get vaultSearchClose => 'סגירת החיפוש';

  @override
  String get vaultSearchHint => 'אלבום, חודש, שנה או תאריך';

  @override
  String get vaultSearchDates => 'בחירת תאריכים';

  @override
  String get vaultSearchEmpty => 'אין בכספת פריטים תואמים';

  @override
  String get protectTitle => 'איך התמונות שלך מוגנות';

  @override
  String get protectSubtitle =>
      'מה מוצפן, מה יוצא מהטלפון, ועל מה האפליקציה לא יכולה להגן';

  @override
  String get protectGalleryTitle => 'תמונות בגלריה';

  @override
  String get protectGalleryBody =>
      'נשמרות רק עם מה שתמונה צריכה: גודל, סיבוב וצבע. בלי מיקום, תאריך, דגם הטלפון או תמונה ממוזערת נסתרת, אלא אם הפעלת תאריך או דגם בהגדרות המצלמה. אלה קבצים רגילים: כל אפליקציה שקיבלה גישה לתמונות יכולה לפתוח אותם.';

  @override
  String get protectVaultTitle => 'הכספת הפרטית';

  @override
  String get protectVaultBody =>
      'כל תמונה מוצפנת בזיכרון, עם מפתח משלה, לפני שהיא מגיעה לאחסון. המפתח הזה נעול למפתח הכספת של הטלפון, שנפתח רק אחרי טביעת אצבע, זיהוי פנים או נעילת המסך. ממסך הנעילה המצלמה יכולה להוסיף לכספת אבל לא לפתוח אותה. אם מפעילים מיקום עבור הכספת, המקום נשמר בפרטים המוצפנים של הפריט, אף פעם לא בתמונה עצמה.';

  @override
  String get protectVideoTitle => 'סרטונים פרטיים';

  @override
  String get protectVideoBody =>
      'סרטון מוקלט לקובץ שרק האפליקציה הזו יכולה לגשת אליו, מוצפן כשעוצרים, ואז הקובץ נדרס ונמחק. עד העצירה הוא מוגן על ידי הצפנת האחסון של אנדרואיד, לא על ידי הכספת. \"הקלטת סרטונים פרטיים בזיכרון\" בהגדרות המצלמה משאירה את ההקלטה מחוץ לאחסון; אורך הסרטון מוגבל אז לפי זיכרון הטלפון.';

  @override
  String get protectBackupTitle => 'גיבוי מוצפן';

  @override
  String get protectBackupBody =>
      'כבוי עד שמפעילים אותו. כל קובץ מוצפן בטלפון הזה עם מפתח משלו לפני שהוא נשלח לחשבון Zero Trust שלך. השרת רואה גדלים וזמנים, לעולם לא תמונות או שמות אלבומים. מי שיכול להיכנס לחשבון שלך ולפתוח אותו יכול לשחזר אותן, לכן יש לשמור היטב על הסיסמה.';

  @override
  String get protectNetworkTitle => 'רשת';

  @override
  String get protectNetworkBody =>
      'במסכי המצלמה אין קוד רשת. האפליקציה מתחברת לאינטרנט רק עבור החשבון והגיבוי, ורק אם משתמשים בהם.';

  @override
  String get protectPermissionsTitle => 'הרשאות';

  @override
  String get protectPermissionsBody =>
      'מצלמה. מיקרופון, לווידאו בלבד. התראות, להתקדמות הגיבוי ולשגיאות שמירה. מצב הרשת, כדי להבדיל בין Wi-Fi לנתונים סלולריים עבור הגיבוי. מיקום, שמתבקש רק אם מפעילים מיקום עבור הכספת הפרטית. בלי גישה לקבצים שלך.';

  @override
  String get protectLimitsTitle => 'על מה היא לא יכולה להגן';

  @override
  String get protectLimitsBody =>
      'טלפון פתוח כשהכספת פתוחה. תמונות ששומרים או משתפים מחוץ לכספת: הן יוצאות כקבצים רגילים, בלי מיקום, תאריך או פרטי הטלפון. טלפון שמישהו אחר מכיר את נעילת המסך שלו.';

  @override
  String get defaultCameraTitle => 'שימוש כמצלמה שלך';

  @override
  String get defaultCameraSubtitle =>
      'פתיחת ZT Camera מכפתור ההפעלה וממסך הנעילה';

  @override
  String get defaultCameraIntro =>
      'באנדרואיד אין מתג אחד למצלמת ברירת המחדל, ולכן נדרשים שני שלבים.';

  @override
  String get defaultCameraStep1 =>
      'לוחצים פעמיים על כפתור ההפעלה, או פותחים את המצלמה ממסך הנעילה. אם אנדרואיד שואל באיזו מצלמה להשתמש, בוחרים ZT Camera ומקישים על תמיד.';

  @override
  String get defaultCameraStep2 =>
      'אם מצלמה אחרת נפתחת בלי לשאול, פותחים את פרטי האפליקציה של אותה מצלמה, מקישים על פתיחה כברירת מחדל, מנקים את ברירות המחדל שלה, וחוזרים לשלב 1.';

  @override
  String get defaultCameraOpenSettings => 'פתיחת אפליקציות ברירת מחדל';

  @override
  String get defaultCameraOtherApps =>
      'בתוך אפליקציות אחרות, למשל כפתור המצלמה באפליקציית צ\'אט, אנדרואיד 11 ומעלה משתמש תמיד במצלמה שהגיעה עם הטלפון. אף אפליקציה לא יכולה לשנות זאת.';

  @override
  String get defaultCameraCardTitle => 'להפוך את ZT Camera למצלמה שלך?';

  @override
  String get defaultCameraCardBody =>
      'פתיחה מכפתור ההפעלה וממסך הנעילה במקום המצלמה של הטלפון.';

  @override
  String get defaultCameraShowMe => 'איך עושים את זה';

  @override
  String get defaultCameraNotNow => 'לא עכשיו';

  @override
  String get vaultMore => 'עוד';

  @override
  String get vaultQuickHide => 'נעילה כשהטלפון הפוך';

  @override
  String get vaultQuickHideOn =>
      'הכספת תינעל מעכשיו כשמניחים את הטלפון עם המסך כלפי מטה';

  @override
  String get vaultLocation => 'הוספת מיקום לתמונות בכספת';

  @override
  String get vaultLocationOn =>
      'תמונות חדשות בכספת הפרטית ישמרו את המיקום שלהן, רק בתוך הכספת. עותקים שנשמרים או משותפים לא נושאים אותו אף פעם.';

  @override
  String get vaultDisappearTitle => 'תמונות נעלמות';

  @override
  String get vaultDisappearBody =>
      'תמונות וסרטונים חדשים בכספת יימחקו לצמיתות מעצמם אחרי הזמן הזה. הם לא עוברים לאשפה ואף פעם לא מגובים. מה שכבר יש בכספת נשאר כמו שהוא.';

  @override
  String get vaultDisappearOff => 'כבוי';

  @override
  String get vaultDisappearHour => 'אחרי שעה';

  @override
  String get vaultDisappearDay => 'אחרי יום';

  @override
  String get vaultDisappearWeek => 'אחרי שבוע';

  @override
  String get vaultDisappearMonth => 'אחרי 30 יום';

  @override
  String get vaultDisappearOn =>
      'תמונות וסרטונים חדשים בכספת יימחקו מעכשיו מעצמם. שעון מסמן אותם.';

  @override
  String get vaultDisappearAfter => 'היעלמות אחרי…';

  @override
  String get vaultDisappearKeep => 'שמירה (עצירת השעון)';

  @override
  String get vaultDisappearBadge => 'נעלם';

  @override
  String get backupSpaceSaver => 'חיסכון במקום';

  @override
  String get backupSpaceSaverSubtitle =>
      'תמונות עולות כעותקים קטנים יותר. המקור נשאר בטלפון הזה, אבל שחזור יחזיר את העותקים הקטנים. סרטונים ותמונות RAW לא משתנים.';

  @override
  String get accountMediaTitle => 'בחשבון שלך';

  @override
  String get accountMediaIntro =>
      'גובו מטלפון אחר ואינם בטלפון הזה. מקישים על פריט כדי לפתוח אותו מהחשבון, או שומרים אותו בטלפון הזה.';

  @override
  String get accountMediaEmpty => 'כל מה שבחשבון שלך כבר נמצא בטלפון הזה.';

  @override
  String get accountMediaFailed =>
      'לא ניתן להגיע לחשבון. יש לבדוק את החיבור ולנסות שוב.';

  @override
  String get accountMediaKeep => 'שמירה בטלפון הזה';

  @override
  String get accountMediaKept => 'נשמר בכספת בטלפון הזה';

  @override
  String get accountMediaBusy =>
      'גיבוי או שחזור פועל כעת. יש לנסות שוב כשיסתיים.';

  @override
  String get accountMediaVideoNote =>
      'כדי לנגן את הסרטון יש לשמור אותו בטלפון הזה.';

  @override
  String get accountMediaRawNote =>
      'כדי לפתוח את תמונת ה-RAW יש לשמור אותה בטלפון הזה.';

  @override
  String get offlineBuildTitle => 'גרסה לא מקוונת';

  @override
  String get offlineBuildBody =>
      'לגרסה הזו אין הרשאת אינטרנט, ולכן אין חשבון ואין גיבוי. הכול נשאר בטלפון הזה.';

  @override
  String get protectNetworkOffline =>
      'זו הגרסה הלא מקוונת: לאפליקציה אין הרשאת אינטרנט כלל. שום דבר לא יכול לצאת מהטלפון דרכה.';
}
