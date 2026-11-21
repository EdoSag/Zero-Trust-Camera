import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../l10n/l10n.dart';
import 'vault_backend.dart';
import 'vault_backup.dart';
import 'vault_library.dart';

/// What is backed up in the account but not on this phone: the way to see
/// the vault from a second phone without restoring all of it. A photo is
/// downloaded and opened in memory when tapped; "Keep on this phone" seals
/// it into the vault here.
class AccountMediaPage extends ConsumerStatefulWidget {
  const AccountMediaPage({
    super.key,
    required this.library,
    required this.save,
  });

  final VaultLibrary library;

  /// Writes [library] (sealed) after a change.
  final Future<void> Function() save;

  @override
  ConsumerState<AccountMediaPage> createState() => _AccountMediaPageState();
}

class _AccountMediaPageState extends ConsumerState<AccountMediaPage> {
  List<CameraMedia>? _media;
  bool _failed = false;
  final Map<String, Future<Uint8List?>> _thumbs = {};
  final Set<String> _keeping = {};

  late final BackupStore _store = ref.read(backupStoreProvider);

  VaultBackup? get _engine {
    final account = ref.read(backupAccountProvider);
    return account == null
        ? null
        : VaultBackup(
            vault: ref.read(vaultBackendProvider),
            store: _store,
            account: account,
          );
  }

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final engine = _engine;
    if (engine == null) return;
    try {
      final media = await engine.missing(widget.library);
      if (mounted) {
        setState(() {
          _media = media;
          _failed = false;
        });
      }
    } on Object {
      if (mounted) setState(() => _failed = true);
    }
  }

  Future<Uint8List?> _thumb(CameraMedia m) =>
      _thumbs.putIfAbsent(m.id, () async {
        final ref = m.thumb;
        if (ref == null) return null;
        try {
          return await _store.download(ref);
        } on Object {
          return null;
        }
      });

  void _say(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(text)));
  }

  /// Seals [m] into the vault on this phone. One backup or restore at a
  /// time, like everywhere else.
  Future<void> _keep(CameraMedia m) async {
    final engine = _engine;
    if (engine == null || _keeping.contains(m.id)) return;
    final l = context.l10n;
    setState(() => _keeping.add(m.id));
    try {
      final id = await VaultBackup.exclusive(() async {
        try {
          return await engine.keep(widget.library, m);
        } finally {
          await widget.save();
        }
      });
      if (id == null) {
        _say(l.accountMediaBusy);
      } else {
        if (mounted) setState(() => _media?.remove(m));
        _say(l.accountMediaKept);
      }
    } on Object {
      _say(l.vaultFailed);
    } finally {
      if (mounted) setState(() => _keeping.remove(m.id));
    }
  }

  Future<void> _open(CameraMedia m) async {
    final kept = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) =>
            _AccountMediaView(media: m, store: _store, thumb: _thumb(m)),
      ),
    );
    if (kept == true) await _keep(m);
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final media = _media;
    // The account locked (its idle lock) or changed: a photo open on top
    // closes, and the previews decrypted so far are dropped.
    ref.listen<String?>(backupAccountProvider, (before, now) {
      if (before == now) return;
      final me = ModalRoute.of(context);
      if (me != null && !me.isCurrent) {
        Navigator.of(context).popUntil((r) => r == me);
      }
      setState(() {
        _thumbs.clear();
        _media = null;
      });
      if (now != null) _load();
    });
    final ready = ref.watch(backupAccountProvider) != null;
    final Widget body;
    if (!ready) {
      body = _Centered(l.backupSignIn);
    } else if (_failed) {
      body = _Centered(l.accountMediaFailed);
    } else if (media == null) {
      body = const Center(child: CircularProgressIndicator());
    } else if (media.isEmpty) {
      body = _Centered(l.accountMediaEmpty);
    } else {
      body = Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: Text(l.accountMediaIntro),
          ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: _load,
              child: GridView.builder(
                padding: const EdgeInsets.all(2),
                gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                  maxCrossAxisExtent: 128,
                  mainAxisSpacing: 2,
                  crossAxisSpacing: 2,
                ),
                itemCount: media.length,
                itemBuilder: (context, i) {
                  final m = media[i];
                  return GestureDetector(
                    key: ValueKey('account_${m.id}'),
                    onTap: () => _open(m),
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        _Preview(future: _thumb(m), isVideo: m.isVideo),
                        if (m.isVideo)
                          const PositionedDirectional(
                            bottom: 4,
                            end: 4,
                            child: Icon(
                              Icons.play_arrow,
                              size: 18,
                              color: Colors.white,
                            ),
                          ),
                        const PositionedDirectional(
                          top: 4,
                          start: 4,
                          child: Icon(
                            Icons.cloud_outlined,
                            size: 16,
                            color: Colors.white,
                          ),
                        ),
                        if (_keeping.contains(m.id))
                          const ColoredBox(
                            color: Colors.black45,
                            child: Center(child: CircularProgressIndicator()),
                          ),
                      ],
                    ),
                  );
                },
              ),
            ),
          ),
        ],
      );
    }
    return Scaffold(
      appBar: AppBar(title: Text(l.accountMediaTitle)),
      body: body,
    );
  }
}

class _Centered extends StatelessWidget {
  const _Centered(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Text(text, textAlign: TextAlign.center),
    ),
  );
}

/// A backed-up item's small preview, or a plain tile when it has none.
class _Preview extends StatelessWidget {
  const _Preview({required this.future, required this.isVideo});

  final Future<Uint8List?> future;
  final bool isVideo;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return FutureBuilder<Uint8List?>(
      future: future,
      builder: (context, snap) {
        final bytes = snap.data;
        if (bytes == null) {
          return ColoredBox(
            color: scheme.surfaceContainerHighest,
            child: Icon(
              isVideo ? Icons.videocam_outlined : Icons.photo_outlined,
              color: scheme.onSurfaceVariant,
            ),
          );
        }
        return Image.memory(bytes, fit: BoxFit.cover, gaplessPlayback: true);
      },
    );
  }
}

/// One backed-up item, opened from the account. A photo is downloaded and
/// shown from memory; nothing is written to this phone unless "Keep on this
/// phone" is tapped (the route then pops with true).
class _AccountMediaView extends StatefulWidget {
  const _AccountMediaView({
    required this.media,
    required this.store,
    required this.thumb,
  });

  final CameraMedia media;
  final BackupStore store;
  final Future<Uint8List?> thumb;

  @override
  State<_AccountMediaView> createState() => _AccountMediaViewState();
}

class _AccountMediaViewState extends State<_AccountMediaView> {
  /// Photos Flutter can draw; a RAW photo or a video shows its preview.
  bool get _showable =>
      !widget.media.isVideo &&
      widget.media.parts.length <= 1 &&
      const {
        'image/jpeg',
        'image/png',
        'image/webp',
      }.contains(widget.media.mime);

  late final Future<Uint8List?> _full = _showable
      ? widget.store
            .download(widget.media.file)
            .then<Uint8List?>((b) => b)
            .catchError((Object _) => null)
      : widget.thumb;

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final m = widget.media;
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: Text(
          MaterialLocalizations.of(context).formatMediumDate(m.takenAt),
        ),
      ),
      body: Column(
        children: [
          Expanded(
            child: FutureBuilder<Uint8List?>(
              future: _full,
              builder: (context, snap) {
                if (snap.connectionState != ConnectionState.done) {
                  return const Center(child: CircularProgressIndicator());
                }
                final bytes = snap.data;
                if (bytes == null) {
                  return Center(
                    child: Icon(
                      m.isVideo
                          ? Icons.videocam_outlined
                          : Icons.photo_outlined,
                      size: 64,
                      color: Colors.white54,
                    ),
                  );
                }
                return InteractiveViewer(
                  child: Center(
                    child: Image.memory(bytes, fit: BoxFit.contain),
                  ),
                );
              },
            ),
          ),
          if (!_showable)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              child: Text(
                m.isVideo ? l.accountMediaVideoNote : l.accountMediaRawNote,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white70),
              ),
            ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  icon: const Icon(Icons.download_outlined),
                  label: Text(l.accountMediaKeep),
                  onPressed: () => Navigator.of(context).pop(true),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
