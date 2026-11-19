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
    files.remove(m.file.id);
  }

  @override
  Future<ZtAttachmentRef> upload(Uint8List bytes, String mime) async {
    if (full) throw Exception('Storage quota exceeded');
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

  test('files over the limit stay on the phone and are counted', () async {
    final lib = _on()..backup.videos = true;
    final r = await backup.backUp(lib, [_video('big', size: 11)]);
    expect(r.tooLarge, 1);
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
