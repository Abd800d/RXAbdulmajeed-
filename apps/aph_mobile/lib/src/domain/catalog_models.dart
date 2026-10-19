import 'dart:convert';

class ActiveIngredient {
  const ActiveIngredient({
    required this.id,
    required this.genericName,
    required this.arabicName,
    required this.systemId,
    required this.pharmacologyClass,
    required this.pharmacologyClassAr,
    required this.atcCode,
    required this.therapeuticClass,
    required this.therapeuticClassAr,
    required this.description,
    required this.descriptionAr,
    required this.mechanism,
    required this.mechanismAr,
    required this.indications,
    required this.indicationsAr,
    required this.cautions,
    required this.cautionsAr,
    required this.contraindications,
    required this.contraindicationsAr,
    required this.adverseEffects,
    required this.adverseEffectsAr,
    required this.interactions,
    required this.foodInteractions,
    required this.foodInteractionsAr,
    required this.dosageNote,
    required this.dosageNoteAr,
    required this.renalAdjustment,
    required this.renalAdjustmentAr,
    required this.hepaticAdjustment,
    required this.hepaticAdjustmentAr,
    required this.pregnancyLactation,
    required this.pregnancyLactationAr,
    required this.overdose,
    required this.overdoseAr,
    required this.evidenceLevel,
    required this.verificationStatus,
    required this.reviewStatus,
    required this.sourceRefs,
    required this.version,
    this.updatedAt,
    this.lastVerifiedAt,
    this.imageUrl,
  });

  final String id;
  final String genericName;
  final String arabicName;
  final String systemId;
  final String pharmacologyClass;
  final String pharmacologyClassAr;
  final String atcCode;
  final String therapeuticClass;
  final String therapeuticClassAr;
  final String description;
  final String descriptionAr;
  final String mechanism;
  final String mechanismAr;
  final List<String> indications;
  final List<String> indicationsAr;
  final List<String> cautions;
  final List<String> cautionsAr;
  final List<String> contraindications;
  final List<String> contraindicationsAr;
  final List<String> adverseEffects;
  final List<String> adverseEffectsAr;
  final List<String> interactions;
  final List<String> foodInteractions;
  final List<String> foodInteractionsAr;
  final String dosageNote;
  final String dosageNoteAr;
  final String renalAdjustment;
  final String renalAdjustmentAr;
  final String hepaticAdjustment;
  final String hepaticAdjustmentAr;
  final String pregnancyLactation;
  final String pregnancyLactationAr;
  final String overdose;
  final String overdoseAr;
  final String evidenceLevel;
  final String verificationStatus;
  final String reviewStatus;
  final List<Map<String, dynamic>> sourceRefs;
  final int version;
  final DateTime? updatedAt;
  final DateTime? lastVerifiedAt;
  final String? imageUrl;

  String name(bool isArabic) => isArabic && arabicName.isNotEmpty ? arabicName : genericName;
  String summary(bool isArabic) => isArabic && descriptionAr.isNotEmpty ? descriptionAr : description;
  String drugClass(bool isArabic) =>
      isArabic && pharmacologyClassAr.isNotEmpty ? pharmacologyClassAr : pharmacologyClass;

  factory ActiveIngredient.fromJson(Map<String, dynamic> json) => ActiveIngredient(
        id: _text(json['id']),
        genericName: _text(json['generic_name'] ?? json['genericName']),
        arabicName: _text(json['arabic_name'] ?? json['arabicName']),
        systemId: _text(json['system_id'] ?? json['systemId'], fallback: 'diagnostic'),
        pharmacologyClass: _text(json['pharmacology_class'] ?? json['pharmacologyClass']),
        pharmacologyClassAr: _text(json['pharmacology_class_ar'] ?? json['pharmacologyClassAr']),
        atcCode: _text(json['atc_code'] ?? json['atcCode']),
        therapeuticClass: _text(json['therapeutic_class'] ?? json['therapeuticClass']),
        therapeuticClassAr: _text(json['therapeutic_class_ar'] ?? json['therapeuticClassAr']),
        description: _text(json['description']),
        descriptionAr: _text(json['description_ar'] ?? json['descriptionAr']),
        mechanism: _text(json['mechanism']),
        mechanismAr: _text(json['mechanism_ar'] ?? json['mechanismAr']),
        indications: _strings(json['indications']),
        indicationsAr: _strings(json['indications_ar'] ?? json['indicationsAr']),
        cautions: _strings(json['cautions']),
        cautionsAr: _strings(json['cautions_ar'] ?? json['cautionsAr']),
        contraindications: _strings(json['contraindications']),
        contraindicationsAr: _strings(json['contraindications_ar'] ?? json['contraindicationsAr']),
        adverseEffects: _strings(json['adverse_effects'] ?? json['adverseEffects']),
        adverseEffectsAr: _strings(json['adverse_effects_ar'] ?? json['adverseEffectsAr']),
        interactions: _strings(json['interactions']),
        foodInteractions: _strings(json['food_interactions'] ?? json['foodInteractions']),
        foodInteractionsAr: _strings(json['food_interactions_ar'] ?? json['foodInteractionsAr']),
        dosageNote: _text(json['dosage_note'] ?? json['dosageNote']),
        dosageNoteAr: _text(json['dosage_note_ar'] ?? json['dosageNoteAr']),
        renalAdjustment: _text(json['renal_adjustment'] ?? json['renalAdjustment']),
        renalAdjustmentAr: _text(json['renal_adjustment_ar'] ?? json['renalAdjustmentAr']),
        hepaticAdjustment: _text(json['hepatic_adjustment'] ?? json['hepaticAdjustment']),
        hepaticAdjustmentAr: _text(json['hepatic_adjustment_ar'] ?? json['hepaticAdjustmentAr']),
        pregnancyLactation: _text(json['pregnancy_lactation'] ?? json['pregnancyLactation']),
        pregnancyLactationAr: _text(json['pregnancy_lactation_ar'] ?? json['pregnancyLactationAr']),
        overdose: _text(json['overdose']),
        overdoseAr: _text(json['overdose_ar'] ?? json['overdoseAr']),
        evidenceLevel: _text(json['evidence_level'] ?? json['evidenceLevel'], fallback: 'unrated'),
        verificationStatus: _text(
          json['verification_status'] ?? json['verificationStatus'],
          fallback: 'unverified',
        ),
        reviewStatus: _text(json['review_status'] ?? json['reviewStatus'], fallback: 'approved'),
        sourceRefs: _maps(json['source_refs'] ?? json['sourceRefs']),
        version: _integer(json['version'], fallback: 1),
        updatedAt: DateTime.tryParse(_text(json['updated_at'] ?? json['updatedAt'])),
        lastVerifiedAt: DateTime.tryParse(_text(json['last_verified_at'] ?? json['lastVerifiedAt'])),
        imageUrl: _nullableText(json['image_url'] ?? json['imageUrl']),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'generic_name': genericName,
        'arabic_name': arabicName,
        'system_id': systemId,
        'pharmacology_class': pharmacologyClass,
        'pharmacology_class_ar': pharmacologyClassAr,
        'atc_code': atcCode,
        'therapeutic_class': therapeuticClass,
        'therapeutic_class_ar': therapeuticClassAr,
        'description': description,
        'description_ar': descriptionAr,
        'mechanism': mechanism,
        'mechanism_ar': mechanismAr,
        'indications': indications,
        'indications_ar': indicationsAr,
        'cautions': cautions,
        'cautions_ar': cautionsAr,
        'contraindications': contraindications,
        'contraindications_ar': contraindicationsAr,
        'adverse_effects': adverseEffects,
        'adverse_effects_ar': adverseEffectsAr,
        'interactions': interactions,
        'food_interactions': foodInteractions,
        'food_interactions_ar': foodInteractionsAr,
        'dosage_note': dosageNote,
        'dosage_note_ar': dosageNoteAr,
        'renal_adjustment': renalAdjustment,
        'renal_adjustment_ar': renalAdjustmentAr,
        'hepatic_adjustment': hepaticAdjustment,
        'hepatic_adjustment_ar': hepaticAdjustmentAr,
        'pregnancy_lactation': pregnancyLactation,
        'pregnancy_lactation_ar': pregnancyLactationAr,
        'overdose': overdose,
        'overdose_ar': overdoseAr,
        'evidence_level': evidenceLevel,
        'verification_status': verificationStatus,
        'review_status': reviewStatus,
        'source_refs': sourceRefs,
        'version': version,
        'updated_at': updatedAt?.toIso8601String(),
        'last_verified_at': lastVerifiedAt?.toIso8601String(),
        'image_url': imageUrl,
      };
}

class BrandProduct {
  const BrandProduct({
    required this.id,
    required this.ingredientId,
    required this.brandName,
    required this.brandNameAr,
    required this.strength,
    required this.dosageForm,
    required this.dosageFormAr,
    required this.route,
    required this.routeAr,
    required this.packSize,
    required this.manufacturer,
    required this.country,
    required this.barcode,
    required this.availabilityStatus,
    required this.sourceNote,
    required this.pharmacistNotes,
    required this.verificationStatus,
    required this.reviewStatus,
    required this.version,
    this.imageUrl,
  });

  final String id;
  final String ingredientId;
  final String brandName;
  final String brandNameAr;
  final String strength;
  final String dosageForm;
  final String dosageFormAr;
  final String route;
  final String routeAr;
  final String packSize;
  final String manufacturer;
  final String country;
  final String barcode;
  final String availabilityStatus;
  final String sourceNote;
  final String pharmacistNotes;
  final String verificationStatus;
  final String reviewStatus;
  final int version;
  final String? imageUrl;

  String name(bool isArabic) => isArabic && brandNameAr.isNotEmpty ? brandNameAr : brandName;

  factory BrandProduct.fromJson(Map<String, dynamic> json) => BrandProduct(
        id: _text(json['id']),
        ingredientId: _text(json['drug_id'] ?? json['ingredientId']),
        brandName: _text(json['brand_name'] ?? json['brandName']),
        brandNameAr: _text(json['brand_name_ar'] ?? json['brandNameAr']),
        strength: _text(json['strength']),
        dosageForm: _text(json['dosage_form'] ?? json['dosageForm']),
        dosageFormAr: _text(json['dosage_form_ar'] ?? json['dosageFormAr']),
        route: _text(json['route']),
        routeAr: _text(json['route_ar'] ?? json['routeAr']),
        packSize: _text(json['pack_size'] ?? json['packSize']),
        manufacturer: _text(json['manufacturer']),
        country: _text(json['country']),
        barcode: _text(json['barcode']),
        availabilityStatus: _text(json['availability_status'] ?? json['availabilityStatus'], fallback: 'unknown'),
        sourceNote: _text(json['source_note'] ?? json['sourceNote']),
        pharmacistNotes: _text(json['pharmacist_notes'] ?? json['pharmacistNotes']),
        verificationStatus: _text(
          json['verification_status'] ?? json['verificationStatus'],
          fallback: 'unverified',
        ),
        reviewStatus: _text(json['review_status'] ?? json['reviewStatus'], fallback: 'approved'),
        version: _integer(json['version'], fallback: 1),
        imageUrl: _nullableText(json['image_url'] ?? json['imageUrl']),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'drug_id': ingredientId,
        'brand_name': brandName,
        'brand_name_ar': brandNameAr,
        'strength': strength,
        'dosage_form': dosageForm,
        'dosage_form_ar': dosageFormAr,
        'route': route,
        'route_ar': routeAr,
        'pack_size': packSize,
        'manufacturer': manufacturer,
        'country': country,
        'barcode': barcode,
        'availability_status': availabilityStatus,
        'source_note': sourceNote,
        'pharmacist_notes': pharmacistNotes,
        'verification_status': verificationStatus,
        'review_status': reviewStatus,
        'version': version,
        'image_url': imageUrl,
      };
}

String encodeJson(Object? value) => jsonEncode(value);

dynamic decodeJson(String value) => jsonDecode(value);

String _text(Object? value, {String fallback = ''}) => value?.toString().trim() ?? fallback;

String? _nullableText(Object? value) {
  final text = _text(value);
  return text.isEmpty ? null : text;
}

int _integer(Object? value, {required int fallback}) =>
    value is int ? value : int.tryParse(value?.toString() ?? '') ?? fallback;

List<String> _strings(Object? value) {
  if (value is List) {
    return value.map((item) => _text(item)).where((item) => item.isNotEmpty).toList(growable: false);
  }
  return const [];
}

List<Map<String, dynamic>> _maps(Object? value) {
  if (value is! List) return const [];
  return value.whereType<Map>().map((item) => Map<String, dynamic>.from(item)).toList(growable: false);
}
