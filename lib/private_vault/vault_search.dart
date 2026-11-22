import 'package:flutter/material.dart';

import 'vault_backend.dart';
import 'vault_content_words.dart';
import 'vault_library.dart';

/// A search of the Private vault by date, album and what is in a photo.
/// Everything is matched on the phone, against details the vault already
/// opened and what it worked out about each photo (VaultContent, kept in the
/// sealed library).
class VaultSearch {
  const VaultSearch({this.text = '', this.range});

  /// Words typed: album names, a month, a year, a day of the month, a date
  /// such as 21/11/2026 or 2026-11-21, what is in a photo (dog, beach, כלב)
  /// or text in it.
  final String text;

  /// Only items taken from the start of the first day to the end of the last.
  final DateTimeRange? range;

  bool get isEmpty => text.trim().isEmpty && range == null;

  List<String> get _words => [
    for (final w in text.toLowerCase().split(RegExp(r'\s+')))
      if (w.isNotEmpty) w,
  ];

  /// Whether an album called [name] matches the words typed.
  bool matchesAlbum(String name) {
    final n = name.toLowerCase();
    return _words.every(n.contains);
  }

  /// The items of [items] that match. [months] gives the names of each month
  /// (1 to 12) in the languages to accept, lower case.
  List<VaultItem> apply(
    List<VaultItem> items,
    VaultLibrary library, {
    required Map<int, List<String>> months,
  }) {
    if (isEmpty) return items;
    final words = _words;
    final albumNames = <String, List<String>>{};
    for (final a in library.albums.values) {
      final name = a.name.toLowerCase();
      for (final id in a.items) {
        (albumNames[id] ??= []).add(name);
      }
    }
    final r = range;
    final from = r == null
        ? null
        : DateTime(r.start.year, r.start.month, r.start.day);
    final to = r == null
        ? null
        : DateTime(r.end.year, r.end.month, r.end.day + 1);
    return [
      for (final i in items)
        if ((from == null ||
                (!i.takenAt.isBefore(from) && i.takenAt.isBefore(to!))) &&
            words.every(
              (w) => _wordMatches(
                w,
                i.takenAt,
                albumNames[i.id] ?? const [],
                months[i.takenAt.month] ?? const [],
                library.content[i.id],
              ),
            ))
          i,
    ];
  }

  static bool _wordMatches(
    String w,
    DateTime at,
    List<String> albums,
    List<String> monthNames,
    VaultContent? content,
  ) {
    if (albums.any((a) => a.contains(w))) return true;
    if (content != null && matchesContent(w, content)) return true;
    final number = int.tryParse(w);
    if (number != null) {
      // A year, or a day of the month.
      return w.length == 4 ? at.year == number : at.day == number;
    }
    // Three letters are enough for a month ("nov"), fewer would match too
    // much.
    if (w.length >= 3 && monthNames.any((m) => m.startsWith(w))) return true;
    final date = _parseDate(w);
    return date != null &&
        date.$1 == at.year &&
        date.$2 == at.month &&
        (date.$3 == null || date.$3 == at.day);
  }

  /// Whether the word [w] (lower case) names what is in a photo, or is in
  /// its text. "dog" finds every breed, "כלב" too; a label's own name
  /// ("golden retriever") works as well.
  static bool matchesContent(String w, VaultContent content) {
    if (w.length < 2) return false;
    final labels = [for (final l in content.labels) l.toLowerCase()];
    if (labels.any((l) => l.contains(w))) return true;
    if (content.text.toLowerCase().contains(w)) return true;
    for (final c in contentCategories) {
      if (c.names.any((n) => n.startsWith(w)) &&
          content.labels.any(c.labels.contains)) {
        return true;
      }
    }
    return false;
  }

  /// 2026-11-21, 21/11/2026, 21.11.2026, and without the day: 2026-11,
  /// 11/2026.
  static (int, int, int?)? _parseDate(String w) {
    final parts = w.split(RegExp(r'[./-]'));
    if (parts.length < 2 || parts.length > 3) return null;
    final n = [for (final p in parts) int.tryParse(p)];
    if (n.contains(null)) return null;
    final yearFirst = parts.first.length == 4;
    final year = yearFirst ? n.first! : n.last!;
    if (year < 1000) return null;
    if (parts.length == 2) return (year, yearFirst ? n[1]! : n[0]!, null);
    return yearFirst ? (year, n[1]!, n[2]) : (year, n[1]!, n[0]);
  }
}
