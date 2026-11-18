import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:zero_trust_core/zero_trust_core.dart';
import 'package:zerotrust_camera/app/auth_providers.dart';
import 'package:zerotrust_camera/app/camera_bridge.dart';
import 'package:zerotrust_camera/app/camera_config.dart';
import 'package:zerotrust_camera/main.dart';
import 'package:zerotrust_camera/ui/home_page.dart';

import 'support/app.dart';

/// An account server that is never reached.
class _NoServer implements AccountBackend {
  @override
  dynamic noSuchMethod(Invocation invocation) => null;
}

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
            authStateChangesProvider.overrideWith(
              (ref) => const Stream<AuthState>.empty(),
            ),
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

  testWidgets(
    'a reset-password link opens "choose a new password" from the first page',
    (tester) async {
      final events = StreamController<AuthState>();
      addTearDown(events.close);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            ztAppConfigProvider.overrideWithValue(cameraAppConfig),
            ztPreferencesStoreProvider.overrideWithValue(
              InMemoryPreferencesStore(),
            ),
            cameraBridgeProvider.overrideWithValue(FakeBridge()),
            currentUserProvider.overrideWithValue(null),
            authStateChangesProvider.overrideWith((ref) => events.stream),
            // The reset screen only has to appear; nothing is sent.
            accountServiceProvider.overrideWithValue(
              AccountService(
                _NoServer(),
                keyCache: SecureStorageKeyCache(),
                biometrics: SecureBiometricKeyStore(promptTitle: 'Unlock'),
              ),
            ),
          ],
          child: const ZeroTrustCameraApp(),
        ),
      );
      await tester.pumpAndSettle();
      // The account page has not been opened: nothing but the first page.
      expect(find.byType(HomePage), findsOneWidget);
      expect(find.byType(ZtResetPasswordScreen), findsNothing);

      // What the link raises once its token is accepted.
      events.add(const AuthState(AuthChangeEvent.passwordRecovery, null));
      await tester.pumpAndSettle();
      expect(find.byType(ZtResetPasswordScreen), findsOneWidget);
    },
  );
}
