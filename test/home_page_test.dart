import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:zero_trust_core/zero_trust_core.dart';
import 'package:zerotrust_camera/ui/home_page.dart';

import 'support/app.dart';

/// A phone as wide as a small one and tall enough to lay the whole page
/// out, so every row can be checked and tapped.
void _phone(WidgetTester tester) {
  tester.view.physicalSize = const Size(360 * 3, 2400 * 3);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
}

void main() {
  testWidgets('works without an account and says what photos keep', (
    tester,
  ) async {
    _phone(tester);
    await tester.pumpWidget(app(const HomePage()));
    await tester.pumpAndSettle();

    expect(find.text('Zero-Trust Camera'), findsOneWidget);
    // The account is offered, not demanded.
    expect(find.text('Sign in or create an account'), findsOneWidget);
    expect(find.textContaining('the camera works without one'), findsOneWidget);

    // Location first, and never; the two choices are off unless turned on.
    final location = tester.getTopLeft(find.text('Location')).dy;
    final dateTime = tester.getTopLeft(find.text('Date and time')).dy;
    final model = tester.getTopLeft(find.text('Phone make and model')).dy;
    expect(location, lessThan(dateTime));
    expect(dateTime, lessThan(model));
    expect(
      find.text('Never saved. The app has no location permission.'),
      findsOneWidget,
    );
    expect(
      find.text("Not saved, unless you turn it on in the camera's settings"),
      findsNWidgets(2),
    );
  });

  testWidgets('the back arrow returns to the camera', (tester) async {
    _phone(tester);
    await tester.pumpWidget(app(const HomePage()));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Back to camera'));
    await tester.pump();
    expect(bridge.closed, 1);
  });

  testWidgets('links open the website pages', (tester) async {
    _phone(tester);
    final opened = <Uri>[];
    await tester.pumpWidget(app(HomePage(onOpenUrl: opened.add)));
    await tester.pumpAndSettle();
    for (final label in [
      'Help and feedback',
      'Privacy policy',
      'Terms of use',
    ]) {
      await tester.tap(find.text(label));
      await tester.pump();
    }
    expect(opened.map((u) => '$u'), [
      'https://zerotrust.bz/support',
      'https://zerotrust.bz/privacy',
      'https://zerotrust.bz/terms',
    ]);
    // The origin of the camera screens is credited where licences are.
    expect(find.textContaining('GrapheneOS Camera (MIT'), findsOneWidget);
    expect(find.textContaining('not made or endorsed'), findsOneWidget);
  });

  testWidgets('picking a language changes the app', (tester) async {
    _phone(tester);
    await tester.pumpWidget(app(const HomePage()));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Language'));
    await tester.pumpAndSettle();
    expect(find.text('English'), findsWidgets);
    await tester.tap(find.text('עברית'));
    await tester.pumpAndSettle();
    final container = ProviderScope.containerOf(
      tester.element(find.byType(HomePage)),
    );
    expect(container.read(ztLanguageProvider), ZtLanguages.hebrew);
  });
}
