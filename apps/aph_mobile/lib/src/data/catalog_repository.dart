import '../core/config/app_config.dart';
import '../domain/catalog_models.dart';
import 'local/app_database.dart';
import 'remote/aph_api_client.dart';

class SyncResult {
  const SyncResult({
    required this.appliedChanges,
    required this.sequence,
    required this.skipped,
  });

  final int appliedChanges;
  final int sequence;
  final bool skipped;
}

class CatalogRepository {
  CatalogRepository(this.database, this.api);

  final AppDatabase database;
  final AphApiClient api;
  bool _syncing = false;

  Stream<List<ActiveIngredient>> watchIngredients({String? systemId}) =>
      database.watchIngredients(systemId: systemId);

  Stream<List<ActiveIngredient>> watchFavorites() => database.watchFavorites();

  Stream<List<ActiveIngredient>> searchLocal(String query) => database.searchIngredients(query);

  Stream<List<BrandProduct>> watchProducts(String ingredientId) => database.watchProducts(ingredientId);

  Future<ActiveIngredient?> ingredientById(String id) => database.ingredientById(id);

  Future<ActiveIngredient?> identifyBarcode(String barcode, String language) async {
    final local = await database.ingredientByBarcode(barcode);
    if (local != null) return local;
    final remote = await searchCloud(barcode, language);
    return remote.isEmpty ? null : remote.first;
  }

  Stream<bool> watchFavorite(String ingredientId) => database.watchFavorite(ingredientId);

  Future<void> toggleFavorite(String ingredientId) => database.toggleFavorite(ingredientId);

  Future<List<ActiveIngredient>> searchCloud(String query, String language) async {
    if (!AppConfig.cloudConfigured || query.trim().length < 2) return const [];
    final remote = await api.search(query, language: language);
    for (final ingredient in remote) {
      await database.putIngredient(ingredient);
    }
    return remote;
  }

  Future<DateTime?> lastSync() async {
    final value = await database.metadata('last_sync_at');
    return value == null ? null : DateTime.tryParse(value);
  }

  Future<SyncResult> synchronize() async {
    if (!AppConfig.cloudConfigured || _syncing) {
      final cursor = int.tryParse(await database.metadata('change_sequence') ?? '') ?? 0;
      return SyncResult(appliedChanges: 0, sequence: cursor, skipped: true);
    }
    _syncing = true;
    try {
      var cursor = int.tryParse(await database.metadata('change_sequence') ?? '') ?? 0;
      var applied = 0;
      var keepGoing = true;
      while (keepGoing) {
        final page = await api.changes(after: cursor);
        final ingredients = <ActiveIngredient>[];
        final products = <BrandProduct>[];
        final deletions = <SyncChange>[];
        for (final change in page.changes) {
          if (change.operation == 'DELETE') {
            deletions.add(change);
          } else if (change.entityType == 'drug') {
            ingredients.add(await api.ingredient(change.entityId));
          } else if (change.entityType == 'product') {
            final product = await api.product(change.entityId);
            if (await database.ingredientById(product.ingredientId) == null &&
                !ingredients.any((ingredient) => ingredient.id == product.ingredientId)) {
              ingredients.add(await api.ingredient(product.ingredientId));
            }
            products.add(product);
          }
        }
        await database.transaction(() async {
          for (final ingredient in ingredients) {
            await database.putIngredient(ingredient);
          }
          for (final product in products) {
            await database.putProduct(product);
          }
          for (final change in deletions) {
            await database.deleteRemoteEntity(change.entityType, change.entityId);
          }
          await database.setMetadata('change_sequence', page.nextSequence.toString());
          await database.setMetadata('last_sync_at', DateTime.now().toUtc().toIso8601String());
        });
        applied += page.changes.length;
        cursor = page.nextSequence;
        keepGoing = page.hasMore && page.changes.isNotEmpty;
      }
      return SyncResult(appliedChanges: applied, sequence: cursor, skipped: false);
    } finally {
      _syncing = false;
    }
  }
}
