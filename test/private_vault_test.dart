import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';
import 'package:zero_trust_core/zero_trust_core.dart';
import 'package:zerotrust_camera/app/camera_config.dart';
import 'package:zerotrust_camera/private_vault/vault_backend.dart';
import 'package:zerotrust_camera/private_vault/vault_backup.dart';
import 'package:zerotrust_camera/private_vault/vault_library.dart';
import 'package:zerotrust_camera/private_vault/vault_page.dart';
import 'package:zerotrust_camera/private_vault/vault_search.dart';

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
  Future<Uint8List> originalPart(String id, int offset, int length) async =>
      Uint8List(length);

  @override
  Future<void> restorePart(String token, Uint8List bytes) async {}

  @override
  Future<String> restoreFinish(
    String token,
    DateTime takenAt, {
    double? lat,
    double? lon,
  }) async => 'restored';

  @override
  Future<void> restoreCancel(String token) async {}

  @override
  Future<String> restore(
    Uint8List bytes, {
    required String mime,
    required bool isVideo,
    required DateTime takenAt,
    double? lat,
    double? lon,
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

  VaultLocationState location = const VaultLocationState(
    on: false,
    granted: false,
  );

  @override
  Future<VaultLocationState> vaultLocation() async => location;

  @override
  Future<void> setVaultLocation(bool on) async =>
      location = VaultLocationState(on: on, granted: on);

  @override
  Future<VaultOriginal?> spaceSaver(String id) async => null;

  bool quickHideOn = false;
  void Function()? lockedListener;

  @override
  Future<bool> quickHide() async => quickHideOn;

  @override
  Future<void> setQuickHide(bool on) async => quickHideOn = on;

  @override
  void onLocked(void Function()? listener) => lockedListener = listener;

  /// The native side locking the vault by itself (the phone put face down).
  void faceDown() {
    unlocked = false;
    lockedListener?.call();
  }

  BackupConditions conditions = const BackupConditions(
    unmetered: true,
    charging: true,
  );
  final List<String> notices = [];
  int noticesEnded = 0;

  @override
  Future<BackupConditions> backupConditions() async => conditions;

  @override
  Future<void> backupNotice(
    String title,
    String text,
    int done,
    int total, {
    bool ask = false,
  }) async => notices.add(text);

  @override
  Future<void> backupNoticeEnd() async => noticesEnded++;
}

/// An account holding one photo backed up from another phone.
class _OtherPhoneStore implements BackupStore {
  final photo = CameraMedia(
    id: 'm1',
    type: 'photo',
    mime: 'image/png',
    takenAt: DateTime(2026, 11, 2),
    size: 70,
    file: ZtAttachmentRef(id: 'f1', key: Uint8List(32), size: 70),
  );
  int downloads = 0;

  @override
  Future<List<CameraMedia>> media() async => [photo];

  @override
  Future<List<CameraAlbum>> albums() async => const [];

  @override
  Future<Uint8List> download(ZtAttachmentRef ref) async {
    downloads++;
    return _onePixelPng;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

/// An account that only counts how often the backup looked at it.
class _CountingStore implements BackupStore {
  int looks = 0;

  @override
  Future<List<CameraMedia>> media() async {
    looks++;
    return const [];
  }

  @override
  Future<List<CameraAlbum>> albums() async => const [];

  @override
  dynamic noSuchMethod(Invocation invocation) => throw UnimplementedError();
}

const _months = {
  3: ['march', 'מרץ'],
  11: ['november', 'נובמבר'],
};

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

    test('disappearing: the rule times new items, keep stops it', () {
      final lib = VaultLibrary()
        ..setDisappearRule(const Duration(days: 1), now);
      final changed = lib.applyDisappearRule([
        (id: 'before', takenAt: now.subtract(const Duration(minutes: 1))),
        (id: 'after', takenAt: now.add(const Duration(hours: 2))),
      ]);
      expect(changed, isTrue);
      // Only what was taken since the rule went on.
      expect(lib.disappearAt, {'after': now.add(const Duration(hours: 26))});
      expect(lib.disappeared(now.add(const Duration(hours: 25))), isEmpty);
      expect(lib.disappeared(now.add(const Duration(hours: 26))), ['after']);

      // Kept by hand: the rule doesn't put it back.
      lib.keep(['after']);
      expect(
        lib.applyDisappearRule([
          (id: 'after', takenAt: now.add(const Duration(hours: 2))),
        ]),
        isFalse,
      );
      expect(lib.disappearAt, isEmpty);

      // Through JSON.
      lib.disappear(['x'], now);
      final back = VaultLibrary.fromJson(lib.toJson());
      expect(back.disappearAfter, const Duration(days: 1));
      expect(back.disappearSince, now);
      expect(back.disappearAt, {'x': now});
      expect(back.disappearKept, {'after'});

      // Off: nothing new is timed; timers already set stay.
      back.setDisappearRule(null, now);
      expect(back.disappearSince, isNull);
      expect(back.disappearAt, {'x': now});
      back.forget(['x']);
      expect(back.disappearAt, isEmpty);
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

  group('search', () {
    final a = _item('a', DateTime(2026, 11, 21, 23, 30));
    final b = _item('b', DateTime(2025, 3, 9));
    final c = _item('c', DateTime(2026, 11, 2));
    final lib = VaultLibrary()..addAlbum('Paris Trip', 'p');
    lib.addToAlbum('p', ['b']);
    List<String> ids(VaultSearch s) => [
      for (final i in s.apply([a, b, c], lib, months: _months)) i.id,
    ];

    test('nothing typed keeps everything', () {
      expect(const VaultSearch().isEmpty, isTrue);
      expect(ids(const VaultSearch(text: '  ')), ['a', 'b', 'c']);
    });

    test('album names, in any case', () {
      expect(ids(const VaultSearch(text: 'paris')), ['b']);
      expect(ids(const VaultSearch(text: 'trip PARIS')), ['b']);
      expect(const VaultSearch(text: 'par').matchesAlbum('Paris Trip'), isTrue);
      expect(const VaultSearch(text: 'rome').matchesAlbum('Paris'), isFalse);
    });

    test('years, months, days and whole dates', () {
      expect(ids(const VaultSearch(text: '2026')), ['a', 'c']);
      expect(ids(const VaultSearch(text: 'nov')), ['a', 'c']);
      expect(ids(const VaultSearch(text: 'נובמבר 2026')), ['a', 'c']);
      expect(ids(const VaultSearch(text: 'november 21')), ['a']);
      expect(ids(const VaultSearch(text: '21/11/2026')), ['a']);
      expect(ids(const VaultSearch(text: '2026-11-02')), ['c']);
      expect(ids(const VaultSearch(text: '3/2025')), ['b']);
      expect(ids(const VaultSearch(text: 'march 2026')), isEmpty);
      // Two letters are too few for a month.
      expect(ids(const VaultSearch(text: 'no')), isEmpty);
    });

    test('a date range takes its whole last day', () {
      final r = DateTimeRange(
        start: DateTime(2026, 11, 2),
        end: DateTime(2026, 11, 21),
      );
      expect(ids(VaultSearch(range: r)), ['a', 'c']);
      expect(ids(VaultSearch(text: '21', range: r)), ['a']);
      expect(
        ids(
          VaultSearch(
            range: DateTimeRange(
              start: DateTime(2026, 11, 3),
              end: DateTime(2026, 11, 20),
            ),
          ),
        ),
        isEmpty,
      );
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

    testWidgets('disappearing items go when their time is up', (tester) async {
      _phone(tester);
      final lib = VaultLibrary()
        ..disappear(['due'], now)
        ..disappear(['later'], now.add(const Duration(hours: 1)));
      final vault = FakeVault(
        unlocked: true,
        items: [_item('due', now), _item('later', now), _item('plain', now)],
        state: lib.toJson(),
      );
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      // Gone before anything was shown, and not to the trash.
      expect(vault.deleted, [
        ['due'],
      ]);
      expect(find.byKey(const ValueKey('due')), findsNothing);
      expect(jsonDecode(vault.state!)['trashed'], isEmpty);
      // The one still waiting has a timer on it, top end.
      final timer = find.descendant(
        of: find.byKey(const ValueKey('later')),
        matching: find.byIcon(Icons.timer_outlined),
      );
      expect(timer, findsOneWidget);
      final tile = tester.getRect(find.byKey(const ValueKey('later')));
      expect(tester.getRect(timer).center.dx, greaterThan(tile.center.dx));
      expect(tester.getRect(timer).center.dy, lessThan(tile.center.dy));
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('plain')),
          matching: find.byIcon(Icons.timer_outlined),
        ),
        findsNothing,
      );
    });

    testWidgets('disappearing photos: turned on from the menu, kept by hand', (
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
      await tester.tap(find.byTooltip('More'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Disappearing photos'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('After 1 day'));
      await tester.pumpAndSettle();
      var saved = VaultLibrary.fromJson(vault.state);
      expect(saved.disappearAfter, const Duration(days: 1));
      expect(saved.disappearSince, now);
      // An item already there is left alone.
      expect(saved.disappearAt, isEmpty);

      // One by hand, then kept.
      await tester.longPress(find.byKey(const ValueKey('a')));
      await tester.pumpAndSettle();
      await tester.tap(find.byType(PopupMenuButton<int>).last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Disappear after…'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('After 1 hour'));
      await tester.pumpAndSettle();
      saved = VaultLibrary.fromJson(vault.state);
      expect(saved.disappearAt, {'a': now.add(const Duration(hours: 1))});
      expect(find.byIcon(Icons.timer_outlined), findsOneWidget);

      await tester.longPress(find.byKey(const ValueKey('a')));
      await tester.pumpAndSettle();
      await tester.tap(find.byType(PopupMenuButton<int>).last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Disappear after…'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Keep (stop the timer)'));
      await tester.pumpAndSettle();
      saved = VaultLibrary.fromJson(vault.state);
      expect(saved.disappearAt, isEmpty);
      expect(saved.disappearKept, {'a'});
      expect(find.byIcon(Icons.timer_outlined), findsNothing);
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

    testWidgets('the quiet backup waits for Wi-Fi and the charger', (
      tester,
    ) async {
      _phone(tester);
      final lib = VaultLibrary()
        ..backup.account = 'u1'
        ..backup.on = true
        ..backup.chargingOnly = true;
      lib.backedUp['a'] = 'm1';
      Future<int> looksWith(BackupConditions c) async {
        final vault = FakeVault(
          unlocked: true,
          items: [_item('a', now)],
          state: lib.toJson(),
        )..conditions = c;
        final store = _CountingStore();
        await tester.pumpWidget(
          app(
            PrivateVaultPage(key: UniqueKey(), now: () => now),
            overrides: [
              vaultBackendProvider.overrideWithValue(vault),
              backupStoreProvider.overrideWithValue(store),
              backupAccountProvider.overrideWithValue('u1'),
            ],
          ),
        );
        await tester.pumpAndSettle();
        // Nothing new to upload: no notification either way.
        expect(vault.notices, isEmpty);
        return store.looks;
      }

      expect(
        await looksWith(
          const BackupConditions(unmetered: false, charging: true),
        ),
        0,
      );
      expect(
        await looksWith(
          const BackupConditions(unmetered: true, charging: false),
        ),
        0,
      );
      expect(
        await looksWith(
          const BackupConditions(unmetered: true, charging: true),
        ),
        greaterThan(0),
      );
    });

    testWidgets('search finds by album name, month and year', (tester) async {
      _phone(tester);
      final lib = VaultLibrary()..addAlbum('Paris trip', 'p');
      lib.addToAlbum('p', ['a']);
      final vault = FakeVault(
        unlocked: true,
        items: [
          _item('a', DateTime(2026, 11, 2)),
          _item('b', DateTime(2025, 3, 9)),
        ],
        state: lib.toJson(),
      );
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('vault_search')), findsNothing);
      await tester.tap(find.byTooltip('Search'));
      await tester.pumpAndSettle();
      final field = find.byKey(const ValueKey('vault_search'));
      // The field sits under the filters, above the photos.
      expect(
        tester.getTopLeft(field).dy,
        greaterThan(tester.getBottomLeft(find.text('Favourites')).dy),
      );
      await tester.enterText(field, 'paris');
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('a')), findsOneWidget);
      expect(find.byKey(const ValueKey('b')), findsNothing);
      await tester.enterText(field, 'march 2025');
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('a')), findsNothing);
      expect(find.byKey(const ValueKey('b')), findsOneWidget);
      await tester.enterText(field, 'nothing like this');
      await tester.pumpAndSettle();
      expect(find.text('Nothing in the vault matches'), findsOneWidget);
      // Closing the search shows everything again.
      await tester.tap(find.byTooltip('Close search'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('a')), findsOneWidget);
      expect(find.byKey(const ValueKey('b')), findsOneWidget);
    });

    testWidgets('quick hide: turned on from More, face down locks', (
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
      // The title still fits beside the buttons.
      final title = tester.getRect(find.text('Private vault'));
      expect(
        title.right,
        lessThanOrEqualTo(
          tester.getRect(find.byTooltip('Import from gallery')).left,
        ),
      );
      await tester.tap(find.byTooltip('More'));
      await tester.pumpAndSettle();
      expect(find.text('Lock when face down'), findsOneWidget);
      await tester.tap(find.byType(CheckedPopupMenuItem<int>).first);
      await tester.pumpAndSettle();
      expect(vault.quickHideOn, isTrue);
      expect(find.byKey(const ValueKey('a')), findsOneWidget);

      vault.faceDown();
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('a')), findsNothing);
      expect(find.text('The vault is locked'), findsOneWidget);
    });

    testWidgets('location: off until turned on in More; shown on the photo', (
      tester,
    ) async {
      _phone(tester);
      final vault = FakeVault(
        unlocked: true,
        items: [
          VaultItem(
            id: 'a',
            isVideo: false,
            takenAt: now,
            lat: 32.0853,
            lon: 34.7818,
          ),
        ],
      );
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          overrides: [vaultBackendProvider.overrideWithValue(vault)],
        ),
      );
      await tester.pumpAndSettle();
      expect(vault.location.on, isFalse);
      await tester.tap(find.byTooltip('More'));
      await tester.pumpAndSettle();
      expect(find.text('Add location to vault photos'), findsOneWidget);
      await tester.tap(find.text('Add location to vault photos'));
      await tester.pumpAndSettle();
      expect(vault.location.on, isTrue);
      expect(find.textContaining('inside the vault only'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('a')));
      await tester.pumpAndSettle();
      final place = find.byKey(const ValueKey('vault_place'));
      expect(tester.widget<Text>(place).data, '32.08530, 34.78180');
      expect(tester.getRect(place).right, lessThanOrEqualTo(360));
    });

    testWidgets('In your account: open a photo from another phone, keep it', (
      tester,
    ) async {
      _phone(tester);
      final vault = FakeVault(unlocked: true, items: [_item('a', now)]);
      final store = _OtherPhoneStore();
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          overrides: [
            vaultBackendProvider.overrideWithValue(vault),
            backupStoreProvider.overrideWithValue(store),
            backupAccountProvider.overrideWithValue('u1'),
          ],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('More'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('In your account'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('Backed up from another phone'),
        findsOneWidget,
      );
      // Listing downloads no photo.
      expect(store.downloads, 0);
      expect(vault.restored, isEmpty);

      await tester.tap(find.byKey(const ValueKey('account_m1')));
      await tester.pumpAndSettle();
      // Opened from the account, in memory: still nothing on this phone.
      expect(store.downloads, 1);
      expect(vault.restored, isEmpty);
      final keep = find.text('Keep on this phone');
      final button = tester.getRect(
        find.ancestor(of: keep, matching: find.byType(FilledButton)),
      );
      expect(button.left, greaterThanOrEqualTo(0));
      expect(button.right, lessThanOrEqualTo(360));
      expect(button.bottom, lessThanOrEqualTo(800));

      await tester.tap(keep);
      await tester.pumpAndSettle();
      expect(vault.restored.single.$2, 'image/png');
      expect(find.byKey(const ValueKey('account_m1')), findsNothing);
      expect(
        find.text('Everything in your account is already on this phone.'),
        findsOneWidget,
      );
      // The vault remembers which copy this is, so it won't upload it again.
      expect(VaultLibrary.fromJson(vault.state).backedUp.values, ['m1']);
    });

    testWidgets('the Offline build shows no backup and no account list', (
      tester,
    ) async {
      _phone(tester);
      final vault = FakeVault(unlocked: true, items: [_item('a', now)]);
      await tester.pumpWidget(
        app(
          PrivateVaultPage(now: () => now),
          overrides: [
            vaultBackendProvider.overrideWithValue(vault),
            offlineBuildProvider.overrideWithValue(true),
          ],
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('a')), findsOneWidget);
      expect(find.byTooltip('Encrypted backup'), findsNothing);
      await tester.tap(find.byTooltip('More'));
      await tester.pumpAndSettle();
      expect(find.text('Scan history'), findsOneWidget);
      expect(find.text('In your account'), findsNothing);
    });

    testWidgets(
      'In your account: an open photo closes when the account locks',
      (tester) async {
        _phone(tester);
        final vault = FakeVault(unlocked: true, items: [_item('a', now)]);
        final account = StateProvider<String?>((ref) => 'u1');
        late WidgetRef outer;
        await tester.pumpWidget(
          app(
            Consumer(
              builder: (context, ref, _) {
                outer = ref;
                return PrivateVaultPage(now: () => now);
              },
            ),
            overrides: [
              vaultBackendProvider.overrideWithValue(vault),
              backupStoreProvider.overrideWithValue(_OtherPhoneStore()),
              backupAccountProvider.overrideWith((ref) => ref.watch(account)),
            ],
          ),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byTooltip('More'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('In your account'));
        await tester.pumpAndSettle();
        await tester.tap(find.byKey(const ValueKey('account_m1')));
        await tester.pumpAndSettle();
        expect(find.text('Keep on this phone'), findsOneWidget);

        outer.read(account.notifier).state = null;
        await tester.pumpAndSettle();
        expect(find.text('Keep on this phone'), findsNothing);
        expect(find.byKey(const ValueKey('account_m1')), findsNothing);
      },
    );

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
      await tester.tap(find.byTooltip('More'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Scan history'));
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
