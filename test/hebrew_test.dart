import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:zero_trust_core/zero_trust_core.dart';
import 'package:zerotrust_camera/ui/home_page.dart';

import 'support/app.dart';

void main() {
  testWidgets('Hebrew: right to left, translated, nothing overflows', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360 * 3, 740 * 3);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(app(const HomePage(), locale: ZtLanguages.hebrew));
    await tester.pumpAndSettle();

    expect(
      Directionality.of(tester.element(find.byType(HomePage))),
      TextDirection.rtl,
    );
    expect(find.text('כניסה או יצירת חשבון'), findsOneWidget);
    // The privacy section sits below the tools; bring it into view.
    await tester.scrollUntilVisible(
      find.text('מיקום'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('מיקום'), findsOneWidget);
    // Right to left: the icon sits to the right of its text.
    final icon = tester.getCenter(find.byIcon(Icons.location_off_outlined)).dx;
    final text = tester.getCenter(find.text('מיקום')).dx;
    expect(icon, greaterThan(text));

    // Scroll through the whole page: an overflow would fail the test.
    await tester.scrollUntilVisible(
      find.text('רישיונות קוד פתוח'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
