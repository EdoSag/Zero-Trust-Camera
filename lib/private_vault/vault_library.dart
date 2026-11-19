import 'dart:convert';

/// Albums, favourites and the trash of the Private vault. Kept by these
/// screens in one sealed file (the native side only stores it).
class VaultLibrary {
  VaultLibrary({
    Map<String, VaultAlbum>? albums,
    Set<String>? favourites,
    Map<String, DateTime>? trashed,
  }) : albums = albums ?? {},
       favourites = favourites ?? {},
       trashed = trashed ?? {};

  /// Items stay in the trash this long, then are deleted for good.
  static const trashDays = 30;

  final Map<String, VaultAlbum> albums;
  final Set<String> favourites;

  /// When each item was put in the trash.
  final Map<String, DateTime> trashed;

  factory VaultLibrary.fromJson(String? text) {
    if (text == null || text.isEmpty) return VaultLibrary();
    try {
      final m = jsonDecode(text) as Map<String, Object?>;
      return VaultLibrary(
        albums: {
          for (final a in (m['albums'] as List<Object?>? ?? const []))
            if (a is Map<String, Object?>)
              a['id']! as String: VaultAlbum(
                id: a['id']! as String,
                name: a['name'] as String? ?? '',
                items: [
                  for (final i in (a['items'] as List<Object?>? ?? const []))
                    i! as String,
                ],
              ),
        },
        favourites: {
          for (final f in (m['favourites'] as List<Object?>? ?? const []))
            f! as String,
        },
        trashed: {
          for (final e
              in (m['trashed'] as Map<String, Object?>? ?? const {}).entries)
            e.key: DateTime.fromMillisecondsSinceEpoch(
              (e.value! as num).toInt(),
            ),
        },
      );
    } on Object {
      return VaultLibrary();
    }
  }

  String toJson() => jsonEncode({
    'v': 1,
    'albums': [
      for (final a in albums.values)
        {'id': a.id, 'name': a.name, 'items': a.items},
    ],
    'favourites': favourites.toList(),
    'trashed': {
      for (final e in trashed.entries) e.key: e.value.millisecondsSinceEpoch,
    },
  });

  bool isTrashed(String id) => trashed.containsKey(id);

  void trash(Iterable<String> ids, DateTime now) {
    for (final id in ids) {
      trashed[id] = now;
    }
  }

  void restore(Iterable<String> ids) {
    for (final id in ids) {
      trashed.remove(id);
    }
  }

  /// Items whose time in the trash is up.
  List<String> expired(DateTime now) => [
    for (final e in trashed.entries)
      if (now.difference(e.value).inDays >= trashDays) e.key,
  ];

  /// Takes deleted items out of every list.
  void forget(Iterable<String> ids) {
    final gone = ids.toSet();
    favourites.removeAll(gone);
    trashed.removeWhere((id, _) => gone.contains(id));
    for (final a in albums.values) {
      a.items.removeWhere(gone.contains);
    }
  }

  /// Drops ids that no longer exist in the vault.
  void keepOnly(Set<String> existing) => forget(
    {
      ...favourites,
      ...trashed.keys,
      for (final a in albums.values) ...a.items,
    }.difference(existing),
  );

  void toggleFavourite(Iterable<String> ids) {
    final all = ids.every(favourites.contains);
    for (final id in ids) {
      all ? favourites.remove(id) : favourites.add(id);
    }
  }

  VaultAlbum addAlbum(String name, String id) =>
      albums[id] = VaultAlbum(id: id, name: name, items: []);

  void addToAlbum(String albumId, Iterable<String> ids) {
    final a = albums[albumId];
    if (a == null) return;
    for (final id in ids) {
      if (!a.items.contains(id)) a.items.add(id);
    }
  }

  void removeFromAlbum(String albumId, Iterable<String> ids) =>
      albums[albumId]?.items.removeWhere(ids.toSet().contains);

  void deleteAlbum(String albumId) => albums.remove(albumId);
}

class VaultAlbum {
  VaultAlbum({required this.id, required this.name, required this.items});

  final String id;
  String name;
  final List<String> items;
}
