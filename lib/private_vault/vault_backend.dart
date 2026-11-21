import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// One photo or video in the Private vault. Its file stays sealed on the
/// phone; these are only its details (opened by the native side).
class VaultItem {
  const VaultItem({
    required this.id,
    required this.isVideo,
    required this.takenAt,
    this.size = 0,
    this.width,
    this.height,
    this.duration,
    this.imported = false,
    this.lat,
    this.lon,
  });

  factory VaultItem.fromMap(Map<Object?, Object?> m) => VaultItem(
    id: m['id']! as String,
    isVideo: m['type'] == 'video',
    takenAt: DateTime.fromMillisecondsSinceEpoch(
      (m['takenAt'] as num?)?.toInt() ?? 0,
    ),
    size: (m['size'] as num?)?.toInt() ?? 0,
    width: (m['width'] as num?)?.toInt(),
    height: (m['height'] as num?)?.toInt(),
    duration: (m['durationMs'] as num?) == null
        ? null
        : Duration(milliseconds: (m['durationMs']! as num).toInt()),
    imported: m['imported'] == true,
    lat: (m['lat'] as num?)?.toDouble(),
    lon: (m['lon'] as num?)?.toDouble(),
  );

  final String id;
  final bool isVideo;
  final DateTime takenAt;
  final int size;
  final int? width;
  final int? height;
  final Duration? duration;
  final bool imported;

  /// Where it was taken, when "Add location" was on. Kept only in the
  /// vault's sealed details, never in the picture.
  final double? lat;
  final double? lon;

  bool get hasPlace => lat != null && lon != null;
}

class VaultStatus {
  const VaultStatus({
    required this.setUp,
    required this.unlocked,
    required this.privateMode,
    required this.screenLock,
  });

  final bool setUp;
  final bool unlocked;

  /// New photos and videos go into the vault (not the gallery).
  final bool privateMode;

  /// The phone has a screen lock, which the vault needs.
  final bool screenLock;
}

/// How setting up or unlocking went.
enum VaultAuth { ok, cancelled, noScreenLock, keyLost, failed }

/// Thrown when the vault locked itself (the app was left) during a call.
/// One scanned code kept in the vault (scan history).
class ScanRecord {
  const ScanRecord({required this.id, required this.text, required this.at});

  final String id;
  final String text;
  final DateTime at;

  factory ScanRecord.fromMap(Map<Object?, Object?> m) => ScanRecord(
    id: m['id']! as String,
    text: m['text']! as String,
    at: DateTime.fromMillisecondsSinceEpoch((m['at'] as num?)?.toInt() ?? 0),
  );
}

class VaultLockedException implements Exception {
  const VaultLockedException();
}

/// The Private vault, which lives in native code (VaultChannel.kt): keys,
/// sealed files, the photo picker, sharing and the video player. Tests use
/// their own.
abstract class VaultBackend {
  Future<VaultStatus> status();
  Future<VaultAuth> setUp();
  Future<VaultAuth> unlock();
  Future<void> lock();
  Future<void> setPrivateMode(bool on);

  /// No screenshots and a blank preview in Recents while the vault shows.
  Future<void> setSecure(bool on);
  Future<List<VaultItem>> list();
  Future<Uint8List?> thumb(String id);
  Future<Uint8List> photo(String id);
  Future<String?> readState();
  Future<void> writeState(String json);
  Future<void> delete(List<String> ids);

  /// Saves cleaned copies (no Exif, no recording time) into the gallery.
  /// Returns how many were saved.
  Future<int> saveToGallery(List<String> ids);

  /// Opens the share sheet with cleaned copies.
  Future<void> share(List<String> ids);

  /// Opens the photo picker and seals what was picked. Returns how many.
  Future<int> importMedia();
  Future<void> playVideo(String id);

  /// Scan history: whether scanned codes are kept, and the kept ones.
  Future<bool> scanHistory();
  Future<void> setScanHistory(bool on);
  Future<List<ScanRecord>> scans();
  Future<void> deleteScans(List<String> ids);

  /// The text in a photo, read on the phone; null when there is none.
  Future<String?> readText(String id);

  /// Opens the photo editor; the edited copy is saved into the vault as a new photo.
  Future<void> editPhoto(String id);

  /// The item's file exactly as sealed, for the encrypted backup.
  Future<VaultOriginal> original(String id);

  /// A smaller copy of a photo, for the backup's space saver. Null for
  /// videos and RAW photos, and when a copy wouldn't be smaller.
  Future<VaultOriginal?> spaceSaver(String id);

  /// [length] bytes of a video's file from [offset], for backing up a video
  /// too large to hold in memory or send as one file.
  Future<Uint8List> originalPart(String id, int offset, int length);

  /// Adds the next part of a large video being brought back from the
  /// backup. [token] names the video until [restoreFinish].
  Future<void> restorePart(String token, Uint8List bytes);

  /// Seals the video whose parts were given to [restorePart]; returns its
  /// new id.
  Future<String> restoreFinish(
    String token,
    DateTime takenAt, {
    double? lat,
    double? lon,
  });

  /// Wipes the parts of a restore that didn't finish.
  Future<void> restoreCancel(String token);

  /// Seals a file brought back from the backup; returns its new id.
  Future<String> restore(
    Uint8List bytes, {
    required String mime,
    required bool isVideo,
    required DateTime takenAt,
    double? lat,
    double? lon,
  });

  /// Whether new vault items keep their place, and whether the phone's
  /// location permission was given.
  Future<VaultLocationState> vaultLocation();

  /// Turns it on or off. Turning it on asks for the location permission.
  Future<void> setVaultLocation(bool on);

  /// Quick hide: whether the open vault locks when the phone is put face
  /// down.
  Future<bool> quickHide();
  Future<void> setQuickHide(bool on);

  /// Calls [listener] when the native side locked the vault by itself
  /// (quick hide). Null stops it.
  void onLocked(void Function()? listener);

  /// Whether the phone is on an unmetered network and charging.
  Future<BackupConditions> backupConditions();

  /// Shows (or updates) the backup's progress notification. Asks for the
  /// notification permission once when [ask] is set; without it nothing shows.
  Future<void> backupNotice(
    String title,
    String text,
    int done,
    int total, {
    bool ask = false,
  });

  /// Takes the progress notification away.
  Future<void> backupNoticeEnd();
}

class VaultLocationState {
  const VaultLocationState({required this.on, required this.granted});

  final bool on;
  final bool granted;
}

/// What the phone is connected to right now, for the backup's rules.
class BackupConditions {
  const BackupConditions({required this.unmetered, required this.charging});

  /// On Wi-Fi or another network that isn't metered.
  final bool unmetered;
  final bool charging;
}

class VaultOriginal {
  const VaultOriginal(this.bytes, this.mime);

  final Uint8List bytes;
  final String mime;
}

class ChannelVaultBackend implements VaultBackend {
  const ChannelVaultBackend();

  static const _channel = MethodChannel('dev.sagron.zerotrustcamera/vault');

  Future<T?> _call<T>(String method, [Map<String, Object?>? args]) async {
    try {
      return await _channel.invokeMethod<T>(method, args);
    } on PlatformException catch (e) {
      if (e.code == 'locked') throw const VaultLockedException();
      rethrow;
    }
  }

  VaultAuth _auth(String? s) => switch (s) {
    'ok' => VaultAuth.ok,
    'cancelled' => VaultAuth.cancelled,
    'noScreenLock' => VaultAuth.noScreenLock,
    'keyLost' => VaultAuth.keyLost,
    _ => VaultAuth.failed,
  };

  @override
  Future<VaultStatus> status() async {
    try {
      final m = await _channel.invokeMapMethod<String, Object?>('status');
      return VaultStatus(
        setUp: m?['setUp'] == true,
        unlocked: m?['unlocked'] == true,
        privateMode: m?['privateMode'] == true,
        screenLock: m?['screenLock'] == true,
      );
    } on MissingPluginException {
      return const VaultStatus(
        setUp: false,
        unlocked: false,
        privateMode: false,
        screenLock: false,
      );
    }
  }

  @override
  Future<VaultAuth> setUp() async => _auth(await _call<String>('setUp'));

  @override
  Future<VaultAuth> unlock() async => _auth(await _call<String>('unlock'));

  @override
  Future<void> lock() => _call<void>('lock');

  @override
  Future<void> setPrivateMode(bool on) =>
      _call<void>('setPrivateMode', {'on': on});

  @override
  Future<void> setSecure(bool on) async {
    try {
      await _channel.invokeMethod<void>('setSecure', {'on': on});
    } on MissingPluginException {
      // Not on Android.
    }
  }

  @override
  Future<List<VaultItem>> list() async {
    final raw = await _call<List<Object?>>('list') ?? const [];
    return [
      for (final m in raw) VaultItem.fromMap(m! as Map<Object?, Object?>),
    ];
  }

  @override
  Future<Uint8List?> thumb(String id) => _call<Uint8List>('thumb', {'id': id});

  @override
  Future<Uint8List> photo(String id) async =>
      (await _call<Uint8List>('photo', {'id': id}))!;

  @override
  Future<String?> readState() => _call<String>('readState');

  @override
  Future<void> writeState(String json) =>
      _call<void>('writeState', {'json': json});

  @override
  Future<void> delete(List<String> ids) => _call<void>('delete', {'ids': ids});

  @override
  Future<int> saveToGallery(List<String> ids) async =>
      await _call<int>('saveToGallery', {'ids': ids}) ?? 0;

  @override
  Future<void> share(List<String> ids) => _call<int>('share', {'ids': ids});

  @override
  Future<int> importMedia() async => await _call<int>('import') ?? 0;

  @override
  Future<void> playVideo(String id) => _call<void>('playVideo', {'id': id});

  @override
  Future<bool> scanHistory() async => await _call<bool>('scanHistory') ?? false;

  @override
  Future<void> setScanHistory(bool on) =>
      _call<void>('setScanHistory', {'on': on});

  @override
  Future<List<ScanRecord>> scans() async {
    final raw = await _call<List<Object?>>('scans') ?? const [];
    return [
      for (final m in raw.cast<Map<Object?, Object?>>()) ScanRecord.fromMap(m),
    ];
  }

  @override
  Future<void> deleteScans(List<String> ids) =>
      _call<void>('deleteScans', {'ids': ids});

  @override
  Future<String?> readText(String id) => _call<String>('readText', {'id': id});

  @override
  Future<void> editPhoto(String id) => _call<void>('editPhoto', {'id': id});

  @override
  Future<VaultOriginal> original(String id) async {
    final m = (await _call<Map<Object?, Object?>>('original', {'id': id}))!;
    return VaultOriginal(m['bytes']! as Uint8List, m['mime']! as String);
  }

  @override
  Future<VaultOriginal?> spaceSaver(String id) async {
    final m = await _call<Map<Object?, Object?>>('spaceSaver', {'id': id});
    return m == null
        ? null
        : VaultOriginal(m['bytes']! as Uint8List, m['mime']! as String);
  }

  @override
  Future<Uint8List> originalPart(String id, int offset, int length) async =>
      (await _call<Uint8List>('originalPart', {
        'id': id,
        'offset': offset,
        'length': length,
      }))!;

  @override
  Future<void> restorePart(String token, Uint8List bytes) =>
      _call<void>('restorePart', {'token': token, 'bytes': bytes});

  @override
  Future<String> restoreFinish(
    String token,
    DateTime takenAt, {
    double? lat,
    double? lon,
  }) async => (await _call<String>('restoreFinish', {
    'token': token,
    'takenAt': takenAt.millisecondsSinceEpoch,
    'lat': lat,
    'lon': lon,
  }))!;

  @override
  Future<void> restoreCancel(String token) =>
      _call<void>('restoreCancel', {'token': token});

  @override
  Future<String> restore(
    Uint8List bytes, {
    required String mime,
    required bool isVideo,
    required DateTime takenAt,
    double? lat,
    double? lon,
  }) async => (await _call<String>('restore', {
    'bytes': bytes,
    'mime': mime,
    'video': isVideo,
    'takenAt': takenAt.millisecondsSinceEpoch,
    'lat': lat,
    'lon': lon,
  }))!;

  @override
  Future<VaultLocationState> vaultLocation() async {
    try {
      final m = await _channel.invokeMapMethod<String, Object?>(
        'vaultLocation',
      );
      return VaultLocationState(
        on: m?['on'] == true,
        granted: m?['granted'] == true,
      );
    } on MissingPluginException {
      return const VaultLocationState(on: false, granted: false);
    }
  }

  @override
  Future<void> setVaultLocation(bool on) =>
      _call<void>('setVaultLocation', {'on': on});

  @override
  Future<bool> quickHide() async {
    try {
      return await _channel.invokeMethod<bool>('quickHide') ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  @override
  Future<void> setQuickHide(bool on) => _call<void>('setQuickHide', {'on': on});

  @override
  void onLocked(void Function()? listener) {
    _channel.setMethodCallHandler(
      listener == null
          ? null
          : (call) async {
              if (call.method == 'locked') listener();
            },
    );
  }

  @override
  Future<BackupConditions> backupConditions() async {
    try {
      final m = await _channel.invokeMapMethod<String, Object?>(
        'backupConditions',
      );
      return BackupConditions(
        unmetered: m?['unmetered'] == true,
        charging: m?['charging'] == true,
      );
    } on Object {
      // Unknown counts as mobile data and on battery: the backup waits.
      return const BackupConditions(unmetered: false, charging: false);
    }
  }

  @override
  Future<void> backupNotice(
    String title,
    String text,
    int done,
    int total, {
    bool ask = false,
  }) async {
    try {
      await _channel.invokeMethod<void>('backupNotice', {
        'title': title,
        'text': text,
        'done': done,
        'total': total,
        'ask': ask,
      });
    } on Object {
      // The backup goes on without its notification.
    }
  }

  @override
  Future<void> backupNoticeEnd() async {
    try {
      await _channel.invokeMethod<void>('backupNoticeEnd');
    } on Object {
      // Nothing was showing.
    }
  }
}

final vaultBackendProvider = Provider<VaultBackend>(
  (ref) => const ChannelVaultBackend(),
);
