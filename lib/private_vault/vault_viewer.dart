import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../l10n/l10n.dart';
import 'vault_backend.dart';
import 'vault_library.dart';
import 'vault_page.dart';

/// One photo or video at a time, swiped through. Photos are opened in
/// memory only; videos play in the native player, which reads the sealed
/// file directly.
class VaultViewer extends ConsumerStatefulWidget {
  const VaultViewer({
    super.key,
    required this.items,
    required this.initial,
    required this.library,
    required this.inTrash,
    required this.thumb,
    required this.onFavourite,
    required this.onTrash,
    required this.onRestore,
    required this.onDeleteForever,
    required this.onSave,
    required this.onShare,
  });

  final List<VaultItem> items;
  final int initial;
  final VaultLibrary library;
  final bool inTrash;
  final Future<Uint8List?> Function(String id) thumb;
  final Future<void> Function(String id) onFavourite;
  final Future<void> Function(String id) onTrash;
  final Future<void> Function(String id) onRestore;
  final Future<void> Function(String id) onDeleteForever;
  final Future<void> Function(String id) onSave;
  final Future<void> Function(String id) onShare;

  @override
  ConsumerState<VaultViewer> createState() => _VaultViewerState();
}

class _VaultViewerState extends ConsumerState<VaultViewer> {
  late final PageController _pages = PageController(
    initialPage: widget.initial,
  );
  late int _index = widget.initial;
  final Map<String, Future<Uint8List>> _photos = {};

  VaultItem get _item => widget.items[_index];

  @override
  void dispose() {
    _pages.dispose();
    super.dispose();
  }

  Future<Uint8List> _photo(String id) {
    // Only the photos next to the one shown stay in memory.
    final keep = {
      for (var i = _index - 1; i <= _index + 1; i++)
        if (i >= 0 && i < widget.items.length) widget.items[i].id,
    };
    _photos.removeWhere((k, _) => !keep.contains(k));
    return _photos.putIfAbsent(
      id,
      () => ref.read(vaultBackendProvider).photo(id),
    );
  }

  Future<void> _thenClose(Future<void> Function(String) action) async {
    await action(_item.id);
    if (mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final item = _item;
    final favourite = widget.library.favourites.contains(item.id);
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: Text(
          MaterialLocalizations.of(context).formatMediumDate(item.takenAt),
        ),
      ),
      body: PageView.builder(
        controller: _pages,
        itemCount: widget.items.length,
        onPageChanged: (i) => setState(() => _index = i),
        itemBuilder: (context, i) {
          final it = widget.items[i];
          if (it.isVideo) {
            return Stack(
              fit: StackFit.expand,
              children: [
                VaultThumb(future: widget.thumb(it.id), fit: BoxFit.contain),
                Center(
                  child: IconButton.filled(
                    iconSize: 48,
                    tooltip: l.vaultPlay,
                    icon: const Icon(Icons.play_arrow),
                    onPressed: () =>
                        ref.read(vaultBackendProvider).playVideo(it.id),
                  ),
                ),
              ],
            );
          }
          return FutureBuilder<Uint8List>(
            future: _photo(it.id),
            builder: (context, snap) {
              final bytes = snap.data;
              if (bytes == null) {
                return VaultThumb(
                  future: widget.thumb(it.id),
                  fit: BoxFit.contain,
                );
              }
              return InteractiveViewer(
                maxScale: 8,
                child: Center(
                  child: Image.memory(bytes, gaplessPlayback: true),
                ),
              );
            },
          );
        },
      ),
      bottomNavigationBar: BottomAppBar(
        color: Colors.black,
        child: IconTheme(
          data: const IconThemeData(color: Colors.white),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: widget.inTrash
                ? [
                    IconButton(
                      icon: const Icon(Icons.restore),
                      tooltip: l.vaultRestore,
                      onPressed: () => _thenClose(widget.onRestore),
                    ),
                    IconButton(
                      icon: const Icon(Icons.delete_forever_outlined),
                      tooltip: l.vaultDeleteForever,
                      onPressed: () => _thenClose(widget.onDeleteForever),
                    ),
                  ]
                : [
                    IconButton(
                      icon: Icon(favourite ? Icons.star : Icons.star_outline),
                      tooltip: l.vaultFavourite,
                      onPressed: () async {
                        await widget.onFavourite(item.id);
                        if (mounted) setState(() {});
                      },
                    ),
                    IconButton(
                      icon: const Icon(Icons.share_outlined),
                      tooltip: l.vaultShare,
                      onPressed: () => widget.onShare(item.id),
                    ),
                    IconButton(
                      icon: const Icon(Icons.download_outlined),
                      tooltip: l.vaultSaveToGallery,
                      onPressed: () => widget.onSave(item.id),
                    ),
                    IconButton(
                      icon: const Icon(Icons.delete_outline),
                      tooltip: l.vaultMoveToTrash,
                      onPressed: () => _thenClose(widget.onTrash),
                    ),
                  ],
          ),
        ),
      ),
    );
  }
}
