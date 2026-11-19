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
  );

  final String id;
  final bool isVideo;
  final DateTime takenAt;
  final int size;
  final int? width;
  final int? height;
  final Duration? duration;
  final bool imported;
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
}

final vaultBackendProvider = Provider<VaultBackend>(
  (ref) => const ChannelVaultBackend(),
);
