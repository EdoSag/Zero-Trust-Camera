import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:zero_trust_core/zero_trust_core.dart';
import 'package:zerotrust_camera/app/auth_providers.dart';
import 'package:zerotrust_camera/app/camera_bridge.dart';
import 'package:zerotrust_camera/app/camera_config.dart';
import 'package:zerotrust_camera/l10n/l10n.dart';

/// Records what would be sent to the camera screens.
class FakeBridge extends CameraBridge {
  final List<String?> languages = [];
  int closed = 0;

  @override
  Future<void> setLanguage(String? languageCode) async =>
      languages.add(languageCode);

  @override
  Future<void> close() async => closed++;
}

late FakeBridge bridge;

/// [home] inside the app's real theme and languages, with no account
/// (or [user]) and nothing native behind it.
Widget app(Widget home, {Locale locale = ZtLanguages.english, User? user}) {
  bridge = FakeBridge();
  return ProviderScope(
    overrides: [
      ztAppConfigProvider.overrideWithValue(cameraAppConfig),
      ztPreferencesStoreProvider.overrideWithValue(InMemoryPreferencesStore()),
      cameraBridgeProvider.overrideWithValue(bridge),
      currentUserProvider.overrideWithValue(user),
    ],
    child: Consumer(
      builder: (context, ref, _) => MaterialApp(
        // The suite's real theme: its full-width buttons break rows that a
        // plain theme lets through.
        theme: ZtTheme.light(accent: cameraAppConfig.accent),
        locale: locale,
        supportedLocales: ZtLanguages.supported,
        localizationsDelegates: const [
          AppL10n.delegate,
          ...ZtLanguages.delegates,
        ],
        home: home,
      ),
    ),
  );
}
