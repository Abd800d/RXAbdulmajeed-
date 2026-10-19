import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/localization/aph_localizations.dart';
import 'core/theme/aph_theme.dart';
import 'navigation/router.dart';
import 'providers.dart';

class AphApp extends ConsumerWidget {
  const AphApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final locale = ref.watch(localeProvider);
    final themeMode = ref.watch(themeModeProvider);
    final router = ref.watch(routerProvider);
    return MaterialApp.router(
      title: 'APH · AbdulmajeedPharma',
      debugShowCheckedModeBanner: false,
      locale: locale,
      supportedLocales: AphLocalizations.supportedLocales,
      localizationsDelegates: const [
        AphLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      theme: AphTheme.light(),
      darkTheme: AphTheme.dark(),
      themeMode: themeMode,
      routerConfig: router,
    );
  }
}
