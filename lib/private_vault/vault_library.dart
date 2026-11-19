import 'dart:convert';

/// Albums, favourites and the trash of the Private vault. Kept by these
/// screens in one sealed file (the native side only stores it).
class VaultLibrary {
  VaultLibrary({
    Map<String, VaultAlbum>? albums,
    Set<String>? favourites,
    Map<String, DateTime>? trashed,
    BackupSettings? backup,
    Map<String, String>? backedUp,
    Set<String>? backupDeletes,
  }) : albums = albums ?? {},
       favourites = favourites ?? {},
       trashed = trashed ?? {},
       backup = backup ?? BackupSettings(),
       backedUp = backedUp ?? {},
       backupDeletes = backupDeletes ?? {};

  /// Items stay in the trash this long, then are deleted for good.
  static const trashDays = 30;

  final Map<String, VaultAlbum> albums;
  final Set<String> favourites;

  /// When each item was put in the trash.
  final Map<String, DateTime> trashed;

  /// What is backed up to the account (see VaultBackup).
  BackupSettings backup;

  /// Vault item id on this phone to its backed-up copy in the account.
  final Map<String, String> backedUp;

  /// Backed-up copies of items deleted for good here, still to be deleted
  /// from the account (it may not have been reachable).
  final Set<String> backupDeletes;

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
        backup: BackupSettings.fromJson(m['backup']),
        backedUp: {
          for (final e
              in (m['backedUp'] as Map<String, Object?>? ?? const {}).entries)
            if (e.value is String) e.key: e.value! as String,
        },
        backupDeletes: {
          for (final d in (m['backupDeletes'] as List<Object?>? ?? const []))
            if (d is String) d,
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
    'backup': backup.toJson(),
    'backedUp': backedUp,
    'backupDeletes': backupDeletes.toList(),
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

  /// Takes items deleted for good out of every list. Their backed-up copies
  /// are queued for deletion from the account.
  void forget(Iterable<String> ids) {
    final gone = ids.toSet();
    favourites.removeAll(gone);
    for (final id in gone) {
      final copy = backedUp.remove(id);
      if (copy != null) backupDeletes.add(copy);
    }
    trashed.removeWhere((id, _) => gone.contains(id));
    for (final a in albums.values) {
      a.items.removeWhere(gone.contains);
    }
  }

  /// Drops ids that no longer exist in the vault. Their backed-up copies are
  /// kept, so a restore can bring them back.
  void keepOnly(Set<String> existing) {
    backedUp.removeWhere((id, _) => !existing.contains(id));
    forget(
      {
        ...favourites,
        ...trashed.keys,
        for (final a in albums.values) ...a.items,
      }.difference(existing),
    );
  }

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

  void deleteAlbum(String albumId) {
    albums.remove(albumId);
    backup.albums.remove(albumId);
  }

  /// Ids of the albums [id] is in.
  List<String> albumsOf(String id) => [
    for (final a in albums.values)
      if (a.items.contains(id)) a.id,
  ];
}

/// The vault backup's settings. Off until turned on.
class BackupSettings {
  BackupSettings({
    this.on = false,
    this.everything = true,
    Set<String>? albums,
    this.videos = false,
  }) : albums = albums ?? {};

  factory BackupSettings.fromJson(Object? j) {
    if (j is! Map<String, Object?>) return BackupSettings();
    return BackupSettings(
      on: j['on'] == true,
      everything: j['everything'] != false,
      albums: {
        for (final a in (j['albums'] as List<Object?>? ?? const []))
          if (a is String) a,
      },
      videos: j['videos'] == true,
    );
  }

  bool on;

  /// Everything, or only the [albums] picked.
  bool everything;
  final Set<String> albums;

  /// Videos too (each up to the attachment size limit).
  bool videos;

  Map<String, Object?> toJson() => {
    'on': on,
    'everything': everything,
    'albums': albums.toList(),
    'videos': videos,
  };
}

class VaultAlbum {
  VaultAlbum({required this.id, required this.name, required this.items});

  final String id;
  String name;
  final List<String> items;
}
