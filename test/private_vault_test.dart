import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:zero_trust_core/zero_trust_core.dart';
import 'package:zerotrust_camera/private_vault/vault_backend.dart';
import 'package:zerotrust_camera/private_vault/vault_library.dart';
import 'package:zerotrust_camera/private_vault/vault_page.dart';

import 'support/app.dart';

/// The vault without the phone: items, state and what was asked of it.
// A real 1x1 PNG, so the viewer can open a photo in tests.
final _onePixelPng = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
);

class FakeVault implements VaultBackend {
  FakeVault({
    this.setUpDone = true,
    this.unlocked = false,
    this.screenLock = true,
    List<VaultItem>? items,
    this.state,
    this.unlockAnswer = VaultAuth.ok,
  }) : items = items ?? [];

  bool setUpDone;
  bool unlocked;
  bool screenLock;
  bool privateMode = false;
  List<VaultItem> items;
  String? state;
  VaultAuth unlockAnswer;
  int unlocks = 0;
  final List<List<String>> deleted = [];
  final List<List<String>> shared = [];
  final List<List<String>> saved = [];
  final List<bool> secure = [];

  @override
  Future<VaultStatus> status() async => VaultStatus(
    setUp: setUpDone,
    unlocked: unlocked,
    privateMode: privateMode,
    screenLock: screenLock,
  );

  @override
  Future<VaultAuth> setUp() async {
    setUpDone = true;
    unlocked = true;
    privateMode = true;
    return VaultAuth.ok;
  }

  @override
  Future<VaultAuth> unlock() async {
    unlocks++;
    if (unlockAnswer == VaultAuth.ok) unlocked = true;
    return unlockAnswer;
  }

  @override
  Future<void> lock() async => unlocked = false;

  @override
  Future<void> setPrivateMode(bool on) async => privateMode = on;

  @override
  Future<void> setSecure(bool on) async => secure.add(on);

  void _check() {
    if (!unlocked) throw const VaultLockedException();
  }

  @override
  Future<List<VaultItem>> list() async {
    _check();
    return items;
  }

  @override
  Future<Uint8List?> thumb(String id) async {
    _check();
    return null;
  }

  @override
  Future<Uint8List> photo(String id) async {
    _check();
    return _onePixelPng;
  }

  @override
  Future<String?> readState() async {
    _check();
    return state;
  }

  @override
  Future<void> writeState(String json) async => state = json;

  @override
  Future<void> delete(List<String> ids) async {
    deleted.add(ids);
    items = [
      for (final i in items)
        if (!ids.contains(i.id)) i,
    ];
  }

  @override
  Future<int> saveToGallery(List<String> ids) async {
    saved.add(ids);
    return ids.length;
  }

  @override
  Future<void> share(List<String> ids) async => shared.add(ids);

  @override
  Future<int> importMedia() async => 0;

  @override
  Future<void> playVideo(String id) async {}

  bool scanHistoryOn = false;
  List<ScanRecord> scanRecords = [];

  @override
  Future<bool> scanHistory() async => scanHistoryOn;

  @override
  Future<void> setScanHistory(bool on) async => scanHistoryOn = on;

  @override
  Future<List<ScanRecord>> scans() async {
    _check();
    return scanRecords;
  }

  @override
  Future<void> deleteScans(List<String> ids) async =>
      scanRecords.removeWhere((s) => ids.contains(s.id));

  String? textInPhoto;

  /// Locks the vault while the text is being read, as leaving the app would.
  bool lockWhileReading = false;

  @override
  Future<String?> readText(String id) async {
    _check();
    if (lockWhileReading) unlocked = false;
    return textInPhoto;
  }

  final edited = <String>[];

  /// Files as sealed, for the backup tests.
  final originals = <String, VaultOriginal>{};
  final restored = <(Uint8List, String, bool, DateTime)>[];

  @override
  Future<VaultOriginal> original(String id) async {
    _check();
    return originals[id] ??
        VaultOriginal(Uint8List.fromList([1, 2, 3]), 'image/jpeg');
  }

  @override
  Future<String> restore(
    Uint8List bytes, {
    required String mime,
    required bool isVideo,
    required DateTime takenAt,
  }) async {
    restored.add((bytes, mime, isVideo, takenAt));
    final id = 'restored${restored.length}';
    items.add(
      VaultItem(id: id, isVideo: isVideo, takenAt: takenAt, size: bytes.length),
    );
    return id;
  }

  @override
  Future<void> editPhoto(String id) async {
    _check();
    edited.add(id);
  }
}

VaultItem _item(String id, DateTime at, {bool video = false}) =>
    VaultItem(id: id, isVideo: video, takenAt: at);

void _phone(WidgetTester tester) {
  tester.view.physicalSize = const Size(360 * 3, 800 * 3);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
}

void main() {
  final now = DateTime(2026, 11, 19, 12);

  group('library', () {
    test('keeps albums, favourites and the trash through JSON', () {
      final lib = VaultLibrary();
      lib.addAlbum('Trip', 'a1');
      lib.addToAlbum('a1', ['x', 'y']);
      lib.toggleFavourite(['x']);
      lib.trash(['y'], now);
      final back = VaultLibrary.fromJson(lib.toJson());
      expect(back.albums['a1']!.name, 'Trip');
      expect(back.albums['a1']!.items, ['x', 'y']);
      expect(back.favourites, {'x'});
      expect(back.trashed['y'], now);
    });

    test('items in the trash for 30 days are due for deleting', () {
      final lib = VaultLibrary();
      lib.trash(['old'], now.subtract(const Duration(days: 30)));
      lib.trash(['new'], now.subtract(const Duration(days: 29)));
      expect(lib.expired(now), ['old']);
    });

    test('deleted items leave every list', () {
      final lib = VaultLibrary();
      lib.addAlbum('A', 'a');
      lib.addToAlbum('a', ['x', 'y']);
      lib.toggleFavourite(['x', 'y']);
      lib.trash(['x'], now);
      lib.keepOnly({'y'});
      expect(lib.albums['a']!.items, ['y']);
      expect(lib.favourites, {'y'});
      expect(lib.trashed, isEmpty);
    });

    test('a damaged state file opens as empty, not as an error', () {
      expect(VaultLibrary.fromJson('{not json').albums, isEmpty);
      expect(VaultLibrary.fromJson(null).favourites, isEmpty);
    });

    test('favourite toggles a mixed selection on, then off', () {
      final lib = VaultLibrary()..toggleFavourite(['a']);
      lib.toggleFavourite(['a', 'b']);
      expect(lib.favourites, {'a', 'b'});
      lib.toggleFavourite(['a', 'b']);
      expect(lib.favourites, isEmpty);
    });
  });

  group('screen', () {
    testWidgets('opens locked, asks to unlock once, then shows the grid', (
      tester,
    ) async {
      _phone(tester);
      final vault = FakeVault(
        items: [_item('a', now), _item('b', now, video: true)],
      );
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      expect(vault.unlocks, 1);
      expect(vault.secure, [true]);
      expect(find.byKey(const ValueKey('a')), findsOneWidget);
      expect(find.byKey(const ValueKey('b')), findsOneWidget);
      // The switch for new photos sits above the filters, which sit above
      // the grid.
      final mode = tester
          .getTopLeft(find.text('Private mode for new photos'))
          .dy;
      final all = tester.getTopLeft(find.text('All')).dy;
      final first = tester.getTopLeft(find.byKey(const ValueKey('a'))).dy;
      expect(mode, lessThan(all));
      expect(all, lessThan(first));
      // Newest first is the native side's order, kept as is.
      expect(
        tester.getTopLeft(find.byKey(const ValueKey('a'))).dx,
        lessThan(tester.getTopLeft(find.byKey(const ValueKey('b'))).dx),
      );
    });

    testWidgets('a cancelled unlock leaves the locked screen with a button', (
      tester,
    ) async {
      _phone(tester);
      final vault = FakeVault(unlockAnswer: VaultAuth.cancelled);
      await tester.pumpWidget(
        app(
          const PrivateVaultPage(),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('The vault is locked'), findsOneWidget);
      vault.unlockAnswer = VaultAuth.ok;
      await tester.tap(find.text('Unlock'));
      await tester.pumpAndSettle();
      expect(find.text('The vault is locked'), findsNothing);
      expect(vault.unlocks, 2);
    });

    testWidgets('without a screen lock it says so instead of offering setup', (
      tester,
    ) async {
      _phone(tester);
      final vault = FakeVault(setUpDone: false, screenLock: false);
      await tester.pumpWidget(
        app(
          const PrivateVaultPage(),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Set up the vault'), findsNothing);
      expect(find.textContaining('Set a screen lock'), findsOneWidget);
      expect(vault.unlocks, 0);
    });

    testWidgets('setting up opens an empty vault with Private mode on', (
      tester,
    ) async {
      _phone(tester);
      final vault = FakeVault(setUpDone: false);
      await tester.pumpWidget(
        app(
          const PrivateVaultPage(),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Set up the vault'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Nothing here yet'), findsOneWidget);
      expect(find.text('New photos and videos go here'), findsOneWidget);
    });

    testWidgets('trash, restore and delete for good', (tester) async {
      _phone(tester);
      final vault = FakeVault(
        unlocked: true,
        items: [_item('a', now), _item('b', now)],
      );
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();

      await tester.longPress(find.byKey(const ValueKey('a')));
      await tester.pumpAndSettle();
      expect(find.text('1 selected'), findsOneWidget);
      await tester.tap(find.byTooltip('Move to trash'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('a')), findsNothing);
      expect(VaultLibrary.fromJson(vault.state).isTrashed('a'), isTrue);

      // The filters scroll sideways on a narrow phone.
      await tester.ensureVisible(find.text('Trash'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Trash'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('a')), findsOneWidget);
      expect(find.byKey(const ValueKey('b')), findsNothing);

      await tester.longPress(find.byKey(const ValueKey('a')));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Delete for good'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Delete'));
      await tester.pumpAndSettle();
      expect(vault.deleted, [
        ['a'],
      ]);
      expect(find.text('The trash is empty'), findsOneWidget);
    });

    testWidgets('the trash empties itself after 30 days', (tester) async {
      _phone(tester);
      final lib = VaultLibrary()
        ..trash(['old'], now.subtract(const Duration(days: 31)));
      final vault = FakeVault(
        unlocked: true,
        items: [_item('old', now), _item('kept', now)],
        state: lib.toJson(),
      );
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      expect(vault.deleted, [
        ['old'],
      ]);
      expect(jsonDecode(vault.state!)['trashed'], isEmpty);
      expect(find.byKey(const ValueKey('kept')), findsOneWidget);
    });

    testWidgets('sharing and saving go through the cleaning native side', (
      tester,
    ) async {
      _phone(tester);
      final vault = FakeVault(unlocked: true, items: [_item('a', now)]);
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      await tester.longPress(find.byKey(const ValueKey('a')));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Share without details'));
      await tester.pumpAndSettle();
      expect(vault.shared, [
        ['a'],
      ]);

      await tester.longPress(find.byKey(const ValueKey('a')));
      await tester.pumpAndSettle();
      await tester.tap(find.byType(PopupMenuButton<int>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Save to gallery'));
      await tester.pumpAndSettle();
      expect(vault.saved, [
        ['a'],
      ]);
      expect(find.textContaining('without location, date'), findsOneWidget);
    });

    testWidgets('Edit a copy opens the editor and closes the photo', (
      tester,
    ) async {
      _phone(tester);
      final vault = FakeVault(unlocked: true, items: [_item('a', now)]);
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('a')));
      await tester.pumpAndSettle();
      final edit = find.byTooltip('Edit a copy');
      expect(edit, findsOneWidget);
      // Edit sits in the viewer's top bar, next to Copy text.
      expect(
        tester.getCenter(edit).dy,
        tester.getCenter(find.byTooltip('Copy text')).dy,
      );
      await tester.tap(edit);
      await tester.pumpAndSettle();
      expect(vault.edited, ['a']);
      expect(find.byTooltip('Edit a copy'), findsNothing);
      expect(find.byKey(const ValueKey('a')), findsOneWidget);
    });

    testWidgets('Copy text shows the text, but not once the vault locked', (
      tester,
    ) async {
      _phone(tester);
      final vault = FakeVault(unlocked: true, items: [_item('a', now)])
        ..textInPhoto = 'Invoice 1234';
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('a')));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Copy text'));
      await tester.pumpAndSettle();
      expect(find.text('Invoice 1234'), findsOneWidget);
      Navigator.of(tester.element(find.text('Invoice 1234'))).pop();
      await tester.pumpAndSettle();

      vault.lockWhileReading = true;
      await tester.tap(find.byTooltip('Copy text'));
      await tester.pumpAndSettle();
      expect(find.text('Invoice 1234'), findsNothing);
    });

    testWidgets('backup asks for the account first and is off until on', (
      tester,
    ) async {
      _phone(tester);
      final vault = FakeVault(unlocked: true, items: [_item('a', now)]);
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Encrypted backup'));
      await tester.pumpAndSettle();
      expect(find.textContaining('encrypted on this phone'), findsOneWidget);
      expect(find.textContaining('Sign in to your Zero Trust'), findsOneWidget);
      expect(find.text('Back up now'), findsNothing);
      expect(vault.originals, isEmpty);
    });

    testWidgets('locking from the app bar hides everything', (tester) async {
      _phone(tester);
      final vault = FakeVault(unlocked: true, items: [_item('a', now)]);
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Lock'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('a')), findsNothing);
      expect(find.text('The vault is locked'), findsOneWidget);
    });

    testWidgets('Hebrew: the vault reads right to left', (tester) async {
      _phone(tester);
      final vault = FakeVault(unlocked: true, items: [_item('a', now)]);
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          locale: ZtLanguages.hebrew,
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('כספת פרטית'), findsOneWidget);
      // The first filter is on the right.
      expect(
        tester.getTopLeft(find.text('הכול')).dx,
        greaterThan(tester.getTopLeft(find.text('אשפה')).dx),
      );
    });

    testWidgets('scan history: switch on top, codes below, delete one', (
      tester,
    ) async {
      _phone(tester);
      final vault = FakeVault(unlocked: true, items: [_item('a', now)])
        ..scanRecords = [
          ScanRecord(id: 's1', text: 'https://example.com', at: now),
          ScanRecord(id: 's2', text: 'WIFI:S:Home;;', at: now),
        ];
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Scan history'));
      await tester.pumpAndSettle();
      final toggle = find.byKey(const Key('scan-history-switch'));
      expect(toggle, findsOneWidget);
      expect(
        tester.getTopLeft(toggle).dy,
        lessThan(tester.getTopLeft(find.text('https://example.com')).dy),
      );
      await tester.tap(toggle);
      await tester.pumpAndSettle();
      expect(vault.scanHistoryOn, isTrue);
      await tester.tap(
        find.descendant(
          of: find.byKey(const ValueKey('scan-s1')),
          matching: find.byTooltip('Delete'),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('https://example.com'), findsNothing);
      expect(find.text('WIFI:S:Home;;'), findsOneWidget);
    });
  });
}
