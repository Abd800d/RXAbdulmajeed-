import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/assistant/assistant_screen.dart';
import '../features/catalog/ingredient_detail_screen.dart';
import '../features/catalog/system_screen.dart';
import '../features/favorites/favorites_screen.dart';
import '../features/home/home_screen.dart';
import '../features/scanner/scanner_screen.dart';
import '../features/search/search_screen.dart';
import '../features/settings/settings_screen.dart';
import '../features/shell/app_shell.dart';

final routerProvider = Provider<GoRouter>(
  (ref) => GoRouter(
    initialLocation: '/',
    routes: [
      ShellRoute(
        builder: (context, state, child) => AppShell(
          location: state.matchedLocation,
          child: child,
        ),
        routes: [
          GoRoute(path: '/', builder: (context, state) => const HomeScreen()),
          GoRoute(path: '/search', builder: (context, state) => const SearchScreen()),
          GoRoute(path: '/scan', builder: (context, state) => const ScannerScreen()),
          GoRoute(path: '/favorites', builder: (context, state) => const FavoritesScreen()),
          GoRoute(path: '/settings', builder: (context, state) => const SettingsScreen()),
        ],
      ),
      GoRoute(
        path: '/system/:systemId',
        builder: (context, state) => SystemScreen(systemId: state.pathParameters['systemId']!),
      ),
      GoRoute(
        path: '/ingredient/:ingredientId',
        builder: (context, state) => IngredientDetailScreen(
          ingredientId: Uri.decodeComponent(state.pathParameters['ingredientId']!),
        ),
      ),
      GoRoute(path: '/assistant', builder: (context, state) => const AssistantScreen()),
    ],
  ),
);
