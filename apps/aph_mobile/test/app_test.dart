import 'package:aph_mobile/src/app.dart';
import 'package:aph_mobile/src/data/local/app_database.dart';
import 'package:aph_mobile/src/providers.dart';
import 'package:drift/native.dart';
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
    await tester.pumpAndSettle();

    expect(find.text('APH'), findsOneWidget);
    expect(find.text('Body systems & therapeutic areas'), findsOneWidget);
    expect(find.text('Nervous System'), findsOneWidget);

    await database.close();
  });
}
