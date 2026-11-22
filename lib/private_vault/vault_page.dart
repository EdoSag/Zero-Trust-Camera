import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../app/camera_bridge.dart';
import '../app/camera_config.dart';
import '../l10n/l10n.dart';
import 'account_media_page.dart';
import 'backup_page.dart';
import 'scan_history_page.dart';
import 'vault_backend.dart';
import 'vault_backup.dart';
import 'vault_library.dart';
import 'vault_search.dart';
import 'vault_viewer.dart';

enum VaultFilter { all, favourites, albums, trash }

/// The Private vault: photos and videos sealed on this phone, opened only
/// here after the fingerprint, face or screen lock. Locks itself as soon as
/// the app is left.
class PrivateVaultPage extends ConsumerStatefulWidget {
  const PrivateVaultPage({super.key, this.fromCamera = false, this.now});

  static const routeName = 'vault';

  static Route<void> route({bool fromCamera = false}) =>
      MaterialPageRoute<void>(
        settings: const RouteSettings(name: routeName),
        builder: (_) => PrivateVaultPage(fromCamera: fromCamera),
      );

  /// Opened straight from the camera: back returns to the viewfinder.
  final bool fromCamera;

  /// The clock, for tests.
  final DateTime Function()? now;

  @override
  ConsumerState<PrivateVaultPage> createState() => _PrivateVaultPageState();
}

class _PrivateVaultPageState extends ConsumerState<PrivateVaultPage>
    with WidgetsBindingObserver {
  VaultStatus? _status;

  /// Goes up every time the vault is shown locked, so a slow answer that started before can
  /// tell it is stale.
  int _lockCount = 0;
  List<VaultItem> _items = const [];
  VaultLibrary _library = VaultLibrary();
  VaultFilter _filter = VaultFilter.all;
  String? _album;

  /// The search under the filters: open or not, its words and its dates.
  bool _searching = false;
  final _searchText = TextEditingController();
  DateTimeRange? _searchRange;
  bool _quickHide = false;

  /// Checks disappearing items while the vault is open.
  Timer? _disappearTimer;

  /// Whether photos are being looked at for search right now.
  bool _describing = false;
  bool _location = false;
  final Set<String> _selected = {};
  final Map<String, Future<Uint8List?>> _thumbs = {};
  bool _busy = false;
  bool _askedOnce = false;
  String? _message;

  // Kept, not read each time: dispose() still needs it.
  late final VaultBackend _vault = ref.read(vaultBackendProvider);
  DateTime _now() => (widget.now ?? DateTime.now)();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(_vault.setSecure(true));
    // Quick hide locked the vault: show it locked.
    _vault.onLocked(() => unawaited(_refresh()));
    unawaited(_readQuickHide());
    unawaited(_readLocation());
    unawaited(_refresh(autoUnlock: true));
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    unawaited(_vault.setSecure(false));
    _vault.onLocked(null);
    _disappearTimer?.cancel();
    _searchText.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // The native side locks the vault when the app is left; show it locked.
    if (state == AppLifecycleState.resumed) {
      unawaited(_refresh());
      // The permission prompt may just have been answered.
      unawaited(_readLocation());
    }
  }

  Future<void> _readQuickHide() async {
    try {
      final on = await _vault.quickHide();
      if (mounted) setState(() => _quickHide = on);
    } on Object {
      // Shown as off.
    }
  }

  Future<void> _readLocation() async {
    try {
      final s = await _vault.vaultLocation();
      // On without the permission adds nothing: shown as off.
      if (mounted) setState(() => _location = s.on && s.granted);
    } on Object {
      // Shown as off.
    }
  }

  Future<void> _setLocation(bool on) async {
    setState(() => _location = on);
    try {
      await _vault.setVaultLocation(on);
      if (on) _say((l) => l.vaultLocationOn);
    } on Object {
      if (mounted) setState(() => _location = !on);
      _say((l) => l.vaultFailed);
    }
  }

  Future<void> _setQuickHide(bool on) async {
    setState(() => _quickHide = on);
    try {
      await _vault.setQuickHide(on);
      if (on) _say((l) => l.vaultQuickHideOn);
    } on Object {
      if (mounted) setState(() => _quickHide = !on);
      _say((l) => l.vaultFailed);
    }
  }

  Future<void> _refresh({bool autoUnlock = false}) async {
    final status = await _vault.status();
    if (!mounted) return;
    setState(() => _status = status);
    if (!status.unlocked) {
      _clear();
      if (autoUnlock && status.setUp && !_askedOnce) {
        _askedOnce = true;
        await _unlock();
      }
      return;
    }
    await _load();
  }

  void _clear() {
    // A photo open on top must not stay on screen once the vault is locked.
    final me = ModalRoute.of(context);
    if (me != null && !me.isCurrent) {
      Navigator.of(context).popUntil((r) => r == me);
    }
    _lockCount++;
    _disappearTimer?.cancel();
    _disappearTimer = null;
    setState(() {
      _items = const [];
      _library = VaultLibrary();
      _thumbs.clear();
      _selected.clear();
    });
  }

  Future<void> _load() async {
    try {
      final items = await _vault.list();
      final library = VaultLibrary.fromJson(await _vault.readState());
      library.keepOnly({for (final i in items) i.id});
      // Time in the trash is up, or a disappearing item's time: deleted for
      // good, before anything is shown.
      final now = _now();
      final ruled = library.applyDisappearRule([
        for (final i in items) (id: i.id, takenAt: i.takenAt),
      ]);
      final expired = {
        ...library.expired(now),
        ...library.disappeared(now),
      }.toList();
      var shown = items;
      if (ruled && expired.isEmpty) {
        await _vault.writeState(library.toJson());
      }
      if (expired.isNotEmpty) {
        await _vault.delete(expired);
        library.forget(expired);
        await _vault.writeState(library.toJson());
        shown = [
          for (final i in items)
            if (!expired.contains(i.id)) i,
        ];
      }
      if (!mounted) return;
      setState(() {
        _items = shown;
        _library = library;
      });
      _disappearTimer?.cancel();
      _disappearTimer = Timer.periodic(
        const Duration(seconds: 30),
        (_) => unawaited(_sweepDisappeared()),
      );
      unawaited(_autoBackup());
      unawaited(_describeContent());
    } on VaultLockedException {
      await _refresh();
    }
  }

  /// With backup on and the account ready, new items go up quietly each time
  /// the vault opens (and copies of items deleted for good are deleted).
  Future<void> _autoBackup() async {
    // Only for the account backup was turned on for: after a switch of
    // accounts nothing goes up until it is turned on again.
    if (ref.read(offlineBuildProvider)) return;
    final account = ref.read(backupAccountProvider);
    if (account == null || !_library.backupFor(account)) return;
    final library = _library;
    final items = _items;
    final l = context.l10n;
    try {
      // The rules (Wi-Fi only, only while charging) are for this quiet
      // backup; "Back up now" on the backup screen always runs.
      final now = await _vault.backupConditions();
      if (!library.backup.allows(
        unmetered: now.unmetered,
        charging: now.charging,
      )) {
        return;
      }
      final engine = VaultBackup(
        vault: _vault,
        store: ref.read(backupStoreProvider),
        account: account,
      );
      final somethingNew = engine
          .eligible(library, items)
          .any((i) => !library.backedUp.containsKey(i.id));
      final locks = _lockCount;
      Future<bool> allowed() async {
        final c = await _vault.backupConditions();
        return library.backup.allows(
          unmetered: c.unmetered,
          charging: c.charging,
        );
      }

      final r = await VaultBackup.exclusive(() async {
        final r = somethingNew
            ? await withBackupNotice(
                _vault,
                l,
                (p) => engine.backUp(
                  library,
                  items,
                  progress: p,
                  allowed: allowed,
                ),
              )
            : await engine.backUp(library, items, allowed: allowed);
        // Saved even with nothing new: copies deleted from the account
        // leave the queue.
        if (locks != _lockCount) return r;
        if (!identical(library, _library)) {
          // The list was read again meanwhile (the app came back to the
          // front): what went up is noted on the one in use now, or it
          // would go up a second time.
          final ids = {for (final i in _items) i.id};
          for (final e in library.backedUp.entries) {
            if (!ids.contains(e.key)) continue;
            if (_library.isDisappearing(e.key)) {
              // Put on a timer meanwhile: its copy comes down again.
              _library.backupDeletes.add(e.value);
            } else {
              _library.backedUp.putIfAbsent(e.key, () => e.value);
            }
          }
        }
        await _vault.writeState(_library.toJson());
        return r;
      });
      if (r != null && r.full) _say((l) => l.backupFull);
    } on Object {
      // Offline or locked: the next opening tries again.
    }
  }

  Future<void> _openBackup() async {
    final library = _library;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        // Saves the library the page works on: this page may read its own
        // again while a backup or restore runs (the notification question
        // brings the app back to the front), and saving that one instead
        // would lose what the job noted.
        builder: (_) => VaultBackupPage(
          library: library,
          items: _items,
          save: () => _save(library),
        ),
      ),
    );
    // A restore adds items the list here doesn't have yet.
    if (mounted) await _refresh();
  }

  /// What is in the account but not on this phone (from another phone).
  Future<void> _openAccountMedia() async {
    // As on the backup screen: the page saves the library it works on.
    final library = _library;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) =>
            AccountMediaPage(library: library, save: () => _save(library)),
      ),
    );
    // Items kept from the account are new here.
    if (mounted) await _refresh();
  }

  Future<void> _save([VaultLibrary? library]) async {
    try {
      await _vault.writeState((library ?? _library).toJson());
    } on Object {
      _say((l) => l.vaultFailed);
    }
  }

  void _say(String Function(AppL10n l) text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(text(context.l10n))));
  }

  String? _authMessage(VaultAuth r) {
    final l = context.l10n;
    return switch (r) {
      VaultAuth.ok || VaultAuth.cancelled => null,
      VaultAuth.noScreenLock => l.vaultNeedsScreenLock,
      VaultAuth.keyLost => l.vaultKeyLost,
      VaultAuth.failed => l.vaultFailed,
    };
  }

  Future<void> _unlock() async {
    final r = await _vault.unlock();
    if (!mounted) return;
    setState(() => _message = _authMessage(r));
    if (r == VaultAuth.ok) await _refresh();
  }

  Future<void> _setUp() async {
    final r = await _vault.setUp();
    if (!mounted) return;
    setState(() => _message = _authMessage(r));
    if (r == VaultAuth.ok) await _refresh();
  }

  Future<void> _lock() async {
    await _vault.lock();
    await _refresh();
  }

  Future<void> _import() async {
    setState(() => _busy = true);
    try {
      final n = await _vault.importMedia();
      if (n > 0) _say((l) => l.vaultImported(n));
      // The vault locked while the photo picker was open: ask to open it again.
      if (!(await _vault.status()).unlocked) {
        await _unlock();
      } else {
        await _load();
      }
    } on Object {
      _say((l) => l.vaultFailed);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _setPrivateMode(bool on) async {
    await _vault.setPrivateMode(on);
    await _refresh();
  }

  // ---- what is shown ---------------------------------------------------

  VaultSearch get _search => _searching
      ? VaultSearch(text: _searchText.text, range: _searchRange)
      : const VaultSearch();

  /// Month names to accept in a search: the app's language and English.
  Map<int, List<String>> _monthNames() {
    final locale = Localizations.localeOf(context).toLanguageTag();
    final names = <int, List<String>>{};
    for (var m = 1; m <= 12; m++) {
      final day = DateTime(2024, m);
      names[m] = {
        for (final tag in {locale, 'en'})
          for (final pattern in const ['MMMM', 'LLLL'])
            _formatMonth(pattern, tag, day),
      }.whereType<String>().toList();
    }
    return names;
  }

  static String? _formatMonth(String pattern, String locale, DateTime day) {
    try {
      return DateFormat(pattern, locale).format(day).toLowerCase();
    } on Object {
      // That language's dates aren't loaded.
      return null;
    }
  }

  List<VaultItem> get _visible {
    final search = _search;
    final list = _filtered;
    return search.isEmpty
        ? list
        : search.apply(list, _library, months: _monthNames());
  }

  Future<void> _pickSearchDates() async {
    final now = _now();
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime(2000),
      lastDate: DateTime(now.year, now.month, now.day),
      initialDateRange: _searchRange,
    );
    if (picked != null && mounted) {
      setState(() {
        _searchRange = picked;
        _selected.clear();
      });
    }
  }

  List<VaultItem> get _filtered {
    return switch (_filter) {
      VaultFilter.all => [
        for (final i in _items)
          if (!_library.isTrashed(i.id)) i,
      ],
      VaultFilter.favourites => [
        for (final i in _items)
          if (!_library.isTrashed(i.id) && _library.favourites.contains(i.id))
            i,
      ],
      VaultFilter.albums =>
        _album == null
            ? const []
            : [
                for (final i in _items)
                  if (!_library.isTrashed(i.id) &&
                      (_library.albums[_album]?.items.contains(i.id) ?? false))
                    i,
              ],
      VaultFilter.trash => [
        for (final i in _items)
          if (_library.isTrashed(i.id)) i,
      ],
    };
  }

  Future<Uint8List?> _thumb(String id) =>
      _thumbs.putIfAbsent(id, () => _vault.thumb(id).catchError((_) => null));

  // ---- actions on items ------------------------------------------------

  Future<void> _favourite(List<String> ids) async {
    setState(() => _library.toggleFavourite(ids));
    await _save();
  }

  Future<void> _trash(List<String> ids) async {
    setState(() {
      _library.trash(ids, _now());
      _selected.clear();
    });
    await _save();
  }

  Future<void> _restore(List<String> ids) async {
    setState(() {
      _library.restore(ids);
      _selected.clear();
    });
    await _save();
  }

  /// While the vault is open: items whose time is up go, and a photo of
  /// theirs open on top closes.
  Future<void> _sweepDisappeared() async {
    final ids = _library.disappeared(_now());
    if (ids.isEmpty || !mounted) return;
    final locks = _lockCount;
    try {
      await _vault.delete(ids);
    } on Object {
      return;
    }
    if (!mounted || locks != _lockCount) return;
    final me = ModalRoute.of(context);
    if (me != null && !me.isCurrent) {
      Navigator.of(context).popUntil((r) => r == me);
    }
    setState(() {
      _library.forget(ids);
      _items = [
        for (final i in _items)
          if (!ids.contains(i.id)) i,
      ];
      _selected.removeAll(ids);
    });
    await _save();
    unawaited(_autoBackup());
  }

  /// Works out what is in each photo not looked at yet (newest first), one
  /// at a time and only while the vault stays open, for search. Saved in the
  /// sealed library every few photos.
  Future<void> _describeContent() async {
    if (_describing) return;
    _describing = true;
    final locks = _lockCount;
    var unsaved = 0;
    bool open() => mounted && locks == _lockCount && _library.contentSearch;
    try {
      while (open()) {
        final next = [
          for (final i in _items)
            if (!_library.content.containsKey(i.id)) i,
        ].firstOrNull;
        if (next == null) break;
        VaultContent? found;
        try {
          found = await _vault.describe(next.id, isVideo: next.isVideo);
          // The picture model is not in this build.
          if (found == null) break;
        } on VaultLockedException {
          break;
        } on Object {
          // A photo that can't be opened is not tried again each time.
          found = const VaultContent();
        }
        if (!open() || !_items.any((i) => i.id == next.id)) continue;
        _library.content[next.id] = found;
        // Results being shown take in what was just found.
        if (_searching) setState(() {});
        if (++unsaved >= 10) {
          unsaved = 0;
          await _save();
        }
      }
    } finally {
      _describing = false;
      if (unsaved > 0 && mounted && locks == _lockCount) await _save();
    }
  }

  Future<void> _setContentSearch(bool on) async {
    setState(() => _library.setContentSearch(on));
    await _save();
    _say((l) => on ? l.vaultContentSearchOn : l.vaultContentSearchOff);
    if (on) unawaited(_describeContent());
  }

  String _disappearLabel(AppL10n l, Duration d) => switch (d.inHours) {
    1 => l.vaultDisappearHour,
    24 => l.vaultDisappearDay,
    168 => l.vaultDisappearWeek,
    _ => l.vaultDisappearMonth,
  };

  /// The rule for new photos and videos: off or one of the choices.
  Future<void> _pickDisappearRule() async {
    final l = context.l10n;
    final current = _library.disappearAfter;
    final picked = await showDialog<Duration?>(
      context: context,
      builder: (c) => SimpleDialog(
        title: Text(l.vaultDisappearTitle),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 0, 24, 8),
            child: Text(l.vaultDisappearBody),
          ),
          RadioGroup<int>(
            groupValue: current?.inMinutes ?? 0,
            onChanged: (v) => Navigator.pop(
              c,
              v == null || v == 0 ? Duration.zero : Duration(minutes: v),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                RadioListTile<int>(value: 0, title: Text(l.vaultDisappearOff)),
                for (final d in VaultLibrary.disappearChoices)
                  RadioListTile<int>(
                    value: d.inMinutes,
                    title: Text(_disappearLabel(l, d)),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
    if (picked == null || !mounted) return;
    final after = picked == Duration.zero ? null : picked;
    if (after == current) return;
    setState(() => _library.setDisappearRule(after, _now()));
    await _save();
    if (after != null) _say((l) => l.vaultDisappearOn);
  }

  /// Puts the selected items on a timer, or takes them off it.
  Future<void> _disappearSelected(List<String> ids) async {
    final l = context.l10n;
    final anyOn = ids.any(_library.isDisappearing);
    final picked = await showDialog<Duration>(
      context: context,
      builder: (c) => SimpleDialog(
        title: Text(l.vaultDisappearAfter),
        children: [
          for (final d in VaultLibrary.disappearChoices)
            SimpleDialogOption(
              onPressed: () => Navigator.pop(c, d),
              child: Text(_disappearLabel(l, d)),
            ),
          if (anyOn)
            SimpleDialogOption(
              onPressed: () => Navigator.pop(c, Duration.zero),
              child: Text(l.vaultDisappearKeep),
            ),
        ],
      ),
    );
    if (picked == null || !mounted) return;
    setState(() {
      if (picked == Duration.zero) {
        _library.keep(ids);
      } else {
        _library.disappear(ids, _now().add(picked));
      }
      _selected.clear();
    });
    await _save();
  }

  Future<void> _deleteForever(List<String> ids) async {
    final l = context.l10n;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        content: Text(l.vaultDeleteForeverConfirm(ids.length)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(c, false),
            child: Text(l.vaultCancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(c, true),
            child: Text(l.vaultDelete),
          ),
        ],
      ),
    );
    if (ok != true) return;
    await _vault.delete(ids);
    setState(() {
      _library.forget(ids);
      _items = [
        for (final i in _items)
          if (!ids.contains(i.id)) i,
      ];
      _selected.clear();
    });
    await _save();
    // Their backed-up copies go too (queued by forget).
    unawaited(_autoBackup());
  }

  Future<void> _saveToGallery(List<String> ids) async {
    setState(() => _busy = true);
    try {
      final n = await _vault.saveToGallery(ids);
      _say((l) => l.vaultSaved(n));
    } on Object {
      _say((l) => l.vaultFailed);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _share(List<String> ids) async {
    setState(() => _busy = true);
    try {
      await _vault.share(ids);
    } on Object {
      _say((l) => l.vaultFailed);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// Opens the photo editor. The edited copy shows up in the vault when this page comes back.
  Future<void> _edit(String id) async {
    try {
      await _vault.editPhoto(id);
    } on Object {
      _say((l) => l.vaultFailed);
    }
  }

  /// Reads the text in a photo (on the phone) and shows it to copy.
  Future<void> _readText(String id) async {
    setState(() => _busy = true);
    final locks = _lockCount;
    String? text;
    try {
      text = await _vault.readText(id);
      // Reading can take a while. If the vault locked meanwhile (the app was left), the text
      // must not show up over the locked vault.
      if (locks != _lockCount || !(await _vault.status()).unlocked) return;
    } on Object {
      _say((l) => l.vaultFailed);
      return;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
    if (!mounted) return;
    final l = context.l10n;
    if (text == null || text.trim().isEmpty) {
      _say((l) => l.vaultNoText);
      return;
    }
    final found = text;
    await showDialog<void>(
      context: context,
      builder: (c) => AlertDialog(
        title: Text(l.vaultTextTitle),
        content: SingleChildScrollView(child: SelectableText(found)),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(c).pop(),
            child: Text(MaterialLocalizations.of(c).closeButtonLabel),
          ),
          TextButton(
            onPressed: () {
              Clipboard.setData(ClipboardData(text: found));
              Navigator.of(c).pop();
              _say((l) => l.scanHistoryCopied);
            },
            child: Text(l.vaultCopy),
          ),
        ],
      ),
    );
  }

  Future<void> _addToAlbum(List<String> ids) async {
    final l = context.l10n;
    final albumId = await showModalBottomSheet<String>(
      context: context,
      builder: (c) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            ListTile(
              leading: const Icon(Icons.add),
              title: Text(l.vaultNewAlbum),
              onTap: () => Navigator.pop(c, ''),
            ),
            for (final a in _library.albums.values)
              ListTile(
                leading: const Icon(Icons.photo_album_outlined),
                title: Text(a.name),
                onTap: () => Navigator.pop(c, a.id),
              ),
          ],
        ),
      ),
    );
    if (albumId == null || !mounted) return;
    var target = albumId;
    if (target.isEmpty) {
      final name = await _askAlbumName();
      if (name == null) return;
      target = _newId();
      _library.addAlbum(name, target);
    }
    setState(() {
      _library.addToAlbum(target, ids);
      _selected.clear();
    });
    await _save();
  }

  Future<String?> _askAlbumName() async {
    final l = context.l10n;
    final controller = TextEditingController();
    final name = await showDialog<String>(
      context: context,
      builder: (c) => AlertDialog(
        title: Text(l.vaultNewAlbum),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: InputDecoration(labelText: l.vaultAlbumName),
          onSubmitted: (v) => Navigator.pop(c, v),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(c),
            child: Text(l.vaultCancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(c, controller.text),
            child: Text(l.vaultCreate),
          ),
        ],
      ),
    );
    controller.dispose();
    final trimmed = name?.trim();
    return trimmed == null || trimmed.isEmpty ? null : trimmed;
  }

  String _newId() {
    final r = Random.secure();
    return List.generate(16, (_) => r.nextInt(16).toRadixString(16)).join();
  }

  Future<void> _open(VaultItem item) async {
    final list = _visible;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => VaultViewer(
          items: list,
          initial: list.indexOf(item),
          library: _library,
          inTrash: _filter == VaultFilter.trash,
          thumb: _thumb,
          onFavourite: (id) => _favourite([id]),
          onTrash: (id) => _trash([id]),
          onRestore: (id) => _restore([id]),
          onDeleteForever: (id) => _deleteForever([id]),
          onSave: (id) => _saveToGallery([id]),
          onShare: (id) => _share([id]),
          onReadText: _readText,
          onEdit: _edit,
        ),
      ),
    );
    if (mounted) setState(() {});
  }

  // ---- screens -----------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final status = _status;
    final unlocked = status?.unlocked == true;
    final selecting = _selected.isNotEmpty;
    final offline = ref.watch(offlineBuildProvider);

    return PopScope(
      canPop: !selecting,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && selecting) setState(_selected.clear);
      },
      child: Scaffold(
        appBar: AppBar(
          leading: selecting
              ? IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () => setState(_selected.clear),
                )
              : IconButton(
                  icon: const BackButtonIcon(),
                  tooltip: widget.fromCamera ? l.backToCamera : null,
                  onPressed: () => widget.fromCamera
                      ? ref.read(cameraBridgeProvider).close()
                      : Navigator.of(context).maybePop(),
                ),
          title: Text(
            selecting ? l.vaultSelected(_selected.length) : l.vaultTitle,
          ),
          actions: [
            if (unlocked && selecting) ..._selectionActions(l),
            if (unlocked && !selecting) ...[
              IconButton(
                icon: const Icon(Icons.add_photo_alternate_outlined),
                tooltip: l.vaultImport,
                onPressed: _busy ? null : _import,
              ),
              // The Offline build has no account to back up to.
              if (!offline)
                IconButton(
                  icon: const Icon(Icons.cloud_outlined),
                  tooltip: l.vaultBackup,
                  onPressed: _openBackup,
                ),
              IconButton(
                icon: const Icon(Icons.lock_outline),
                tooltip: l.vaultLock,
                onPressed: _lock,
              ),
              PopupMenuButton<int>(
                tooltip: l.vaultMore,
                onSelected: (v) => switch (v) {
                  0 => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => const ScanHistoryPage(),
                    ),
                  ),
                  1 => _setQuickHide(!_quickHide),
                  2 => _setLocation(!_location),
                  4 => _pickDisappearRule(),
                  5 => _setContentSearch(!_library.contentSearch),
                  _ => _openAccountMedia(),
                },
                itemBuilder: (_) => [
                  PopupMenuItem(value: 0, child: Text(l.scanHistoryTitle)),
                  if (!offline)
                    PopupMenuItem(value: 3, child: Text(l.accountMediaTitle)),
                  CheckedPopupMenuItem(
                    value: 1,
                    checked: _quickHide,
                    child: Text(l.vaultQuickHide),
                  ),
                  CheckedPopupMenuItem(
                    value: 2,
                    checked: _location,
                    child: Text(l.vaultLocation),
                  ),
                  CheckedPopupMenuItem(
                    value: 4,
                    checked: _library.disappearAfter != null,
                    child: Text(l.vaultDisappearTitle),
                  ),
                  CheckedPopupMenuItem(
                    value: 5,
                    checked: _library.contentSearch,
                    child: Text(l.vaultContentSearch),
                  ),
                ],
              ),
            ],
          ],
          bottom: _busy
              ? const PreferredSize(
                  preferredSize: Size.fromHeight(4),
                  child: LinearProgressIndicator(),
                )
              : null,
        ),
        body: status == null
            ? const Center(child: CircularProgressIndicator())
            : !status.setUp
            ? _setUpView(l, status)
            : !unlocked
            ? _lockedView(l)
            : _unlockedView(l, status),
      ),
    );
  }

  List<Widget> _selectionActions(AppL10n l) {
    final ids = _selected.toList();
    if (_filter == VaultFilter.trash) {
      return [
        IconButton(
          icon: const Icon(Icons.restore),
          tooltip: l.vaultRestore,
          onPressed: () => _restore(ids),
        ),
        IconButton(
          icon: const Icon(Icons.delete_forever_outlined),
          tooltip: l.vaultDeleteForever,
          onPressed: () => _deleteForever(ids),
        ),
      ];
    }
    return [
      IconButton(
        icon: const Icon(Icons.star_outline),
        tooltip: l.vaultFavourite,
        onPressed: () => _favourite(ids),
      ),
      IconButton(
        icon: const Icon(Icons.share_outlined),
        tooltip: l.vaultShare,
        onPressed: () => _share(ids),
      ),
      IconButton(
        icon: const Icon(Icons.delete_outline),
        tooltip: l.vaultMoveToTrash,
        onPressed: () => _trash(ids),
      ),
      PopupMenuButton<int>(
        onSelected: (v) => switch (v) {
          0 => _addToAlbum(ids),
          1 => _saveToGallery(ids),
          3 => _disappearSelected(ids),
          _ => _removeFromAlbum(ids),
        },
        itemBuilder: (_) => [
          PopupMenuItem(value: 0, child: Text(l.vaultAddToAlbum)),
          PopupMenuItem(value: 1, child: Text(l.vaultSaveToGallery)),
          PopupMenuItem(value: 3, child: Text(l.vaultDisappearAfter)),
          if (_filter == VaultFilter.albums && _album != null)
            PopupMenuItem(value: 2, child: Text(l.vaultRemoveFromAlbum)),
        ],
      ),
    ];
  }

  Future<void> _removeFromAlbum(List<String> ids) async {
    final album = _album;
    if (album == null) return;
    setState(() {
      _library.removeFromAlbum(album, ids);
      _selected.clear();
    });
    await _save();
  }

  Widget _centered(List<Widget> children) => Center(
    child: SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(mainAxisSize: MainAxisSize.min, children: children),
    ),
  );

  Widget _setUpView(AppL10n l, VaultStatus status) {
    final theme = Theme.of(context);
    return _centered([
      Icon(
        Icons.enhanced_encryption_outlined,
        size: 64,
        color: theme.colorScheme.primary,
      ),
      const SizedBox(height: 16),
      Text(
        l.vaultSetUpTitle,
        style: theme.textTheme.titleLarge,
        textAlign: TextAlign.center,
      ),
      const SizedBox(height: 12),
      Text(l.vaultSetUpBody, textAlign: TextAlign.center),
      const SizedBox(height: 24),
      if (!status.screenLock)
        Text(
          l.vaultNeedsScreenLock,
          textAlign: TextAlign.center,
          style: TextStyle(color: theme.colorScheme.error),
        )
      else
        FilledButton.icon(
          icon: const Icon(Icons.lock_outline),
          label: Text(l.vaultSetUp),
          onPressed: _setUp,
        ),
      if (_message != null) ...[
        const SizedBox(height: 16),
        Text(
          _message!,
          textAlign: TextAlign.center,
          style: TextStyle(color: theme.colorScheme.error),
        ),
      ],
    ]);
  }

  Widget _lockedView(AppL10n l) {
    final theme = Theme.of(context);
    return _centered([
      Icon(Icons.lock_outline, size: 64, color: theme.colorScheme.primary),
      const SizedBox(height: 16),
      Text(
        l.vaultLocked,
        style: theme.textTheme.titleLarge,
        textAlign: TextAlign.center,
      ),
      const SizedBox(height: 12),
      Text(l.vaultLockedBody, textAlign: TextAlign.center),
      const SizedBox(height: 24),
      FilledButton.icon(
        icon: const Icon(Icons.fingerprint),
        label: Text(l.vaultUnlock),
        onPressed: _unlock,
      ),
      if (_message != null) ...[
        const SizedBox(height: 16),
        Text(
          _message!,
          textAlign: TextAlign.center,
          style: TextStyle(color: theme.colorScheme.error),
        ),
      ],
    ]);
  }

  Widget _unlockedView(AppL10n l, VaultStatus status) {
    final visible = _visible;
    final showAlbumList = _filter == VaultFilter.albums && _album == null;
    return Column(
      children: [
        SwitchListTile(
          secondary: const Icon(Icons.enhanced_encryption_outlined),
          title: Text(l.vaultPrivateMode),
          subtitle: Text(
            status.privateMode ? l.vaultPrivateModeOn : l.vaultPrivateModeOff,
          ),
          value: status.privateMode,
          onChanged: _setPrivateMode,
        ),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Row(
            children: [
              IconButton(
                icon: Icon(_searching ? Icons.search_off : Icons.search),
                tooltip: _searching ? l.vaultSearchClose : l.vaultSearch,
                onPressed: () => setState(() {
                  _searching = !_searching;
                  _searchText.clear();
                  _searchRange = null;
                  _selected.clear();
                }),
              ),
              for (final f in VaultFilter.values)
                Padding(
                  padding: const EdgeInsetsDirectional.only(end: 8),
                  child: ChoiceChip(
                    label: Text(switch (f) {
                      VaultFilter.all => l.vaultAll,
                      VaultFilter.favourites => l.vaultFavourites,
                      VaultFilter.albums => l.vaultAlbums,
                      VaultFilter.trash => l.vaultTrash,
                    }),
                    selected: _filter == f,
                    onSelected: (_) => setState(() {
                      _filter = f;
                      _album = null;
                      _selected.clear();
                    }),
                  ),
                ),
            ],
          ),
        ),
        if (_searching) _searchBar(l),
        if (_filter == VaultFilter.trash)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Text(
              l.vaultTrashNote,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
        if (_filter == VaultFilter.albums && _album != null)
          ListTile(
            leading: IconButton(
              icon: const BackButtonIcon(),
              onPressed: () => setState(() {
                _album = null;
                _selected.clear();
              }),
            ),
            title: Text(_library.albums[_album]?.name ?? ''),
            trailing: IconButton(
              icon: const Icon(Icons.delete_outline),
              tooltip: l.vaultDeleteAlbum,
              onPressed: () async {
                final id = _album!;
                setState(() {
                  _library.deleteAlbum(id);
                  _album = null;
                });
                await _save();
              },
            ),
          ),
        Expanded(
          child: showAlbumList
              ? _albumList(l)
              : visible.isEmpty
              ? _centered([
                  Text(
                    !_search.isEmpty
                        ? l.vaultSearchEmpty
                        : _filter == VaultFilter.trash
                        ? l.vaultTrashEmpty
                        : l.vaultEmpty,
                    textAlign: TextAlign.center,
                  ),
                ])
              : RefreshIndicator(onRefresh: _load, child: _grid(visible)),
        ),
      ],
    );
  }

  Widget _searchBar(AppL10n l) {
    final range = _searchRange;
    final dates = MaterialLocalizations.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 8, 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: TextField(
                  key: const ValueKey('vault_search'),
                  controller: _searchText,
                  autofocus: true,
                  textInputAction: TextInputAction.search,
                  decoration: InputDecoration(
                    hintText: l.vaultSearchHint,
                    isDense: true,
                  ),
                  onChanged: (_) => setState(_selected.clear),
                ),
              ),
              IconButton(
                icon: const Icon(Icons.date_range_outlined),
                tooltip: l.vaultSearchDates,
                onPressed: _pickSearchDates,
              ),
            ],
          ),
          if (range != null)
            InputChip(
              label: Text(
                '${dates.formatShortDate(range.start)} - '
                '${dates.formatShortDate(range.end)}',
              ),
              onDeleted: () => setState(() => _searchRange = null),
            ),
        ],
      ),
    );
  }

  Widget _albumList(AppL10n l) {
    final search = _search;
    final albums = [
      for (final a in _library.albums.values)
        if (search.text.trim().isEmpty || search.matchesAlbum(a.name)) a,
    ];
    return ListView(
      children: [
        ListTile(
          leading: const Icon(Icons.add),
          title: Text(l.vaultNewAlbum),
          onTap: () async {
            final name = await _askAlbumName();
            if (name == null) return;
            setState(() => _library.addAlbum(name, _newId()));
            await _save();
          },
        ),
        if (albums.isEmpty)
          Padding(
            padding: const EdgeInsets.all(24),
            child: Text(
              search.text.trim().isEmpty ? l.vaultNoAlbums : l.vaultSearchEmpty,
              textAlign: TextAlign.center,
            ),
          ),
        for (final a in albums)
          ListTile(
            leading: const Icon(Icons.photo_album_outlined),
            title: Text(a.name),
            subtitle: Text(
              l.vaultAlbumCount(
                a.items.where((id) => !_library.isTrashed(id)).length,
              ),
            ),
            onTap: () => setState(() => _album = a.id),
          ),
      ],
    );
  }

  Widget _grid(List<VaultItem> items) {
    return GridView.builder(
      padding: const EdgeInsets.all(2),
      gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
        maxCrossAxisExtent: 128,
        mainAxisSpacing: 2,
        crossAxisSpacing: 2,
      ),
      itemCount: items.length,
      itemBuilder: (context, i) {
        final item = items[i];
        final selected = _selected.contains(item.id);
        return GestureDetector(
          key: ValueKey(item.id),
          onTap: () => _selected.isNotEmpty
              ? setState(
                  () => selected
                      ? _selected.remove(item.id)
                      : _selected.add(item.id),
                )
              : _open(item),
          onLongPress: () => setState(() => _selected.add(item.id)),
          child: Stack(
            fit: StackFit.expand,
            children: [
              VaultThumb(future: _thumb(item.id)),
              if (item.isVideo)
                PositionedDirectional(
                  bottom: 4,
                  end: 4,
                  child: _VideoBadge(duration: item.duration),
                ),
              if (_library.isDisappearing(item.id))
                PositionedDirectional(
                  top: 4,
                  end: 4,
                  child: Icon(
                    Icons.timer_outlined,
                    size: 18,
                    color: Colors.white,
                    semanticLabel: context.l10n.vaultDisappearBadge,
                  ),
                ),
              if (_library.favourites.contains(item.id))
                const PositionedDirectional(
                  top: 4,
                  start: 4,
                  child: Icon(Icons.star, size: 18, color: Colors.white),
                ),
              if (selected)
                Container(
                  color: Colors.black38,
                  alignment: Alignment.center,
                  child: const Icon(Icons.check_circle, color: Colors.white),
                ),
            ],
          ),
        );
      },
    );
  }
}

/// A sealed thumbnail, opened on demand.
class VaultThumb extends StatelessWidget {
  const VaultThumb({super.key, required this.future, this.fit = BoxFit.cover});

  final Future<Uint8List?> future;
  final BoxFit fit;

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme.surfaceContainerHighest;
    return FutureBuilder<Uint8List?>(
      future: future,
      builder: (context, snap) {
        final bytes = snap.data;
        if (bytes == null) return ColoredBox(color: color);
        return Image.memory(bytes, fit: fit, gaplessPlayback: true);
      },
    );
  }
}

class _VideoBadge extends StatelessWidget {
  const _VideoBadge({this.duration});

  final Duration? duration;

  @override
  Widget build(BuildContext context) {
    final d = duration;
    final text = d == null
        ? ''
        : '${d.inMinutes}:${(d.inSeconds % 60).toString().padLeft(2, '0')}';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
      decoration: BoxDecoration(
        color: Colors.black54,
        borderRadius: BorderRadius.circular(4),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.play_arrow, size: 14, color: Colors.white),
          if (text.isNotEmpty)
            Text(
              text,
              style: const TextStyle(color: Colors.white, fontSize: 11),
            ),
        ],
      ),
    );
  }
}
