import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'data/catalog_repository.dart';
import 'data/local/app_database.dart';
import 'data/remote/aph_api_client.dart';
import 'domain/catalog_models.dart';

final databaseProvider = Provider<AppDatabase>((ref) => throw StateError('Database was not initialized'));

final sharedPreferencesProvider =
    Provider<SharedPreferences>((ref) => throw StateError('Preferences were not initialized'));

final apiClientProvider = Provider<AphApiClient>((ref) => AphApiClient());

final catalogRepositoryProvider = Provider<CatalogRepository>(
  (ref) => CatalogRepository(ref.watch(databaseProvider), ref.watch(apiClientProvider)),
);

final localeProvider = StateProvider<Locale>((ref) {
  final stored = ref.watch(sharedPreferencesProvider).getString('locale');
  final platform = PlatformDispatcher.instance.locale.languageCode;
  return Locale(stored ?? (platform == 'ar' ? 'ar' : 'en'));
});

final themeModeProvider = StateProvider<ThemeMode>((ref) {
  final stored = ref.watch(sharedPreferencesProvider).getString('theme_mode');
  return switch (stored) {
    'dark' => ThemeMode.dark,
    'light' => ThemeMode.light,
    _ => ThemeMode.system,
  };
});

final allIngredientsProvider = StreamProvider<List<ActiveIngredient>>(
  (ref) => ref.watch(catalogRepositoryProvider).watchIngredients(),
);

final systemIngredientsProvider = StreamProvider.family<List<ActiveIngredient>, String>(
  (ref, systemId) => ref.watch(catalogRepositoryProvider).watchIngredients(systemId: systemId),
);

final favoriteIngredientsProvider = StreamProvider<List<ActiveIngredient>>(
  (ref) => ref.watch(catalogRepositoryProvider).watchFavorites(),
);

final productsProvider = StreamProvider.family<List<BrandProduct>, String>(
  (ref, ingredientId) => ref.watch(catalogRepositoryProvider).watchProducts(ingredientId),
);

final isFavoriteProvider = StreamProvider.family<bool, String>(
  (ref, ingredientId) => ref.watch(catalogRepositoryProvider).watchFavorite(ingredientId),
);

final ingredientProvider = FutureProvider.family<ActiveIngredient?, String>(
  (ref, ingredientId) => ref.watch(catalogRepositoryProvider).ingredientById(ingredientId),
);

final lastSyncProvider = FutureProvider<DateTime?>(
  (ref) => ref.watch(catalogRepositoryProvider).lastSync(),
);

class SyncController extends StateNotifier<AsyncValue<SyncResult?>> {
  SyncController(this._repository, this._ref) : super(const AsyncData(null));

  final CatalogRepository _repository;
  final Ref _ref;

  Future<void> synchronize() async {
    if (state.isLoading) return;
    state = const AsyncLoading();
    state = await AsyncValue.guard(_repository.synchronize);
    _ref.invalidate(lastSyncProvider);
  }
}

final syncControllerProvider = StateNotifierProvider<SyncController, AsyncValue<SyncResult?>>(
  (ref) => SyncController(ref.watch(catalogRepositoryProvider), ref),
);
