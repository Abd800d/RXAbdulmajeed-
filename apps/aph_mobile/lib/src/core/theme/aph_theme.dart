import 'package:flutter/material.dart';

class AphColors {
  const AphColors._();

  static const navy = Color(0xFF062C3E);
  static const deepNavy = Color(0xFF031E2C);
  static const teal = Color(0xFF0C8F8A);
  static const mint = Color(0xFF58D0C2);
  static const gold = Color(0xFFE6AF4B);
  static const surface = Color(0xFFF5F8F8);
  static const ink = Color(0xFF12262E);
}

class AphTheme {
  const AphTheme._();

  static ThemeData light() {
    final scheme = ColorScheme.fromSeed(
      seedColor: AphColors.teal,
      brightness: Brightness.light,
      primary: AphColors.teal,
      secondary: AphColors.gold,
      surface: AphColors.surface,
    );
    return _base(scheme);
  }

  static ThemeData dark() {
    final scheme = ColorScheme.fromSeed(
      seedColor: AphColors.mint,
      brightness: Brightness.dark,
      primary: AphColors.mint,
      secondary: AphColors.gold,
      surface: AphColors.deepNavy,
    );
    return _base(scheme);
  }

  static ThemeData _base(ColorScheme scheme) => ThemeData(
        useMaterial3: true,
        colorScheme: scheme,
        scaffoldBackgroundColor: scheme.surface,
        visualDensity: VisualDensity.standard,
        appBarTheme: AppBarTheme(
          centerTitle: false,
          backgroundColor: Colors.transparent,
          foregroundColor: scheme.onSurface,
          elevation: 0,
          scrolledUnderElevation: 0,
        ),
        cardTheme: CardThemeData(
          elevation: 0,
          color: scheme.surfaceContainerLowest,
          margin: EdgeInsets.zero,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(22),
            side: BorderSide(color: scheme.outlineVariant.withValues(alpha: 0.55)),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: scheme.surfaceContainerLowest,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(18),
            borderSide: BorderSide.none,
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(18),
            borderSide: BorderSide(color: scheme.outlineVariant.withValues(alpha: 0.7)),
          ),
          contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        ),
        navigationBarTheme: NavigationBarThemeData(
          height: 72,
          indicatorColor: scheme.primaryContainer,
          labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
        ),
        filledButtonTheme: FilledButtonThemeData(
          style: FilledButton.styleFrom(
            minimumSize: const Size(48, 48),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          ),
        ),
      );
}
