import 'dart:convert';

/// Albums, favourites, the trash and disappearing items of the Private vault. Kept by these
/// screens in one sealed file (the native side only stores it).
class VaultLibrary {
  VaultLibrary({
    Map<String, VaultAlbum>? albums,
    Set<String>? favourites,
    Map<String, DateTime>? trashed,
    BackupSettings? backup,
    Map<String, String>? backedUp,
    Set<String>? backupDeletes,
    Map<String, DateTime>? disappearAt,
    Set<String>? disappearKept,
    this.disappearAfter,
    this.disappearSince,
    Map<String, VaultContent>? content,
    this.contentSearch = true,
  }) : content = content ?? {},
       albums = albums ?? {},
       favourites = favourites ?? {},
       trashed = trashed ?? {},
       backup = backup ?? BackupSettings(),
       backedUp = backedUp ?? {},
       backupDeletes = backupDeletes ?? {},
       disappearAt = disappearAt ?? {},
       disappearKept = disappearKept ?? {};

  /// The choices for disappearing items, shortest first.
  static const disappearChoices = [
    Duration(hours: 1),
    Duration(days: 1),
    Duration(days: 7),
    Duration(days: 30),
  ];

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

  /// When each disappearing item deletes itself for good (not to the trash).
  /// Disappearing items are never backed up.
  final Map<String, DateTime> disappearAt;

  /// New photos and videos disappear this long after they were taken; null
  /// is off.
  Duration? disappearAfter;

  /// When [disappearAfter] was turned on: items taken before then are left
  /// alone.
  DateTime? disappearSince;

  /// Items taken under the rule that were kept (taken off the timer) by hand.
  final Set<String> disappearKept;

  /// What is in each item, worked out on the phone for search (see
  /// VaultContent). Kept only here, in the sealed library: never backed up.
  final Map<String, VaultContent> content;

  /// Whether the vault works out what is in photos for search. On by
  /// default; turning it off forgets what was worked out.
  bool contentSearch;

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
        disappearAt: {
          for (final e
              in (m['disappearAt'] as Map<String, Object?>? ?? const {})
                  .entries)
            if (e.value is num)
              e.key: DateTime.fromMillisecondsSinceEpoch(
                (e.value! as num).toInt(),
              ),
        },
        disappearKept: {
          for (final d in (m['disappearKept'] as List<Object?>? ?? const []))
            if (d is String) d,
        },
        disappearAfter: switch (m['disappearAfter']) {
          final num ms when ms > 0 => Duration(milliseconds: ms.toInt()),
          _ => null,
        },
        disappearSince: switch (m['disappearSince']) {
          final num ms => DateTime.fromMillisecondsSinceEpoch(ms.toInt()),
          _ => null,
        },
        content: {
          for (final e
              in (m['content'] as Map<String, Object?>? ?? const {}).entries)
            if (VaultContent.fromJson(e.value) case final c?) e.key: c,
        },
        contentSearch: m['contentSearch'] != false,
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
    'disappearAt': {
      for (final e in disappearAt.entries)
        e.key: e.value.millisecondsSinceEpoch,
    },
    'disappearKept': disappearKept.toList(),
    'disappearAfter': disappearAfter?.inMilliseconds,
    'disappearSince': disappearSince?.millisecondsSinceEpoch,
    'content': {for (final e in content.entries) e.key: e.value.toJson()},
    'contentSearch': contentSearch,
  });

  /// Turns working out what is in photos on or off. Off forgets it all.
  void setContentSearch(bool on) {
    contentSearch = on;
    if (!on) content.clear();
  }

  bool isDisappearing(String id) => disappearAt.containsKey(id);

  /// Turns the rule for new items on ([after]) or off (null). Items already
  /// on a timer keep it.
  void setDisappearRule(Duration? after, DateTime now) {
    disappearAfter = after;
    disappearSince = after == null ? null : now;
  }

  /// Puts [ids] on a timer: they delete themselves at [at]. A copy already
  /// backed up is queued for deletion from the account, since disappearing
  /// items never leave the phone.
  void disappear(Iterable<String> ids, DateTime at) {
    for (final id in ids) {
      disappearAt[id] = at;
      disappearKept.remove(id);
      final copy = backedUp.remove(id);
      if (copy != null) backupDeletes.add(copy);
    }
  }

  /// Takes [ids] off the timer, for good: the rule doesn't put them back.
  void keep(Iterable<String> ids) {
    for (final id in ids) {
      if (disappearAt.remove(id) != null) disappearKept.add(id);
    }
  }

  /// Puts items taken since the rule was turned on on its timer (counted
  /// from when each was taken). Returns whether anything changed.
  bool applyDisappearRule(Iterable<({String id, DateTime takenAt})> items) {
    final after = disappearAfter;
    final since = disappearSince;
    if (after == null || since == null) return false;
    var changed = false;
    for (final i in items) {
      if (i.takenAt.isBefore(since) ||
          disappearAt.containsKey(i.id) ||
          disappearKept.contains(i.id)) {
        continue;
      }
      disappearAt[i.id] = i.takenAt.add(after);
      changed = true;
    }
    return changed;
  }

  /// Items whose time is up.
  List<String> disappeared(DateTime now) => [
    for (final e in disappearAt.entries)
      if (!e.value.isAfter(now)) e.key,
  ];

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
    disappearAt.removeWhere((id, _) => gone.contains(id));
    disappearKept.removeAll(gone);
    content.removeWhere((id, _) => gone.contains(id));
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
        ...disappearAt.keys,
        ...disappearKept,
        ...content.keys,
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

  /// Whether backup is on for [account] (the signed-in user's id). On for
  /// another account counts as off: nothing may go to an account the owner
  /// didn't pick.
  bool backupFor(String? account) =>
      backup.on && account != null && backup.account == account;

  /// Points the backup at [account]. Coming from another account (or from
  /// none), backup is off until turned on again, and what this phone knew
  /// about the other account's copies is dropped: they stay there, untouched.
  void useBackupAccount(String account) {
    if (backup.account == account) return;
    backup
      ..on = false
      ..account = account;
    backedUp.clear();
    backupDeletes.clear();
  }

  /// Ids of the albums [id] is in.
  List<String> albumsOf(String id) => [
    for (final a in albums.values)
      if (a.items.contains(id)) a.id,
  ];
}

/// The vault backup's settings. Off until turned on, and on only for the
/// account it was turned on for.
class BackupSettings {
  BackupSettings({
    this.on = false,
    this.everything = true,
    Set<String>? albums,
    this.videos = false,
    this.wifiOnly = true,
    this.chargingOnly = false,
    this.spaceSaver = false,
    this.account,
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
      wifiOnly: j['wifiOnly'] != false,
      chargingOnly: j['chargingOnly'] == true,
      spaceSaver: j['spaceSaver'] == true,
      account: j['account'] as String?,
    );
  }

  bool on;

  /// The account (user id) the backup was turned on for. Backup never runs
  /// for any other account.
  String? account;

  /// Everything, or only the [albums] picked.
  bool everything;
  final Set<String> albums;

  /// Videos too (each up to the attachment size limit).
  bool videos;

  /// The backup that runs by itself waits for Wi-Fi (a network that isn't
  /// metered). "Back up now" always runs.
  bool wifiOnly;

  /// The backup that runs by itself waits for the charger.
  bool chargingOnly;

  /// Photos go up as smaller copies (the originals stay on the phone).
  /// Videos and RAW photos always go up as they are.
  bool spaceSaver;

  /// Whether the backup that runs by itself may run now.
  bool allows({required bool unmetered, required bool charging}) =>
      (!wifiOnly || unmetered) && (!chargingOnly || charging);

  Map<String, Object?> toJson() => {
    'on': on,
    'everything': everything,
    'albums': albums.toList(),
    'videos': videos,
    'wifiOnly': wifiOnly,
    'chargingOnly': chargingOnly,
    'spaceSaver': spaceSaver,
    'account': account,
  };
}

class VaultAlbum {
  VaultAlbum({required this.id, required this.name, required this.items});

  final String id;
  String name;
  final List<String> items;
}

/// What is in one vault item, worked out on the phone: labels from the
/// picture model (English, ImageNet's names) and the text read in a photo.
class VaultContent {
  const VaultContent({this.labels = const [], this.text = ''});

  /// Text beyond this is not kept: enough to find a receipt or a sign.
  static const maxText = 2000;

  final List<String> labels;
  final String text;

  static VaultContent? fromJson(Object? v) {
    if (v is! Map<String, Object?>) return null;
    return VaultContent(
      labels: [
        for (final l in (v['l'] as List<Object?>? ?? const []))
          if (l is String) l,
      ],
      text: v['t'] as String? ?? '',
    );
  }

  /// From what the phone sends back; null when it can't be worked out (the
  /// model is not in this build).
  static VaultContent? fromNative(Object? v) {
    if (v is! Map) return null;
    final labels = v['labels'];
    if (labels is! List) return null;
    final text = (v['text'] as String? ?? '').trim();
    return VaultContent(
      labels: [
        for (final l in labels)
          if (l is String) l,
      ],
      text: text.length > maxText ? text.substring(0, maxText) : text,
    );
  }

  Map<String, Object?> toJson() => {
    'l': labels,
    if (text.isNotEmpty) 't': text,
  };
}
