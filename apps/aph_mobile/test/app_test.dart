import 'package:aph_mobile/src/app.dart';
import 'package:aph_mobile/src/domain/catalog_models.dart';
import 'package:aph_mobile/src/providers.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('starts with the bilingual offline catalogue shell', (tester) async {
    SharedPreferences.setMockInitialValues({'locale': 'en'});
    final preferences = await SharedPreferences.getInstance();
    final ingredient = ActiveIngredient.fromJson({
      'id': 'offline-nervous-record',
      'generic_name': 'Offline nervous medicine',
      'system_id': 'nervous',
      'pharmacology_class': 'Reference class',
      'verification_status': 'verified',
      'review_status': 'approved',
    });

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          sharedPreferencesProvider.overrideWithValue(preferences),
          allIngredientsProvider.overrideWith((ref) => Stream.value([ingredient])),
        ],
        child: const AphApp(),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('APH'), findsOneWidget);
    expect(find.text('Body systems & therapeutic areas'), findsOneWidget);
    expect(find.text('Nervous System'), findsOneWidget);
    expect(find.text('Offline nervous medicine'), findsOneWidget);
  });
}
