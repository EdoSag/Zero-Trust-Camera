import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:zero_trust_core/zero_trust_core.dart';

import '../app/auth_providers.dart';
import '../app/camera_config.dart';
import '../l10n/l10n.dart';
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
    this.parts = const [],
    this.favourite = false,
    this.albums = const [],
    this.lat,
    this.lon,
    this.saver = false,
    this.thumb,
  });

  final String id;

  /// "photo" or "video".
  final String type;
  final String mime;
  final DateTime takenAt;
  final int size;
  final ZtAttachmentRef file;

  /// A video over the attachment size limit is kept as several files, in
  /// order; [file] is then the first of them. Empty for everything else.
  final List<ZtAttachmentRef> parts;
  final bool favourite;

  /// Ids of [CameraAlbum]s this item is in.
  final List<String> albums;

  /// Where it was taken, if the owner chose to keep places in the vault.
  /// Inside the encrypted item, like everything else here.
  final double? lat;
  final double? lon;

  /// The backed-up file is a smaller copy (space saver), not the original.
  final bool saver;

  /// A small preview, encrypted like the file, so another phone can show
  /// what is in the account without downloading everything. Null for items
  /// backed up before previews existed.
  final ZtAttachmentRef? thumb;

  bool get isVideo => type == 'video';

  /// Every file this item is kept in.
  List<ZtAttachmentRef> get files => parts.isEmpty ? [file] : parts;

  CameraMedia copyWith({bool? favourite, List<String>? albums}) => CameraMedia(
    id: id,
    type: type,
    mime: mime,
    takenAt: takenAt,
    size: size,
    file: file,
    parts: parts,
    favourite: favourite ?? this.favourite,
    albums: albums ?? this.albums,
    lat: lat,
    lon: lon,
    saver: saver,
    thumb: thumb,
  );

  Map<String, Object?> toJson() => {
    'id': id,
    'type': type,
    'mime': mime,
    'takenAt': takenAt.millisecondsSinceEpoch,
    'size': size,
    'file': file.toJson(),
    if (parts.isNotEmpty) 'parts': [for (final p in parts) p.toJson()],
    'favourite': favourite,
    'albums': albums,
    if (lat != null && lon != null) ...{'lat': lat, 'lon': lon},
    if (saver) 'saver': true,
    if (thumb != null) 'thumb': thumb!.toJson(),
  };

  static CameraMedia fromJson(
    String _,
    Map<String, Object?> j,
    DateTime _,
  ) => CameraMedia(
    id: j['id']! as String,
    type: j['type'] as String? ?? 'photo',
    mime: j['mime'] as String? ?? 'image/jpeg',
    takenAt: DateTime.fromMillisecondsSinceEpoch(
      (j['takenAt'] as num?)?.toInt() ?? 0,
    ),
    size: (j['size'] as num?)?.toInt() ?? 0,
    file: ZtAttachmentRef.fromJson((j['file']! as Map).cast<String, Object?>()),
    parts: [
      for (final p in (j['parts'] as List<Object?>? ?? const []))
        if (p is Map) ZtAttachmentRef.fromJson(p.cast<String, Object?>()),
    ],
    favourite: j['favourite'] == true,
    albums: [
      for (final a in (j['albums'] as List<Object?>? ?? const []))
        if (a is String) a,
    ],
    lat: (j['lat'] as num?)?.toDouble(),
    lon: (j['lon'] as num?)?.toDouble(),
    saver: j['saver'] == true,
    thumb: j['thumb'] is Map
        ? ZtAttachmentRef.fromJson((j['thumb']! as Map).cast<String, Object?>())
        : null,
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

  /// Deletes an uploaded file that no item ended up using.
  Future<void> discard(ZtAttachmentRef ref);
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
    final files = await _files;
    for (final f in [...m.files, ?m.thumb]) {
      await files.delete(f);
    }
    await cameraMediaCollection.delete(_sync, m.id);
  }

  @override
  Future<void> discard(ZtAttachmentRef ref) async => (await _files).delete(ref);

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

  /// Files too large to back up, left on the phone only.
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
    required this.account,
    this.maxBytes = ZtAttachments.defaultMaxBytes,
  });

  final VaultBackend vault;
  final BackupStore store;

  /// The signed-in user's id: where [store] reads and writes.
  final String account;

  /// The most one file in the account can hold. A larger video goes up as
  /// several files of this size.
  final int maxBytes;

  /// The most files one video may be split into (2 GB at the usual limit).
  static const maxParts = 100;

  /// True while a backup or restore runs, anywhere in the app.
  static final running = ValueNotifier<bool>(false);

  /// Runs [job] unless a backup or restore is already running (then null):
  /// two at once would upload the same item twice.
  static Future<T?> exclusive<T>(Future<T> Function() job) async {
    if (running.value) return null;
    running.value = true;
    try {
      return await job();
    } finally {
      running.value = false;
    }
  }

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
    if (!library.backupFor(account)) return const BackupResult();
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
      // Space saver: a smaller copy of a photo, when one can be made.
      VaultOriginal? small;
      if (library.backup.spaceSaver && !item.isVideo) {
        try {
          small = await vault.spaceSaver(item.id);
        } on Object {
          // The original goes up instead.
        }
      }
      if (item.size > maxBytes && small == null) {
        if (!item.isVideo || item.size > maxBytes * maxParts) {
          tooLarge++;
          continue;
        }
        final uploaded = <ZtAttachmentRef>[];
        try {
          for (var at = 0; at < item.size; at += maxBytes) {
            final want = min(maxBytes, item.size - at);
            final bytes = await vault.originalPart(item.id, at, want);
            if (bytes.length != want) throw StateError('short read');
            uploaded.add(await store.upload(bytes, 'video/mp4'));
          }
          final media = CameraMedia(
            id: newBackupId(),
            type: 'video',
            mime: 'video/mp4',
            takenAt: item.takenAt,
            size: item.size,
            file: uploaded.first,
            parts: uploaded,
            favourite: favourite,
            albums: albums,
            lat: item.lat,
            lon: item.lon,
            thumb: await _uploadThumb(item.id),
          );
          await store.saveMedia(media);
          library.backedUp[item.id] = media.id;
          done++;
        } on Object catch (e) {
          // Half a video is no use: what went up comes down again.
          for (final ref in uploaded) {
            try {
              await store.discard(ref);
            } on Object {
              // Left behind; it counts toward storage until deleted.
            }
          }
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
        continue;
      }
      try {
        final original = small ?? await vault.original(item.id);
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
          lat: item.lat,
          lon: item.lon,
          saver: small != null,
          thumb: await _uploadThumb(item.id),
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

  /// The item's small preview, uploaded for other phones to show. Null when
  /// there is none or it didn't go up: the backup of the file still counts.
  Future<ZtAttachmentRef?> _uploadThumb(String id) async {
    try {
      final bytes = await vault.thumb(id);
      if (bytes == null || bytes.isEmpty) return null;
      return await store.upload(bytes, 'image/jpeg');
    } on Object {
      return null;
    }
  }

  /// What is in the account but not on this phone, newest first. Reads
  /// only; nothing is changed.
  Future<List<CameraMedia>> missing(VaultLibrary library) async {
    // What this phone knows of another account's copies says nothing here.
    final here = library.backup.account == account
        ? {...library.backedUp.values, ...library.backupDeletes}
        : const <String>{};
    return [
      for (final m in await store.media())
        if (!here.contains(m.id)) m,
    ]..sort((a, b) => b.takenAt.compareTo(a.takenAt));
  }

  /// Brings one backed-up item onto this phone, sealed into the vault with
  /// its favourite mark, albums and place. Changes [library]; the caller
  /// saves it. Returns the new vault id.
  Future<String> keep(
    VaultLibrary library,
    CameraMedia m, {
    Map<String, String>? albumNames,
  }) async {
    library.useBackupAccount(account);
    final names =
        albumNames ?? {for (final a in await store.albums()) a.id: a.name};
    final String id;
    if (m.parts.length > 1) {
      final token = newBackupId();
      try {
        for (final part in m.parts) {
          await vault.restorePart(token, await store.download(part));
        }
        id = await vault.restoreFinish(
          token,
          m.takenAt,
          lat: m.lat,
          lon: m.lon,
        );
      } on Object {
        await vault.restoreCancel(token);
        rethrow;
      }
    } else {
      id = await vault.restore(
        await store.download(m.file),
        mime: m.mime,
        isVideo: m.isVideo,
        takenAt: m.takenAt,
        lat: m.lat,
        lon: m.lon,
      );
    }
    library.backedUp[id] = m.id;
    if (m.favourite) library.favourites.add(id);
    for (final albumId in m.albums) {
      if (!library.albums.containsKey(albumId)) {
        library.addAlbum(names[albumId] ?? '', albumId);
      }
      library.addToAlbum(albumId, [id]);
    }
    return id;
  }

  /// Seals every backed-up item this phone doesn't have into the vault,
  /// with its favourite mark and albums. Changes [library]; the caller saves
  /// it.
  Future<BackupResult> restore(
    VaultLibrary library, {
    void Function(int done, int total)? progress,
  }) async {
    library.useBackupAccount(account);
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
        await keep(library, m, albumNames: albumNames);
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

/// Runs a backup or restore [job] with its progress in a notification, which
/// goes away when the job ends. [ask] asks for the notification permission
/// (only right after a tap).
Future<T> withBackupNotice<T>(
  VaultBackend vault,
  AppL10n l,
  Future<T> Function(void Function(int done, int total) progress) job, {
  void Function(int done, int total)? progress,
  bool ask = false,
}) async {
  var asked = !ask;
  try {
    return await job((done, total) {
      progress?.call(done, total);
      vault.backupNotice(
        l.backupTitle,
        l.backupProgress(done, total),
        done,
        total,
        ask: !asked,
      );
      asked = true;
    });
  } finally {
    await vault.backupNoticeEnd();
  }
}

/// The id of the account backup can use right now, or null: signed in, and
/// its own vault (the account key) unlocked.
final backupAccountProvider = Provider<String?>(
  (ref) =>
      !ref.watch(offlineBuildProvider) &&
          ref.watch(vaultControllerProvider) is VaultUnlocked
      ? ref.watch(currentUserProvider)?.id
      : null,
);

/// Whether the account is ready for backup.
final backupReadyProvider = Provider<bool>(
  (ref) => ref.watch(backupAccountProvider) != null,
);
