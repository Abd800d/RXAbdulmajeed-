import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';
import 'package:flutter/services.dart';

import '../../domain/catalog_models.dart';

part 'app_database.g.dart';

@DataClassName('CachedIngredient')
class CachedIngredients extends Table {
  TextColumn get id => text()();
  TextColumn get genericName => text()();
  TextColumn get arabicName => text().withDefault(const Constant(''))();
  TextColumn get systemId => text().withDefault(const Constant('diagnostic'))();
  TextColumn get pharmacologyClass => text().withDefault(const Constant(''))();
  TextColumn get pharmacologyClassAr => text().withDefault(const Constant(''))();
  TextColumn get verificationStatus => text().withDefault(const Constant('unverified'))();
  TextColumn get imageUrl => text().nullable()();
  TextColumn get payloadJson => text()();
  IntColumn get entityVersion => integer().withDefault(const Constant(1))();
  DateTimeColumn get updatedAt => dateTime().nullable()();

  @override
  Set<Column<Object>> get primaryKey => {id};
}

@DataClassName('CachedProduct')
class CachedProducts extends Table {
  TextColumn get id => text()();
  TextColumn get ingredientId => text().references(CachedIngredients, #id, onDelete: KeyAction.cascade)();
  TextColumn get brandName => text()();
  TextColumn get brandNameAr => text().withDefault(const Constant(''))();
  TextColumn get strength => text().withDefault(const Constant(''))();
  TextColumn get dosageForm => text().withDefault(const Constant(''))();
  TextColumn get barcode => text().withDefault(const Constant(''))();
  TextColumn get imageUrl => text().nullable()();
  TextColumn get payloadJson => text()();
  IntColumn get entityVersion => integer().withDefault(const Constant(1))();
  DateTimeColumn get updatedAt => dateTime().nullable()();

  @override
  Set<Column<Object>> get primaryKey => {id};
}

class FavoriteIngredients extends Table {
  TextColumn get ingredientId => text().references(CachedIngredients, #id, onDelete: KeyAction.cascade)();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();

  @override
  Set<Column<Object>> get primaryKey => {ingredientId};
}

class SyncMetadata extends Table {
  TextColumn get key => text()();
  TextColumn get value => text()();
  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();

  @override
  Set<Column<Object>> get primaryKey => {key};
}

class PendingMutations extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get clientMutationId => text().unique()();
  TextColumn get entityType => text()();
  TextColumn get entityId => text()();
  TextColumn get operation => text()();
  TextColumn get payloadJson => text()();
  IntColumn get baseVersion => integer().nullable()();
  IntColumn get attemptCount => integer().withDefault(const Constant(0))();
  TextColumn get lastError => text().nullable()();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}

@DriftDatabase(
  tables: [
    CachedIngredients,
    CachedProducts,
    FavoriteIngredients,
    SyncMetadata,
    PendingMutations,
  ],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase([QueryExecutor? executor]) : super(executor ?? driftDatabase(name: 'aph_v2'));

  @override
  int get schemaVersion => 1;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (migrator) async => migrator.createAll(),
        beforeOpen: (details) async {
          await customStatement('PRAGMA foreign_keys = ON');
          await customStatement(
            'CREATE INDEX IF NOT EXISTS cached_ingredients_system_idx '
            'ON cached_ingredients(system_id)',
          );
          await customStatement(
            'CREATE INDEX IF NOT EXISTS cached_products_ingredient_idx '
            'ON cached_products(ingredient_id)',
          );
        },
      );

  Future<void> initialize() async {
    await customSelect('SELECT 1').get();
    final countRow = await customSelect(
      'SELECT COUNT(*) AS ingredient_count FROM cached_ingredients',
      readsFrom: {cachedIngredients},
    ).getSingle();
    final count = countRow.read<int>('ingredient_count');
    if (count == 0) await _loadBootstrapCatalogue();
  }

  Future<void> _loadBootstrapCatalogue() async {
    final source = await rootBundle.loadString('assets/data/catalog_bootstrap.json');
    final payload = jsonDecode(source) as Map<String, dynamic>;
    final ingredients = (payload['ingredients'] as List<dynamic>? ?? const [])
        .whereType<Map>()
        .map((item) => ActiveIngredient.fromJson(Map<String, dynamic>.from(item)));
    final products = (payload['products'] as List<dynamic>? ?? const [])
        .whereType<Map>()
        .map((item) => BrandProduct.fromJson(Map<String, dynamic>.from(item)));
    await transaction(() async {
      for (final ingredient in ingredients) {
        await putIngredient(ingredient);
      }
      for (final product in products) {
        await putProduct(product);
      }
      await setMetadata('bootstrap_version', payload['schemaVersion']?.toString() ?? '1');
    });
  }

  Stream<List<ActiveIngredient>> watchIngredients({String? systemId}) {
    final query = select(cachedIngredients);
    if (systemId != null && systemId.isNotEmpty) {
      query.where((table) => table.systemId.equals(systemId));
    }
    query.orderBy([(table) => OrderingTerm.asc(table.genericName)]);
    return query.watch().map(
          (rows) => rows.map(_ingredientFromRow).toList(growable: false),
        );
  }

  Stream<List<ActiveIngredient>> watchFavorites() {
    final query = select(cachedIngredients).join([
      innerJoin(
        favoriteIngredients,
        favoriteIngredients.ingredientId.equalsExp(cachedIngredients.id),
      ),
    ])..orderBy([OrderingTerm.asc(cachedIngredients.genericName)]);
    return query.watch().map(
          (rows) => rows
              .map((row) => _ingredientFromRow(row.readTable(cachedIngredients)))
              .toList(growable: false),
        );
  }

  Stream<List<ActiveIngredient>> searchIngredients(String searchTerm) {
    final normalized = searchTerm.trim();
    if (normalized.isEmpty) return watchIngredients();
    final query = select(cachedIngredients)
      ..where(
        (table) =>
            table.genericName.like('%$normalized%') |
            table.arabicName.like('%$normalized%') |
            table.pharmacologyClass.like('%$normalized%') |
            table.pharmacologyClassAr.like('%$normalized%'),
      )
      ..orderBy([(table) => OrderingTerm.asc(table.genericName)])
      ..limit(100);
    return query.watch().map(
          (rows) => rows.map(_ingredientFromRow).toList(growable: false),
        );
  }

  Stream<List<BrandProduct>> watchProducts(String ingredientId) {
    final query = select(cachedProducts)
      ..where((table) => table.ingredientId.equals(ingredientId))
      ..orderBy([(table) => OrderingTerm.asc(table.brandName)]);
    return query.watch().map(
          (rows) => rows.map(_productFromRow).toList(growable: false),
        );
  }

  Future<ActiveIngredient?> ingredientById(String id) async {
    final query = select(cachedIngredients)..where((table) => table.id.equals(id));
    return (await query.getSingleOrNull()).mapOrNull(_ingredientFromRow);
  }

  Future<ActiveIngredient?> ingredientByBarcode(String barcode) async {
    final query = select(cachedProducts).join([
      innerJoin(
        cachedIngredients,
        cachedIngredients.id.equalsExp(cachedProducts.ingredientId),
      ),
    ])..where(cachedProducts.barcode.equals(barcode));
    final row = await query.getSingleOrNull();
    return row == null ? null : _ingredientFromRow(row.readTable(cachedIngredients));
  }

  Future<void> putIngredient(ActiveIngredient ingredient) => into(cachedIngredients).insertOnConflictUpdate(
        CachedIngredientsCompanion.insert(
          id: ingredient.id,
          genericName: ingredient.genericName,
          arabicName: Value(ingredient.arabicName),
          systemId: Value(ingredient.systemId),
          pharmacologyClass: Value(ingredient.pharmacologyClass),
          pharmacologyClassAr: Value(ingredient.pharmacologyClassAr),
          verificationStatus: Value(ingredient.verificationStatus),
          imageUrl: Value(ingredient.imageUrl),
          payloadJson: jsonEncode(ingredient.toJson()),
          entityVersion: Value(ingredient.version),
          updatedAt: Value(ingredient.updatedAt),
        ),
      );

  Future<void> putProduct(BrandProduct product) => into(cachedProducts).insertOnConflictUpdate(
        CachedProductsCompanion.insert(
          id: product.id,
          ingredientId: product.ingredientId,
          brandName: product.brandName,
          brandNameAr: Value(product.brandNameAr),
          strength: Value(product.strength),
          dosageForm: Value(product.dosageForm),
          barcode: Value(product.barcode),
          imageUrl: Value(product.imageUrl),
          payloadJson: jsonEncode(product.toJson()),
          entityVersion: Value(product.version),
        ),
      );

  Future<void> deleteRemoteEntity(String entityType, String entityId) async {
    if (entityType == 'drug') {
      await (delete(cachedIngredients)..where((table) => table.id.equals(entityId))).go();
    } else if (entityType == 'product') {
      await (delete(cachedProducts)..where((table) => table.id.equals(entityId))).go();
    }
  }

  Stream<bool> watchFavorite(String ingredientId) =>
      (select(favoriteIngredients)..where((table) => table.ingredientId.equals(ingredientId)))
          .watch()
          .map((rows) => rows.isNotEmpty);

  Future<void> toggleFavorite(String ingredientId) async {
    final query = select(favoriteIngredients)..where((table) => table.ingredientId.equals(ingredientId));
    if (await query.getSingleOrNull() == null) {
      await into(favoriteIngredients).insert(
        FavoriteIngredientsCompanion.insert(ingredientId: ingredientId),
      );
    } else {
      await (delete(favoriteIngredients)..where((table) => table.ingredientId.equals(ingredientId))).go();
    }
  }

  Future<String?> metadata(String key) async {
    final query = select(syncMetadata)..where((table) => table.key.equals(key));
    return (await query.getSingleOrNull())?.value;
  }

  Future<void> setMetadata(String key, String value) => into(syncMetadata).insertOnConflictUpdate(
        SyncMetadataCompanion.insert(
          key: key,
          value: value,
          updatedAt: Value(DateTime.now().toUtc()),
        ),
      );

  ActiveIngredient _ingredientFromRow(CachedIngredient row) =>
      ActiveIngredient.fromJson(jsonDecode(row.payloadJson) as Map<String, dynamic>);

  BrandProduct _productFromRow(CachedProduct row) =>
      BrandProduct.fromJson(jsonDecode(row.payloadJson) as Map<String, dynamic>);
}

extension _NullableMap<T> on T? {
  R? mapOrNull<R>(R Function(T value) transform) {
    final value = this;
    return value == null ? null : transform(value);
  }
}
