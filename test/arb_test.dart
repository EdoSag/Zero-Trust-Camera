import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// The strings of the camera screens live in Android resource files, which
/// nothing else checks: every English string needs its Hebrew one, with the
/// same placeholders.
Map<String, String> _strings(String path) {
  final xml = File(path).readAsStringSync();
  return {
    for (final m in RegExp(
      r'<string name="([^"]+)"([^>]*)>(.*?)</string>',
      dotAll: true,
    ).allMatches(xml))
      if (!m.group(2)!.contains('translatable="false"'))
        m.group(1)!: m.group(3)!,
  };
}

List<String> _placeholders(String s) =>
    RegExp(r'%(?:\d\$)?[sd]').allMatches(s).map((m) => m.group(0)!).toList()
      ..sort();

void main() {
  const res = 'android/capture/src/main/res';

  test('camera screens: every string is translated to Hebrew', () {
    final en = _strings('$res/values/strings.xml');
    final he = _strings('$res/values-iw/strings.xml');
    expect(en, isNotEmpty);
    expect(he.keys.toSet(), en.keys.toSet());
    for (final name in en.keys) {
      expect(_placeholders(he[name]!), _placeholders(en[name]!), reason: name);
      expect(
        RegExp(r'[֐-׿]').hasMatch(he[name]!),
        isTrue,
        reason: '$name is not in Hebrew',
      );
    }
  });

  test('app screens: English and Hebrew have the same strings', () {
    Map<String, dynamic> arb(String code) =>
        jsonDecode(File('lib/l10n/app_$code.arb').readAsStringSync())
            as Map<String, dynamic>;
    expect(arb('he').keys.toSet(), arb('en').keys.toSet());
  });

  test('the camera never asks for location', () {
    for (final manifest in [
      'android/app/src/main/AndroidManifest.xml',
      'android/capture/src/main/AndroidManifest.xml',
    ]) {
      expect(File(manifest).readAsStringSync(), isNot(contains('LOCATION')));
    }
  });
}
