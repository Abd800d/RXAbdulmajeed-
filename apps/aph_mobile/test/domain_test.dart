import 'package:aph_mobile/src/domain/body_system.dart';
import 'package:aph_mobile/src/domain/catalog_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('the complete APH body-system taxonomy is present', () {
    expect(bodySystems, hasLength(18));
    expect(bodySystems.map((item) => item.id).toSet(), hasLength(18));
    expect(bodySystems.every((item) => item.nameAr.isNotEmpty), isTrue);
  });

  test('ingredient decoder accepts Supabase snake-case fields', () {
    final ingredient = ActiveIngredient.fromJson({
      'id': 'amoxicillin',
      'generic_name': 'Amoxicillin',
      'arabic_name': 'أموكسيسيلين',
      'system_id': 'antiinfective',
      'contraindications': ['Severe penicillin hypersensitivity'],
      'renal_adjustment': 'Refer to approved dosing reference.',
      'verification_status': 'verified',
      'review_status': 'approved',
      'version': 4,
    });
    expect(ingredient.genericName, 'Amoxicillin');
    expect(ingredient.contraindications, hasLength(1));
    expect(ingredient.renalAdjustment, isNotEmpty);
    expect(ingredient.version, 4);
  });
}
