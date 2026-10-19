import 'package:flutter/material.dart';

class BodySystem {
  const BodySystem({
    required this.id,
    required this.name,
    required this.nameAr,
    required this.color,
    required this.icon,
  });

  final String id;
  final String name;
  final String nameAr;
  final Color color;
  final IconData icon;

  String localized(bool isArabic) => isArabic ? nameAr : name;
}

const bodySystems = <BodySystem>[
  BodySystem(id: 'nervous', name: 'Nervous System', nameAr: 'الجهاز العصبي', color: Color(0xFF7259A8), icon: Icons.psychology_alt_outlined),
  BodySystem(id: 'cardiovascular', name: 'Cardiovascular System', nameAr: 'جهاز القلب والأوعية', color: Color(0xFFD45161), icon: Icons.monitor_heart_outlined),
  BodySystem(id: 'blood', name: 'Blood & Hematopoietic', nameAr: 'الدم وتكوين الدم', color: Color(0xFFA83E58), icon: Icons.bloodtype_outlined),
  BodySystem(id: 'respiratory', name: 'Respiratory System', nameAr: 'الجهاز التنفسي', color: Color(0xFF3F87A6), icon: Icons.air_outlined),
  BodySystem(id: 'gastrointestinal', name: 'Gastrointestinal System', nameAr: 'الجهاز الهضمي', color: Color(0xFFD47B39), icon: Icons.medical_information_outlined),
  BodySystem(id: 'hepatobiliary', name: 'Hepatobiliary System', nameAr: 'الكبد والقنوات الصفراوية', color: Color(0xFFA77935), icon: Icons.health_and_safety_outlined),
  BodySystem(id: 'endocrine', name: 'Endocrine System', nameAr: 'جهاز الغدد الصماء', color: Color(0xFF3B8F87), icon: Icons.hub_outlined),
  BodySystem(id: 'renal', name: 'Renal & Urinary System', nameAr: 'الكلى والجهاز البولي', color: Color(0xFF6884BD), icon: Icons.water_drop_outlined),
  BodySystem(id: 'reproductive', name: 'Reproductive System', nameAr: 'الجهاز التناسلي', color: Color(0xFFB65E91), icon: Icons.family_restroom_outlined),
  BodySystem(id: 'musculoskeletal', name: 'Musculoskeletal System', nameAr: 'الجهاز العضلي الهيكلي', color: Color(0xFF99704B), icon: Icons.accessibility_new_outlined),
  BodySystem(id: 'skin', name: 'Skin', nameAr: 'الجلد', color: Color(0xFFB9785A), icon: Icons.face_retouching_natural_outlined),
  BodySystem(id: 'eye', name: 'Eye', nameAr: 'العين', color: Color(0xFF4B8DB5), icon: Icons.visibility_outlined),
  BodySystem(id: 'ent', name: 'Ear, Nose & Throat', nameAr: 'الأنف والأذن والحنجرة', color: Color(0xFF707CB4), icon: Icons.hearing_outlined),
  BodySystem(id: 'immune', name: 'Immune System', nameAr: 'الجهاز المناعي', color: Color(0xFF4D9169), icon: Icons.shield_outlined),
  BodySystem(id: 'antiinfective', name: 'Anti-infectives', nameAr: 'مضادات العدوى', color: Color(0xFF168477), icon: Icons.coronavirus_outlined),
  BodySystem(id: 'oncology', name: 'Oncology', nameAr: 'الأورام', color: Color(0xFF85629E), icon: Icons.join_inner_outlined),
  BodySystem(id: 'emergency', name: 'Emergency & Toxicology', nameAr: 'الطوارئ والسموم', color: Color(0xFFD35B42), icon: Icons.emergency_outlined),
  BodySystem(id: 'diagnostic', name: 'Diagnostic & Miscellaneous', nameAr: 'التشخيص والأدوية المتنوعة', color: Color(0xFF63737B), icon: Icons.biotech_outlined),
];

BodySystem bodySystemById(String id) => bodySystems.firstWhere(
      (system) => system.id == id,
      orElse: () => bodySystems.last,
    );
