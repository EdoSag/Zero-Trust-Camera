import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:zero_trust_core/zero_trust_core.dart';

import '../l10n/l10n.dart';
import 'vault_backend.dart';
import 'vault_backup.dart';
import 'vault_library.dart';

/// Encrypted backup of the Private vault: on or off, everything or some
/// albums, videos or not, back up now, and restore on a new phone.
class VaultBackupPage extends ConsumerStatefulWidget {
  const VaultBackupPage({
    super.key,
    required this.library,
    required this.items,
    required this.save,
  });

  final VaultLibrary library;
  final List<VaultItem> items;

  /// Writes [library] (sealed) after a change.
  final Future<void> Function() save;

  @override
  ConsumerState<VaultBackupPage> createState() => _VaultBackupPageState();
}

class _VaultBackupPageState extends ConsumerState<VaultBackupPage> {
  (int, int)? _progress;
  String? _result;

  VaultLibrary get _lib => widget.library;
  BackupSettings get _s => _lib.backup;

  VaultBackup get _engine => VaultBackup(
    vault: ref.read(vaultBackendProvider),
    store: ref.read(backupStoreProvider),
    account: ref.read(backupAccountProvider) ?? '',
  );

  @override
  void initState() {
    super.initState();
    VaultBackup.running.addListener(_onRunning);
  }

  @override
  void dispose() {
    VaultBackup.running.removeListener(_onRunning);
    super.dispose();
  }

  void _onRunning() {
    if (mounted) setState(() {});
  }

  Future<void> _change(void Function() edit) async {
    setState(edit);
    await widget.save();
  }

  Future<void> _run(
    Future<BackupResult> Function(void Function(int, int) progress) job,
    String Function(AppL10n l, BackupResult r) say,
  ) async {
    setState(() {
      _result = null;
      _progress = null;
    });
    final l = context.l10n;
    String? text;
    try {
      // One at a time, the quiet backup on opening the vault included.
      text = await VaultBackup.exclusive(() async {
        try {
          final r = await job((done, total) {
            if (mounted) setState(() => _progress = (done, total));
          });
          return say(l, r);
        } finally {
          await widget.save();
        }
      });
    } on Object {
      text = l.vaultFailed;
    }
    if (!mounted) return;
    setState(() => _result = text);
  }

  String _summary(AppL10n l, BackupResult r) => [
    l.backupDone(r.done),
    if (r.tooLarge > 0) l.backupTooLarge(r.tooLarge),
    if (r.failed > 0) l.backupFailed(r.failed),
    if (r.full) l.backupFull,
  ].join('\n');

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final account = ref.watch(backupAccountProvider);
    final ready = account != null;
    final on = _lib.backupFor(account);
    final running = VaultBackup.running.value;
    final eligible = _engine.eligible(_lib, widget.items);
    final backedUp = eligible
        .where((i) => _lib.backedUp.containsKey(i.id))
        .length;
    return Scaffold(
      appBar: AppBar(title: Text(l.backupTitle)),
      body: ListView(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Text(l.backupIntro),
          ),
          if (!ready)
            ListTile(
              leading: const Icon(Icons.account_circle_outlined),
              title: Text(l.backupSignIn),
            )
          else ...[
            SwitchListTile(
              secondary: const Icon(Icons.cloud_upload_outlined),
              title: Text(l.backupOn),
              subtitle: on
                  ? Text(l.backupCount(backedUp, eligible.length))
                  : null,
              value: on,
              onChanged: running
                  ? null
                  : (v) => _change(() {
                      // Turning it on is for this account, and only this one.
                      if (v) _lib.useBackupAccount(account);
                      _s.on = v;
                    }),
            ),
            if (on) ...[
              RadioGroup<bool>(
                groupValue: _s.everything,
                onChanged: (v) => _change(() => _s.everything = v ?? true),
                child: Column(
                  children: [
                    RadioListTile<bool>(
                      value: true,
                      title: Text(l.backupEverything),
                    ),
                    RadioListTile<bool>(
                      value: false,
                      title: Text(l.backupAlbums),
                    ),
                  ],
                ),
              ),
              if (!_s.everything)
                for (final a in _lib.albums.values)
                  CheckboxListTile(
                    contentPadding: const EdgeInsetsDirectional.only(
                      start: 56,
                      end: 16,
                    ),
                    title: Text(a.name),
                    value: _s.albums.contains(a.id),
                    onChanged: (v) => _change(
                      () => v == true
                          ? _s.albums.add(a.id)
                          : _s.albums.remove(a.id),
                    ),
                  ),
              SwitchListTile(
                secondary: const Icon(Icons.videocam_outlined),
                title: Text(l.backupVideos),
                subtitle: Text(l.backupVideosSubtitle),
                value: _s.videos,
                onChanged: (v) => _change(() => _s.videos = v),
              ),
              ListTile(
                leading: const Icon(Icons.backup_outlined),
                title: Text(l.backupNow),
                enabled: !running,
                onTap: () => _run(
                  (p) => _engine.backUp(_lib, widget.items, progress: p),
                  _summary,
                ),
              ),
            ],
            ListTile(
              leading: const Icon(Icons.restore),
              title: Text(l.backupRestore),
              subtitle: Text(l.backupRestoreSubtitle),
              enabled: !running,
              onTap: () => _run(
                (p) => _engine.restore(_lib, progress: p),
                (l, r) => [
                  l.backupRestored(r.done),
                  if (r.failed > 0) l.backupFailed(r.failed),
                ].join('\n'),
              ),
            ),
            if (running)
              Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    LinearProgressIndicator(
                      value: _progress == null || _progress!.$2 == 0
                          ? null
                          : _progress!.$1 / _progress!.$2,
                    ),
                    if (_progress != null) ...[
                      const SizedBox(height: 8),
                      Text(l.backupProgress(_progress!.$1, _progress!.$2)),
                    ],
                  ],
                ),
              ),
            if (_result != null)
              Padding(padding: const EdgeInsets.all(16), child: Text(_result!)),
            const ZtStorageTile(),
          ],
        ],
      ),
    );
  }
}
