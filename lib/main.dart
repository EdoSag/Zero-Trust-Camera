import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:zero_trust_core/zero_trust_core.dart';

import 'app/camera_bridge.dart';
import 'app/camera_config.dart';
import 'app/email_links.dart';
import 'l10n/l10n.dart';
import 'ui/home_page.dart';

/// The Flutter half of ZT Camera: account, language, appearance and about.
/// The camera itself (viewfinder, shutter, gallery, camera settings) is
/// native Android code in `android/capture`, and is what the launcher opens;
/// these screens open from its settings.
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await dotenv.load(fileName: '.env');
  await Supabase.initialize(
    url: dotenv.env['SUPABASE_URL']!,
    publishableKey: dotenv.env['SUPABASE_ANON_KEY']!,
  );
  unawaited(EmailLinks.listen());
  // The camera screens started from GrapheneOS Camera (MIT): its notice is
  // listed under Open source licences.
  LicenseRegistry.addLicense(() async* {
    yield LicenseEntryWithLineBreaks(const [
      'Camera screens (from GrapheneOS Camera)',
    ], await rootBundle.loadString('assets/licenses/capture-upstream.txt'));
  });
  // English or Hebrew, as picked in Settings, from the first frame.
  await ZtLanguageController.preload();
  runApp(
    ProviderScope(
      overrides: [ztAppConfigProvider.overrideWithValue(cameraAppConfig)],
      child: const ZeroTrustCameraApp(),
    ),
  );
}

class ZeroTrustCameraApp extends ConsumerStatefulWidget {
  const ZeroTrustCameraApp({super.key, this.home = const HomePage()});

  final Widget home;

  @override
  ConsumerState<ZeroTrustCameraApp> createState() => _ZeroTrustCameraAppState();
}

class _ZeroTrustCameraAppState extends ConsumerState<ZeroTrustCameraApp> {
  @override
  void initState() {
    super.initState();
    // The camera screens follow the language picked here: now, and (in
    // build) every time it changes.
    ref
        .read(cameraBridgeProvider)
        .setLanguage(ref.read(ztLanguageProvider)?.languageCode);
  }

  @override
  Widget build(BuildContext context) {
    ref.listen<Locale?>(ztLanguageProvider, (_, Locale? next) {
      ref.read(cameraBridgeProvider).setLanguage(next?.languageCode);
    });
    return ZtThemeBuilder(
      builder: (context, theme, darkTheme, mode) => MaterialApp(
        // On-device task switcher label; the store name is Zero-Trust Camera.
        title: 'ZT Camera',
        theme: theme,
        darkTheme: darkTheme,
        themeMode: mode,
        // English (default) or Hebrew, from Appearance → Language.
        locale: ref.watch(ztLanguageProvider),
        supportedLocales: ZtLanguages.supported,
        localizationsDelegates: const [
          AppL10n.delegate,
          ...ZtLanguages.delegates,
        ],
        home: widget.home,
        // Any touch on any screen counts as activity for auto-lock.
        builder: (BuildContext context, Widget? child) => Listener(
          onPointerDown: (_) =>
              ref.read(vaultControllerProvider.notifier).touch(),
          child: child,
        ),
      ),
    );
  }
}
