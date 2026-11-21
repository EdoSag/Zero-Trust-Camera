import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:zero_trust_core/zero_trust_core.dart';
import 'package:zerotrust_camera/private_vault/vault_backend.dart';
import 'package:zerotrust_camera/private_vault/vault_backup.dart';
import 'package:zerotrust_camera/private_vault/vault_library.dart';

/// The vault's two backup calls; nothing else is used.
class _Vault implements VaultBackend {
  final files = <String, VaultOriginal>{};
  final restored = <String, (Uint8List, String, bool)>{};

  @override
  Future<VaultOriginal> original(String id) async => files[id]!;

  @override
  Future<Uint8List> originalPart(String id, int offset, int length) async {
    partReads++;
    return Uint8List.sublistView(files[id]!.bytes, offset, offset + length);
  }

  int partReads = 0;
  final parts = <String, List<int>>{};
  final cancelled = <String>[];
  bool failFinish = false;

  @override
  Future<void> restorePart(String token, Uint8List bytes) async =>
      (parts[token] ??= []).addAll(bytes);

  @override
  Future<String> restoreFinish(String token, DateTime takenAt) async {
    if (failFinish) throw Exception('not sealed');
    final id = 'new${restored.length + 1}';
    restored[id] = (
      Uint8List.fromList(parts.remove(token)!),
      'video/mp4',
      true,
    );
    return id;
  }

  @override
  Future<void> restoreCancel(String token) async {
    parts.remove(token);
    cancelled.add(token);
  }

  @override
  Future<String> restore(
    Uint8List bytes, {
    required String mime,
    required bool isVideo,
    required DateTime takenAt,
  }) async {
    final id = 'new${restored.length + 1}';
    restored[id] = (bytes, mime, isVideo);
    return id;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

/// The account, in memory. Attachments are kept as given (the real one
/// encrypts them on the phone).
class _Account implements BackupStore {
  final media_ = <String, CameraMedia>{};
  final albums_ = <String, CameraAlbum>{};
  final files = <String, Uint8List>{};
  int uploads = 0;
  bool full = false;

  @override
  Future<List<CameraMedia>> media() async => media_.values.toList();

  @override
  Future<List<CameraAlbum>> albums() async => albums_.values.toList();

  @override
  Future<void> saveMedia(CameraMedia m) async => media_[m.id] = m;

  @override
  Future<void> saveAlbum(CameraAlbum a) async => albums_[a.id] = a;

  @override
  Future<void> deleteMedia(CameraMedia m) async {
    media_.remove(m.id);
    for (final f in m.files) {
      files.remove(f.id);
    }
  }

  @override
  Future<void> discard(ZtAttachmentRef ref) async => files.remove(ref.id);

  /// Uploads start failing after this many.
  int? failAfter;

  @override
  Future<ZtAttachmentRef> upload(Uint8List bytes, String mime) async {
    if (full) throw Exception('Storage quota exceeded');
    if (failAfter != null && uploads >= failAfter!) throw Exception('offline');
    uploads++;
    final id = 'f$uploads';
    files[id] = bytes;
    return ZtAttachmentRef(
      id: id,
      key: Uint8List(32),
      size: bytes.length,
      mimeType: mime,
    );
  }

  @override
  Future<Uint8List> download(ZtAttachmentRef ref) async => files[ref.id]!;
}

final _t = DateTime(2026, 11, 19, 12);

VaultItem _photo(String id, {int size = 3}) =>
    VaultItem(id: id, isVideo: false, takenAt: _t, size: size);

VaultItem _video(String id, {int size = 3}) =>
    VaultItem(id: id, isVideo: true, takenAt: _t, size: size);

/// A library with backup turned on for the account "u1".
VaultLibrary _on() => VaultLibrary()
  ..useBackupAccount('u1')
  ..backup.on = true;

void main() {
  late _Vault vault;
  late _Account account;
  late VaultBackup backup;

  setUp(() {
    vault = _Vault();
    account = _Account();
    backup = VaultBackup(
      vault: vault,
      store: account,
      account: 'u1',
      maxBytes: 10,
    );
  });

  test('off by default: nothing leaves the phone', () async {
    final lib = VaultLibrary();
    vault.files['a'] = VaultOriginal(Uint8List(3), 'image/jpeg');
    final r = await backup.backUp(lib, [_photo('a')]);
    expect(r.done, 0);
    expect(account.uploads, 0);
    expect(lib.backup.on, isFalse);
  });

  test('on for one account never uploads to another', () async {
    final lib = _on();
    vault.files['a'] = VaultOriginal(Uint8List(3), 'image/jpeg');
    await backup.backUp(lib, [_photo('a')]);
    expect(account.uploads, 1);

    // Signed in to someone else's account: nothing goes there.
    final other = _Account();
    final theirs = VaultBackup(vault: vault, store: other, account: 'u2');
    expect(lib.backupFor('u2'), isFalse);
    final r = await theirs.backUp(lib, [_photo('a')]);
    expect(r.done, 0);
    expect(other.uploads, 0);

    // Turning it on there starts clean: the first account's copies are
    // neither reused nor queued for deletion.
    lib
      ..useBackupAccount('u2')
      ..backup.on = true;
    expect(lib.backedUp, isEmpty);
    expect(lib.backupDeletes, isEmpty);
    await theirs.backUp(lib, [_photo('a')]);
    expect(other.uploads, 1);
    expect(account.media_, hasLength(1));
  });

  test('backup that was on with no account recorded is off', () async {
    final lib = VaultLibrary.fromJson('{"backup":{"on":true}}');
    expect(lib.backupFor('u1'), isFalse);
    vault.files['a'] = VaultOriginal(Uint8List(3), 'image/jpeg');
    await backup.backUp(lib, [_photo('a')]);
    expect(account.uploads, 0);
  });

  test('one backup at a time', () async {
    final first = VaultBackup.exclusive(() async {
      expect(VaultBackup.running.value, isTrue);
      expect(await VaultBackup.exclusive(() async => 2), isNull);
      return 1;
    });
    expect(await first, 1);
    expect(VaultBackup.running.value, isFalse);
    expect(await VaultBackup.exclusive(() async => 3), 3);
  });

  test('backs up photos once, with favourites and albums', () async {
    final lib = _on();
    lib.addAlbum('Trip', 'al1');
    lib.addToAlbum('al1', ['a']);
    lib.favourites.add('b');
    vault.files['a'] = VaultOriginal(Uint8List.fromList([1, 2]), 'image/jpeg');
    vault.files['b'] = VaultOriginal(
      Uint8List.fromList([3]),
      'image/x-adobe-dng',
    );

    final r = await backup.backUp(lib, [_photo('a'), _photo('b')]);
    expect(r.done, 2);
    expect(account.albums_['al1']?.name, 'Trip');
    final a = account.media_[lib.backedUp['a']]!;
    expect(a.albums, ['al1']);
    expect(a.favourite, isFalse);
    final b = account.media_[lib.backedUp['b']]!;
    expect(b.favourite, isTrue);
    expect(b.mime, 'image/x-adobe-dng');

    // A second run uploads nothing, but carries a new favourite over.
    lib.favourites.add('a');
    final again = await backup.backUp(lib, [_photo('a'), _photo('b')]);
    expect(again.done, 0);
    expect(account.uploads, 2);
    expect(account.media_[lib.backedUp['a']]!.favourite, isTrue);
  });

  test('only the albums picked, videos only when asked, trash never', () async {
    final lib = VaultLibrary()
      ..backup.on = true
      ..backup.everything = false;
    lib.addAlbum('Kids', 'k');
    lib.addToAlbum('k', ['a', 'v', 't']);
    lib.backup.albums.add('k');
    lib.trash(['t'], _t);
    final items = [_photo('a'), _photo('b'), _video('v'), _photo('t')];
    expect(backup.eligible(lib, items).map((i) => i.id), ['a']);
    lib.backup.videos = true;
    expect(backup.eligible(lib, items).map((i) => i.id), ['a', 'v']);
  });

  test('a large video goes up in parts and comes back whole', () async {
    final lib = _on()..backup.videos = true;
    final bytes = Uint8List.fromList(List.generate(25, (i) => i));
    vault.files['v'] = VaultOriginal(bytes, 'video/mp4');
    lib.favourites.add('v');

    final r = await backup.backUp(lib, [_video('v', size: 25)]);
    expect(r.done, 1);
    expect(r.tooLarge, 0);
    // 10 + 10 + 5, never the whole file at once.
    expect(account.uploads, 3);
    expect(vault.partReads, 3);
    final m = account.media_[lib.backedUp['v']]!;
    expect(m.parts.map((p) => p.size), [10, 10, 5]);
    expect(m.file.id, m.parts.first.id);
    expect(m.size, 25);

    // It survives being stored, and a second run uploads nothing.
    final stored = CameraMedia.fromJson(m.id, m.toJson(), _t);
    expect(stored.parts.map((p) => p.id), m.parts.map((p) => p.id));
    await backup.backUp(lib, [_video('v', size: 25)]);
    expect(account.uploads, 3);

    // A new phone gets the same bytes back, as one video.
    final fresh = VaultLibrary();
    final back = await backup.restore(fresh);
    expect(back.done, 1);
    expect(vault.restored.values.single.$1, bytes);
    expect(vault.restored.values.single.$3, isTrue);
    expect(fresh.favourites, hasLength(1));

    // Deleted for good on the phone: every part leaves the account.
    lib.forget(['v']);
    await backup.backUp(lib, []);
    expect(account.media_, isEmpty);
    expect(account.files, isEmpty);
  });

  test('a large video that fails half way leaves nothing behind', () async {
    final lib = _on()..backup.videos = true;
    vault.files['v'] = VaultOriginal(Uint8List(25), 'video/mp4');
    account.failAfter = 2;
    final r = await backup.backUp(lib, [_video('v', size: 25)]);
    expect(r.done, 0);
    expect(r.failed, 1);
    expect(account.files, isEmpty);
    expect(account.media_, isEmpty);
    expect(lib.backedUp, isEmpty);
  });

  test('a large restore that fails wipes its parts', () async {
    final lib = _on()..backup.videos = true;
    vault.files['v'] = VaultOriginal(Uint8List(25), 'video/mp4');
    await backup.backUp(lib, [_video('v', size: 25)]);
    vault.failFinish = true;
    final r = await backup.restore(VaultLibrary());
    expect(r.failed, 1);
    expect(vault.cancelled, hasLength(1));
    expect(vault.restored, isEmpty);
  });

  test('a large photo, or a video past the cap, stays on the phone', () async {
    final lib = _on()..backup.videos = true;
    final r = await backup.backUp(lib, [
      _photo('raw', size: 11),
      _video('huge', size: 10 * VaultBackup.maxParts + 1),
    ]);
    expect(r.tooLarge, 2);
    expect(account.uploads, 0);
  });

  test('a full account stops the run and says so', () async {
    final lib = _on();
    vault.files['a'] = VaultOriginal(Uint8List(2), 'image/jpeg');
    account.full = true;
    final r = await backup.backUp(lib, [_photo('a')]);
    expect(r.full, isTrue);
    expect(lib.backedUp, isEmpty);
  });

  test(
    'restore on a new phone brings back files, favourites and albums',
    () async {
      final first = _on();
      first.addAlbum('Trip', 'al1');
      first.addToAlbum('al1', ['a']);
      first.favourites.add('a');
      vault.files['a'] = VaultOriginal(
        Uint8List.fromList([7, 8]),
        'image/jpeg',
      );
      vault.files['v'] = VaultOriginal(Uint8List.fromList([9]), 'video/mp4');
      first.backup.videos = true;
      await backup.backUp(first, [_photo('a'), _video('v')]);

      final phone2 = VaultLibrary();
      final r = await backup.restore(phone2);
      expect(r.done, 2);
      expect(vault.restored, hasLength(2));
      final photoId = vault.restored.entries.firstWhere((e) => !e.value.$3).key;
      expect(vault.restored[photoId]!.$1, [7, 8]);
      expect(phone2.favourites, {photoId});
      expect(phone2.albums['al1']?.name, 'Trip');
      expect(phone2.albums['al1']?.items, [photoId]);
      expect(phone2.backedUp.keys, containsAll(vault.restored.keys));

      // Running it again brings nothing twice.
      expect((await backup.restore(phone2)).done, 0);
    },
  );

  test('deleting for good deletes the backed-up copy too', () async {
    final lib = _on();
    vault.files['a'] = VaultOriginal(Uint8List(2), 'image/jpeg');
    await backup.backUp(lib, [_photo('a')]);
    expect(account.media_, hasLength(1));
    lib.forget(['a']);
    expect(lib.backedUp, isEmpty);
    // Queued even while the account can't be reached, and not restored.
    expect(lib.backupDeletes, hasLength(1));
    expect((await backup.restore(lib)).done, 0);
    await backup.deletePending(lib);
    expect(account.media_, isEmpty);
    expect(account.files, isEmpty);
    expect(lib.backupDeletes, isEmpty);
  });

  test('settings and the backed-up list survive the sealed state file', () {
    final lib = VaultLibrary()
      ..backup.on = true
      ..backup.everything = false
      ..backup.videos = true;
    lib.backup.albums.add('k');
    lib.backedUp['a'] = 'm1';
    final back = VaultLibrary.fromJson(lib.toJson());
    expect(back.backup.on, isTrue);
    expect(back.backup.everything, isFalse);
    expect(back.backup.videos, isTrue);
    expect(back.backup.albums, {'k'});
    expect(back.backedUp, {'a': 'm1'});
    // An old state file without them: backup off, nothing backed up.
    lib.backupDeletes.add('m9');
    expect(VaultLibrary.fromJson(lib.toJson()).backupDeletes, {'m9'});
    final old = VaultLibrary.fromJson('{"v":1,"albums":[]}');
    expect(old.backup.on, isFalse);
    expect(old.backedUp, isEmpty);
  });

  test('the rules: Wi-Fi only by default, charging only when asked', () {
    final s = BackupSettings();
    expect(s.allows(unmetered: false, charging: true), isFalse);
    expect(s.allows(unmetered: true, charging: false), isTrue);
    s.chargingOnly = true;
    expect(s.allows(unmetered: true, charging: false), isFalse);
    expect(s.allows(unmetered: true, charging: true), isTrue);
    s.wifiOnly = false;
    expect(s.allows(unmetered: false, charging: true), isTrue);
    final back = BackupSettings.fromJson(s.toJson());
    expect(back.wifiOnly, isFalse);
    expect(back.chargingOnly, isTrue);
    // Settings saved before the rules existed: Wi-Fi only.
    final old = BackupSettings.fromJson(<String, Object?>{'on': true});
    expect(old.wifiOnly, isTrue);
    expect(old.chargingOnly, isFalse);
  });

  test('the item kinds round-trip through JSON', () {
    final m = CameraMedia(
      id: 'm',
      type: 'video',
      mime: 'video/mp4',
      takenAt: _t,
      size: 5,
      file: ZtAttachmentRef(id: 'f', key: Uint8List(32), size: 5),
      favourite: true,
      albums: const ['x'],
    );
    final json = cameraMediaCollection.encode(m);
    expect(json['kind'], 'camera_media');
    final back = CameraMedia.fromJson('m', json, _t);
    expect(back.isVideo, isTrue);
    expect(back.albums, ['x']);
    expect(back.file.id, 'f');
    expect(back.takenAt, _t);
  });

  test('a file gone from this phone keeps its copy, to restore', () {
    final lib = VaultLibrary()..backedUp['a'] = 'm1';
    lib.keepOnly({});
    expect(lib.backedUp, isEmpty);
    expect(lib.backupDeletes, isEmpty);
  });
}
