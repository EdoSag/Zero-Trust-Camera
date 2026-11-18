import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:zero_trust_core/zero_trust_core.dart';
import 'package:zerotrust_camera/app/auth_providers.dart';
import 'package:zerotrust_camera/app/camera_bridge.dart';
import 'package:zerotrust_camera/app/camera_config.dart';
import 'package:zerotrust_camera/main.dart';
import 'package:zerotrust_camera/ui/home_page.dart';

import 'support/app.dart';

void main() {
  testWidgets(
    'the camera screens are told the language, at start and on change',
    (tester) async {
      final fake = FakeBridge();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            ztAppConfigProvider.overrideWithValue(cameraAppConfig),
            ztPreferencesStoreProvider.overrideWithValue(
              InMemoryPreferencesStore(),
            ),
            cameraBridgeProvider.overrideWithValue(fake),
            currentUserProvider.overrideWithValue(null),
          ],
          child: const ZeroTrustCameraApp(),
        ),
      );
      await tester.pumpAndSettle();
      expect(fake.languages, ['en']);

      final container = ProviderScope.containerOf(
        tester.element(find.byType(HomePage)),
      );
      await container.read(ztLanguageProvider.notifier).set(ZtLanguages.hebrew);
      await tester.pumpAndSettle();
      expect(fake.languages, ['en', 'he']);
      expect(
        Directionality.of(tester.element(find.byType(HomePage))),
        TextDirection.rtl,
      );

      // "Phone language": the camera follows the phone again.
      await container.read(ztLanguageProvider.notifier).set(null);
      await tester.pumpAndSettle();
      expect(fake.languages, ['en', 'he', null]);
    },
  );
}
