import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/localization/aph_localizations.dart';

class AppShell extends StatelessWidget {
  const AppShell({
    required this.location,
    required this.child,
    super.key,
  });

  final String location;
  final Widget child;

  static const _paths = ['/', '/search', '/scan', '/favorites', '/settings'];

  int get _selectedIndex {
    final index = _paths.indexWhere(
      (path) => path == '/' ? location == '/' : location.startsWith(path),
    );
    return index < 0 ? 0 : index;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return Scaffold(
      body: SafeArea(bottom: false, child: child),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: (index) => context.go(_paths[index]),
        destinations: [
          NavigationDestination(icon: const Icon(Icons.home_outlined), selectedIcon: const Icon(Icons.home), label: l10n.text('home')),
          NavigationDestination(icon: const Icon(Icons.search), label: l10n.text('search')),
          NavigationDestination(icon: const Icon(Icons.qr_code_scanner), label: l10n.text('scan')),
          NavigationDestination(icon: const Icon(Icons.bookmark_border), selectedIcon: const Icon(Icons.bookmark), label: l10n.text('favorites')),
          NavigationDestination(icon: const Icon(Icons.tune), label: l10n.text('settings')),
        ],
      ),
    );
  }
}
