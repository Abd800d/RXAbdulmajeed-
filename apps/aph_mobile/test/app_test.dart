import 'package:aph_mobile/src/app.dart';
import 'package:aph_mobile/src/data/local/app_database.dart';
import 'package:aph_mobile/src/providers.dart';
import 'package:drift/native.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('starts with the permanent offline catalogue', (tester) async {
    SharedPreferences.setMockInitialValues({'locale': 'en'});
    final preferences = await SharedPreferences.getInstance();
    final database = AppDatabase(NativeDatabase.memory());
    await database.initialize();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          databaseProvider.overrideWithValue(database),
          sharedPreferencesProvider.overrideWithValue(preferences),
        ],
        child: const AphApp(),
      ),
    );
    // Riverpod + Drift streams remain open for the life of the app, so a
    // bounded pump is correct here; pumpAndSettle would wait on live streams.
    for (var frame = 0; frame < 20; frame += 1) {
      await tester.pump(const Duration(milliseconds: 50));
    }

    expect(find.text('APH'), findsOneWidget);
    expect(find.text('Body systems & therapeutic areas'), findsOneWidget);
    expect(find.text('Nervous System'), findsOneWidget);

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump();
    await database.close();
  });
}
