import 'dart:math';
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:zero_trust_core/zero_trust_core.dart';

import '../app/auth_providers.dart';
import 'vault_backend.dart';
import 'vault_library.dart';

/// A vault photo or video kept in the Zero Trust account. The file itself is
/// an encrypted attachment whose key lives only here, inside the item (which
/// is encrypted too): the server sees sizes and times, nothing else.
///
/// The same kind can be read by a future ZT Photos app, so nothing needs
/// moving later.
class CameraMedia {
  const CameraMedia({
    required this.id,
    required this.type,
    required this.mime,
    required this.takenAt,
    required this.size,
    required this.file,
    this.favourite = false,
    this.albums = const [],
  });

  final String id;

  /// "photo" or "video".
  final String type;
  final String mime;
  final DateTime takenAt;
  final int size;
  final ZtAttachmentRef file;
  final bool favourite;

  /// Ids of [CameraAlbum]s this item is in.
  final List<String> albums;

  bool get isVideo => type == 'video';

  CameraMedia copyWith({bool? favourite, List<String>? albums}) => CameraMedia(
    id: id,
    type: type,
    mime: mime,
    takenAt: takenAt,
    size: size,
    file: file,
    favourite: favourite ?? this.favourite,
    albums: albums ?? this.albums,
  );

  Map<String, Object?> toJson() => {
    'id': id,
    'type': type,
    'mime': mime,
    'takenAt': takenAt.millisecondsSinceEpoch,
    'size': size,
    'file': file.toJson(),
    'favourite': favourite,
    'albums': albums,
  };

  static CameraMedia fromJson(String _, Map<String, Object?> j, DateTime _) =>
      CameraMedia(
        id: j['id']! as String,
        type: j['type'] as String? ?? 'photo',
        mime: j['mime'] as String? ?? 'image/jpeg',
        takenAt: DateTime.fromMillisecondsSinceEpoch(
          (j['takenAt'] as num?)?.toInt() ?? 0,
        ),
        size: (j['size'] as num?)?.toInt() ?? 0,
        file: ZtAttachmentRef.fromJson(
          (j['file']! as Map).cast<String, Object?>(),
        ),
        favourite: j['favourite'] == true,
        albums: [
          for (final a in (j['albums'] as List<Object?>? ?? const []))
            if (a is String) a,
        ],
      );
}

/// A vault album, kept in the account so a restore can put items back in it.
class CameraAlbum {
  const CameraAlbum({required this.id, required this.name});

  final String id;
  final String name;

  Map<String, Object?> toJson() => {'id': id, 'name': name};

  static CameraAlbum fromJson(String _, Map<String, Object?> j, DateTime _) =>
      CameraAlbum(id: j['id']! as String, name: j['name'] as String? ?? '');
}

final cameraMediaCollection = ZtCollection<CameraMedia>(
  kind: 'camera_media',
  fromJson: CameraMedia.fromJson,
  toJson: (m) => m.toJson(),
  idOf: (m) => m.id,
);

final cameraAlbumCollection = ZtCollection<CameraAlbum>(
  kind: 'camera_album',
  fromJson: CameraAlbum.fromJson,
  toJson: (a) => a.toJson(),
  idOf: (a) => a.id,
);

/// Where backed-up items go: the account's synced items and encrypted
/// attachments. Tests use a fake.
abstract class BackupStore {
  Future<List<CameraMedia>> media();
  Future<List<CameraAlbum>> albums();
  Future<void> saveMedia(CameraMedia m);
  Future<void> saveAlbum(CameraAlbum a);
  Future<void> deleteMedia(CameraMedia m);
  Future<ZtAttachmentRef> upload(Uint8List bytes, String mime);
  Future<Uint8List> download(ZtAttachmentRef ref);
}

/// [BackupStore] on the core: synced items plus [ZtAttachments]. Needs the
/// account signed in and its vault unlocked.
class CoreBackupStore implements BackupStore {
  CoreBackupStore(this._ref);

  final Ref _ref;

  Future<LocalItemStore> get _store => _ref.read(ztLocalStoreProvider.future);
  SyncController get _sync => _ref.read(ztSyncControllerProvider.notifier);
  Future<ZtAttachments> get _files => _ref.read(ztAttachmentsProvider.future);

  @override
  Future<List<CameraMedia>> media() async =>
      cameraMediaCollection.list(await _store);

  @override
  Future<List<CameraAlbum>> albums() async =>
      cameraAlbumCollection.list(await _store);

  @override
  Future<void> saveMedia(CameraMedia m) async =>
      cameraMediaCollection.save(_sync, await _store, m);

  @override
  Future<void> saveAlbum(CameraAlbum a) async =>
      cameraAlbumCollection.save(_sync, await _store, a);

  @override
  Future<void> deleteMedia(CameraMedia m) async {
    await (await _files).delete(m.file);
    await cameraMediaCollection.delete(_sync, m.id);
  }

  @override
  Future<ZtAttachmentRef> upload(Uint8List bytes, String mime) async =>
      (await _files).put(bytes, mimeType: mime);

  @override
  Future<Uint8List> download(ZtAttachmentRef ref) async =>
      (await _files).read(ref);
}

final backupStoreProvider = Provider<BackupStore>(CoreBackupStore.new);

class BackupResult {
  const BackupResult({
    this.done = 0,
    this.tooLarge = 0,
    this.failed = 0,
    this.full = false,
  });

  /// Items newly backed up (or restored).
  final int done;

  /// Videos over the attachment size limit, left on the phone only.
  final int tooLarge;
  final int failed;

  /// The account's storage is full; the rest waits.
  final bool full;
}

/// Backs the Private vault up to the account, and brings it back on a new
/// phone. Every file is encrypted on the phone with its own key before it
/// leaves.
class VaultBackup {
  VaultBackup({
    required this.vault,
    required this.store,
    this.maxBytes = ZtAttachments.defaultMaxBytes,
  });

  final VaultBackend vault;
  final BackupStore store;
  final int maxBytes;

  /// The items [library]'s settings say to back up.
  List<VaultItem> eligible(VaultLibrary library, List<VaultItem> items) {
    final s = library.backup;
    final inAlbums = {for (final id in s.albums) ...?library.albums[id]?.items};
    return [
      for (final i in items)
        if (!library.isTrashed(i.id) &&
            (s.everything || inAlbums.contains(i.id)) &&
            (!i.isVideo || s.videos))
          i,
    ];
  }

  /// Uploads what isn't in the account yet, and keeps favourites and albums
  /// up to date for what is. Changes [library]; the caller saves it.
  Future<BackupResult> backUp(
    VaultLibrary library,
    List<VaultItem> items, {
    void Function(int done, int total)? progress,
  }) async {
    if (!library.backup.on) return const BackupResult();
    await deletePending(library);
    final todo = eligible(library, items);
    final remote = {for (final m in await store.media()) m.id: m};
    // Albums first, so a restore elsewhere finds them.
    final knownAlbums = {for (final a in await store.albums()) a.id: a.name};
    for (final a in library.albums.values) {
      if (knownAlbums[a.id] != a.name) {
        await store.saveAlbum(CameraAlbum(id: a.id, name: a.name));
      }
    }
    var done = 0, tooLarge = 0, failed = 0, step = 0;
    for (final item in todo) {
      progress?.call(step++, todo.length);
      final favourite = library.favourites.contains(item.id);
      final albums = library.albumsOf(item.id);
      final mediaId = library.backedUp[item.id];
      final existing = mediaId == null ? null : remote[mediaId];
      if (existing != null) {
        if (existing.favourite != favourite ||
            !_sameList(existing.albums, albums)) {
          await store.saveMedia(
            existing.copyWith(favourite: favourite, albums: albums),
          );
        }
        continue;
      }
      if (item.size > maxBytes) {
        tooLarge++;
        continue;
      }
      try {
        final original = await vault.original(item.id);
        if (original.bytes.length > maxBytes) {
          tooLarge++;
          continue;
        }
        final ref = await store.upload(original.bytes, original.mime);
        final media = CameraMedia(
          id: newBackupId(),
          type: item.isVideo ? 'video' : 'photo',
          mime: original.mime,
          takenAt: item.takenAt,
          size: original.bytes.length,
          file: ref,
          favourite: favourite,
          albums: albums,
        );
        await store.saveMedia(media);
        library.backedUp[item.id] = media.id;
        done++;
      } on AttachmentTooLarge {
        tooLarge++;
      } on Object catch (e) {
        if (_isQuota(e)) {
          return BackupResult(
            done: done,
            tooLarge: tooLarge,
            failed: failed,
            full: true,
          );
        }
        failed++;
      }
    }
    progress?.call(todo.length, todo.length);
    return BackupResult(done: done, tooLarge: tooLarge, failed: failed);
  }

  /// Seals every backed-up item this phone doesn't have into the vault,
  /// with its favourite mark and albums. Changes [library]; the caller saves
  /// it.
  Future<BackupResult> restore(
    VaultLibrary library, {
    void Function(int done, int total)? progress,
  }) async {
    final here = {...library.backedUp.values, ...library.backupDeletes};
    final missing = [
      for (final m in await store.media())
        if (!here.contains(m.id)) m,
    ];
    final albumNames = {for (final a in await store.albums()) a.id: a.name};
    var done = 0, failed = 0, step = 0;
    for (final m in missing) {
      progress?.call(step++, missing.length);
      try {
        final bytes = await store.download(m.file);
        final id = await vault.restore(
          bytes,
          mime: m.mime,
          isVideo: m.isVideo,
          takenAt: m.takenAt,
        );
        library.backedUp[id] = m.id;
        if (m.favourite) library.favourites.add(id);
        for (final albumId in m.albums) {
          if (!library.albums.containsKey(albumId)) {
            library.addAlbum(albumNames[albumId] ?? '', albumId);
          }
          library.addToAlbum(albumId, [id]);
        }
        done++;
      } on Object {
        failed++;
      }
    }
    progress?.call(missing.length, missing.length);
    return BackupResult(done: done, failed: failed);
  }

  /// Deletes from the account the copies of items deleted for good on this
  /// phone (queued by [VaultLibrary.forget]). Ones that fail stay queued.
  Future<void> deletePending(VaultLibrary library) async {
    if (library.backupDeletes.isEmpty) return;
    final remote = {for (final m in await store.media()) m.id: m};
    for (final mediaId in library.backupDeletes.toList()) {
      final m = remote[mediaId];
      try {
        if (m != null) await store.deleteMedia(m);
        library.backupDeletes.remove(mediaId);
      } on Object {
        // Tried again next time.
      }
    }
  }

  static bool _sameList(List<String> a, List<String> b) =>
      a.length == b.length && a.toSet().containsAll(b);

  static bool _isQuota(Object e) {
    final s = e.toString().toLowerCase();
    return s.contains('quota') ||
        s.contains('storage limit') ||
        s.contains('payload too large');
  }

  static final _random = Random.secure();

  static String newBackupId() => List.generate(
    16,
    (_) => _random.nextInt(256).toRadixString(16).padLeft(2, '0'),
  ).join();
}

/// Whether the account is ready for backup: signed in, and its own vault
/// (the account key) unlocked.
final backupReadyProvider = Provider<bool>(
  (ref) =>
      ref.watch(currentUserProvider) != null &&
      ref.watch(vaultControllerProvider) is VaultUnlocked,
);
